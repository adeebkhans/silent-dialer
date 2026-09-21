package com.silentdialer

import android.content.Context
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

/**
 * Process-wide holder for the live [Call] objects and a bridge to the bound
 * [InCallService] for audio routing (mute / speaker). The in-call UI observes
 * this object; the service feeds it.
 */
object CallManager {

    interface Listener {
        fun onCallsChanged()
        fun onAudioStateChanged()
    }

    private val calls = mutableListOf<Call>()
    private var listener: Listener? = null

    /**
     * Fired on every call/state change so the bound service can refresh the
     * ongoing-call notification even while the in-call UI is not in front.
     */
    var notificationHook: (() -> Unit)? = null

    /** Set by the service while it is bound. */
    var inCallService: InCallService? = null

    /** Remembers, per call, whether it was an outgoing (dialed) call. */
    private val outgoingCalls = mutableSetOf<Call>()

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            listener?.onCallsChanged()
            notificationHook?.invoke()
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            listener?.onCallsChanged()
            notificationHook?.invoke()
        }
    }

    fun setListener(l: Listener?) {
        listener = l
    }

    fun getCalls(): List<Call> = calls.toList()

    /** The call the UI should treat as primary (last active, else last added). */
    fun primaryCall(): Call? {
        return calls.firstOrNull { it.stateCompat() == Call.STATE_ACTIVE }
            ?: calls.firstOrNull { it.stateCompat() == Call.STATE_RINGING }
            ?: calls.lastOrNull()
    }

    fun heldCall(): Call? =
        calls.firstOrNull { it.stateCompat() == Call.STATE_HOLDING }

    /** Dialed/caller number of the primary call, or null when unavailable. */
    fun primaryNumber(): String? = primaryCall()?.let { numberOf(it) }

    fun onCallAdded(call: Call) {
        calls.add(call)
        call.registerCallback(callback)
        if (isOutgoing(call)) {
            outgoingCalls.add(call)
        }
        listener?.onCallsChanged()
        notificationHook?.invoke()
    }

    /** Answer the ringing call (used by the notification action). */
    fun answerPrimary() {
        val call = calls.firstOrNull { it.stateCompat() == Call.STATE_RINGING } ?: primaryCall()
        call?.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
    }

    /** Reject a ringing call or disconnect an ongoing one (notification action). */
    fun hangupPrimary() {
        val call = primaryCall() ?: return
        if (call.stateCompat() == Call.STATE_RINGING) {
            call.reject(false, null)
        } else {
            call.disconnect()
        }
    }

    fun onCallRemoved(context: Context, call: Call) {
        call.unregisterCallback(callback)
        calls.remove(call)

        val wasOutgoing = outgoingCalls.remove(call)
        if (wasOutgoing) {
            maybeDeleteFromCallLog(context, call)
        }
        listener?.onCallsChanged()
        notificationHook?.invoke()
    }

    fun onAudioStateChanged() {
        listener?.onAudioStateChanged()
    }

    // --- Audio helpers ---------------------------------------------------

    fun isMuted(): Boolean =
        inCallService?.callAudioState?.isMuted == true

    fun setMuted(muted: Boolean) {
        inCallService?.setMuted(muted)
    }

    fun isSpeakerOn(): Boolean =
        inCallService?.callAudioState?.route == CallAudioState.ROUTE_SPEAKER

    fun setSpeaker(on: Boolean) {
        val route = if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
        inCallService?.setAudioRoute(route)
    }

    // --- Direction detection --------------------------------------------

    private fun isOutgoing(call: Call): Boolean {
        // Most reliable on API 29+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (call.details.callDirection) {
                Call.Details.DIRECTION_OUTGOING -> return true
                Call.Details.DIRECTION_INCOMING -> return false
            }
        }
        // Fallback: an outgoing call is added in a connecting/dialing state,
        // whereas an incoming call is added in the ringing state.
        return when (call.stateCompat()) {
            Call.STATE_CONNECTING, Call.STATE_DIALING -> true
            else -> false
        }
    }

    private fun numberOf(call: Call): String? =
        call.details?.handle?.schemeSpecificPart

    private fun maybeDeleteFromCallLog(context: Context, call: Call) {
        val target = Prefs.lastTenDigits(Prefs.getTargetNumber(context)) ?: return
        val callNumber = Prefs.lastTenDigits(numberOf(call)) ?: return
        if (target == callNumber) {
            // Android writes the call-log row *after* disconnect, so we retry.
            CallLogCleaner.deleteWithRetry(context.applicationContext, target)
        }
    }
}

/** Compat accessor for [Call.getState] which is deprecated on newer APIs. */
fun Call.stateCompat(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        details.state
    } else {
        @Suppress("DEPRECATION")
        state
    }
}
