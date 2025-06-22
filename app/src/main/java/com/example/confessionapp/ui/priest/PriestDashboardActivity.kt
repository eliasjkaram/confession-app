package com.example.confessionapp.ui.priest

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.confessionapp.R
import com.example.confessionapp.ui.call.ConfessionActivity
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

    // UI Elements
    private lateinit var incomingCallLayout: LinearLayout
    private lateinit var confessorNameTextView: TextView
    private lateinit var acceptCallButton: Button
    private lateinit var rejectCallButton: Button
    private lateinit var noActiveCallTextView: TextView

    // Store active invitations shown to the priest to avoid reprocessing / managing single display
    private val activeInvitations = mutableMapOf<String, CallInvitation>()
    private var currentlyDisplayedInvitationId: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_priest_dashboard)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        currentUser = auth.currentUser

        incomingCallLayout = findViewById(R.id.incomingCallLayout)
        confessorNameTextView = findViewById(R.id.confessorNameTextView)
        acceptCallButton = findViewById(R.id.acceptCallButton)
        rejectCallButton = findViewById(R.id.rejectCallButton)
        noActiveCallTextView = findViewById(R.id.noActiveCallTextView)

        if (currentUser == null) {
            Log.e("PriestDashboardActivity", "Priest not signed in. Cannot listen for invitations.")
            noActiveCallTextView.text = "Error: Priest not signed in. Please login."
            incomingCallLayout.visibility = View.GONE
            // Potentially finish() or redirect to login
            return
        }

        updateUiForIdleState() // Initial state
        Log.d("PriestDashboardActivity", "Activity Created for user: ${currentUser!!.uid}")
        listenForIncomingInvitations()
    }

    private fun updateUiForIdleState() {
        incomingCallLayout.visibility = View.GONE
        noActiveCallTextView.visibility = View.VISIBLE
        currentlyDisplayedInvitationId = null
    }

    private fun displayInvitation(invitation: CallInvitation) {
        if (currentlyDisplayedInvitationId != null && currentlyDisplayedInvitationId != invitation.invitationId) {
            Log.d("PriestDashboardActivity", "Already displaying an invitation. New invitation ${invitation.invitationId} queued (conceptually).")
            // In a real app, you might add to a queue or list. For now, only one shows.
            // If another invitation is already displayed, don't overwrite it until it's handled.
            // However, if the currently displayed one was cancelled/removed, this new one can take its place.
            // The logic in onChildAdded and onChildChanged should handle this.
            return // Or, if the current one is stale, replace it.
        }

        currentlyDisplayedInvitationId = invitation.invitationId
        confessorNameTextView.text = invitation.confessorName ?: "Unknown Confessor"
        incomingCallLayout.visibility = View.VISIBLE
        noActiveCallTextView.visibility = View.GONE

        acceptCallButton.setOnClickListener {
            handleInvitationResponse(invitation.invitationId!!, invitation.roomId!!, true)
        }
        rejectCallButton.setOnClickListener {
            handleInvitationResponse(invitation.invitationId!!, invitation.roomId!!, false)
        }
        Log.d("PriestDashboardActivity", "Displaying invitation: ${invitation.invitationId}")
    }


    private fun listenForIncomingInvitations() {
        val priestId = currentUser?.uid ?: return
        invitationsRef = database.getReference("call_invitations").child(priestId)

        invitationListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val invitation = snapshot.getValue(CallInvitation::class.java)
                invitation?.invitationId = snapshot.key

                if (invitation != null && invitation.status == "pending") {
                    activeInvitations[invitation.invitationId!!] = invitation // Track all pending
                    Log.d("PriestDashboardActivity", "Pending invitation added: ${invitation.invitationId}")
                    // If no call is currently displayed, show this one.
                    if (currentlyDisplayedInvitationId == null) {
                        displayInvitation(invitation)
                    } else {
                        Log.d("PriestDashboardActivity", "Another call already displayed. ${invitation.invitationId} is pending.")
                    }
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val changedInvitation = snapshot.getValue(CallInvitation::class.java)
                changedInvitation?.invitationId = snapshot.key

                if (changedInvitation == null || changedInvitation.invitationId == null) return

                // Update local cache
                activeInvitations[changedInvitation.invitationId!!] = changedInvitation

                if (changedInvitation.invitationId == currentlyDisplayedInvitationId) {
                    if (changedInvitation.status != "pending") {
                        // The currently displayed invitation is no longer pending (e.g., accepted elsewhere, cancelled by confessor)
                        Log.d("PriestDashboardActivity", "Displayed invitation ${changedInvitation.invitationId} status changed to ${changedInvitation.status}. Hiding it.")
                        updateUiForIdleState()
                        activeInvitations.remove(changedInvitation.invitationId) // Remove if handled
                        // Try to display next pending invitation if any
                        displayNextPendingInvitation()
                    }
                    // If status is still pending but other details changed, displayInvitation would re-render if called.
                } else if (changedInvitation.status != "pending") {
                    // A non-displayed invitation is no longer pending, remove from active list if it was there
                    activeInvitations.remove(changedInvitation.invitationId)
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val removedInvitationId = snapshot.key
                if (removedInvitationId == null) return

                activeInvitations.remove(removedInvitationId)
                Log.d("PriestDashboardActivity", "Invitation $removedInvitationId removed.")

                if (removedInvitationId == currentlyDisplayedInvitationId) {
                    updateUiForIdleState()
                    // Try to display next pending invitation if any
                    displayNextPendingInvitation()
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.w("PriestDashboardActivity", "listenForIncomingInvitations:onCancelled", error.toException())
            }
        }
        invitationsRef!!.orderByChild("timestamp").addChildEventListener(invitationListener!!) // Order by time
        Log.d("PriestDashboardActivity", "Started listening for invitations for priest: $priestId")
    }

    private fun displayNextPendingInvitation() {
        if (currentlyDisplayedInvitationId != null) return // Already showing one

        val nextInvitation = activeInvitations.values
            .filter { it.status == "pending" }
            .minByOrNull { it.timestamp ?: Long.MAX_VALUE } // FIFO based on timestamp

        nextInvitation?.let {
            displayInvitation(it)
        }
    }

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

                if (invitationId == currentlyDisplayedInvitationId) {
                    updateUiForIdleState() // Hide the current call UI
                     // Attempt to show next pending call if any
                    displayNextPendingInvitation()
                }

                if (accepted) {
                    val intent = Intent(this, ConfessionActivity::class.java)
                    intent.putExtra("roomId", roomId)
                    intent.putExtra("invitationId", invitationId)
                    intent.putExtra("priestId", priestId)
                    startActivity(intent)
                }
            }
            .addOnFailureListener { e ->
                Log.e("PriestDashboardActivity", "Failed to update invitation $invitationId status", e)
                // Potentially show error to priest. For now, UI might remain or get reset by listener.
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        invitationListener?.let { listener ->
            invitationsRef?.removeEventListener(listener)
            Log.d("PriestDashboardActivity", "Removed invitation listener.")
        }
    }
}
