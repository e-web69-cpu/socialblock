package com.example.socialblock

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * SociaLock v1.3 feloldási folyamat (indoklás nélkül):
 * 1. kérés indítása → 2. várakozás (30/60 perc) → 3. PIN-SMS a segítőnek →
 * 4. legalább 90 mp-es hívás a segítővel → 5. megerősítő kérdések → 6. gépelési próba →
 * 7. PIN beírása → 15 perc feloldás. A kérés minden lépésben visszavonható.
 */
class UnlockFlowActivity : AppCompatActivity() {

    private lateinit var content: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private var ticker: Runnable? = null
    private var pinSendInProgress = false

    private var confirmSober = false
    private var confirmNoRegret = false
    private var typingOk = false
    private val challengeSentence by lazy { challengeSentences.random() }

    private val challengeSentences = listOf(
        "Tudatosan döntök úgy, hogy most, ebben a pillanatban feloldom a tiltást.",
        "Higgadtan átgondoltam, és vállalom a felelősséget azért, amit most írok.",
        "Ez a döntés az én józan, megfontolt választásom, nem egy pillanatnyi impulzus."
    )

    private val permissionRequestCode = 4321

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(24)
            setPadding(p, dp(28), p, p)
        }
        scroll.addView(content)
        // Élő, animált háttér a folyamat mögött is
        val root = android.widget.FrameLayout(this)
        root.addView(AnimatedBackgroundView(this))
        root.addView(scroll)
        setContentView(root)
        requestNeededPermissions()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onPause() {
        stopTicker()
        super.onPause()
    }

    private fun requestNeededPermissions() {
        val needed = listOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALL_LOG
        ).filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), permissionRequestCode)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode) render()
    }

    // ---- Megjelenítés ----

    private fun render() {
        stopTicker()
        content.removeAllViews()
        addTitle(getString(R.string.unlock_title))

        if (ScheduleManager.isTemporarilyUnlocked(this)) {
            addText(getString(R.string.unlock_already_active), R.color.accent)
            addPrimaryButton(getString(R.string.close)) { finish() }
            return
        }
        when (ScheduleManager.flowState(this)) {
            ScheduleManager.STATE_WAIT -> renderWait()
            ScheduleManager.STATE_PIN -> renderPin()
            else -> renderStart()
        }
    }

    private fun renderStart() {
        if (!ScheduleManager.hasHelper(this)) {
            addText(getString(R.string.unlock_no_helper), R.color.text_secondary)
            addPrimaryButton(getString(R.string.close)) { finish() }
            return
        }
        if (!ScheduleManager.canRequestUnlock(this)) {
            addText(getString(R.string.weekly_limit_reached), R.color.danger)
            addPrimaryButton(getString(R.string.close)) { finish() }
            return
        }
        addText(
            getString(R.string.unlock_intro, ScheduleManager.waitMinutesNow(), ScheduleManager.PIN_VALID_MINUTES),
            R.color.text_secondary
        )
        addText(
            getString(
                R.string.unlocks_remaining,
                ScheduleManager.MAX_UNLOCKS_PER_WEEK - ScheduleManager.unlocksUsedThisWeek(this)
            ),
            R.color.text_secondary
        )
        addPrimaryButton(getString(R.string.unlock_start)) {
            if (ScheduleManager.startRequest(this)) render()
        }
        addSecondaryButton(getString(R.string.close)) { finish() }
    }

    private fun renderWait() {
        val countdown = addText("", R.color.text_primary, sizeSp = 22f, bold = true)
        addText(getString(R.string.unlock_wait_info), R.color.text_secondary)
        val status = addText("", R.color.danger)
        addCancelButton()

        startTicker {
            val left = ScheduleManager.flowReadyAt(this) - System.currentTimeMillis()
            if (left > 0) {
                countdown.text = getString(R.string.unlock_wait_countdown, mmss(left))
            } else if (!pinSendInProgress) {
                pinSendInProgress = true
                countdown.text = getString(R.string.unlock_sending_pin)
                val ok = ScheduleManager.sendPin(this)
                pinSendInProgress = false
                if (ok) {
                    render()
                } else {
                    // Nincs PIN: a kérés újrakezdhető.
                    ScheduleManager.clearFlow(this)
                    status.text = getString(R.string.unlock_sms_failed)
                    stopTicker()
                    addPrimaryButton(getString(R.string.unlock_restart)) { render() }
                }
            }
        }
    }

    private fun renderPin() {
        if (ScheduleManager.pinExpired(this)) {
            ScheduleManager.clearFlow(this)
            addText(getString(R.string.unlock_pin_expired), R.color.danger)
            addPrimaryButton(getString(R.string.unlock_restart)) { render() }
            addSecondaryButton(getString(R.string.close)) { finish() }
            return
        }

        val pinCountdown = addText("", R.color.accent, sizeSp = 18f, bold = true)
        addText(getString(R.string.unlock_pin_sent), R.color.text_secondary)

        // 4. Hívás
        addStepTitle(getString(R.string.unlock_step_call))
        val callStatus = addText("", R.color.text_secondary)
        val confirmBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(confirmBox)

        // 5–7. Kérdések, gépelés, PIN (csak sikeres hívás után)
        fun buildRest() {
            confirmBox.removeAllViews()
            addStepTitle(getString(R.string.unlock_step_confirm), confirmBox)
            val cb1 = CheckBox(this).apply {
                text = getString(R.string.confirm_not_intoxicated)
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                isChecked = confirmSober
            }
            val cb2 = CheckBox(this).apply {
                text = getString(R.string.confirm_no_regret)
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                isChecked = confirmNoRegret
            }
            confirmBox.addView(cb1); confirmBox.addView(cb2)

            addStepTitle(getString(R.string.typing_challenge_prompt), confirmBox)
            addText(challengeSentence, R.color.accent, parent = confirmBox)
            val typing = addEdit(getString(R.string.unlock_typing_hint), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, confirmBox)

            addStepTitle(getString(R.string.unlock_step_pin), confirmBox)
            val pinEdit = addEdit(getString(R.string.pin_hint), InputType.TYPE_CLASS_NUMBER, confirmBox)
            val pinStatus = addText("", R.color.danger, parent = confirmBox)
            val unlockBtn = addPrimaryButton(getString(R.string.final_unlock), confirmBox) {}

            fun update() {
                confirmSober = cb1.isChecked
                confirmNoRegret = cb2.isChecked
                typingOk = typing.text.toString().trim() == challengeSentence
                val ready = confirmSober && confirmNoRegret && typingOk && pinEdit.text.length == 6
                unlockBtn.isEnabled = ready
                unlockBtn.alpha = if (ready) 1f else 0.5f
            }
            cb1.setOnCheckedChangeListener { _, _ -> update() }
            cb2.setOnCheckedChangeListener { _, _ -> update() }
            typing.addTextChangedListener(simpleWatcher { update() })
            pinEdit.addTextChangedListener(simpleWatcher { update() })
            update()

            unlockBtn.setOnClickListener {
                if (ScheduleManager.checkPin(this, pinEdit.text.toString())) {
                    finish()
                } else if (ScheduleManager.flowState(this) == ScheduleManager.STATE_NONE) {
                    render() // 3 hibás próba vagy lejárt kód: a kérés lezárult
                } else {
                    val left = ScheduleManager.MAX_PIN_FAILS - ScheduleManager.flowPinFails(this)
                    pinStatus.text = getString(R.string.pin_wrong_left, left)
                    pinEdit.setText("")
                }
            }
        }

        addCancelButton()

        var restBuilt = false
        startTicker {
            val left = ScheduleManager.flowPinSentAt(this) +
                ScheduleManager.PIN_VALID_MINUTES * 60_000L - System.currentTimeMillis()
            if (left <= 0) {
                render()
                return@startTicker
            }
            pinCountdown.text = getString(R.string.unlock_pin_valid, mmss(left))
            if (ScheduleManager.checkCall(this)) {
                callStatus.text = getString(R.string.unlock_call_ok)
                callStatus.setTextColor(ContextCompat.getColor(this, R.color.accent))
                if (!restBuilt) { restBuilt = true; buildRest() }
            } else {
                callStatus.text = getString(
                    R.string.unlock_call_waiting,
                    ScheduleManager.getHelperPhone(this) ?: "",
                    ScheduleManager.MIN_CALL_SECONDS
                )
            }
        }
    }

    private fun addCancelButton() {
        addSecondaryButton(getString(R.string.unlock_cancel)) {
            val afterPin = ScheduleManager.flowState(this) == ScheduleManager.STATE_PIN
            AlertDialog.Builder(this)
                .setTitle(R.string.unlock_cancel)
                .setMessage(if (afterPin) R.string.unlock_cancel_confirm_after_pin else R.string.unlock_cancel_confirm_before_pin)
                .setPositiveButton(R.string.unlock_cancel_yes) { _, _ ->
                    ScheduleManager.cancelRequest(this)
                    finish()
                }
                .setNegativeButton(R.string.unlock_cancel_no, null)
                .show()
        }
    }

    // ---- Időzítő ----

    private fun startTicker(tick: () -> Unit) {
        stopTicker()
        val r = object : Runnable {
            override fun run() {
                tick()
                if (ticker === this) handler.postDelayed(this, 1000)
            }
        }
        ticker = r
        handler.post(r)
    }

    private fun stopTicker() {
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
    }

    override fun onDestroy() {
        stopTicker()
        super.onDestroy()
    }

    // ---- Segédek ----

    private fun mmss(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        return String.format("%d:%02d", total / 60, total % 60)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun simpleWatcher(onChange: () -> Unit) = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) = onChange()
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }

    private fun addTitle(text: String) {
        addText(text, R.color.text_primary, sizeSp = 24f, bold = true)
    }

    private fun addStepTitle(text: String, parent: LinearLayout = content) {
        addText(text, R.color.text_primary, sizeSp = 16f, bold = true, parent = parent, topMargin = 20)
    }

    private fun addText(
        text: String,
        colorRes: Int,
        sizeSp: Float = 16f,
        bold: Boolean = false,
        parent: LinearLayout = content,
        topMargin: Int = 12
    ): TextView {
        val tv = TextView(this).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(ContextCompat.getColor(context, colorRes))
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setLineSpacing(0f, 1.2f)
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { this.topMargin = dp(topMargin) }
        parent.addView(tv, lp)
        return tv
    }

    private fun addEdit(hint: String, inputType: Int, parent: LinearLayout = content): EditText {
        val e = EditText(this).apply {
            this.hint = hint
            this.inputType = inputType
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setBackgroundResource(R.drawable.bg_search)
            val p = dp(14)
            setPadding(dp(16), p, dp(16), p)
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) }
        parent.addView(e, lp)
        return e
    }

    private fun addPrimaryButton(text: String, parent: LinearLayout = content, onClick: () -> Unit): Button =
        addButton(text, R.drawable.bg_button_gradient, R.color.text_primary, parent, onClick)

    private fun addSecondaryButton(text: String, parent: LinearLayout = content, onClick: () -> Unit): Button =
        addButton(text, R.drawable.bg_button_outline, R.color.text_secondary, parent, onClick)

    private fun addButton(text: String, bgRes: Int, colorRes: Int, parent: LinearLayout, onClick: () -> Unit): Button {
        val b = Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 16f
            gravity = Gravity.CENTER
            setBackgroundResource(bgRes)
            setTextColor(ContextCompat.getColor(context, colorRes))
            stateListAnimator = null
            minHeight = dp(52)
            setOnClickListener { onClick() }
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) }
        parent.addView(b, lp)
        return b
    }

    @Suppress("unused")
    private fun hide(v: View) { v.visibility = View.GONE }
}
