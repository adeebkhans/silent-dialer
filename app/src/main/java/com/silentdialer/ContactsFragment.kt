package com.silentdialer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.silentdialer.databinding.FragmentContactsBinding

/**
 * Contacts tab: the device contacts with phone numbers, One UI style. Tapping a
 * row (or its call button) dials the contact's number.
 */
class ContactsFragment : Fragment(), MainActivity.Refreshable {

    private var _binding: FragmentContactsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ContactsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContactsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = ContactsAdapter { number ->
            (activity as? MainActivity)?.placeCall(number)
        }
        binding.contactsList.layoutManager = LinearLayoutManager(requireContext())
        binding.contactsList.adapter = adapter

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                adapter.filter(s?.toString().orEmpty()) { updateEmpty() }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        refresh()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun refresh() {
        val ctx = context ?: return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            adapter.submit(emptyList())
            showEmpty(getString(R.string.grant_contacts))
            return
        }
        val appCtx = ctx.applicationContext
        Thread {
            val items = query(appCtx)
            view?.post {
                if (_binding == null) return@post
                adapter.submit(items)
                adapter.filter(binding.searchInput.text?.toString().orEmpty()) { updateEmpty() }
            }
        }.start()
    }

    private fun query(ctx: android.content.Context): List<Contact> {
        val result = mutableListOf<Contact>()
        val seen = HashSet<String>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
            )?.use { c ->
                val nameIdx = c.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                )
                val numIdx = c.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                while (c.moveToNext()) {
                    val name = c.getString(nameIdx).orEmpty()
                    val number = c.getString(numIdx).orEmpty()
                    if (name.isBlank() && number.isBlank()) continue
                    val key = "$name|${number.filter { it.isDigit() }}"
                    if (seen.add(key)) result.add(Contact(name, number))
                }
            }
        } catch (_: Exception) {
            // Permission revoked or provider error.
        }
        return result
    }

    private fun updateEmpty() {
        if (adapter.itemCount == 0) showEmpty(getString(R.string.contacts_empty))
        else binding.emptyText.visibility = View.GONE
    }

    private fun showEmpty(message: String) {
        if (_binding == null) return
        binding.emptyText.text = message
        binding.emptyText.visibility = View.VISIBLE
    }
}

/** One contact phone entry. */
data class Contact(
    val name: String,
    val number: String
)
