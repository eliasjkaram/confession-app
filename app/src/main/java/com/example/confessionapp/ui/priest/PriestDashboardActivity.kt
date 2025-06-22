package com.example.confessionapp.ui.priest

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
// Temporarily commenting out R.layout.activity_priest_dashboard until layout files are handled.
// import com.example.confessionapp.R
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*

data class CallInvitation(
    val roomId: String? = null,
    val confessorId: String? = null,
    val confessorName: String? = null,
    val priestId: String? = null,
    val timestamp: Long? = null,
    var status: String? = null,
    var invitationId: String? = null // To store the key of the invitation
)

class PriestDashboardActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private var currentUser: FirebaseUser? = null
    private var invitationsRef: DatabaseReference? = null
    private var invitationListener: ChildEventListener? = null

    // Store active invitations shown to the priest to avoid reprocessing
    private val activeInvitations = mutableMapOf<String, CallInvitation>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContentView(R.layout.activity_priest_dashboard)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        currentUser = auth.currentUser

        if (currentUser == null) {
            Log.e("PriestDashboardActivity", "Priest not signed in. Cannot listen for invitations.")
            // In a real app, redirect to login screen
            // For now, just return or show error
            println("PriestDashboardActivity: Error - Priest not signed in.")
            return
        }

        println("PriestDashboardActivity Created for user: ${currentUser!!.uid}")
        listenForIncomingInvitations()
    }

    private fun listenForIncomingInvitations() {
        val priestId = currentUser?.uid ?: return
        invitationsRef = database.getReference("call_invitations").child(priestId)

        invitationListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val invitation = snapshot.getValue(CallInvitation::class.java)
                invitation?.invitationId = snapshot.key // Store the Firebase key as invitationId

                if (invitation != null && invitation.status == "pending" && !activeInvitations.containsKey(invitation.invitationId)) {
                    activeInvitations[invitation.invitationId!!] = invitation
                    Log.d("PriestDashboardActivity", "New pending invitation: ${invitation.invitationId} from ${invitation.confessorName} for room ${invitation.roomId}")
                    // TODO: Display incoming call UI (dialog) with Accept/Reject options
                    // Pass invitation.invitationId and invitation.roomId to the UI handler
                    println("Incoming call from ${invitation.confessorName}! Room: ${invitation.roomId}. Invitation ID: ${invitation.invitationId}")
                    println("Options: Accept / Reject (Conceptual)")
                    // For now, let's simulate priest accepting immediately for testing flow
                    // handleInvitationResponse(invitation.invitationId!!, invitation.roomId!!, true)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val invitation = snapshot.getValue(CallInvitation::class.java)
                invitation?.invitationId = snapshot.key
                // If an active invitation is no longer pending (e.g., cancelled by confessor, accepted by this priest on another device)
                if (invitation != null && invitation.status != "pending" && activeInvitations.containsKey(invitation.invitationId)) {
                    Log.d("PriestDashboardActivity", "Invitation ${invitation.invitationId} status changed to ${invitation.status}. Removing from active.")
                    activeInvitations.remove(invitation.invitationId)
                    // TODO: Update UI - e.g., dismiss the incoming call dialog if it's for this invitation
                    println("Invitation ${invitation.invitationId} is no longer pending (status: ${invitation.status}). UI should be updated.")
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val invitationId = snapshot.key
                if (invitationId != null && activeInvitations.containsKey(invitationId)) {
                    activeInvitations.remove(invitationId)
                    Log.d("PriestDashboardActivity", "Invitation $invitationId removed. Updating UI.")
                    // TODO: Update UI - dismiss incoming call dialog if it was for this invitation
                     println("Invitation $invitationId was removed. UI should be updated.")
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.w("PriestDashboardActivity", "listenForIncomingInvitations:onCancelled", error.toException())
            }
        }
        invitationsRef!!.addChildEventListener(invitationListener!!)
        Log.d("PriestDashboardActivity", "Started listening for invitations for priest: $priestId")
    }

    // Placeholder for handling response (will be detailed in the next step)
    // private fun handleInvitationResponse(invitationId: String, roomId: String, accepted: Boolean) { // Made public for now if called from dialog
    fun handleInvitationResponse(invitationId: String, roomId: String, accepted: Boolean) {
        val priestId = currentUser?.uid ?: run {
            Log.e("PriestDashboardActivity", "Cannot handle response, priestId is null")
            return
        }
        val statusToSet = if (accepted) "accepted" else "rejected"

        val invitationStatusRef = database.getReference("call_invitations").child(priestId).child(invitationId).child("status")

        invitationStatusRef.setValue(statusToSet)
            .addOnSuccessListener {
                Log.d("PriestDashboardActivity", "Invitation $invitationId status updated to $statusToSet")
                activeInvitations.remove(invitationId) // Remove from active list as it's handled

                if (accepted) {
                    // Launch ConfessionActivity
                    val intent = android.content.Intent(this, com.example.confessionapp.ui.call.ConfessionActivity::class.java)
                    intent.putExtra("roomId", roomId)
                    intent.putExtra("invitationId", invitationId)
                    intent.putExtra("priestId", priestId)
                    startActivity(intent)
                    println("Invitation accepted. Launching ConfessionActivity for room $roomId...")
                } else {
                    println("Invitation rejected. Invitation ID: $invitationId")
                    // UI would be dismissed by the dialog/notification handler itself or by onChildChanged
                }
            }
            .addOnFailureListener { e ->
                Log.e("PriestDashboardActivity", "Failed to update invitation $invitationId status", e)
                // Potentially re-add to activeInvitations or show error to allow retry? For now, just log.
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        invitationListener?.let { listener ->
            invitationsRef?.removeEventListener(listener)
            Log.d("PriestDashboardActivity", "Removed invitation listener.")
        }
    }

    // TODO: Implement actual UI for incoming call notification/dialog.
    // TODO: Implement logic for priest accepting/rejecting via UI interaction.
    // TODO: Handle scenarios like priest already in a call.
}
