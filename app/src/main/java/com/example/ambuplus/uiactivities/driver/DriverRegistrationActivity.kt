package com.example.ambuplus.uiactivities.driver

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ambuplus.databinding.ActivityDriverRegistrationBinding
import com.example.ambuplus.models.Driver
import com.example.ambuplus.uiactivities.main.MainActivity
import com.example.ambuplus.utils.ServiceLocator
import kotlinx.coroutines.launch

class DriverRegistrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDriverRegistrationBinding
    private val driverRepository = ServiceLocator.driverRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDriverRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnRegisterDriver.setOnClickListener {
            registerDriver()
        }
    }

    private fun registerDriver() {
        val fullName = binding.etFullName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val licenseNumber = binding.etLicenseNumber.text.toString().trim()
        val vehicleNumber = binding.etVehicleNumber.text.toString().trim()
        val vehicleType = binding.etVehicleType.text.toString().trim()

        if (validateInputs(fullName, phone, licenseNumber, vehicleNumber, vehicleType)) {
            binding.progressBar.visibility = android.view.View.VISIBLE
            binding.btnRegisterDriver.isEnabled = false

            lifecycleScope.launch {
                try {
                    val userId = ServiceLocator.authRepository.currentUser()?.id
                    if (userId == null) {
                        Toast.makeText(this@DriverRegistrationActivity, "User not logged in", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val driver = Driver(
                        userId = userId,
                        name = fullName,
                        phone = phone,
                        licenseNumber = licenseNumber,
                        vehicleNumber = vehicleNumber,
                        vehicleType = vehicleType,
                        available = false, // Start as unavailable until verified
                        isVerified = false // Admin needs to verify
                    )

                    driverRepository.registerDriver(driver)

                    Toast.makeText(this@DriverRegistrationActivity, "Driver registration submitted for verification!", Toast.LENGTH_LONG).show()

                    // Navigate back to main activity
                    val intent = Intent(this@DriverRegistrationActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()

                } catch (e: Exception) {
                    Toast.makeText(this@DriverRegistrationActivity, "Registration failed: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnRegisterDriver.isEnabled = true
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
            showError("Please enter your driver license number")
            return false
        }
        if (vehicleNumber.isEmpty()) {
            showError("Please enter your vehicle registration number")
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