package com.signalfence.app

import android.content.Context

object SessionManager {
    private const val PREF = "signalfence_prefs"

    private const val KEY_FIRST_LAUNCH_DONE = "first_launch_done" // onboarding done
    private const val KEY_LOGGED_IN = "logged_in"
    private const val KEY_MOBILE = "user_mobile"
    private const val KEY_PERMISSIONS_SKIPPED = "permissions_skipped"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_USER_ROLE = "user_role"
    private const val KEY_AGGRESSIVE_FILTERING = "aggressive_filtering"

    const val ROLE_USER = "user"
    const val ROLE_ADMIN = "admin"

    fun isAggressiveFiltering(ctx: Context): Boolean {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_AGGRESSIVE_FILTERING, false)
    }

    fun setAggressiveFiltering(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AGGRESSIVE_FILTERING, enabled)
            .apply()
    }

    fun getRole(ctx: Context): String {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_USER_ROLE, ROLE_USER) ?: ROLE_USER
    }

    fun setRole(ctx: Context, role: String) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_ROLE, role)
            .apply()
    }

    fun isFirstLaunchDone(ctx: Context): Boolean {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_FIRST_LAUNCH_DONE, false)
    }

    fun setFirstLaunchDone(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FIRST_LAUNCH_DONE, true)
            .apply()
    }

    fun isPermissionsSkipped(ctx: Context): Boolean {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_PERMISSIONS_SKIPPED, false)
    }

    fun setPermissionsSkipped(ctx: Context, skipped: Boolean) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PERMISSIONS_SKIPPED, skipped)
            .apply()
    }

    fun isDarkMode(ctx: Context): Boolean {
        // Default to true because it's a security app and we love Midnight Onyx
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK_MODE, true)
    }

    fun setDarkMode(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK_MODE, enabled)
            .apply()
        applyTheme(enabled)
    }

    fun applyTheme(darkMode: Boolean) {
        if (darkMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    fun isLoggedIn(ctx: Context): Boolean {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOGGED_IN, false)
    }

    fun setLoggedIn(ctx: Context, mobile: String?) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LOGGED_IN, true)
            .putString(KEY_MOBILE, mobile ?: "")
            .apply()
    }

    fun logout(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LOGGED_IN, false)
            .remove(KEY_MOBILE)
            .apply()
    }

    fun getMobile(ctx: Context): String {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_MOBILE, "") ?: ""
    }

    // ✅ Self-Learning AI Memory
    private const val KEY_TRUSTED_SENDERS = "trusted_senders"
    private const val KEY_BLOCKED_SENDERS = "blocked_senders"

    fun isSenderTrusted(ctx: Context, address: String): Boolean {
        return getSet(ctx, KEY_TRUSTED_SENDERS).contains(address)
    }

    fun isSenderBlocked(ctx: Context, address: String): Boolean {
        return getSet(ctx, KEY_BLOCKED_SENDERS).contains(address)
    }

    fun markSenderTrusted(ctx: Context, address: String) {
        removeFromSet(ctx, KEY_BLOCKED_SENDERS, address)
        addToSet(ctx, KEY_TRUSTED_SENDERS, address)
    }

    fun markSenderBlocked(ctx: Context, address: String) {
        removeFromSet(ctx, KEY_TRUSTED_SENDERS, address)
        addToSet(ctx, KEY_BLOCKED_SENDERS, address)
    }

    private fun getSet(ctx: Context, key: String): Set<String> {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getStringSet(key, emptySet()) ?: emptySet()
    }

    private fun addToSet(ctx: Context, key: String, value: String) {
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val newSet = (sp.getStringSet(key, emptySet()) ?: emptySet()).toMutableSet()
        newSet.add(value)
        sp.edit().putStringSet(key, newSet).apply()
    }

    private fun removeFromSet(ctx: Context, key: String, value: String) {
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val newSet = (sp.getStringSet(key, emptySet()) ?: emptySet()).toMutableSet()
        newSet.remove(value)
        sp.edit().putStringSet(key, newSet).apply()
    }
}
