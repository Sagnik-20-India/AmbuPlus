package com.example.ambuplus.uiactivities.request

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.ambuplus.R
import com.example.ambuplus.databinding.ActivityMapLocationPickerBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class MapLocationPickerActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMapLocationPickerBinding
    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var selectedLocation: LatLng? = null
    private var selectedAddress: String = "Tap on map to select location"

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        const val EXTRA_SELECTED_LOCATION = "selected_location"
        const val EXTRA_SELECTED_ADDRESS = "selected_address"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapLocationPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupMapFragment()
        setupClickListeners()
    }

    private fun setupMapFragment() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun setupClickListeners() {
        binding.btnMyLocation.setOnClickListener {
            getCurrentLocation()
        }

        binding.btnCancel.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        binding.btnConfirmLocation.setOnClickListener {
            if (selectedLocation != null) {
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_SELECTED_LOCATION, selectedLocation)
                    putExtra(EXTRA_SELECTED_ADDRESS, selectedAddress)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            } else {
                Toast.makeText(this, "Please select a location on the map", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // Enable zoom controls
        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.uiSettings.isCompassEnabled = true
        googleMap.uiSettings.isMapToolbarEnabled = true

        // Set up map click listener
        googleMap.setOnMapClickListener { latLng ->
            selectedLocation = latLng
            updateMarker(latLng)
            reverseGeocode(latLng)
        }

        // Set up map long click listener
        googleMap.setOnMapLongClickListener { latLng ->
            selectedLocation = latLng
            updateMarker(latLng)
            reverseGeocode(latLng)
        }

        // Check location permission
        if (checkLocationPermission()) {
            enableMyLocation()
            getCurrentLocation()
        } else {
            requestLocationPermission()
            // Move to default location if no permission
            val defaultLocation = LatLng(22.5726, 88.3639) // Kolkata
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 12f))
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {
        if (checkLocationPermission()) {
            googleMap.isMyLocationEnabled = true
        }
    }

    private fun updateMarker(latLng: LatLng) {
        googleMap.clear()
        googleMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Pickup Location")
                .snippet("Tap confirm to use this location")
        )

        // Move camera to selected location
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
    }

    private fun reverseGeocode(latLng: LatLng) {
        // In a real app, use Geocoding API to get address from coordinates
        // For now, show coordinates and a simple message
        selectedAddress = String.format(
            "Lat: %.6f, Lng: %.6f\nTap confirm to use this location",
            latLng.latitude,
            latLng.longitude
        )
        binding.tvSelectedAddress.text = selectedAddress
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        if (checkLocationPermission()) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    location?.let {
                        val currentLatLng = LatLng(it.latitude, it.longitude)
                        selectedLocation = currentLatLng
                        updateMarker(currentLatLng)
                        reverseGeocode(currentLatLng)
                    } ?: run {
                        Toast.makeText(this, "Unable to get current location", Toast.LENGTH_SHORT).show()
                        // Show default location
                        val defaultLocation = LatLng(22.5726, 88.3639)
                        selectedLocation = defaultLocation
                        updateMarker(defaultLocation)
                        reverseGeocode(defaultLocation)
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error getting location: ${e.message}", Toast.LENGTH_SHORT).show()
                    // Show default location on error
                    val defaultLocation = LatLng(22.5726, 88.3639)
                    selectedLocation = defaultLocation
                    updateMarker(defaultLocation)
                    reverseGeocode(defaultLocation)
                }
        } else {
            requestLocationPermission()
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

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    enableMyLocation()
                    getCurrentLocation()
                }
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}