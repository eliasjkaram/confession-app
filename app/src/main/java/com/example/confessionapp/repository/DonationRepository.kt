package com.example.confessionapp.repository

import com.google.firebase.firestore.FirebaseFirestore

class DonationRepository(private val firestore: FirebaseFirestore) {
    fun recordDonation(uid: String, donation: Map<String, Any>, onResult: (Boolean) -> Unit) {
        firestore.collection("donations").add(donation)
            .addOnSuccessListener {
                onResult(true)
            }
            .addOnFailureListener {
                onResult(false)
            }
    }

    fun getDonations(uid: String, onResult: (List<Map<String, Any>>) -> Unit) {
        firestore.collection("donations").whereEqualTo("uid", uid).get()
            .addOnSuccessListener { result ->
                val donations = result.mapNotNull { it.data }
                onResult(donations)
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }
}
