package com.signalfence.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import androidx.core.content.ContextCompat

object AccessManager {

    fun requiredSmsPermissions(): Array<String> = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.SEND_SMS
    )

    fun hasSmsPermissions(ctx: Context): Boolean =
        requiredSmsPermissions().all {
            ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
        }

    fun isDefaultSmsApp(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = ctx.getSystemService(Context.ROLE_SERVICE) as? android.app.role.RoleManager
            if (roleManager != null) {
                return roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_SMS)
            }
        }
        val def = Telephony.Sms.getDefaultSmsPackage(ctx) ?: return false
        return def.trim().equals(ctx.packageName.trim(), ignoreCase = true)
    }

    fun isAccessReady(ctx: Context): Boolean =
        hasSmsPermissions(ctx) && isDefaultSmsApp(ctx)

    // ✅ keep compatibility with your old calls
    fun hasAllPermissions(ctx: Context): Boolean = hasSmsPermissions(ctx)
}
