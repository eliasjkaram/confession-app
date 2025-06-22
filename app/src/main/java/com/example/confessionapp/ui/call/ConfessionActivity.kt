package com.example.confessionapp.ui.call

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.confessionapp.R // Import R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class ConfessionActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    // UI Elements
    private lateinit var roomIdTextView: TextView
    private lateinit var endCallButton: Button

    private var roomId: String? = null
    private var invitationId: String? = null
    private var priestId: String? = null
    private var isPriest: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confession)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        roomIdTextView = findViewById(R.id.roomIdTextView)
        endCallButton = findViewById(R.id.endCallButton)

        roomId = intent.getStringExtra("roomId")
        invitationId = intent.getStringExtra("invitationId")
        priestId = intent.getStringExtra("priestId")

        if (roomId == null || invitationId == null || priestId == null) {
            Log.e("ConfessionActivity", "Missing critical call information in Intent. Finishing activity.")
            roomIdTextView.text = "Error: Missing call info."
            endCallButton.isEnabled = false // Or finish() directly as below
            finish()
            return
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e("ConfessionActivity", "User not signed in. Finishing activity.")
            roomIdTextView.text = "Error: User not signed in."
            finish()
            return
        }
        isPriest = currentUser.uid == priestId

        Log.d("ConfessionActivity", "Launched. Room: $roomId, Invitation: $invitationId, Priest: $priestId, IsPriest: $isPriest")
        roomIdTextView.text = "Room ID: $roomId\nRole: ${if (isPriest) "Priest" else "Confessor"}"

        // TODO: Initialize WebRTC connection here using the roomId

        if (isPriest) {
            markInvitationAsAnswered()
        }

        endCallButton.setOnClickListener {
            endCall()
        }
    }

    private fun markInvitationAsAnswered() {
        // Update status to "answered" only if it's not already "answered" or "completed" etc.
        // This prevents unnecessary writes if re-entering activity or race conditions.
        val invitationRef = database.getReference("call_invitations").child(priestId!!).child(invitationId!!)
        invitationRef.child("status").get().addOnSuccessListener { snapshot ->
            val currentStatus = snapshot.getValue(String::class.java)
            if (currentStatus != null && (currentStatus == "accepted" || currentStatus == "pending")) { // "pending" could be if confessor joined before priest accepted fully
                invitationRef.child("status").setValue("answered")
                    .addOnSuccessListener {
                        Log.d("ConfessionActivity", "Priest marked invitation $invitationId as 'answered'.")
                    }
                    .addOnFailureListener { e ->
                        Log.e("ConfessionActivity", "Failed to mark invitation $invitationId as 'answered'.", e)
                    }
            } else {
                Log.d("ConfessionActivity", "Invitation $invitationId status is already '$currentStatus', not updating to 'answered'.")
            }
        }.addOnFailureListener {
             Log.e("ConfessionActivity", "Failed to get current status for invitation $invitationId before marking as answered.", it)
        }
    }

    private fun endCall() {
        Log.d("ConfessionActivity", "End Call button clicked for room $roomId.")
        // Update status to "completed" or remove the invitation
        // For simplicity, let's set to "completed"
        if (priestId != null && invitationId != null) {
            database.getReference("call_invitations").child(priestId!!).child(invitationId!!).child("status")
                .setValue("completed")
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("ConfessionActivity", "Invitation $invitationId marked as 'completed'.")
                    } else {
                        Log.w("ConfessionActivity", "Failed to mark invitation $invitationId as 'completed'.", task.exception)
                    }
                    // Regardless of success, finish the activity
                    finishAndCleanup()
                }
        } else {
            // Should not happen if initial checks pass
            finishAndCleanup()
        }
    }

    private fun finishAndCleanup() {
        // TODO: Add any WebRTC cleanup logic here (disconnect, release resources)
        println("ConfessionActivity: Call ended for room $roomId. Finishing activity.")
        finish()
    }

    // Simulating end call button click for now if needed for testing from code
    // public fun performEndCall() { endCall() }
}
