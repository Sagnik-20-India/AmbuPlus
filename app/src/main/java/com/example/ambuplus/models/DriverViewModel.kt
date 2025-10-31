package com.example.ambuplus.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ambuplus.data.DriverRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DriverViewModel(private val driverRepository: DriverRepository) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    suspend fun updateDriverAvailability(driverId: String, available: Boolean) {
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                driverRepository.updateDriverAvailability(driverId, available)
                _isLoading.value = false
            } catch (e: Exception) {
                _isLoading.value = false
                _errorMessage.value = e.message ?: "Failed to update availability"
            }
        }
    }

    // ViewModel Factory
    class DriverViewModelFactory(private val driverRepository: DriverRepository) {
        fun create(): DriverViewModel {
            return DriverViewModel(driverRepository)
        }
    }
}