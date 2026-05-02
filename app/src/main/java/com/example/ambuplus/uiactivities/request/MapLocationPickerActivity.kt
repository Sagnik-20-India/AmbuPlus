package com.example.ambuplus.uiactivities.request

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.ambuplus.databinding.ActivityMapLocationPickerBinding
import com.example.ambuplus.utils.NominatimResult
import com.example.ambuplus.utils.RetrofitClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import android.view.MotionEvent

class MapLocationPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapLocationPickerBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var selectedLocation: GeoPoint? = null
    private var selectedMarker: Marker? = null
    private var selectedAddress: String = "Tap on map or search above to select location"
    private val suggestionsList = mutableListOf<NominatimResult>()
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var isReversingGeocoding = false

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        const val EXTRA_SELECTED_ADDRESS = "selected_address"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize osmdroid configuration
        Configuration.getInstance().load(
            applicationContext,
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )

        binding = ActivityMapLocationPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupMap()
        setupSearchView()
        setupClickListeners()
    }

    private fun setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.setBuiltInZoomControls(true)
        binding.mapView.controller.setZoom(15.0)

        // Set up map click listener
        val touchOverlay = object : Overlay() {
            override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
                val projection = mapView.projection
                val geoPoint = projection.fromPixels(e.x.toInt(), e.y.toInt())
                if (geoPoint != null) {
                    selectedLocation = geoPoint as GeoPoint
                    updateMarker(selectedLocation!!)
                    reverseGeocode(selectedLocation!!)
                }
                return true
            }
        }
        binding.mapView.overlays.add(touchOverlay)

        // Check location permission and get current location
        if (checkLocationPermission()) {
            getCurrentLocation()
        } else {
            requestLocationPermission()
            // Show default location
            val defaultLocation = GeoPoint(22.5726, 88.3639)
            binding.mapView.controller.setCenter(defaultLocation)
            updateMarker(defaultLocation)
            reverseGeocode(defaultLocation)
        }
    }

    private fun setupSearchView() {
        // Add text change listener for real-time search
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                if (query.length >= 3) {
                    searchRunnable?.let { searchHandler.removeCallbacks(it) }
                    searchRunnable = Runnable {
                        searchAddress(query)
                    }
                    searchHandler.postDelayed(searchRunnable!!, 500)
                } else {
                    suggestionsList.clear()
                    binding.etSearch.setAdapter(null)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Handle focus changes - dismiss dropdown when focus is lost
        binding.etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                binding.etSearch.dismissDropDown()
            }
        }
    }

    private fun searchAddress(query: String) {
        lifecycleScope.launch {
            try {
                binding.etSearch.hint = "Searching..."
                binding.progressBarSearch.visibility = View.VISIBLE

                val results = RetrofitClient.instance.searchAddress(
                    query = query,
                    limit = 5
                )

                suggestionsList.clear()
                suggestionsList.addAll(results)

                if (results.isNotEmpty()) {
                    val displayNames = results.map { it.display_name }.toTypedArray()

                    val adapter = android.widget.ArrayAdapter(
                        this@MapLocationPickerActivity,
                        android.R.layout.simple_dropdown_item_1line,
                        displayNames
                    )
                    binding.etSearch.setAdapter(adapter)
                    binding.etSearch.showDropDown()

                    // Clear previous item click listener to avoid duplicate
                    binding.etSearch.onItemClickListener = null

                    // Handle item selection
                    binding.etSearch.setOnItemClickListener { _, _, position, _ ->
                        val selectedResult = results[position]

                        // Show the selected address in the search bar
                        binding.etSearch.setText(selectedResult.display_name)

                        // Move map to selected location
                        moveToLocation(selectedResult)

                        // Dismiss dropdown and hide keyboard
                        binding.etSearch.dismissDropDown()
                        binding.etSearch.clearFocus()

                        // Hide keyboard
                        val imm = getSystemService(android.app.Activity.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
                    }
                } else {
                    Toast.makeText(this@MapLocationPickerActivity,
                        "No locations found. Try a different search term.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MapLocationPickerActivity,
                    "Error searching: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.etSearch.hint = "Search for address, landmark, or location..."
                binding.progressBarSearch.visibility = View.GONE
            }
        }
    }

    private fun moveToLocation(result: NominatimResult) {
        try {
            val lat = result.lat.toDouble()
            val lon = result.lon.toDouble()
            val location = GeoPoint(lat, lon)

            selectedLocation = location
            updateMarker(location)

            selectedAddress = result.display_name
            binding.tvSelectedAddress.text = selectedAddress

            binding.mapView.controller.animateTo(location)
            binding.mapView.controller.setZoom(16.0)

            Toast.makeText(this, "Location selected", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error parsing location", Toast.LENGTH_SHORT).show()
        }
    }

    // FIXED: Reverse geocode to get proper address from coordinates
    private fun reverseGeocode(latLng: GeoPoint) {
        if (isReversingGeocoding) return

        lifecycleScope.launch {
            try {
                isReversingGeocoding = true
                binding.tvSelectedAddress.text = "Getting address..."

                // Call Nominatim reverse geocoding API
                val address = getAddressFromCoordinates(latLng.latitude, latLng.longitude)

                if (address != null && address.isNotEmpty()) {
                    selectedAddress = address
                    binding.tvSelectedAddress.text = address
                } else {
                    // Fallback to coordinates if reverse geocoding fails
                    selectedAddress = String.format(
                        "📍 Lat: %.6f, Lng: %.6f\n(Long press to select or search above)",
                        latLng.latitude,
                        latLng.longitude
                    )
                    binding.tvSelectedAddress.text = selectedAddress
                }
            } catch (e: Exception) {
                e.printStackTrace()
                selectedAddress = String.format(
                    "📍 Lat: %.6f, Lng: %.6f\n(Tap confirm to use this location)",
                    latLng.latitude,
                    latLng.longitude
                )
                binding.tvSelectedAddress.text = selectedAddress
            } finally {
                isReversingGeocoding = false
            }
        }
    }

    // Function to get address from coordinates using Nominatim
    private suspend fun getAddressFromCoordinates(lat: Double, lon: Double): String? {
        return try {
            val result = RetrofitClient.instance.reverseGeocode(
                lat = lat,
                lon = lon,
                format = "json"
            )
            result?.display_name
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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
                    putExtra("latitude", selectedLocation!!.latitude)
                    putExtra("longitude", selectedLocation!!.longitude)
                    putExtra(EXTRA_SELECTED_ADDRESS, selectedAddress)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            } else {
                Toast.makeText(this, "Please select a location on the map or search for an address", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateMarker(latLng: GeoPoint) {
        selectedMarker?.let {
            binding.mapView.overlays.remove(it)
        }

        selectedMarker = Marker(binding.mapView).apply {
            position = latLng
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Pickup Location"
            subDescription = "Tap confirm to use this location"
            binding.mapView.overlays.add(this)
        }

        binding.mapView.controller.animateTo(latLng)
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        if (checkLocationPermission()) {
            binding.tvSelectedAddress.text = "Getting your location..."

            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    location?.let {
                        val currentLocation = GeoPoint(it.latitude, it.longitude)
                        selectedLocation = currentLocation
                        updateMarker(currentLocation)
                        reverseGeocode(currentLocation) // This will get the address
                        binding.mapView.controller.setCenter(currentLocation)
                        binding.mapView.controller.setZoom(16.0)
                    } ?: run {
                        Toast.makeText(this, "Unable to get current location", Toast.LENGTH_SHORT).show()
                        val defaultLocation = GeoPoint(22.5726, 88.3639)
                        selectedLocation = defaultLocation
                        updateMarker(defaultLocation)
                        reverseGeocode(defaultLocation)
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error getting location: ${e.message}", Toast.LENGTH_SHORT).show()
                    val defaultLocation = GeoPoint(22.5726, 88.3639)
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
                getCurrentLocation()
            } else {
                Toast.makeText(this, "Location permission required to select pickup location", Toast.LENGTH_SHORT).show()
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