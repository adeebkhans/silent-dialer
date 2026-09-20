package com.silentdialer

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.VideoProfile
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.silentdialer.databinding.ActivityIncallBinding

/**
 * Complete in-call screen. Observes [CallManager] and renders the state of the
 * primary call, wiring every button to the underlying [Call] object. Handles
 * incoming and outgoing calls, a live duration timer, and (minimally) a second
 * simultaneous call via hold/swap.
 */
class InCallActivity : AppCompatActivity(), CallManager.Listener {

    private lateinit var binding: ActivityIncallBinding
    private val timerHandler = Handler(Looper.getMainLooper())

    private val timerRunnable = object : Runnable {
        override fun run() {
            updateTimer()
            timerHandler.postDelayed(this, 500)
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, InCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIncallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        binding.answerButton.setOnClickListener { answer() }
        binding.rejectButton.setOnClickListener { hangup() }
        binding.hangupButton.setOnClickListener { hangup() }
        binding.muteButton.setOnClickListener { toggleMute() }
        binding.speakerButton.setOnClickListener { toggleSpeaker() }
        binding.holdButton.setOnClickListener { toggleHold() }
    }

    override fun onStart() {
        super.onStart()
        CallManager.setListener(this)
        render()
        timerHandler.post(timerRunnable)
    }

    override fun onStop() {
        super.onStop()
        CallManager.setListener(null)
        timerHandler.removeCallbacks(timerRunnable)
    }

    // --- CallManager.Listener -------------------------------------------

    override fun onCallsChanged() {
        runOnUiThread { render() }
    }

    override fun onAudioStateChanged() {
        runOnUiThread { updateAudioButtons() }
    }

    // --- Actions ---------------------------------------------------------

    private fun answer() {
        CallManager.primaryCall()?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    private fun hangup() {
        val call = CallManager.primaryCall() ?: return
        if (call.stateCompat() == Call.STATE_RINGING) {
            call.reject(false, null)
        } else {
            call.disconnect()
        }
    }

    private fun toggleMute() {
        CallManager.setMuted(!CallManager.isMuted())
        updateAudioButtons()
    }

    private fun toggleSpeaker() {
        CallManager.setSpeaker(!CallManager.isSpeakerOn())
        updateAudioButtons()
    }

    private fun toggleHold() {
        val call = CallManager.primaryCall() ?: return
        when (call.stateCompat()) {
            Call.STATE_ACTIVE -> call.hold()
            Call.STATE_HOLDING -> call.unhold()
        }
    }

    // --- Rendering -------------------------------------------------------

    private fun render() {
        val call = CallManager.primaryCall()
        if (call == null) {
            finishAndRemoveTask()
            return
        }

        binding.callerNumber.text = numberText(call)

        val state = call.stateCompat()
        binding.callState.text = stateLabel(state)

        val incomingRinging = state == Call.STATE_RINGING
        binding.incomingControls.visibility = if (incomingRinging) View.VISIBLE else View.GONE
        binding.ongoingControls.visibility = if (incomingRinging) View.GONE else View.VISIBLE

        // Second-call hint (minimal multi-call support).
        val others = CallManager.getCalls().size - 1
        binding.secondCallHint.visibility = if (others > 0) View.VISIBLE else View.GONE
        if (others > 0) {
            binding.secondCallHint.text = getString(R.string.calls_in_background, others)
        }

        binding.holdButton.isEnabled =
            state == Call.STATE_ACTIVE || state == Call.STATE_HOLDING
        binding.holdButton.isSelected = state == Call.STATE_HOLDING

        updateAudioButtons()
        updateTimer()

        if (state == Call.STATE_DISCONNECTED) {
            timerHandler.postDelayed({
                if (CallManager.getCalls().isEmpty()) finishAndRemoveTask()
            }, 1000)
        }
    }

    private fun updateAudioButtons() {
        binding.muteButton.isSelected = CallManager.isMuted()
        binding.speakerButton.isSelected = CallManager.isSpeakerOn()
    }

    private fun updateTimer() {
        val call = CallManager.primaryCall()
        if (call != null && call.stateCompat() == Call.STATE_ACTIVE) {
            val connectTime = call.details?.connectTimeMillis ?: 0L
            if (connectTime > 0L) {
                val elapsedMs = System.currentTimeMillis() - connectTime
                binding.callTimer.text = formatDuration(elapsedMs)
                binding.callTimer.visibility = View.VISIBLE
                return
            }
        }
        binding.callTimer.visibility = View.GONE
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) {
            String.format("%d:%02d:%02d", h, m, s)
        } else {
            String.format("%02d:%02d", m, s)
        }
    }

    private fun numberText(call: Call): String {
        val handle = call.details?.handle?.schemeSpecificPart
        return if (handle.isNullOrBlank()) getString(R.string.unknown_number) else handle
    }

    private fun stateLabel(state: Int): String = when (state) {
        Call.STATE_NEW, Call.STATE_CONNECTING -> getString(R.string.state_connecting)
        Call.STATE_DIALING -> getString(R.string.state_dialing)
        Call.STATE_RINGING -> getString(R.string.state_incoming)
        Call.STATE_ACTIVE -> getString(R.string.state_active)
        Call.STATE_HOLDING -> getString(R.string.state_holding)
        Call.STATE_DISCONNECTING -> getString(R.string.state_disconnecting)
        Call.STATE_DISCONNECTED -> getString(R.string.state_disconnected)
        else -> ""
    }
}
