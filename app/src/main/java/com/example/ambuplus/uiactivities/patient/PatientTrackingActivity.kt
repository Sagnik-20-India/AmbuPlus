package com.example.ambuplus.uiactivities.patient

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.ambuplus.databinding.ActivityPatientTrackingBinding
import com.example.ambuplus.uiactivities.main.MainActivity
import com.example.ambuplus.utils.RoutingHelper
import com.example.ambuplus.utils.ServiceLocator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class PatientTrackingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPatientTrackingBinding
    private lateinit var routingHelper: RoutingHelper
    private lateinit var driverName: String
    private lateinit var driverPhone: String
    private lateinit var driverVehicleNumber: String
    private lateinit var driverVehicleType: String

    private var ambulanceLocation: GeoPoint? = null
    private var pickupLocation: GeoPoint? = null
    private var ambulanceMarker: Marker? = null
    private var requestId: Int = -1
    private var driverId: String? = null
    private var isTrackingActive = true
    private var currentRoad: Road? = null
    private var isArrivalNotified = false
    private var isEmergencyMode = false
    private var isWaitingForDriver = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(
            applicationContext,
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )

        binding = ActivityPatientTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        routingHelper = RoutingHelper(this)

        // Get request data
        requestId = intent.getIntExtra("request_id", -1)
        val pickupLat = intent.getDoubleExtra("pickup_lat", 0.0)
        val pickupLng = intent.getDoubleExtra("pickup_lng", 0.0)
        val patientName = intent.getStringExtra("patient_name") ?: "Patient"
        driverId = intent.getStringExtra("driver_id")

        binding.tvPatientName.text = "Patient: $patientName"

        // Show emergency indicator if in emergency mode
        if (isEmergencyMode) {
            binding.tvStatus.text = "🚨 EMERGENCY REQUEST - Ambulance is on the way!"
            binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#F44336"))
        }

        if (pickupLat != 0.0 && pickupLng != 0.0) {
            pickupLocation = GeoPoint(pickupLat, pickupLng)
        }

        setupMap()
        setupClickListeners()

