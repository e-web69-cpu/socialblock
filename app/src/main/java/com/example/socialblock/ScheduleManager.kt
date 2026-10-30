package com.example.socialblock

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Calendar

/** Egyetlen tiltási idősáv: napok + kezdő/záró óra:perc. */
data class TimeWindow(
    val daysOfWeek: Set<Int>, // Calendar.SUNDAY..Calendar.SATURDAY
    val startHour: Int, val startMinute: Int,
    val endHour: Int, val endMinute: Int
) {
    /** Az idősáv hossza percben (éjfélen átnyúló sávnál is helyes). */
    fun lengthMinutes(): Int {
        val s = startHour * 60 + startMinute
        val e = endHour * 60 + endMinute
        return (e - s + 1440) % 1440
    }
}

/**
 * SociaLock v1.3 – helyi logika:
 * kötelező idősáv, segítő (SMS-beleegyezéssel), feloldási állapotgép
 * (várakozás → PIN-SMS → hívásellenőrzés → kérdések → gépelés → PIN), visszavonás, heti 3 feloldás.
 */
object ScheduleManager {

    private const val PREFS = "socialblock_prefs"
    private const val KEY_WINDOWS = "windows"
    private const val KEY_BLOCKED_PACKAGES = "blocked_packages"
    private const val KEY_ACTIVE_UNLOCK_UNTIL = "active_unlock_until"
    private const val KEY_SIMPLE_ENABLED = "simple_enabled"
    private const val KEY_UNLOCK_DONE = "unlock_done_v13"

    // Segítő
    private const val KEY_HELPER_PHONE = "helper_phone_v13"
    private const val KEY_PENDING_PHONE = "helper_pending_phone"
    private const val KEY_PENDING_AT = "helper_pending_at"
    private const val KEY_PENDING_CONSENT_AT = "helper_pending_consent_at"

    // Feloldási kérés állapota
    private const val KEY_FLOW_STATE = "flow_state"
    private const val KEY_FLOW_REQUESTED_AT = "flow_requested_at"
    private const val KEY_FLOW_READY_AT = "flow_ready_at"
    private const val KEY_FLOW_PIN = "flow_pin"
    private const val KEY_FLOW_PIN_SENT_AT = "flow_pin_sent_at"
    private const val KEY_FLOW_CALL_OK = "flow_call_ok"
    private const val KEY_FLOW_PIN_FAILS = "flow_pin_fails"

    const val UNLOCK_DURATION_MINUTES = 15
    const val MAX_UNLOCKS_PER_WEEK = 3
    const val WAIT_MINUTES_DAY = 30
    const val WAIT_MINUTES_NIGHT = 60
    const val PIN_VALID_MINUTES = 15
    const val MIN_CALL_SECONDS = 90
    const val HELPER_CONSENT_HOURS = 48
    const val HELPER_CHANGE_DELAY_HOURS = 48
    const val MAX_WINDOW_MINUTES = 23 * 60
    const val MAX_PIN_FAILS = 3

    const val STATE_NONE = ""
    const val STATE_WAIT = "wait"
    const val STATE_PIN = "pin"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- Appok ----

    fun getBlockedPackages(ctx: Context): Set<String> {
        val p = prefs(ctx)
        if (!p.contains(KEY_BLOCKED_PACKAGES)) {
            val defaults = setOf(
                "com.facebook.katana", "com.instagram.android", "com.facebook.orca",
                "com.twitter.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
                "com.google.android.gm", "com.microsoft.office.outlook",
                "com.yahoo.mobile.client.android.mail", "com.samsung.android.email.provider"
            )
            setBlockedPackages(ctx, defaults)
            return defaults
        }
        val arr = JSONArray(p.getString(KEY_BLOCKED_PACKAGES, "[]") ?: "[]")
        return (0 until arr.length()).map { arr.getString(it) }.toSet()
    }

    fun setBlockedPackages(ctx: Context, packages: Set<String>) {
        val arr = JSONArray()
        packages.forEach { arr.put(it) }
        prefs(ctx).edit().putString(KEY_BLOCKED_PACKAGES, arr.toString()).apply()
    }

