package com.silentdialer

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

/**
 * The telecom framework binds to this service while our app is the default
 * dialer. It is the single source of truth for live calls; it forwards
 * everything to [CallManager], keeps the ongoing-call notification current, and
 * drives the in-call UI.
 */
class SilentInCallService : InCallService() {

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.inCallService = this
        // Keep the notification refreshed on every subsequent state change.
        CallManager.notificationHook = { refreshNotification() }
        CallManager.onCallAdded(call)

        refreshNotification()
        // Directly launch the in-call UI for the foreground/outgoing case; the
        // full-screen intent on the notification covers the background case.
        InCallActivity.start(this)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.onCallRemoved(applicationContext, call)

        if (CallManager.getCalls().isEmpty()) {
            CallManager.notificationHook = null
            CallNotifier.cancel(this)
        } else {
            refreshNotification()
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        CallManager.onAudioStateChanged()
    }

    /** Rebuild the ongoing/incoming call notification from the current state. */
    private fun refreshNotification() {
        val call = CallManager.primaryCall() ?: return
        val state = call.stateCompat()
        val incoming = state == Call.STATE_RINGING
        val active = state == Call.STATE_ACTIVE
        val number = CallManager.primaryNumber()?.takeIf { it.isNotBlank() }
            ?: getString(R.string.unknown_number)
        val connectTime = call.details?.connectTimeMillis ?: 0L

        CallNotifier.update(
            context = this,
            number = number,
            incoming = incoming,
            active = active,
            connectTimeMillis = connectTime,
            stateLabel = stateLabel(state)
        )
    }

    private fun stateLabel(state: Int): String = when (state) {
        Call.STATE_NEW, Call.STATE_CONNECTING -> getString(R.string.state_connecting)
        Call.STATE_DIALING -> getString(R.string.state_dialing)
        Call.STATE_RINGING -> getString(R.string.state_incoming)
        Call.STATE_ACTIVE -> getString(R.string.state_active)
        Call.STATE_HOLDING -> getString(R.string.state_holding)
        Call.STATE_DISCONNECTING -> getString(R.string.state_disconnecting)
        Call.STATE_DISCONNECTED -> getString(R.string.state_disconnected)
        else -> getString(R.string.state_active)
    }
}
