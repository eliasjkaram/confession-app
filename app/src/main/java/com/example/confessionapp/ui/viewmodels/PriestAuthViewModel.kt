package com.example.confessionapp.ui.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.confessionapp.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class PriestAuthViewModel : ViewModel() {

    private val authRepository = AuthRepository(FirebaseAuth.getInstance())

    private val _priestLoginResult = MutableLiveData<Boolean>()
    val priestLoginResult: LiveData<Boolean> = _priestLoginResult

    private val _priestRegisterResult = MutableLiveData<Boolean>()
    val priestRegisterResult: LiveData<Boolean> = _priestRegisterResult

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorEvent = MutableLiveData<String?>()
    val errorEvent: LiveData<String?> = _errorEvent

    fun loginPriest(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _errorEvent.value = "Email and password cannot be empty."
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            authRepository.signInWithEmail(email, password) { success ->
                _priestLoginResult.postValue(success)
                if (!success) _errorEvent.postValue("Login failed. Check credentials.")
                _isLoading.postValue(false)
            }
        }
    }

    fun registerPriest(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _errorEvent.value = "Email and password cannot be empty."
            return
        }
        if (password.length < 6) {
            _errorEvent.value = "Password must be at least 6 characters long."
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            authRepository.registerWithEmail(email, password) { success ->
                _priestRegisterResult.postValue(success)
                if (!success) _errorEvent.postValue("Registration failed. Email might be in use or invalid.")
                _isLoading.postValue(false)
            }
        }
    }

    fun doneConsumingError() {
        _errorEvent.value = null
    }
}
