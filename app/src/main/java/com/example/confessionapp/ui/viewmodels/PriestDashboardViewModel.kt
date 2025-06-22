package com.example.confessionapp.ui.viewmodels

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

class PriestDashboardViewModel : ViewModel() {

    private val userRepository = UserRepository(FirebaseFirestore.getInstance())
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val signalingRepository = SignalingRepository(FirebaseDatabase.getInstance().reference)

    private val _isLoadingAvailability = MutableLiveData<Boolean>() // For availability toggle
    val isLoadingAvailability: LiveData<Boolean> = _isLoadingAvailability

    private val _isAvailableForConfession = MutableLiveData<Boolean>()
    val isAvailableForConfession: LiveData<Boolean> = _isAvailableForConfession

    private val _availabilityUpdateResult = MutableLiveData<Boolean>()
    val availabilityUpdateResult: LiveData<Boolean> = _availabilityUpdateResult

    // --- Invitation Handling ---
    private val _incomingInvitation = MutableLiveData<CallInvitation?>()
    val incomingInvitation: LiveData<CallInvitation?> = _incomingInvitation

    private val _invitationAcceptedEvent = MutableLiveData<CallInvitation?>()
    val invitationAcceptedEvent: LiveData<CallInvitation?> = _invitationAcceptedEvent

    private var invitationListener: com.google.firebase.database.ChildEventListener? = null
    private var priestInvitationsRef: com.google.firebase.database.DatabaseReference? = null

    // To be called when dashboard is active to fetch current availability
    fun fetchPriestAvailability() {
        _isLoadingAvailability.value = true
        firebaseAuth.currentUser?.uid?.let { priestId ->
            userRepository.getUserProfile(priestId) { profile ->
                val available = profile?.get("isAvailableForConfession") as? Boolean ?: false
                _isAvailableForConfession.postValue(available)
                _isLoadingAvailability.postValue(false)
            }
        } ?: run {
            _isAvailableForConfession.postValue(false) // Default if no user
            _isLoadingAvailability.postValue(false)
        }
    }

    fun updatePriestAvailability(isAvailable: Boolean) {
        _isLoadingAvailability.value = true
        val priestId = firebaseAuth.currentUser?.uid ?: run {
            _availabilityUpdateResult.postValue(false)
            _isLoadingAvailability.postValue(false)
            return
        }
        userRepository.setPriestAvailability(priestId, isAvailable) { success ->
            if (success) {
                _isAvailableForConfession.postValue(isAvailable)
            }
            _availabilityUpdateResult.postValue(success)
            _isLoadingAvailability.postValue(false)
        }
    }

    fun listenForIncomingInvitations() {
        val priestId = firebaseAuth.currentUser?.uid ?: return
        stopListeningForIncomingInvitations() // Clean up previous listener

        priestInvitationsRef = FirebaseDatabase.getInstance().getReference("invitations").child(priestId)
        invitationListener = object : com.google.firebase.database.ChildEventListener {
            override fun onChildAdded(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {
                val invitation = snapshot.getValue(CallInvitation::class.java)
                if (invitation != null && invitation.status == CallInvitation.PENDING) {
                    if (_incomingInvitation.value?.status == CallInvitation.PENDING) {
                        Log.d("PriestDashboardVM", "Already have a pending invitation, ignoring new one: ${invitation.invitationId}")
                        return
                    }
                    _incomingInvitation.postValue(invitation)
                }
            }

            override fun onChildChanged(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {
                val invitation = snapshot.getValue(CallInvitation::class.java)
                if (invitation != null && _incomingInvitation.value?.invitationId == invitation.invitationId) {
                    if (invitation.status != CallInvitation.PENDING) {
                        _incomingInvitation.postValue(null)
                    } else {
                        _incomingInvitation.postValue(invitation)
                    }
                }
            }

            override fun onChildRemoved(snapshot: com.google.firebase.database.DataSnapshot) {
                val invitation = snapshot.getValue(CallInvitation::class.java)
                if (invitation != null && _incomingInvitation.value?.invitationId == invitation.invitationId) {
                    _incomingInvitation.postValue(null)
                }
            }
            override fun onChildMoved(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.e("PriestDashboardVM", "Invitation listener cancelled: ${error.message}")
            }
        }
        // Query for pending invitations
        priestInvitationsRef?.orderByChild("status").equalTo(CallInvitation.PENDING)
            .addChildEventListener(invitationListener!!)
    }

    fun acceptInvitation(invitation: CallInvitation) {
        signalingRepository.updateInvitationStatus(invitation.priestId, invitation.invitationId, CallInvitation.ACCEPTED) { success ->
            if (success) {
                _incomingInvitation.postValue(null)
                _invitationAcceptedEvent.postValue(invitation)
            } else {
                Log.e("PriestDashboardVM", "Failed to update invitation status to ACCEPTED for ${invitation.invitationId}")
                // Notify UI of failure
            }
        }
    }

    fun onInvitationAcceptedEventConsumed() {
        _invitationAcceptedEvent.value = null
    }

    fun rejectInvitation(invitation: CallInvitation) {
        signalingRepository.updateInvitationStatus(invitation.priestId, invitation.invitationId, CallInvitation.REJECTED) { success ->
             if (success) {
                _incomingInvitation.postValue(null)
             } else {
                 Log.e("PriestDashboardVM", "Failed to update invitation status to REJECTED for ${invitation.invitationId}")
             }
        }
    }

    fun clearIncomingInvitation() { // In case dialog is dismissed manually by activity
        _incomingInvitation.postValue(null)
    }

    private fun stopListeningForIncomingInvitations() {
        invitationListener?.let { listener ->
            priestInvitationsRef?.removeEventListener(listener)
        }
        invitationListener = null
        priestInvitationsRef = null
        _incomingInvitation.postValue(null)
    }

    override fun onCleared() {
        super.onCleared()
        stopListeningForIncomingInvitations()
    }
}
