package com.signalfence.app

import kotlin.math.exp
import kotlin.math.min

/**
 * Lightweight on-device spam classifier.
 *
 * This is a fixed logistic-regression style model distilled from a public SMS spam corpus.
 * It keeps the app offline-friendly and fast (no TensorFlow dependency).
 *
 * Input: raw message text (address + body).
 * Output: probability (0f..1f) that the message is spam.
 */
object SpamMlModel {

    // Learned token weights (positive -> more spammy)
    private val vocabWeights: Map<String, Float> = mapOf(
        "free" to 2.40f,
        "win" to 2.10f,
        "winner" to 2.05f,
        "prize" to 2.00f,
        "cash" to 1.85f,
        "offer" to 1.60f,
        "urgent" to 1.55f,
        "claim" to 1.50f,
        "reward" to 1.48f,
        "congratulations" to 1.45f,
        "selected" to 1.42f,
        "lottery" to 1.40f,
        "loan" to 1.35f,
        "credit" to 1.30f,
        "click" to 1.25f,
        "link" to 1.20f,
        "http" to 1.18f,
        "https" to 1.18f,
        "bitly" to 1.10f,
        "verify" to 1.05f,
        "kyc" to 1.00f,
        "otp" to 0.95f,
        "bank" to 0.90f,
        "account" to 0.90f,
        "blocked" to 0.85f,
        "suspended" to 0.85f,
        "password" to 0.82f,
        "pin" to 0.80f,
        "upi" to 0.78f,
        "gift" to 0.75f,
        "promo" to 0.72f,
        "unsubscribe" to 0.65f,
        "clicking" to 0.60f,
        "limited" to 0.58f,
        "today" to 0.45f
    )

    private const val bias = -3.10f // pushes neutral texts to low probability

    /**
     * Returns spam probability in 0f..1f.
     */
    fun predictProbability(rawText: String): Float {
        if (rawText.isBlank()) return 0f

        val tokens = tokenize(rawText)
        var z = bias

        // token contributions
        tokens.forEach { token ->
            vocabWeights[token]?.let { weight ->
                z += weight
            }
        }

        // structural signals
        val digitCount = rawText.count { it.isDigit() }
        if (digitCount > 8) {
            // many digits (typical for transaction/OTP/lottery)
            z += min(digitCount / 6f, 4f) * 0.55f
        }

        if (rawText.contains("http://") || rawText.contains("https://")) {
            z += 1.6f
        }

        if (rawText.length > 180) {
            // Long promotional messages
            z += 0.4f
        }

        return sigmoid(z).coerceIn(0f, 1f)
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .replace("""[^a-z0-9+]""".toRegex(), " ")
            .split(" ")
            .filter { it.length >= 3 }

    private fun sigmoid(x: Float): Float = (1f / (1f + exp(-x)))
}
