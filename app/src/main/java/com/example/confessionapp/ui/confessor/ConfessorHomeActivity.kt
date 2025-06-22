package com.example.confessionapp.ui.confessor

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
// Temporarily commenting out R.layout.activity_confessor_home until layout files are handled.
// import com.example.confessionapp.R
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import java.util.UUID

class ConfessorHomeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private var currentUser: FirebaseUser? = null

    // To store the current invitation details for listening
    private var currentInvitationId: String? = null
    private var currentPriestId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContentView(R.layout.activity_confessor_home) // Assuming a layout with a button and EditText for priest ID

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance() // Ensure google-services.json is set up

        // Placeholder: Sign in anonymously if not already signed in
        // In a real app, this would be handled more robustly, perhaps in a splash screen or login activity
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Log.d("ConfessorHomeActivity", "signInAnonymously:success")
                        currentUser = auth.currentUser
                        // Update UI or enable call button now that user is signed in
                    } else {
                        Log.w("ConfessorHomeActivity", "signInAnonymously:failure", task.exception)
                        // Handle sign-in failure (e.g., show error, disable call functionality)
                    }
                }
        } else {
            currentUser = auth.currentUser
        }

        // Placeholder for UI interaction
        // Button click listener would call: requestConfession("some_priest_id_from_input")
        println("ConfessorHomeActivity Created and Firebase Initialized (conceptually)")

        // Example of how to initiate:
        // requestConfession("testPriestId123") // Replace with actual priest ID from UI input
    }

    private fun requestConfession(priestId: String) {
        if (currentUser == null) {
            Log.w("ConfessorHomeActivity", "Cannot request confession, user not signed in.")
            // Show message to user: "Please wait, establishing connection..." or "Sign-in failed, please restart."
            return
        }

        if (priestId.isBlank()) {
            Log.w("ConfessorHomeActivity", "Priest ID cannot be empty.")
            // Show message to user: "Please enter a Priest ID."
            return
        }

        val confessorId = currentUser!!.uid
        val roomId = UUID.randomUUID().toString()
        val invitationRef = database.getReference("call_invitations").child(priestId).push()
        val invitationId = invitationRef.key ?: run {
            Log.e("ConfessorHomeActivity", "Failed to get push key for invitation.")
            // Show error to user
            return
        }

        val invitation = hashMapOf(
            "roomId" to roomId,
            "confessorId" to confessorId,
            "confessorName" to "Anonymous Confessor", // Can be enhanced later
            "priestId" to priestId,
            "timestamp" to ServerValue.TIMESTAMP,
            "status" to "pending"
        )

        invitationRef.setValue(invitation)
            .addOnSuccessListener {
                Log.d("ConfessorHomeActivity", "Invitation sent successfully. Invitation ID: $invitationId")
                this.currentInvitationId = invitationId
                this.currentPriestId = priestId
                // Transition to waiting UI & start listening for status updates
                // listenForInvitationStatus(priestId, invitationId)
                println("Invitation sent. Room: $roomId. Waiting for priest...")
            }
            .addOnFailureListener { e ->
                Log.e("ConfessorHomeActivity", "Failed to send invitation", e)
                // Show error to user
            }
    }

    // Placeholder for listening logic (will be detailed in a later step)
    private var statusListener: ValueEventListener? = null
    private var statusListenerRef: DatabaseReference? = null

    private fun listenForInvitationStatus(priestId: String, invitationId: String, roomId: String) {
        // Clean up any previous listener before attaching a new one
        removeInvitationStatusListener()

        statusListenerRef = database.getReference("call_invitations").child(priestId).child(invitationId) // Listen to the whole invitation node
        statusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val invitation = snapshot.getValue(com.example.confessionapp.ui.priest.CallInvitation::class.java) // Re-use Priest's data class
                val status = invitation?.status

                Log.d("ConfessorHomeActivity", "Invitation status changed: $status. Full data: $invitation")

                when (status) {
                    "accepted" -> {
                        println("Invitation accepted by priest! Joining call for room $roomId...")
                        // Launch ConfessionActivity
                        val intent = android.content.Intent(this@ConfessorHomeActivity, com.example.confessionapp.ui.call.ConfessionActivity::class.java)
                        intent.putExtra("roomId", roomId) // roomId is passed to this function
                        intent.putExtra("invitationId", invitationId)
                        intent.putExtra("priestId", priestId)
                        // Potentially add a flag to indicate user role if ConfessionActivity needs it
                        // intent.putExtra("userRole", "confessor")
                        startActivity(intent)
                        removeInvitationStatusListener() // Stop listening after navigating
                        resetCurrentInvitationState()
                    }
                    "rejected" -> {
                        println("Invitation rejected by priest.")
                        // Show message to user: "The priest is unavailable or rejected your request."
                        removeInvitationStatusListener()
                        resetCurrentInvitationState()
                        // TODO: Update UI to allow sending a new request
                    }
                    "missed" -> {
                        println("Call missed. The priest did not respond.")
                        // Show message to user
                        removeInvitationStatusListener()
                        resetCurrentInvitationState()
                        // TODO: Update UI to allow sending a new request
                    }
                    "answered" -> {
                         println("Call has been answered by the priest.")
                         // This might be redundant if "accepted" already leads to joining the call.
                         // Could be used for specific UI changes if needed.
                         // removeInvitationStatusListener() // Or keep listening if further status like "completed" is expected
                         // resetCurrentInvitationState() // if this is a final state for this activity
                    }
                    "cancelled" -> {
                        println("Invitation was cancelled.")
                        // This could be triggered if another client instance for the same confessor cancelled it,
                        // or if this client cancelled it and the listener picks up its own change.
                        removeInvitationStatusListener()
                        resetCurrentInvitationState()
                        // TODO: Ensure UI is back to initial state
                    }
                    "pending" -> {
                        // This is the initial state, or if something reverted it.
                        // UI should show "Waiting for priest..."
                        println("Invitation is still pending...")
                    }
                    null -> {
                        Log.w("ConfessorHomeActivity", "Invitation data or status is null. Invitation might have been deleted.")
                        // This can happen if the node is deleted (e.g. by priest rejecting and deleting, or admin cleanup)
                        println("The invitation seems to have been removed or does not exist anymore.")
                        removeInvitationStatusListener()
                        resetCurrentInvitationState()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w("ConfessorHomeActivity", "listenForInvitationStatus:onCancelled", error.toException())
                resetCurrentInvitationState()
            }
        }
        statusListenerRef?.addValueEventListener(statusListener!!)
    }

    private fun removeInvitationStatusListener() {
        statusListener?.let { listener ->
            statusListenerRef?.removeEventListener(listener)
            Log.d("ConfessorHomeActivity", "Removed previous status listener.")
        }
        statusListener = null
        statusListenerRef = null
    }

    private fun resetCurrentInvitationState() {
        currentInvitationId = null
        currentPriestId = null
        // currentRoomId = null; // if you store roomId separately
        // TODO: Update UI to reflect that there's no active outgoing call request
        println("ConfessorHomeActivity: Resetting current invitation state. Ready for new request.")
    }

    // Call this function from a "Cancel" button in the waiting UI
    private fun cancelCurrentInvitation() {
        if (currentUser == null || currentPriestId == null || currentInvitationId == null) {
            Log.w("ConfessorHomeActivity", "No active invitation to cancel or user not signed in.")
            return
        }

        val invitationStatusRef = database.getReference("call_invitations")
            .child(currentPriestId!!)
            .child(currentInvitationId!!)
            .child("status")

        invitationStatusRef.setValue("cancelled")
            .addOnSuccessListener {
                Log.d("ConfessorHomeActivity", "Invitation $currentInvitationId cancelled successfully by confessor.")
                // The listener (listenForInvitationStatus) should pick up this change and update UI.
                // No need to call resetCurrentInvitationState() here as the listener will do it.
                // If listener is not robust enough, call it here.
            }
            .addOnFailureListener { e ->
                Log.e("ConfessorHomeActivity", "Failed to cancel invitation $currentInvitationId", e)
                // Show error to user
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeInvitationStatusListener() // Important to prevent memory leaks
    }

    // TODO: Add UI elements (EditText for priestId, Button to call, Button to Cancel, TextView for status)
    // TODO: Implement actual UI transition to a waiting screen.
    // TODO: Implement actual call to listenForInvitationStatus after sending.
    // TODO: Handle anonymous sign-in flow more gracefully.
    // TODO: Add a way for the confessor to input or select a priestId.
}
