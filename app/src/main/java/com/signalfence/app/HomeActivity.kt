package com.signalfence.app

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

class HomeActivity : AppCompatActivity(R.layout.activity_home) {

    private lateinit var tabMessages: LinearLayout
    private lateinit var tabDashboard: LinearLayout
    private lateinit var tabSettings: LinearLayout
    private lateinit var ivMessages: ImageView
    private lateinit var ivDashboard: ImageView
    private lateinit var ivSettings: ImageView
    private lateinit var tvMessages: TextView
    private lateinit var tvDashboard: TextView
    private lateinit var tvSettings: TextView

    private val TAG_MSG = "tab_messages"
    private val TAG_DASH = "tab_dashboard"
    private val TAG_SET = "tab_settings"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContextProvider.init(applicationContext)
        supportActionBar?.hide()
        NotificationHelper.ensureChannels(this)

        tabMessages = findViewById(R.id.tabMessages)
        tabDashboard = findViewById(R.id.tabDashboard)
        tabSettings = findViewById(R.id.tabSettings)
        ivMessages = findViewById(R.id.ivMessages)
        ivDashboard = findViewById(R.id.ivDashboard)
        ivSettings = findViewById(R.id.ivSettings)
        tvMessages = findViewById(R.id.tvMessages)
        tvDashboard = findViewById(R.id.tvDashboard)
        tvSettings = findViewById(R.id.tvSettings)

        if (savedInstanceState == null) {
            val msg = MessagesFragment()
            val dash = DashboardFragment()
            val set = SettingsFragment()

            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, msg, TAG_MSG)
                .add(R.id.fragmentContainer, dash, TAG_DASH)
                .add(R.id.fragmentContainer, set, TAG_SET)
                .hide(dash)
                .hide(set)
                .commit()

            selectTab(0)
        } else {
            val msg = supportFragmentManager.findFragmentByTag(TAG_MSG)
            val dash = supportFragmentManager.findFragmentByTag(TAG_DASH)
            val index = when {
                msg?.isVisible == true -> 0
                dash?.isVisible == true -> 1
                else -> 2
            }
            selectTab(index)
        }

        tabMessages.setOnClickListener { showTab(0) }
        tabDashboard.setOnClickListener { showTab(1) }
        tabSettings.setOnClickListener { showTab(2) }
    }

    override fun onResume() {
        super.onResume()
        if (!AccessManager.isAccessReady(this) && !SessionManager.isPermissionsSkipped(this)) {
            startActivity(Intent(this, RequestsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
        requestNotificationPermissionIfNeeded()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val granted = androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()
        if (!granted) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 99)
        }
    }

    private fun showTab(index: Int) {
        val fragments = arrayOf(
            supportFragmentManager.findFragmentByTag(TAG_MSG),
            supportFragmentManager.findFragmentByTag(TAG_DASH),
            supportFragmentManager.findFragmentByTag(TAG_SET)
        )
        supportFragmentManager.beginTransaction()
            .apply {
                fragments.forEachIndexed { i, f ->
                    if (f != null) { if (i == index) show(f) else hide(f) }
                }
            }
            .commit()
        selectTab(index)
    }

    private fun selectTab(index: Int) {
        val active = getColor(R.color.colorPrimary)
        val inactive = getColor(R.color.colorOnSurfaceVariant)

        ivMessages.setColorFilter(if (index == 0) active else inactive)
        tvMessages.setTextColor(if (index == 0) active else inactive)

        ivDashboard.setColorFilter(if (index == 1) active else inactive)
        tvDashboard.setTextColor(if (index == 1) active else inactive)

        ivSettings.setColorFilter(if (index == 2) active else inactive)
        tvSettings.setTextColor(if (index == 2) active else inactive)
    }
}
