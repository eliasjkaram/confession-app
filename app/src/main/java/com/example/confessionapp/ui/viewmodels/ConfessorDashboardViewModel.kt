package com.example.confessionapp.ui.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.confessionapp.repository.UserRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

// PriestData could include more details if needed for a list display
data class PriestData(
    val uid: String,
    val displayName: String? // Assuming priests might have a display name or use email
)

sealed class PriestFindingResult {
    object Searching : PriestFindingResult()
    data class PriestFound(val priest: PriestData) : PriestFindingResult()
    object NoPriestsAvailable : PriestFindingResult()
    data class Error(val message: String) : PriestFindingResult()
}

class ConfessorDashboardViewModel : ViewModel() {

    private val userRepository = UserRepository(FirebaseFirestore.getInstance())

    private val _priestFindingState = MutableLiveData<PriestFindingResult>()
    val priestFindingState: LiveData<PriestFindingResult> = _priestFindingState

    private val _isLoading = MutableLiveData<Boolean>() // General loading for other operations if any
    val isLoading: LiveData<Boolean> = _isLoading

    fun findAvailablePriest() {
        _priestFindingState.value = PriestFindingResult.Searching
        viewModelScope.launch {
            userRepository.getAvailablePriests { priests ->
                if (priests.isNotEmpty()) {
                    // For simplicity, pick the first available priest.
                    // A more complex app might show a list or use other matching logic.
                    val firstPriestData = priests.first()
                    val priestUid = firstPriestData["uid"] as? String
                    val priestEmail = firstPriestData["email"] as? String // Assuming email is stored

                    if (priestUid != null) {
                        // For now, using email as display name if available, or UID
                        _priestFindingState.postValue(PriestFindingResult.PriestFound(PriestData(priestUid, priestEmail ?: priestUid)))
                    } else {
                        _priestFindingState.postValue(PriestFindingResult.Error("Found priest with invalid data."))
                    }
                } else {
                    _priestFindingState.postValue(PriestFindingResult.NoPriestsAvailable)
                }
            }
        }
    }
}
