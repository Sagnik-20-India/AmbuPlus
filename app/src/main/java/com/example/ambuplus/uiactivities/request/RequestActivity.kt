package com.example.ambuplus.uiactivities.request

import android.Manifest
import android.app.Activity
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
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RequestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRequestBinding
    private lateinit var authViewModel: AuthViewModel
    private lateinit var requestViewModel: RequestViewModel

    private var selectedLatLng: LatLng? = null
    private var selectedAddress: String = ""

    // Register for activity result
    private val mapPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            selectedLatLng = data?.getParcelableExtra(MapLocationPickerActivity.EXTRA_SELECTED_LOCATION)
            selectedAddress = data?.getStringExtra(MapLocationPickerActivity.EXTRA_SELECTED_ADDRESS) ?: ""

            binding.etLocation.setText(selectedAddress)
        }
    }

    // Location permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // Permission granted, open map picker
            openMapLocationPicker()
        } else {
            Toast.makeText(this, "Location permission required to select pickup location", Toast.LENGTH_SHORT).show()
        }
    }

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

    private fun submitRequest() {
        val currentUser = authViewModel.currentUser.value
        if (currentUser == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            return
        }

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

            // Show loading
            binding.btnSubmitRequest.isEnabled = false
            val progressBar = findViewById<android.widget.ProgressBar>(com.example.ambuplus.R.id.progressBar)
            progressBar?.visibility = android.view.View.VISIBLE

            lifecycleScope.launch {
                try {
                    // Create request first
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                    // Include coordinates if available
                    val locationWithCoords = if (selectedLatLng != null) {
                        "$location|${selectedLatLng!!.latitude},${selectedLatLng!!.longitude}"
                    } else {
                        location
                    }

                    // Create the request object
                    val request = com.example.ambuplus.models.Request(
                        userId = currentUser.id,
                        location = locationWithCoords, // Store coordinates with address
                        emergencyLevel = emergencyLevel,
                        contactNumber = contactNumber,
                        timestamp = timestamp,
                        patientName = patientName,
                        patientAge = patientAge,
                        medicalNotes = medicalNotes,
                        status = "pending"
                    )

                    // Save to database
                    ServiceLocator.requestRepository.addRequest(request)

                    // Get the latest requests to find our new request
                    val allRequests = ServiceLocator.requestRepository.getAllRequests()
                    val newRequest = allRequests.findLast {
                        it.userId == currentUser.id &&
                                it.timestamp.contains(timestamp.substring(0, 10))
                    }

                    if (newRequest != null && newRequest.id != null) {
                        // Navigate to detail page with request ID
                        val intent = Intent(this@RequestActivity, RequestDetailActivity::class.java).apply {
                            putExtra("patient_name", patientName)
                            putExtra("contact_number", contactNumber)
                            putExtra("location", location)
                            putExtra("emergency_level", emergencyLevel)
                            putExtra("medical_notes", medicalNotes)
                            putExtra("request_id", newRequest.id!!)
                            putExtra("selected_lat", selectedLatLng?.latitude)
                            putExtra("selected_lng", selectedLatLng?.longitude)
                            patientAge?.let { putExtra("patient_age", it) }
                        }

                        Toast.makeText(this@RequestActivity, "Ambulance request submitted successfully!", Toast.LENGTH_SHORT).show()
                        startActivity(intent)
                    } else {
                        // Fallback
                        val intent = Intent(this@RequestActivity, RequestDetailActivity::class.java).apply {
                            putExtra("patient_name", patientName)
                            putExtra("contact_number", contactNumber)
                            putExtra("location", location)
                            putExtra("emergency_level", emergencyLevel)
                            putExtra("medical_notes", medicalNotes)
                            putExtra("request_id", -1)
                            putExtra("selected_lat", selectedLatLng?.latitude)
                            putExtra("selected_lng", selectedLatLng?.longitude)
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
}

// Factory classes (keep as before)
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