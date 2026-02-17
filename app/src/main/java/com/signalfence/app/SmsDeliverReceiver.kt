package com.signalfence.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            AppContextProvider.init(context)
            val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (msgs.isNullOrEmpty()) return

            val first = msgs.first()
            val address = first.originatingAddress ?: "Unknown"
            val fullBody = msgs.joinToString(separator = "") { it.messageBody ?: "" }
            val time = first.timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()

            val detailed = SpamScorer.scoreDetailed(address, fullBody)
            val score = detailed.score
            val category = detailed.category
            val reasons = detailed.reasons

            val quarantine =
                (category == RiskCategory.CRITICAL && SignalFencePrefs.blockCritical(context)) ||
                        (category == RiskCategory.SPAM && SignalFencePrefs.blockSpam(context))

            Log.d(
                "SignalFenceDeliver",
                "from=$address cat=$category score=$score quarantine=$quarantine"
            )

            if (quarantine) {
                FilteredStore(context).save(
                    address = address,
                    body = fullBody,
                    timeMillis = time,
                    category = category,
                    score = score,
                    reasons = reasons
                )

                // Best-effort stop further delivery
                if (isOrderedBroadcast) abortBroadcast()

                context.sendBroadcast(Intent(MessagesFragment.ACTION_SMS_UPDATED))
                // Optional: notify user about blocked spam
                NotificationHelper.ensureChannels(context)
                NotificationHelper.notifyMessage(
                    context,
                    address = address,
                    body = fullBody,
                    threadId = -time, // negative to avoid collision
                    isSpam = true
                )
                return
            }

            // Normal -> write to inbox (as default SMS app)
            SmsProviderWriter.insertInbox(context, address, fullBody, time)
            context.sendBroadcast(Intent(MessagesFragment.ACTION_SMS_UPDATED))
            try {
                val threadId = SmsRepository(context).resolveThreadIdForAddress(address)
                NotificationHelper.ensureChannels(context)
                NotificationHelper.notifyMessage(
                    context,
                    address = address,
                    body = fullBody,
                    threadId = if (threadId > 0) threadId else time,
                    isSpam = false
                )
            } catch (_: Throwable) { }

        } catch (t: Throwable) {
            Log.e("SignalFenceDeliver", "deliver error", t)
        }
    }
}
