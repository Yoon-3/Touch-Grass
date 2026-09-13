package com.touchgrass.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
                // Read the clock once: the mission fetch below can straddle
                // midnight, and storing a later date than the one we compared
                // against would skip a day's reset.
                val today = todayString()
                if (context.appState.getLastResetDate() != today) {
                    context.appState.resetAllUsage()
                    val result = context.gemini.generateMission()
                    val mission = result.getOrElse { "Take a photo of a bench." }
                    context.appState.setMission(mission)
                    context.appState.setLastResetDate(today)
                }
            } finally {
                AlarmScheduler.scheduleMidnightAlarm(context)
                pendingResult.finish()
            }
        }
    }
}
