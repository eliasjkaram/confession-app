package com.example.confessionapp.repository

import com.google.firebase.database.DatabaseReference

class SignalingRepository(private val dbRef: DatabaseReference) {
    fun sendSignal(roomId: String, signal: Map<String, Any>) {
        dbRef.child("signals").child(roomId).push().setValue(signal)
    }

    fun listenForSignals(roomId: String, onSignal: (Map<String, Any>?) -> Unit) {
        dbRef.child("signals").child(roomId)
            .addChildEventListener(object : com.google.firebase.database.ChildEventListener {
                override fun onChildAdded(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {
                    val signal = snapshot.value as? Map<String, Any>
                    onSignal(signal)
                }
                override fun onChildChanged(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {}
                override fun onChildRemoved(snapshot: com.google.firebase.database.DataSnapshot) {}
                override fun onChildMoved(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
    }
}
