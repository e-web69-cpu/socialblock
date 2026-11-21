package hu.eweb.hivasteszt

import android.telecom.Call
import android.telecom.CallScreeningService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Minden hívást átenged, csak feljegyzi a hívó számát. */
class ScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val incoming = details.callDirection == Call.Details.DIRECTION_INCOMING
        val number = details.handle?.schemeSpecificPart ?: "(rejtett)"
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        Log.add(this, "$time  ${if (incoming) "BEJÖVŐ" else "KIMENŐ"} hívás: $number")
        respondToCall(details, CallResponse.Builder().build())
    }
}
