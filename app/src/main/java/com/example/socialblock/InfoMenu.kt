package com.example.socialblock

import android.app.Activity
import android.content.Intent
import android.app.AlertDialog
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView

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
        popup.menu.add(0, items.size, items.size, R.string.sync_menu_title)
        popup.menu.add(0, items.size + 1, items.size + 1, R.string.sites_menu_title)
        popup.menu.add(0, items.size + 2, items.size + 2, R.string.pc_menu_title)
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == items.size + 2) { showPcDialog(activity); return@setOnMenuItemClickListener true }
            if (item.itemId == items.size + 1) { showSitesDialog(activity); return@setOnMenuItemClickListener true }
            if (item.itemId == items.size) { showSyncDialog(activity); return@setOnMenuItemClickListener true }
            val (title, body) = items[item.itemId]
            activity.startActivity(Intent(activity, InfoActivity::class.java)
                .putExtra(InfoActivity.EXTRA_TITLE, title)
                .putExtra(InfoActivity.EXTRA_BODY, body))
            true
        }
        popup.show()
    }

    private const val PC_LINK = "https://raw.githubusercontent.com/e-web69-cpu/socialblock/main/dist/SociaLock_windows_modul.zip"

    private fun showPcDialog(activity: Activity) {
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0) }
        box.addView(TextView(activity).apply { setText(R.string.pc_steps) })
        val input = EditText(activity).apply {
            setHint(R.string.pc_key_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            setText(SyncManager.getReadKey(activity))
        }
        box.addView(input)
        fun save() { SyncManager.setReadKey(activity, input.text.toString()) }
        val scroll = android.widget.ScrollView(activity).apply { addView(box) }
        AlertDialog.Builder(activity)
            .setTitle(R.string.pc_menu_title)
            .setView(scroll)
            .setPositiveButton(R.string.pc_share_link) { _, _ ->
                save()
                activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, PC_LINK)
                }, activity.getString(R.string.pc_share_link)))
            }
            .setNeutralButton(R.string.pc_copy_key) { _, _ ->
                save()
                val cm = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("SociaLock", input.text.toString().trim()))
                android.widget.Toast.makeText(activity, R.string.pc_key_copied, android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.sync_save) { _, _ -> save() }
            .show()
    }

    private fun showSitesDialog(activity: Activity) {
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0) }
        box.addView(TextView(activity).apply { setText(R.string.sites_info) })
        val input = EditText(activity).apply {
            setHint(R.string.sites_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_VARIATION_URI
            minLines = 5
            gravity = android.view.Gravity.TOP
            setText(ScheduleManager.getBlockedSites(activity).joinToString("\n"))
        }
        box.addView(input)
        AlertDialog.Builder(activity)
            .setTitle(R.string.sites_menu_title)
            .setView(box)
            .setPositiveButton(R.string.sync_save) { _, _ ->
                ScheduleManager.setBlockedSites(activity, ScheduleManager.parseSites(input.text.toString()))
                SyncManager.syncIfNeeded(activity)
            }
            .setNegativeButton(R.string.sync_cancel, null)
            .show()
    }

    private fun showSyncDialog(activity: Activity) {
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0) }
        box.addView(TextView(activity).apply { setText(R.string.sync_info) })
        val status = TextView(activity).apply { text = SyncManager.statusText(activity); setPadding(0, pad / 2, 0, pad / 2) }
        box.addView(status)
        val input = EditText(activity).apply {
            setHint(R.string.sync_key_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        box.addView(input)
        AlertDialog.Builder(activity)
            .setTitle(R.string.sync_dialog_title)
            .setView(box)
            .setPositiveButton(R.string.sync_save) { _, _ ->
                val k = input.text.toString().trim()
                if (k.isNotEmpty()) { SyncManager.setKey(activity, k); SyncManager.syncIfNeeded(activity) }
            }
            .setNeutralButton(R.string.sync_clear) { _, _ -> SyncManager.clearKey(activity) }
            .setNegativeButton(R.string.sync_cancel, null)
            .show()
    }
}
