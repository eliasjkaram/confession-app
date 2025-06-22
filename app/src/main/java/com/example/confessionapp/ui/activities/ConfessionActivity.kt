package com.example.confessionapp.ui.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.confessionapp.databinding.ActivityConfessionBinding
import com.example.confessionapp.ui.adapters.ChatAdapter
import com.example.confessionapp.ui.viewmodels.CallStatus
import com.example.confessionapp.ui.viewmodels.ConfessionViewModel
import com.google.firebase.auth.FirebaseAuth
import java.util.UUID

class ConfessionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfessionBinding
    private val viewModel: ConfessionViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter

    private var priestId: String? = null
    private var priestDisplayName: String? = null
    private var currentRoomId: String? = null
    private var isCaller: Boolean = false // True if this user initiated the call (Confessor)

    companion object {
        private const val AUDIO_PERMISSION_REQUEST_CODE = 101
        const val EXTRA_PRIEST_ID = "PRIEST_ID"
        const val EXTRA_PRIEST_DISPLAY_NAME = "PRIEST_DISPLAY_NAME"
        const val EXTRA_ROOM_ID = "ROOM_ID" // Priest will receive this
        const val EXTRA_IS_CALLER = "IS_CALLER" // True for confessor, false for priest
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfessionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        priestId = intent.getStringExtra(EXTRA_PRIEST_ID)
        priestDisplayName = intent.getStringExtra(EXTRA_PRIEST_DISPLAY_NAME)
        currentRoomId = intent.getStringExtra(EXTRA_ROOM_ID)
        isCaller = intent.getBooleanExtra(EXTRA_IS_CALLER, false)


        if (currentUserId == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        chatAdapter = ChatAdapter(currentUserId!!)
        binding.rvChatMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvChatMessages.adapter = chatAdapter

        binding.tvConfessionWithLabel.text = if (isCaller) "Confession with: ${priestDisplayName ?: "Priest"}" else "Confession with: Anonymous Confessor"
        binding.etChatMessage.isEnabled = false
        binding.btnSendChatMessage.isEnabled = false
        binding.btnToggleMute.isEnabled = false


        if (checkAudioPermission()) {
            initializeCall()
        } else {
            requestAudioPermission()
        }

        setupObservers()
        setupClickListeners()
    }

    private val currentUserId: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid


    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeCall()
            } else {
                // Permission denied
                AlertDialog.Builder(this)
                    .setTitle("Permission Denied")
                    .setMessage("Audio permission is required to start a confession. Please grant the permission to continue. You can also grant it from app settings.")
                    .setPositiveButton("Retry") { _, _ ->
                        requestAudioPermission()
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                        finish() // Close activity if user cancels
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun initializeCall() {
        if (currentRoomId == null && isCaller && priestId != null) {
            // Confessor (caller) generates room ID
            currentRoomId = UUID.randomUUID().toString()
            // TODO: How does the priest get this room ID?
            // This needs a mechanism. E.g., confessor writes it to a specific DB path priest is listening on.
            // For now, this activity will be started on priest side with roomID via notification/intent.
            // This part of signaling (room initiation) is simplified here.
            Log.d("ConfessionActivity", "Confessor initiating call. Room ID: $currentRoomId, Priest ID: $priestId")

        } else if (currentRoomId == null && !isCaller) {
             Toast.makeText(this, "Room ID missing for priest.", Toast.LENGTH_LONG).show()
             Log.e("ConfessionActivity", "Priest side: Room ID is null. Cannot start call.")
             finish()
             return
        }

        viewModel.initSession(currentRoomId!!, isCaller, priestDisplayName)
    }


    private fun setupObservers() {
        viewModel.callStatus.observe(this) { status ->
            Log.d("ConfessionActivity", "Call status changed: $status")
            binding.tvCallStatus.text = "Status: ${status.name}"
            when (status) {
                CallStatus.CONNECTED -> {
                    binding.progressBarConfession.visibility = View.GONE
                    binding.llCallControls.visibility = View.VISIBLE
                    binding.llChatInput.visibility = View.VISIBLE
                    binding.etChatMessage.isEnabled = true
                    binding.btnSendChatMessage.isEnabled = true
                    binding.btnToggleMute.isEnabled = true
                    binding.btnEndCall.isEnabled = true
                }
                CallStatus.DISCONNECTED, CallStatus.ERROR -> {
                    binding.progressBarConfession.visibility = View.GONE
                    Toast.makeText(this, "Call ${status.name.lowercase()}.", Toast.LENGTH_SHORT).show()
                    binding.btnEndCall.text = "Call Ended"
                    binding.btnEndCall.isEnabled = false
                    binding.btnToggleMute.isEnabled = false
                    binding.etChatMessage.isEnabled = false
                    binding.btnSendChatMessage.isEnabled = false
                }
                CallStatus.CONNECTING, CallStatus.IDLE -> { // IDLE might be initial state before init
                    binding.progressBarConfession.visibility = View.VISIBLE
                    binding.etChatMessage.isEnabled = false
                    binding.btnSendChatMessage.isEnabled = false
                    binding.btnToggleMute.isEnabled = false
                    binding.btnEndCall.isEnabled = false // Only enable end call when connected or trying to connect
                }
            }
        }

        viewModel.chatMessages.observe(this) { messages ->
            chatAdapter.submitList(messages) {
                binding.rvChatMessages.smoothScrollToPosition(chatAdapter.itemCount -1)
            }
        }

        viewModel.isMuted.observe(this) { isMuted ->
            binding.btnToggleMute.text = if (isMuted) "Unmute" else "Mute"
        }
    }

    private fun setupClickListeners() {
        binding.btnEndCall.setOnClickListener {
            viewModel.endCall()
        }

        binding.btnToggleMute.setOnClickListener {
            viewModel.toggleMute()
        }

        binding.btnSendChatMessage.setOnClickListener {
            val messageText = binding.etChatMessage.text.toString().trim()
            if (messageText.isNotEmpty()) {
                // Pass priest's display name if this user is the confessor (caller)
                // If this user is the priest, they don't need to pass confessor's name (it's anonymous)
                val effectivePriestDisplayName = if(isCaller) priestDisplayName else null
                viewModel.sendChatMessage(messageText, effectivePriestDisplayName)
                binding.etChatMessage.text.clear()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.endCall() // Ensure call is ended and resources are cleaned up
    }
}
