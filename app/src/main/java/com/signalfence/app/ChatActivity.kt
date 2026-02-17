package com.signalfence.app

import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.signalfence.app.RiskCategory
import com.signalfence.app.FilteredStore
import kotlin.concurrent.thread

class ChatActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_THREAD_ID = "thread_id"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_TITLE = "title"
        private const val TAG = "SignalFenceChat"
    }

    private lateinit var repo: SmsRepository
    private lateinit var adapter: ChatAdapter

    private var threadId: Long = -1L
    private var address: String = ""
    private var title: String = ""

    private lateinit var rv: RecyclerView

    // Risk UI
    private lateinit var chip: MaterialCardView
    private lateinit var tvChip: TextView
    private lateinit var ivChip: ImageView

    // Feedback UI
    private lateinit var feedbackBanner: LinearLayout
    private lateinit var tvFeedbackReason: TextView
    private lateinit var btnMarkSpam: MaterialButton
    private lateinit var btnMarkSafe: MaterialButton

    private var lastChipLabel: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        supportActionBar?.hide()

        repo = SmsRepository(applicationContext)

        threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
        address = intent.getStringExtra(EXTRA_ADDRESS) ?: ""
        title = intent.getStringExtra(EXTRA_TITLE) ?: address

        findViewById<TextView>(R.id.tvChatName).text = title
        findViewById<TextView>(R.id.tvChatNumber).text = address

        chip = findViewById(R.id.chipSafe)
        tvChip = findViewById(R.id.tvChipLabel)
        ivChip = findViewById(R.id.ivChipIcon)

        feedbackBanner = findViewById(R.id.feedbackBanner)
        tvFeedbackReason = findViewById(R.id.tvFeedbackReason)
        btnMarkSpam = findViewById(R.id.btnMarkSpam)
        btnMarkSafe = findViewById(R.id.btnMarkSafe)

        applyChipStyle(label = "Safe", animate = false)

        findViewById<FrameLayout>(R.id.btnBack).setOnClickListener { finish() }

        rv = findViewById(R.id.rvChat)
        rv.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        adapter = ChatAdapter()
        rv.adapter = adapter

        val et = findViewById<EditText>(R.id.etMsg)

        findViewById<FrameLayout>(R.id.btnSend).setOnClickListener {
            val msg = et.text?.toString()?.trim().orEmpty()
            if (msg.isBlank()) return@setOnClickListener
            sendMessage(msg)
            et.setText("")
        }

        setupFeedbackLogic()
    }

    private fun setupFeedbackLogic() {
        btnMarkSpam.setOnClickListener {
            SessionManager.markSenderBlocked(this, address)
            Toast.makeText(this, "SignalFence updated: Sender flagged as spam", Toast.LENGTH_SHORT).show()
            feedbackBanner.visibility = View.GONE
            
            // Trigger UI refresh and move user to Spam tab
            val intent = android.content.Intent(MessagesFragment.ACTION_SMS_UPDATED).apply {
                putExtra(MessagesFragment.EXTRA_FORCE_SPAM_TAB, true)
            }
            sendBroadcast(intent)
            MessagesFragment.scheduleSpamReload()
            loadMessagesAsync(scrollToEnd = false)
        }
        btnMarkSafe.setOnClickListener {
            SessionManager.markSenderTrusted(this, address)
            Toast.makeText(this, "SignalFence updated: Sender marked as trusted", Toast.LENGTH_SHORT).show()
            feedbackBanner.visibility = View.GONE
            
            // Trigger UI refresh
            sendBroadcast(android.content.Intent(MessagesFragment.ACTION_SMS_UPDATED))
            loadMessagesAsync(scrollToEnd = false)
        }
    }

    private fun sendMessage(msg: String) {
        if (address.isBlank()) {
            Toast.makeText(this, "Invalid recipient", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            SmsManager.getDefault().sendTextMessage(address, null, msg, null, null)
            tryInsertSentIntoProvider(address, msg)
            
            val newThreadId = findThreadIdByAddress(address)
            if (newThreadId > 0) threadId = newThreadId

            loadMessagesAsync(scrollToEnd = true)
            sendBroadcast(android.content.Intent(MessagesFragment.ACTION_SMS_UPDATED))
        } catch (e: Exception) {
            Toast.makeText(this, "Send failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        loadMessagesAsync(scrollToEnd = true)
    }

    private fun loadMessagesAsync(scrollToEnd: Boolean) {
        thread {
            // If this is a quarantined/auto-blocked thread, fetch from local store
            if (threadId <= 0L) {
                val quarantined = FilteredStore(applicationContext).listMessages(address, limit = 500)
                val uiList = quarantined.map { q ->
                    MessageRow(
                        id = q.timeMillis,
                        address = q.address,
                        body = q.body,
                        timeMillis = q.timeMillis,
                        isOutgoing = false
                    )
                }
                if (uiList.isEmpty()) return@thread

                val riskResult = computeHighPrecisionRisk(uiList.takeLast(5))

                runOnUiThread {
                    adapter.submit(uiList)
                    applyChipStyle(riskResult.label, animate = (riskResult.label != lastChipLabel))
                    lastChipLabel = riskResult.label
                    tvFeedbackReason.text = "Spam Probability: ${riskResult.score}%. Help the AI by confirming this result."
                    if (scrollToEnd && uiList.isNotEmpty()) {
                        rv.scrollToPosition(uiList.size - 1)
                    }
                }
                return@thread
            }

            val listProvider: List<SmsRepository.Message> =
                repo.getThreadMessages(threadId, limit = 500)

            val baseUi: List<MessageRow> = listProvider.map { m ->
                MessageRow(
                    id = m.id,
                    address = m.address,
                    body = m.body,
                    timeMillis = m.timeMillis,
                    isOutgoing = m.isOutgoing
                )
            }

            val uiList: List<MessageRow> = if (baseUi.isNotEmpty()) baseUi else {
                // fallback: quarantined store
                FilteredStore(applicationContext).listMessages(address, limit = 500).map { q ->
                    MessageRow(
                        id = q.timeMillis,
                        address = q.address,
                        body = q.body,
                        timeMillis = q.timeMillis,
                        isOutgoing = false
                    )
                }
            }

            if (uiList.isEmpty()) return@thread

            val riskResult = computeHighPrecisionRisk(uiList.takeLast(5))

            runOnUiThread {
                adapter.submit(uiList)
                applyChipStyle(riskResult.label, animate = (riskResult.label != lastChipLabel))
                lastChipLabel = riskResult.label

                tvFeedbackReason.text = "Spam Probability: ${riskResult.score}%. Help the AI by confirming this result."

                if (scrollToEnd && uiList.isNotEmpty()) {
                    rv.scrollToPosition(uiList.size - 1)
                }
            }
        }
    }

    private fun computeHighPrecisionRisk(lastN: List<MessageRow>): RiskResult {
        // If user already blocked sender, treat as spam immediately
        if (SessionManager.isSenderBlocked(this, address)) {
            return RiskResult(95, "Spam", true)
        }

        if (lastN.isEmpty()) return RiskResult(0, "Safe", false)

        var best = SpamScorer.scoreDetailed(address, lastN.first().body)
        lastN.drop(1).forEach { m ->
            val r = SpamScorer.scoreDetailed(address, m.body)
            if (r.score > best.score) best = r
        }

        val label = when (best.category) {
            RiskCategory.CRITICAL -> "High Risk"
            RiskCategory.SPAM -> "Spam"
            else -> "Safe"
        }
        return RiskResult(best.score, label, best.isSpam)
    }

    private fun applyChipStyle(label: String, animate: Boolean) {
        val colorRes = when (label) {
            "High Risk", "Spam" -> R.color.vibrant_spam
            "Warning" -> R.color.vibrant_warning
            else -> R.color.vibrant_safe
        }

        tvChip.text = label
        val color = getColor(colorRes)
        tvChip.setTextColor(color)
        ivChip.setColorFilter(color)

        if (animate) {
            chip.animate().cancel()
            chip.scaleX = 0.90f
            chip.scaleY = 0.90f
            chip.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
        }
    }

    private fun tryInsertSentIntoProvider(addr: String, body: String) {
        try {
            val cv = ContentValues().apply {
                put(android.provider.Telephony.Sms.ADDRESS, addr)
                put(android.provider.Telephony.Sms.BODY, body)
                put(android.provider.Telephony.Sms.DATE, System.currentTimeMillis())
                put(android.provider.Telephony.Sms.READ, 1)
                put(android.provider.Telephony.Sms.SEEN, 1)
                put(android.provider.Telephony.Sms.TYPE, android.provider.Telephony.Sms.MESSAGE_TYPE_SENT)
            }
            contentResolver.insert(android.provider.Telephony.Sms.Sent.CONTENT_URI, cv)
        } catch (t: Throwable) {
            Log.w(TAG, "Insert into Sent failed: ${t.message}")
        }
    }

    private fun findThreadIdByAddress(addr: String): Long {
        return try {
            val c: Cursor? = contentResolver.query(
                Uri.parse("content://sms"),
                arrayOf("thread_id"),
                "address=?",
                arrayOf(addr),
                "date DESC"
            )
            c.use { if (it != null && it.moveToFirst()) it.getLong(0) else -1L }
        } catch (_: Throwable) { -1L }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.ss_pop_enter, R.anim.ss_pop_exit)
    }
}


