package hu.eweb.hivasteszt

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hívás-teszt: látja-e az app (hívásszűrő szerep + névjegy-engedély mellett)
 * a névjegyzékben szereplő hívó számát, és méri-e a felvett hívás hosszát.
 */
class MainActivity : ComponentActivity() {

    private lateinit var status: TextView
    private lateinit var log: TextView
    private var ringingAt = 0L
    private var answeredAt = 0L
    private val handler = Handler(Looper.getMainLooper())

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        startCallStateListener(); refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = (20 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(p, p * 2, p, p) }
        fun text(s: String, size: Float = 16f) = TextView(this).apply { text = s; textSize = size; setPadding(0, p / 2, 0, p / 2) }
        col.addView(text("Hívás-teszt", 26f))
        col.addView(text(
            "1. Nyomd meg a két gombot, és engedélyezz mindent.\n" +
            "2. Hívjon fel valaki, aki a névjegyeid között van.\n" +
            "3. Vedd fel, beszéljetek kicsit, tegyétek le.\n" +
            "4. Gyere vissza ide: lent látszik, mit látott az app."
        ))
        col.addView(Button(this).apply { text = "1. Engedélyek (névjegyek, telefon)"; setOnClickListener {
            permLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_PHONE_STATE))
        } })
        col.addView(Button(this).apply { text = "2. Hívásszűrő szerep"; setOnClickListener { requestRole() } })
        col.addView(Button(this).apply { text = "Napló törlése"; setOnClickListener { Log.clear(this@MainActivity); refresh() } })
        status = text("", 16f); log = text("", 16f)
        col.addView(status); col.addView(log)
        setContentView(ScrollView(this).apply { addView(col) })
        startCallStateListener()
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun requestRole() {
        if (Build.VERSION.SDK_INT < 29) { status.text = "Android 10 alatt nincs hívásszűrő szerep."; return }
        val rm = getSystemService(RoleManager::class.java)
        if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) { status.text = "A szerep nem elérhető ezen a telefonon."; return }
        roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun refresh() {
        val role = if (Build.VERSION.SDK_INT >= 29)
            getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING) else false
        status.text = "Névjegyek: ${if (granted(Manifest.permission.READ_CONTACTS)) "✓" else "✗"}   " +
            "Telefon: ${if (granted(Manifest.permission.READ_PHONE_STATE)) "✓" else "✗"}   " +
            "Hívásszűrő: ${if (role) "✓" else "✗"}"
        log.text = "Napló:\n" + Log.get(this).ifEmpty { "(még semmi)" }
    }

    private var listening = false
    private fun startCallStateListener() {
        if (listening || !granted(Manifest.permission.READ_PHONE_STATE)) return
        val tm = getSystemService(TelephonyManager::class.java)
        if (Build.VERSION.SDK_INT >= 31) {
            tm.registerTelephonyCallback(mainExecutor, object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = onState(state)
            })
        } else {
            @Suppress("DEPRECATION")
            tm.listen(object : PhoneStateListener() {
                @Deprecated("") override fun onCallStateChanged(state: Int, n: String?) = onState(state)
            }, PhoneStateListener.LISTEN_CALL_STATE)
        }
        listening = true
    }

    private fun now() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    private fun onState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> { ringingAt = System.currentTimeMillis(); answeredAt = 0 }
            TelephonyManager.CALL_STATE_OFFHOOK -> if (ringingAt > 0 && answeredAt == 0L) {
                answeredAt = System.currentTimeMillis(); Log.add(this, "${now()}  felvéve")
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (answeredAt > 0) Log.add(this, "${now()}  vége, beszélgetés: ${(System.currentTimeMillis() - answeredAt) / 1000} mp")
                ringingAt = 0; answeredAt = 0
            }
        }
        handler.post { refresh() }
    }
}
