package com.example.confessionapp.ui.viewmodels

// Imports for streamlined ViewModel
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.confessionapp.repository.UserRepository // Still needed for setUserProfile for verificationStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import java.util.UUID

// VerificationStatus data class can remain here or be moved to a common 'data' package if shared
// For now, keeping it here as it's primarily used by this ViewModel and its Activity.
data class VerificationUploadStatus( // Renamed to be more specific
    val isSuccess: Boolean,
    val message: String,
    val isVerificationPending: Boolean = false // Indicates if submission resulted in 'pending'
)

class PriestVerificationViewModel : ViewModel() {

    private val userRepository = UserRepository(FirebaseFirestore.getInstance())
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val _uploadResult = MutableLiveData<VerificationUploadStatus>()
    val uploadResult: LiveData<VerificationUploadStatus> = _uploadResult

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // Document types can be exposed for the Spinner in PriestVerificationActivity
    val documentTypes = listOf("Letter of Good Standing", "Certificate of Ordination", "Other")

    fun uploadVerificationDocument(uri: Uri, documentType: String, fileName: String) {
        _isLoading.value = true
        val priestId = firebaseAuth.currentUser?.uid ?: run {
            _uploadResult.postValue(VerificationUploadStatus(false, "User not authenticated."))
            _isLoading.postValue(false)
            return
        }

        val requestId = UUID.randomUUID().toString()
        val storagePath = "verification/$requestId/$documentType/$fileName"
        val storageRef = storage.reference.child(storagePath)

        val metadata = com.google.firebase.storage.StorageMetadata.Builder()
            .setCustomMetadata("priestId", priestId)
            .setCustomMetadata("requestId", requestId)
            .setCustomMetadata("documentType", documentType)
            .build()

        storageRef.putFile(uri, metadata)
            .addOnSuccessListener {
                createVerificationRequestFirestore(priestId, requestId, documentType, fileName, storagePath)
            }
            .addOnFailureListener { exception ->
                _uploadResult.postValue(VerificationUploadStatus(false, "Upload failed: ${exception.message}"))
                _isLoading.postValue(false)
            }
    }

    private fun createVerificationRequestFirestore(priestId: String, requestId: String, documentType: String, uploadedFileName: String, storagePath: String) {
        val requestData = hashMapOf(
            "priestId" to priestId,
            "requestId" to requestId,
            "documentType" to documentType,
            "uploadedFileName" to uploadedFileName,
            "storagePath" to storagePath,
            "status" to "pending", // Default status upon submission
            "timestamp" to com.google.firebase.Timestamp.now()
        )

        FirebaseFirestore.getInstance().collection("verificationRequests").document(requestId)
            .set(requestData)
            .addOnSuccessListener {
                // Update user's profile to indicate verification is now pending with this request ID
                // Using a map that only updates specific fields related to verification submission.
                val userProfileUpdate = mapOf(
                    "verificationStatus" to "pending",
                    "lastVerificationRequestId" to requestId
                )
                userRepository.setUserProfile(priestId, userProfileUpdate, true) { success -> // Assuming setUserProfile can handle partial updates if implemented that way
                    if (success) {
                        _uploadResult.postValue(VerificationUploadStatus(true, "Document uploaded and verification request submitted.", isVerificationPending = true))
                    } else {
                        // Still a success for upload and request creation, but profile update failed.
                        // This might need more nuanced error reporting.
                        _uploadResult.postValue(VerificationUploadStatus(true, "Request submitted, but failed to update user profile status. Contact support if verification status doesn't update.", isVerificationPending = true))
                    }
                    _isLoading.postValue(false)
                }
            }
            .addOnFailureListener { exception ->
                _uploadResult.postValue(VerificationUploadStatus(false, "Failed to create verification request in Firestore: ${exception.message}"))
                _isLoading.postValue(false)
            }
    }
}
