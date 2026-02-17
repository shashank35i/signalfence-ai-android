package com.signalfence.app

data class ConversationRow(
    val threadId: Long,
    val address: String,
    val title: String,
    val snippet: String,
    val timeMillis: Long,
    val unreadCount: Int,
    val risk: RiskResult
)

