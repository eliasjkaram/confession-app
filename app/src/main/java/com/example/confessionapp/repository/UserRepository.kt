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
            .addOnFailureListener { exception -> // Pass exception
                onResult(null) // Existing behavior, but ideally caller knows about error
            }
    }

    fun setUserProfile(uid: String, profile: Map<String, Any>, onResult: (success: Boolean, exception: Exception?) -> Unit) {
        firestore.collection("users").document(uid).set(profile)
            .addOnSuccessListener {
                onResult(true, null)
            }
            .addOnFailureListener { exception ->
                onResult(false, exception)
            }
    }

    fun checkPriestVerification(uid: String, onResult: (isVerified: Boolean, exception: Exception?) -> Unit) {
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                val isVerified = document.getBoolean("isPriestVerified") ?: false
                onResult(isVerified, null)
            }
            .addOnFailureListener { exception ->
                onResult(false, exception)
            }
    }

    // getVerifiedPriests already returns Result<T> which includes exceptions, so it's fine.
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
                        // Consider how to report this, e.g., logging to Crashlytics or a default PriestUser
                        // For example: Log.e("UserRepository", "Error parsing priest document ${document.id}", e)
                    }
                }
                onResult(Result.success(priestList))
            }
            .addOnFailureListener { exception ->
                // Log.e("UserRepository", "Error fetching verified priests", exception)
                onResult(Result.failure(exception))
            }
    }

    fun getSupportedLanguages(onResult: (Result<List<String>>) -> Unit) {
        firestore.collection("app_config").document("languages").get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val languages = document.get("supported_languages") as? List<String>
                    if (languages != null) {
                        onResult(Result.success(languages.sorted()))
                    } else {
                        onResult(Result.failure(Exception("supported_languages field is missing or not a list")))
                    }
                } else {
                    onResult(Result.failure(Exception("Languages config document not found")))
                }
            }
            .addOnFailureListener { exception ->
                onResult(Result.failure(exception))
            }
    }
}
