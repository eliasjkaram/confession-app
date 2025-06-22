package com.example.confessionapp.ui.viewmodels

import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.confessionapp.data.CallInvitation
import com.example.confessionapp.repository.SignalingRepository
import com.example.confessionapp.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.UUID

// PriestData could include more details if needed for a list display
data class PriestData(
    val uid: String,
    val displayName: String? // Now includes languages: "Priest XYZ (Languages: English, Spanish)"
)

sealed class PriestFindingResult {
    object Searching : PriestFindingResult()
    data class PriestsFoundList(val priests: List<PriestData>) : PriestFindingResult()
    object NoPriestsAvailable : PriestFindingResult()
    data class Error(val message: String) : PriestFindingResult()

    // New states for invitation flow
    object SendingInvitation : PriestFindingResult()
    data class InvitationSent(val invitation: CallInvitation) : PriestFindingResult()
    data class InvitationAccepted(val invitation: CallInvitation) : PriestFindingResult()
    data class InvitationRejected(val invitation: CallInvitation) : PriestFindingResult()
    data class InvitationTimeout(val invitation: CallInvitation) : PriestFindingResult() // New state for timeout
    data class InvitationError(val message: String) : PriestFindingResult()
    object Idle : PriestFindingResult() // Initial state or after an action completes
}

class ConfessorDashboardViewModel : ViewModel() {

    private val userRepository = UserRepository(FirebaseFirestore.getInstance())
    private val signalingRepository = SignalingRepository(FirebaseDatabase.getInstance().reference)
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    private val _priestFindingState = MutableLiveData<PriestFindingResult>(PriestFindingResult.Idle)
    val priestFindingState: LiveData<PriestFindingResult> = _priestFindingState

    private var currentInvitationListenerPriestId: String? = null
    private var currentInvitationListenerInvitationId: String? = null
    private var invitationTimeoutTimer: CountDownTimer? = null
    private val INVITATION_TIMEOUT_MS = 30000L // 30 seconds, adjustable

    val supportedLanguages = listOf("Any", "English", "Spanish", "French", "Latin", "German", "Italian")

    fun searchForAvailablePriests(preferredLanguage: String) {
        if (currentUserId == null) {
            _priestFindingState.value = PriestFindingResult.Error("User not logged in.")
            return
        }
        _priestFindingState.value = PriestFindingResult.Searching
        viewModelScope.launch {
            userRepository.getAvailablePriests(preferredLanguage) { priestsDocs ->
                if (priestsDocs.isNotEmpty()) {
                    val priestDataList = priestsDocs.mapNotNull { doc ->
                        val uid = doc["uid"] as? String
                        val displayNameFromDb = doc["displayName"] as? String ?: uid?.takeLast(4) ?: "N/A" // Example naming
                        val languages = doc["languages"] as? List<String> ?: emptyList()
                        if (uid != null) {
                            PriestData(uid, "Priest $displayNameFromDb (Languages: ${languages.joinToString(", ")})")
                        } else {
                            null
                        }
                    }
                    if (priestDataList.isNotEmpty()) {
                        _priestFindingState.postValue(PriestFindingResult.PriestsFoundList(priestDataList))
                    } else {
                        // This case might occur if all priests found had missing UIDs, which is unlikely with Firestore rules
                        _priestFindingState.postValue(PriestFindingResult.NoPriestsAvailable)
                    }
                } else {
                    _priestFindingState.postValue(PriestFindingResult.NoPriestsAvailable)
                }
            }
        }
    }

    fun sendInvitationToSelectedPriest(priest: PriestData) {
        if (currentUserId == null) {
            _priestFindingState.value = PriestFindingResult.InvitationError("User not logged in.")
            return
        }
        _priestFindingState.value = PriestFindingResult.SendingInvitation
        val invitationId = UUID.randomUUID().toString()
        val roomId = UUID.randomUUID().toString()

        val invitation = CallInvitation(
            invitationId = invitationId,
            roomId = roomId,
            confessorId = currentUserId!!,
            priestId = priest.uid, // Use priest.uid
            status = CallInvitation.PENDING
        )

        signalingRepository.sendInvitation(priest.uid, invitation) { success ->
            if (success) {
                _priestFindingState.postValue(PriestFindingResult.InvitationSent(invitation))
                listenForInvitationResponse(priest.uid, invitationId, invitation) // Pass full invitation for timeout
            } else {
                _priestFindingState.postValue(PriestFindingResult.InvitationError("Failed to send invitation."))
            }
        }
    }

