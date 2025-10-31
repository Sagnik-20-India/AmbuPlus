package com.example.ambuplus.uiactivities.driver

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ambuplus.databinding.ActivityDriverProfileBinding
import com.example.ambuplus.models.Driver
import com.example.ambuplus.utils.ServiceLocator
import kotlinx.coroutines.launch

class DriverProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDriverProfileBinding
    private val driverRepository = ServiceLocator.driverRepository
    private val authRepository = ServiceLocator.authRepository
    private var currentDriver: Driver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDriverProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        loadDriverData()
    }

    private fun setupClickListeners() {
        binding.btnSave.setOnClickListener {
            updateDriverProfile()
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }

        binding.switchAvailability.setOnCheckedChangeListener { _, isChecked ->
            // Real-time availability update
            currentDriver?.let { driver ->
                if (driver.id != null) {
                    lifecycleScope.launch {
                        try {
                            driverRepository.updateDriverAvailability(driver.id, isChecked)
                            Toast.makeText(this@DriverProfileActivity,
                                if (isChecked) "You're now available" else "You're now unavailable",
                                Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(this@DriverProfileActivity, "Failed to update availability", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun loadDriverData() {
        binding.progressBar.visibility = android.view.View.VISIBLE

        lifecycleScope.launch {
            try {
                val currentUser = authRepository.currentUser()
                if (currentUser == null) {
                    Toast.makeText(this@DriverProfileActivity, "User not logged in", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }

                // Load driver data
                currentDriver = driverRepository.getDriverByUserId(currentUser.id)
                if (currentDriver == null) {
                    Toast.makeText(this@DriverProfileActivity, "Driver profile not found", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }

                // Populate UI with driver data
                populateDriverData(currentDriver!!)

                // Set email from auth
                binding.etEmail.setText(currentUser.email ?: "")

            } catch (e: Exception) {
                Toast.makeText(this@DriverProfileActivity, "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            } finally {
                binding.progressBar.visibility = android.view.View.GONE
            }
        }
    }

    private fun populateDriverData(driver: Driver) {
        binding.etFullName.setText(driver.name)
        binding.etPhone.setText(driver.phone)
        binding.etLicenseNumber.setText(driver.licenseNumber)
        binding.etVehicleNumber.setText(driver.vehicleNumber)
        binding.etVehicleType.setText(driver.vehicleType)
        binding.switchAvailability.isChecked = driver.available

        val verificationStatus = if (driver.isVerified) "Verified" else "Pending"
        val verificationColor = if (driver.isVerified) "#4CAF50" else "#E53935"
        binding.tvVerificationStatus.text = verificationStatus
        binding.tvVerificationStatus.setTextColor(android.graphics.Color.parseColor(verificationColor))
    }

    private fun updateDriverProfile() {
        val fullName = binding.etFullName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val licenseNumber = binding.etLicenseNumber.text.toString().trim()
        val vehicleNumber = binding.etVehicleNumber.text.toString().trim()
        val vehicleType = binding.etVehicleType.text.toString().trim()

        if (validateInputs(fullName, phone, licenseNumber, vehicleNumber, vehicleType)) {
            binding.progressBar.visibility = android.view.View.VISIBLE
            binding.btnSave.isEnabled = false

            lifecycleScope.launch {
                try {
                    val updatedDriver = currentDriver?.copy(
                        name = fullName,
                        phone = phone,
                        licenseNumber = licenseNumber,
                        vehicleNumber = vehicleNumber,
                        vehicleType = vehicleType
                    )

                    if (updatedDriver != null && updatedDriver.id != null) {
                        // Update driver in database
                        driverRepository.updateDriverProfile(updatedDriver)
                        Toast.makeText(this@DriverProfileActivity, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@DriverProfileActivity, "Failed to update profile: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnSave.isEnabled = true
                }
            }
        }
    }

    private fun validateInputs(
        fullName: String,
        phone: String,
        licenseNumber: String,
        vehicleNumber: String,
        vehicleType: String
    ): Boolean {
        if (fullName.isEmpty()) {
            showError("Please enter your full name")
            return false
        }
        if (phone.isEmpty()) {
            showError("Please enter your phone number")
            return false
        }
        if (licenseNumber.isEmpty()) {
            showError("Please enter your license number")
            return false
        }
        if (vehicleNumber.isEmpty()) {
            showError("Please enter your vehicle number")
            return false
        }
        if (vehicleType.isEmpty()) {
            showError("Please enter your vehicle type")
            return false
        }
        return true
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}