package com.example.confessionapp.ui.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.confessionapp.repository.DonationRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class GooglePayDonationViewModel : ViewModel() {

    private val donationRepository = DonationRepository(FirebaseFirestore.getInstance())
    private val firebaseAuth = FirebaseAuth.getInstance()

    private val _donationRecordResult = MutableLiveData<Boolean>()
    val donationRecordResult: LiveData<Boolean> = _donationRecordResult

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun recordDonation(amount: Double, currency: String, paymentToken: String, orderId: String) {
        _isLoading.value = true
        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            _donationRecordResult.postValue(false)
            _isLoading.postValue(false)
            // Handle error: user not logged in
            return
        }

        // Structure for the donation object, can be expanded
        val donationData = hashMapOf(
            "uid" to userId,
            "amount" to amount,
            "currency" to currency,
            "timestamp" to com.google.firebase.Timestamp.now(),
            "payment_method" to "google_pay",
            "orderId" to orderId, // from Google Pay response
            "status" to "completed" // Assuming it's completed if this method is called
        )

        viewModelScope.launch {
            donationRepository.recordDonation(userId, donationData) { success ->
                _donationRecordResult.postValue(success)
                _isLoading.postValue(false)
            }
        }
    }
}
