package com.signalfence.app

object SpamScorer {

    // Platt calibration coefficients (fitted on your data)
    private const val PLATT_A = 2.001024f
    private const val PLATT_B = 2.840971f

    private fun calibratePlatt(p: Float): Float {
        val clipped = p.coerceIn(1e-6f, 1f - 1e-6f)
        val logit = kotlin.math.ln(clipped / (1f - clipped))
        return (1f / (1f + kotlin.math.exp(-(PLATT_A * logit + PLATT_B)))).coerceIn(0f, 1f)
    }

    fun score(address: String, body: String): RiskResult {
        val d = scoreDetailed(address, body)
        return RiskResult(score = d.score, label = d.label, isSpam = d.isSpam)
    }

    fun scoreDetailed(address: String, body: String): RiskDetails {
        val addr = address.trim()
        val text = (body + " " + addr).lowercase()

        var score = 0
        val reasons = ArrayList<String>()

        fun hit(points: Int, reason: String) { score += points; reasons.add(reason) }

        // Links & shorteners
        val hasHttp = text.contains("http://") || text.contains("https://")
        if (hasHttp) hit(25, "Contains link")
        if (text.contains("bit.ly") || text.contains("tinyurl") || text.contains("t.co") || text.contains("goo.gl")) hit(20, "Shortened link")

        // Phishing patterns
        listOf("update-", "kyc-", "secure-", "-login", "service-", "portal", "verification",
            "verify-", "customer-", "support-", "helpdesk", ".online", ".site", ".xyz", ".top")
            .forEach { if (hasHttp && text.contains(it)) hit(18, "Phishing domain pattern: $it") }

        // Job/task scams
        listOf("work from home", "part time", "daily pay", "earn money", "telegram",
            "task", "subscription", "youtube", "like", "screenshot", "invest")
            .forEach { if (text.contains(it)) hit(12, "Job/Task scam detected: $it") }

        // Critical keywords
        listOf("otp", "one time password", "password", "pin", "upi", "bank", "card", "cvv",
            "kyc", "account suspended", "account blocked", "deactivated", "verify now",
            "immediately", "suspended", "blocked", "aadh-", "pan-")
            .forEach { if (text.contains(it)) hit(7, "Critical keyword: $it") }

        // UPI handle
        if (Regex("""\b[\w.\-]{2,}@[a-z]{2,}\b""").containsMatchIn(text)) hit(15, "UPI handle detected")

        // Scam words
        listOf("win", "lottery", "prize", "claim", "gift", "refund", "cashback",
            "offer", "free", "limited", "congratulations", "selected", "urgent")
            .forEach { if (text.contains(it)) hit(5, "Scam keyword: $it") }

        // Abusive boost
        if (listOf("fuck", "f*ck", "shit", "bitch", "asshole").any { text.contains(it) } && score < 40) {
            hit(40 - score, "Abusive language")
        }

        // Money markers
        if (text.contains("rs") || text.contains("inr") || text.contains("$") || text.contains("\u20B9")) hit(10, "Money mention")

        // Numeric sender / noise
        val digits = addr.filter { it.isDigit() }
        if (digits.length >= 8 && addr.length >= 8) hit(10, "Unknown numeric sender")
        if (body.count { it.isDigit() } >= 18) hit(6, "Too many numbers")
        if (body.count { it == '!' } >= 3) hit(4, "Excessive exclamations")

        score = score.coerceIn(0, 100)

        // XGBoost ONNX probability
        val mlProbRaw = try { AppContextProvider.get()?.let { XgbOnnx.predict(it, text) } ?: 0f } catch (_: Throwable) { 0f }
        val strongSpamPattern = (
                text.contains("bit.ly") ||
                        (text.contains("win") && text.contains("lottery")) ||
                        text.contains("prize") ||
                        text.contains("congratulations") ||
                        text.contains("\u20B9") || text.contains("rs") || text.contains("inr")
                )
        val mlProb = (if (mlProbRaw < 0.3f && strongSpamPattern) 0.95f else mlProbRaw)
        val mlScore = (mlProb * 100).toInt()
        reasons.add("XGBoost ${mlScore}%")

        // ML dominates when available
        val fusedScore = kotlin.math.max(score, mlScore)

        val category = when {
            mlProb >= 0.82f || fusedScore >= 80 -> RiskCategory.CRITICAL
            mlProb >= 0.45f || fusedScore >= 55 -> RiskCategory.SPAM
            else -> RiskCategory.NORMAL
        }

        val label = when (category) {
            RiskCategory.CRITICAL -> "Critical"
            RiskCategory.SPAM -> "Spam"
            RiskCategory.NORMAL -> if (fusedScore >= 40) "Warning" else "Safe"
        }

        return RiskDetails(
            score = fusedScore,
            label = label,
            isSpam = category != RiskCategory.NORMAL,
            category = category,
            reasons = reasons.distinct()
        )
    }
}






