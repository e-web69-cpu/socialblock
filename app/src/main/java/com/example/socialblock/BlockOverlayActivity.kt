package com.example.socialblock

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.socialblock.databinding.ActivityBlockOverlayBinding

class BlockOverlayActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
    }

    private lateinit var binding: ActivityBlockOverlayBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pkg = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE) ?: ""
        binding.textBlockedApp.text = getString(R.string.blocked_message, pkg)

        val used = ScheduleManager.unlocksUsedThisWeek(this)
        val remaining = ScheduleManager.MAX_UNLOCKS_PER_WEEK - used
        binding.textUnlocksRemaining.text =
            getString(R.string.unlocks_remaining, remaining)

        binding.buttonRequestUnlock.isEnabled = ScheduleManager.canRequestUnlock(this)
        binding.buttonRequestUnlock.setOnClickListener {
            startActivity(Intent(this, UnlockFlowActivity::class.java))
        }

        binding.buttonGoHome.setOnClickListener {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            finish()
        }
    }

    // Letiltjuk a vissza gombot, hogy ne lehessen egyszerűen "átlapozni" a blokkolón
    override fun onBackPressed() {
        // szándékosan nem hívjuk a super-t
    }
}
