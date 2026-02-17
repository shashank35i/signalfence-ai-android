package com.signalfence.app

import android.app.Service
import android.content.Intent
import android.os.IBinder

class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Minimal: We just stop; your app already supports sending inside ChatActivity.
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
