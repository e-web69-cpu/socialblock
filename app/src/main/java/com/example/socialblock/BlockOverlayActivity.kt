package com.example.socialblock

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import java.util.Calendar
import java.util.Locale
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
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
            // A gyári app ikon, a saját (pl. fehér) hátterével együtt.
            binding.imageBlockedApp.setImageDrawable(pm.getApplicationIcon(pkg))
        } catch (e: Exception) {
            binding.imageBlockedApp.visibility = View.GONE
        }
        binding.textBlockedApp.text = getString(R.string.blocked_message, article(label), label, currentWindowText())

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

        val remaining = (ScheduleManager.MAX_UNLOCKS_PER_WEEK - ScheduleManager.unlocksUsedThisWeek(this)).coerceAtLeast(0)
        // A „3 feloldási” rész nagyobb, félkövér és fehér.
        val full = getString(R.string.unlocks_remaining, remaining)
        val key = "$remaining feloldási"
        val span = SpannableString(full)
        val start = full.indexOf(key)
        if (start >= 0) {
            val end = start + key.length
            span.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            span.setSpan(RelativeSizeSpan(1.15f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            span.setSpan(ForegroundColorSpan(Color.WHITE), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.textUnlocksRemaining.text = span
        // Segítő nélkül nincs feloldás; folyamatban lévő kérésnél a gomb a kérés képernyőjére visz.
        val inProgress = ScheduleManager.flowState(this) != ScheduleManager.STATE_NONE
        val hasHelper = ScheduleManager.hasHelper(this)
        binding.buttonRequestUnlock.text = when {
            !hasHelper -> getString(R.string.unlock_need_helper)
            inProgress -> getString(R.string.unlock_in_progress)
            else -> getString(R.string.request_unlock)
        }
        binding.buttonRequestUnlock.isEnabled = hasHelper && (inProgress || ScheduleManager.canRequestUnlock(this))
    }

    // Letiltjuk a vissza gombot, hogy ne lehessen egyszerűen "átlapozni" a blokkolón
    override fun onBackPressed() {
        // szándékosan nem hívjuk a super-t
    }

    /** Az éppen érvényes (vagy ha egyik sem, az első) beállított idősáv, pl. „18:00–02:30”. */
    private fun currentWindowText(): String {
        val windows = ScheduleManager.getWindows(this)
        if (windows.isEmpty()) return "–"
        val now = Calendar.getInstance()
        val day = now.get(Calendar.DAY_OF_WEEK)
        val prevDay = if (day == Calendar.SUNDAY) Calendar.SATURDAY else day - 1
        val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val active = windows.firstOrNull { w ->
            val start = w.startHour * 60 + w.startMinute
            val end = w.endHour * 60 + w.endMinute
            if (start <= end) day in w.daysOfWeek && nowMin in start until end
            else (day in w.daysOfWeek && nowMin >= start) || (prevDay in w.daysOfWeek && nowMin < end)
        } ?: windows.first()
        return String.format(
            Locale.ROOT, "%02d:%02d–%02d:%02d",
            active.startHour, active.startMinute, active.endHour, active.endMinute
        )
    }

    /** Magyar névelő az app neve elé: „A Messenger”, „Az X”, „Az Instagram”. */
    private fun article(name: String): String {
        val first = name.trim().firstOrNull() ?: return "A"
        val c = first.lowercaseChar()
        if (c in "aáeéiíoóöőuúüű") return "Az"
        // Betűszó vagy egyetlen betű: a betű neve magánhangzóval kezdődik (ef, el, em, en, er, es, iksz)
        val word = name.trim().takeWhile { !it.isWhitespace() }
        val isAcronym = word.length == 1 || (word.length <= 4 && word == word.uppercase())
        if (isAcronym && first.uppercaseChar() in "FLMNRSX") return "Az"
        if (first == '1' || first == '5') return "Az"
        return "A"
    }
}
