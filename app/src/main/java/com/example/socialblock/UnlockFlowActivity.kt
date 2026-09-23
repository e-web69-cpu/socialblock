package com.example.socialblock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.Telephony
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.socialblock.databinding.ActivityUnlockFlowBinding

class UnlockFlowActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUnlockFlowBinding
    private var countDownTimer: CountDownTimer? = null
    private val pollHandler = Handler(Looper.getMainLooper())
    private var pollRunnable: Runnable? = null

    private val satisfiedMethods = mutableSetOf<String>()
    private var typingCorrect = false

    private val PERMISSION_REQUEST_CODE = 4321

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUnlockFlowBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pending = ScheduleManager.getPendingRequest(this)
        if (pending == null) {
            showJustificationStep()
        } else {
            showWaitingStep(pending.timestampRequested)
        }
    }

    private fun showJustificationStep() {
        binding.layoutJustification.visibility = android.view.View.VISIBLE
        binding.layoutWaiting.visibility = android.view.View.GONE

        binding.textMinLength.text = getString(
            R.string.min_justification_length, ScheduleManager.MIN_JUSTIFICATION_LENGTH
        )

        binding.buttonSubmitJustification.setOnClickListener {
            val text = binding.editJustification.text.toString().trim()
            if (text.length < ScheduleManager.MIN_JUSTIFICATION_LENGTH) {
                binding.editJustification.error = getString(
                    R.string.justification_too_short,
                    ScheduleManager.MIN_JUSTIFICATION_LENGTH - text.length
                )
                return@setOnClickListener
            }
            if (!ScheduleManager.canRequestUnlock(this)) {
                binding.textMinLength.text = getString(R.string.weekly_limit_reached)
                return@setOnClickListener
            }
            val requestedAt = ScheduleManager.recordUnlockRequest(this, text)
            showWaitingStep(requestedAt)
        }
    }

    private fun showWaitingStep(requestedAt: Long) {
        binding.layoutJustification.visibility = android.view.View.GONE
        binding.layoutWaiting.visibility = android.view.View.VISIBLE

        val waitMillis = ScheduleManager.FORCED_WAIT_MINUTES * 60_000L
        val readyAt = requestedAt + waitMillis
        val remaining = readyAt - System.currentTimeMillis()

        if (remaining <= 0) {
            showReadyToUnlock()
            return
        }

        binding.buttonFinalUnlock.isEnabled = false
        countDownTimer = object : CountDownTimer(remaining, 1000) {
            override fun onTick(msLeft: Long) {
                val minutes = (msLeft / 60000)
                val seconds = (msLeft / 1000) % 60
                binding.textCountdown.text = getString(
                    R.string.time_remaining_format, minutes, seconds
                )
            }

            override fun onFinish() {
                showReadyToUnlock()
            }
        }.start()
    }

    private fun showReadyToUnlock() {
        binding.textCountdown.text = getString(R.string.ready_to_unlock)
        showTypingChallenge()
        setupExternalApproval()
    }

    private val challengeSentences = listOf(
        "Tudatosan döntök úgy, hogy most, ebben a pillanatban feloldom a közösségi médiát.",
        "Higgadtan átgondoltam, és felelősséget vállalok azért, amit ez után az app után teszek.",
        "Ez a döntés az én józan, megfontolt választásom, nem egy pillanatnyi impulzus."
    )

    private fun showTypingChallenge() {
        binding.layoutTypingChallenge.visibility = android.view.View.VISIBLE
        binding.buttonFinalUnlock.isEnabled = false

        val target = challengeSentences.random()
        binding.textChallengeSentence.text = target

        binding.editChallengeInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                typingCorrect = s?.toString() == target
                updateFinalUnlockEnabled()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.buttonFinalUnlock.setOnClickListener {
            ScheduleManager.markLatestApproved(this)
            ScheduleManager.grantTemporaryUnlock(this)
            finish()
        }
    }

    private fun requiredMethods(): Set<String> =
        if (ScheduleManager.requiresExternalApproval(this)) ScheduleManager.getApprovalMethods(this)
        else emptySet()

    private fun updateFinalUnlockEnabled() {
        val required = requiredMethods()
        val approvalOk = required.isEmpty() || required.all { it in satisfiedMethods }
        binding.buttonFinalUnlock.isEnabled = typingCorrect && approvalOk
    }

    private fun setupExternalApproval() {
        val required = requiredMethods()
        if (required.isEmpty()) return

        binding.layoutExternalApproval.visibility = android.view.View.VISIBLE

        if ("sms" in required || "callback" in required) {
            requestReadPermissionsIfNeeded()
        }

        if ("sms" in required) setupSmsApproval()
        if ("callback" in required) setupCallbackApproval()
        if ("pin" in required) setupPinApproval()

        updateFinalUnlockEnabled()
    }

    private fun requestReadPermissionsIfNeeded() {
        val perms = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.READ_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.READ_CALL_LOG)
        }
        if (perms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    private fun setupSmsApproval() {
        binding.layoutSmsApproval.visibility = android.view.View.VISIBLE
        binding.buttonSendSms.setOnClickListener {
            val phone = ScheduleManager.getHelperPhone(this)
            if (phone.isNullOrBlank()) {
                binding.textSmsStatus.text = getString(R.string.helper_phone_hint)
                return@setOnClickListener
            }
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply {
                putExtra("sms_body", getString(R.string.sms_message_template))
            }
            try { startActivity(intent) } catch (e: Exception) { }
            ScheduleManager.markSmsApprovalRequested(this)
            binding.textSmsStatus.text = getString(R.string.sms_waiting)
            startPolling()
        }
    }

    private fun setupCallbackApproval() {
        binding.layoutCallbackApproval.visibility = android.view.View.VISIBLE
        binding.buttonRequestCallback.setOnClickListener {
            ScheduleManager.markCallbackRequested(this)
            binding.textCallbackStatus.text = getString(R.string.callback_waiting)
            startPolling()
        }
    }

    private fun setupPinApproval() {
        binding.layoutPinApproval.visibility = android.view.View.VISIBLE
        binding.buttonSubmitPin1.setOnClickListener {
            val entered = binding.editPin1.text.toString().trim()
            val expected = ScheduleManager.getManualPin1(this) ?: ""
            if (entered.isNotEmpty() && entered == expected) {
                binding.textPin1Status.text = getString(R.string.pin1_confirmed_waiting)
                binding.buttonSubmitPin1.isEnabled = false
                binding.editPin1.isEnabled = false
                startPin2Countdown()
            } else {
                binding.textPin1Status.text = getString(R.string.pin1_wrong)
            }
        }
    }

    private fun startPin2Countdown() {
        val waitMillis = 15 * 60_000L
        object : CountDownTimer(waitMillis, 1000) {
            override fun onTick(msLeft: Long) {
                val minutes = (msLeft / 60000)
                val seconds = (msLeft / 1000) % 60
                binding.textPin1Status.text = getString(
                    R.string.time_remaining_format, minutes, seconds
                )
            }
            override fun onFinish() {
                val pin2 = ScheduleManager.generatePin2(this@UnlockFlowActivity)
                binding.textPin2Ready.visibility = android.view.View.VISIBLE
                binding.editPin2.visibility = android.view.View.VISIBLE
                binding.buttonSubmitPin2.visibility = android.view.View.VISIBLE
                binding.buttonSubmitPin2.setOnClickListener {
                    val entered = binding.editPin2.text.toString().trim()
                    if (entered.isNotEmpty() && entered == pin2) {
                        satisfiedMethods.add("pin")
                        ScheduleManager.clearPin2(this@UnlockFlowActivity)
                        binding.textPin2Status.text = getString(R.string.pin2_confirmed)
                        binding.buttonSubmitPin2.isEnabled = false
                        binding.editPin2.isEnabled = false
                        updateFinalUnlockEnabled()
                    } else {
                        binding.textPin2Status.text = getString(R.string.pin2_wrong)
                    }
                }
            }
        }.start()
    }

    private fun startPolling() {
        pollRunnable?.let { pollHandler.removeCallbacks(it) }
        val runnable = object : Runnable {
            override fun run() {
                checkSmsAndCallbackApproval()
                pollHandler.postDelayed(this, 5000)
            }
        }
        pollRunnable = runnable
        pollHandler.post(runnable)
    }

    private fun checkSmsAndCallbackApproval() {
        val phone = ScheduleManager.getHelperPhone(this) ?: return
        val digits = phone.filter { it.isDigit() }.takeLast(9)
        if (digits.isEmpty()) return

        if ("sms" !in satisfiedMethods &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        ) {
            val since = ScheduleManager.getSmsRequestedAt(this)
            if (since > 0 && hasSmsApproval(digits, since)) {
                satisfiedMethods.add("sms")
                binding.textSmsStatus.text = getString(R.string.sms_approved)
                updateFinalUnlockEnabled()
            }
        }

        if ("callback" !in satisfiedMethods &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        ) {
            val since = ScheduleManager.getCallbackRequestedAt(this)
            if (since > 0 && hasIncomingCall(digits, since)) {
                satisfiedMethods.add("callback")
                binding.textCallbackStatus.text = getString(R.string.callback_approved)
                updateFinalUnlockEnabled()
            }
        }
    }

    private fun hasSmsApproval(helperDigits: String, sinceMillis: Long): Boolean {
        return try {
            val cursor = contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ?", arrayOf(sinceMillis.toString()),
                "${Telephony.Sms.DATE} DESC"
            )
            var found = false
            cursor?.use {
                val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
                while (it.moveToNext()) {
                    val address = it.getString(addressIdx) ?: continue
                    val body = it.getString(bodyIdx) ?: continue
                    if (address.filter { c -> c.isDigit() }.endsWith(helperDigits) &&
                        body.contains("IGEN", ignoreCase = true)
                    ) {
                        found = true
                        return@use
                    }
                }
            }
            found
        } catch (e: Exception) { false }
    }

    private fun hasIncomingCall(helperDigits: String, sinceMillis: Long): Boolean {
        return try {
            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE),
                "${CallLog.Calls.DATE} >= ?", arrayOf(sinceMillis.toString()),
                "${CallLog.Calls.DATE} DESC"
            )
            var found = false
            cursor?.use {
                val numberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                while (it.moveToNext()) {
                    val number = it.getString(numberIdx) ?: continue
                    val type = it.getInt(typeIdx)
                    if (type == CallLog.Calls.INCOMING_TYPE &&
                        number.filter { c -> c.isDigit() }.endsWith(helperDigits)
                    ) {
                        found = true
                        return@use
                    }
                }
            }
            found
        } catch (e: Exception) { false }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            startPolling()
        }
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        pollRunnable?.let { pollHandler.removeCallbacks(it) }
        super.onDestroy()
    }
}
