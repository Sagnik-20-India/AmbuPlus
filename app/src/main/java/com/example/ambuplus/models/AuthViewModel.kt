package com.example.ambuplus.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ambuplus.data.AuthRepository
import io.github.jan.supabase.gotrue.user.UserInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _currentUser = MutableStateFlow<UserInfo?>(null)
    val currentUser: StateFlow<UserInfo?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        _currentUser.value = authRepository.currentUser()
    }

    fun signUp(email: String, password: String, name: String) {
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            val result = authRepository.signUp(email, password, name)
            _isLoading.value = false

            if (result.isSuccess) {
                val user = result.getOrNull()
                if (user == null) {
                    // Email confirmation required
                    _errorMessage.value = "Verification email sent! Please check your inbox and confirm your email before logging in."
                } else {
                    _currentUser.value = user
                }
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Sign up failed"
            }
        }
    }

//    fun signUp(email: String, password: String, name: String) {
//        _isLoading.value = true
//        _errorMessage.value = null
//
//        viewModelScope.launch {
//            val result = authRepository.signUp(email, password, name)
//            _isLoading.value = false
//
//            if (result.isSuccess) {
//                _currentUser.value = result.getOrNull()
//            } else {
//                _errorMessage.value = result.exceptionOrNull()?.message ?: "Sign up failed"
//            }
//        }
//    }

    fun login(email: String, password: String) {
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            val result = authRepository.login(email, password)
            _isLoading.value = false

            if (result.isSuccess) {
                _currentUser.value = result.getOrNull()
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Login failed"
            }
        }
    }

    // Add this function to your AuthViewModel.kt
    fun resetPassword(email: String) {
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            val result = authRepository.resetPassword(email)
            _isLoading.value = false

            if (result.isSuccess) {
                _errorMessage.value = "Password reset email sent. Check your inbox."
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to send reset email"
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _currentUser.value = null
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}