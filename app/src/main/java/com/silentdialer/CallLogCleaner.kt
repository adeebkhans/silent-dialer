package com.silentdialer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Removes the system call-log entry for a given (last-10-digit) number.
 *
 * The system does not write the call-log row until *after* the call fully
 * disconnects, and the exact timing varies by device. So we poll: try to delete
 * matching rows, and if none were found yet, wait and try again a handful of
 * times before giving up.
 */
object CallLogCleaner {

    private const val TAG = "CallLogCleaner"
    private const val MAX_ATTEMPTS = 8
    private const val FIRST_DELAY_MS = 800L
    private const val RETRY_DELAY_MS = 1200L

    fun deleteWithRetry(context: Context, lastTenDigits: String) {
        if (!hasWriteCallLog(context) || !hasReadCallLog(context)) {
            Log.w(TAG, "Missing call-log permissions; cannot clean entry.")
            return
        }

        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            var attempt = 0
            override fun run() {
                attempt++
                val deleted = tryDeleteOnce(context, lastTenDigits)
                if (deleted > 0) {
                    Log.d(TAG, "Deleted $deleted call-log row(s) on attempt $attempt.")
                    return
                }
                if (attempt < MAX_ATTEMPTS) {
                    handler.postDelayed(this, RETRY_DELAY_MS)
                } else {
                    Log.w(TAG, "Gave up after $attempt attempts; no matching row found.")
                }
            }
        }
        handler.postDelayed(runnable, FIRST_DELAY_MS)
    }

    /**
     * Deletes every call-log row whose number ends with [lastTenDigits].
     * Returns the number of rows deleted.
     */
    private fun tryDeleteOnce(context: Context, lastTenDigits: String): Int {
        val resolver = context.contentResolver
        var deleted = 0
        try {
            resolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER),
                null,
                null,
                CallLog.Calls.DATE + " DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
                val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                while (cursor.moveToNext()) {
                    val rowNumber = cursor.getString(numberIndex)
                    if (Prefs.lastTenDigits(rowNumber) == lastTenDigits) {
                        val id = cursor.getLong(idIndex)
                        val rows = resolver.delete(
                            CallLog.Calls.CONTENT_URI,
                            CallLog.Calls._ID + " = ?",
                            arrayOf(id.toString())
                        )
                        deleted += rows
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException while cleaning call log", e)
        } catch (e: Exception) {
            Log.w(TAG, "Error while cleaning call log", e)
        }
        return deleted
    }

    private fun hasWriteCallLog(context: Context) =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasReadCallLog(context: Context) =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
}
