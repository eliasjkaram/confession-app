package com.example.confessionapp.repository

import com.example.confessionapp.data.models.PriestUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.Query
import android.util.Log

class UserRepository(private val firestore: FirebaseFirestore) {
    fun getUserProfile(uid: String, onResult: (Map<String, Any>?) -> Unit) {
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                onResult(document.data)
            }
            .addOnFailureListener { exception ->
                Log.e("UserRepository", "Error getting user profile for $uid", exception)
                onResult(null)
            }
    }

    fun setUserProfile(uid: String, profile: Map<String, Any>, onResult: (Boolean) -> Unit) {
        firestore.collection("users").document(uid).set(profile)
            .addOnSuccessListener {
                onResult(true)
            }
            .addOnFailureListener { exception ->
                Log.e("UserRepository", "Error setting user profile for $uid", exception)
                onResult(false)
            }
    }

    fun checkPriestVerification(uid: String, onResult: (Boolean) -> Unit) {
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                val isVerified = document.getBoolean("isPriestVerified") ?: false
                onResult(isVerified)
            }
            .addOnFailureListener { exception ->
                Log.e("UserRepository", "Error checking priest verification for $uid", exception)
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
                            name = document.getString("name") ?: "Unknown Priest",
                            email = document.getString("email") ?: "",
                            photoUrl = document.getString("photoUrl"),
                            languages = document.get("languages") as? List<String> ?: emptyList(),
                            isPriestVerified = document.getBoolean("isPriestVerified") ?: false
                        )
                        // Double check, though query should ensure this
                        if (priest.isPriestVerified) {
                            priestList.add(priest)
                        }
                    } catch (e: Exception) {
                        Log.e("UserRepository", "Error parsing priest document ${document.id}", e)
                        // Optionally skip this priest or add a default/error state
                    }
                }
                onResult(Result.success(priestList))
            }
            .addOnFailureListener { exception ->
                Log.e("UserRepository", "Error fetching verified priests", exception)
                onResult(Result.failure(exception))
            }
    }
}
