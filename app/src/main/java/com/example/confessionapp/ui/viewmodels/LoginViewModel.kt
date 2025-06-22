package com.example.confessionapp.ui.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.confessionapp.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {

    private val authRepository = AuthRepository(FirebaseAuth.getInstance())

    private val _anonymousSignInResult = MutableLiveData<Boolean>()
    val anonymousSignInResult: LiveData<Boolean> = _anonymousSignInResult

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun signInAnonymously() {
        _isLoading.value = true
        viewModelScope.launch {
            authRepository.signInAnonymously { success ->
                _anonymousSignInResult.postValue(success)
                _isLoading.postValue(false)
            }
        }
    }
}
