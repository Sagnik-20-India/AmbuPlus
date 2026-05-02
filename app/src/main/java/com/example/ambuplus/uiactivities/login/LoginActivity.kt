package com.example.ambuplus.uiactivities.login

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Button
import com.example.ambuplus.R
import android.widget.Toast
import java.util.UUID
import androidx.appcompat.app.AlertDialog
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.ambuplus.databinding.ActivityLoginBinding
import com.example.ambuplus.uiactivities.main.MainActivity
import com.example.ambuplus.uiactivities.request.RequestActivity
import com.example.ambuplus.uiactivities.request.RequestDetailActivity
import com.example.ambuplus.models.AuthViewModel
import com.example.ambuplus.utils.ServiceLocator
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import android.util.Log

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(ServiceLocator.authRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        // Observe currentUser StateFlow
        lifecycleScope.launch {
            authViewModel.currentUser.collect { user ->
                if (user != null) {
                    navigateToMain()
                }
            }
        }

        // Observe isLoading StateFlow
        lifecycleScope.launch {
            authViewModel.isLoading.collect { isLoading ->
                binding.progressBar.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
                binding.btnLogin.isEnabled = !isLoading
            }
        }

        // Observe errorMessage StateFlow
        lifecycleScope.launch {
            authViewModel.errorMessage.collect { error ->
                if (error != null) {
                    // Check if it's a confirmation message
                    if (error.contains("confirmation") || error.contains("email")) {
                        showEmailVerificationDialog()
                    } else {
                        binding.tvError.text = error
                        binding.tvError.visibility = android.view.View.VISIBLE
                    }
                } else {
                    binding.tvError.visibility = android.view.View.GONE
                }
            }
        }
    }
//        lifecycleScope.launch {
//            authViewModel.errorMessage.collect { error ->
//                if (error != null) {
//                    binding.tvError.text = error
//                    binding.tvError.visibility = android.view.View.VISIBLE
//
//                } else {
//                    binding.tvError.visibility = android.view.View.GONE
//                }
//            }
//        }
//    }

    private fun showEmailVerificationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Verify Your Email")
            .setMessage("We've sent a verification link to your email address. Please check your inbox and click the link to verify your account before logging in.")
            .setPositiveButton("OK") { _, _ ->
                // Dismiss dialog
            }
            .setCancelable(false)
            .show()
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (validateInputs(email, password)) {
                authViewModel.login(email, password)
            }
        }

        binding.tvSignUp.setOnClickListener {
            showSimpleSignUpDialog()
        }

        binding.tvError.setOnClickListener {
            authViewModel.clearError()
        }

        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }

