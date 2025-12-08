package com.example.ambuplus.uiactivities.request

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.ambuplus.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class RequestDetailActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private lateinit var googleMap: GoogleMap

    private var patientName: String? = null
    private var contactNumber: String? = null
    private var location: String? = null
    private var emergencyLevel: String? = null
    private var medicalNotes: String? = null
    private var patientAge: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.request_detail)

        // Get data from intent
        patientName = intent.getStringExtra("patient_name")
        contactNumber = intent.getStringExtra("contact_number")
        location = intent.getStringExtra("location")
        emergencyLevel = intent.getStringExtra("emergency_level")
        medicalNotes = intent.getStringExtra("medical_notes")
        patientAge = intent.getIntExtra("patient_age", -1).takeIf { it != -1 }

        // Display the data in your layout (you'll need to add TextViews to request_detail.xml)
        displayRequestInfo()

        // Initialize MapView
        mapView = findViewById(R.id.mapView7)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)
    }

    private fun displayRequestInfo() {
        // Add TextViews to your request_detail.xml layout and update them here
        findViewById<TextView>(R.id.tvPatientName)?.text = "Patient: $patientName"
        findViewById<TextView>(R.id.tvContactNumber)?.text = "Contact: $contactNumber"
        findViewById<TextView>(R.id.tvLocation)?.text = "Location: $location"
        findViewById<TextView>(R.id.tvEmergencyLevel)?.text = "Emergency Level: ${emergencyLevel?.uppercase()}"
        medicalNotes?.let {
            findViewById<TextView>(R.id.tvMedicalNotes)?.text = "Notes: $it"
        }
        patientAge?.let {
            findViewById<TextView>(R.id.tvPatientAge)?.text = "Age: $it"
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // TODO: Parse the location string to get actual coordinates
        // For now, using default coordinates
        val pickupLocation = LatLng(22.5726, 88.3639)

        googleMap.addMarker(
            MarkerOptions()
                .position(pickupLocation)
                .title("Pickup: $location")
        )

        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pickupLocation, 14f))
    }

    // MapView lifecycle
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

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}
