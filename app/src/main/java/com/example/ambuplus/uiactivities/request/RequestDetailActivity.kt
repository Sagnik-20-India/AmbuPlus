package com.example.ambuplus.uiactivities.request

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ambuplus.databinding.RequestDetailBinding
import com.example.ambuplus.uiactivities.main.MainActivity
import com.example.ambuplus.uiactivities.patient.PatientTrackingActivity
import com.example.ambuplus.models.Request
import com.example.ambuplus.utils.ServiceLocator
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

class RequestDetailActivity : AppCompatActivity() {

    private lateinit var binding: RequestDetailBinding
    private var requestId: Int = -1
    private var selectedLat: Double? = null
    private var selectedLng: Double? = null
    private var isEmergencyMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(
            applicationContext,
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )

        binding = RequestDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get request data from intent
        requestId = intent.getIntExtra("request_id", -1)
        selectedLat = intent.getDoubleExtra("selected_lat", 0.0).takeIf { it != 0.0 }
        selectedLng = intent.getDoubleExtra("selected_lng", 0.0).takeIf { it != 0.0 }
        isEmergencyMode = intent.getBooleanExtra("is_emergency_mode", false)

        // If in emergency mode, save the request ID for persistence
        if (isEmergencyMode && requestId != -1) {
            val prefs = getSharedPreferences("EmergencyPrefs", Context.MODE_PRIVATE)
            prefs.edit().putInt("emergency_active_request_id", requestId).apply()
        }

        setupMap()
        displayRequestInfo()
        setupClickListeners()

