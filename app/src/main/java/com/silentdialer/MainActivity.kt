package com.silentdialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.silentdialer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    private val requestDialerRole =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateDefaultDialerHint()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDialPad()

        binding.callButton.setOnClickListener { placeCall() }
        binding.backspaceButton.setOnClickListener { onBackspace() }
        binding.backspaceButton.setOnLongClickListener {
            binding.numberDisplay.text = ""
            true
        }
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.setDefaultButton.setOnClickListener { requestDefaultDialer() }

        // If launched via a tel: intent, pre-fill the number.
        handleDialIntent(intent)

        requestRuntimePermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDialIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updateDefaultDialerHint()
    }

    private fun handleDialIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "tel") {
            binding.numberDisplay.text = Uri.decode(data.schemeSpecificPart)
        }
    }

    private fun setupDialPad() {
        val keys = mapOf(
            binding.key0 to "0", binding.key1 to "1", binding.key2 to "2",
            binding.key3 to "3", binding.key4 to "4", binding.key5 to "5",
            binding.key6 to "6", binding.key7 to "7", binding.key8 to "8",
            binding.key9 to "9", binding.keyStar to "*", binding.keyHash to "#"
        )
        for ((button, digit) in keys) {
            button.setOnClickListener { append(digit) }
        }
        binding.key0.setOnLongClickListener {
            // Replace a trailing 0 with + (standard dialer behaviour).
            val text = binding.numberDisplay.text.toString()
            binding.numberDisplay.text = if (text.endsWith("0")) {
                text.dropLast(1) + "+"
            } else {
                "$text+"
            }
            true
        }
    }

    private fun append(s: String) {
        binding.numberDisplay.text = binding.numberDisplay.text.toString() + s
    }

    private fun onBackspace() {
        val text = binding.numberDisplay.text.toString()
        if (text.isNotEmpty()) {
            binding.numberDisplay.text = text.dropLast(1)
        }
    }

    private fun placeCall() {
        val number = binding.numberDisplay.text.toString().trim()
        if (number.isEmpty()) {
            toast(getString(R.string.enter_a_number))
            return
        }
        if (!isDefaultDialer()) {
            toast(getString(R.string.please_set_default))
            requestDefaultDialer()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            toast(getString(R.string.grant_call_permission))
            requestRuntimePermissions()
            return
        }

        val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val uri = Uri.fromParts("tel", number, null)
        try {
            telecomManager.placeCall(uri, Bundle())
        } catch (e: SecurityException) {
            toast(getString(R.string.call_failed))
        }
    }

    // --- Default dialer role ---------------------------------------------

    private fun isDefaultDialer(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        } else {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecomManager.defaultDialerPackage == packageName
        }
    }

    private fun requestDefaultDialer() {
        if (isDefaultDialer()) {
            updateDefaultDialerHint()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                requestDialerRole.launch(intent)
            }
        } else {
            @Suppress("DEPRECATION")
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                .putExtra(
                    TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                    packageName
                )
            requestDialerRole.launch(intent)
        }
    }

    private fun updateDefaultDialerHint() {
        binding.setDefaultButton.visibility =
            if (isDefaultDialer()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun requestRuntimePermissions() {
        val needed = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            needed.add(Manifest.permission.READ_PHONE_NUMBERS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val toRequest = needed.filter {
            ContextCompat.checkSelfPermission(this, it) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) {
            requestPermissions.launch(toRequest.toTypedArray())
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
