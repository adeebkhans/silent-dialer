package com.silentdialer

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

/**
 * The telecom framework binds to this service while our app is the default
 * dialer. It is the single source of truth for live calls; it forwards
 * everything to [CallManager] and drives the in-call UI.
 */
class SilentInCallService : InCallService() {

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.inCallService = this
        CallManager.onCallAdded(call)

        val incoming = call.stateCompat() == Call.STATE_RINGING
        // Post a full-screen-intent notification (required to launch an activity
        // from the background on modern Android) and also attempt a direct
        // launch for the foreground/outgoing case.
        CallNotifier.showIncall(this, incoming)
        InCallActivity.start(this)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.onCallRemoved(applicationContext, call)

        if (CallManager.getCalls().isEmpty()) {
            CallNotifier.cancel(this)
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        CallManager.onAudioStateChanged()
    }
}
