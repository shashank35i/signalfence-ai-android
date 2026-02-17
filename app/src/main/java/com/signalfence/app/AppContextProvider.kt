package com.signalfence.app

import android.content.Context

/**
 * Simple holder for application context so static utilities (SpamScorer) can reach assets safely.
 */
object AppContextProvider {
    @Volatile private var ctx: Context? = null

    fun init(context: Context) {
        if (ctx == null) {
            ctx = context.applicationContext
        }
    }

    fun get(): Context? = ctx
}
