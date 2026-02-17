package com.signalfence.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Notify UI to refresh conversation list
        try {
            context.sendBroadcast(Intent(MessagesFragment.ACTION_SMS_UPDATED))
        } catch (_: Throwable) {}
    }
}