//        if (driverId != null && driverId!!.isNotEmpty()) {
//            startAmbulanceTracking()
//        } else {
//            checkRequestStatusAndStartTracking()
//        }
        checkRequestStatusAndStartTracking()
        // Start polling for driver acceptance
        startPollingForDriver()
    }

    private fun setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.setBuiltInZoomControls(true)

        // Add pickup location marker
        pickupLocation?.let {
            val pickupMarker = Marker(binding.mapView)
            pickupMarker.position = it
            pickupMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            pickupMarker.title = "Your Pickup Location"
            binding.mapView.overlays.add(pickupMarker)

            binding.mapView.controller.setCenter(it)
            binding.mapView.controller.setZoom(15.0)
        }

        // Add ambulance marker
        ambulanceMarker = Marker(binding.mapView)
        ambulanceMarker?.position = pickupLocation ?: GeoPoint(0.0, 0.0)
        ambulanceMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        ambulanceMarker?.title = "Ambulance Location"
        binding.mapView.overlays.add(ambulanceMarker)
    }

    private fun setupClickListeners() {
        // Recenter Map button - centers map on ambulance location
        binding.btnReCenter.setOnClickListener {
            if (ambulanceLocation != null) {
                binding.mapView.controller.animateTo(ambulanceLocation)
                binding.mapView.controller.setZoom(16.0)
                Toast.makeText(this, "Map centered on ambulance", Toast.LENGTH_SHORT).show()
            } else if (pickupLocation != null) {
                binding.mapView.controller.animateTo(pickupLocation)
                Toast.makeText(this, "Waiting for ambulance location...", Toast.LENGTH_SHORT).show()
            }
        }

        // Cancel Request button
        binding.btnCancelRequest.setOnClickListener {
            showCancelConfirmationDialog()
        }

        // Exit button - goes back to MainActivity without canceling request
        binding.btnExit.setOnClickListener {
            Toast.makeText(this, "You can track your ambulance from the main screen", Toast.LENGTH_LONG).show()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
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

    private fun cancelRequest() {
        lifecycleScope.launch {
            try {
                ServiceLocator.requestRepository.updateRequestStatus(requestId, "cancelled")

                // Clear saved emergency request ID if in emergency mode
                if (isEmergencyMode) {
                    val prefs = getSharedPreferences("EmergencyPrefs", Context.MODE_PRIVATE)
                    prefs.edit().remove("emergency_active_request_id").apply()
                }

                Toast.makeText(this@PatientTrackingActivity,
                    "Request cancelled successfully", Toast.LENGTH_LONG).show()

                isTrackingActive = false

                val intent = Intent(this@PatientTrackingActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                Toast.makeText(this@PatientTrackingActivity,
                    "Failed to cancel request: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkRequestStatusAndStartTracking() {
        lifecycleScope.launch {
            try {
                val request = ServiceLocator.requestRepository.getRequestById(requestId)
                when (request?.status) {
                    "pending" -> {
                        binding.tvStatus.text = "⏳ Waiting for a driver to accept your request..."
                        binding.tvEta.text = "⏱️ ETA: Finding driver..."
                        binding.tvDistance.text = "📏 Distance: -- km"
                        pollForDriverAcceptance()
                    }
                    "accepted" -> {
                        driverId = request.driverId
                        binding.tvStatus.text = "✅ Driver assigned! Ambulance is on the way!"
                        fetchDriverDetails()
                        startAmbulanceTracking()
                    }
                    "cancelled" -> {
                        binding.tvStatus.text = "❌ Request has been cancelled"
                        Toast.makeText(this@PatientTrackingActivity, "This request has been cancelled", Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        binding.tvStatus.text = "❌ No active request found"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startPollingForDriver() {
        lifecycleScope.launch {
            Log.d("PATIENT", "Starting to poll for driver acceptance...")

            while (isTrackingActive && isWaitingForDriver) {
                try {
                    val request = ServiceLocator.requestRepository.getRequestById(requestId)
                    Log.d("PATIENT", "Polling - Request status: ${request?.status}, Driver ID: ${request?.driverId}")

                    if (request?.status == "accepted") {
                        driverId = request.driverId
                        Log.d("PATIENT", "✅ Driver accepted! Driver ID: $driverId")
                        isWaitingForDriver = false

                        binding.tvStatus.text = if (isEmergencyMode) {
                            "🚨 EMERGENCY - Driver assigned! Ambulance is on the way!"
                        } else {
                            "✅ Driver assigned! Ambulance is on the way!"
                        }

                        fetchDriverDetails()
                        startAmbulanceTracking()
                        break
                    } else if (request?.status == "cancelled") {
                        Log.d("PATIENT", "Request was cancelled")
                        binding.tvStatus.text = "❌ Request has been cancelled"
                        isTrackingActive = false
                        break
                    }

                    // Update waiting message with animation effect
                    if (isWaitingForDriver) {
                        val waitingMessage = if (isEmergencyMode) {
                            "🚨 EMERGENCY - Searching for nearby drivers..."
                        } else {
                            "⏳ Waiting for a driver to accept..."
                        }
                        binding.tvStatus.text = waitingMessage
                        binding.tvEta.text = "⏱️ Finding nearest driver..."
                        binding.tvDistance.text = "📏 Waiting for driver..."
                    }

                    delay(3000) // Check every 3 seconds
                } catch (e: Exception) {
                    Log.e("PATIENT", "Polling error: ${e.message}")
                }
            }
        }
    }

    private fun fetchDriverDetails() {
        lifecycleScope.launch {
            try {
                if (driverId != null && driverId!!.isNotEmpty()) {
                    Log.d("PATIENT", "Fetching driver details for ID: $driverId")

                    val driver = ServiceLocator.driverRepository.getDriverById(driverId!!)

                    if (driver != null) {
                        driverName = driver.name
                        driverPhone = driver.phone
                        driverVehicleNumber = driver.vehicleNumber
                        driverVehicleType = driver.vehicleType

                        updateDriverInfoUI()

                        // Show the driver info card
                        binding.cardDriverInfo.visibility = android.view.View.VISIBLE

                        Log.d("PATIENT", "Driver details loaded: $driverName, $driverPhone")
                    } else {
                        Log.e("PATIENT", "Driver not found for ID: $driverId")
                    }
                }
            } catch (e: Exception) {
                Log.e("PATIENT", "Error fetching driver details: ${e.message}")
            }
        }
    }

    // ADDED: Function to update UI with driver info
    private fun updateDriverInfoUI() {
        binding.tvDriverName.text = "👨‍✈️ $driverName"
        binding.tvDriverPhone.text = "📞 $driverPhone"
        binding.tvDriverVehicle.text = "🚐 $driverVehicleNumber ($driverVehicleType)"
    }

    private fun pollForDriverAcceptance() {
        lifecycleScope.launch {
            while (isTrackingActive) {
                delay(5000)
                try {
                    val request = ServiceLocator.requestRepository.getRequestById(requestId)
                    if (request?.status == "accepted") {
                        driverId = request.driverId
                        binding.tvStatus.text = "✅ Driver assigned! Ambulance is on the way!"
                        fetchDriverDetails()
                        startAmbulanceTracking()
                        break
                    } else if (request?.status == "cancelled") {
                        binding.tvStatus.text = "❌ Request has been cancelled"
                        break
                    }
                } catch (e: Exception) {
                    // Handle error
                }
            }
        }
    }

    private fun startAmbulanceTracking() {
        binding.tvStatus.text = "🚑 Ambulance is on the way!"
        lifecycleScope.launch {
            while (isTrackingActive) {
                fetchAmbulanceLocation()
                delay(10000) // Update every 10 seconds
            }
        }
    }

    private fun fetchAmbulanceLocation() {
        lifecycleScope.launch {
            try {
                Log.d("PATIENT", "Driver ID: $driverId")
                if (driverId != null && driverId!!.isNotEmpty()) {
                    Log.d("PATIENT", "Fetching location for driver: $driverId")

                    val driver = ServiceLocator.driverRepository.getDriverById(driverId!!)
                    val locationString = driver?.currentLocation

                    Log.d("PATIENT", "Raw location string: $locationString")

                    locationString?.let {
                        val coords = it.split(",")
                        if (coords.size == 2) {
                            val lat = coords[0].toDoubleOrNull()
                            val lng = coords[1].toDoubleOrNull()
                            if (lat != null && lng != null) {
                                Log.d("PATIENT", "📍 Ambulance at: $lat, $lng")
                                updateAmbulanceLocation(GeoPoint(lat, lng))
                            } else {
                                Log.e("PATIENT", "Invalid coordinates: $it")
                            }
                        }
                    }
                } else {
                    Log.e("PATIENT", "Driver ID is null or empty")
                }
            } catch (e: Exception) {
                Log.e("PATIENT", "Error fetching location: ${e.message}")
            }
        }
    }

    private fun updateAmbulanceLocation(newLocation: GeoPoint) {
        ambulanceLocation = newLocation
        ambulanceMarker?.position = newLocation
        drawRoute()
        binding.mapView.controller.animateTo(newLocation)

        // Calculate and show distance immediately
        if (pickupLocation != null) {
            val distanceMeters = calculateDistance(ambulanceLocation!!, pickupLocation!!)
            if (distanceMeters < 100) {
                binding.tvDistance.text = "📏 Distance: ${distanceMeters.toInt()} meters away"
            } else if (distanceMeters < 1000) {
                binding.tvDistance.text = "📏 Distance: ${String.format("%.0f", distanceMeters)} meters away"
            } else {
                binding.tvDistance.text = "📏 Distance: ${String.format("%.1f", distanceMeters / 1000)} km away"
            }

            val etaMinutes = (distanceMeters / 1000 / 40 * 60).toInt()
            binding.tvEta.text = "⏱️ ETA: ~${String.format("%.0f", etaMinutes.toDouble())} min"

            when {
                etaMinutes <= 2 -> binding.tvStatus.text = "🚨 Ambulance is very close! 🚨"
                etaMinutes <= 5 -> binding.tvStatus.text = "🟢 Ambulance arriving in $etaMinutes minutes"
                else -> binding.tvStatus.text = "🟡 Ambulance is $etaMinutes minutes away"
            }
        }
    }


    private fun drawRoute() {
        if (ambulanceLocation != null && pickupLocation != null) {
            lifecycleScope.launch {
                routingHelper.getRoute(
                    start = ambulanceLocation!!,
                    end = pickupLocation!!,
                    onSuccess = { road ->
                        currentRoad = road
                        routingHelper.drawRoadOnMap(binding.mapView, road)


                        // FIX: Get actual distance from road
//                        val distanceInMeters = road.mLength.toDouble()
//                        val distanceInKm = distanceInMeters / 1000.0
//                        val durationInMinutes = road.mDuration / 60.0

                        val distanceInKm = road.mLength
                        val distanceInMeters = distanceInKm * 1000
                        val durationInMinutes = road.mDuration / 60.0

                        // Show distance properly
//                        if (distanceInMeters < 100) {
//                            binding.tvDistance.text = "📏 Distance: ${distanceInMeters.toInt()} meters away"
//                        } else if (distanceInKm < 1) {
//                            binding.tvDistance.text = "📏 Distance: ${String.format("%.0f", distanceInMeters)} meters away"
//                        } else {
//                            binding.tvDistance.text = "📏 Distance: ${String.format("%.1f", distanceInKm)} km away"
//                        }
                        if (distanceInMeters < 100) {
                            binding.tvDistance.text = "📏 Distance: ${distanceInMeters.toInt()} meters"
                        } else if (distanceInKm < 1) {
                            binding.tvDistance.text = "📏 Distance: ${String.format("%.0f", distanceInMeters)} meters"
                        } else {
                            binding.tvDistance.text = "📏 Distance: ${String.format("%.1f", distanceInKm)} km"
                        }
                        binding.tvEta.text = "⏱️ ETA: ~${String.format("%.0f", durationInMinutes)} min"

                        when {
                            durationInMinutes <= 2 -> binding.tvStatus.text = "🚨 Ambulance is very close! 🚨"
                            durationInMinutes <= 5 -> binding.tvStatus.text = "🟢 Ambulance arriving in ${String.format("%.0f", durationInMinutes)} minutes"
                            else -> binding.tvStatus.text = "🟡 Ambulance is ${String.format("%.0f", durationInMinutes)} minutes away"
                        }

                        Log.d("PATIENT", "Distance: ${distanceInMeters}m, Duration: ${road.mDuration}s")
                    },


                        // USE FALLBACK DISTANCE CALCULATION INSTEAD OF road.mLength
//                        val distanceMeters = calculateDistance(ambulanceLocation!!, pickupLocation!!)
//                        val distanceInKm = distanceMeters / 1000.0
//                        val etaMinutes = (distanceMeters / 1000 / 40 * 60).toInt()
//
//                        binding.tvDistance.text = if (distanceMeters < 1000) {
//                            "📏 Distance: ${distanceMeters.toInt()} meters"
//                        } else {
//                            "📏 Distance: ${String.format("%.1f", distanceInKm)} km"
//                        }
//                        binding.tvEta.text = "⏱️ ETA: ~${String.format("%.0f", etaMinutes)} min"
//
//                        when {
//                            etaMinutes <= 2 -> binding.tvStatus.text = "🚨 Ambulance is very close! 🚨"
//                            etaMinutes <= 5 -> binding.tvStatus.text = "🟢 Ambulance arriving in ${String.format("%.0f", etaMinutes)} minutes"
//                            else -> binding.tvStatus.text = "🟡 Ambulance is ${String.format("%.0f", etaMinutes)} minutes away"
//                        }
//
//                        Log.d("PATIENT", "Distance: ${distanceMeters}m, Duration: ${road.mDuration}s")
//                    },


                    onError = { error ->
                        Log.e("PATIENT", "Routing error: $error")
                        drawStraightLine()
                    }
                )
            }
        }
    }

    private fun drawStraightLine() {
        if (ambulanceLocation != null && pickupLocation != null) {
            routingHelper.clearRouteFromMap(binding.mapView)

            val routeOverlay = Polyline()
            routeOverlay.addPoint(ambulanceLocation)
            routeOverlay.addPoint(pickupLocation)
            routeOverlay.color = Color.BLUE
            routeOverlay.width = 8f
            binding.mapView.overlays.add(routeOverlay)

            val distance = calculateDistance(ambulanceLocation!!, pickupLocation!!)
            val etaMinutes = (distance / 40 * 60).toInt()

            binding.tvEta.text = "⏱️ ETA: ~$etaMinutes min (approx)"
            binding.tvDistance.text = "📏 Distance: ${String.format("%.1f", distance)} km away"

            when {
                etaMinutes <= 2 -> binding.tvStatus.text = "🚨 Ambulance is very close! 🚨"
                etaMinutes <= 5 -> binding.tvStatus.text = "🟢 Ambulance arriving in $etaMinutes minutes"
                else -> binding.tvStatus.text = "🟡 Ambulance is $etaMinutes minutes away"
            }
        }
    }

    private fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Double {
        val lat1 = Math.toRadians(point1.latitude)
        val lat2 = Math.toRadians(point2.latitude)
        val dLat = Math.toRadians(point2.latitude - point1.latitude)
        val dLon = Math.toRadians(point2.longitude - point1.longitude)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return 6371 * c
    }

    private fun checkIfAmbulanceArrived() {
        if (ambulanceLocation != null && pickupLocation != null) {
            val distance = calculateDistance(ambulanceLocation!!, pickupLocation!!)
            if (distance < 0.05 && !isArrivalNotified) {
                isArrivalNotified = true
                showArrivalNotification()
            }
        }
    }

    private fun showArrivalNotification() {
        AlertDialog.Builder(this)
            .setTitle("Ambulance Arrived")
            .setMessage("Your ambulance has arrived at your location!")
            .setPositiveButton("OK") { _, _ ->
                // Just dismiss
            }
            .show()

        binding.tvStatus.text = "✅ Ambulance has arrived!"
        binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
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