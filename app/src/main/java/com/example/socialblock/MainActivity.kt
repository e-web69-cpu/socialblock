package com.example.socialblock

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.socialblock.databinding.ActivityMainBinding
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private val CURATED_PACKAGES = listOf(
        "com.facebook.katana",
        "com.instagram.android",
        "com.facebook.orca",
        "com.twitter.android",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.google.android.gm",
        "com.microsoft.office.outlook",
        "com.yahoo.mobile.client.android.mail",
        "com.samsung.android.email.provider"
    )

    private lateinit var binding: ActivityMainBinding
    private lateinit var appListAdapter: AppListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        styleHero()
        playEntranceAnimation()

        binding.recyclerApps.layoutManager = LinearLayoutManager(this)
        loadInstalledApps()

        binding.buttonGrantAccessibility.setOnClickListener {
            // Play-szabály: külön tájékoztatás és kifejezett hozzájárulás a Kisegítő lehetőség előtt
            val prefs = getSharedPreferences("socialock_consent", MODE_PRIVATE)
            if (prefs.getBoolean("a11y_consent", false)) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.info_a11y_title)
                    .setMessage(android.text.Html.fromHtml(getString(R.string.info_a11y_body), android.text.Html.FROM_HTML_MODE_COMPACT))
                    .setCancelable(false)
                    .setPositiveButton(R.string.a11y_accept) { _, _ ->
                        prefs.edit().putBoolean("a11y_consent", true).apply()
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    .setNegativeButton(R.string.a11y_decline, null)
                    .show()
            }
        }

        binding.buttonMenu.setOnClickListener { v ->
            val items = listOf(
                R.string.info_install_title to R.string.info_install_body,
                R.string.info_howto_title to R.string.info_howto_body,
                R.string.info_protocol_title to R.string.info_protocol_body,
                R.string.info_privacy_title to R.string.info_privacy_body,
                R.string.info_a11y_title to R.string.info_a11y_body
            )
            val popup = android.widget.PopupMenu(this, v)
            items.forEachIndexed { i, (title, _) -> popup.menu.add(0, i, i, title) }
            popup.setOnMenuItemClickListener { item ->
                val (title, body) = items[item.itemId]
                startActivity(Intent(this, InfoActivity::class.java)
                    .putExtra(InfoActivity.EXTRA_TITLE, title)
                    .putExtra(InfoActivity.EXTRA_BODY, body))
                true
            }
            popup.show()
        }

        binding.buttonGrantUsageAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        binding.buttonGrantOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        binding.buttonEnableDeviceAdmin.setOnClickListener {
            val adminComponent = ComponentName(this, BlockDeviceAdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.device_admin_explanation)
                )
            }
            startActivity(intent)
        }

        binding.switchBlockingEnabled.isChecked = ScheduleManager.isSimpleBlockingEnabled(this)
        binding.switchBlockingEnabled.setOnCheckedChangeListener { buttonView, checked ->
            if (!checked && !ScheduleManager.canLoosenProtection(this)) {
                buttonView.isChecked = true
                showLockedMessage(R.string.settings_locked)
                return@setOnCheckedChangeListener
            }
            if (checked && !ScheduleManager.hasWindow(this)) {
                // Az idősáv kötelező: nélküle nem kapcsolható be a tiltás.
                buttonView.isChecked = false
                showLockedMessage(R.string.schedule_required)
                return@setOnCheckedChangeListener
            }
            ScheduleManager.setSimpleBlockingEnabled(this, checked)
            refreshLockState()
        }

        binding.buttonSaveSchedule.setOnClickListener { saveSchedule() }
        loadExistingSchedule()

        setupHelperCard()

        binding.inputSearchApps.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    appListAdapter.filter(s?.toString().orEmpty())
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
        refreshLockState()
        lockTicker.postDelayed(lockTick, 5_000)
    }

    override fun onPause() {
        lockTicker.removeCallbacks(lockTick)
        super.onPause()
    }

    // A zárolás állapota akkor is frissül, ha a felhasználó az oldalon marad és közben elindul a tiltási idősáv.
    private val lockTicker = android.os.Handler(android.os.Looper.getMainLooper())
    private val lockTick = object : Runnable {
        override fun run() {
            refreshLockState()
            lockTicker.postDelayed(this, 5_000)
        }
    }

    /** Idősáv, segítő és fejléc állapotának frissítése az aktuális tiltás szerint. */
    private fun refreshLockState() {
        refreshScheduleLock()
        ScheduleManager.refreshHelper(this)
        refreshHelperCard()
        refreshHeaderStatus()
    }

    // ---- Megjelenés (v1.4) ----

    /** Fejléc: „A SociaLock jelenleg: AKTÍV/INAKTÍV”. */
    private fun refreshHeaderStatus() {
        val active = ScheduleManager.isBlockingActiveNow(this) && !ScheduleManager.isTemporarilyUnlocked(this)
        binding.textHeaderStatus.text = getString(if (active) R.string.status_active else R.string.status_inactive)
        binding.textHeaderStatus.setTextColor(
            androidx.core.content.ContextCompat.getColor(this, if (active) R.color.danger else R.color.accent)
        )
        binding.textHeaderStatus.setBackgroundResource(if (active) R.drawable.bg_status_on else R.drawable.bg_status_off)
    }

    /** Cím: csak az S (arculati kék) és az L (lakat zöld) színes; az alcím a cím szélességében; „lélegző” logó. */
    private fun styleHero() {
        val title = binding.textHeroTitle
        val text = title.text.toString()
        val span = android.text.SpannableString(text)
        val blue = android.graphics.Color.parseColor("#3A6EF8")
        val green = android.graphics.Color.parseColor("#10B981")
        val sIdx = text.indexOf('S')
        val lIdx = text.indexOf('L')
        if (sIdx >= 0) span.setSpan(android.text.style.ForegroundColorSpan(blue), sIdx, sIdx + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (lIdx >= 0) span.setSpan(android.text.style.ForegroundColorSpan(green), lIdx, lIdx + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        title.text = span

        // Az alcím szélességét a betűközzel a címéhez igazítjuk.
        val sub = binding.textHeroSubtitle
        title.post {
            val target = title.paint.measureText(text)
            val subText = sub.text.toString()
            if (subText.length > 1) {
                sub.letterSpacing = 0f
                val base = sub.paint.measureText(subText)
                val extraPx = (target - base) / (subText.length - 1)
                val spacingEm = extraPx / sub.textSize
                sub.letterSpacing = spacingEm.coerceIn(-0.05f, 0.6f)
            }
        }
        android.animation.ObjectAnimator.ofPropertyValuesHolder(
            binding.imageHeroLogo,
            android.animation.PropertyValuesHolder.ofFloat(android.view.View.SCALE_X, 1f, 1.06f),
            android.animation.PropertyValuesHolder.ofFloat(android.view.View.SCALE_Y, 1f, 1.06f)
        ).apply {
            duration = 2200
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            start()
        }
    }

    /** A kártyák egymás után, lentről beúsznak. */
    private fun playEntranceAnimation() {
        val container = binding.contentContainer
        val shift = 40f * resources.displayMetrics.density
        for (i in 0 until container.childCount) {
            val v = container.getChildAt(i)
            v.alpha = 0f
            v.translationY = shift
            v.animate()
                .alpha(1f).translationY(0f)
                .setStartDelay(80L * i)
                .setDuration(500)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.6f))
                .start()
        }
    }

    private fun refreshScheduleLock() {
        val editable = ScheduleManager.canEditSchedule(this)
        val alpha = if (editable) 1f else 0.5f
        listOf(
            binding.inputStartHour, binding.inputStartMinute,
            binding.inputEndHour, binding.inputEndMinute
        ).forEach {
            it.isEnabled = editable
            it.alpha = alpha
        }
        binding.buttonSaveSchedule.alpha = alpha
    }

    private fun showLockedMessage(resId: Int) {
        android.widget.Toast.makeText(this, getString(resId), android.widget.Toast.LENGTH_LONG).show()
    }

    /** A Jogosultságok kártya csak akkor látszik, ha valamelyik engedély hiányzik; a kártyák számozása ehhez igazodik. */
    private fun refreshPermissionCard(allGranted: Boolean) {
        binding.cardPermissions.visibility = if (allGranted) android.view.View.GONE else android.view.View.VISIBLE
        var n = 1
        if (!allGranted) binding.badgePerm.text = (n++).toString()
        binding.badgeSchedule.text = (n++).toString()
        binding.badgeHelper.text = (n++).toString()
        binding.badgeApps.text = (n).toString()
    }

    private fun refreshPermissionState() {
        setRowState(binding.rowAccessibility, binding.buttonGrantAccessibility, binding.checkAccessibility, isAccessibilityServiceEnabled())
        setRowState(binding.rowUsage, binding.buttonGrantUsageAccess, binding.checkUsage, isUsageAccessGranted())
        setRowState(binding.rowOverlay, binding.buttonGrantOverlay, binding.checkOverlay, Settings.canDrawOverlays(this))
        setRowState(binding.rowDeviceAdmin, binding.buttonEnableDeviceAdmin, binding.checkDeviceAdmin, isDeviceAdminActive())
        refreshPermissionCard(
            isAccessibilityServiceEnabled() && isUsageAccessGranted() &&
                Settings.canDrawOverlays(this) && isDeviceAdminActive()
        )
    }

    private fun setRowState(
        row: android.widget.LinearLayout,
        button: android.widget.Button,
        check: android.widget.TextView,
        granted: Boolean
    ) {
        button.visibility = if (granted) android.view.View.GONE else android.view.View.VISIBLE
        check.visibility = if (granted) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "$packageName/${BlockAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun isUsageAccessGranted(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isDeviceAdminActive(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, BlockDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(adminComponent)
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val launchableApps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.packageName }.filter { it != packageName }.distinct().sorted()

        val curatedApps = CURATED_PACKAGES.filter { it in launchableApps }
        val selected = ScheduleManager.getBlockedPackages(this).toMutableSet()

        appListAdapter = AppListAdapter(
            packageManager, launchableApps, curatedApps, selected
        ) { pkg, checked ->
            if (!checked && !ScheduleManager.canLoosenProtection(this)) {
                selected.add(pkg)
                showLockedMessage(R.string.settings_locked)
                binding.recyclerApps.post { appListAdapter.notifyDataSetChanged() }
            } else {
                ScheduleManager.setBlockedPackages(this, selected)
            }
        }
        binding.recyclerApps.adapter = appListAdapter
    }

    private fun loadExistingSchedule() {
        val windows = ScheduleManager.getWindows(this)
        val w = windows.firstOrNull() ?: return
        binding.inputStartHour.setText(w.startHour.toString())
        binding.inputStartMinute.setText(w.startMinute.toString())
        binding.inputEndHour.setText(w.endHour.toString())
        binding.inputEndMinute.setText(w.endMinute.toString())
    }

    private fun saveSchedule() {
        if (!ScheduleManager.canEditSchedule(this)) {
            showLockedMessage(R.string.schedule_locked)
            return
        }
        val startHour = binding.inputStartHour.text.toString().toIntOrNull() ?: return
        val startMinute = binding.inputStartMinute.text.toString().toIntOrNull() ?: 0
        val endHour = binding.inputEndHour.text.toString().toIntOrNull() ?: return
        val endMinute = binding.inputEndMinute.text.toString().toIntOrNull() ?: 0

        val allDays = setOf(
            Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
        )

        val window = TimeWindow(allDays, startHour, startMinute, endHour, endMinute)
        if (!ScheduleManager.isValidWindow(window)) {
            showLockedMessage(R.string.schedule_invalid)
            return
        }
        // Második megerősítés: mentés után a tiltási idősávban már nem módosítható.
        val windowText = String.format(java.util.Locale.ROOT, "%02d:%02d–%02d:%02d", startHour, startMinute, endHour, endMinute)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.schedule_confirm_title)
            .setMessage(getString(R.string.schedule_confirm_message, windowText))
            .setPositiveButton(R.string.schedule_confirm_yes) { _, _ ->
                ScheduleManager.setWindows(this, listOf(window))
                binding.textSaveConfirmation.visibility = android.view.View.VISIBLE
                refreshLockState()
            }
            .setNegativeButton(R.string.schedule_confirm_no, null)
            .show()
    }

    // ---- Segítő ----

    private val smsPermissionCode = 7001

    private fun setupHelperCard() {
        binding.radioHelper.setOnCheckedChangeListener { _, id ->
            binding.inputHelperPhone.visibility =
                if (id == R.id.radioHelperYes) android.view.View.VISIBLE else android.view.View.GONE
        }
        val current = ScheduleManager.getPendingHelperPhone(this) ?: ScheduleManager.getHelperPhone(this)
        binding.inputHelperPhone.setText(current ?: "")
        binding.radioHelper.check(if (current != null) R.id.radioHelperYes else R.id.radioHelperNo)
        binding.buttonSaveHelper.setOnClickListener { saveHelper() }
    }

    private fun refreshHelperCard() {
        val editable = ScheduleManager.canEditHelper(this)
        val alpha = if (editable) 1f else 0.5f
        listOf<android.view.View>(
            binding.radioHelperYes, binding.radioHelperNo, binding.inputHelperPhone, binding.buttonSaveHelper
        ).forEach { it.isEnabled = editable; it.alpha = alpha }

        val active = ScheduleManager.getHelperPhone(this)
        val pending = ScheduleManager.getPendingHelperPhone(this)
        val lines = mutableListOf<String>()
        lines += if (active != null) getString(R.string.helper_status_active, active)
        else getString(R.string.helper_status_none)
        if (pending != null) {
            val effectiveAt = ScheduleManager.helperChangeEffectiveAt(this)
            lines += if (effectiveAt == 0L) getString(R.string.helper_status_pending, pending)
            else getString(
                R.string.helper_status_change, pending,
                java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                    .format(java.util.Date(effectiveAt))
            )
        }
        if (!editable) lines += getString(R.string.helper_locked)
        binding.textHelperStatus.text = lines.joinToString("\n")
    }

    private fun saveHelper() {
        if (!ScheduleManager.canEditHelper(this)) {
            showLockedMessage(R.string.helper_locked)
            return
        }
        if (binding.radioHelper.checkedRadioButtonId != R.id.radioHelperYes) {
            ScheduleManager.removeHelper(this)
            showLockedMessage(R.string.helper_removed)
            refreshHelperCard()
            return
        }
        val phone = binding.inputHelperPhone.text.toString().trim()
        if (phone.filter { it.isDigit() }.length < 8) {
            showLockedMessage(R.string.helper_phone_invalid)
            return
        }
        val needed = listOf(
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.READ_CALL_LOG
        ).filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            androidx.core.app.ActivityCompat.requestPermissions(this, needed.toTypedArray(), smsPermissionCode)
            return
        }
        val ok = ScheduleManager.requestHelper(this, phone)
        showLockedMessage(if (ok) R.string.helper_request_sent else R.string.helper_sms_failed)
        refreshHelperCard()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == smsPermissionCode &&
            grantResults.isNotEmpty() && grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }
        ) {
            saveHelper()
        }
    }
}
