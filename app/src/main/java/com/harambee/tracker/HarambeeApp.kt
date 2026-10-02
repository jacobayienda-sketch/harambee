package com.harambee.tracker

import android.app.Application
import android.content.Context
import com.harambee.tracker.core.Templates
import com.harambee.tracker.data.HarambeeDatabase
import com.harambee.tracker.data.HarambeeRepository
import com.harambee.tracker.data.IngestResult
import com.harambee.tracker.sms.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HarambeeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannels()
    }
}

val Context.container: AppContainer get() = (applicationContext as HarambeeApp).container

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _askBeforeAdding = MutableStateFlow(prefs.getBoolean(KEY_ASK, true))

    /** Ask (via notification) before counting each M-Pesa payment, instead of adding it automatically. */
    val askBeforeAdding: StateFlow<Boolean> = _askBeforeAdding

    fun setAskBeforeAdding(value: Boolean) {
        prefs.edit().putBoolean(KEY_ASK, value).apply()
        _askBeforeAdding.value = value
    }

    val thankYouTemplate = TextSetting("thank_you_template", Templates.THANK_YOU)
    val pledgeReminderTemplate = TextSetting("pledge_reminder_template", Templates.PLEDGE_REMINDER)
    val memberReminderTemplate = TextSetting("member_reminder_template", Templates.MEMBER_REMINDER)

    inner class TextSetting(private val key: String, val default: String) {
        private val state = MutableStateFlow(prefs.getString(key, null) ?: default)
        val value: StateFlow<String> = state

        fun set(text: String) {
            val v = text.ifBlank { default }
            prefs.edit().putString(key, v).apply()
            state.value = v
        }
    }

    private companion object {
        const val KEY_ASK = "ask_before_adding"
    }
}

class AppContainer(context: Context) {
    val repository = HarambeeRepository(HarambeeDatabase.create(context).dao())
    val settings = Settings(context)
    val notifier = Notifier(context, settings)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Called for every M-Pesa SMS that arrives while the app is installed. */
    suspend fun handleIncomingSms(body: String, smsTime: Long) {
        when (val result = repository.ingestSms(body, smsTime, autoConfirm = !settings.askBeforeAdding.value)) {
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
