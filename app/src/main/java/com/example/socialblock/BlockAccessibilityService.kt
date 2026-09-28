package com.example.socialblock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class BlockAccessibilityService : AccessibilityService() {

    private var lastBlockedPackage: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        if (!ScheduleManager.isSimpleBlockingEnabled(this)) {
            lastBlockedPackage = null
            return
        }

        // A SocialBlock saját magát soha nem blokkolja.
        val targets = ScheduleManager.getBlockedPackages(this) - packageName
        if (pkg !in targets) {
            lastBlockedPackage = null
            return
        }

        if (!ScheduleManager.isNowWithinSchedule(this)) {
            lastBlockedPackage = null
            return
        }

        // Sikeres feloldás után az ideiglenes szünet alatt nem blokkolunk.
        if (ScheduleManager.isTemporarilyUnlocked(this)) {
            lastBlockedPackage = null
            return
        }

        if (lastBlockedPackage == pkg) return
        lastBlockedPackage = pkg

        val intent = Intent(this, BlockOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockOverlayActivity.EXTRA_BLOCKED_PACKAGE, pkg)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
    }
}
