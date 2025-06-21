package com.example.confessionapp.ui.viewmodels

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.confessionapp.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import java.util.UUID

data class VerificationStatus(
    val isSuccess: Boolean,
    val message: String,
    val isVerificationPending: Boolean = false
)

class PriestVerificationViewModel : ViewModel() {

    private val userRepository = UserRepository(FirebaseFirestore.getInstance())
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val _uploadResult = MutableLiveData<VerificationStatus>()
    val uploadResult: LiveData<VerificationStatus> = _uploadResult

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    val documentTypes = listOf("Letter of Good Standing", "Certificate of Ordination", "Other") // Example types

    fun uploadVerificationDocument(uri: Uri, documentType: String, fileName: String) {
        _isLoading.value = true
        val priestId = firebaseAuth.currentUser?.uid ?: run {
            _uploadResult.postValue(VerificationStatus(false, "User not authenticated."))
            _isLoading.postValue(false)
            return
        }

        val requestId = UUID.randomUUID().toString()
        // Path based on Setup.md: /verification/{requestId}/{documentType}/{fileName}
        val storageRef = storage.reference.child("verification/$requestId/$documentType/$fileName")

        storageRef.putFile(uri)
            .addOnSuccessListener {
                // Document uploaded to Storage, now create/update verification request in Firestore
                createVerificationRequest(priestId, requestId, documentType, fileName)
            }
            .addOnFailureListener { exception ->
                _uploadResult.postValue(VerificationStatus(false, "Upload failed: ${exception.message}"))
                _isLoading.postValue(false)
            }
    }

    private fun createVerificationRequest(priestId: String, requestId: String, documentType: String, uploadedFileName: String) {
        // This data structure for 'verificationRequests' collection needs to align with backend expectations.
        // For now, storing basic info.
        val requestData = hashMapOf(
            "priestId" to priestId,
            "requestId" to requestId,
            "documentType" to documentType,
            "uploadedFileName" to uploadedFileName,
            "status" to "pending", // "pending", "approved", "rejected"
            "timestamp" to com.google.firebase.Timestamp.now()
            // Backend might add storagePath: "verification/$requestId/$documentType/$uploadedFileName"
        )

        FirebaseFirestore.getInstance().collection("verificationRequests").document(requestId)
            .set(requestData)
            .addOnSuccessListener {
                // Also update user's profile to indicate verification is pending
                userRepository.setUserProfile(priestId, mapOf("verificationStatus" to "pending", "lastVerificationRequestId" to requestId), true) { success ->
                    if (success) {
                        _uploadResult.postValue(VerificationStatus(true, "Document uploaded and verification request submitted.", isVerificationPending = true))
                    } else {
                        _uploadResult.postValue(VerificationStatus(false, "Failed to update user profile for verification status."))
                    }
                    _isLoading.postValue(false)
                }
            }
            .addOnFailureListener { exception ->
                _uploadResult.postValue(VerificationStatus(false, "Failed to create verification request: ${exception.message}"))
                _isLoading.postValue(false)
            }
    }

    private val _verificationStatus = MutableLiveData<Pair<Boolean, String?>>() // isVerified, statusString
    val verificationStatus: LiveData<Pair<Boolean, String?>> = _verificationStatus

    fun checkPriestVerificationStatus() {
        _isLoading.value = true
        val priestId = firebaseAuth.currentUser?.uid ?: run {
            _verificationStatus.postValue(Pair(false, "Not Authenticated"))
            _isLoading.postValue(false)
            return
        }

        userRepository.getUserProfile(priestId) { profile ->
            if (profile != null) {
                val isVerified = profile["isPriestVerified"] as? Boolean ?: false
                val currentStatus = profile["verificationStatus"] as? String // e.g., "pending", "approved", "rejected"
                if (isVerified) {
                    _verificationStatus.postValue(Pair(true, "Verified"))
                } else {
                    _verificationStatus.postValue(Pair(false, currentStatus ?: "Not Verified"))
                }
            } else {
                _verificationStatus.postValue(Pair(false, "Profile not found"))
            }
            _isLoading.postValue(false)
        }
    }

    private val _availabilityUpdateResult = MutableLiveData<Boolean>()
    val availabilityUpdateResult: LiveData<Boolean> = _availabilityUpdateResult

    fun updatePriestAvailability(isAvailable: Boolean) {
        _isLoading.value = true
        val priestId = firebaseAuth.currentUser?.uid ?: run {
            _availabilityUpdateResult.postValue(false)
            _isLoading.postValue(false)
            return
        }
        userRepository.setPriestAvailability(priestId, isAvailable) { success ->
            _availabilityUpdateResult.postValue(success)
            if (success) {
                // Refresh verification status which also contains availability info from user profile
                checkPriestVerificationStatus()
            }
            _isLoading.postValue(false)
        }
    }

    // To observe current availability status from the profile
    val isAvailableForConfession = MutableLiveData<Boolean>()

    // Modify checkPriestVerificationStatus to also update isAvailableForConfession
    fun checkPriestVerificationStatus() { // Renamed for clarity, though it does more now
        _isLoading.value = true
        val priestId = firebaseAuth.currentUser?.uid ?: run {
            _verificationStatus.postValue(Pair(false, "Not Authenticated"))
            isAvailableForConfession.postValue(false)
            _isLoading.postValue(false)
            return
        }

        userRepository.getUserProfile(priestId) { profile ->
            if (profile != null) {
                val isVerified = profile["isPriestVerified"] as? Boolean ?: false
                val currentStatus = profile["verificationStatus"] as? String
                val available = profile["isAvailableForConfession"] as? Boolean ?: false
                isAvailableForConfession.postValue(available)

                if (isVerified) {
                    _verificationStatus.postValue(Pair(true, "Verified"))
                } else {
                    _verificationStatus.postValue(Pair(false, currentStatus ?: "Not Verified"))
                }
            } else {
                _verificationStatus.postValue(Pair(false, "Profile not found"))
                isAvailableForConfession.postValue(false)
            }
            _isLoading.postValue(false)
        }
    }
}
