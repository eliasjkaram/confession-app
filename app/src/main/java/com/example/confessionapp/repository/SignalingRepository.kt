package com.example.confessionapp.repository

import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DatabaseReference

class SignalingRepository(private val dbRef: DatabaseReference) {
    private var childEventListener: ChildEventListener? = null // Member variable to hold the listener

    fun sendSignal(roomId: String, signal: Map<String, Any>) {
        // It's safer to use the dbRef passed in constructor if it already points to "signals"
        // Or ensure the path is consistently built. Assuming dbRef is base ref for "confession_rooms"
        dbRef.child(roomId).child("signals").push().setValue(signal)
    }

    fun listenForSignals(roomId: String, onSignal: (Map<String, Any>?) -> Unit) {
        // Remove any existing listener before adding a new one for this room path
        removeListeners(roomId) // Ensures no multiple listeners on same path if called multiple times

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {
                val signal = snapshot.value as? Map<String, Any>
                onSignal(signal)
            }
            override fun onChildChanged(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: com.google.firebase.database.DataSnapshot) {}
            override fun onChildMoved(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                // Handle error, e.g., log it
            }
        }
        this.childEventListener = listener // Store the listener
        // Assuming dbRef is base ref for "confession_rooms"
        dbRef.child(roomId).child("signals").addChildEventListener(listener)
    }

    fun removeListeners(roomId: String) {
        childEventListener?.let {
            // Assuming dbRef is base ref for "confession_rooms"
            dbRef.child(roomId).child("signals").removeEventListener(it)
        }
        childEventListener = null
    }

    // A generic removeAllListeners if the repository instance is meant to be short-lived or reset
    fun removeAllListeners() {
       // This is tricky if the listener was attached to a specific roomId path that is not stored
       // If childEventListener is always for ONE path (e.g. dbRef IS the signals path), then it's simpler:
       // childEventListener?.let { dbRef.removeEventListener(it) }
       // For now, relying on removeListeners(roomId) called by ViewModel with specific room.
       // If SignalingRepository is instantiated per room, then this is fine.
       // If SignalingRepository is a singleton, it needs to manage multiple listeners for multiple rooms.
       // Current ConfessionViewModel creates a new SignalingRepository, so it's one per room session.
       childEventListener?.let {
            // This would require dbRef to be the exact path the listener was added to,
            // or the path to be reconstructed.
            // Since ViewModel passes roomId for removal, this generic one might not be safe
            // without more context on dbRef's exact nature.
            // For now, it's better to rely on the ViewModel calling removeListeners(roomId).
       }
       childEventListener = null
    }

    // --- Invitation Methods ---
    // dbRef here is assumed to be the root of "confession_rooms" or a similar base path.
    // For invitations, we need a path like "/invitations/{priestId}/{invitationId}"

    fun sendInvitation(priestId: String, invitation: com.example.confessionapp.data.CallInvitation, onResult: (Boolean) -> Unit) {
        // Assuming dbRef is a general Firebase Database root reference.
        // If dbRef is specific (e.g., to "confession_rooms"), adjust pathing or use FirebaseDatabase.getInstance().getReference(...)
        val invitationsRef = FirebaseDatabase.getInstance().getReference("invitations")
        invitationsRef.child(priestId).child(invitation.invitationId).setValue(invitation)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    private var invitationStatusListener: com.google.firebase.database.ValueEventListener? = null
    private var invitationRef: DatabaseReference? = null

    fun listenToInvitationStatus(priestId: String, invitationId: String, onStatusChange: (com.example.confessionapp.data.CallInvitation?) -> Unit) {
        removeInvitationStatusListener() // Clean up previous listener

        invitationRef = FirebaseDatabase.getInstance().getReference("invitations").child(priestId).child(invitationId)
        invitationStatusListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val invitation = snapshot.getValue(com.example.confessionapp.data.CallInvitation::class.java)
                onStatusChange(invitation)
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                // Handle error
                onStatusChange(null) // Indicate error or listener cancellation
            }
        }
        invitationRef?.addValueEventListener(invitationStatusListener!!)
    }

    fun removeInvitationStatusListener() {
        invitationStatusListener?.let { listener ->
            invitationRef?.removeEventListener(listener)
        }
        invitationStatusListener = null
        invitationRef = null
    }

     fun updateInvitationStatus(priestId: String, invitationId: String, newStatus: String, onResult: (Boolean) -> Unit) {
        val updates = mapOf("status" to newStatus, "priestRespondedTimestamp" to System.currentTimeMillis())
        FirebaseDatabase.getInstance().getReference("invitations")
            .child(priestId).child(invitationId)
            .updateChildren(updates)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }
}
