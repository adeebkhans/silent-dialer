package com.silentdialer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Posts and keeps up to date a high-priority call notification. It carries a
 * full-screen intent (so the in-call UI can appear from the background or a
 * locked screen), a tap target that returns to the in-call screen, a live
 * duration chronometer while connected, and inline actions:
 *  - incoming call: Answer / Decline
 *  - ongoing call:  End call
 *
 * This is what lets the user get back to (or hang up) a call after navigating
 * away from the in-call screen.
 */
object CallNotifier {

    private const val CHANNEL_ID = "silent_dialer_calls"
    private const val NOTIFICATION_ID = 42

    /**
     * Create or refresh the call notification for the current primary call.
     *
     * @param incoming        the call is ringing (show Answer/Decline)
     * @param active          the call is connected (show a running timer)
     * @param connectTimeMillis wall-clock time the call connected (for the timer)
     * @param stateLabel      short human-readable state used as the body text
     */
    fun update(
        context: Context,
        number: String,
        incoming: Boolean,
        active: Boolean,
        connectTimeMillis: Long,
        stateLabel: String
    ) {
        ensureChannel(context)

        val returnIntent = Intent(context, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentPending = PendingIntent.getActivity(context, 0, returnIntent, piFlags)

        val body = if (incoming) {
            context.getString(R.string.notif_incoming)
        } else {
            stateLabel + "  ·  " + context.getString(R.string.notif_tap_to_return)
        }

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(number)
            .setContentText(body)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_CALL)
            .setContentIntent(contentPending)

        // Live call timer.
        if (active && connectTimeMillis > 0L) {
            builder.setUsesChronometer(true)
            builder.setWhen(connectTimeMillis)
            builder.setShowWhen(true)
        } else {
            builder.setShowWhen(false)
        }

        if (incoming) {
            // Pop the full-screen in-call UI for a ringing call.
            builder.setFullScreenIntent(contentPending, true)
            builder.addAction(
                R.drawable.ic_call_end,
                context.getString(R.string.notif_action_decline),
                CallActionReceiver.pendingIntent(context, CallActionReceiver.ACTION_HANGUP)
            )
            builder.addAction(
                R.drawable.ic_call,
                context.getString(R.string.notif_action_answer),
                CallActionReceiver.pendingIntent(context, CallActionReceiver.ACTION_ANSWER)
            )
        } else {
            builder.addAction(
                R.drawable.ic_call_end,
                context.getString(R.string.notif_action_hangup),
                CallActionReceiver.pendingIntent(context, CallActionReceiver.ACTION_HANGUP)
            )
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_calls),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notif_channel_calls_desc)
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}