        // Check if request is already accepted
        checkRequestStatus()
    }

    private fun setupClickListeners() {
        // Cancel Request button
        binding.btnCancelRequest.setOnClickListener {
            showCancelConfirmationDialog()
        }

        // Exit button - goes back to MainActivity without canceling request
        binding.btnExit.setOnClickListener {
            Toast.makeText(this, "You can track your request from the main screen", Toast.LENGTH_LONG).show()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }

        // Recenter Map button
        binding.btnReCenter.setOnClickListener {
            if (selectedLat != null && selectedLng != null) {
                val mapLocation = GeoPoint(selectedLat!!, selectedLng!!)
                binding.mapView.controller.animateTo(mapLocation)
                binding.mapView.controller.setZoom(16.0)
                Toast.makeText(this, "Map centered on pickup location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCancelConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Cancel Request")
            .setMessage("Are you sure you want to cancel your ambulance request?")
            .setPositiveButton("Yes, Cancel") { _, _ ->
                cancelRequest()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun checkRequestStatus() {
        lifecycleScope.launch {
            try {
                val request = ServiceLocator.requestRepository.getRequestById(requestId)
                if (request?.status == "accepted") {
                    Toast.makeText(this@RequestDetailActivity, "Ambulance is on the way! Opening tracker...", Toast.LENGTH_LONG).show()
                    navigateToPatientTracking(request)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun navigateToPatientTracking(request: Request) {
        val coords = extractCoordinates(request.location)
        val intent = Intent(this, PatientTrackingActivity::class.java).apply {
            putExtra("request_id", request.id ?: -1)
            putExtra("pickup_lat", coords?.first ?: 0.0)
            putExtra("pickup_lng", coords?.second ?: 0.0)
            putExtra("patient_name", request.patientName ?: "Patient")
            putExtra("driver_id", request.driverId ?: "")
            putExtra("is_emergency_mode", isEmergencyMode)
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

    private fun setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.setBuiltInZoomControls(true)

        val mapLocation = if (selectedLat != null && selectedLng != null) {
            GeoPoint(selectedLat!!, selectedLng!!)
        } else {
            val location = intent.getStringExtra("location") ?: ""
            parseCoordinatesFromString(location) ?: GeoPoint(22.5726, 88.3639)
        }

        val marker = Marker(binding.mapView).apply {
            position = mapLocation
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Pickup Location"
            subDescription = intent.getStringExtra("location") ?: "Location"
            binding.mapView.overlays.add(this)
        }

        binding.mapView.controller.setCenter(mapLocation)
        binding.mapView.controller.setZoom(15.0)
    }

    private fun parseCoordinatesFromString(location: String): GeoPoint? {
        return try {
            if (location.contains("|")) {
                val parts = location.split("|")
                if (parts.size == 2) {
                    val coords = parts[1].split(",")
                    if (coords.size == 2) {
                        GeoPoint(coords[0].toDouble(), coords[1].toDouble())
                    } else null
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun displayRequestInfo() {
        val patientName = intent.getStringExtra("patient_name") ?: "N/A"
        val patientAge = intent.getIntExtra("patient_age", 0)
        val contactNumber = intent.getStringExtra("contact_number") ?: "N/A"
        val location = intent.getStringExtra("location") ?: "N/A"
        val emergencyLevel = intent.getStringExtra("emergency_level") ?: "medium"
        val medicalNotes = intent.getStringExtra("medical_notes") ?: "None"

        binding.tvPatientName.text = "Patient: $patientName"
        binding.tvPatientAge.text = "Age: ${if (patientAge > 0) patientAge else "N/A"}"
        binding.tvContactNumber.text = "Contact: $contactNumber"
        binding.tvLocation.text = "Location: $location"
        binding.tvEmergencyLevel.text = "Emergency Level: ${emergencyLevel.uppercase()}"
        binding.tvMedicalNotes.text = "Notes: $medicalNotes"

        // Show different status message for emergency mode
        if (isEmergencyMode) {
            binding.tvStatus.text = "🚨 EMERGENCY REQUEST - Finding ambulance..."
        } else {
            binding.tvStatus.text = "Status: Finding ambulance..."
        }
    }

    private fun cancelRequest() {
        if (requestId == -1) {
            Toast.makeText(this, "Cannot cancel: Request ID not found", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                ServiceLocator.requestRepository.updateRequestStatus(requestId, "cancelled")

                // Clear saved emergency request ID if in emergency mode
                if (isEmergencyMode) {
                    val prefs = getSharedPreferences("EmergencyPrefs", Context.MODE_PRIVATE)
                    prefs.edit().remove("emergency_active_request_id").apply()
                }

                Toast.makeText(this@RequestDetailActivity, "Request cancelled successfully", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@RequestDetailActivity, "Failed to cancel request: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }
}





//package com.example.ambuplus.uiactivities.request
//
//import android.app.AlertDialog
//import android.content.Intent
//import android.os.Bundle
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import androidx.lifecycle.lifecycleScope
//import com.example.ambuplus.databinding.RequestDetailBinding
//import com.example.ambuplus.uiactivities.main.MainActivity
//import com.example.ambuplus.uiactivities.patient.PatientTrackingActivity
//import com.example.ambuplus.models.Request
//import com.example.ambuplus.utils.ServiceLocator
//import kotlinx.coroutines.launch
//import org.osmdroid.config.Configuration
//import org.osmdroid.tileprovider.tilesource.TileSourceFactory
//import org.osmdroid.util.GeoPoint
//import org.osmdroid.views.overlay.Marker
//
//class RequestDetailActivity : AppCompatActivity() {
//
//    private lateinit var binding: RequestDetailBinding
//    private var requestId: Int = -1
//    private var selectedLat: Double? = null
//    private var selectedLng: Double? = null
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        Configuration.getInstance().load(
//            applicationContext,
//            androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
//        )
//
//        binding = RequestDetailBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        // Get request data from intent
//        requestId = intent.getIntExtra("request_id", -1)
//        selectedLat = intent.getDoubleExtra("selected_lat", 0.0).takeIf { it != 0.0 }
//        selectedLng = intent.getDoubleExtra("selected_lng", 0.0).takeIf { it != 0.0 }
//
//        setupMap()
//        displayRequestInfo()
//        setupClickListeners()
//
//        // Check if request is already accepted
//        checkRequestStatus()
//    }
//
//    private fun setupClickListeners() {
//        // Cancel Request button
//        binding.btnCancelRequest.setOnClickListener {
//            showCancelConfirmationDialog()
//        }
//
//        // Exit button - goes back to MainActivity without canceling request
//        binding.btnExit.setOnClickListener {
//            Toast.makeText(this, "You can track your request from the main screen", Toast.LENGTH_LONG).show()
//            val intent = Intent(this, MainActivity::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
//            startActivity(intent)
//            finish()
//        }
//
//        // Recenter Map button
//        binding.btnReCenter.setOnClickListener {
//            if (selectedLat != null && selectedLng != null) {
//                val mapLocation = GeoPoint(selectedLat!!, selectedLng!!)
//                binding.mapView.controller.animateTo(mapLocation)
//                binding.mapView.controller.setZoom(16.0)
//                Toast.makeText(this, "Map centered on pickup location", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
//
//    private fun showCancelConfirmationDialog() {
//        AlertDialog.Builder(this)
//            .setTitle("Cancel Request")
//            .setMessage("Are you sure you want to cancel your ambulance request?")
//            .setPositiveButton("Yes, Cancel") { _, _ ->
//                cancelRequest()
//            }
//            .setNegativeButton("No", null)
//            .show()
//    }
//
//    private fun checkRequestStatus() {
//        lifecycleScope.launch {
//            try {
//                val request = ServiceLocator.requestRepository.getRequestById(requestId)
//                if (request?.status == "accepted") {
//                    Toast.makeText(this@RequestDetailActivity, "Ambulance is on the way! Opening tracker...", Toast.LENGTH_LONG).show()
//                    navigateToPatientTracking(request)
//                }
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }
//    }
//
//    private fun navigateToPatientTracking(request: Request) {
//        val coords = extractCoordinates(request.location)
//        val intent = Intent(this, PatientTrackingActivity::class.java).apply {
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
//
//    private fun setupMap() {
//        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
//        binding.mapView.setMultiTouchControls(true)
//        binding.mapView.setBuiltInZoomControls(true)
//
//        val mapLocation = if (selectedLat != null && selectedLng != null) {
//            GeoPoint(selectedLat!!, selectedLng!!)
//        } else {
//            val location = intent.getStringExtra("location") ?: ""
//            parseCoordinatesFromString(location) ?: GeoPoint(22.5726, 88.3639)
//        }
//
//        val marker = Marker(binding.mapView).apply {
//            position = mapLocation
//            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
//            title = "Pickup Location"
//            subDescription = intent.getStringExtra("location") ?: "Location"
//            binding.mapView.overlays.add(this)
//        }
//
//        binding.mapView.controller.setCenter(mapLocation)
//        binding.mapView.controller.setZoom(15.0)
//    }
//
//    private fun parseCoordinatesFromString(location: String): GeoPoint? {
//        return try {
//            if (location.contains("|")) {
//                val parts = location.split("|")
//                if (parts.size == 2) {
//                    val coords = parts[1].split(",")
//                    if (coords.size == 2) {
//                        GeoPoint(coords[0].toDouble(), coords[1].toDouble())
//                    } else null
//                } else null
//            } else null
//        } catch (e: Exception) {
//            null
//        }
//    }
//
//    private fun displayRequestInfo() {
//        val patientName = intent.getStringExtra("patient_name") ?: "N/A"
//        val patientAge = intent.getIntExtra("patient_age", 0)
//        val contactNumber = intent.getStringExtra("contact_number") ?: "N/A"
//        val location = intent.getStringExtra("location") ?: "N/A"
//        val emergencyLevel = intent.getStringExtra("emergency_level") ?: "medium"
//        val medicalNotes = intent.getStringExtra("medical_notes") ?: "None"
//
//        binding.tvPatientName.text = "Patient: $patientName"
//        binding.tvPatientAge.text = "Age: ${if (patientAge > 0) patientAge else "N/A"}"
//        binding.tvContactNumber.text = "Contact: $contactNumber"
//        binding.tvLocation.text = "Location: $location"
//        binding.tvEmergencyLevel.text = "Emergency Level: ${emergencyLevel.uppercase()}"
//        binding.tvMedicalNotes.text = "Notes: $medicalNotes"
//        binding.tvStatus.text = "Status: Finding ambulance..."
//    }
//
//    private fun cancelRequest() {
//        if (requestId == -1) {
//            Toast.makeText(this, "Cannot cancel: Request ID not found", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        lifecycleScope.launch {
//            try {
//                ServiceLocator.requestRepository.updateRequestStatus(requestId, "cancelled")
//                Toast.makeText(this@RequestDetailActivity, "Request cancelled successfully", Toast.LENGTH_SHORT).show()
//                finish()
//            } catch (e: Exception) {
//                Toast.makeText(this@RequestDetailActivity, "Failed to cancel request: ${e.message}", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
//
//    override fun onResume() {
//        super.onResume()
//        binding.mapView.onResume()
//    }
//
//    override fun onPause() {
//        super.onPause()
//        binding.mapView.onPause()
//    }
//}