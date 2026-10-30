package com.example.socialblock

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/** v1.5: a menüből megnyitható leírások (útmutató, működés, protokoll, adatvédelem, Kisegítő lehetőség). */
class InfoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TITLE = "title_res"
        const val EXTRA_BODY = "body_res"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val titleRes = intent.getIntExtra(EXTRA_TITLE, R.string.info_howto_title)
        val bodyRes = intent.getIntExtra(EXTRA_BODY, R.string.info_howto_body)
        fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(28))
        }
        content.addView(TextView(this).apply {
            text = getString(titleRes)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTypeface(typeface, Typeface.BOLD)
        })
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_neon)
            setPadding(dp(18), dp(14), dp(18), dp(18))
            elevation = dp(6).toFloat()
        }
        card.addView(TextView(this).apply {
            text = Html.fromHtml(getString(bodyRes), Html.FROM_HTML_MODE_COMPACT)
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setLineSpacing(0f, 1.25f)
            movementMethod = LinkMovementMethod.getInstance()
        })
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        content.addView(TextView(this).apply {
            text = getString(R.string.footer_copyright)
            setTextColor(Color.parseColor("#6B7280"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })

        val scroll = ScrollView(this).apply { addView(content) }
        val root = FrameLayout(this)
        root.addView(AnimatedBackgroundView(this))
        root.addView(scroll)
        setContentView(root)
    }
}
