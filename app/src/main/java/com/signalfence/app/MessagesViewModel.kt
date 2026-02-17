package com.signalfence.app

import android.os.Parcelable
import androidx.lifecycle.ViewModel

class MessagesViewModel : ViewModel() {
    var cached: List<ConversationRow> = emptyList()
    var activeTab: MessagesFragment.Tab = MessagesFragment.Tab.NORMAL
    var query: String = ""
    var listState: Parcelable? = null
    var lastLoadedAtMs: Long = 0L
}
