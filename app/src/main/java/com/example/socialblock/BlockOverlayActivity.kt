package com.example.socialblock

import android.content.Intent
import android.os.Bundle
import android.view.View
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
        val pm = packageManager

        val label = try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
        try {
            binding.imageBlockedApp.setImageDrawable(pm.getApplicationIcon(pkg))
        } catch (e: Exception) {
            binding.imageBlockedApp.visibility = View.GONE
        }
        binding.textBlockedApp.text = getString(R.string.blocked_message, label)

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

    override fun onResume() {
        super.onResume()

        // Ha közben sikeresen feloldottak, a blokkoló képernyő eltűnik,
        // és visszakerülünk az alatta lévő apphoz.
        if (ScheduleManager.isTemporarilyUnlocked(this)) {
            finish()
            return
        }

        val remaining = ScheduleManager.MAX_UNLOCKS_PER_WEEK - ScheduleManager.unlocksUsedThisWeek(this)
        binding.textUnlocksRemaining.text = getString(R.string.unlocks_remaining, remaining)
        binding.buttonRequestUnlock.isEnabled = ScheduleManager.canRequestUnlock(this)
    }

    // Letiltjuk a vissza gombot, hogy ne lehessen egyszerűen "átlapozni" a blokkolón
    override fun onBackPressed() {
        // szándékosan nem hívjuk a super-t
    }
}
