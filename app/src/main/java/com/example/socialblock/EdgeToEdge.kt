package com.example.socialblock

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Android 15+ (targetSdk 35/36) alatt az appok a rendszersávok alá rajzolnak.
 * A tartalom kap annyi belső margót, hogy ne csússzon az állapot- és navigációs sáv alá.
 */
object EdgeToEdge {
    fun apply(activity: Activity) {
        val content = activity.findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(content)
    }
}
