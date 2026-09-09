package com.example.socialblock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Figyeli, melyik app kerül előtérbe. Ha tiltott app + tiltott idősáv,
 * ráteszi a teljes képernyős blokkoló Activity-t.
 *
 * Jogosultság: Beállítások > Kisegítő lehetőségek > SocialBlock > Bekapcsolás.
 */
class BlockAccessibilityService : AccessibilityService() {

    private var lastBlockedPackage: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val blockedPackages = ScheduleManager.getBlockedPackages(this)
        if (pkg !in blockedPackages) {
            lastBlockedPackage = null
            return
        }

        if (!ScheduleManager.isInBlockedWindowNow(this)) return

        // Elkerüljük, hogy ugyanarra az appra újra és újra elinduljon az overlay
        if (lastBlockedPackage == pkg) return
        lastBlockedPackage = pkg

        val intent = Intent(this, BlockOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockOverlayActivity.EXTRA_BLOCKED_PACKAGE, pkg)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        // no-op
    }
}
