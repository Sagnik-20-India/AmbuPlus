package com.example.ambuplus.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ambuplus.data.RequestRepository
import com.example.ambuplus.models.Request
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RequestViewModel(private val requestRepository: RequestRepository) : ViewModel() {

    private val _requests = MutableStateFlow<List<Request>>(emptyList())
    val requests: StateFlow<List<Request>> = _requests.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun createRequest(
        userId: String,
        location: String,
        emergencyLevel: String,
        contactNumber: String,
        patientName: String? = null,
        patientAge: Int? = null,
        medicalNotes: String? = null
    ) {
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val request = Request(
                    userId = userId,
                    location = location,
                    emergencyLevel = emergencyLevel,
                    contactNumber = contactNumber,
                    timestamp = timestamp,
                    patientName = patientName,
                    patientAge = patientAge,
                    medicalNotes = medicalNotes
                )

                requestRepository.addRequest(request)
                _isLoading.value = false
            } catch (e: Exception) {
                _isLoading.value = false
                _errorMessage.value = e.message ?: "Failed to create request"
            }
        }
    }

    fun loadUserRequests(userId: String) {
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val userRequests = requestRepository.getRequestsByUser(userId)
                _requests.value = userRequests
                _isLoading.value = false
            } catch (e: Exception) {
                _isLoading.value = false
                _errorMessage.value = e.message ?: "Failed to load requests"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // ViewModel Factory
    class RequestViewModelFactory(private val requestRepository: RequestRepository) {
        fun create(): RequestViewModel {
            return RequestViewModel(requestRepository)
        }
    }
}