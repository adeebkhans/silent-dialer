package com.silentdialer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.silentdialer.databinding.ItemContactBinding

/** Renders contacts with One UI-style rows and simple name/number filtering. */
class ContactsAdapter(
    private val onCall: (String) -> Unit
) : RecyclerView.Adapter<ContactsAdapter.VH>() {

    private val all = mutableListOf<Contact>()
    private val shown = mutableListOf<Contact>()

    fun submit(items: List<Contact>) {
        all.clear()
        all.addAll(items)
        shown.clear()
        shown.addAll(items)
        notifyDataSetChanged()
    }

    fun filter(query: String, onDone: () -> Unit) {
        val q = query.trim().lowercase()
        val qDigits = q.filter { it.isDigit() }
        shown.clear()
        if (q.isEmpty()) {
            shown.addAll(all)
        } else {
            all.filterTo(shown) { c ->
                c.name.lowercase().contains(q) ||
                    c.number.lowercase().contains(q) ||
                    (qDigits.isNotEmpty() &&
                        c.number.filter { it.isDigit() }.contains(qDigits))
            }
        }
        notifyDataSetChanged()
        onDone()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemContactBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(shown[position])

    override fun getItemCount(): Int = shown.size

    inner class VH(private val b: ItemContactBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: Contact) {
            val ctx = b.root.context
            val name = item.name.takeIf { it.isNotBlank() }
                ?: item.number.takeIf { it.isNotBlank() }
                ?: ctx.getString(R.string.unknown_number)
            b.nameText.text = name
            b.numberText.text = item.number
            b.initial.text = name.trim().firstOrNull()?.uppercase() ?: "#"

            val dial = { if (item.number.isNotBlank()) onCall(item.number) }
            b.root.setOnClickListener { dial() }
            b.callButton.setOnClickListener { dial() }
        }
    }
}
