package com.silentdialer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CallLog
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.silentdialer.databinding.FragmentRecentsBinding

/**
 * Recents tab: the device call log, One UI style. Tapping a row (or its call
 * button) re-dials. Numbers hidden via the secret sync stay absent because they
 * are already removed from the system log.
 */
class RecentsFragment : Fragment(), MainActivity.Refreshable {

    private var _binding: FragmentRecentsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: RecentsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = RecentsAdapter { number ->
            (activity as? MainActivity)?.placeCall(number)
        }
        binding.recentsList.layoutManager = LinearLayoutManager(requireContext())
        binding.recentsList.adapter = adapter

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
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) {
            adapter.submit(emptyList())
            showEmpty(getString(R.string.grant_call_log))
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

    private fun query(ctx: android.content.Context): List<RecentCall> {
        val result = mutableListOf<RecentCall>()
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE
        )
        try {
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                CallLog.Calls.DATE + " DESC LIMIT 300"
            )?.use { c ->
                val numIdx = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val nameIdx = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val typeIdx = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val dateIdx = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
                while (c.moveToNext()) {
                    result.add(
                        RecentCall(
                            number = c.getString(numIdx).orEmpty(),
                            name = c.getString(nameIdx),
                            type = c.getInt(typeIdx),
                            date = c.getLong(dateIdx)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Permission revoked or provider error; return whatever we have.
        }
        return result
    }

    private fun updateEmpty() {
        if (adapter.itemCount == 0) showEmpty(getString(R.string.recents_empty))
        else binding.emptyText.visibility = View.GONE
    }

    private fun showEmpty(message: String) {
        if (_binding == null) return
        binding.emptyText.text = message
        binding.emptyText.visibility = View.VISIBLE
    }
}

/** One call-log entry. */
data class RecentCall(
    val number: String,
    val name: String?,
    val type: Int,
    val date: Long
)
