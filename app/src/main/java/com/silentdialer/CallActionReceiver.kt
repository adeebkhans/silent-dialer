package com.silentdialer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles the inline actions on the ongoing-call notification (Answer / End),
 * so the user can control the call without opening the in-call screen.
 */
class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ANSWER -> CallManager.answerPrimary()
            ACTION_HANGUP -> CallManager.hangupPrimary()
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.silentdialer.action.ANSWER"
        const val ACTION_HANGUP = "com.silentdialer.action.HANGUP"

        fun pendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, CallActionReceiver::class.java).setAction(action)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            // Distinct request codes so the two actions don't collapse together.
            val requestCode = if (action == ACTION_ANSWER) 1 else 2
            return PendingIntent.getBroadcast(context, requestCode, intent, flags)
        }
    }
}
