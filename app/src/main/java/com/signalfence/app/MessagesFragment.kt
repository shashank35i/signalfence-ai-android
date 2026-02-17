package com.signalfence.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.provider.Telephony
import android.provider.ContactsContract
import android.util.Log
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.concurrent.thread

class MessagesFragment : Fragment() {

    companion object {
        const val ACTION_SMS_UPDATED = "com.signalfence.app.ACTION_SMS_UPDATED"
        const val EXTRA_FORCE_SPAM_TAB = "force_spam_tab"
        private const val TAG = "SignalFenceMsgs"
        @Volatile var forceSpamReload: Boolean = false

        fun scheduleSpamReload() {
            forceSpamReload = true
        }

        // ✅ Static cache so switching fragments doesn't reload from scratch
        private object Cache {
            var activeTab: Tab = Tab.NORMAL
            var query: String = ""
            var all: List<ConversationRow> = emptyList()
            var hasLoadedOnce: Boolean = false
            var listState: Parcelable? = null
        }
    }

    private lateinit var repo: SmsRepository
    private lateinit var adapter: ConversationAdapter

    private lateinit var etSearch: EditText
    private lateinit var tabNormalCard: CardView
    private lateinit var tabSpamCard: CardView
    private lateinit var tvRisk: TextView
    private lateinit var rv: RecyclerView

    // ✅ Preloader overlay
    private lateinit var loadingOverlay: View
    private lateinit var tvLoadingText: TextView
    @Volatile private var isLoading: Boolean = false

    // ✅ Empty-state message
    private lateinit var tvEmptyState: TextView
    private lateinit var llEmptyState: View

    // ✅ Animators for loading
    private var pulseAnimator1: android.animation.ObjectAnimator? = null
    private var pulseAnimator2: android.animation.ObjectAnimator? = null
    private var breathingAnimator: android.animation.ObjectAnimator? = null

    private var receiverRegistered = false

    enum class Tab { NORMAL, SPAM }

