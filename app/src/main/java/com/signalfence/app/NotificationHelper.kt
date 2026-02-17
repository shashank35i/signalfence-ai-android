package com.signalfence.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder

object NotificationHelper {

    private const val CHANNEL_INBOX = "inbox_msgs"
    private const val CHANNEL_SPAM = "spam_msgs"
    private const val FALLBACK_BODY = "(No message body)"

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()

        val inbox = NotificationChannel(
            CHANNEL_INBOX,
            "Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incoming SMS"
            enableVibration(true)
            setSound(sound, attrs)
            lightColor = Color.GREEN
        }

        val spam = NotificationChannel(
            CHANNEL_SPAM,
            "Blocked Spam",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Spam blocked by SignalFence"
            enableVibration(true)
            setSound(sound, attrs)
            lightColor = Color.RED
        }

        nm.createNotificationChannels(listOf(inbox, spam))
    }

    fun notifyMessage(ctx: Context, address: String, body: String, threadId: Long, isSpam: Boolean) {
        val channel = if (isSpam) CHANNEL_SPAM else CHANNEL_INBOX
        val nm = NotificationManagerCompat.from(ctx)

        val displayName = resolveDisplayName(ctx, address)
        val title = if (displayName == address || displayName.isBlank()) {
            if (isSpam) "Blocked: $address" else address
        } else {
            if (isSpam) "Blocked: $displayName" else displayName
        }
        val content = if (body.isNotBlank()) body.take(240) else FALLBACK_BODY

        val tapIntent = Intent(ctx, ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_THREAD_ID, threadId)
            putExtra(ChatActivity.EXTRA_ADDRESS, address)
            putExtra(ChatActivity.EXTRA_TITLE, displayName)
        }

        // Build proper back stack: HomeActivity -> ChatActivity
        val pi = TaskStackBuilder.create(ctx).run {
            addNextIntentWithParentStack(Intent(ctx, HomeActivity::class.java))
            addNextIntent(tapIntent)
            getPendingIntent((threadId % 100000).toInt(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val builder = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(if (isSpam) R.drawable.ic_brand_shield else R.drawable.ic_nav_inbox)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        nm.notify((threadId % 100000).toInt(), builder.build())
    }

    private fun resolveDisplayName(ctx: Context, address: String): String {
        if (address.isBlank()) return "Unknown sender"
        return try {
            val lookupUri = Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(address)
            )
            val projection = arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME)
            ctx.contentResolver.query(lookupUri, projection, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) ?: address else address
            } ?: address
        } catch (_: Throwable) {
            address
        }
    }
}
