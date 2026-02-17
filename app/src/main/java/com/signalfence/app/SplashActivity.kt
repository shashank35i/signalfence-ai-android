package com.signalfence.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        supportActionBar?.hide()
        super.onCreate(savedInstanceState)
        AppContextProvider.init(applicationContext)

        // ✅ Apply saved theme on startup
        SessionManager.applyTheme(SessionManager.isDarkMode(this))

        val next = if (!SessionManager.isFirstLaunchDone(this)) {
            Intent(this, OnboardingActivity::class.java)
        } else {
            Intent(this, HomeActivity::class.java)
        }

        next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(next)
        overridePendingTransition(0, 0)
        finish()
    }
}
