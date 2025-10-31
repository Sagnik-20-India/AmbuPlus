package com.example.ambuplus.uiactivities.driver

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ambuplus.databinding.ActivityDriverBinding
import com.example.ambuplus.utils.ServiceLocator
import kotlinx.coroutines.launch

class DriverActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDriverBinding
    private val driverRepository = ServiceLocator.driverRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDriverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnToggleAvailability.setOnClickListener {
            toggleDriverAvailability()
        }

        binding.btnViewRequests.setOnClickListener {
            Toast.makeText(this, "View Requests - Coming Soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleDriverAvailability() {
        val driverId = "1" // Example driver ID
        val currentAvailability = true // This should come from your data

        lifecycleScope.launch {
            try {
                driverRepository.updateDriverAvailability(driverId, !currentAvailability)
                Toast.makeText(this@DriverActivity, "Availability updated", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@DriverActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}