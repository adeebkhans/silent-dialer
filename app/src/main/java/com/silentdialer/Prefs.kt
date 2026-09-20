package com.silentdialer

import android.content.Context

/**
 * Thin wrapper around SharedPreferences that stores the single "target" number
 * whose outgoing-call log entries should be automatically removed.
 *
 * Note: by design the saved number is never surfaced back to the UI. The
 * settings screen only ever *overwrites* it (write-only), so the value cannot
 * be read out of the app once set.
 */
object Prefs {
    private const val FILE = "silent_dialer_prefs"
    private const val KEY_TARGET = "target_number"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Store the raw number the user typed (may include +91, spaces, etc.). */
    fun setTargetNumber(context: Context, number: String) {
        prefs(context).edit().putString(KEY_TARGET, number.trim()).apply()
    }

    /** Remove the target entirely (nothing will be auto-deleted afterwards). */
    fun clearTargetNumber(context: Context) {
        prefs(context).edit().remove(KEY_TARGET).apply()
    }

    /** Internal read used only by the deletion logic; not exposed to any screen. */
    fun getTargetNumber(context: Context): String? =
        prefs(context).getString(KEY_TARGET, null)

    /** Whether a target has been configured at all. */
    fun hasTarget(context: Context): Boolean =
        !getTargetNumber(context).isNullOrBlank()

    /**
     * Normalise a phone number to its last 10 digits so that +91XXXXXXXXXX,
     * 0XXXXXXXXXX and the bare XXXXXXXXXX forms all compare equal (Indian
     * numbering). Returns null when there are not enough digits to be useful.
     */
    fun lastTenDigits(raw: String?): String? {
        if (raw == null) return null
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 7) return null
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }
}
