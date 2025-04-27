package com.example.confessionapp.repository

import com.google.firebase.auth.FirebaseAuth

class AuthRepository(private val firebaseAuth: FirebaseAuth) {
    fun signInAnonymously(onResult: (Boolean) -> Unit) {
        firebaseAuth.signInAnonymously()
            .addOnCompleteListener { task ->
                onResult(task.isSuccessful)
            }
    }

    fun signInWithEmail(email: String, password: String, onResult: (Boolean) -> Unit) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                onResult(task.isSuccessful)
            }
    }

    fun registerWithEmail(email: String, password: String, onResult: (Boolean) -> Unit) {
        firebaseAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                onResult(task.isSuccessful)
            }
    }

    fun signOut() {
        firebaseAuth.signOut()
    }
}
