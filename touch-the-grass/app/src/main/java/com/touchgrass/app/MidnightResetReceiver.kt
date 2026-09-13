package com.touchgrass.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Starts a new day: clears every blocked app's spent time budget and asks
 * Gemini for a fresh mission.
 *
 * Reached two ways - the midnight alarm, and BOOT_COMPLETED, since alarms don't
 * survive a reboot and the chain would otherwise stay broken until the user
 * re-activated by hand. Both paths run the same date-guarded rollover, so a
 * reboot is harmless and a midnight missed while the phone was off (or whose
 * alarm the OEM killed) still gets caught on the next delivery.
 */
class MidnightResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (context.appState.getLastResetDate() != todayString()) {
                    context.appState.resetAllUsage()
                    val result = context.gemini.generateMission()
                    val mission = result.getOrElse { "Take a photo of a bench." }
                    context.appState.setMission(mission)
                    context.appState.setLastResetDate(todayString())
                }
            } finally {
                AlarmScheduler.scheduleMidnightAlarm(context)
                pendingResult.finish()
            }
        }
    }
}

object AlarmScheduler {
    private const val REQUEST_CODE = 1001

    fun scheduleMidnightAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, MidnightResetReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 5)
            set(Calendar.MILLISECOND, 0)
        }

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent
            )
        } catch (e: SecurityException) {
            // Android 12+ without the exact-alarm permission granted - fall back to inexact.
            alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
    }
}

fun todayString(): String {
    val cal = Calendar.getInstance()
    return "%04d-%02d-%02d".format(
        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
    )
}
