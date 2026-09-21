package com.silentdialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.silentdialer.databinding.ActivityMainBinding

/**
 * Single-activity host for the One UI-style tabbed dialer: Keypad, Recents and
 * Contacts. It also owns the cross-cutting concerns the fragments need —
 * default-dialer role, runtime permissions and placing calls.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var keypadFragment: KeypadFragment
    private lateinit var recentsFragment: RecentsFragment
    private lateinit var contactsFragment: ContactsFragment
    private lateinit var activeFragment: Fragment

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshVisibleFragment()
        }

    private val requestDialerRole =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            keypadFragment.updateDefaultDialerHint()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fm = supportFragmentManager
        if (savedInstanceState == null) {
            keypadFragment = KeypadFragment()
            recentsFragment = RecentsFragment()
            contactsFragment = ContactsFragment()
            fm.beginTransaction()
                .add(R.id.fragmentContainer, contactsFragment, TAG_CONTACTS).hide(contactsFragment)
                .add(R.id.fragmentContainer, recentsFragment, TAG_RECENTS).hide(recentsFragment)
                .add(R.id.fragmentContainer, keypadFragment, TAG_KEYPAD)
                .commit()
            activeFragment = keypadFragment
        } else {
            // Reattach to the fragments the manager restored.
            keypadFragment = fm.findFragmentByTag(TAG_KEYPAD) as KeypadFragment
            recentsFragment = fm.findFragmentByTag(TAG_RECENTS) as RecentsFragment
            contactsFragment = fm.findFragmentByTag(TAG_CONTACTS) as ContactsFragment
            activeFragment = listOf(keypadFragment, recentsFragment, contactsFragment)
                .firstOrNull { !it.isHidden } ?: keypadFragment
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            val target = when (item.itemId) {
                R.id.nav_keypad -> keypadFragment
                R.id.nav_recents -> recentsFragment
                R.id.nav_contacts -> contactsFragment
                else -> return@setOnItemSelectedListener false
            }
            showFragment(target)
            true
        }

        handleDialIntent(intent)
        requestRuntimePermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDialIntent(intent)
    }

    private fun showFragment(target: Fragment) {
        if (target === activeFragment) return
        supportFragmentManager.beginTransaction()
            .hide(activeFragment)
            .show(target)
            .commit()
        activeFragment = target
    }

    private fun refreshVisibleFragment() {
        (activeFragment as? Refreshable)?.refresh()
    }

    private fun handleDialIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "tel") {
            val number = Uri.decode(data.schemeSpecificPart)
            keypadFragment.setNumber(number)
            binding.bottomNav.selectedItemId = R.id.nav_keypad
        }
    }

    // --- Shared helpers used by the fragments ---------------------------

    fun placeCall(rawNumber: String) {
        val number = rawNumber.trim()
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
            != PackageManager.PERMISSION_GRANTED
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

    fun isDefaultDialer(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_DIALER)
        } else {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecomManager.defaultDialerPackage == packageName
        }
    }

    fun requestDefaultDialer() {
        if (isDefaultDialer()) {
            keypadFragment.updateDefaultDialerHint()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                requestDialerRole.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
            }
        } else {
            @Suppress("DEPRECATION")
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            requestDialerRole.launch(intent)
        }
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
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) {
            requestPermissions.launch(toRequest.toTypedArray())
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    /** Implemented by list fragments that should reload after permission grants. */
    interface Refreshable {
        fun refresh()
    }

    companion object {
        private const val TAG_KEYPAD = "keypad"
        private const val TAG_RECENTS = "recents"
        private const val TAG_CONTACTS = "contacts"
    }
}
