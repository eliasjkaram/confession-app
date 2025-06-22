package com.example.confessionapp.repository

import com.example.confessionapp.data.models.PriestUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.Query

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

    fun getVerifiedPriests(language: String? = null, onResult: (Result<List<PriestUser>>) -> Unit) {
        var query: Query = firestore.collection("users").whereEqualTo("isPriestVerified", true)

        if (!language.isNullOrEmpty()) {
            query = query.whereArrayContains("languages", language)
        }

        query.get()
            .addOnSuccessListener { documents ->
                val priestList = mutableListOf<PriestUser>()
                for (document in documents) {
                    try {
                        val priest = PriestUser(
                            uid = document.id,
                            name = document.getString("name") ?: "",
                            email = document.getString("email") ?: "",
                            photoUrl = document.getString("photoUrl"),
                            languages = document.get("languages") as? List<String> ?: emptyList(),
                            isPriestVerified = document.getBoolean("isPriestVerified") ?: false
                        )
                        // Only add if indeed verified, as an extra check, though query should handle this
                        if (priest.isPriestVerified) {
                            priestList.add(priest)
                        }
                    } catch (e: Exception) {
                        // Log error or handle individual document parsing errors
                        // For now, we'll skip problematic documents
                        // Consider how to report this, e.g., logging to Crashlytics
                    }
                }
                onResult(Result.success(priestList))
            }
            .addOnFailureListener { exception ->
                onResult(Result.failure(exception))
            }
    }
}
