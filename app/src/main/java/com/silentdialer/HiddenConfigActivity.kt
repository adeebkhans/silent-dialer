package com.silentdialer

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.silentdialer.databinding.ActivityHiddenConfigBinding

/**
 * Covert configuration of the target number, reachable only by typing the
 * secret sequence ([Prefs.SECRET_CODE]) on the dial pad. Nothing in the visible
 * app links here or hints at its existence.
 *
 * The saved number is write-only: the field always starts blank and typing +
 * Save simply overwrites whatever was stored, so the hidden number can be
 * changed but never read back from the UI.
 */
class HiddenConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHiddenConfigBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHiddenConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.hidden_title)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.targetInput.setText("")
        binding.statusText.text = if (Prefs.hasTarget(this)) {
            getString(R.string.status_configured)
        } else {
            getString(R.string.status_not_configured)
        }

        binding.saveButton.setOnClickListener { save() }
        binding.clearButton.setOnClickListener { clear() }
    }

    private fun save() {
        val entered = binding.targetInput.text.toString().trim()
        val digits = entered.filter { it.isDigit() }
        if (digits.length < 7) {
            Toast.makeText(this, getString(R.string.invalid_number), Toast.LENGTH_SHORT).show()
            return
        }
        Prefs.setTargetNumber(this, entered)
        binding.targetInput.setText("")
        binding.statusText.text = getString(R.string.status_configured)
        Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show()
    }

    private fun clear() {
        Prefs.clearTargetNumber(this)
        binding.targetInput.setText("")
        binding.statusText.text = getString(R.string.status_not_configured)
        Toast.makeText(this, getString(R.string.cleared), Toast.LENGTH_SHORT).show()
    }
}
