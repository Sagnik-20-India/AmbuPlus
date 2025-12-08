package com.example.ambuplus.uiactivities.request

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.ambuplus.databinding.ActivityRequestBinding
import com.example.ambuplus.models.AuthViewModel
import com.example.ambuplus.models.RequestViewModel
import com.example.ambuplus.utils.ServiceLocator
import kotlinx.coroutines.launch

class RequestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRequestBinding
    private lateinit var authViewModel: AuthViewModel
    private lateinit var requestViewModel: RequestViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRequestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize ViewModels with proper factories
        val authFactory = AuthViewModelFactory(ServiceLocator.authRepository)
        authViewModel = ViewModelProvider(this, authFactory)[AuthViewModel::class.java]

        val requestFactory = RequestViewModelFactory(ServiceLocator.requestRepository)
        requestViewModel = ViewModelProvider(this, requestFactory)[RequestViewModel::class.java]

        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        // Observe currentUser StateFlow
        lifecycleScope.launch {
            authViewModel.currentUser.collect { user ->
                if (user == null) {
                    // User not logged in, should not happen but handle gracefully
                    finish()
                }
            }
        }

        // Observe isLoading StateFlow
        lifecycleScope.launch {
            requestViewModel.isLoading.collect { isLoading ->
                val progressBar = findViewById<android.widget.ProgressBar>(com.example.ambuplus.R.id.progressBar)
                progressBar?.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
                binding.btnSubmitRequest.isEnabled = !isLoading
            }
        }

        // Observe errorMessage StateFlow
        lifecycleScope.launch {
            requestViewModel.errorMessage.collect { error ->
                val tvError = findViewById<android.widget.TextView>(com.example.ambuplus.R.id.tvError)
                if (error != null) {
                    tvError?.text = error
                    tvError?.visibility = android.view.View.VISIBLE
                    Toast.makeText(this@RequestActivity, error, Toast.LENGTH_LONG).show()
                } else {
                    tvError?.visibility = android.view.View.GONE
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnSubmitRequest.setOnClickListener {
            submitRequest()
        }

        val tvError = findViewById<android.widget.TextView>(com.example.ambuplus.R.id.tvError)
        tvError?.setOnClickListener {
            requestViewModel.clearError()
        }
    }

    private fun submitRequest() {
        val currentUser = authViewModel.currentUser.value
        if (currentUser == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            return
        }

        android.util.Log.d("RequestActivity", "User ID: ${currentUser.id}")


        val patientName = binding.etPatientName.text.toString().trim()
        val patientAgeText = binding.etPatientAge.text.toString().trim()
        val contactNumber = binding.etContactNumber.text.toString().trim()
        val location = binding.etLocation.text.toString().trim()
        val medicalNotes = binding.etMedicalNotes.text.toString().trim()

        // Get emergency level using RadioGroup
        val emergencyLevel = when (binding.rgEmergencyLevel.checkedRadioButtonId) {
            com.example.ambuplus.R.id.rbHigh -> "high"
            com.example.ambuplus.R.id.rbMedium -> "medium"
            com.example.ambuplus.R.id.rbLow -> "low"
            else -> "medium" // default
        }

        if (validateInputs(patientName, patientAgeText, contactNumber, location)) {
            val patientAge = if (patientAgeText.isNotEmpty()) patientAgeText.toInt() else null

            // Create the request
            requestViewModel.createRequest(
                userId = currentUser.id,
                location = location,
                emergencyLevel = emergencyLevel,
                contactNumber = contactNumber,
                patientName = patientName,
                patientAge = patientAge,
                medicalNotes = medicalNotes
            )

            // Navigate immediately (don't wait for backend response)
            Toast.makeText(this, "Ambulance request submitted successfully!", Toast.LENGTH_SHORT).show()

            val intent = Intent(this, RequestDetailActivity::class.java).apply {
                putExtra("patient_name", patientName)
                putExtra("contact_number", contactNumber)
                putExtra("location", location)
                putExtra("emergency_level", emergencyLevel)
                putExtra("medical_notes", medicalNotes)
                patientAge?.let { putExtra("patient_age", it) }
            }

            android.util.Log.d("RequestActivity", "Starting RequestDetailActivity")


            startActivity(intent)
            // Don't call finish() here so user can go back to edit if needed
        }
    }

    private fun validateInputs(
        patientName: String,
        patientAge: String,
        contactNumber: String,
        location: String
    ): Boolean {
        if (patientName.isEmpty()) {
            showError("Please enter patient name")
            return false
        }

        if (contactNumber.isEmpty()) {
            showError("Please enter contact number")
            return false
        }

        if (location.isEmpty()) {
            showError("Please enter pickup location")
            return false
        }

        if (patientAge.isNotEmpty()) {
            try {
                val age = patientAge.toInt()
                if (age <= 0 || age > 150) {
                    showError("Please enter a valid age")
                    return false
                }
            } catch (e: NumberFormatException) {
                showError("Please enter a valid age")
                return false
            }
        }

        return true
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

// Factory classes
class AuthViewModelFactory(private val authRepository: com.example.ambuplus.data.AuthRepository) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class RequestViewModelFactory(private val requestRepository: com.example.ambuplus.data.RequestRepository) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RequestViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RequestViewModel(requestRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}