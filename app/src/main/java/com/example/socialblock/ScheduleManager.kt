package com.example.socialblock

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Egyetlen tiltási idősáv: napok + kezdő/záró óra:perc.
 */
data class TimeWindow(
    val daysOfWeek: Set<Int>, // Calendar.SUNDAY..Calendar.SATURDAY
    val startHour: Int, val startMinute: Int,
    val endHour: Int, val endMinute: Int
)

data class UnlockRequest(
    val timestampRequested: Long,
    val justification: String,
    val approvedAt: Long? = null,
    val expiresAt: Long? = null
)

object ScheduleManager {

    private const val PREFS = "socialblock_prefs"
    private const val KEY_WINDOWS = "windows"
    private const val KEY_BLOCKED_PACKAGES = "blocked_packages"
    private const val KEY_UNLOCK_HISTORY = "unlock_history"
    private const val KEY_ACTIVE_UNLOCK_UNTIL = "active_unlock_until"
    private const val KEY_REQUIRE_EXTERNAL_APPROVAL = "require_external_approval"

    const val FORCED_WAIT_MINUTES = 30
    const val UNLOCK_DURATION_MINUTES = 15
    const val MAX_UNLOCKS_PER_WEEK = 3
    const val MIN_JUSTIFICATION_LENGTH = 150

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

            fun getBlockedPackages(ctx: Context): Set<String> {
                            val p = prefs(ctx)
                                        if (!p.contains(KEY_BLOCKED_PACKAGES)) {
                                                            val defaults = setOf(
                                                                                                "com.facebook.katana",
                                                                            "com.instagram.android",
                                                                            "com.facebook.orca",
                                                                            "com.twitter.android",
                                                                            "com.zhiliaoapp.musically",
                                                                            "com.ss.android.ugc.trill"
                                                                                )
                                                                            setBlockedPackages(ctx, defaults)
                                                                                            return defaults
                                        }
                                                    val raw = p.getString(KEY_BLOCKED_PACKAGES, "[]") ?: "[]"
                            val arr = JSONArray(raw)
                                        return (0 until arr.length()).map { arr.getString(it) }.toSet()
            }

    fun setBlockedPackages(ctx: Context, packages: Set<String>) {
        val arr = JSONArray()
        packages.forEach { arr.put(it) }
        prefs(ctx).edit().putString(KEY_BLOCKED_PACKAGES, arr.toString()).apply()
    }

