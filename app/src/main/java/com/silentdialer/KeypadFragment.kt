package com.silentdialer

import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import com.silentdialer.databinding.FragmentKeypadBinding

/**
 * The One UI keypad tab: dial pad, number display and the call button. Also the
 * only entry point to the hidden configuration — typing the secret sequence
 * ([Prefs.SECRET_CODE]) opens it instead of dialing.
 */
class KeypadFragment : Fragment() {

    private var _binding: FragmentKeypadBinding? = null
    private val binding get() = _binding!!

    /** Number requested before the view existed (e.g. from a tel: intent). */
    private var pendingNumber: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKeypadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val keys = mapOf(
            binding.key0 to "0", binding.key1 to "1", binding.key2 to "2",
            binding.key3 to "3", binding.key4 to "4", binding.key5 to "5",
            binding.key6 to "6", binding.key7 to "7", binding.key8 to "8",
            binding.key9 to "9", binding.keyStar to "*", binding.keyHash to "#"
        )
        for ((keyView, digit) in keys) {
            keyView.setOnClickListener { append(digit) }
        }
        binding.key0.setOnLongClickListener {
            val text = currentNumber()
            setNumber(if (text.endsWith("0")) text.dropLast(1) + "+" else "$text+")
            true
        }

        binding.callButton.setOnClickListener {
            (activity as? MainActivity)?.placeCall(currentNumber())
        }
        binding.backspaceButton.setOnClickListener { onBackspace() }
        binding.backspaceButton.setOnLongClickListener { setNumber(""); true }

        binding.addToContacts.setOnClickListener { addToContacts() }
        binding.moreButton.setOnClickListener { showOverflow(it) }
        binding.setDefaultButton.setOnClickListener {
            (activity as? MainActivity)?.requestDefaultDialer()
        }

        pendingNumber?.let { setNumber(it) }
        pendingNumber = null
        refreshDisplayState()
    }

    override fun onResume() {
        super.onResume()
        updateDefaultDialerHint()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --- Number handling -------------------------------------------------

    private fun currentNumber(): String = binding.numberDisplay.text.toString()

    private fun append(s: String) {
        setNumber(currentNumber() + s)
    }

    private fun onBackspace() {
        val text = currentNumber()
        if (text.isNotEmpty()) setNumber(text.dropLast(1))
    }

    /** Public entry to set the dialed number (from the host activity). */
    fun setNumber(number: String) {
        if (_binding == null) {
            pendingNumber = number
            return
        }
        // Intercept the secret unlock sequence before it becomes a "number".
        if (Prefs.isSecretCode(number)) {
            binding.numberDisplay.text = ""
            refreshDisplayState()
            startActivity(Intent(requireContext(), HiddenConfigActivity::class.java))
            return
        }
        binding.numberDisplay.text = number
        refreshDisplayState()
    }

    private fun refreshDisplayState() {
        val hasText = currentNumber().isNotEmpty()
        binding.backspaceButton.visibility = if (hasText) View.VISIBLE else View.INVISIBLE
        binding.addToContacts.visibility = if (hasText) View.VISIBLE else View.GONE
    }

    fun updateDefaultDialerHint() {
        if (_binding == null) return
        val isDefault = (activity as? MainActivity)?.isDefaultDialer() ?: true
        binding.setDefaultButton.visibility = if (isDefault) View.GONE else View.VISIBLE
    }

    // --- Overflow / contacts --------------------------------------------

    private fun showOverflow(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, MENU_SETTINGS, 0, R.string.settings)
        popup.setOnMenuItemClickListener {
            if (it.itemId == MENU_SETTINGS) {
                startActivity(Intent(requireContext(), SettingsActivity::class.java))
                true
            } else {
                false
            }
        }
        popup.show()
    }

    private fun addToContacts() {
        val number = currentNumber().ifBlank { return }
        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, number)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            // No contacts app available; ignore.
        }
    }

    companion object {
        private const val MENU_SETTINGS = 1
    }
}
