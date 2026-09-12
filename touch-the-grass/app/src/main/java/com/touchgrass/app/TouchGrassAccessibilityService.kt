package com.touchgrass.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class TouchGrassAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return

        if (packageName == applicationContext.packageName) return

        val isActivated = AppStateManager.isActivated(this)
        val isLocked = AppStateManager.isLocked(this)
        val blockedApps = AppStateManager.getBlockedApps(this)

        if (isActivated && isLocked && blockedApps.contains(packageName)) {
            val overlayIntent = Intent(this, BlockOverlayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(overlayIntent)
        }
    }

    override fun onInterrupt() {}
}
