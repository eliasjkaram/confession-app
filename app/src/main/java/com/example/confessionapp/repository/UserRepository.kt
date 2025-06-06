package com.example.confessionapp.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseUser

class UserRepository(private val firestore: FirebaseFirestore) {
    fun getUserProfile(uid: String, onResult: (Map<String, Any>?) -> Unit) {
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                onResult(document.data)
            }
            .addOnFailureListener {
                onResult(null)
            }
    }

    fun setUserProfile(uid: String, profile: Map<String, Any>, onResult: (Boolean) -> Unit) {
        firestore.collection("users").document(uid).set(profile)
            .addOnSuccessListener {
                onResult(true)
            }
            .addOnFailureListener {
                onResult(false)
            }
    }

    fun checkPriestVerification(uid: String, onResult: (Boolean) -> Unit) {
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                val isVerified = document.getBoolean("isPriestVerified") ?: false
                onResult(isVerified)
            }
            .addOnFailureListener {
                onResult(false)
            }
    }
}
