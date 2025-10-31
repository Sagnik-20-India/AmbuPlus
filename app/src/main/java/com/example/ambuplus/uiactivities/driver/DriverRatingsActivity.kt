package com.example.ambuplus.uiactivities.driver

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ambuplus.databinding.ActivityDriverRatingsBinding
import com.example.ambuplus.utils.ServiceLocator
import kotlinx.coroutines.launch

class DriverRatingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDriverRatingsBinding
    private val driverRepository = ServiceLocator.driverRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDriverRatingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        loadDriverRatings()
    }

    private fun setupClickListeners() {
        binding.btnViewAllReviews.setOnClickListener {
            Toast.makeText(this, "All reviews feature coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadDriverRatings() {
        binding.progressBar.visibility = android.view.View.VISIBLE

        lifecycleScope.launch {
            try {
                val currentUser = ServiceLocator.authRepository.currentUser()
                if (currentUser == null) {
                    Toast.makeText(this@DriverRatingsActivity, "User not logged in", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }

                // Load driver data to get ID
                val driver = driverRepository.getDriverByUserId(currentUser.id)
                if (driver != null) {
                    // In a real app, you would fetch ratings and earnings from your database
                    // For now, we'll use mock data
                    loadMockRatingsData(driver.id!!)
                } else {
                    Toast.makeText(this@DriverRatingsActivity, "Driver profile not found", Toast.LENGTH_SHORT).show()
                    finish()
                }

            } catch (e: Exception) {
                Toast.makeText(this@DriverRatingsActivity, "Error loading ratings: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            } finally {
                binding.progressBar.visibility = android.view.View.GONE
            }
        }
    }

    private fun loadMockRatingsData(driverId: String) {
        // Mock data - replace with actual API calls
        val overallRating = 4.5f
        val totalRatings = 24
        val totalEarnings = "₹12,450"
        val monthlyEarnings = "₹3,200"
        val totalTrips = 45

        // Update UI with data
        binding.ratingBarOverall.rating = overallRating
        binding.tvRatingValue.text = overallRating.toString()
        binding.tvTotalRatings.text = "Based on $totalRatings reviews"
        binding.tvTotalEarnings.text = totalEarnings
        binding.tvMonthlyEarnings.text = monthlyEarnings
        binding.tvTotalTrips.text = totalTrips.toString()
    }
}