    private val smsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "[receiver] SMS_UPDATED -> reloading")
            if (intent.getBooleanExtra(EXTRA_FORCE_SPAM_TAB, false)) {
                Cache.activeTab = Tab.SPAM
            }
            loadThreadsAsync(force = true)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_messages, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        AppContextProvider.init(requireContext().applicationContext)
        repo = SmsRepository(requireContext().applicationContext)

        etSearch = view.findViewById(R.id.etSearch)
        tabNormalCard = view.findViewById(R.id.tabNormalCard)
        tabSpamCard = view.findViewById(R.id.tabSpamCard)
        tvRisk = view.findViewById(R.id.tvRisk)
        rv = view.findViewById(R.id.rvConversations)

        loadingOverlay = view.findViewById(R.id.loadingOverlay)
        tvLoadingText = view.findViewById(R.id.tvLoadingText)

        tvEmptyState = view.findViewById(R.id.tvEmptyState)
        llEmptyState = view.findViewById(R.id.llEmptyState)

        rv.layoutManager = LinearLayoutManager(requireContext())

        adapter = ConversationAdapter { row ->
            startActivity(Intent(requireContext(), ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_THREAD_ID, row.threadId)
                putExtra(ChatActivity.EXTRA_ADDRESS, row.address)
                putExtra(ChatActivity.EXTRA_TITLE, row.title)
            })
        }
        rv.adapter = adapter

        // restore query text
        if (Cache.query.isNotEmpty()) {
            etSearch.setText(Cache.query)
            etSearch.setSelection(Cache.query.length)
        }

        tabNormalCard.setOnClickListener {
            Cache.activeTab = Tab.NORMAL
            applyTabUi(view)
            render()
            if (isLoading) updateLoadingText()
        }

        tabSpamCard.setOnClickListener {
            Cache.activeTab = Tab.SPAM
            applyTabUi(view)
            render()
            if (isLoading) updateLoadingText()
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                Cache.query = (s?.toString() ?: "").trim()
                render()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        applyTabUi(view)

        // If cached list exists, show immediately
        if (Cache.all.isNotEmpty()) {
            render()
            restoreListStateIfAny()
        }
    }

    override fun onResume() {
        super.onResume()

        if (!AccessManager.isAccessReady(requireContext())) {
            startActivity(Intent(requireContext(), RequestsActivity::class.java))
            return
        }

        registerSmsRefreshReceiverIfNeeded()
        loadThreadsAsync(force = true)
    }

    override fun onPause() {
        super.onPause()
        saveListState()
        unregisterSmsRefreshReceiverIfNeeded()
    }

    private fun saveListState() {
        Cache.listState = rv.layoutManager?.onSaveInstanceState()
    }

    private fun restoreListStateIfAny() {
        Cache.listState?.let { rv.layoutManager?.onRestoreInstanceState(it) }
    }

    private fun registerSmsRefreshReceiverIfNeeded() {
        if (receiverRegistered) return
        val filter = IntentFilter(ACTION_SMS_UPDATED)
        try {
            val ctx = requireContext()
            androidx.core.content.ContextCompat.registerReceiver(
                ctx, 
                smsUpdateReceiver, 
                filter, 
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        } catch (_: Throwable) {
            receiverRegistered = false
        }
    }

    private fun unregisterSmsRefreshReceiverIfNeeded() {
        if (!receiverRegistered) return
        try {
            requireContext().unregisterReceiver(smsUpdateReceiver)
        } catch (_: Throwable) {
        } finally {
            receiverRegistered = false
        }
    }

    // ✅ Preloader helpers
    private fun updateLoadingText() {
        tvLoadingText.text = if (Cache.activeTab == Tab.SPAM) "Loading blocked messages..." else "Loading messages..."
    }

    private fun setLoading(show: Boolean) {
        isLoading = show
        if (!isAdded) return
        requireActivity().runOnUiThread {
            updateLoadingText()
            loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
            
            if (show) {
                startPulseAnimations()
            } else {
                stopPulseAnimations()
            }
        }
    }

    private fun startPulseAnimations() {
        val ring1 = loadingOverlay.findViewById<View>(R.id.pulseRing1) ?: return
        val ring2 = loadingOverlay.findViewById<View>(R.id.pulseRing2) ?: return

        // Ring 1 Pulse (Scale + Alpha)
        pulseAnimator1 = android.animation.ObjectAnimator.ofPropertyValuesHolder(
            ring1,
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.5f),
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.5f),
            android.animation.PropertyValuesHolder.ofFloat(View.ALPHA, 0.4f, 0f)
        ).apply {
            duration = 1500
            repeatCount = android.animation.ValueAnimator.INFINITE
            start()
        }

        // Ring 2 Pulse (Delayed)
        pulseAnimator2 = android.animation.ObjectAnimator.ofPropertyValuesHolder(
            ring2,
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.4f),
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.4f),
            android.animation.PropertyValuesHolder.ofFloat(View.ALPHA, 0.6f, 0f)
        ).apply {
            duration = 1500
            startDelay = 500
            repeatCount = android.animation.ValueAnimator.INFINITE
            start()
        }
    }

    private fun stopPulseAnimations() {
        pulseAnimator1?.cancel()
        pulseAnimator2?.cancel()
    }

    private fun loadThreadsAsync(force: Boolean) {
        if (!force && Cache.hasLoadedOnce) return
        if (isLoading) return

        val ctx = try { requireContext().applicationContext } catch (e: Exception) { return }

        val showLoader = !Cache.hasLoadedOnce   // only first load
        if (showLoader) setLoading(true)

        thread {
            try {
                // 1) Quick pass: snippets only
                val convs = try { repo.getConversations(limit = 200) } catch (t: Throwable) {
                    Log.e(TAG, "repo.getConversations failed", t); emptyList()
                }

                val quickRows = convs.map { c ->
                    val riskBase = SpamScorer.score(c.address, c.snippet)
                    val risk = applyUserFeedback(ctx, c.address, riskBase)
                    val titleResolved = resolveDisplayName(c.address) ?: c.title
                    ConversationRow(
                        threadId = c.threadId,
                        address = c.address,
                        title = titleResolved,
                        snippet = c.snippet,
                        timeMillis = c.timeMillis,
                        unreadCount = 0,
                        risk = risk
                    )
                }.toMutableList().also { list ->
                    // also include quarantined items so Spam tab shows them immediately
                    val qStore = FilteredStore(ctx)
                    val quarantined = qStore.listConversations(RiskCategory.SPAM, 200) + qStore.listConversations(RiskCategory.CRITICAL, 200)
                    quarantined.forEach { q ->
                        val name = resolveDisplayName(q.address)
                        val risk = RiskResult(score = maxOf(q.maxScore, 90), label = "Spam (Blocked)", isSpam = true)
                        list.add(
                            ConversationRow(
                                threadId = -q.lastTime,
                                address = q.address,
                                title = name ?: q.address,
                                snippet = q.lastBody.ifBlank { "(Blocked spam)" },
                                timeMillis = q.lastTime,
                                unreadCount = 0,
                                risk = risk
                            )
                        )
                    }
                }.sortedByDescending { it.timeMillis }

                if (isAdded) requireActivity().runOnUiThread {
                    Cache.all = quickRows
                    Cache.hasLoadedOnce = true
                    StatsStore.updateFromConversations(quickRows)
                    updateTopRiskCard()
                    render()
                    restoreListStateIfAny()
                    if (showLoader) setLoading(false)
                }

                // 2) Deep pass: real last SMS body per thread
                val rawThreads: List<RawThread> = if (convs.isNotEmpty()) {
                    convs.map { RawThread(it.threadId, it.title, it.snippet, it.timeMillis) }
                } else {
                    queryThreadsFallback(limit = 200)
                }

                val refined = ArrayList<ConversationRow>(rawThreads.size)
                rawThreads.forEach { t ->
                    val last = queryLastSmsForThread(t.threadId)
                    val address = (last?.address ?: t.title ?: "").trim()
                    val body = (last?.body ?: t.snippet ?: "").trim()
                    val time = last?.timeMillis?.takeIf { it > 0 } ?: t.timeMillis
                    val title = (t.title?.takeIf { it.isNotBlank() } ?: address).ifBlank { "Unknown" }

                    val base = SpamScorer.score(address, body.ifBlank { t.snippet ?: "" })
                    var risk = upgradeIfHighConfidenceScam(base, address, body)
                    risk = applyUserFeedback(ctx, address, risk)

                    refined.add(
                        ConversationRow(
                            threadId = t.threadId,
                            address = address,
                            title = title,
                            snippet = body.ifBlank { t.snippet ?: "" },
                            timeMillis = time,
                            unreadCount = 0,
                            risk = risk
                        )
                    )
                }

                                // Merge quarantined (auto-blocked) messages from local store so user can review
                val qStore = FilteredStore(ctx)
                val quarantined = qStore.listConversations(RiskCategory.SPAM, 200) + qStore.listConversations(RiskCategory.CRITICAL, 200)
                quarantined.forEach { q ->
                    val name = resolveDisplayName(q.address)
                    val risk = RiskResult(score = maxOf(q.maxScore, 90), label = "Spam (Blocked)", isSpam = true)
                    refined.add(
                        ConversationRow(
                            threadId = -q.lastTime,
                            address = q.address,
                            title = name ?: q.address,
                            snippet = q.lastBody.ifBlank { "(Blocked spam)" },
                            timeMillis = q.lastTime,
                            unreadCount = 0,
                            risk = risk
                        )
                    )
                }

                refined.sortByDescending { it.timeMillis }

                if (isAdded) requireActivity().runOnUiThread {
                    Cache.all = refined
                    StatsStore.updateFromConversations(refined)
                    updateTopRiskCard()
                    render()
                    if (showLoader) setLoading(false)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "loadThreadsAsync fatal", t)
                if (isAdded) requireActivity().runOnUiThread {
                    setLoading(false)
                    render()
                }
            }
        }
    }


    private fun applyUserFeedback(ctx: Context, address: String, base: RiskResult): RiskResult {
        var risk = base
        if (SessionManager.isSenderTrusted(ctx, address)) {
            risk = RiskResult(0, "Safe", false)
        } else if (SessionManager.isSenderBlocked(ctx, address)) {
            risk = RiskResult(99, "Critical", true)
        } else if (SessionManager.isAggressiveFiltering(ctx)) {
            val isNumericSender = address.all { it.isDigit() || it == '+' || it == ' ' }
            if (isNumericSender && risk.score > 15 && !risk.isSpam) {
                risk = RiskResult(maxOf(risk.score, 65), "Spam (Aggressive)", true)
            } else if (risk.score >= 40 && !risk.isSpam) {
                risk = RiskResult(maxOf(risk.score, 60), "Spam (Aggressive)", true)
            }
        }
        return risk
    }

    // -------------------------
    // ✅ Fallback: list threads
    // -------------------------
    private data class RawThread(
        val threadId: Long,
        val title: String?,
        val snippet: String?,
        val timeMillis: Long
    )

    private fun queryThreadsFallback(limit: Int): List<RawThread> {
        val out = ArrayList<RawThread>()
        val uri = Uri.parse("content://mms-sms/conversations?simple=true")
        val projection = arrayOf("_id", "snippet", "date")
        val sort = "date DESC"

        try {
            requireContext().contentResolver.query(uri, projection, null, null, sort)?.use { c ->
                val idxId = c.getColumnIndex("_id")
                val idxSnippet = c.getColumnIndex("snippet")
                val idxDate = c.getColumnIndex("date")

                while (c.moveToNext() && out.size < limit) {
                    val tid = if (idxId >= 0) c.getLong(idxId) else -1L
                    if (tid <= 0) continue

                    val snippet = if (idxSnippet >= 0) (c.getString(idxSnippet) ?: "") else ""
                    val dateSeconds = if (idxDate >= 0) c.getLong(idxDate) else 0L
                    val timeMillis = if (dateSeconds > 0) dateSeconds * 1000L else 0L

                    out.add(RawThread(threadId = tid, title = null, snippet = snippet, timeMillis = timeMillis))
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "queryThreadsFallback error", t)
        }

        return out
    }

    // -----------------------------------------
    // ✅ Read last SMS for a specific threadId
    // -----------------------------------------
    private data class LastSms(
        val address: String,
        val body: String,
        val timeMillis: Long
    )

    private fun queryLastSmsForThread(threadId: Long): LastSms? {
        if (threadId <= 0) return null

        val uri = Uri.parse("content://sms")
        val projection = arrayOf("address", "body", "date", "thread_id")
        val selection = "thread_id=?"
        val args = arrayOf(threadId.toString())
        val sort = "date DESC"

        return try {
            requireContext().contentResolver.query(uri, projection, selection, args, sort)?.use { c ->
                val idxAddr = c.getColumnIndex("address")
                val idxBody = c.getColumnIndex("body")
                val idxDate = c.getColumnIndex("date")

                if (c.moveToFirst()) {
                    LastSms(
                        address = if (idxAddr >= 0) (c.getString(idxAddr) ?: "") else "",
                        body = if (idxBody >= 0) (c.getString(idxBody) ?: "") else "",
                        timeMillis = if (idxDate >= 0) c.getLong(idxDate) else 0L
                    )
                } else null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "queryLastSmsForThread error threadId=$threadId", t)
            null
        }
    }

    // -------------------------------------------------------
    // ✅ Strong scam upgrade (so spam tab is not empty)
    // -------------------------------------------------------
    private fun upgradeIfHighConfidenceScam(base: RiskResult, address: String, body: String): RiskResult {
        // if already spam, keep
        if (base.isSpam) return base

        val text = (address + " " + body).lowercase()

        // High confidence patterns
        val hasLink = text.contains("http://") || text.contains("https://")
        val hasShort = text.contains("bit.ly") || text.contains("tinyurl") || text.contains("t.co") || text.contains("goo.gl")
        val hasUpi = Regex("""\b[\w.\-]{2,}@[a-z]{2,}\b""").containsMatchIn(text)
        val hasOtp = Regex("""\botp\b|\bone\s*time\s*password\b""").containsMatchIn(text)
        val hasKyc = text.contains("kyc") || text.contains("re-kyc") || text.contains("update kyc")
        val hasBankThreat = text.contains("account suspended") || text.contains("account blocked") || text.contains("will be blocked") || text.contains("deactivated")
        val hasPinCvv = text.contains("cvv") || text.contains("pin") || text.contains("password")
        val hasPolice = text.contains("police") || text.contains("warrant") || text.contains("court") || text.contains("fine")

        val strong =
            (hasOtp && (hasLink || hasUpi || hasKyc || hasPinCvv)) ||
                    (hasKyc && (hasLink || hasUpi || hasBankThreat)) ||
                    (hasBankThreat && (hasLink || hasUpi)) ||
                    (hasPolice && (hasLink || text.contains("pay") || text.contains("upi"))) ||
                    (hasShort && hasLink) ||
                    (hasUpi && (text.contains("pay") || text.contains("send") || text.contains("transfer")))

        if (!strong) return base

        // Force into spam with strong score
        val forcedScore = maxOf(base.score, 72)
        return RiskResult(
            score = forcedScore,
            label = "Spam",
            isSpam = true
        )
    }

    private fun updateTopRiskCard() {
        val all = Cache.all
        if (all.isEmpty()) {
            tvRisk.text = "Low Risk"
            return
        }

        val sample = all.take(30)
        val avg = (sample.sumOf { it.risk.score } / sample.size)

        tvRisk.text = when {
            avg >= 70 -> "High Risk"
            avg >= 40 -> "Medium Risk"
            else -> "Low Risk"
        }
    }

    private fun updateEmptyState(count: Int) {
        val q = Cache.query.trim()
        val msg = when {
            q.isNotEmpty() -> "No results for \"$q\""
            Cache.activeTab == Tab.SPAM -> "All clear. No blocked messages."
            else -> "No messages yet."
        }
        tvEmptyState.text = msg
        
        val targetVis = if (count == 0) View.VISIBLE else View.GONE
        if (llEmptyState.visibility != targetVis) {
            llEmptyState.visibility = targetVis
            if (targetVis == View.VISIBLE) {
                // Smooth reveal: Scale + Fade
                llEmptyState.alpha = 0f
                llEmptyState.scaleX = 0.9f
                llEmptyState.scaleY = 0.9f
                llEmptyState.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(400)
                    .setInterpolator(android.view.animation.OvershootInterpolator())
                    .withEndAction { startBreathing() }
                    .start()
            } else {
                stopBreathing()
            }
        }
    }

    private fun startBreathing() {
        val target = (llEmptyState as? android.view.ViewGroup)?.getChildAt(0) ?: return
        
        breathingAnimator?.cancel()
        breathingAnimator = android.animation.ObjectAnimator.ofPropertyValuesHolder(
            target,
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.05f),
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.05f)
        ).apply {
            duration = 2000
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            start()
        }
    }

    private fun stopBreathing() {
        breathingAnimator?.cancel()
    }

    private fun render() {
        val q = Cache.query.lowercase()

        val filteredByTab = Cache.all.filter { row ->
            val blocked = SessionManager.isSenderBlocked(requireContext(), row.address)
            when (Cache.activeTab) {
                Tab.NORMAL -> !row.risk.isSpam && !blocked
                Tab.SPAM -> row.risk.isSpam || blocked
            }
        }

        // spam sorted by risk desc
        val byTabSorted = filteredByTab.sortedByDescending { it.timeMillis }

        val filteredByQuery = if (q.isBlank()) byTabSorted else {
            byTabSorted.filter {
                it.title.lowercase().contains(q) ||
                        it.snippet.lowercase().contains(q) ||
                        it.address.lowercase().contains(q)
            }
        }

        adapter.submitList(filteredByQuery)
        updateEmptyState(filteredByQuery.size)

        Log.d(TAG, "render tab=${Cache.activeTab} total=${Cache.all.size} shown=${filteredByQuery.size}")
    }

        private fun resolveDisplayName(phone: String?): String? {
        if (phone.isNullOrBlank()) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone))
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            requireContext().contentResolver.query(uri, projection, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Throwable) { null }
    }
    private fun applyTabUi(root: View) {
        val ctx = root.context
        val activeBg = androidx.core.content.ContextCompat.getColor(ctx, R.color.colorPrimary)
        val inactiveBg = android.graphics.Color.TRANSPARENT
        val activeText = androidx.core.content.ContextCompat.getColor(ctx, R.color.colorOnPrimary)
        val inactiveText = androidx.core.content.ContextCompat.getColor(ctx, R.color.colorOnSurfaceVariant)

        val normalIsActive = Cache.activeTab == Tab.NORMAL
        val spamIsActive = Cache.activeTab == Tab.SPAM

        tabNormalCard.setCardBackgroundColor(if (normalIsActive) activeBg else inactiveBg)
        tabSpamCard.setCardBackgroundColor(if (spamIsActive) activeBg else inactiveBg)

        val tvNormal = root.findViewById<TextView>(R.id.tvTabNormal)
        val tvSpam = root.findViewById<TextView>(R.id.tvTabSpam)

        tvNormal.setTextColor(if (normalIsActive) activeText else inactiveText)
        tvSpam.setTextColor(if (spamIsActive) activeText else inactiveText)
    }
}