    fun getWindows(ctx: Context): List<TimeWindow> {
        val raw = prefs(ctx).getString(KEY_WINDOWS, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val daysArr = o.getJSONArray("days")
            val days = (0 until daysArr.length()).map { daysArr.getInt(it) }.toSet()
            TimeWindow(
                daysOfWeek = days,
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

    /** Van-e most aktív, ideiglenes feloldás. */
    fun isTemporarilyUnlocked(ctx: Context): Boolean {
        val until = prefs(ctx).getLong(KEY_ACTIVE_UNLOCK_UNTIL, 0L)
        return System.currentTimeMillis() < until
    }

    fun grantTemporaryUnlock(ctx: Context) {
        val until = System.currentTimeMillis() + UNLOCK_DURATION_MINUTES * 60_000L
        prefs(ctx).edit().putLong(KEY_ACTIVE_UNLOCK_UNTIL, until).apply()
    }

    /** Most, ebben a pillanatban tiltási idősávban vagyunk-e. */
    fun isInBlockedWindowNow(ctx: Context): Boolean {
        if (isTemporarilyUnlocked(ctx)) return false
        val now = Calendar.getInstance()
        val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return getWindows(ctx).any { w ->
            if (dayOfWeek !in w.daysOfWeek) return@any false
            val startMinutes = w.startHour * 60 + w.startMinute
            val endMinutes = w.endHour * 60 + w.endMinute
            if (startMinutes <= endMinutes) {
                nowMinutes in startMinutes until endMinutes
            } else {
                // átnyúlik éjfélen (pl. 22:00 - 07:00)
                nowMinutes >= startMinutes || nowMinutes < endMinutes
            }
        }
    }

    // ---- Feloldási kérések naplózása és heti limit ----

    private fun getHistory(ctx: Context): MutableList<UnlockRequest> {
        val raw = prefs(ctx).getString(KEY_UNLOCK_HISTORY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<UnlockRequest>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                UnlockRequest(
                    timestampRequested = o.getLong("requested"),
                    justification = o.getString("justification"),
                    approvedAt = if (o.has("approvedAt")) o.getLong("approvedAt") else null,
                    expiresAt = if (o.has("expiresAt")) o.getLong("expiresAt") else null
                )
            )
        }
        return list
    }

    private fun saveHistory(ctx: Context, list: List<UnlockRequest>) {
        val arr = JSONArray()
        list.forEach { r ->
            val o = JSONObject()
            o.put("requested", r.timestampRequested)
            o.put("justification", r.justification)
            r.approvedAt?.let { o.put("approvedAt", it) }
            r.expiresAt?.let { o.put("expiresAt", it) }
            arr.put(o)
        }
        prefs(ctx).edit().putString(KEY_UNLOCK_HISTORY, arr.toString()).apply()
    }

    fun unlocksUsedThisWeek(ctx: Context): Int {
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        return getHistory(ctx).count { it.timestampRequested >= weekAgo }
    }

    fun canRequestUnlock(ctx: Context): Boolean =
        unlocksUsedThisWeek(ctx) < MAX_UNLOCKS_PER_WEEK

    fun recordUnlockRequest(ctx: Context, justification: String): Long {
        val history = getHistory(ctx)
        val now = System.currentTimeMillis()
        history.add(UnlockRequest(timestampRequested = now, justification = justification))
        saveHistory(ctx, history)
        return now
    }

    /** A legutóbbi kérés, ha még nincs feldolgozva (nincs approvedAt). */
    fun getPendingRequest(ctx: Context): UnlockRequest? =
        getHistory(ctx).lastOrNull { it.approvedAt == null }

    fun markLatestApproved(ctx: Context) {
        val history = getHistory(ctx)
        val idx = history.indexOfLast { it.approvedAt == null }
        if (idx == -1) return
        val old = history[idx]
        history[idx] = old.copy(
            approvedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + UNLOCK_DURATION_MINUTES * 60_000L
        )
        saveHistory(ctx, history)
    }

    fun requiresExternalApproval(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_REQUIRE_EXTERNAL_APPROVAL, false)

    fun setRequiresExternalApproval(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_REQUIRE_EXTERNAL_APPROVAL, value).apply()
    }

        private const val KEY_SIMPLE_ENABLED = "simple_enabled"
        private const val KEY_SIMPLE_PACKAGE = "simple_blocked_package"

        fun isSimpleBlockingEnabled(ctx: Context): Boolean =
            prefs(ctx).getBoolean(KEY_SIMPLE_ENABLED, false)

                fun setSimpleBlockingEnabled(ctx: Context, value: Boolean) {
                            prefs(ctx).edit().putBoolean(KEY_SIMPLE_ENABLED, value).apply()
                }

                    fun getSimpleBlockedPackage(ctx: Context): String? =
            prefs(ctx).getString(KEY_SIMPLE_PACKAGE, null)

                fun setSimpleBlockedPackage(ctx: Context, pkg: String?) {
                            prefs(ctx).edit().putString(KEY_SIMPLE_PACKAGE, pkg).apply()
                }

                    fun isNowWithinSchedule(ctx: Context): Boolean {
                                val windows = getWindows(ctx)
                                        if (windows.isEmpty()) return true
                                val now = Calendar.getInstance()
                                        val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
                                                val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                                                        return windows.any { w ->
                                                                        if (dayOfWeek !in w.daysOfWeek) return@any false
                                                                        val start = w.startHour * 60 + w.startMinute
                                                                        val end = w.endHour * 60 + w.endMinute
                                                                        if (start <= end) nowMinutes in start until end
                                                                        else nowMinutes >= start || nowMinutes < end
                                                        }
                    }

                        private const val KEY_APPROVAL_METHODS = "approval_methods"
        private const val KEY_HELPER_PHONE = "helper_phone"
        private const val KEY_MANUAL_PIN1 = "manual_pin1"
        private const val KEY_PIN2_VALUE = "pin2_value"
        private const val KEY_PIN2_GENERATED_AT = "pin2_generated_at"
        private const val KEY_SMS_REQUESTED_AT = "sms_requested_at"
        private const val KEY_CALLBACK_REQUESTED_AT = "callback_requested_at"

        fun getApprovalMethods(ctx: Context): Set<String> {
                    val raw = prefs(ctx).getString(KEY_APPROVAL_METHODS, "") ?: ""
                    return raw.split(",").filter { it.isNotBlank() }.toSet()
        }

            fun setApprovalMethods(ctx: Context, methods: Set<String>) {
                        prefs(ctx).edit().putString(KEY_APPROVAL_METHODS, methods.joinToString(",")).apply()
            }

                fun getHelperPhone(ctx: Context): String? = prefs(ctx).getString(KEY_HELPER_PHONE, null)

                    fun setHelperPhone(ctx: Context, phone: String) {
                                prefs(ctx).edit().putString(KEY_HELPER_PHONE, phone).apply()
                    }

                        fun getManualPin1(ctx: Context): String? = prefs(ctx).getString(KEY_MANUAL_PIN1, null)

                            fun setManualPin1(ctx: Context, pin: String) {
                                        prefs(ctx).edit().putString(KEY_MANUAL_PIN1, pin).apply()
                            }

                                fun generatePin2(ctx: Context): String {
                                            val pin = (100000..999999).random().toString()
                                                    prefs(ctx).edit()
                                                                .putString(KEY_PIN2_VALUE, pin)
                                                                            .putLong(KEY_PIN2_GENERATED_AT, System.currentTimeMillis())
                                                                                        .apply()
                                                                                                return pin
                                }

                                    fun getPin2(ctx: Context): String? = prefs(ctx).getString(KEY_PIN2_VALUE, null)

                                        fun clearPin2(ctx: Context) {
                                                    prefs(ctx).edit().remove(KEY_PIN2_VALUE).remove(KEY_PIN2_GENERATED_AT).apply()
                                        }

                                            fun markSmsApprovalRequested(ctx: Context) {
                                                        prefs(ctx).edit().putLong(KEY_SMS_REQUESTED_AT, System.currentTimeMillis()).apply()
                                            }

                                                fun getSmsRequestedAt(ctx: Context): Long = prefs(ctx).getLong(KEY_SMS_REQUESTED_AT, 0L)

                                                    fun markCallbackRequested(ctx: Context) {
                                                                prefs(ctx).edit().putLong(KEY_CALLBACK_REQUESTED_AT, System.currentTimeMillis()).apply()
                                                    }

                                                        fun getCallbackRequestedAt(ctx: Context): Long = prefs(ctx).getLong(KEY_CALLBACK_REQUESTED_AT, 0L)
}
