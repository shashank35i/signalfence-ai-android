package com.signalfence.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.BaseVH>() {

    private val items = mutableListOf<MessageRow>()

    fun submit(list: List<MessageRow>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    // ✅ show message immediately like real apps
    fun addOptimisticOutgoing(address: String, body: String, timeMillis: Long) {
        val m = MessageRow(
            id = -timeMillis, // temp negative id
            address = address,
            body = body,
            timeMillis = timeMillis,
            isOutgoing = true
        )
        items.add(m)
        notifyItemInserted(items.size - 1)
    }

    override fun getItemViewType(position: Int): Int = if (items[position].isOutgoing) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseVH {
        val layout = if (viewType == 1) R.layout.item_chat_outgoing else R.layout.item_chat_incoming
        val v = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return BaseVH(v)
    }

    override fun onBindViewHolder(holder: BaseVH, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    class BaseVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvBody: TextView = itemView.findViewById(R.id.tvBody)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)

        fun bind(m: MessageRow) {
            tvBody.text = m.body
            tvTime.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(m.timeMillis))
        }
    }
}
