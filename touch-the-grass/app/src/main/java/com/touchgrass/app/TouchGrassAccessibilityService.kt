package com.touchgrass.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Tracks how long each blocked app has been in the foreground and shows the
 * block overlay once its daily time budget (AppStateManager.getTimeLimitMinutes)
 * runs out - either immediately on switching to an already-over-budget app, or
 * via a delayed callback timed to fire the moment the budget runs out while
 * the app stays in the foreground.
 */
class TouchGrassAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var currentPackage: String? = null
    private var sessionStartElapsed: Long = 0L
    private var pendingBlock: Runnable? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == currentPackage) return

        endSession()
        currentPackage = packageName
        sessionStartElapsed = SystemClock.elapsedRealtime()

        val isTracked = appState.isActivated() &&
            appState.getBlockedApps().contains(packageName)
        if (!isTracked) return
        // Already bought back with a photo today - leave it alone, or a
        // 0-minute limit would re-block the instant the overlay closes.
        if (appState.isUnlockedForToday(packageName)) return

        val limitMillis = appState.getTimeLimitMinutes(packageName) * 60_000L
        val remaining = limitMillis - appState.getUsageMillis(packageName)

        if (remaining <= 0) {
            showBlockOverlay(packageName)
        } else {
            val runnable = Runnable { if (currentPackage == packageName) showBlockOverlay(packageName) }
            pendingBlock = runnable
            handler.postDelayed(runnable, remaining)
        }
    }

    /** Banks elapsed foreground time for the app we're leaving and cancels its pending block. */
    private fun endSession() {
        pendingBlock?.let { handler.removeCallbacks(it) }
        pendingBlock = null
        val packageName = currentPackage ?: return
        if (appState.isActivated() && appState.getBlockedApps().contains(packageName)) {
            val elapsed = SystemClock.elapsedRealtime() - sessionStartElapsed
            appState.addUsageMillis(packageName, elapsed)
        }
    }

    private fun showBlockOverlay(packageName: String) {
        val overlayIntent = Intent(this, BlockOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(BlockOverlayActivity.EXTRA_PACKAGE_NAME, packageName)
        }
        startActivity(overlayIntent)
    }

    override fun onInterrupt() {}
}