    // ---- Idősáv (kötelező) ----

    fun getWindows(ctx: Context): List<TimeWindow> {
        val arr = JSONArray(prefs(ctx).getString(KEY_WINDOWS, "[]") ?: "[]")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val daysArr = o.getJSONArray("days")
            TimeWindow(
                daysOfWeek = (0 until daysArr.length()).map { daysArr.getInt(it) }.toSet(),
                startHour = o.getInt("startHour"), startMinute = o.getInt("startMinute"),
                endHour = o.getInt("endHour"), endMinute = o.getInt("endMinute")
            )
        }
    }

    fun setWindows(ctx: Context, windows: List<TimeWindow>) {
        val arr = JSONArray()
        windows.forEach { w ->
            val o = JSONObject()
            val daysArr = JSONArray()
            w.daysOfWeek.forEach { daysArr.put(it) }
            o.put("days", daysArr)
            o.put("startHour", w.startHour); o.put("startMinute", w.startMinute)
            o.put("endHour", w.endHour); o.put("endMinute", w.endMinute)
            arr.put(o)
        }
        prefs(ctx).edit().putString(KEY_WINDOWS, arr.toString()).apply()
    }

    fun hasWindow(ctx: Context): Boolean = getWindows(ctx).isNotEmpty()

    /** Érvényes-e az idősáv: 1 perc és 23 óra között. */
    fun isValidWindow(w: TimeWindow): Boolean =
        w.startHour in 0..23 && w.endHour in 0..23 &&
            w.startMinute in 0..59 && w.endMinute in 0..59 &&
            w.lengthMinutes() in 1..MAX_WINDOW_MINUTES

    /** Az idő a tiltási idősávban van-e. Idősáv nélkül nincs tiltás. */
    fun isNowWithinSchedule(ctx: Context): Boolean {
        val windows = getWindows(ctx)
        if (windows.isEmpty()) return false
        val now = Calendar.getInstance()
        val day = now.get(Calendar.DAY_OF_WEEK)
        val prevDay = if (day == Calendar.SUNDAY) Calendar.SATURDAY else day - 1
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return windows.any { w ->
            val start = w.startHour * 60 + w.startMinute
            val end = w.endHour * 60 + w.endMinute
            if (start <= end) day in w.daysOfWeek && nowMinutes in start until end
            else (day in w.daysOfWeek && nowMinutes >= start) ||
                (prevDay in w.daysOfWeek && nowMinutes < end)
        }
    }

    // ---- Kapcsoló ----

    fun isSimpleBlockingEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_SIMPLE_ENABLED, false)

    fun setSimpleBlockingEnabled(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_SIMPLE_ENABLED, value).apply()
    }

    // ---- Ideiglenes feloldás ----

    fun isTemporarilyUnlocked(ctx: Context): Boolean =
        System.currentTimeMillis() < prefs(ctx).getLong(KEY_ACTIVE_UNLOCK_UNTIL, 0L)

    fun unlockedUntil(ctx: Context): Long = prefs(ctx).getLong(KEY_ACTIVE_UNLOCK_UNTIL, 0L)

    private fun grantTemporaryUnlock(ctx: Context) {
        val until = System.currentTimeMillis() + UNLOCK_DURATION_MINUTES * 60_000L
        prefs(ctx).edit().putLong(KEY_ACTIVE_UNLOCK_UNTIL, until).apply()
    }

    // ---- Zárolás ----

    /** Éppen érvényben van-e a tiltás (a feloldás alatt is). */
    fun isBlockingActiveNow(ctx: Context): Boolean =
        isSimpleBlockingEnabled(ctx) && isNowWithinSchedule(ctx)

    /** Az idősáv csak tiltási időn kívül módosítható (feloldás alatt sem). */
    fun canEditSchedule(ctx: Context): Boolean = !isBlockingActiveNow(ctx)

    /** A segítő csak tiltási időn kívül módosítható. */
    fun canEditHelper(ctx: Context): Boolean = !isBlockingActiveNow(ctx)

    /** Kapcsoló kikapcsolása, app kivétele: tiltási időn kívül, vagy egy végigvitt feloldás alatt. */
    fun canLoosenProtection(ctx: Context): Boolean =
        !isBlockingActiveNow(ctx) || isTemporarilyUnlocked(ctx)

    // ---- Heti korlát (csak a sikeres feloldások számítanak) ----

    private fun getDone(ctx: Context): MutableList<Long> {
        val arr = JSONArray(prefs(ctx).getString(KEY_UNLOCK_DONE, "[]") ?: "[]")
        return (0 until arr.length()).map { arr.getLong(it) }.toMutableList()
    }

    fun unlocksUsedThisWeek(ctx: Context): Int {
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        return getDone(ctx).count { it >= weekAgo }
    }

    fun canRequestUnlock(ctx: Context): Boolean = unlocksUsedThisWeek(ctx) < MAX_UNLOCKS_PER_WEEK

    // ---- Segítő ----

    fun getHelperPhone(ctx: Context): String? =
        prefs(ctx).getString(KEY_HELPER_PHONE, null)?.takeIf { it.isNotBlank() }

    fun hasHelper(ctx: Context): Boolean = getHelperPhone(ctx) != null

    fun getPendingHelperPhone(ctx: Context): String? =
        prefs(ctx).getString(KEY_PENDING_PHONE, null)?.takeIf { it.isNotBlank() }

    fun getPendingHelperAt(ctx: Context): Long = prefs(ctx).getLong(KEY_PENDING_AT, 0L)
    fun getPendingConsentAt(ctx: Context): Long = prefs(ctx).getLong(KEY_PENDING_CONSENT_AT, 0L)

    /** Új segítő jelölése: SMS-ben megkérdezzük, hogy beleegyezik-e. Csak tiltási időn kívül. */
    fun requestHelper(ctx: Context, phone: String): Boolean {
        if (!canEditHelper(ctx)) return false
        val ok = sendSms(
            ctx, phone,
            "SociaLock: arra kerlek, legyel a segitom. Ha feloldast kerek, kapsz egy kodot, " +
                "amit csak telefonbeszelgetes utan adj meg. Ha vallalod, ${HELPER_CONSENT_HOURS} oran belul valaszolj IGEN-nel."
        )
        if (ok) {
            prefs(ctx).edit()
                .putString(KEY_PENDING_PHONE, phone)
                .putLong(KEY_PENDING_AT, System.currentTimeMillis())
                .putLong(KEY_PENDING_CONSENT_AT, 0L)
                .apply()
        }
        return ok
    }

    /** „Nem kérek segítőt”: a segítő törlése (csak tiltási időn kívül). */
    fun removeHelper(ctx: Context): Boolean {
        if (!canEditHelper(ctx)) return false
        prefs(ctx).edit().remove(KEY_HELPER_PHONE).apply()
        clearPendingHelper(ctx)
        return true
    }

    fun clearPendingHelper(ctx: Context) {
        prefs(ctx).edit().remove(KEY_PENDING_PHONE).remove(KEY_PENDING_AT).remove(KEY_PENDING_CONSENT_AT).apply()
    }

    private fun digitsTail(phone: String): String = phone.filter { it.isDigit() }.takeLast(9)

    /**
     * A függő segítő frissítése:
     * - IGEN válasz keresése a beérkezett SMS-ek között (READ_SMS),
     * - 48 óra beleegyezés nélkül: a függő beállítás lejár,
     * - beleegyezés után: ha még nincs segítő, azonnal él; ha van, a csere 48 óra múlva lép életbe.
     */
    fun refreshHelper(ctx: Context): Boolean {
        val pending = getPendingHelperPhone(ctx) ?: return false
        val now = System.currentTimeMillis()
        var consentAt = getPendingConsentAt(ctx)
        if (consentAt == 0L && hasYesReply(ctx, digitsTail(pending), getPendingHelperAt(ctx))) {
            consentAt = now
            prefs(ctx).edit().putLong(KEY_PENDING_CONSENT_AT, now).apply()
        }
        if (consentAt == 0L) {
            if (now - getPendingHelperAt(ctx) > HELPER_CONSENT_HOURS * 3_600_000L) {
                clearPendingHelper(ctx)
                return true
            }
            return false
        }
        val effectiveAt = if (hasHelper(ctx)) consentAt + HELPER_CHANGE_DELAY_HOURS * 3_600_000L else consentAt
        if (now >= effectiveAt) {
            prefs(ctx).edit().putString(KEY_HELPER_PHONE, pending).apply()
            clearPendingHelper(ctx)
            return true
        }
        return false
    }

    fun helperChangeEffectiveAt(ctx: Context): Long {
        val c = getPendingConsentAt(ctx)
        return if (c == 0L) 0L else c + HELPER_CHANGE_DELAY_HOURS * 3_600_000L
    }

    private fun hasYesReply(ctx: Context, helperDigits: String, sinceMillis: Long): Boolean {
        if (helperDigits.isEmpty()) return false
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return false
        return try {
            var found = false
            ctx.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ?", arrayOf(sinceMillis.toString()),
                "${Telephony.Sms.DATE} DESC"
            )?.use { c ->
                val aIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val bIdx = c.getColumnIndex(Telephony.Sms.BODY)
                while (c.moveToNext()) {
                    val address = c.getString(aIdx) ?: continue
                    val body = c.getString(bIdx) ?: continue
                    if (address.filter { it.isDigit() }.endsWith(helperDigits) &&
                        body.trim().startsWith("IGEN", ignoreCase = true)
                    ) { found = true; break }
                }
            }
            found
        } catch (e: Exception) { false }
    }

    // ---- SMS ----

    fun sendSms(ctx: Context, phone: String, text: String): Boolean = try {
        @Suppress("DEPRECATION")
        val sm = SmsManager.getDefault()
        val parts = sm.divideMessage(text)
        sm.sendMultipartTextMessage(phone, null, parts, null, null)
        true
    } catch (e: Exception) { false }

    // ---- Feloldási kérés ----

    fun flowState(ctx: Context): String = prefs(ctx).getString(KEY_FLOW_STATE, STATE_NONE) ?: STATE_NONE
    fun flowReadyAt(ctx: Context): Long = prefs(ctx).getLong(KEY_FLOW_READY_AT, 0L)
    fun flowPinSentAt(ctx: Context): Long = prefs(ctx).getLong(KEY_FLOW_PIN_SENT_AT, 0L)
    fun flowCallOk(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_FLOW_CALL_OK, false)
    fun flowPinFails(ctx: Context): Int = prefs(ctx).getInt(KEY_FLOW_PIN_FAILS, 0)
    fun flowRequestedAt(ctx: Context): Long = prefs(ctx).getLong(KEY_FLOW_REQUESTED_AT, 0L)

    private fun isNightNow(): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return h >= 22 || h < 6
    }

    fun waitMinutesNow(): Int = if (isNightNow()) WAIT_MINUTES_NIGHT else WAIT_MINUTES_DAY

    fun startRequest(ctx: Context): Boolean {
        if (!hasHelper(ctx) || !canRequestUnlock(ctx) || flowState(ctx) != STATE_NONE) return false
        val now = System.currentTimeMillis()
        prefs(ctx).edit()
            .putString(KEY_FLOW_STATE, STATE_WAIT)
            .putLong(KEY_FLOW_REQUESTED_AT, now)
            .putLong(KEY_FLOW_READY_AT, now + waitMinutesNow() * 60_000L)
            .remove(KEY_FLOW_PIN).putLong(KEY_FLOW_PIN_SENT_AT, 0L)
            .putBoolean(KEY_FLOW_CALL_OK, false).putInt(KEY_FLOW_PIN_FAILS, 0)
            .apply()
        return true
    }

    /** A PIN-SMS elküldése a segítőnek (a várakozás végén). */
    fun sendPin(ctx: Context): Boolean {
        val phone = getHelperPhone(ctx) ?: return false
        val pin = (100000 + SecureRandom().nextInt(900000)).toString()
        val ok = sendSms(
            ctx, phone,
            "SociaLock: feloldasi kod: $pin. Csak telefonbeszelgetes utan add meg, ${PIN_VALID_MINUTES} percig ervenyes. " +
                "Ha nem beszeltetek, ne add meg."
        )
        if (!ok) return false
        prefs(ctx).edit()
            .putString(KEY_FLOW_STATE, STATE_PIN)
            .putString(KEY_FLOW_PIN, pin)
            .putLong(KEY_FLOW_PIN_SENT_AT, System.currentTimeMillis())
            .putBoolean(KEY_FLOW_CALL_OK, false).putInt(KEY_FLOW_PIN_FAILS, 0)
            .apply()
        return true
    }

    fun pinExpired(ctx: Context): Boolean =
        flowState(ctx) == STATE_PIN &&
            System.currentTimeMillis() > flowPinSentAt(ctx) + PIN_VALID_MINUTES * 60_000L

    /**
     * Volt-e a kérés indítása óta legalább 90 mp-es kapcsolt hívás a segítő számával
     * (kimenő vagy bejövő), a hívásnapló alapján.
     */
    fun checkCall(ctx: Context): Boolean {
        if (flowCallOk(ctx)) return true
        val phone = getHelperPhone(ctx) ?: return false
        val digits = digitsTail(phone)
        if (digits.isEmpty()) return false
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return false
        val ok = try {
            var found = false
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE, CallLog.Calls.DURATION),
                "${CallLog.Calls.DATE} >= ?", arrayOf(flowRequestedAt(ctx).toString()),
                "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                val nIdx = c.getColumnIndex(CallLog.Calls.NUMBER)
                val tIdx = c.getColumnIndex(CallLog.Calls.TYPE)
                val dIdx = c.getColumnIndex(CallLog.Calls.DURATION)
                while (c.moveToNext()) {
                    val number = c.getString(nIdx) ?: continue
                    val type = c.getInt(tIdx)
                    val duration = c.getLong(dIdx)
                    val connected = type == CallLog.Calls.INCOMING_TYPE || type == CallLog.Calls.OUTGOING_TYPE
                    if (connected && duration >= MIN_CALL_SECONDS &&
                        number.filter { it.isDigit() }.endsWith(digits)
                    ) { found = true; break }
                }
            }
            found
        } catch (e: Exception) { false }
        if (ok) prefs(ctx).edit().putBoolean(KEY_FLOW_CALL_OK, true).apply()
        return ok
    }

    /** A kérés visszavonása. Ha már ment PIN-SMS, a segítő második SMS-t kap, és a kód érvénytelen. */
    fun cancelRequest(ctx: Context) {
        if (flowState(ctx) == STATE_PIN && !pinExpired(ctx)) {
            getHelperPhone(ctx)?.let {
                sendSms(ctx, it, "SociaLock: a feloldasi kerest visszavontam, az elozo kod ervenytelen.")
            }
        }
        clearFlow(ctx)
    }

    fun clearFlow(ctx: Context) {
        prefs(ctx).edit()
            .putString(KEY_FLOW_STATE, STATE_NONE)
            .remove(KEY_FLOW_PIN).putLong(KEY_FLOW_PIN_SENT_AT, 0L)
            .putBoolean(KEY_FLOW_CALL_OK, false).putInt(KEY_FLOW_PIN_FAILS, 0)
            .putLong(KEY_FLOW_READY_AT, 0L)
            .apply()
    }

    /** PIN ellenőrzése. 3 hibás próba után a kérés zárol (törlődik). Visszatér: true, ha jó. */
    fun checkPin(ctx: Context, entered: String): Boolean {
        if (pinExpired(ctx)) { clearFlow(ctx); return false }
        val pin = prefs(ctx).getString(KEY_FLOW_PIN, null)
        if (pin != null && entered.trim() == pin) {
            val done = getDone(ctx)
            done.add(System.currentTimeMillis())
            val arr = JSONArray()
            done.forEach { arr.put(it) }
            prefs(ctx).edit().putString(KEY_UNLOCK_DONE, arr.toString()).apply()
            grantTemporaryUnlock(ctx)
            clearFlow(ctx)
            return true
        }
        val fails = flowPinFails(ctx) + 1
        if (fails >= MAX_PIN_FAILS) clearFlow(ctx)
        else prefs(ctx).edit().putInt(KEY_FLOW_PIN_FAILS, fails).apply()
        return false
    }
}
