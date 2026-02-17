package com.signalfence.app

import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton

class RequestsActivity : AppCompatActivity() {

    private val TAG = "SignalFenceRequests"

    private lateinit var tvSkip: TextView
    private lateinit var btnAllow: AppCompatButton
    private lateinit var tvNotNow: TextView

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            log("permLauncher", result.toString())
            continueFlow("after_permissions")
        }

    private val defaultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            log("defaultLauncher return", "resultCode=${res.resultCode}")
            logStatus("after_default_screen_return")

            // ✅ If system canceled or refused, open Default Apps settings so user can set manually
            if (!AccessManager.isDefaultSmsApp(this)) {
                log("fallback", "Still not default -> open default apps settings")
                openDefaultAppsSettings()
            }

            continueFlow("after_default")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.ensureChannels(this)
        setContentView(R.layout.activity_allow_message_access)
        supportActionBar?.hide()

        tvSkip = findViewById(R.id.tvSkip)
        btnAllow = findViewById(R.id.btnAllow)
        tvNotNow = findViewById(R.id.tvNotNow)

        tvSkip.setOnClickListener { goHomeAnyway() }
        tvNotNow.setOnClickListener { goHomeAnyway() }

        btnAllow.setOnClickListener {
            log("[tap] Allow", "")
            SessionManager.setPermissionsSkipped(this, false) // reset if they try again
            startFlow()
        }

        // long press debug
        btnAllow.setOnLongClickListener {
            showDebugDialog()
            true
        }

        logStatus("onCreate")
    }

    override fun onResume() {
        super.onResume()
        logStatus("onResume")

        updateButtonUi()

        if (AccessManager.isAccessReady(this)) {
            log("ready", "Access ready -> goHome()")
            goHome()
            return
        }
    }

    private fun updateButtonUi() {
        if (AccessManager.isAccessReady(this)) {
            btnAllow.text = "CONTINUE TO DASHBOARD"
        } else if (AccessManager.hasSmsPermissions(this)) {
            btnAllow.text = "SET AS DEFAULT SMS APP"
        } else {
            btnAllow.text = "ACTIVATE PROTECTION"
        }
    }

    private fun startFlow() {
        logStatus("startFlow")

        if (!AccessManager.hasSmsPermissions(this)) {
            log("step", "Requesting permissions")
            permLauncher.launch(AccessManager.requiredSmsPermissions())
            return
        }

        if (!AccessManager.isDefaultSmsApp(this)) {
            log("step", "Requesting Default SMS role/app")
            requestDefaultSms()
            return
        }

        SessionManager.setPermissionsSkipped(this, false) // reset if success
        goHome()
    }

    private fun continueFlow(source: String) {
        log("continueFlow", source)
        logStatus("continueFlow:$source")

        if (!AccessManager.hasSmsPermissions(this)) {
            toast("Please allow SMS permissions")
            // No auto-redirect here to avoid annoyance
            updateButtonUi()
            return
        }

        if (!AccessManager.isDefaultSmsApp(this)) {
            toast("Please set SignalFence as Default SMS app")
            log("blocked", "not default SMS yet")
            updateButtonUi()
            return
        }

        goHome()
    }

    private fun requestDefaultSms() {
        try {
            // Android 10+ RoleManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rm = getSystemService(RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_SMS)) {
                    log("role", "available=true held=${rm.isRoleHeld(RoleManager.ROLE_SMS)}")
                    val i = rm.createRequestRoleIntent(RoleManager.ROLE_SMS)
                    defaultLauncher.launch(i)
                    return
                } else {
                    log("role", "RoleManager null or role not available -> fallback")
                }
            }

            // Fallback older devices
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            }
            defaultLauncher.launch(intent)

        } catch (e: Throwable) {
            log("error", "requestDefaultSms failed: ${e.message}")
            openDefaultAppsSettings()
        }
    }

    private fun openDefaultAppsSettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (_: Throwable) {
            openAppSettings()
        }
    }

    private fun openAppSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            )
        } catch (_: Throwable) {}
    }

    private fun goHome() {
        log("nav", "HomeActivity")
        SessionManager.setFirstLaunchDone(this)
        startActivity(Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun goHomeAnyway() {
        SessionManager.setFirstLaunchDone(this)
        SessionManager.setPermissionsSkipped(this, true) // ✅ mark as skipped
        startActivity(Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun logStatus(where: String) {
        val hasPerm = AccessManager.hasSmsPermissions(this)
        val defPkg = try { Telephony.Sms.getDefaultSmsPackage(this) } catch (_: Throwable) { "?" }
        val isDef = defPkg == packageName

        val hasTelephony = packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

        val tm: TelephonyManager? = try { getSystemService(TelephonyManager::class.java) } catch (_: Throwable) { null }
        val smsCapable = try {
            if (Build.VERSION.SDK_INT >= 22) tm?.isSmsCapable == true else hasTelephony
        } catch (_: Throwable) { hasTelephony }

        log(
            "status:$where",
            "hasPerm=$hasPerm isDefault=$isDef defaultPkg=$defPkg myPkg=$packageName telephony=$hasTelephony smsCapable=$smsCapable"
        )
    }

    private fun showDebugDialog() {
        val hasPerm = AccessManager.hasSmsPermissions(this)
        val defPkg = try { Telephony.Sms.getDefaultSmsPackage(this) } catch (_: Throwable) { "?" }
        val isDef = defPkg == packageName

        AlertDialog.Builder(this)
            .setTitle("Debug")
            .setMessage(
                "Permissions: $hasPerm\n" +
                        "Default SMS: $isDef\n" +
                        "Default pkg: $defPkg\n" +
                        "My pkg: $packageName\n\n" +
                        "If Default pkg is null or Default SMS stays false, open Default Apps and set SMS manually."
            )
            .setPositiveButton("Open Default Apps") { _, _ -> openDefaultAppsSettings() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun log(k: String, v: String) {
        Log.d(TAG, "[$k] $v")
    }

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }
}
