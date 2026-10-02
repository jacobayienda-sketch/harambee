package com.harambee.tracker

import android.app.Application
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.harambee.tracker.data.HarambeeDatabase
import com.harambee.tracker.data.HarambeeRepository
import com.harambee.tracker.data.IngestResult
import com.harambee.tracker.sms.Notifier
import com.harambee.tracker.sms.SmsSources
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class HarambeeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannels()
        container.onAppStart()
    }
}

val Context.container: AppContainer get() = (applicationContext as HarambeeApp).container

class AppContainer(private val context: Context) {
    val repository = HarambeeRepository(HarambeeDatabase.create(context))
    val settings = Settings(context)
    val notifier = Notifier(context, settings)
    val backups = BackupManager(context, repository, settings)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val catchUpLock = Mutex()

    /** Daily safety copy; cheap and silent. */
    fun onAppStart() {
        scope.launch { runCatching { backups.snapshotIfDue() } }
    }

    /**
     * Picks up M-Pesa messages that arrived while the app wasn't running (phone restarted, app
     * force-stopped, battery saver). Already-recorded codes are skipped, so running it often is safe.
     */
    fun catchUp(minIntervalMs: Long = 10 * 60_000L) {
        if (!BuildConfig.SMS_CAPTURE || !settings.autoCatchUp.value.value) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return
        val now = System.currentTimeMillis()
        if (now - settings.lastScanAt.value.value < minIntervalMs) return
        scope.launch {
            if (!catchUpLock.tryLock()) return@launch
            try {
                val earliest = repository.earliestActiveStart() ?: return@launch
                // Overlap the last scan by a day: a message may be dated before it was delivered.
                val since = maxOf(earliest, settings.lastScanAt.value.value - 24 * 3600_000L)
                val summary = SmsSources.scanInbox(context, since, campaignId = null, autoConfirm = !settings.askBeforeAdding.value.value)
                settings.lastScanAt.set(now)
                if (summary.toReview > 0) notifier.showCatchUp(summary.toReview)
            } finally {
                catchUpLock.unlock()
            }
        }
    }

    /** Called for every M-Pesa SMS that arrives while the app is installed. */
    suspend fun handleIncomingSms(body: String, smsTime: Long) {
        when (val result = repository.ingestSms(body, smsTime, autoConfirm = !settings.askBeforeAdding.value.value)) {
            is IngestResult.Recorded -> {
                val confirmed = result.confirmed
                if (confirmed != null) {
                    notifier.showConfirmed(confirmed)
                } else {
                    notifier.showPending(result.contribution, repository.reviewInfo(result.contribution))
                }
            }
            is IngestResult.Reversed -> notifier.showReversed(result.contribution)
            is IngestResult.Duplicate, IngestResult.Ignored -> Unit
        }
    }
}
