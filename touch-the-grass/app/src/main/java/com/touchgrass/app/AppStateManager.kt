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
    private const val KEY_IS_LOCKED = "is_locked"
    private const val KEY_IS_ACTIVATED = "is_activated"
    private const val KEY_MISSION = "current_mission"
    private const val KEY_LAST_RESET_DATE = "last_reset_date"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getBlockedApps(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_BLOCKED_APPS, emptySet()) ?: emptySet()

    fun setBlockedApps(context: Context, packages: Set<String>) {
        prefs(context).edit().putStringSet(KEY_BLOCKED_APPS, packages).apply()
    }

    fun isLocked(context: Context): Boolean =
        prefs(context).getBoolean(KEY_IS_LOCKED, false)

    fun setLocked(context: Context, locked: Boolean) {
        prefs(context).edit().putBoolean(KEY_IS_LOCKED, locked).apply()
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
}
