package com.optork.app

import android.telecom.Call
import android.telecom.CallScreeningService

class CallBlocker : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val raw = details.handle?.schemeSpecificPart.orEmpty()
        val num = raw.filter { it.isDigit() }
        val blockList = Prefs.blocked(this)
            .map { n -> n.filter { it.isDigit() } }
            .filter { it.isNotEmpty() }
        val hiddenBlocked = details.handle == null && Prefs.flag(this, "block_hidden")
        val listed = num.isNotEmpty() && blockList.any { num.endsWith(it) }
        val block = hiddenBlocked || listed
        val response = CallScreeningService.CallResponse.Builder()
            .setDisallowCall(block)
            .setRejectCall(block)
            .setSkipCallLog(block)
            .setSkipNotification(block)
            .build()
        respondToCall(details, response)
    }
}
