package com.example.socialblock

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * A Device Admin jogosultság önmagában nem tudja letiltani más appok indítását,
 * de megnehezíti (megerősítő dialógushoz köti) az app eltávolítását és
 * bizonyos rendszerbeállítások módosítását. Kombinálva az Accessibility
 * Service-szel ez egy plusz súrlódási réteg.
 */
class BlockDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return context.getString(R.string.device_admin_disable_warning)
    }
}
