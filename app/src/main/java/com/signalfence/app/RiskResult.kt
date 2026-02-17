package com.signalfence.app

data class RiskResult(
    val score: Int,      // 0..100
    val label: String,   // "Low Risk" / "Medium Risk" / "High Risk"
    val isSpam: Boolean
)
