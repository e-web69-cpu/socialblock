package com.example.socialblock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * v1.6: opcionális szinkron a sb.trendz.hu szerverrel.
 * Csak az állapot megy fel: idősáv, tiltott appok csomagnevei, AKTÍV/INAKTÍV (enabled).
 * A segítő száma és a feloldási események NEM kerülnek fel. Kulcs nélkül az app semmit nem küld.
 * Offline vagy hiba esetén a helyi szabályok érvényben maradnak (fail-closed).
 */
object SyncManager {
    private const val PREFS = "socialblock_sync"
    private const val BASE = "https://sb.trendz.hu/api.php"
    private const val K_KEY = "master_key"
    private const val K_HASH = "last_hash"
    private const val K_OK_AT = "last_ok_at"
    private const val K_TRY_AT = "last_try_at"
    private const val K_ERR = "last_err"
    private const val RETRY_MS = 5 * 60_000L
    private const val REFRESH_MS = 30 * 60_000L
    private const val MIN_GAP_MS = 2_000L

    @Volatile private var busy = false

    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasKey(ctx: Context): Boolean = !p(ctx).getString(K_KEY, null).isNullOrBlank()

    fun setKey(ctx: Context, key: String) {
        p(ctx).edit().putString(K_KEY, key.trim()).remove(K_HASH).remove(K_ERR).putLong(K_TRY_AT, 0L).apply()
    }

    fun hasConsent(ctx: Context): Boolean = p(ctx).getBoolean("consent", false)
    fun setConsent(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("consent", v).apply() }
    fun getReadKey(ctx: Context): String = p(ctx).getString("read_key", "") ?: ""
    fun setReadKey(ctx: Context, k: String) { p(ctx).edit().putString("read_key", k.trim()).apply() }

    fun clearKey(ctx: Context) {
        val rk = getReadKey(ctx); p(ctx).edit().clear().putString("read_key", rk).putBoolean("consent", false).apply()
    }

    // A szerver szigorúan HH:MM formátumot vár (két számjegyű óra).
    private fun fmt(h: Int, m: Int) = String.format(Locale.ROOT, "%02d:%02d", h, m)

    private fun buildBody(ctx: Context): String {
        val o = JSONObject()
        o.put("enabled", ScheduleManager.isSimpleBlockingEnabled(ctx))
        val windows = JSONArray()
        val w = ScheduleManager.getWindows(ctx).firstOrNull()
        if (w != null) {
            val days = JSONArray()
            for (d in 1..7) days.put(d) // 1 = vasárnap … 7 = szombat, minden nap
            val wo = JSONObject()
            wo.put("days", days)
            wo.put("start", fmt(w.startHour, w.startMinute))
            wo.put("end", fmt(w.endHour, w.endMinute))
            windows.put(wo)
        }
        o.put("windows", windows)
        val apps = JSONArray()
        ScheduleManager.getBlockedPackages(ctx).sorted().forEach { apps.put(it) }
        o.put("apps", apps)
        val sites = JSONArray()
        ScheduleManager.getBlockedSites(ctx).forEach { sites.put(it) }
        o.put("sites", sites)
        return o.toString()
    }

    /** Ha van kulcs és változott az állapot (vagy lejárt a frissítés), feltölti. Bármikor hívható, gyorsan visszatér. */
    fun syncIfNeeded(ctx: Context) {
        val key = p(ctx).getString(K_KEY, null)
        if (key.isNullOrBlank() || busy || !hasConsent(ctx)) return
        val body = buildBody(ctx)
        val now = System.currentTimeMillis()
        val pr = p(ctx)
        val changed = body != pr.getString(K_HASH, null)
        val sinceTry = now - pr.getLong(K_TRY_AT, 0L)
        val failedBefore = !pr.getString(K_ERR, null).isNullOrEmpty()
        if (!changed && sinceTry < REFRESH_MS) return
        if (changed && sinceTry < MIN_GAP_MS) return
        if (changed && failedBefore && sinceTry < RETRY_MS) return
        busy = true
        pr.edit().putLong(K_TRY_AT, now).apply()
        val app = ctx.applicationContext
        thread(name = "socialock-sync") {
            try {
                val c = URL(BASE + "?r=rules").openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.connectTimeout = 8000
                c.readTimeout = 8000
                c.doOutput = true
                c.setRequestProperty("X-Key", key)
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = c.responseCode
                c.disconnect()
                val e = p(app).edit()
                when (code) {
                    200 -> e.putString(K_HASH, body).putLong(K_OK_AT, System.currentTimeMillis()).remove(K_ERR)
                    401 -> e.putString(K_ERR, "Hibás kulcs (401)")
                    403 -> e.putString(K_ERR, "A kulcs nem módosíthat (403)")
                    423, 409 -> e.putString(K_ERR, "A szerver tiltási időben zárolva van ($code)")
                    400 -> e.putString(K_ERR, "A szerver elutasította az adatot (400)")
                    else -> e.putString(K_ERR, "Szerverhiba ($code)")
                }
                e.apply()
            } catch (ex: Exception) {
                p(app).edit().putString(K_ERR, "Nincs kapcsolat").apply()
            } finally {
                busy = false
            }
        }
    }

    fun statusText(ctx: Context): String {
        if (!hasKey(ctx)) return ctx.getString(R.string.sync_status_off)
        val pr = p(ctx)
        val err = pr.getString(K_ERR, null)
        val ok = pr.getLong(K_OK_AT, 0L)
        val okText = if (ok > 0) SimpleDateFormat("yyyy.MM.dd. HH:mm", Locale.ROOT).format(Date(ok)) else null
        return when {
            !err.isNullOrEmpty() && okText != null -> ctx.getString(R.string.sync_status_err_ok, err, okText)
            !err.isNullOrEmpty() -> ctx.getString(R.string.sync_status_err, err)
            okText != null -> ctx.getString(R.string.sync_status_ok, okText)
            else -> ctx.getString(R.string.sync_status_wait)
        }
    }
}
