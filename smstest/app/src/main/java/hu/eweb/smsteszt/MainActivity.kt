package hu.eweb.smsteszt

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status

/**
 * SMS-teszt: kipróbálja, hogy az SMS User Consent API átadja-e
 * egy névjegyzékben szereplő szám SMS-ét (engedély nélkül).
 */
class MainActivity : ComponentActivity() {

    private lateinit var numberEdit: EditText
    private lateinit var status: TextView
    private lateinit var result: TextView
    private var timer: CountDownTimer? = null
    private var registered = false

    private val consentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == Activity.RESULT_OK) {
                val msg = res.data?.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE) ?: "(üres)"
                stopTimer()
                status.text = "SIKER ✅ Az SMS átjött."
                result.text = "Az üzenet szövege:\n$msg"
            } else {
                stopTimer()
                status.text = "Az ablak feljött, de nem engedélyezted. (Ez is siker: az SMS átjött.)"
            }
        }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != SmsRetriever.SMS_RETRIEVED_ACTION) return
            val extras = intent.extras ?: return
            val st = if (Build.VERSION.SDK_INT >= 33)
                extras.getParcelable(SmsRetriever.EXTRA_STATUS, Status::class.java)
            else @Suppress("DEPRECATION") extras.get(SmsRetriever.EXTRA_STATUS) as? Status
            when (st?.statusCode) {
                CommonStatusCodes.SUCCESS -> {
                    val consent: Intent? = if (Build.VERSION.SDK_INT >= 33)
                        extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT, Intent::class.java)
                    else @Suppress("DEPRECATION") extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT)
                    val pkg = consent?.resolveActivity(packageManager)?.packageName
                    if (consent != null && pkg == "com.google.android.gms") {
                        status.text = "SMS érkezett, engedélykérő ablak…"
                        consentLauncher.launch(consent)
                    }
                }
                CommonStatusCodes.TIMEOUT -> {
                    stopTimer()
                    status.text = "LEJÁRT ❌ 5 perc alatt nem jött megfelelő SMS."
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = (20 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(p, p * 2, p, p) }
        fun text(s: String, size: Float = 16f) = TextView(this).apply { text = s; textSize = size; setPadding(0, p / 2, 0, p / 2) }

        col.addView(text("SMS-teszt", 26f))
        col.addView(text(
            "1. Írd be annak a telefonszámát, aki a névjegyeid között van (pl. +36301234567).\n" +
            "2. Nyomd meg a Figyelés indítása gombot.\n" +
            "3. 5 percen belül küldjön neked egy SMS-t, amiben van egy szám, pl.: IGEN 4821\n" +
            "4. Ha feljön egy ablak, és megjelenik az üzenet: működik."
        ))
        numberEdit = EditText(this).apply {
            hint = "+36301234567"
            inputType = InputType.TYPE_CLASS_PHONE
        }
        col.addView(numberEdit)
        col.addView(Button(this).apply {
            text = "Figyelés indítása (ettől a számtól)"
            setOnClickListener { start(numberEdit.text.toString().trim().ifEmpty { null }) }
        })
        col.addView(Button(this).apply {
            text = "Figyelés indítása (bármilyen számtól)"
            setOnClickListener { start(null) }
        })
        status = text("", 18f)
        result = text("")
        col.addView(status)
        col.addView(result)
        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun start(number: String?) {
        if (!registered) {
            ContextCompat.registerReceiver(
                this, receiver, IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION),
                SmsRetriever.SEND_PERMISSION, null, ContextCompat.RECEIVER_EXPORTED
            )
            registered = true
        }
        result.text = ""
        SmsRetriever.getClient(this).startSmsUserConsent(number)
            .addOnSuccessListener {
                val who = number ?: "bármilyen számtól"
                stopTimer()
                timer = object : CountDownTimer(5 * 60_000L, 1000) {
                    override fun onTick(ms: Long) {
                        val s = ms / 1000
                        status.text = "Figyelés ($who): még ${s / 60}:${String.format("%02d", s % 60)}\nMost küldjék az SMS-t."
                    }
                    override fun onFinish() {}
                }.start()
            }
            .addOnFailureListener { e -> status.text = "Nem indult el: ${e.message}" }
    }

    private fun stopTimer() { timer?.cancel(); timer = null }

    override fun onDestroy() {
        stopTimer()
        if (registered) unregisterReceiver(receiver)
        super.onDestroy()
    }
}
