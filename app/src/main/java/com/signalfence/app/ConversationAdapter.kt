package com.signalfence.app

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationAdapter(
    private val onClick: (ConversationRow) -> Unit
) : ListAdapter<ConversationRow, ConversationAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_conversation, parent, false)
        return VH(v, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(itemView: View, private val onClick: (ConversationRow) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val tvInitial: TextView = itemView.findViewById(R.id.tvInitial)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvSnippet: TextView = itemView.findViewById(R.id.tvSnippet)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val unreadDot: View = itemView.findViewById(R.id.vUnreadDot)
        private val chev: ImageView = itemView.findViewById(R.id.ivChev)
        private val tvSpamScore: TextView = itemView.findViewById(R.id.tvSpamScore)

        private val pressInterpolator = AccelerateDecelerateInterpolator()

        fun bind(row: ConversationRow) {
            val initial = row.title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            tvInitial.text = initial
            tvName.text = row.title
            tvSnippet.text = row.snippet
            tvTime.text = formatTime(row.timeMillis)
            unreadDot.visibility = if (row.unreadCount > 0) View.VISIBLE else View.INVISIBLE
            chev.contentDescription = "open"

            // Spam Score UI
            tvSpamScore.text = "${row.risk.score}% Risk"
            val colorRes = when {
                row.risk.score < 30 -> R.color.vibrant_safe
                row.risk.score < 70 -> R.color.vibrant_warning
                else -> R.color.vibrant_spam
            }
            tvSpamScore.setTextColor(itemView.context.getColor(colorRes))
            tvSpamScore.visibility = if (row.risk.score > 0 || row.risk.isSpam) View.VISIBLE else View.GONE

            // ✅ Press feedback only (NO activity transitions here)
            itemView.isClickable = true
            itemView.isFocusable = true

            itemView.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.animate()
                            .scaleX(0.985f).scaleY(0.985f)
                            .setDuration(90)
                            .setInterpolator(pressInterpolator)
                            .start()
                    }
                    MotionEvent.ACTION_UP -> {
                        v.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(120)
                            .setInterpolator(pressInterpolator)
                            .start()
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(120)
                            .setInterpolator(pressInterpolator)
                            .start()
                    }
                }
                false
            }

            itemView.setOnClickListener {
                onClick(row) // ✅ your fragment/activity handles startActivity + overridePendingTransition
            }
        }

        private fun formatTime(ms: Long): String {
            val d = Date(ms)
            val now = Date()
            val sameDay =
                SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(d) ==
                        SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(now)

            return if (sameDay) {
                SimpleDateFormat("h:mm a", Locale.getDefault()).format(d)
            } else {
                SimpleDateFormat("MMM d", Locale.getDefault()).format(d)
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ConversationRow>() {
            override fun areItemsTheSame(oldItem: ConversationRow, newItem: ConversationRow) =
                oldItem.threadId == newItem.threadId

            override fun areContentsTheSame(oldItem: ConversationRow, newItem: ConversationRow) =
                oldItem == newItem
        }
    }
}
