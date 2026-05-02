package com.example.ambuplus.uiactivities.request

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.ambuplus.R
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.ambuplus.databinding.ActivityRequestBinding
import com.example.ambuplus.models.AuthViewModel
import com.example.ambuplus.models.RequestViewModel
import com.example.ambuplus.utils.ServiceLocator
import io.github.jan.supabase.gotrue.user.UserInfo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class RequestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRequestBinding
    private lateinit var authViewModel: AuthViewModel
    private lateinit var requestViewModel: RequestViewModel

    private var selectedLat: Double? = null
    private var selectedLng: Double? = null
    private var selectedAddress: String = ""

    // Emergency mode variables
    private var isEmergencyMode = false
    private val MAX_EMERGENCY_REQUESTS = 3

    // Fixed dummy user ID for emergency requests
    private companion object {
        private const val EMERGENCY_USER_ID = "bfca1d22-8d85-43b2-b825-fe2a7a582ad7"
    }

    // Register for activity result
    private val mapPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            selectedLat = data?.getDoubleExtra("latitude", 0.0) ?: 0.0
            selectedLng = data?.getDoubleExtra("longitude", 0.0) ?: 0.0
            selectedAddress = data?.getStringExtra(MapLocationPickerActivity.EXTRA_SELECTED_ADDRESS) ?: ""

            if (selectedLat != 0.0 && selectedLng != 0.0) {
                binding.etLocation.setText(selectedAddress)
            }
        }
    }

    // Location permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            openMapLocationPicker()
        } else {
            Toast.makeText(this, "Location permission required to select pickup location", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRequestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if this is emergency mode
        isEmergencyMode = intent.getBooleanExtra("emergency_mode", false)

        if (isEmergencyMode) {
            // Show emergency banner
            binding.tvEmergencyBanner.visibility = android.view.View.VISIBLE
            updateEmergencyCountDisplay()
            Toast.makeText(this, "🚨 EMERGENCY MODE - No login required", Toast.LENGTH_LONG).show()
        }

        // Initialize ViewModels with proper factories
        val authFactory = AuthViewModelFactory(ServiceLocator.authRepository)
        authViewModel = ViewModelProvider(this, authFactory)[AuthViewModel::class.java]

        val requestFactory = RequestViewModelFactory(ServiceLocator.requestRepository)
        requestViewModel = ViewModelProvider(this, requestFactory)[RequestViewModel::class.java]

        setupObservers()
        setupClickListeners()
    }

    // Function to get device session ID
    private fun getSessionId(): String {
        val prefs = getSharedPreferences("EmergencyPrefs", MODE_PRIVATE)
        var sessionId = prefs.getString("emergency_session_id", null)
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString()
            prefs.edit().putString("emergency_session_id", sessionId).apply()
        }
        return sessionId
    }

    // Function to get current emergency count
    private fun getEmergencyCount(): Int {
        val prefs = getSharedPreferences("EmergencyPrefs", MODE_PRIVATE)
        val sessionId = getSessionId()
        return prefs.getInt("emergency_count_$sessionId", 0)
    }

    // Function to update emergency count display
    private fun updateEmergencyCountDisplay() {
        val usedCount = getEmergencyCount()
        val remaining = MAX_EMERGENCY_REQUESTS - usedCount

        when {
            remaining > 1 -> {
                binding.tvEmergencyBanner.text = "🚨 EMERGENCY MODE: $remaining requests remaining"
                binding.tvEmergencyBanner.setBackgroundColor(android.graphics.Color.parseColor("#F44336"))
                binding.btnSubmitRequest.isEnabled = true
            }
            remaining == 1 -> {
                binding.tvEmergencyBanner.text = "🚨 EMERGENCY MODE: LAST REQUEST REMAINING!"
                binding.tvEmergencyBanner.setBackgroundColor(android.graphics.Color.parseColor("#FF9800"))
                binding.btnSubmitRequest.isEnabled = true
            }
            else -> {
                binding.tvEmergencyBanner.text = "🚨 EMERGENCY MODE: Limit reached (3/3). Please sign up."
                binding.tvEmergencyBanner.setBackgroundColor(android.graphics.Color.parseColor("#9E9E9E"))
                binding.btnSubmitRequest.isEnabled = false
            }
        }
    }

    private fun setupObservers() {
        // Observe currentUser StateFlow
        lifecycleScope.launch {
            authViewModel.currentUser.collect { user ->
                // Skip login check for emergency mode
                if (!isEmergencyMode && user == null) {
                    finish()
                } else if (!isEmergencyMode && user != null) {
                    // Check for active request when user is loaded
                    checkForActiveRequest(user)
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

    private fun checkForActiveRequest(user: UserInfo) {
        lifecycleScope.launch {
            try {
                val activeRequest = ServiceLocator.requestRepository.getActiveRequest(user.id)
                if (activeRequest != null) {
                    Toast.makeText(
                        this@RequestActivity,
                        "You already have an active request!",
                        Toast.LENGTH_LONG
                    ).show()

                    when (activeRequest.status) {
                        "accepted" -> {
                            navigateToPatientTracking(activeRequest)
                        }
                        "pending" -> {
                            navigateToRequestDetail(activeRequest)
                        }
                        else -> {
                            // For other statuses, allow new request
                        }
                    }
                }
            } catch (e: Exception) {
                // No active request found, allow new request creation
                e.printStackTrace()
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnSubmitRequest.setOnClickListener {
            checkAndSubmitRequest()
        }

        // Add click listener for location field
        binding.etLocation.setOnClickListener {
            checkLocationPermissionAndOpenMap()
        }

        // Add a button next to location field for map selection
        binding.etLocation.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_map_marker, 0)
        binding.etLocation.setOnTouchListener { v, event ->
            val drawableRight = 2
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                if (event.rawX >= (binding.etLocation.right - binding.etLocation.compoundDrawables[drawableRight].bounds.width())) {
                    checkLocationPermissionAndOpenMap()
                    return@setOnTouchListener true
                }
            }
            false
        }

        val tvError = findViewById<android.widget.TextView>(com.example.ambuplus.R.id.tvError)
        tvError?.setOnClickListener {
            requestViewModel.clearError()
        }
    }

    private fun checkLocationPermissionAndOpenMap() {
        if (checkLocationPermission()) {
            openMapLocationPicker()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openMapLocationPicker() {
        val intent = Intent(this, MapLocationPickerActivity::class.java)
        mapPickerLauncher.launch(intent)
    }

    private fun checkAndSubmitRequest() {
        val emergencyLevel = when (binding.rgEmergencyLevel.checkedRadioButtonId) {
            com.example.ambuplus.R.id.rbHigh -> "high"
            com.example.ambuplus.R.id.rbMedium -> "medium"
            com.example.ambuplus.R.id.rbLow -> "low"
            else -> "medium"
        }

        if (emergencyLevel == "high") {
            AlertDialog.Builder(this)
                .setTitle("🚨 Genuine Emergency Confirmation")
                .setMessage("HIGH emergency priority is for LIFE-THREATENING situations only:\n\n" +
                        "✓ Severe chest pain\n" +
                        "✓ Difficulty breathing\n" +
                        "✓ Uncontrolled bleeding\n" +
                        "✓ Loss of consciousness\n" +
                        "✓ Stroke/seizure symptoms\n\n" +
                        "⚠️ Misuse of high priority may delay help for real emergencies.\n\n" +
                        "Is this a genuine life-threatening emergency?")
                .setPositiveButton("Yes, it's an emergency") { _, _ ->
                    submitRequest()
                }
                .setNegativeButton("No, lower priority") { _, _ ->
                    binding.rbMedium.isChecked = true
                    submitRequest()
                }
                .show()
        } else {
            submitRequest()
        }
    }

    private fun submitRequest() {
        // Emergency mode check
        if (isEmergencyMode) {
            val usedCount = getEmergencyCount()

            // FIX: Check if already at limit (>= MAX, not >)
            if (usedCount >= MAX_EMERGENCY_REQUESTS) {
                AlertDialog.Builder(this)
                    .setTitle("Emergency Limit Reached")
                    .setMessage("You have used all $MAX_EMERGENCY_REQUESTS emergency requests. Please sign up to continue using AmbuPlus.")
                    .setPositiveButton("Sign Up") { _, _ ->
                        val intent = Intent(this, com.example.ambuplus.uiactivities.login.LoginActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return
            }
        }

        val currentUser = authViewModel.currentUser.value
        if (currentUser == null && !isEmergencyMode) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            return
        }

        // Check for active request before submitting new one
        lifecycleScope.launch {
            try {
                if (!isEmergencyMode && currentUser != null) {
                    val activeRequest = ServiceLocator.requestRepository.getActiveRequest(currentUser.id)
                    if (activeRequest != null) {
                        Toast.makeText(
                            this@RequestActivity,
                            "You already have an active request! Cannot create another.",
                            Toast.LENGTH_LONG
                        ).show()

                        when (activeRequest.status) {
                            "accepted" -> navigateToPatientTracking(activeRequest)
                            "pending" -> navigateToRequestDetail(activeRequest)
                        }
                        return@launch
                    }
                }

                // No active request, proceed with creation
                if (isEmergencyMode) {
                    proceedWithEmergencyRequestCreation()
                } else if (currentUser != null) {
                    proceedWithRequestCreation(currentUser)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (isEmergencyMode) {
                    proceedWithEmergencyRequestCreation()
                } else if (currentUser != null) {
                    proceedWithRequestCreation(currentUser)
                }
            }
        }
    }

    // Emergency request creation without login
    private fun proceedWithEmergencyRequestCreation() {
        val patientName = binding.etPatientName.text.toString().trim()
        val patientAgeText = binding.etPatientAge.text.toString().trim()
        val contactNumber = binding.etContactNumber.text.toString().trim()
        val location = binding.etLocation.text.toString().trim()
        val medicalNotes = binding.etMedicalNotes.text.toString().trim()

        val emergencyLevel = when (binding.rgEmergencyLevel.checkedRadioButtonId) {
            com.example.ambuplus.R.id.rbHigh -> "high"
            com.example.ambuplus.R.id.rbMedium -> "medium"
            com.example.ambuplus.R.id.rbLow -> "low"
            else -> "medium"
        }

        if (validateInputs(patientName, patientAgeText, contactNumber, location)) {
            val patientAge = if (patientAgeText.isNotEmpty()) patientAgeText.toInt() else null

            binding.btnSubmitRequest.isEnabled = false
            val progressBar = findViewById<android.widget.ProgressBar>(com.example.ambuplus.R.id.progressBar)
            progressBar?.visibility = android.view.View.VISIBLE

            lifecycleScope.launch {
                try {
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                    val locationWithCoords = if (selectedLat != null && selectedLng != null && selectedLat != 0.0 && selectedLng != 0.0) {
                        "$location|${selectedLat},${selectedLng}"
                    } else {
                        location
                    }

                    val request = com.example.ambuplus.models.Request(
                        userId = EMERGENCY_USER_ID,
                        location = locationWithCoords,
                        emergencyLevel = emergencyLevel,
                        contactNumber = contactNumber,
                        timestamp = timestamp,
                        patientName = patientName,
                        patientAge = patientAge,
                        medicalNotes = medicalNotes,
                        status = "pending"
                    )

                    ServiceLocator.requestRepository.addRequest(request)

                    // Get the prefs instance
                    val prefs = getSharedPreferences("EmergencyPrefs", MODE_PRIVATE)
                    val sessionId = getSessionId()

                    // Wait a moment for the database to process
                    kotlinx.coroutines.delay(500)

                    // Fetch the newly created request
                    val allRequests = ServiceLocator.requestRepository.getAllRequests()
                    val newRequest = allRequests.findLast {
                        it.userId == EMERGENCY_USER_ID &&
                                it.patientName == patientName &&
                                it.timestamp.contains(timestamp.substring(0, 10))
                    }

                    if (newRequest != null && newRequest.id != null) {
                        // Increment emergency counter AFTER successful request
                        val usedCount = prefs.getInt("emergency_count_$sessionId", 0)
                        val newCount = usedCount + 1
                        prefs.edit().putInt("emergency_count_$sessionId", newCount).apply()

                        // Save the request ID for persistence
                        prefs.edit().putInt("emergency_active_request_id", newRequest.id).apply()

                        // Update the banner display
                        updateEmergencyCountDisplay()

                        val intent = Intent(this@RequestActivity, RequestDetailActivity::class.java).apply {
                            putExtra("patient_name", patientName)
                            putExtra("contact_number", contactNumber)
                            putExtra("location", location)
                            putExtra("emergency_level", emergencyLevel)
                            putExtra("medical_notes", medicalNotes)
                            putExtra("request_id", newRequest.id)
                            putExtra("selected_lat", selectedLat ?: 0.0)
                            putExtra("selected_lng", selectedLng ?: 0.0)
                            patientAge?.let { putExtra("patient_age", it) }
                            putExtra("is_emergency_mode", true)
                        }

                        Toast.makeText(this@RequestActivity, "🚨 Emergency request submitted successfully! Used ${newCount}/${MAX_EMERGENCY_REQUESTS}", Toast.LENGTH_LONG).show()
                        startActivity(intent)
                        finish()
                    } else {
                        // Request was created but we couldn't find it - still proceed
                        Toast.makeText(this@RequestActivity, "🚨 Emergency request submitted!", Toast.LENGTH_SHORT).show()
                        finish()
                    }

                } catch (e: Exception) {
                    Toast.makeText(this@RequestActivity, "Failed to create emergency request: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    binding.btnSubmitRequest.isEnabled = true
                    progressBar?.visibility = android.view.View.GONE
                }
            }
        }
    }

    private fun proceedWithRequestCreation(currentUser: UserInfo) {
        val patientName = binding.etPatientName.text.toString().trim()
        val patientAgeText = binding.etPatientAge.text.toString().trim()
        val contactNumber = binding.etContactNumber.text.toString().trim()
        val location = binding.etLocation.text.toString().trim()
        val medicalNotes = binding.etMedicalNotes.text.toString().trim()

        val emergencyLevel = when (binding.rgEmergencyLevel.checkedRadioButtonId) {
            com.example.ambuplus.R.id.rbHigh -> "high"
            com.example.ambuplus.R.id.rbMedium -> "medium"
            com.example.ambuplus.R.id.rbLow -> "low"
            else -> "medium"
        }

        if (validateInputs(patientName, patientAgeText, contactNumber, location)) {
            val patientAge = if (patientAgeText.isNotEmpty()) patientAgeText.toInt() else null

            binding.btnSubmitRequest.isEnabled = false
            val progressBar = findViewById<android.widget.ProgressBar>(com.example.ambuplus.R.id.progressBar)
            progressBar?.visibility = android.view.View.VISIBLE

            lifecycleScope.launch {
                try {
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                    val locationWithCoords = if (selectedLat != null && selectedLng != null && selectedLat != 0.0 && selectedLng != 0.0) {
                        "$location|${selectedLat},${selectedLng}"
                    } else {
                        location
                    }

                    val request = com.example.ambuplus.models.Request(
                        userId = currentUser.id,
                        location = locationWithCoords,
                        emergencyLevel = emergencyLevel,
                        contactNumber = contactNumber,
                        timestamp = timestamp,
                        patientName = patientName,
                        patientAge = patientAge,
                        medicalNotes = medicalNotes,
                        status = "pending"
                    )

                    ServiceLocator.requestRepository.addRequest(request)

                    val allRequests = ServiceLocator.requestRepository.getAllRequests()
                    val newRequest = allRequests.findLast {
                        it.userId == currentUser.id && it.timestamp.contains(timestamp.substring(0, 10))
                    }

                    if (newRequest != null && newRequest.id != null) {
                        val intent = Intent(this@RequestActivity, RequestDetailActivity::class.java).apply {
                            putExtra("patient_name", patientName)
                            putExtra("contact_number", contactNumber)
                            putExtra("location", location)
                            putExtra("emergency_level", emergencyLevel)
                            putExtra("medical_notes", medicalNotes)
                            putExtra("request_id", newRequest.id!!)
                            putExtra("selected_lat", selectedLat ?: 0.0)
                            putExtra("selected_lng", selectedLng ?: 0.0)
                            patientAge?.let { putExtra("patient_age", it) }
                        }

                        Toast.makeText(this@RequestActivity, "Ambulance request submitted successfully!", Toast.LENGTH_SHORT).show()
                        startActivity(intent)
                    } else {
                        val intent = Intent(this@RequestActivity, RequestDetailActivity::class.java).apply {
                            putExtra("patient_name", patientName)
                            putExtra("contact_number", contactNumber)
                            putExtra("location", location)
                            putExtra("emergency_level", emergencyLevel)
                            putExtra("medical_notes", medicalNotes)
                            putExtra("request_id", -1)
                            putExtra("selected_lat", selectedLat ?: 0.0)
                            putExtra("selected_lng", selectedLng ?: 0.0)
                            patientAge?.let { putExtra("patient_age", it) }
                        }

                        Toast.makeText(this@RequestActivity, "Request submitted!", Toast.LENGTH_SHORT).show()
                        startActivity(intent)
                    }

                } catch (e: Exception) {
                    Toast.makeText(this@RequestActivity, "Failed to create request: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    binding.btnSubmitRequest.isEnabled = true
                    progressBar?.visibility = android.view.View.GONE
                }
            }
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
            showError("Please select pickup location")
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






//package com.example.ambuplus.uiactivities.request
//
//import android.Manifest
//import android.app.Activity
//import android.app.AlertDialog
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.os.Bundle
//import android.widget.Toast
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.appcompat.app.AppCompatActivity
//import com.example.ambuplus.R
//import androidx.core.app.ActivityCompat
//import androidx.core.content.ContextCompat
//import androidx.lifecycle.ViewModelProvider
//import androidx.lifecycle.lifecycleScope
//import com.example.ambuplus.databinding.ActivityRequestBinding
//import com.example.ambuplus.models.AuthViewModel
//import com.example.ambuplus.models.RequestViewModel
//import com.example.ambuplus.utils.ServiceLocator
//import io.github.jan.supabase.gotrue.user.UserInfo
//import kotlinx.coroutines.launch
//import java.text.SimpleDateFormat
//import java.util.Date
//import java.util.Locale
//import java.util.UUID
//
//class RequestActivity : AppCompatActivity() {
//
//    private lateinit var binding: ActivityRequestBinding
//    private lateinit var authViewModel: AuthViewModel
//    private lateinit var requestViewModel: RequestViewModel
//
//    private var selectedLat: Double? = null
//    private var selectedLng: Double? = null
//    private var selectedAddress: String = ""
//
//    // ADDED: Emergency mode variables
//    private var isEmergencyMode = false
//    private val MAX_EMERGENCY_REQUESTS = 3
//
//    // Register for activity result
//    private val mapPickerLauncher = registerForActivityResult(
//        ActivityResultContracts.StartActivityForResult()
//    ) { result ->
//        if (result.resultCode == Activity.RESULT_OK) {
//            val data: Intent? = result.data
//            selectedLat = data?.getDoubleExtra("latitude", 0.0) ?: 0.0
//            selectedLng = data?.getDoubleExtra("longitude", 0.0) ?: 0.0
//            selectedAddress = data?.getStringExtra(MapLocationPickerActivity.EXTRA_SELECTED_ADDRESS) ?: ""
//
//            if (selectedLat != 0.0 && selectedLng != 0.0) {
//                binding.etLocation.setText(selectedAddress)
//            }
//        }
//    }
//
//    // Location permission launcher
//    private val requestPermissionLauncher = registerForActivityResult(
//        ActivityResultContracts.RequestMultiplePermissions()
//    ) { permissions ->
//        val allGranted = permissions.entries.all { it.value }
//        if (allGranted) {
//            openMapLocationPicker()
//        } else {
//            Toast.makeText(this, "Location permission required to select pickup location", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding = ActivityRequestBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        // ADDED: Check if this is emergency mode
//        isEmergencyMode = intent.getBooleanExtra("emergency_mode", false)
//
//        if (isEmergencyMode) {
//            // Show emergency banner
//            binding.tvEmergencyBanner.visibility = android.view.View.VISIBLE
//            updateEmergencyCountDisplay()
//            Toast.makeText(this, "🚨 EMERGENCY MODE - No login required", Toast.LENGTH_LONG).show()
//        }
//
//        // Initialize ViewModels with proper factories
//        val authFactory = AuthViewModelFactory(ServiceLocator.authRepository)
//        authViewModel = ViewModelProvider(this, authFactory)[AuthViewModel::class.java]
//
//        val requestFactory = RequestViewModelFactory(ServiceLocator.requestRepository)
//        requestViewModel = ViewModelProvider(this, requestFactory)[RequestViewModel::class.java]
//
//        setupObservers()
//        setupClickListeners()
//    }
//
//    // ADDED: Function to get device session ID
//    private fun getSessionId(): String {
//        val prefs = getSharedPreferences("EmergencyPrefs", MODE_PRIVATE)
//        var sessionId = prefs.getString("emergency_session_id", null)
//        if (sessionId == null) {
//            sessionId = UUID.randomUUID().toString()
//            prefs.edit().putString("emergency_session_id", sessionId).apply()
//        }
//        return sessionId
//    }
//
//    // ADDED: Function to update emergency count display
//    private fun updateEmergencyCountDisplay() {
//        val prefs = getSharedPreferences("EmergencyPrefs", MODE_PRIVATE)
//        val sessionId = getSessionId()
//        val usedCount = prefs.getInt("emergency_count_$sessionId", 0)
//        val remaining = MAX_EMERGENCY_REQUESTS - usedCount
//        binding.tvEmergencyBanner.text = "🚨 EMERGENCY MODE: $remaining request(s) remaining on this device"
//
//        if (remaining <= 0) {
//            binding.tvEmergencyBanner.setBackgroundColor(android.graphics.Color.parseColor("#9E9E9E"))
//        }
//    }
//
//    private fun setupObservers() {
//        // Observe currentUser StateFlow
//        lifecycleScope.launch {
//            authViewModel.currentUser.collect { user ->
//                // MODIFIED: Skip login check for emergency mode
//                if (!isEmergencyMode && user == null) {
//                    finish()
//                } else if (!isEmergencyMode && user != null) {
//                    // Check for active request when user is loaded
//                    checkForActiveRequest(user)
//                }
//            }
//        }
//
//        // Observe isLoading StateFlow
//        lifecycleScope.launch {
//            requestViewModel.isLoading.collect { isLoading ->
//                val progressBar = findViewById<android.widget.ProgressBar>(com.example.ambuplus.R.id.progressBar)
//                progressBar?.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
//                binding.btnSubmitRequest.isEnabled = !isLoading
//            }
//        }
//
//        // Observe errorMessage StateFlow
//        lifecycleScope.launch {
//            requestViewModel.errorMessage.collect { error ->
//                val tvError = findViewById<android.widget.TextView>(com.example.ambuplus.R.id.tvError)
//                if (error != null) {
//                    tvError?.text = error
//                    tvError?.visibility = android.view.View.VISIBLE
//                    Toast.makeText(this@RequestActivity, error, Toast.LENGTH_LONG).show()
//                } else {
//                    tvError?.visibility = android.view.View.GONE
//                }
//            }
//        }
//    }
//
//    private fun checkForActiveRequest(user: UserInfo) {
//        lifecycleScope.launch {
//            try {
//                val activeRequest = ServiceLocator.requestRepository.getActiveRequest(user.id)
//                if (activeRequest != null) {
//                    Toast.makeText(
//                        this@RequestActivity,
//                        "You already have an active request!",
//                        Toast.LENGTH_LONG
//                    ).show()
//
//                    when (activeRequest.status) {
//                        "accepted" -> {
//                            navigateToPatientTracking(activeRequest)
//                        }
//                        "pending" -> {
//                            navigateToRequestDetail(activeRequest)
//                        }
//                        else -> {
//                            // For other statuses, allow new request
//                        }
//                    }
//                }
//            } catch (e: Exception) {
//                // No active request found, allow new request creation
//                e.printStackTrace()
//            }
//        }
//    }
//
//    private fun setupClickListeners() {
//        binding.btnSubmitRequest.setOnClickListener {
//            checkAndSubmitRequest()  // Changed from submitRequest()
//        }
//
//        // Add click listener for location field
//        binding.etLocation.setOnClickListener {
//            checkLocationPermissionAndOpenMap()
//        }
//
//        // Add a button next to location field for map selection
//        binding.etLocation.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_map_marker, 0)
//        binding.etLocation.setOnTouchListener { v, event ->
//            val drawableRight = 2
//            if (event.action == android.view.MotionEvent.ACTION_UP) {
//                if (event.rawX >= (binding.etLocation.right - binding.etLocation.compoundDrawables[drawableRight].bounds.width())) {
//                    checkLocationPermissionAndOpenMap()
//                    return@setOnTouchListener true
//                }
//            }
//            false
//        }
//
//        val tvError = findViewById<android.widget.TextView>(com.example.ambuplus.R.id.tvError)
//        tvError?.setOnClickListener {
//            requestViewModel.clearError()
//        }
//    }
//
//    private fun checkLocationPermissionAndOpenMap() {
//        if (checkLocationPermission()) {
//            openMapLocationPicker()
//        } else {
//            requestPermissionLauncher.launch(
//                arrayOf(
//                    Manifest.permission.ACCESS_FINE_LOCATION,
//                    Manifest.permission.ACCESS_COARSE_LOCATION
//                )
//            )
//        }
//    }
//
//    private fun checkLocationPermission(): Boolean {
//        return ContextCompat.checkSelfPermission(
//            this,
//            Manifest.permission.ACCESS_FINE_LOCATION
//        ) == PackageManager.PERMISSION_GRANTED ||
//                ContextCompat.checkSelfPermission(
//                    this,
//                    Manifest.permission.ACCESS_COARSE_LOCATION
//                ) == PackageManager.PERMISSION_GRANTED
//    }
//
//    private fun openMapLocationPicker() {
//        val intent = Intent(this, MapLocationPickerActivity::class.java)
//        mapPickerLauncher.launch(intent)
//    }
//
//    private fun checkAndSubmitRequest() {
//        val emergencyLevel = when (binding.rgEmergencyLevel.checkedRadioButtonId) {
//            com.example.ambuplus.R.id.rbHigh -> "high"
//            com.example.ambuplus.R.id.rbMedium -> "medium"
//            com.example.ambuplus.R.id.rbLow -> "low"
//            else -> "medium"
//        }
//
//        if (emergencyLevel == "high") {
//            AlertDialog.Builder(this)
//                .setTitle("🚨 Genuine Emergency Confirmation")
//                .setMessage("HIGH emergency priority is for LIFE-THREATENING situations only:\n\n" +
//                        "✓ Severe chest pain\n" +
//                        "✓ Difficulty breathing\n" +
//                        "✓ Uncontrolled bleeding\n" +
//                        "✓ Loss of consciousness\n" +
//                        "✓ Stroke/seizure symptoms\n\n" +
//                        "⚠️ Misuse of high priority may delay help for real emergencies.\n\n" +
//                        "Is this a genuine life-threatening emergency?")
//                .setPositiveButton("Yes, it's an emergency") { _, _ ->
//                    submitRequest()
//                }
//                .setNegativeButton("No, lower priority") { _, _ ->
//                    binding.rbMedium.isChecked = true
//                    submitRequest()
//                }
//                .show()
//        } else {
//            submitRequest()
//        }
//    }
//
//    private fun submitRequest() {
//        // ADDED: Emergency mode check
//        if (isEmergencyMode) {
//            val prefs = getSharedPreferences("EmergencyPrefs", MODE_PRIVATE)
//            val sessionId = getSessionId()
//            val usedCount = prefs.getInt("emergency_count_$sessionId", 0)
//
//            if (usedCount >= MAX_EMERGENCY_REQUESTS) {
//                AlertDialog.Builder(this)
//                    .setTitle("Emergency Limit Reached")
//                    .setMessage("You have used all $MAX_EMERGENCY_REQUESTS emergency requests. Please sign up to continue using AmbuPlus.")
//                    .setPositiveButton("Sign Up") { _, _ ->
//                        val intent = Intent(this, com.example.ambuplus.uiactivities.login.LoginActivity::class.java)
//                        startActivity(intent)
//                        finish()
//                    }
//                    .setNegativeButton("Cancel", null)
//                    .show()
//                return
//            }
//        }
//
//        val currentUser = authViewModel.currentUser.value
//        if (currentUser == null && !isEmergencyMode) {
//            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        // Check for active request before submitting new one
//        lifecycleScope.launch {
//            try {
//                if (!isEmergencyMode && currentUser != null) {
//                    val activeRequest = ServiceLocator.requestRepository.getActiveRequest(currentUser.id)
//                    if (activeRequest != null) {
//                        Toast.makeText(
//                            this@RequestActivity,
//                            "You already have an active request! Cannot create another.",
//                            Toast.LENGTH_LONG
//                        ).show()
//
//                        when (activeRequest.status) {
//                            "accepted" -> navigateToPatientTracking(activeRequest)
//                            "pending" -> navigateToRequestDetail(activeRequest)
//                        }
//                        return@launch
//                    }
//                }
//
//                // No active request, proceed with creation
//                if (isEmergencyMode) {
//                    proceedWithEmergencyRequestCreation()
//                } else if (currentUser != null) {
//                    proceedWithRequestCreation(currentUser)
//                }
//            } catch (e: Exception) {
//                e.printStackTrace()
//                if (isEmergencyMode) {
//                    proceedWithEmergencyRequestCreation()
//                } else if (currentUser != null) {
//                    proceedWithRequestCreation(currentUser)
//                }
//            }
//        }
//    }
//
//    // ADDED: Emergency request creation without login
//    private fun proceedWithEmergencyRequestCreation() {
//        val patientName = binding.etPatientName.text.toString().trim()
//        val patientAgeText = binding.etPatientAge.text.toString().trim()
//        val contactNumber = binding.etContactNumber.text.toString().trim()
//        val location = binding.etLocation.text.toString().trim()
//        val medicalNotes = binding.etMedicalNotes.text.toString().trim()
//
//        val emergencyLevel = when (binding.rgEmergencyLevel.checkedRadioButtonId) {
//            com.example.ambuplus.R.id.rbHigh -> "high"
//            com.example.ambuplus.R.id.rbMedium -> "medium"
//            com.example.ambuplus.R.id.rbLow -> "low"
//            else -> "medium"
//        }
//
//        if (validateInputs(patientName, patientAgeText, contactNumber, location)) {
//            val patientAge = if (patientAgeText.isNotEmpty()) patientAgeText.toInt() else null
//
//            binding.btnSubmitRequest.isEnabled = false
//            val progressBar = findViewById<android.widget.ProgressBar>(com.example.ambuplus.R.id.progressBar)
//            progressBar?.visibility = android.view.View.VISIBLE
//
//            lifecycleScope.launch {
//                try {
//                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
//
//                    val locationWithCoords = if (selectedLat != null && selectedLng != null && selectedLat != 0.0 && selectedLng != 0.0) {
//                        "$location|${selectedLat},${selectedLng}"
//                    } else {
//                        location
//                    }
//
//                    val sessionId = getSessionId()
//
//                    val request = com.example.ambuplus.models.Request(
//                        userId = sessionId,
//                        location = locationWithCoords,
//                        emergencyLevel = emergencyLevel,
//                        contactNumber = contactNumber,
//                        timestamp = timestamp,
//                        patientName = patientName,
//                        patientAge = patientAge,
//                        medicalNotes = medicalNotes,
//                        status = "pending"
//                    )
//
//                    ServiceLocator.requestRepository.addRequest(request)
//
//                    // Increment emergency counter
//                    val prefs = getSharedPreferences("EmergencyPrefs", MODE_PRIVATE)
//                    val usedCount = prefs.getInt("emergency_count_$sessionId", 0)
//                    prefs.edit().putInt("emergency_count_$sessionId", usedCount + 1).apply()
//
//                    val allRequests = ServiceLocator.requestRepository.getAllRequests()
//                    val newRequest = allRequests.findLast {
//                        it.userId == sessionId && it.timestamp.contains(timestamp.substring(0, 10))
//                    }
//
//                    if (newRequest != null && newRequest.id != null) {
//                        val intent = Intent(this@RequestActivity, RequestDetailActivity::class.java).apply {
//                            putExtra("patient_name", patientName)
//                            putExtra("contact_number", contactNumber)
//                            putExtra("location", location)
//                            putExtra("emergency_level", emergencyLevel)
//                            putExtra("medical_notes", medicalNotes)
//                            putExtra("request_id", newRequest.id!!)
//                            putExtra("selected_lat", selectedLat ?: 0.0)
//                            putExtra("selected_lng", selectedLng ?: 0.0)
//                            patientAge?.let { putExtra("patient_age", it) }
//                            putExtra("is_emergency_mode", true)
//                        }
//
//                        Toast.makeText(this@RequestActivity, "🚨 Emergency request submitted successfully!", Toast.LENGTH_SHORT).show()
//                        startActivity(intent)
//                    } else {
//                        val intent = Intent(this@RequestActivity, RequestDetailActivity::class.java).apply {
//                            putExtra("patient_name", patientName)
//                            putExtra("contact_number", contactNumber)
//                            putExtra("location", location)
//                            putExtra("emergency_level", emergencyLevel)
//                            putExtra("medical_notes", medicalNotes)
//                            putExtra("request_id", -1)
//                            putExtra("selected_lat", selectedLat ?: 0.0)
//                            putExtra("selected_lng", selectedLng ?: 0.0)
//                            patientAge?.let { putExtra("patient_age", it) }
//                            putExtra("is_emergency_mode", true)
//                        }
//
//                        Toast.makeText(this@RequestActivity, "🚨 Emergency request submitted!", Toast.LENGTH_SHORT).show()
//                        startActivity(intent)
//                    }
//
//                } catch (e: Exception) {
//                    Toast.makeText(this@RequestActivity, "Failed to create emergency request: ${e.message}", Toast.LENGTH_LONG).show()
//                } finally {
//                    binding.btnSubmitRequest.isEnabled = true
//                    progressBar?.visibility = android.view.View.GONE
//                }
//            }
//        }
//    }
//
//    private fun proceedWithRequestCreation(currentUser: UserInfo) {
//        val patientName = binding.etPatientName.text.toString().trim()
//        val patientAgeText = binding.etPatientAge.text.toString().trim()
//        val contactNumber = binding.etContactNumber.text.toString().trim()
//        val location = binding.etLocation.text.toString().trim()
//        val medicalNotes = binding.etMedicalNotes.text.toString().trim()
//
//        // Get emergency level using RadioGroup
//        val emergencyLevel = when (binding.rgEmergencyLevel.checkedRadioButtonId) {
//            com.example.ambuplus.R.id.rbHigh -> "high"
//            com.example.ambuplus.R.id.rbMedium -> "medium"
//            com.example.ambuplus.R.id.rbLow -> "low"
//            else -> "medium"
//        }
//
//        if (validateInputs(patientName, patientAgeText, contactNumber, location)) {
//            val patientAge = if (patientAgeText.isNotEmpty()) patientAgeText.toInt() else null
//
//            // Show loading
//            binding.btnSubmitRequest.isEnabled = false
//            val progressBar = findViewById<android.widget.ProgressBar>(com.example.ambuplus.R.id.progressBar)
//            progressBar?.visibility = android.view.View.VISIBLE
//
//            lifecycleScope.launch {
//                try {
//                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
//
//                    // Include coordinates if available
//                    val locationWithCoords = if (selectedLat != null && selectedLng != null && selectedLat != 0.0 && selectedLng != 0.0) {
//                        "$location|${selectedLat},${selectedLng}"
//                    } else {
//                        location
//                    }
//
//                    // Create the request object
//                    val request = com.example.ambuplus.models.Request(
//                        userId = currentUser.id,
//                        location = locationWithCoords,
//                        emergencyLevel = emergencyLevel,
//                        contactNumber = contactNumber,
//                        timestamp = timestamp,
//                        patientName = patientName,
//                        patientAge = patientAge,
//                        medicalNotes = medicalNotes,
//                        status = "pending"
//                    )
//
//                    // Save to database
//                    ServiceLocator.requestRepository.addRequest(request)
//
//                    // Get the latest requests to find our new request
//                    val allRequests = ServiceLocator.requestRepository.getAllRequests()
//                    val newRequest = allRequests.findLast {
//                        it.userId == currentUser.id &&
//                                it.timestamp.contains(timestamp.substring(0, 10))
//                    }
//
//                    if (newRequest != null && newRequest.id != null) {
//                        val intent = Intent(this@RequestActivity, RequestDetailActivity::class.java).apply {
//                            putExtra("patient_name", patientName)
//                            putExtra("contact_number", contactNumber)
//                            putExtra("location", location)
//                            putExtra("emergency_level", emergencyLevel)
//                            putExtra("medical_notes", medicalNotes)
//                            putExtra("request_id", newRequest.id!!)
//                            putExtra("selected_lat", selectedLat ?: 0.0)
//                            putExtra("selected_lng", selectedLng ?: 0.0)
//                            patientAge?.let { putExtra("patient_age", it) }
//                        }
//
//                        Toast.makeText(this@RequestActivity, "Ambulance request submitted successfully!", Toast.LENGTH_SHORT).show()
//                        startActivity(intent)
//                    } else {
//                        val intent = Intent(this@RequestActivity, RequestDetailActivity::class.java).apply {
//                            putExtra("patient_name", patientName)
//                            putExtra("contact_number", contactNumber)
//                            putExtra("location", location)
//                            putExtra("emergency_level", emergencyLevel)
//                            putExtra("medical_notes", medicalNotes)
//                            putExtra("request_id", -1)
//                            putExtra("selected_lat", selectedLat ?: 0.0)
//                            putExtra("selected_lng", selectedLng ?: 0.0)
//                            patientAge?.let { putExtra("patient_age", it) }
//                        }
//
//                        Toast.makeText(this@RequestActivity, "Request submitted!", Toast.LENGTH_SHORT).show()
//                        startActivity(intent)
//                    }
//
//                } catch (e: Exception) {
//                    Toast.makeText(this@RequestActivity, "Failed to create request: ${e.message}", Toast.LENGTH_LONG).show()
//                } finally {
//                    binding.btnSubmitRequest.isEnabled = true
//                    progressBar?.visibility = android.view.View.GONE
//                }
//            }
//        }
//    }
//
//    private fun validateInputs(
//        patientName: String,
//        patientAge: String,
//        contactNumber: String,
//        location: String
//    ): Boolean {
//        if (patientName.isEmpty()) {
//            showError("Please enter patient name")
//            return false
//        }
//
//        if (contactNumber.isEmpty()) {
//            showError("Please enter contact number")
//            return false
//        }
//
//        if (location.isEmpty()) {
//            showError("Please select pickup location")
//            return false
//        }
//
//        if (patientAge.isNotEmpty()) {
//            try {
//                val age = patientAge.toInt()
//                if (age <= 0 || age > 150) {
//                    showError("Please enter a valid age")
//                    return false
//                }
//            } catch (e: NumberFormatException) {
//                showError("Please enter a valid age")
//                return false
//            }
//        }
//
//        return true
//    }
//
//    private fun showError(message: String) {
//        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
//    }
//
//    private fun navigateToRequestDetail(request: com.example.ambuplus.models.Request) {
//        val intent = Intent(this, RequestDetailActivity::class.java).apply {
//            putExtra("patient_name", request.patientName ?: "")
//            putExtra("contact_number", request.contactNumber)
//            putExtra("location", request.location.split("|").firstOrNull() ?: request.location)
//            putExtra("emergency_level", request.emergencyLevel)
//            putExtra("medical_notes", request.medicalNotes ?: "")
//            putExtra("request_id", request.id ?: -1)
//
//            val coords = extractCoordinates(request.location)
//            putExtra("selected_lat", coords?.first ?: 0.0)
//            putExtra("selected_lng", coords?.second ?: 0.0)
//            request.patientAge?.let { putExtra("patient_age", it) }
//        }
//        startActivity(intent)
//        finish()
//    }
//
//    private fun navigateToPatientTracking(request: com.example.ambuplus.models.Request) {
//        val coords = extractCoordinates(request.location)
//        val intent = Intent(this, com.example.ambuplus.uiactivities.patient.PatientTrackingActivity::class.java).apply {
//            putExtra("request_id", request.id ?: -1)
//            putExtra("pickup_lat", coords?.first ?: 0.0)
//            putExtra("pickup_lng", coords?.second ?: 0.0)
//            putExtra("patient_name", request.patientName ?: "Patient")
//            putExtra("driver_id", request.driverId ?: "")
//        }
//        startActivity(intent)
//        finish()
//    }
//
//    private fun extractCoordinates(location: String): Pair<Double, Double>? {
//        return try {
//            if (location.contains("|")) {
//                val parts = location.split("|")
//                if (parts.size == 2) {
//                    val coords = parts[1].split(",")
//                    if (coords.size == 2) {
//                        Pair(coords[0].toDouble(), coords[1].toDouble())
//                    } else null
//                } else null
//            } else null
//        } catch (e: Exception) {
//            null
//        }
//    }
//}
//
//// Factory classes
//class AuthViewModelFactory(private val authRepository: com.example.ambuplus.data.AuthRepository) : ViewModelProvider.Factory {
//    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
//        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
//            @Suppress("UNCHECKED_CAST")
//            return AuthViewModel(authRepository) as T
//        }
//        throw IllegalArgumentException("Unknown ViewModel class")
//    }
//}
//
//class RequestViewModelFactory(private val requestRepository: com.example.ambuplus.data.RequestRepository) : ViewModelProvider.Factory {
//    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
//        if (modelClass.isAssignableFrom(RequestViewModel::class.java)) {
//            @Suppress("UNCHECKED_CAST")
//            return RequestViewModel(requestRepository) as T
//        }
//        throw IllegalArgumentException("Unknown ViewModel class")
//    }
//}





//package com.example.ambuplus.uiactivities.request
//
//import android.Manifest
//import android.app.Activity
//import android.app.AlertDialog
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.os.Bundle
//import android.widget.Toast
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.appcompat.app.AppCompatActivity
//import com.example.ambuplus.R
//import androidx.core.app.ActivityCompat
//import androidx.core.content.ContextCompat
//import androidx.lifecycle.ViewModelProvider
//import androidx.lifecycle.lifecycleScope
//import com.example.ambuplus.databinding.ActivityRequestBinding
//import com.example.ambuplus.models.AuthViewModel
//import com.example.ambuplus.models.RequestViewModel
//import com.example.ambuplus.utils.ServiceLocator
//import io.github.jan.supabase.gotrue.user.UserInfo
//import kotlinx.coroutines.launch
//import java.text.SimpleDateFormat
//import java.util.Date
//import java.util.Locale
//
//class RequestActivity : AppCompatActivity() {
//
//    private lateinit var binding: ActivityRequestBinding
//    private lateinit var authViewModel: AuthViewModel
//    private lateinit var requestViewModel: RequestViewModel
//
//    private var selectedLat: Double? = null
//    private var selectedLng: Double? = null
//    private var selectedAddress: String = ""
//
//    // Register for activity result
//    private val mapPickerLauncher = registerForActivityResult(
//        ActivityResultContracts.StartActivityForResult()
//    ) { result ->
//        if (result.resultCode == Activity.RESULT_OK) {
//            val data: Intent? = result.data
//            selectedLat = data?.getDoubleExtra("latitude", 0.0) ?: 0.0
//            selectedLng = data?.getDoubleExtra("longitude", 0.0) ?: 0.0
//            selectedAddress = data?.getStringExtra(MapLocationPickerActivity.EXTRA_SELECTED_ADDRESS) ?: ""
//
//            if (selectedLat != 0.0 && selectedLng != 0.0) {
//                binding.etLocation.setText(selectedAddress)
//            }
//        }
//    }
//
//    // Location permission launcher
//    private val requestPermissionLauncher = registerForActivityResult(
//        ActivityResultContracts.RequestMultiplePermissions()
//    ) { permissions ->
//        val allGranted = permissions.entries.all { it.value }
//        if (allGranted) {
//            openMapLocationPicker()
//        } else {
//            Toast.makeText(this, "Location permission required to select pickup location", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding = ActivityRequestBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        // Initialize ViewModels with proper factories
//        val authFactory = AuthViewModelFactory(ServiceLocator.authRepository)
//        authViewModel = ViewModelProvider(this, authFactory)[AuthViewModel::class.java]
//
//        val requestFactory = RequestViewModelFactory(ServiceLocator.requestRepository)
//        requestViewModel = ViewModelProvider(this, requestFactory)[RequestViewModel::class.java]
//
//        setupObservers()
//        setupClickListeners()
//    }
//
//    private fun setupObservers() {
//        // Observe currentUser StateFlow
//        lifecycleScope.launch {
//            authViewModel.currentUser.collect { user ->
//                if (user == null) {
//                    finish()
//                } else {
//                    // Check for active request when user is loaded
//                    checkForActiveRequest(user)
//                }
//            }
//        }
//
//        // Observe isLoading StateFlow
//        lifecycleScope.launch {
//            requestViewModel.isLoading.collect { isLoading ->
//                val progressBar = findViewById<android.widget.ProgressBar>(com.example.ambuplus.R.id.progressBar)
//                progressBar?.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
//                binding.btnSubmitRequest.isEnabled = !isLoading
//            }
//        }
//
//        // Observe errorMessage StateFlow
//        lifecycleScope.launch {
//            requestViewModel.errorMessage.collect { error ->
//                val tvError = findViewById<android.widget.TextView>(com.example.ambuplus.R.id.tvError)
//                if (error != null) {
//                    tvError?.text = error
//                    tvError?.visibility = android.view.View.VISIBLE
//                    Toast.makeText(this@RequestActivity, error, Toast.LENGTH_LONG).show()
//                } else {
//                    tvError?.visibility = android.view.View.GONE
//                }
//            }
//        }
//    }
//
//    private fun checkForActiveRequest(user: UserInfo) {
//        lifecycleScope.launch {
//            try {
//                val activeRequest = ServiceLocator.requestRepository.getActiveRequest(user.id)
//                if (activeRequest != null) {
//                    Toast.makeText(
//                        this@RequestActivity,
//                        "You already have an active request!",
//                        Toast.LENGTH_LONG
//                    ).show()
//
//                    when (activeRequest.status) {
//                        "accepted" -> {
//                            navigateToPatientTracking(activeRequest)
//                        }
//                        "pending" -> {
//                            navigateToRequestDetail(activeRequest)
//                        }
//                        else -> {
//                            // For other statuses, allow new request
//                        }
//                    }
//                }
//            } catch (e: Exception) {
//                // No active request found, allow new request creation
//                e.printStackTrace()
//            }
//        }
//    }
//
//    private fun setupClickListeners() {
//        binding.btnSubmitRequest.setOnClickListener {
//            checkAndSubmitRequest()  // Changed from submitRequest()
//        }
//
//        // Add click listener for location field
//        binding.etLocation.setOnClickListener {
//            checkLocationPermissionAndOpenMap()
//        }
//
//        // Add a button next to location field for map selection
//        binding.etLocation.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_map_marker, 0)
//        binding.etLocation.setOnTouchListener { v, event ->
//            val drawableRight = 2
//            if (event.action == android.view.MotionEvent.ACTION_UP) {
//                if (event.rawX >= (binding.etLocation.right - binding.etLocation.compoundDrawables[drawableRight].bounds.width())) {
//                    checkLocationPermissionAndOpenMap()
//                    return@setOnTouchListener true
//                }
//            }
//            false
//        }
//
//        val tvError = findViewById<android.widget.TextView>(com.example.ambuplus.R.id.tvError)
//        tvError?.setOnClickListener {
//            requestViewModel.clearError()
//        }
//    }
//
//    private fun checkLocationPermissionAndOpenMap() {
//        if (checkLocationPermission()) {
//            openMapLocationPicker()
//        } else {
//            requestPermissionLauncher.launch(
//                arrayOf(
//                    Manifest.permission.ACCESS_FINE_LOCATION,
//                    Manifest.permission.ACCESS_COARSE_LOCATION
//                )
//            )
//        }
//    }
//
//    private fun checkLocationPermission(): Boolean {
//        return ContextCompat.checkSelfPermission(
//            this,
//            Manifest.permission.ACCESS_FINE_LOCATION
//        ) == PackageManager.PERMISSION_GRANTED ||
//                ContextCompat.checkSelfPermission(
//                    this,
//                    Manifest.permission.ACCESS_COARSE_LOCATION
//                ) == PackageManager.PERMISSION_GRANTED
//    }
//
//    private fun openMapLocationPicker() {
//        val intent = Intent(this, MapLocationPickerActivity::class.java)
//        mapPickerLauncher.launch(intent)
//    }
//
//    private fun checkAndSubmitRequest() {
//        val emergencyLevel = when (binding.rgEmergencyLevel.checkedRadioButtonId) {
//            com.example.ambuplus.R.id.rbHigh -> "high"
//            com.example.ambuplus.R.id.rbMedium -> "medium"
//            com.example.ambuplus.R.id.rbLow -> "low"
//            else -> "medium"
//        }
//
//        if (emergencyLevel == "high") {
//            AlertDialog.Builder(this)
//                .setTitle("🚨 Genuine Emergency Confirmation")
//                .setMessage("HIGH emergency priority is for LIFE-THREATENING situations only:\n\n" +
//                        "✓ Severe chest pain\n" +
//                        "✓ Difficulty breathing\n" +
//                        "✓ Uncontrolled bleeding\n" +
//                        "✓ Loss of consciousness\n" +
//                        "✓ Stroke/seizure symptoms\n\n" +
//                        "⚠️ Misuse of high priority may delay help for real emergencies.\n\n" +
//                        "Is this a genuine life-threatening emergency?")
//                .setPositiveButton("Yes, it's an emergency") { _, _ ->
//                    submitRequest()
//                }
//                .setNegativeButton("No, lower priority") { _, _ ->
//                    binding.rbMedium.isChecked = true
//                    submitRequest()
//                }
//                .show()
//        } else {
//            submitRequest()
//        }
//    }
//
//    private fun submitRequest() {
//        val currentUser = authViewModel.currentUser.value
//        if (currentUser == null) {
//            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        // Check for active request before submitting new one
//        lifecycleScope.launch {
//            try {
//                val activeRequest = ServiceLocator.requestRepository.getActiveRequest(currentUser.id)
//                if (activeRequest != null) {
//                    Toast.makeText(
//                        this@RequestActivity,
//                        "You already have an active request! Cannot create another.",
//                        Toast.LENGTH_LONG
//                    ).show()
//
//                    when (activeRequest.status) {
//                        "accepted" -> navigateToPatientTracking(activeRequest)
//                        "pending" -> navigateToRequestDetail(activeRequest)
//                    }
//                    return@launch
//                }
//
//                // No active request, proceed with creation
//                proceedWithRequestCreation(currentUser)
//            } catch (e: Exception) {
//                e.printStackTrace()
//                // If error checking active request, proceed with caution
//                proceedWithRequestCreation(currentUser)
//            }
//        }
//    }
//
//    private fun proceedWithRequestCreation(currentUser: UserInfo) {
//        val patientName = binding.etPatientName.text.toString().trim()
//        val patientAgeText = binding.etPatientAge.text.toString().trim()
//        val contactNumber = binding.etContactNumber.text.toString().trim()
//        val location = binding.etLocation.text.toString().trim()
//        val medicalNotes = binding.etMedicalNotes.text.toString().trim()
//
//        // Get emergency level using RadioGroup
//        val emergencyLevel = when (binding.rgEmergencyLevel.checkedRadioButtonId) {
//            com.example.ambuplus.R.id.rbHigh -> "high"
//            com.example.ambuplus.R.id.rbMedium -> "medium"
//            com.example.ambuplus.R.id.rbLow -> "low"
//            else -> "medium"
//        }
//
//        if (validateInputs(patientName, patientAgeText, contactNumber, location)) {
//            val patientAge = if (patientAgeText.isNotEmpty()) patientAgeText.toInt() else null
//
//            // Show loading
//            binding.btnSubmitRequest.isEnabled = false
//            val progressBar = findViewById<android.widget.ProgressBar>(com.example.ambuplus.R.id.progressBar)
//            progressBar?.visibility = android.view.View.VISIBLE
//
//            lifecycleScope.launch {
//                try {
//                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
//
//                    // Include coordinates if available
//                    val locationWithCoords = if (selectedLat != null && selectedLng != null && selectedLat != 0.0 && selectedLng != 0.0) {
//                        "$location|${selectedLat},${selectedLng}"
//                    } else {
//                        location
//                    }
//
//                    // Create the request object
//                    val request = com.example.ambuplus.models.Request(
//                        userId = currentUser.id,
//                        location = locationWithCoords,
//                        emergencyLevel = emergencyLevel,
//                        contactNumber = contactNumber,
//                        timestamp = timestamp,
//                        patientName = patientName,
//                        patientAge = patientAge,
//                        medicalNotes = medicalNotes,
//                        status = "pending"
//                    )
//
//                    // Save to database
//                    ServiceLocator.requestRepository.addRequest(request)
//
//                    // Get the latest requests to find our new request
//                    val allRequests = ServiceLocator.requestRepository.getAllRequests()
//                    val newRequest = allRequests.findLast {
//                        it.userId == currentUser.id &&
//                                it.timestamp.contains(timestamp.substring(0, 10))
//                    }
//
//                    if (newRequest != null && newRequest.id != null) {
//                        val intent = Intent(this@RequestActivity, RequestDetailActivity::class.java).apply {
//                            putExtra("patient_name", patientName)
//                            putExtra("contact_number", contactNumber)
//                            putExtra("location", location)
//                            putExtra("emergency_level", emergencyLevel)
//                            putExtra("medical_notes", medicalNotes)
//                            putExtra("request_id", newRequest.id!!)
//                            putExtra("selected_lat", selectedLat ?: 0.0)
//                            putExtra("selected_lng", selectedLng ?: 0.0)
//                            patientAge?.let { putExtra("patient_age", it) }
//                        }
//
//                        Toast.makeText(this@RequestActivity, "Ambulance request submitted successfully!", Toast.LENGTH_SHORT).show()
//                        startActivity(intent)
//                    } else {
//                        val intent = Intent(this@RequestActivity, RequestDetailActivity::class.java).apply {
//                            putExtra("patient_name", patientName)
//                            putExtra("contact_number", contactNumber)
//                            putExtra("location", location)
//                            putExtra("emergency_level", emergencyLevel)
//                            putExtra("medical_notes", medicalNotes)
//                            putExtra("request_id", -1)
//                            putExtra("selected_lat", selectedLat ?: 0.0)
//                            putExtra("selected_lng", selectedLng ?: 0.0)
//                            patientAge?.let { putExtra("patient_age", it) }
//                        }
//
//                        Toast.makeText(this@RequestActivity, "Request submitted!", Toast.LENGTH_SHORT).show()
//                        startActivity(intent)
//                    }
//
//                } catch (e: Exception) {
//                    Toast.makeText(this@RequestActivity, "Failed to create request: ${e.message}", Toast.LENGTH_LONG).show()
//                } finally {
//                    binding.btnSubmitRequest.isEnabled = true
//                    progressBar?.visibility = android.view.View.GONE
//                }
//            }
//        }
//    }
//
//    private fun validateInputs(
//        patientName: String,
//        patientAge: String,
//        contactNumber: String,
//        location: String
//    ): Boolean {
//        if (patientName.isEmpty()) {
//            showError("Please enter patient name")
//            return false
//        }
//
//        if (contactNumber.isEmpty()) {
//            showError("Please enter contact number")
//            return false
//        }
//
//        if (location.isEmpty()) {
//            showError("Please select pickup location")
//            return false
//        }
//
//        if (patientAge.isNotEmpty()) {
//            try {
//                val age = patientAge.toInt()
//                if (age <= 0 || age > 150) {
//                    showError("Please enter a valid age")
//                    return false
//                }
//            } catch (e: NumberFormatException) {
//                showError("Please enter a valid age")
//                return false
//            }
//        }
//
//        return true
//    }
//
//    private fun showError(message: String) {
//        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
//    }
//
//    private fun navigateToRequestDetail(request: com.example.ambuplus.models.Request) {
//        val intent = Intent(this, RequestDetailActivity::class.java).apply {
//            putExtra("patient_name", request.patientName ?: "")
//            putExtra("contact_number", request.contactNumber)
//            putExtra("location", request.location.split("|").firstOrNull() ?: request.location)
//            putExtra("emergency_level", request.emergencyLevel)
//            putExtra("medical_notes", request.medicalNotes ?: "")
//            putExtra("request_id", request.id ?: -1)
//
//            val coords = extractCoordinates(request.location)
//            putExtra("selected_lat", coords?.first ?: 0.0)
//            putExtra("selected_lng", coords?.second ?: 0.0)
//            request.patientAge?.let { putExtra("patient_age", it) }
//        }
//        startActivity(intent)
//        finish()
//    }
//
//    private fun navigateToPatientTracking(request: com.example.ambuplus.models.Request) {
//        val coords = extractCoordinates(request.location)
//        val intent = Intent(this, com.example.ambuplus.uiactivities.patient.PatientTrackingActivity::class.java).apply {
//            putExtra("request_id", request.id ?: -1)
//            putExtra("pickup_lat", coords?.first ?: 0.0)
//            putExtra("pickup_lng", coords?.second ?: 0.0)
//            putExtra("patient_name", request.patientName ?: "Patient")
//            putExtra("driver_id", request.driverId ?: "")
//        }
//        startActivity(intent)
//        finish()
//    }
//
//    private fun extractCoordinates(location: String): Pair<Double, Double>? {
//        return try {
//            if (location.contains("|")) {
//                val parts = location.split("|")
//                if (parts.size == 2) {
//                    val coords = parts[1].split(",")
//                    if (coords.size == 2) {
//                        Pair(coords[0].toDouble(), coords[1].toDouble())
//                    } else null
//                } else null
//            } else null
//        } catch (e: Exception) {
//            null
//        }
//    }
//}
//
//// Factory classes
//class AuthViewModelFactory(private val authRepository: com.example.ambuplus.data.AuthRepository) : ViewModelProvider.Factory {
//    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
//        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
//            @Suppress("UNCHECKED_CAST")
//            return AuthViewModel(authRepository) as T
//        }
//        throw IllegalArgumentException("Unknown ViewModel class")
//    }
//}
//
//class RequestViewModelFactory(private val requestRepository: com.example.ambuplus.data.RequestRepository) : ViewModelProvider.Factory {
//    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
//        if (modelClass.isAssignableFrom(RequestViewModel::class.java)) {
//            @Suppress("UNCHECKED_CAST")
//            return RequestViewModel(requestRepository) as T
//        }
//        throw IllegalArgumentException("Unknown ViewModel class")
//    }
//}