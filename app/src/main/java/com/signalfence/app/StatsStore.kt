package com.signalfence.app

/**
 * Shared in-memory snapshot of latest conversation-level stats, refreshed by MessagesFragment.
 * Keeps Dashboard fast and consistent with the Inbox/Spam tabs.
 */
object StatsStore {
    @Volatile private var hasData = false
    @Volatile private var totalScanned = 0
    @Volatile private var spamCount = 0
    @Volatile private var phishingCount = 0
    @Volatile private var aiPrecision = 0
    @Volatile private var avgRisk = 0

    fun updateFromConversations(list: List<ConversationRow>) {
        if (list.isEmpty()) return

        var spam = 0
        var phishing = 0
        var safeCorrect = 0
        var riskSum = 0

        list.forEach { row ->
            val lower = row.snippet.lowercase()
            if (row.risk.isSpam) {
                spam++
                if (lower.contains("http") || lower.contains("bit.ly")) phishing++
            } else if (row.risk.score < 40) {
                safeCorrect++
            }
            riskSum += row.risk.score
        }

        val size = list.size
        totalScanned = size
        spamCount = spam
        phishingCount = phishing
        aiPrecision = if (size > 0) (safeCorrect * 100 / size) else 100
        avgRisk = if (size > 0) riskSum / size else 0
        hasData = true
    }

    fun setSnapshot(s: DashboardSnapshot) {
        totalScanned = s.totalScanned
        spamCount = s.spamCount
        phishingCount = s.phishingCount
        aiPrecision = s.aiPrecision
        avgRisk = s.avgRisk
        hasData = true
    }

    fun snapshot(): DashboardSnapshot? {
        if (!hasData) return null
        return DashboardSnapshot(
            totalScanned = totalScanned,
            spamCount = spamCount,
            phishingCount = phishingCount,
            aiPrecision = aiPrecision,
            avgRisk = avgRisk
        )
    }
}

data class DashboardSnapshot(
    val totalScanned: Int,
    val spamCount: Int,
    val phishingCount: Int,
    val aiPrecision: Int,
    val avgRisk: Int
)
