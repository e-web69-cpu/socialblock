package com.example.socialblock

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.PopupMenu

/** A létramenü (útmutató, működés, protokoll, adatvédelem, Kisegítő lehetőség) – fő- és tiltó képernyőn is. */
object InfoMenu {
    private val items = listOf(
        R.string.info_install_title to R.string.info_install_body,
        R.string.info_howto_title to R.string.info_howto_body,
        R.string.info_protocol_title to R.string.info_protocol_body,
        R.string.info_privacy_title to R.string.info_privacy_body,
        R.string.info_a11y_title to R.string.info_a11y_body
    )

    fun show(activity: Activity, anchor: View) {
        val popup = PopupMenu(activity, anchor)
        items.forEachIndexed { i, (title, _) -> popup.menu.add(0, i, i, title) }
        popup.setOnMenuItemClickListener { item ->
            val (title, body) = items[item.itemId]
            activity.startActivity(Intent(activity, InfoActivity::class.java)
                .putExtra(InfoActivity.EXTRA_TITLE, title)
                .putExtra(InfoActivity.EXTRA_BODY, body))
            true
        }
        popup.show()
    }
}
