package com.example.socialblock

import android.os.Bundle
import android.os.CountDownTimer
import androidx.appcompat.app.AppCompatActivity
import com.example.socialblock.databinding.ActivityUnlockFlowBinding

/**
 * Hosszú, szándékosan kellemetlen feloldási procedúra:
 * 1) Indoklás beírása (min. karakterszám)
 * 2) Kényszerített várakozás (FORCED_WAIT_MINUTES)
 * 3) Csak a várakozás letelte után válik aktívvá a végleges "Feloldás" gomb
 * 4) A feloldás csak UNLOCK_DURATION_MINUTES percre szól, utána automatikusan visszazár
 */
class UnlockFlowActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUnlockFlowBinding
    private var countDownTimer: CountDownTimer? = null

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
                binding.buttonFinalUnlock.isEnabled = s?.toString() == target
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

    override fun onDestroy() {
        countDownTimer?.cancel()
        super.onDestroy()
    }
}
