package com.signalfence.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("SignalFenceMms", "WAP_PUSH_DELIVER received: ${intent.action}")
        try {
            context.sendBroadcast(Intent(MessagesFragment.ACTION_SMS_UPDATED))
        } catch (_: Throwable) {}
    }
}
