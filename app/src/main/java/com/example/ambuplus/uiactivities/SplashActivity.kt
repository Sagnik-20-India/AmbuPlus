package com.example.ambuplus.uiactivities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ambuplus.uiactivities.main.MainActivity
import com.example.ambuplus.uiactivities.login.LoginActivity
import com.example.ambuplus.uiactivities.driver.DriverTrackingActivity
import com.example.ambuplus.uiactivities.patient.PatientTrackingActivity
import com.example.ambuplus.uiactivities.request.RequestDetailActivity
import com.example.ambuplus.models.Request
import com.example.ambuplus.utils.ServiceLocator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "AmbuPlus"
        private const val KEY_ACTIVE_DRIVER_REQUEST_ID = "active_driver_request_id"
        private const val KEY_ACTIVE_DRIVER_ID = "active_driver_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            delay(500)

            println("DEBUG: ====== SPLASH ACTIVITY START ======")

            val currentUser = ServiceLocator.authRepository.currentUser()
            println("DEBUG: Current user: ${currentUser?.id}")

            if (currentUser == null) {
                navigateToLogin()
            } else {
                // Clear any invalid saved requests before checking
                clearInvalidSavedRequests()
                checkActiveRequests(currentUser.id)
            }
        }
    }

    private fun clearInvalidSavedRequests() {
        val sharedPref = getSharedPreferences("AmbuPlus", Context.MODE_PRIVATE)
        val savedRequestId = sharedPref.getInt("active_driver_request_id", -1)

        if (savedRequestId != -1) {
            lifecycleScope.launch {
                try {
                    val request = ServiceLocator.requestRepository.getRequestById(savedRequestId)
                    if (request == null || request.status != "accepted") {
                        println("DEBUG: Splash - Clearing invalid saved request ID: $savedRequestId")
                        sharedPref.edit().remove("active_driver_request_id").apply()
                        sharedPref.edit().remove("active_driver_id").apply()
                    }
                } catch (e: Exception) {
                    sharedPref.edit().remove("active_driver_request_id").apply()
                    sharedPref.edit().remove("active_driver_id").apply()
                }
            }
        }
    }

    private suspend fun checkActiveRequests(userId: String) {

        // Check for emergency active request first
        val emergencyRequest = checkForActiveEmergencyRequest()
        if (emergencyRequest != null) {
            when (emergencyRequest.status) {
                "pending" -> navigateToRequestDetail(emergencyRequest)
                "accepted" -> navigateToPatientTracking(emergencyRequest)
            }
            return
        }

        // FIRST: Check for driver active request
        val driver = ServiceLocator.driverRepository.getDriverByUserId(userId)

        if (driver != null && driver.id != null) {
            val driverActiveRequest = ServiceLocator.requestRepository.getActiveRequestForDriver(driver.id)

            if (driverActiveRequest != null && driverActiveRequest.status == "accepted") {
                println("DEBUG: Found active driver request! Navigating to DriverTrackingActivity")
                navigateToDriverTracking(driverActiveRequest)
                return
            }
        }

        // SECOND: Check for patient active request
        val activeRequest = ServiceLocator.requestRepository.getActiveRequest(userId)

        if (activeRequest != null) {
            when (activeRequest.status) {
                "pending" -> {
                    println("DEBUG: Navigating to RequestDetailActivity (pending)")
                    navigateToRequestDetail(activeRequest)
                }
                "accepted" -> {
                    println("DEBUG: Navigating to PatientTrackingActivity (accepted)")
                    navigateToPatientTracking(activeRequest)
                }
                else -> {
                    println("DEBUG: Unknown status, going to MainActivity")
                    navigateToMain()
                }
            }
            return
        }

        println("DEBUG: No active requests found, going to MainActivity")
        navigateToMain()
    }

    // Add this function to check for active emergency request
    private suspend fun checkForActiveEmergencyRequest(): Request? {
        val prefs = getSharedPreferences("EmergencyPrefs", Context.MODE_PRIVATE)
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

    private fun navigateToLogin() {
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }, 500)
    }

    private fun navigateToMain() {
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }, 500)
    }

    private fun navigateToRequestDetail(request: Request) {
        val intent = Intent(this, RequestDetailActivity::class.java).apply {
            putExtra("patient_name", request.patientName ?: "")
            putExtra("contact_number", request.contactNumber ?: "")
            putExtra("location", request.location.split("|").firstOrNull() ?: request.location)
            putExtra("emergency_level", request.emergencyLevel ?: "medium")
            putExtra("medical_notes", request.medicalNotes ?: "")
            putExtra("request_id", request.id ?: -1)
            val coords = extractCoordinates(request.location)
            putExtra("selected_lat", coords?.first ?: 0.0)
            putExtra("selected_lng", coords?.second ?: 0.0)
            request.patientAge?.let { putExtra("patient_age", it) }
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToPatientTracking(request: Request) {
        val coords = extractCoordinates(request.location)
        val intent = Intent(this, PatientTrackingActivity::class.java).apply {
            putExtra("request_id", request.id ?: -1)
            putExtra("pickup_lat", coords?.first ?: 0.0)
            putExtra("pickup_lng", coords?.second ?: 0.0)
            putExtra("patient_name", request.patientName ?: "Patient")
            putExtra("driver_id", request.driverId ?: "")
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToDriverTracking(request: Request) {
        val coords = extractCoordinates(request.location)
        val intent = Intent(this, DriverTrackingActivity::class.java).apply {
            putExtra("request_id", request.id ?: -1)
            putExtra("patient_lat", coords?.first ?: 0.0)
            putExtra("patient_lng", coords?.second ?: 0.0)
            putExtra("patient_address", request.location.split("|").firstOrNull() ?: request.location)
            putExtra("patient_name", request.patientName ?: "Patient")
            putExtra("contact_number", request.contactNumber ?: "")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
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