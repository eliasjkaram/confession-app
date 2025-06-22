package com.example.confessionapp.ui.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.confessionapp.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

// Data class to hold combined profile status for UI
data class PriestProfileStatus(
    val isVerified: Boolean = false,
    val verificationStatusString: String? = "Not Verified",
    val languages: List<String> = emptyList()
)

class PriestProfileViewModel : ViewModel() {

    private val userRepository = UserRepository(FirebaseFirestore.getInstance())
    private val firebaseAuth = FirebaseAuth.getInstance()

    private val _isLoadingProfile = MutableLiveData<Boolean>()
    val isLoadingProfile: LiveData<Boolean> = _isLoadingProfile

    private val _priestProfileStatus = MutableLiveData<PriestProfileStatus>()
    val priestProfileStatus: LiveData<PriestProfileStatus> = _priestProfileStatus

    private val _languageUpdateResult = MutableLiveData<Boolean>()
    val languageUpdateResult: LiveData<Boolean> = _languageUpdateResult


    fun fetchPriestProfileAndStatus() {
        _isLoadingProfile.value = true
        val priestId = firebaseAuth.currentUser?.uid ?: run {
            _priestProfileStatus.postValue(PriestProfileStatus()) // Default/logged out state
            _isLoadingProfile.postValue(false)
            return
        }

        userRepository.getUserProfile(priestId) { profile ->
            if (profile != null) {
                val isVerified = profile["isPriestVerified"] as? Boolean ?: false
                val currentStatus = profile["verificationStatus"] as? String
                val languages = profile["languages"] as? List<String> ?: emptyList()

                _priestProfileStatus.postValue(
                    PriestProfileStatus(
                        isVerified = isVerified,
                        verificationStatusString = currentStatus ?: if(isVerified) "Verified" else "Not Verified",
                        languages = languages
                    )
                )
            } else {
                _priestProfileStatus.postValue(PriestProfileStatus()) // Profile not found state
            }
            _isLoadingProfile.postValue(false)
        }
    }

    fun updatePriestLanguages(languages: List<String>) {
        _isLoadingProfile.value = true // Can use the same loading state or a specific one
        val priestId = firebaseAuth.currentUser?.uid ?: run {
            _languageUpdateResult.postValue(false)
            _isLoadingProfile.postValue(false)
            return
        }
        userRepository.setUserProfile(priestId, mapOf("languages" to languages), true) { success ->
            _languageUpdateResult.postValue(success)
            if (success) {
                // Re-fetch profile to ensure consistency, or update LiveData directly
                fetchPriestProfileAndStatus() // This will update _priestProfileStatus.languages
            }
            _isLoadingProfile.postValue(false)
        }
    }
}
