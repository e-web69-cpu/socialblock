package hu.eweb.hivasteszt

import android.content.Context

object Log {
    private fun p(c: Context) = c.getSharedPreferences("log", Context.MODE_PRIVATE)
    fun add(c: Context, line: String) {
        val old = p(c).getString("l", "") ?: ""
        p(c).edit().putString("l", (line + "\n" + old).take(4000)).apply()
    }
    fun get(c: Context): String = p(c).getString("l", "") ?: ""
    fun clear(c: Context) = p(c).edit().remove("l").apply()
}
