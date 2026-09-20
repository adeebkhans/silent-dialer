package com.silentdialer

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.silentdialer.databinding.ActivitySettingsBinding

/**
 * Write-only configuration of the target number.
 *
 * The currently-saved number is deliberately NOT displayed or pre-filled: the
 * field always starts empty and typing + Save simply overwrites whatever was
 * stored. This means the hidden number can be changed but never read back from
 * the UI.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        // Field is intentionally left blank; we never read the stored value out.
        binding.targetInput.setText("")

        // Show only whether *a* number is configured, not the number itself.
        binding.statusText.text = if (Prefs.hasTarget(this)) {
            getString(R.string.status_configured)
        } else {
            getString(R.string.status_not_configured)
        }

        binding.saveButton.setOnClickListener { save() }
        binding.clearButton.setOnClickListener { clear() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
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
