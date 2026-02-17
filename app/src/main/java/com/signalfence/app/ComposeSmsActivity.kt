package com.signalfence.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ComposeSmsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Redirect SENDTO intents to Home (or ChatActivity if you want)
        val data: Uri? = intent?.data
        val address = data?.schemeSpecificPart ?: ""

        val i = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(ChatActivity.EXTRA_ADDRESS, address)
        }
        startActivity(i)
        finish()
    }
}
