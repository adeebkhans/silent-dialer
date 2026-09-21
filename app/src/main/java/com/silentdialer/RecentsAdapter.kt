package com.silentdialer

import android.provider.CallLog
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.silentdialer.databinding.ItemRecentBinding

/** Renders the call log with One UI-style rows and simple text filtering. */
class RecentsAdapter(
    private val onCall: (String) -> Unit
) : RecyclerView.Adapter<RecentsAdapter.VH>() {

    private val all = mutableListOf<RecentCall>()
    private val shown = mutableListOf<RecentCall>()

    fun submit(items: List<RecentCall>) {
        all.clear()
        all.addAll(items)
        shown.clear()
        shown.addAll(items)
        notifyDataSetChanged()
    }

    fun filter(query: String, onDone: () -> Unit) {
        val q = query.trim().lowercase()
        shown.clear()
        if (q.isEmpty()) {
            shown.addAll(all)
        } else {
            all.filterTo(shown) {
                it.number.lowercase().contains(q) ||
                    (it.name?.lowercase()?.contains(q) == true)
            }
        }
        notifyDataSetChanged()
        onDone()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemRecentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(shown[position])

    override fun getItemCount(): Int = shown.size

    inner class VH(private val b: ItemRecentBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: RecentCall) {
            val ctx = b.root.context
            val title = item.name?.takeIf { it.isNotBlank() }
                ?: item.number.takeIf { it.isNotBlank() }
                ?: ctx.getString(R.string.unknown_number)
            b.nameText.text = title

            val missed = item.type == CallLog.Calls.MISSED_TYPE ||
                item.type == CallLog.Calls.REJECTED_TYPE
            b.nameText.setTextColor(
                ContextCompat.getColor(ctx, if (missed) R.color.missed_red else R.color.on_surface)
            )

            b.typeIcon.setImageResource(
                when (item.type) {
                    CallLog.Calls.OUTGOING_TYPE -> R.drawable.ic_call_made
                    CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> R.drawable.ic_call_missed
                    else -> R.drawable.ic_call_received
                }
            )
            b.typeIcon.setColorFilter(
                ContextCompat.getColor(
                    ctx, if (missed) R.color.missed_red else R.color.on_surface_muted
                )
            )

            val typeLabel = when (item.type) {
                CallLog.Calls.OUTGOING_TYPE -> ctx.getString(R.string.call_type_outgoing)
                CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE ->
                    ctx.getString(R.string.call_type_missed)
                else -> ctx.getString(R.string.call_type_incoming)
            }
            val time = DateUtils.getRelativeTimeSpanString(
                item.date, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            )
            b.subText.text = "$typeLabel · $time"

            val dial = { if (item.number.isNotBlank()) onCall(item.number) }
            b.root.setOnClickListener { dial() }
            b.callButton.setOnClickListener { dial() }
        }
    }
}