    private fun listenForInvitationResponse(priestId: String, invitationId: String, originalInvitation: CallInvitation) {
        currentInvitationListenerPriestId = priestId
        currentInvitationListenerInvitationId = invitationId
        invitationTimeoutTimer?.cancel() // Cancel any existing timer
        invitationTimeoutTimer = object : CountDownTimer(INVITATION_TIMEOUT_MS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // Optionally update UI with countdown, not implemented here
                Log.d("ConfessorVM", "Invitation ${invitationId} timeout in: ${millisUntilFinished / 1000}s")
            }

            override fun onFinish() {
                Log.d("ConfessorVM", "Invitation ${invitationId} timed out.")
                // Check if we are still in InvitationSent state for this specific invitation
                val currentState = _priestFindingState.value
                if (currentState is PriestFindingResult.InvitationSent && currentState.invitation.invitationId == invitationId) {
                    _priestFindingState.postValue(PriestFindingResult.InvitationTimeout(originalInvitation))
                    // Update status in DB to EXPIRED or MISSED
                    signalingRepository.updateInvitationStatus(priestId, invitationId, CallInvitation.EXPIRED) { Log.d("ConfessorVM", "Invitation $invitationId status updated to EXPIRED on timeout.")}
                }
                stopListeningForCurrentInvitation() // Stop DB listener as well
            }
        }.start()

        signalingRepository.listenToInvitationStatus(priestId, invitationId) { updatedInvitation ->
            if (updatedInvitation != null) {
                // Only process if the status is relevant and we haven't timed out already for this invite
                if (_priestFindingState.value !is PriestFindingResult.InvitationTimeout ||
                    (_priestFindingState.value as? PriestFindingResult.InvitationTimeout)?.invitation?.invitationId != updatedInvitation.invitationId) {

                    when (updatedInvitation.status) {
                        CallInvitation.ACCEPTED -> {
                            invitationTimeoutTimer?.cancel()
                            _priestFindingState.postValue(PriestFindingResult.InvitationAccepted(updatedInvitation))
                            stopListeningForCurrentInvitation()
                        }
                        CallInvitation.REJECTED -> {
                            invitationTimeoutTimer?.cancel()
                            _priestFindingState.postValue(PriestFindingResult.InvitationRejected(updatedInvitation))
                            stopListeningForCurrentInvitation()
                        }
                        CallInvitation.PENDING -> { /* Still waiting, timer is running */ }
                        else -> { // EXPIRED, MISSED, etc. handled by timeout or priest's action if they respond late
                            invitationTimeoutTimer?.cancel()
                            Log.d("ConfessorVM", "Invitation ${updatedInvitation.invitationId} has status ${updatedInvitation.status}, handled by timeout or priest.")
                            // If priest set to MISSED, we might want a specific UI update
                            if (updatedInvitation.status == CallInvitation.MISSED || updatedInvitation.status == CallInvitation.EXPIRED) {
                                // Ensure UI reflects this if timer didn't catch it first or priest set it
                                if (!(_priestFindingState.value is PriestFindingResult.InvitationTimeout &&
                                    (_priestFindingState.value as PriestFindingResult.InvitationTimeout).invitation.invitationId == updatedInvitation.invitationId)) {
                                    _priestFindingState.postValue(PriestFindingResult.InvitationTimeout(updatedInvitation)) // Using Timeout state for general "no response"
                                }
                            }
                            stopListeningForCurrentInvitation()
                        }
                    }
                }
            } else { // Listener cancelled or error from repository
                invitationTimeoutTimer?.cancel()
                // Only post error if we weren't already in a terminal state for this invitation
                if (!(_priestFindingState.value is PriestFindingResult.InvitationAccepted ||
                      _priestFindingState.value is PriestFindingResult.InvitationRejected ||
                      _priestFindingState.value is PriestFindingResult.InvitationTimeout)) {
                    _priestFindingState.postValue(PriestFindingResult.InvitationError("Error listening to invitation status."))
                }
                stopListeningForCurrentInvitation()
            }
        }
    }

    fun cancelCurrentInvitationSearch() { // Renamed to reflect it cancels more than just listener
        invitationTimeoutTimer?.cancel()
        val currentSentInvitation = (_priestFindingState.value as? PriestFindingResult.InvitationSent)?.invitation
        if (currentSentInvitation != null) {
            // Update status in DB to EXPIRED or CANCELLED by confessor
            signalingRepository.updateInvitationStatus(currentSentInvitation.priestId, currentSentInvitation.invitationId, CallInvitation.EXPIRED) {
                Log.d("ConfessorVM", "Invitation ${currentSentInvitation.invitationId} cancelled by confessor.")
            }
        }
        stopListeningForCurrentInvitation()
        _priestFindingState.postValue(PriestFindingResult.Idle)
    }

    private fun stopListeningForCurrentInvitation() {
        invitationTimeoutTimer?.cancel() // Ensure timer is always stopped
        invitationTimeoutTimer = null
        currentInvitationListenerPriestId?.let { pId ->
            currentInvitationListenerInvitationId?.let { invId ->
                signalingRepository.removeInvitationStatusListener()
            }
        }
        currentInvitationListenerPriestId = null
        currentInvitationListenerInvitationId = null
    }

    override fun onCleared() {
        super.onCleared()
        stopListeningForCurrentInvitation()
    }
}
