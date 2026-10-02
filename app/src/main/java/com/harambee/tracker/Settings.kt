package com.harambee.tracker

import android.content.Context
import com.harambee.tracker.core.Templates
import com.harambee.tracker.core.UpdateOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** App preferences, each readable as a StateFlow so screens update immediately. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    inner class BoolSetting(val key: String, val default: Boolean, val portable: Boolean = true) {
        private val state = MutableStateFlow(prefs.getBoolean(key, default))
        val value: StateFlow<Boolean> = state
        fun set(v: Boolean) {
            prefs.edit().putBoolean(key, v).apply()
            state.value = v
        }
    }

    inner class LongSetting(val key: String, val default: Long) {
        private val state = MutableStateFlow(prefs.getLong(key, default))
        val value: StateFlow<Long> = state
        fun set(v: Long) {
            prefs.edit().putLong(key, v).apply()
            state.value = v
        }
    }

    inner class TextSetting(val key: String, val default: String, val portable: Boolean = true) {
        private val state = MutableStateFlow(prefs.getString(key, null) ?: default)
        val value: StateFlow<String> = state
        fun set(text: String) {
            val v = text.ifBlank { default }
            prefs.edit().putString(key, v).apply()
            state.value = v
        }
    }

    // Capture
    /** Ask (via notification) before counting each M-Pesa payment, instead of adding it automatically. */
    val askBeforeAdding = BoolSetting("ask_before_adding", true)
    /** When the app opens, look in the inbox for M-Pesa messages that arrived while it wasn't running. */
    val autoCatchUp = BoolSetting("auto_catch_up", true)
    val lastScanAt = LongSetting("last_scan_at", 0)

    // Look and security
    val themeMode = TextSetting("theme_mode", THEME_SYSTEM)
    val dynamicColor = BoolSetting("dynamic_color", false)
    /** Fingerprint / PIN when opening the app; also hides the screen in the recent-apps view. */
    val appLock = BoolSetting("app_lock", false, portable = false)
    val onboarded = BoolSetting("onboarded", false, portable = false)

    // Data safety
    val lastBackupAt = LongSetting("last_backup_at", 0)
    val lastSnapshotAt = LongSetting("last_snapshot_at", 0)

    // Messages
    val thankYouTemplate = TextSetting("thank_you_template", Templates.THANK_YOU)
    val pledgeReminderTemplate = TextSetting("pledge_reminder_template", Templates.PLEDGE_REMINDER)
    val memberReminderTemplate = TextSetting("member_reminder_template", Templates.MEMBER_REMINDER)

    // WhatsApp update defaults (remembered between updates)
    private val combineRepeat = BoolSetting("update_combine", true)
    private val showTotal = BoolSetting("update_total", true)
    private val showTarget = BoolSetting("update_target", true)
    private val showPledges = BoolSetting("update_pledges", true)
    private val addNextNumber = BoolSetting("update_next_number", true)
    private val showDate = BoolSetting("update_date", false)
    private val sortByAmount = BoolSetting("update_sort_amount", false)
    private val listLimit = LongSetting("update_list_limit", -1)

    fun updateOptions() = UpdateOptions(
        showTotal = showTotal.value.value,
        showTarget = showTarget.value.value,
        showPledges = showPledges.value.value,
        addNextNumber = addNextNumber.value.value,
        showDate = showDate.value.value,
        sortByAmount = sortByAmount.value.value,
        combineRepeat = combineRepeat.value.value,
        listLimit = listLimit.value.value.takeIf { it >= 0 }?.toInt(),
    )

    fun saveUpdateOptions(o: UpdateOptions) {
        showTotal.set(o.showTotal)
        showTarget.set(o.showTarget)
        showPledges.set(o.showPledges)
        addNextNumber.set(o.addNextNumber)
        showDate.set(o.showDate)
        sortByAmount.set(o.sortByAmount)
        combineRepeat.set(o.combineRepeat)
        listLimit.set(o.listLimit?.toLong() ?: -1)
    }

    private val portableBools get() = listOf(askBeforeAdding, autoCatchUp, dynamicColor, combineRepeat, showTotal, showTarget, showPledges, addNextNumber, showDate, sortByAmount)
    private val portableTexts get() = listOf(themeMode, thankYouTemplate, pledgeReminderTemplate, memberReminderTemplate)

    /** Preferences that travel with a backup (not device-specific ones like the app lock). */
    fun export(): Map<String, String> =
        portableBools.associate { it.key to it.value.value.toString() } +
            portableTexts.associate { it.key to it.value.value } +
            mapOf(listLimit.key to listLimit.value.value.toString())

    fun import(values: Map<String, String>) {
        portableBools.forEach { s -> values[s.key]?.toBooleanStrictOrNull()?.let { s.set(it) } }
        portableTexts.forEach { s -> values[s.key]?.let { s.set(it) } }
        values[listLimit.key]?.toLongOrNull()?.let { listLimit.set(it) }
    }

    companion object {
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
    }
}
