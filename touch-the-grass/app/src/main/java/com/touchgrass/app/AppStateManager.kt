package com.touchgrass.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Simple SharedPreferences-backed state store. Kept synchronous on purpose so
 * it can be read directly from the AccessibilityService callback without
 * coroutines.
 */
object AppStateManager {
    private const val PREFS_NAME = "touch_the_grass_prefs"
    private const val KEY_BLOCKED_APPS = "blocked_apps"
    private const val KEY_IS_ACTIVATED = "is_activated"
    private const val KEY_MISSION = "current_mission"
    private const val KEY_LAST_RESET_DATE = "last_reset_date"
    private const val KEY_LIMIT_PREFIX = "time_limit_minutes_"
    private const val KEY_USAGE_PREFIX = "usage_millis_"
    const val DEFAULT_TIME_LIMIT_MINUTES = 30

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getBlockedApps(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_BLOCKED_APPS, emptySet()) ?: emptySet()

    fun setBlockedApps(context: Context, packages: Set<String>) {
        prefs(context).edit().putStringSet(KEY_BLOCKED_APPS, packages).apply()
    }

    fun isActivated(context: Context): Boolean =
        prefs(context).getBoolean(KEY_IS_ACTIVATED, false)

    fun setActivated(context: Context, activated: Boolean) {
        prefs(context).edit().putBoolean(KEY_IS_ACTIVATED, activated).apply()
    }

    fun getMission(context: Context): String =
        prefs(context).getString(KEY_MISSION, "") ?: ""

    fun setMission(context: Context, mission: String) {
        prefs(context).edit().putString(KEY_MISSION, mission).apply()
    }

    fun getLastResetDate(context: Context): String =
        prefs(context).getString(KEY_LAST_RESET_DATE, "") ?: ""

    fun setLastResetDate(context: Context, date: String) {
        prefs(context).edit().putString(KEY_LAST_RESET_DATE, date).apply()
    }

    fun getTimeLimitMinutes(context: Context, packageName: String): Int =
        prefs(context).getInt(KEY_LIMIT_PREFIX + packageName, DEFAULT_TIME_LIMIT_MINUTES)

    fun setTimeLimitMinutes(context: Context, packageName: String, minutes: Int) {
        prefs(context).edit().putInt(KEY_LIMIT_PREFIX + packageName, minutes).apply()
    }

    /** Cumulative foreground time (ms) for [packageName] since the last reset. */
    fun getUsageMillis(context: Context, packageName: String): Long =
        prefs(context).getLong(KEY_USAGE_PREFIX + packageName, 0L)

    fun addUsageMillis(context: Context, packageName: String, millis: Long) {
        val key = KEY_USAGE_PREFIX + packageName
        val updated = prefs(context).getLong(key, 0L) + millis
        prefs(context).edit().putLong(key, updated).apply()
    }

    fun resetUsage(context: Context, packageName: String) {
        prefs(context).edit().remove(KEY_USAGE_PREFIX + packageName).apply()
    }

    fun resetAllUsage(context: Context) {
        val editor = prefs(context).edit()
        getBlockedApps(context).forEach { editor.remove(KEY_USAGE_PREFIX + it) }
        editor.apply()
    }
}
