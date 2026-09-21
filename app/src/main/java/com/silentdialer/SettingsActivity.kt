package com.silentdialer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.silentdialer.databinding.ActivitySettingsBinding

/**
 * Ordinary-looking "Call settings" screen. It is a decoy: the rows are the kind
 * of options any dialer exposes and reveal nothing about the hidden call-log
 * behaviour. The real configuration lives in [HiddenConfigActivity], reachable
 * only via the secret keypad code.
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
        binding.toolbar.setNavigationOnClickListener { finish() }
    }
}
