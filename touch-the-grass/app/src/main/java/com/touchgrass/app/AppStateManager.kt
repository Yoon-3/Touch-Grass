package com.touchgrass.app

import android.content.Context

/**
 * Simple SharedPreferences-backed state store. Kept synchronous on purpose so
 * it can be read directly from the AccessibilityService callback without
 * coroutines. Built once by [TouchGrassApp]; reach it via `context.appState`.
 */
class AppStateManager(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getBlockedApps(): Set<String> =
        prefs.getStringSet(KEY_BLOCKED_APPS, emptySet()) ?: emptySet()

    fun setBlockedApps(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_BLOCKED_APPS, packages).apply()
    }

    fun isActivated(): Boolean = prefs.getBoolean(KEY_IS_ACTIVATED, false)

    fun setActivated(activated: Boolean) {
        prefs.edit().putBoolean(KEY_IS_ACTIVATED, activated).apply()
    }

    fun getMission(): String = prefs.getString(KEY_MISSION, "") ?: ""

    fun setMission(mission: String) {
        prefs.edit().putString(KEY_MISSION, mission).apply()
    }

    fun getLastResetDate(): String = prefs.getString(KEY_LAST_RESET_DATE, "") ?: ""

    fun setLastResetDate(date: String) {
        prefs.edit().putString(KEY_LAST_RESET_DATE, date).apply()
    }

    fun getTimeLimitMinutes(packageName: String): Int =
        prefs.getInt(KEY_LIMIT_PREFIX + packageName, DEFAULT_TIME_LIMIT_MINUTES)

    fun setTimeLimitMinutes(packageName: String, minutes: Int) {
        prefs.edit().putInt(KEY_LIMIT_PREFIX + packageName, minutes).apply()
    }

    /** Cumulative foreground time (ms) for [packageName] since the last reset. */
    fun getUsageMillis(packageName: String): Long =
        prefs.getLong(KEY_USAGE_PREFIX + packageName, 0L)

    fun addUsageMillis(packageName: String, millis: Long) {
        val key = KEY_USAGE_PREFIX + packageName
        val updated = prefs.getLong(key, 0L) + millis
        prefs.edit().putLong(key, updated).apply()
    }

    fun resetUsage(packageName: String) {
        prefs.edit().remove(KEY_USAGE_PREFIX + packageName).apply()
    }

    fun resetAllUsage() {
        val editor = prefs.edit()
        getBlockedApps().forEach { editor.remove(KEY_USAGE_PREFIX + it) }
        editor.apply()
    }

    companion object {
        private const val PREFS_NAME = "touch_the_grass_prefs"
        private const val KEY_BLOCKED_APPS = "blocked_apps"
        private const val KEY_IS_ACTIVATED = "is_activated"
        private const val KEY_MISSION = "current_mission"
        private const val KEY_LAST_RESET_DATE = "last_reset_date"
        private const val KEY_LIMIT_PREFIX = "time_limit_minutes_"
        private const val KEY_USAGE_PREFIX = "usage_millis_"
        const val DEFAULT_TIME_LIMIT_MINUTES = 30
    }
}