//        binding.btnEmergencyRequest.setOnClickListener {
//            // Check emergency request limit
//            val prefs = getSharedPreferences("EmergencyPrefs", MODE_PRIVATE)
//            val sessionId = getSessionId()
//            val emergencyCount = prefs.getInt("emergency_count_$sessionId", 0)
//
//            if (emergencyCount >= 3) {
//                AlertDialog.Builder(this)
//                    .setTitle("Emergency Limit Reached")
//                    .setMessage("You have used all 3 emergency requests. Please sign up to continue using AmbuPlus.")
//                    .setPositiveButton("Sign Up") { _, _ ->
//                        showSimpleSignUpDialog()
//                    }
//                    .setNegativeButton("Cancel", null)
//                    .show()
//            } else {
//                // Open RequestActivity in emergency mode
//                val intent = Intent(this, RequestActivity::class.java)
//                intent.putExtra("emergency_mode", true)
//                startActivity(intent)
//            }
//        }

        binding.btnEmergencyRequest.setOnClickListener {
            // First, check if there's an active emergency request
            lifecycleScope.launch {
                val activeEmergencyRequest = checkForActiveEmergencyRequest()

                if (activeEmergencyRequest != null) {
                    // User has an active emergency request - go to tracking
                    when (activeEmergencyRequest.status) {
                        "pending" -> {
                            Toast.makeText(this@LoginActivity,
                                "You have an active emergency request. Opening tracking...",
                                Toast.LENGTH_LONG).show()
                            navigateToRequestDetail(activeEmergencyRequest)
                        }
                        "accepted" -> {
                            Toast.makeText(this@LoginActivity,
                                "Ambulance is on the way! Opening tracking...",
                                Toast.LENGTH_LONG).show()
                            navigateToPatientTracking(activeEmergencyRequest)
                        }
                        else -> {
                            // Request is completed or cancelled, allow new request
                            checkEmergencyLimitAndProceed()
                        }
                    }
                } else {
                    // No active request, check limit and proceed
                    checkEmergencyLimitAndProceed()
                }
            }
        }

    }

    // Add this function to check for active emergency request
    private suspend fun checkForActiveEmergencyRequest(): com.example.ambuplus.models.Request? {
        val prefs = getSharedPreferences("EmergencyPrefs", MODE_PRIVATE)
        val savedRequestId = prefs.getInt("emergency_active_request_id", -1)

        if (savedRequestId != -1) {
            val request = ServiceLocator.requestRepository.getRequestById(savedRequestId)
            if (request != null && (request.status == "pending" || request.status == "accepted")) {
                return request
            } else {
                // Clear invalid saved request
                prefs.edit().remove("emergency_active_request_id").apply()
            }
        }
        return null
    }

    // Add this function to handle emergency limit check and proceed
    private fun checkEmergencyLimitAndProceed() {
        val prefs = getSharedPreferences("EmergencyPrefs", MODE_PRIVATE)
        val sessionId = getSessionId()
        val emergencyCount = prefs.getInt("emergency_count_$sessionId", 0)

        if (emergencyCount >= 3) {
            AlertDialog.Builder(this)
                .setTitle("Emergency Limit Reached")
                .setMessage("You have used all 3 emergency requests. Please sign up to continue using AmbuPlus.")
                .setPositiveButton("Sign Up") { _, _ ->
                    showSimpleSignUpDialog()
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            // Open RequestActivity in emergency mode
            val intent = Intent(this, RequestActivity::class.java)
            intent.putExtra("emergency_mode", true)
            startActivity(intent)
        }
    }

    // Add navigation functions
    private fun navigateToRequestDetail(request: com.example.ambuplus.models.Request) {
        val intent = Intent(this, RequestDetailActivity::class.java).apply {
            putExtra("patient_name", request.patientName ?: "")
            putExtra("contact_number", request.contactNumber)
            putExtra("location", request.location.split("|").firstOrNull() ?: request.location)
            putExtra("emergency_level", request.emergencyLevel)
            putExtra("medical_notes", request.medicalNotes ?: "")
            putExtra("request_id", request.id ?: -1)
            val coords = extractCoordinates(request.location)
            putExtra("selected_lat", coords?.first ?: 0.0)
            putExtra("selected_lng", coords?.second ?: 0.0)
            request.patientAge?.let { putExtra("patient_age", it) }
            putExtra("is_emergency_mode", true)
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToPatientTracking(request: com.example.ambuplus.models.Request) {
        val coords = extractCoordinates(request.location)
        val intent = Intent(this, com.example.ambuplus.uiactivities.patient.PatientTrackingActivity::class.java).apply {
            putExtra("request_id", request.id ?: -1)
            putExtra("pickup_lat", coords?.first ?: 0.0)
            putExtra("pickup_lng", coords?.second ?: 0.0)
            putExtra("patient_name", request.patientName ?: "Patient")
            putExtra("driver_id", request.driverId ?: "")
            putExtra("is_emergency_mode", true)
        }
        startActivity(intent)
        finish()
    }

    private fun extractCoordinates(location: String): Pair<Double, Double>? {
        return try {
            if (location.contains("|")) {
                val parts = location.split("|")
                if (parts.size == 2) {
                    val coords = parts[1].split(",")
                    if (coords.size == 2) {
                        Pair(coords[0].toDouble(), coords[1].toDouble())
                    } else null
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun getSessionId(): String {
        val prefs = getSharedPreferences("EmergencyPrefs", MODE_PRIVATE)
        var sessionId = prefs.getString("emergency_session_id", null)
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString()
            prefs.edit().putString("emergency_session_id", sessionId).apply()
        }
        return sessionId
    }

    private fun showSimpleSignUpDialog() {
        // Inflate the dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_signup, null)

        // Method 1: Try using the view's resources
        val etName = dialogView.findViewById<EditText>(dialogView.resources.getIdentifier("signupName", "id", packageName))
        val etEmail = dialogView.findViewById<EditText>(dialogView.resources.getIdentifier("signupEmail", "id", packageName))
        val etPassword = dialogView.findViewById<EditText>(dialogView.resources.getIdentifier("signupPassword", "id", packageName))
        val etConfirmPassword = dialogView.findViewById<EditText>(dialogView.resources.getIdentifier("signupConfirmPassword", "id", packageName))

        val dialog = AlertDialog.Builder(this)
            .setTitle("Create Account")
            .setView(dialogView)
            .setPositiveButton("Sign Up") { dialog, _ ->
                val name = etName?.text.toString().trim()
                val email = etEmail?.text.toString().trim()
                val password = etPassword?.text.toString().trim()
                val confirmPassword = etConfirmPassword?.text.toString().trim()

                if (validateSignUpInputs(name, email, password, confirmPassword)) {
                    authViewModel.signUp(email, password, name)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        dialog.show()
    }

    private fun validateInputs(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            showError("Please enter your email")
            return false
        }

        // Add email format validation for login
        if (!isValidEmailFormat(email)) {
            showError("Please enter a valid email address (e.g., name@gmail.com)")
            return false
        }

        if (password.isEmpty()) {
            showError("Please enter your password")
            return false
        }

        if (password.length < 6) {
            showError("Password must be at least 6 characters")
            return false
        }

        return true
    }

    private fun validateSignUpInputs(name: String, email: String, password: String, confirmPassword: String): Boolean {
        if (name.isEmpty()) {
            showError("Please enter your name")
            return false
        }

        if (email.isEmpty()) {
            showError("Please enter your email")
            return false
        }

        // Add email format validation for signup
        if (!isValidEmailFormat(email)) {
            showError("Please enter a valid email address (e.g., name@gmail.com)")
            return false
        }

        if (password.isEmpty()) {
            showError("Please enter your password")
            return false
        }

        if (password.length < 6) {
            showError("Password must be at least 6 characters")
            return false
        }

        if (password != confirmPassword) {
            showError("Passwords do not match")
            return false
        }

        return true
    }

    private fun isValidEmailFormat(email: String): Boolean {
        // Simple email validation: must contain @ and have a dot after @
        if (!email.contains("@")) return false
        val domain = email.substringAfter("@")
        return domain.contains(".") && domain.length >= 4
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun showForgotPasswordDialog() {
        val emailInput = EditText(this)
        emailInput.hint = "Enter your email"
        emailInput.inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

        AlertDialog.Builder(this)
            .setTitle("Reset Password")
            .setMessage("We'll send a password reset link to your email")
            .setView(emailInput)
            .setPositiveButton("Send Reset Link") { _, _ ->
                val email = emailInput.text.toString().trim()
                if (email.isNotEmpty()) {
                    // Log the email before sending
                    Log.d("ForgotPassword", "Sending reset link to: $email")
                    authViewModel.resetPassword(email)
                } else {
                    Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

}

// Factory class for AuthViewModel
class AuthViewModelFactory(private val authRepository: com.example.ambuplus.data.AuthRepository) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}