package com.example.ambuplus.uiactivities.request

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ambuplus.databinding.RequestDetailBinding
import com.example.ambuplus.utils.ServiceLocator
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.launch

class RequestDetailActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: RequestDetailBinding
    private lateinit var mapView: MapView
    private lateinit var googleMap: GoogleMap
    private var requestId: Int = -1
    private var selectedLat: Double? = null
    private var selectedLng: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = RequestDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get request data from intent
        requestId = intent.getIntExtra("request_id", -1)
        selectedLat = intent.getDoubleExtra("selected_lat", 0.0).takeIf { it != 0.0 }
        selectedLng = intent.getDoubleExtra("selected_lng", 0.0).takeIf { it != 0.0 }

        setupViews(savedInstanceState)
        displayRequestInfo()
        setupClickListeners()
    }

    private fun setupViews(savedInstanceState: Bundle?) {
        // Initialize MapView with savedInstanceState
        mapView = binding.mapView7
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)
    }

    private fun displayRequestInfo() {
        // Get data from intent
        val patientName = intent.getStringExtra("patient_name") ?: "N/A"
        val patientAge = intent.getIntExtra("patient_age", 0)
        val contactNumber = intent.getStringExtra("contact_number") ?: "N/A"
        val location = intent.getStringExtra("location") ?: "N/A"
        val emergencyLevel = intent.getStringExtra("emergency_level") ?: "medium"
        val medicalNotes = intent.getStringExtra("medical_notes") ?: "None"

        // Update UI
        binding.tvPatientName.text = "Patient: $patientName"
        binding.tvPatientAge.text = "Age: ${if (patientAge > 0) patientAge else "N/A"}"
        binding.tvContactNumber.text = "Contact: $contactNumber"
        binding.tvLocation.text = "Location: $location"
        binding.tvEmergencyLevel.text = "Emergency Level: ${emergencyLevel.uppercase()}"
        binding.tvMedicalNotes.text = "Notes: $medicalNotes"
        binding.tvStatus.text = "Status: Finding ambulance..."
    }

    private fun setupClickListeners() {
        binding.btnCancelRequest.setOnClickListener {
            cancelRequest()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // Use selected coordinates if available, otherwise use default
        val mapLocation = if (selectedLat != null && selectedLng != null) {
            LatLng(selectedLat!!, selectedLng!!)
        } else {
            // Try to parse coordinates from location string
            val location = intent.getStringExtra("location") ?: ""
            parseCoordinatesFromString(location) ?: LatLng(22.5726, 88.3639) // Default to Kolkata
        }

        // Add marker
        googleMap.addMarker(
            MarkerOptions()
                .position(mapLocation)
                .title("Pickup Location")
                .snippet(intent.getStringExtra("location") ?: "Location")
        )

        // Move camera to location
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mapLocation, 15f))

        // Enable basic UI controls
        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.uiSettings.isCompassEnabled = true
    }

    private fun parseCoordinatesFromString(location: String): LatLng? {
        return try {
            // Check if location contains coordinates (format: address|lat,lng)
            if (location.contains("|")) {
                val parts = location.split("|")
                if (parts.size == 2) {
                    val coords = parts[1].split(",")
                    if (coords.size == 2) {
                        LatLng(coords[0].toDouble(), coords[1].toDouble())
                    } else null
                } else null
            } else null
        } catch (e: Exception) {
            null
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
                Toast.makeText(this@RequestDetailActivity, "Request cancelled successfully", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@RequestDetailActivity, "Failed to cancel request: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // MapView lifecycle methods
    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}