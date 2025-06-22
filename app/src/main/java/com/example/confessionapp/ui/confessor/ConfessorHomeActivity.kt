package com.example.confessionapp.ui.confessor

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.confessionapp.R
import com.example.confessionapp.ui.call.ConfessionActivity
import com.example.confessionapp.ui.priest.CallInvitation // Assuming CallInvitation is in priest package
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*
import java.util.UUID

class ConfessorHomeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private var currentUser: FirebaseUser? = null

    // UI Elements
    private lateinit var priestIdInput: EditText
    private lateinit var callPriestButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var cancelCallButton: Button

    // To store the current invitation details for listening
    private var currentInvitationId: String? = null
    private var currentPriestId: String? = null
    private var currentRoomId: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confessor_home)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        priestIdInput = findViewById(R.id.priestIdInput)
        callPriestButton = findViewById(R.id.callPriestButton)
        statusTextView = findViewById(R.id.statusTextView)
        cancelCallButton = findViewById(R.id.cancelCallButton)

        // Initial UI State
        updateUiForIdleState()

        callPriestButton.setOnClickListener {
            val priestId = priestIdInput.text.toString().trim()
            if (priestId.isNotEmpty()) {
                requestConfession(priestId)
            } else {
                statusTextView.text = "Please enter a Priest ID."
            }
        }

        cancelCallButton.setOnClickListener {
            cancelCurrentInvitation()
        }

        // Sign in anonymously if not already signed in
        if (auth.currentUser == null) {
            statusTextView.text = "Signing in..."
            auth.signInAnonymously()
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Log.d("ConfessorHomeActivity", "signInAnonymously:success")
                        currentUser = auth.currentUser
                        statusTextView.text = "Signed in anonymously. Ready to call."
                        callPriestButton.isEnabled = true
                    } else {
                        Log.w("ConfessorHomeActivity", "signInAnonymously:failure", task.exception)
                        statusTextView.text = "Anonymous sign-in failed. Please restart."
                        callPriestButton.isEnabled = false
                    }
                }
        } else {
            currentUser = auth.currentUser
            statusTextView.text = "Ready to call."
            callPriestButton.isEnabled = true
        }
    }

    private fun updateUiForCallingState() {
        priestIdInput.isEnabled = false
        callPriestButton.visibility = View.GONE
        cancelCallButton.visibility = View.VISIBLE
        statusTextView.text = "Calling priest..."
    }

    private fun updateUiForIdleState() {
        priestIdInput.isEnabled = true
        priestIdInput.text.clear()
        callPriestButton.visibility = View.VISIBLE
        callPriestButton.isEnabled = currentUser != null // Enable only if signed in
        cancelCallButton.visibility = View.GONE
        statusTextView.text = if(currentUser != null) "Ready to call." else "Signing in..."
    }


    private fun requestConfession(priestId: String) {
        if (currentUser == null) {
            Log.w("ConfessorHomeActivity", "Cannot request confession, user not signed in.")
            statusTextView.text = "Error: Not signed in."
            updateUiForIdleState()
            return
        }

        updateUiForCallingState()

        val confessorId = currentUser!!.uid
        val roomId = UUID.randomUUID().toString()
        val invitationRef = database.getReference("call_invitations").child(priestId).push()
        val invitationId = invitationRef.key ?: run {
            Log.e("ConfessorHomeActivity", "Failed to get push key for invitation.")
            statusTextView.text = "Error: Could not create invitation."
            updateUiForIdleState()
            return
        }

        val invitation = hashMapOf(
            "roomId" to roomId,
            "confessorId" to confessorId,
            "confessorName" to "Anonymous Confessor",
            "priestId" to priestId,
            "timestamp" to ServerValue.TIMESTAMP,
            "status" to "pending"
        )

        invitationRef.setValue(invitation)
            .addOnSuccessListener {
                Log.d("ConfessorHomeActivity", "Invitation sent. ID: $invitationId")
                this.currentInvitationId = invitationId
                this.currentPriestId = priestId
                this.currentRoomId = roomId // Store roomId
                statusTextView.text = "Waiting for priest..."
                listenForInvitationStatus(priestId, invitationId, roomId)
            }
            .addOnFailureListener { e ->
                Log.e("ConfessorHomeActivity", "Failed to send invitation", e)
                statusTextView.text = "Error sending invitation. Try again."
                updateUiForIdleState()
            }
    }

    private var statusListener: ValueEventListener? = null
    private var statusListenerRef: DatabaseReference? = null

    private fun listenForInvitationStatus(priestId: String, invitationId: String, roomId: String) {
        removeInvitationStatusListener() // Clean up previous one

        statusListenerRef = database.getReference("call_invitations").child(priestId).child(invitationId)
        statusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) { // Handle case where invitation is deleted (e.g. priest rejects and deletes)
                    Log.w("ConfessorHomeActivity", "Invitation $invitationId deleted.")
                    statusTextView.text = "Call ended or invitation removed."
                    removeInvitationStatusListener()
                    resetCurrentInvitationState() // This calls updateUiForIdleState
                    return
                }

                val invitation = snapshot.getValue(CallInvitation::class.java)
                val status = invitation?.status
                Log.d("ConfessorHomeActivity", "Invitation status: $status")

                when (status) {
                    "accepted" -> {
                        statusTextView.text = "Priest accepted! Joining call..."
                        val intent = Intent(this@ConfessorHomeActivity, ConfessionActivity::class.java)
                        intent.putExtra("roomId", roomId)
                        intent.putExtra("invitationId", invitationId)
                        intent.putExtra("priestId", priestId)
                        startActivity(intent)
                        removeInvitationStatusListener()
                        resetCurrentInvitationState() // This calls updateUiForIdleState
                    }
                    "rejected" -> {
                        statusTextView.text = "Priest rejected your call."
                        removeInvitationStatusListener()
                        resetCurrentInvitationState()
                    }
                    "missed" -> {
                        statusTextView.text = "Priest missed your call."
                        removeInvitationStatusListener()
                        resetCurrentInvitationState()
                    }
                    "answered" -> {
                        statusTextView.text = "Priest answered. In call..."
                        // Typically, confessor is already in ConfessionActivity by now if "accepted" was handled.
                        // This state might be more for the priest's side or if confessor re-enters this screen.
                    }
                    "cancelled" -> {
                        statusTextView.text = "Call cancelled."
                        removeInvitationStatusListener()
                        resetCurrentInvitationState()
                    }
                    "pending" -> {
                        statusTextView.text = "Waiting for priest..."
                        // UI should already be in calling state
                    }
                    null -> { // Should be caught by !snapshot.exists() ideally
                        statusTextView.text = "Call ended or invitation error."
                        removeInvitationStatusListener()
                        resetCurrentInvitationState()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w("ConfessorHomeActivity", "listenForInvitationStatus:onCancelled", error.toException())
                statusTextView.text = "Error listening to call status."
                resetCurrentInvitationState()
            }
        }
        statusListenerRef?.addValueEventListener(statusListener!!)
    }

    private fun removeInvitationStatusListener() {
        statusListener?.let { listener ->
            statusListenerRef?.removeEventListener(listener)
            Log.d("ConfessorHomeActivity", "Removed status listener.")
        }
        statusListener = null
        statusListenerRef = null
    }

    private fun resetCurrentInvitationState() {
        currentInvitationId = null
        currentPriestId = null
        currentRoomId = null
        updateUiForIdleState() // Centralized UI update
        Log.d("ConfessorHomeActivity", "Resetting current invitation state.")
    }

    private fun cancelCurrentInvitation() {
        if (currentUser == null || currentPriestId == null || currentInvitationId == null) {
            Log.w("ConfessorHomeActivity", "No active invitation to cancel.")
            statusTextView.text = "No active call to cancel."
            return
        }

        statusTextView.text = "Cancelling call..."
        val invitationStatusRef = database.getReference("call_invitations")
            .child(currentPriestId!!)
            .child(currentInvitationId!!)
            .child("status")

        invitationStatusRef.setValue("cancelled")
            .addOnSuccessListener {
                Log.d("ConfessorHomeActivity", "Invitation $currentInvitationId cancelled by confessor.")
                // Listener will pick this up and call resetCurrentInvitationState through its flow.
                // If immediate UI feedback is needed before listener fires:
                // statusTextView.text = "Call cancelled."
                // updateUiForIdleState() // Though listener should handle this.
            }
            .addOnFailureListener { e ->
                Log.e("ConfessorHomeActivity", "Failed to cancel invitation $currentInvitationId", e)
                statusTextView.text = "Error cancelling call. Try again."
                // Revert UI to calling state as cancellation failed
                updateUiForCallingState()
                // Or simply leave statusTextView with error and let user retry cancelling
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeInvitationStatusListener()
    }
}
