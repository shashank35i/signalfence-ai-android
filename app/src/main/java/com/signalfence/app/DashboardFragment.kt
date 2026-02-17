package com.signalfence.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import kotlin.concurrent.thread

class DashboardFragment : Fragment() {

    private lateinit var tvSafetyScore: TextView
    private lateinit var tvSpamCount: TextView
    private lateinit var tvPhishingCount: TextView
    private lateinit var tvStatusText: TextView
    private lateinit var tvAiPrecision: TextView
    private lateinit var tvTotalScanned: TextView

    private var loadingOverlay: View? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvSafetyScore = view.findViewById(R.id.tvSafetyScore)
        tvSpamCount = view.findViewById(R.id.tvSpamCount)
        tvPhishingCount = view.findViewById(R.id.tvPhishingCount)
        tvStatusText = view.findViewById(R.id.tvStatusText)
        tvAiPrecision = view.findViewById(R.id.tvAiPrecision)
        tvTotalScanned = view.findViewById(R.id.tvTotalScanned)
        loadingOverlay = view.findViewById(R.id.loadingOverlay)

        updateStats()
    }

    override fun onResume() {
        super.onResume()
        updateStats()
    }

    private fun updateStats() {
        val ctx = context ?: return

        val cached = StatsStore.snapshot()
        if (cached != null) {
            if (!isAdded) return
            requireActivity().runOnUiThread {
                renderSnapshot(cached)
                setLoading(false)
            }
        } else {
            setLoading(true)
        }

        thread {
            val repo = SmsRepository(ctx.applicationContext)
            val threads = repo.getConversations(limit = 2000)

            var totalMessages = 0
            var spamMessages = 0
            var phishingMessages = 0
            var riskSum = 0
            var correctSafe = 0

            for (conv in threads) {
                val msgs = repo.getThreadMessages(conv.threadId, limit = 300)
                if (msgs.isEmpty()) {
                    val fallbackRisk = SpamScorer.scoreDetailed(conv.address, conv.snippet)
                    riskSum += fallbackRisk.score
                    if (!fallbackRisk.isSpam && fallbackRisk.score < 40) correctSafe++
                    if (fallbackRisk.isSpam) spamMessages++
                    if (conv.snippet.lowercase().contains("http")) phishingMessages++
                    totalMessages += 1
                    continue
                }

                msgs.forEach { m ->
                    val risk = SpamScorer.scoreDetailed(m.address, m.body)
                    riskSum += risk.score
                    if (!risk.isSpam && risk.score < 40) correctSafe++
                    if (risk.isSpam) {
                        spamMessages++
                        val lower = m.body.lowercase()
                        if (lower.contains("http") || lower.contains("bit.ly")) phishingMessages++
                    }
                }
                totalMessages += msgs.size
            }

            val avgRisk = if (totalMessages > 0) riskSum / totalMessages else 0
            val aiPrecision = if (totalMessages > 0) (correctSafe * 100 / totalMessages).coerceIn(0, 100) else 100

            val snap = DashboardSnapshot(
                spamCount = spamMessages,
                phishingCount = phishingMessages,
                aiPrecision = aiPrecision,
                avgRisk = avgRisk,
                totalScanned = totalMessages
            )

            if (!isAdded) return@thread
            requireActivity().runOnUiThread {
                renderSnapshot(snap)
                StatsStore.setSnapshot(snap)
                setLoading(false)
            }
        }
    }

    private fun renderSnapshot(s: DashboardSnapshot) {
        val safetyScore = (100 - s.avgRisk).coerceIn(0, 100)
        tvSafetyScore.text = "$safetyScore%"
        tvSpamCount.text = s.spamCount.toString()
        tvPhishingCount.text = s.phishingCount.toString()
        tvAiPrecision.text = "${s.aiPrecision}%"
        tvTotalScanned.text = s.totalScanned.toString()

        tvStatusText.text = when {
            safetyScore >= 90 -> "All clear. SignalFence is actively monitoring your inbox."
            safetyScore >= 70 -> "Protection is active. A few suspicious items were filtered."
            else -> "Warning: Elevated risk detected. Consider enabling Aggressive Filtering."
        }
    }

    private fun setLoading(show: Boolean) {
        loadingOverlay?.visibility = if (show) View.VISIBLE else View.GONE
    }
}
