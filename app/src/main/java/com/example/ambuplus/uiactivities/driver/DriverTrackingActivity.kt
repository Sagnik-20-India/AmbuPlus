package com.example.ambuplus.uiactivities.driver

import android.Manifest
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.UUID
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.ambuplus.uiactivities.main.MainActivity
import com.example.ambuplus.databinding.ActivityDriverTrackingBinding
import com.example.ambuplus.utils.RoutingHelper
import com.example.ambuplus.utils.ServiceLocator
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class DriverTrackingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDriverTrackingBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var myLocationOverlay: MyLocationNewOverlay
    private lateinit var destinationMarker: Marker
    private lateinit var sharedPreferences: android.content.SharedPreferences
    private lateinit var routingHelper: RoutingHelper
    private var currentLocation: GeoPoint? = null
    private var destinationLocation: GeoPoint? = null
    private var requestId: Int = -1
    private var driverId: String? = null
    private var patientName: String = ""
    private var patientAddress: String = ""
    private var contactNumber: String = ""
    private var isTrackingActive = true
    private var currentRoad: Road? = null
    private var acceptanceTime: Long = 0
    private var patientEmergencyLevel: String = "medium"

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
        private const val PREFS_NAME = "AmbuPlus"
        private const val KEY_ACTIVE_DRIVER_REQUEST_ID = "active_driver_request_id"
        private const val KEY_ACTIVE_DRIVER_ID = "active_driver_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(
            applicationContext,
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )

        binding = ActivityDriverTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        routingHelper = RoutingHelper(this)

        if (savedInstanceState != null) {
            restoreState(savedInstanceState)
        } else {
            loadIntentData()
        }

        // Store acceptance time for cancellation logic
        acceptanceTime = System.currentTimeMillis()
        patientEmergencyLevel = intent.getStringExtra("emergency_level") ?: "medium"

        if (requestId == -1) {
            loadSavedRequestFromPreferences()
        }

        lifecycleScope.launch {
            val currentUser = ServiceLocator.authRepository.currentUser()
            if (currentUser != null) {
                val driver = ServiceLocator.driverRepository.getDriverByUserId(currentUser.id)
                driverId = driver?.id
                println("DEBUG: DriverTracking - Driver ID: $driverId")

                if (driverId != null && requestId != -1) {
                    verifyRequestStillActive()
                } else if (requestId != -1) {
                    loadRequestFromDatabase()
                }
            }
        }

        setupMap()
        checkLocationPermission()
        startLocationUpdates()
        startPatientCancellationMonitoring()
        setupClickListeners()
        displayPatientInfo()
    }

    private fun loadSavedRequestFromPreferences() {
        val savedRequestId = sharedPreferences.getInt(KEY_ACTIVE_DRIVER_REQUEST_ID, -1)
        if (savedRequestId != -1) {
            println("DEBUG: DriverTracking - Found saved request ID: $savedRequestId")
            requestId = savedRequestId
            lifecycleScope.launch {
                loadRequestFromDatabase()
            }
        }
    }

    private suspend fun loadRequestFromDatabase() {
        try {
            val request = ServiceLocator.requestRepository.getRequestById(requestId)
            if (request != null && request.status == "accepted") {
                patientName = request.patientName ?: "Patient"
                patientAddress = request.location.split("|").firstOrNull() ?: "Location"
                contactNumber = request.contactNumber ?: ""
                patientEmergencyLevel = request.emergencyLevel ?: "medium"
                val coords = extractCoordinates(request.location)
                if (coords != null) {
                    destinationLocation = GeoPoint(coords.first, coords.second)
                }
                displayPatientInfo()
                setupMap()
            }
        } catch (e: Exception) {
            println("DEBUG: DriverTracking - Error loading request: ${e.message}")
        }
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

    private fun restoreState(savedInstanceState: Bundle) {
        requestId = savedInstanceState.getInt("request_id", -1)
        patientName = savedInstanceState.getString("patient_name", "")
        patientAddress = savedInstanceState.getString("patient_address", "")
        contactNumber = savedInstanceState.getString("contact_number", "")
        patientEmergencyLevel = savedInstanceState.getString("emergency_level", "medium")
        val patientLat = savedInstanceState.getDouble("patient_lat", 0.0)
        val patientLng = savedInstanceState.getDouble("patient_lng", 0.0)
        if (patientLat != 0.0 && patientLng != 0.0) {
            destinationLocation = GeoPoint(patientLat, patientLng)
        }
    }

    private fun loadIntentData() {
        requestId = intent.getIntExtra("request_id", -1)
        patientName = intent.getStringExtra("patient_name") ?: "Patient"
        patientAddress = intent.getStringExtra("patient_address") ?: "Location"
        contactNumber = intent.getStringExtra("contact_number") ?: ""
        patientEmergencyLevel = intent.getStringExtra("emergency_level") ?: "medium"
        val patientLat = intent.getDoubleExtra("patient_lat", 0.0)
        val patientLng = intent.getDoubleExtra("patient_lng", 0.0)

        if (patientLat != 0.0 && patientLng != 0.0) {
            destinationLocation = GeoPoint(patientLat, patientLng)
        }

        if (requestId != -1) {
            sharedPreferences.edit().putInt(KEY_ACTIVE_DRIVER_REQUEST_ID, requestId).apply()
        }
    }

    private suspend fun verifyRequestStillActive() {
        try {
            val request = ServiceLocator.requestRepository.getRequestById(requestId)
            if (request == null) {
                Toast.makeText(this, "This trip is no longer active", Toast.LENGTH_LONG).show()
                isTrackingActive = false
                clearSavedRequest()
                // Navigate to DriverRequestsActivity
                val intent = Intent(this, DriverRequestsActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            } else if (request.status == "cancelled" || request.status == "cancelled_by_patient") {
                Toast.makeText(this, "Request was cancelled by patient", Toast.LENGTH_LONG).show()
                isTrackingActive = false
                clearSavedRequest()
                runOnUiThread {
                    showPatientCancelledDialog()
                }
            } else if (request.status != "accepted") {
                Toast.makeText(this, "This trip is no longer active", Toast.LENGTH_LONG).show()
                isTrackingActive = false
                clearSavedRequest()
                // Navigate to DriverRequestsActivity
                val intent = Intent(this, DriverRequestsActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            } else {
                patientName = request.patientName ?: patientName
                patientAddress = request.location.split("|").firstOrNull() ?: patientAddress
                contactNumber = request.contactNumber ?: contactNumber
                patientEmergencyLevel = request.emergencyLevel ?: "medium"
                displayPatientInfo()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun clearSavedRequest() {
        sharedPreferences.edit().remove(KEY_ACTIVE_DRIVER_REQUEST_ID).apply()
        sharedPreferences.edit().remove(KEY_ACTIVE_DRIVER_ID).apply()
    }

    private fun setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.setBuiltInZoomControls(true)

        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), binding.mapView)
        myLocationOverlay.enableMyLocation()
        myLocationOverlay.enableFollowLocation()
        binding.mapView.overlays.add(myLocationOverlay)

        destinationLocation?.let {
            destinationMarker = Marker(binding.mapView)
            destinationMarker.position = it
            destinationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            destinationMarker.title = "Patient Pickup Location"
            destinationMarker.subDescription = patientAddress
            binding.mapView.overlays.add(destinationMarker)

            val centerLat = (it.latitude + (currentLocation?.latitude ?: it.latitude)) / 2
            val centerLng = (it.longitude + (currentLocation?.longitude ?: it.longitude)) / 2
            binding.mapView.controller.setCenter(GeoPoint(centerLat, centerLng))
            binding.mapView.controller.setZoom(14.0)
        }
    }

    private fun displayPatientInfo() {
        binding.tvPatientName.text = "Patient: $patientName"
        binding.tvContactNumber.text = "📞 $contactNumber"
        binding.tvDestinationAddress.text = "📍 $patientAddress"
    }

    private fun setupClickListeners() {
        binding.btnReCenter.setOnClickListener {
            currentLocation?.let {
                binding.mapView.controller.animateTo(it)
                binding.mapView.controller.setZoom(16.0)
                Toast.makeText(this, "Map centered on your location", Toast.LENGTH_SHORT).show()
            } ?: run {
                Toast.makeText(this, "Waiting for location...", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnExit.setOnClickListener {
            Toast.makeText(this, "Tracking continues in background. You can resume from View Requests.", Toast.LENGTH_LONG).show()
            finish()
        }

        // Cancel Trip Button
        binding.btnCancelTrip.setOnClickListener {
            showCancelTripDialog()
        }
    }

    private fun showCancelTripDialog() {
        val currentDistance = if (currentLocation != null && destinationLocation != null) {
            calculateDistance(currentLocation!!, destinationLocation!!)
        } else {
            100.0
        }

        val timeSinceAcceptance = (System.currentTimeMillis() - acceptanceTime) / 1000 / 60
        val isHighEmergency = patientEmergencyLevel == "high"
        val isNearPatient = currentDistance < 0.5

        when {
            isHighEmergency -> {
                AlertDialog.Builder(this)
                    .setTitle("Cannot Cancel Emergency Trip")
                    .setMessage("This is a HIGH PRIORITY emergency request. Canceling could put the patient at serious risk.\n\nPlease complete this trip.")
                    .setPositiveButton("Continue Trip", null)
                    .show()
                return
            }
            isNearPatient -> {
                AlertDialog.Builder(this)
                    .setTitle("Almost There!")
                    .setMessage("You are very close to the patient (${String.format("%.1f", currentDistance)} km). Please complete the trip.")
                    .setPositiveButton("Continue", null)
                    .show()
                return
            }
            else -> {
                showCancellationReasonDialog()
            }
        }
    }

    private fun showCancellationReasonDialog() {
        val reasons = arrayOf(
            "🚗 Vehicle issue",
            "🛑 Personal emergency",
            "📍 Wrong address",
            "👤 Patient not responding",
            "🚨 Other emergency"
        )

        AlertDialog.Builder(this)
            .setTitle("Cancel Trip - Select Reason")
            .setItems(reasons) { _, which ->
                val reason = reasons[which]
                confirmCancellation(reason)
            }
            .setNegativeButton("Go Back", null)
            .show()
    }

    private fun confirmCancellation(reason: String) {
        val timeSinceAcceptance = (System.currentTimeMillis() - acceptanceTime) / 1000 / 60
        val willPenalize = timeSinceAcceptance > 2

        val message = if (willPenalize) {
            "⚠️ Warning: Cancelling after ${timeSinceAcceptance} minutes will affect your reliability score.\n\nReason: $reason\n\nAre you sure?"
        } else {
            "Cancel trip?\n\nReason: $reason"
        }

        AlertDialog.Builder(this)
            .setTitle("Confirm Cancellation")
            .setMessage(message)
            .setPositiveButton("Yes, Cancel") { _, _ ->
                executeCancellation(reason, willPenalize)
            }
            .setNegativeButton("No, Continue", null)
            .show()
    }

//    private fun executeCancellation(reason: String, penalize: Boolean) {
//        lifecycleScope.launch {
//            try {
//                ServiceLocator.requestRepository.updateRequestStatus(requestId, "cancelled_by_driver", driverId)
//
//                Toast.makeText(this@DriverTrackingActivity,
//                    "Trip cancelled. ${if (penalize) "This will affect your reliability score." else ""}",
//                    Toast.LENGTH_LONG).show()
//
//                clearSavedRequest()
//                finish()
//            } catch (e: Exception) {
//                Toast.makeText(this@DriverTrackingActivity, "Failed to cancel: ${e.message}", Toast.LENGTH_LONG).show()
//            }
//        }
//    }

    private fun executeCancellation(reason: String, penalize: Boolean) {
        lifecycleScope.launch {
            try {
                ServiceLocator.requestRepository.updateRequestStatus(requestId, "cancelled_by_driver", driverId)

                // ✅ DO NOT increment emergency counter here - it's driver's fault

                Toast.makeText(this@DriverTrackingActivity,
                    "Trip cancelled. ${if (penalize) "This will affect your reliability score." else ""}",
                    Toast.LENGTH_LONG).show()

                clearSavedRequest()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@DriverTrackingActivity, "Failed to cancel: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showPatientCancelledDialog() {
        AlertDialog.Builder(this)
            .setTitle("Request Cancelled by Patient")
            .setMessage("The patient has cancelled this request.")
            .setPositiveButton("OK") { _, _ ->
                clearSavedRequest()
                // Navigate to DriverRequestsActivity instead of just finishing
                val intent = Intent(this, DriverRequestsActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun startPatientCancellationMonitoring() {
        lifecycleScope.launch {
            while (isTrackingActive) {
                delay(5000) // Check every 5 seconds
                try {
                    val request = ServiceLocator.requestRepository.getRequestById(requestId)
                    if (request?.status == "cancelled" || request?.status == "cancelled_by_patient") {
                        Log.d("DRIVER", "Patient cancelled the request")
                        runOnUiThread {
                            showPatientCancelledDialog()
                        }
                        break
                    }
                } catch (e: Exception) {
                    Log.e("DRIVER", "Error checking request status: ${e.message}")
                }
            }
        }
    }

    private fun startLocationUpdates() {
        lifecycleScope.launch {
            while (true) {
                if (isTrackingActive) {
                    getCurrentLocation()
                    updateDriverLocationInDatabase()
                }
                delay(3000)
            }
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                currentLocation = GeoPoint(it.latitude, it.longitude)
                drawRoute()
                checkIfReachedDestination()
            }
        }
    }

    private fun drawRoute() {
        if (currentLocation != null && destinationLocation != null) {
            lifecycleScope.launch {
                routingHelper.getRoute(
                    start = currentLocation!!,
                    end = destinationLocation!!,
                    onSuccess = { road ->
                        currentRoad = road
                        routingHelper.drawRoadOnMap(binding.mapView, road)

                        Log.d("ROUTE_DEBUG", "road.mLength = ${road.mLength}")
                        Log.d("ROUTE_DEBUG", "road.mDuration = ${road.mDuration}")
                        Log.d("ROUTE_DEBUG", "road.mStatus = ${road.mStatus}")

                        val distanceInKm = road.mLength
                        val distanceInMeters = distanceInKm * 1000
                        val durationInMinutes = road.mDuration / 60.0

                        Log.d("ROUTE_DEBUG", "distanceInMeters = $distanceInMeters")
                        Log.d("ROUTE_DEBUG", "distanceInKm = $distanceInKm")

                        if (distanceInMeters < 100) {
                            binding.tvDistance.text = "📏 Distance: ${distanceInMeters.toInt()} meters"
                        } else if (distanceInKm < 1) {
                            binding.tvDistance.text = "📏 Distance: ${String.format("%.0f", distanceInMeters)} meters"
                        } else {
                            binding.tvDistance.text = "📏 Distance: ${String.format("%.1f", distanceInKm)} km"
                        }
                        binding.tvEta.text = "⏱️ ETA: ~${String.format("%.0f", durationInMinutes)} min"

                        when {
                            distanceInKm < 0.5 -> binding.tvStatus.text = "🟢 Very close! Almost there!"
                            distanceInKm < 2 -> binding.tvStatus.text = "🟡 Arriving in ${String.format("%.0f", durationInMinutes)} minutes"
                            else -> binding.tvStatus.text = "🔴 On the way to patient"
                        }

                        Log.d("TRACKING", "Distance: ${distanceInMeters}m, Duration: ${road.mDuration}s")
                    },
                    onError = { error ->
                        Log.e("TRACKING", "Routing error: $error")
                        drawStraightLine()
                    }
                )
            }
        }
    }

    private fun drawStraightLine() {
        if (currentLocation != null && destinationLocation != null) {
            routingHelper.clearRouteFromMap(binding.mapView)

            val routeOverlay = Polyline()
            routeOverlay.addPoint(currentLocation)
            routeOverlay.addPoint(destinationLocation)
            routeOverlay.color = Color.BLUE
            routeOverlay.width = 8f
            binding.mapView.overlays.add(routeOverlay)

            val distance = calculateDistance(currentLocation!!, destinationLocation!!)
            val etaMinutes = (distance / 40 * 60).toInt()

            binding.tvEta.text = "⏱️ ETA: ~$etaMinutes min (approx)"
            binding.tvDistance.text = "📏 Distance: ${String.format("%.1f", distance)} km (straight line)"

            when {
                distance < 0.5 -> binding.tvStatus.text = "🟢 Very close! Almost there!"
                distance < 2 -> binding.tvStatus.text = "🟡 Arriving in $etaMinutes minutes"
                else -> binding.tvStatus.text = "🔴 On the way to patient"
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

    private fun updateDriverLocationInDatabase() {
        lifecycleScope.launch {
            try {
                currentLocation?.let {
                    if (driverId != null && requestId != -1 && isTrackingActive) {
                        val locationString = "${it.latitude},${it.longitude}"
                        ServiceLocator.driverRepository.updateLiveLocation(driverId!!, locationString)
                        Log.d("DRIVER", "📍 Location saved: $locationString")
                    }
                }
            } catch (e: Exception) {
                Log.e("DRIVER", "Error updating location: ${e.message}")
            }
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        }
    }

    private fun checkIfReachedDestination() {
        if (currentLocation != null && destinationLocation != null) {
            val distance = calculateDistance(currentLocation!!, destinationLocation!!)
            if (distance < 0.05) {
                onDestinationReached()
            }
        }
    }

    private fun onDestinationReached() {
        if (!isTrackingActive) return

        isTrackingActive = false
        binding.tvStatus.text = "✅ Patient reached! Trip completed."
        binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
        binding.tvEta.text = "⏱️ Trip Completed"
        binding.tvDistance.text = "📏 Destination reached"

        AlertDialog.Builder(this)
            .setTitle("Trip Completed")
            .setMessage("You have successfully reached the patient location. Mark this trip as completed?")
            .setPositiveButton("Complete Trip") { _, _ ->
                completeTrip()
            }
            .setNegativeButton("Not Yet") { _, _ ->
                isTrackingActive = true
                binding.tvStatus.text = "🔴 On the way to patient"
                binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#E53935"))
                startLocationUpdates()
            }
            .setCancelable(false)
            .show()
    }

    private fun completeTrip() {
        lifecycleScope.launch {
            try {
                ServiceLocator.requestRepository.updateRequestStatus(requestId, "completed", driverId)

                // Count emergency request only when trip is completed
                val request = ServiceLocator.requestRepository.getRequestById(requestId)
                Log.d("EMERGENCY", "Request userId: ${request?.userId}")
                Log.d("EMERGENCY", "Checking against: bfca1d22-8d85-43b2-b825-fe2a7a582ad7")

                if (request?.userId == "bfca1d22-8d85-43b2-b825-fe2a7a582ad7") {
                    // This was an emergency request, increment counter
                    val prefs = getSharedPreferences("EmergencyPrefs", Context.MODE_PRIVATE)
                    val sessionId = getEmergencySessionId()
                    val usedCount = prefs.getInt("emergency_count_$sessionId", 0)
                    val newCount = usedCount + 1
                    prefs.edit().putInt("emergency_count_$sessionId", newCount).apply()
                    Log.d("EMERGENCY", "Emergency request completed! Count: $newCount/3")
                    Toast.makeText(this@DriverTrackingActivity,
                        "Emergency trip completed! Patient has used $newCount/3 emergency requests.",
                        Toast.LENGTH_LONG).show()
                } else {
                    Log.d("EMERGENCY", "Not an emergency request. UserId: ${request?.userId}")
                }

                clearSavedRequest()
                Toast.makeText(this@DriverTrackingActivity,
                    "Trip completed successfully! Thank you for your service.",
                    Toast.LENGTH_LONG).show()

                val intent = Intent(this@DriverTrackingActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Log.e("DRIVER", "Error completing trip: ${e.message}")
                Toast.makeText(this@DriverTrackingActivity,
                    "Error completing trip: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getEmergencySessionId(): String {
        val prefs = getSharedPreferences("EmergencyPrefs", MODE_PRIVATE)
        var sessionId = prefs.getString("emergency_session_id", null)
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString()
            prefs.edit().putString("emergency_session_id", sessionId).apply()
        }
        return sessionId
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupMap()
                startLocationUpdates()
            } else {
                Toast.makeText(this, "Location permission required for tracking", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("request_id", requestId)
        outState.putString("patient_name", patientName)
        outState.putString("patient_address", patientAddress)
        outState.putString("contact_number", contactNumber)
        outState.putString("emergency_level", patientEmergencyLevel)
        destinationLocation?.let {
            outState.putDouble("patient_lat", it.latitude)
            outState.putDouble("patient_lng", it.longitude)
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        if (driverId != null && requestId != -1) {
            lifecycleScope.launch {
                verifyRequestStillActive()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        isTrackingActive = false
    }
}













//package com.example.ambuplus.uiactivities.driver
//
//import android.Manifest
//import android.content.Context
//import android.content.Intent
//import android.util.Log
//import android.content.pm.PackageManager
//import android.graphics.Color
//import android.location.Location
//import android.os.Bundle
//import android.widget.Toast
//import androidx.appcompat.app.AlertDialog
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.app.ActivityCompat
//import androidx.core.content.ContextCompat
//import androidx.lifecycle.lifecycleScope
//import com.example.ambuplus.uiactivities.main.MainActivity
//import com.example.ambuplus.databinding.ActivityDriverTrackingBinding
//import com.example.ambuplus.utils.RoutingHelper
//import com.example.ambuplus.utils.ServiceLocator
//import com.google.android.gms.location.FusedLocationProviderClient
//import com.google.android.gms.location.LocationServices
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.launch
//import org.osmdroid.bonuspack.routing.Road
//import org.osmdroid.config.Configuration
//import org.osmdroid.tileprovider.tilesource.TileSourceFactory
//import org.osmdroid.util.GeoPoint
//import org.osmdroid.views.overlay.Marker
//import org.osmdroid.views.overlay.Polyline
//import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
//import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
//
//class DriverTrackingActivity : AppCompatActivity() {
//
//    private lateinit var binding: ActivityDriverTrackingBinding
//    private lateinit var fusedLocationClient: FusedLocationProviderClient
//    private lateinit var myLocationOverlay: MyLocationNewOverlay
//    private lateinit var destinationMarker: Marker
//    private lateinit var sharedPreferences: android.content.SharedPreferences
//    private lateinit var routingHelper: RoutingHelper
//    private var currentLocation: GeoPoint? = null
//    private var destinationLocation: GeoPoint? = null
//    private var requestId: Int = -1
//    private var driverId: String? = null
//    private var patientName: String = ""
//    private var patientAddress: String = ""
//    private var contactNumber: String = ""
//    private var isTrackingActive = true
//    private var currentRoad: Road? = null
//
//    companion object {
//        private const val LOCATION_PERMISSION_REQUEST = 1001
//        private const val PREFS_NAME = "AmbuPlus"
//        private const val KEY_ACTIVE_DRIVER_REQUEST_ID = "active_driver_request_id"
//        private const val KEY_ACTIVE_DRIVER_ID = "active_driver_id"
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        Configuration.getInstance().load(
//            applicationContext,
//            androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
//        )
//
//        binding = ActivityDriverTrackingBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
//        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
//        routingHelper = RoutingHelper(this)
//
//        if (savedInstanceState != null) {
//            restoreState(savedInstanceState)
//        } else {
//            loadIntentData()
//        }
//
//        if (requestId == -1) {
//            loadSavedRequestFromPreferences()
//        }
//
//        lifecycleScope.launch {
//            val currentUser = ServiceLocator.authRepository.currentUser()
//            if (currentUser != null) {
//                val driver = ServiceLocator.driverRepository.getDriverByUserId(currentUser.id)
//                driverId = driver?.id
//                println("DEBUG: DriverTracking - Driver ID: $driverId")
//
//                if (driverId != null && requestId != -1) {
//                    verifyRequestStillActive()
//                } else if (requestId != -1) {
//                    loadRequestFromDatabase()
//                }
//            }
//        }
//
//        setupMap()
//        checkLocationPermission()
//        startLocationUpdates()
//        setupClickListeners()
//        displayPatientInfo()
//
//    }
//
//    private fun loadSavedRequestFromPreferences() {
//        val savedRequestId = sharedPreferences.getInt(KEY_ACTIVE_DRIVER_REQUEST_ID, -1)
//        if (savedRequestId != -1) {
//            println("DEBUG: DriverTracking - Found saved request ID: $savedRequestId")
//            requestId = savedRequestId
//            lifecycleScope.launch {
//                loadRequestFromDatabase()
//            }
//        }
//    }
//
//    private suspend fun loadRequestFromDatabase() {
//        try {
//            val request = ServiceLocator.requestRepository.getRequestById(requestId)
//            if (request != null && request.status == "accepted") {
//                patientName = request.patientName ?: "Patient"
//                patientAddress = request.location.split("|").firstOrNull() ?: "Location"
//                contactNumber = request.contactNumber ?: ""
//                val coords = extractCoordinates(request.location)
//                if (coords != null) {
//                    destinationLocation = GeoPoint(coords.first, coords.second)
//                }
//                displayPatientInfo()
//                setupMap()
//            }
//        } catch (e: Exception) {
//            println("DEBUG: DriverTracking - Error loading request: ${e.message}")
//        }
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
//    private fun restoreState(savedInstanceState: Bundle) {
//        requestId = savedInstanceState.getInt("request_id", -1)
//        patientName = savedInstanceState.getString("patient_name", "")
//        patientAddress = savedInstanceState.getString("patient_address", "")
//        contactNumber = savedInstanceState.getString("contact_number", "")
//        val patientLat = savedInstanceState.getDouble("patient_lat", 0.0)
//        val patientLng = savedInstanceState.getDouble("patient_lng", 0.0)
//        if (patientLat != 0.0 && patientLng != 0.0) {
//            destinationLocation = GeoPoint(patientLat, patientLng)
//        }
//    }
//
//    private fun loadIntentData() {
//        requestId = intent.getIntExtra("request_id", -1)
//        patientName = intent.getStringExtra("patient_name") ?: "Patient"
//        patientAddress = intent.getStringExtra("patient_address") ?: "Location"
//        contactNumber = intent.getStringExtra("contact_number") ?: ""
//        val patientLat = intent.getDoubleExtra("patient_lat", 0.0)
//        val patientLng = intent.getDoubleExtra("patient_lng", 0.0)
//
//        if (patientLat != 0.0 && patientLng != 0.0) {
//            destinationLocation = GeoPoint(patientLat, patientLng)
//        }
//
//        if (requestId != -1) {
//            sharedPreferences.edit().putInt(KEY_ACTIVE_DRIVER_REQUEST_ID, requestId).apply()
//        }
//    }
//
//    private suspend fun verifyRequestStillActive() {
//        try {
//            val request = ServiceLocator.requestRepository.getRequestById(requestId)
//            if (request == null || request.status != "accepted") {
//                Toast.makeText(this, "This trip is no longer active", Toast.LENGTH_LONG).show()
//                isTrackingActive = false
//                clearSavedRequest()
//                finish()
//            } else {
//                patientName = request.patientName ?: patientName
//                patientAddress = request.location.split("|").firstOrNull() ?: patientAddress
//                contactNumber = request.contactNumber ?: contactNumber
//                displayPatientInfo()
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//    }
//
//    private fun clearSavedRequest() {
//        sharedPreferences.edit().remove(KEY_ACTIVE_DRIVER_REQUEST_ID).apply()
//        sharedPreferences.edit().remove(KEY_ACTIVE_DRIVER_ID).apply()
//    }
//
//    private fun setupMap() {
//        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
//        binding.mapView.setMultiTouchControls(true)
//        binding.mapView.setBuiltInZoomControls(true)
//
//        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), binding.mapView)
//        myLocationOverlay.enableMyLocation()
//        myLocationOverlay.enableFollowLocation()
//        binding.mapView.overlays.add(myLocationOverlay)
//
//        destinationLocation?.let {
//            destinationMarker = Marker(binding.mapView)
//            destinationMarker.position = it
//            destinationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
//            destinationMarker.title = "Patient Pickup Location"
//            destinationMarker.subDescription = patientAddress
//            binding.mapView.overlays.add(destinationMarker)
//
//            val centerLat = (it.latitude + (currentLocation?.latitude ?: it.latitude)) / 2
//            val centerLng = (it.longitude + (currentLocation?.longitude ?: it.longitude)) / 2
//            binding.mapView.controller.setCenter(GeoPoint(centerLat, centerLng))
//            binding.mapView.controller.setZoom(14.0)
//        }
//    }
//
//    private fun displayPatientInfo() {
//        binding.tvPatientName.text = "Patient: $patientName"
//        binding.tvContactNumber.text = "📞 $contactNumber"
//        binding.tvDestinationAddress.text = "📍 $patientAddress"
//    }
//
//    private fun setupClickListeners() {
//        binding.btnReCenter.setOnClickListener {
//            currentLocation?.let {
//                binding.mapView.controller.animateTo(it)
//                binding.mapView.controller.setZoom(16.0)
//                Toast.makeText(this, "Map centered on your location", Toast.LENGTH_SHORT).show()
//            } ?: run {
//                Toast.makeText(this, "Waiting for location...", Toast.LENGTH_SHORT).show()
//            }
//        }
//
//        binding.btnExit.setOnClickListener {
//            Toast.makeText(this, "Tracking continues in background. You can resume from View Requests.", Toast.LENGTH_LONG).show()
//            finish()
//        }
//    }
//
//    private fun startLocationUpdates() {
//        lifecycleScope.launch {
//            while (isTrackingActive) {
//                getCurrentLocation()
//                updateDriverLocationInDatabase()
//                delay(3000)
//            }
//        }
//    }
//
//    private fun getCurrentLocation() {
//        if (ActivityCompat.checkSelfPermission(
//                this,
//                Manifest.permission.ACCESS_FINE_LOCATION
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            return
//        }
//
//        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
//            location?.let {
//                currentLocation = GeoPoint(it.latitude, it.longitude)
//                drawRoute()
//                checkIfReachedDestination()  // ADD THIS LINE
//            }
//        }
//    }
//
//
//
//    private fun drawRoute() {
//        if (currentLocation != null && destinationLocation != null) {
//            lifecycleScope.launch {
//                routingHelper.getRoute(
//                    start = currentLocation!!,
//                    end = destinationLocation!!,
//                    onSuccess = { road ->
//                        currentRoad = road
//                        routingHelper.drawRoadOnMap(binding.mapView, road)
//
//                        Log.d("ROUTE_DEBUG", "road.mLength = ${road.mLength}")
//                        Log.d("ROUTE_DEBUG", "road.mDuration = ${road.mDuration}")
//                        Log.d("ROUTE_DEBUG", "road.mStatus = ${road.mStatus}")
//
//                        // FIX: Get actual distance from road
////                        val distanceInMeters = road.mLength.toDouble()
////                        val distanceInKm = distanceInMeters / 1000.0
////                        val durationInMinutes = road.mDuration / 60.0
//
//                        val distanceInKm = road.mLength
//                        val distanceInMeters = distanceInKm * 1000
//                        val durationInMinutes = road.mDuration / 60.0
//
//                        Log.d("ROUTE_DEBUG", "distanceInMeters = $distanceInMeters")
//                        Log.d("ROUTE_DEBUG", "distanceInKm = $distanceInKm")
//
//                        // Show distance properly
////                        if (distanceInMeters < 100) {
////                            binding.tvDistance.text = "📏 Distance: ${distanceInMeters.toInt()} meters"
////                        } else if (distanceInKm < 1) {
////                            binding.tvDistance.text = "📏 Distance: ${String.format("%.0f", distanceInMeters)} meters"
////                        } else {
////                            binding.tvDistance.text = "📏 Distance: ${String.format("%.1f", distanceInKm)} km"
////                        }
//                        if (distanceInMeters < 100) {
//                            binding.tvDistance.text = "📏 Distance: ${distanceInMeters.toInt()} meters"
//                        } else if (distanceInKm < 1) {
//                            binding.tvDistance.text = "📏 Distance: ${String.format("%.0f", distanceInMeters)} meters"
//                        } else {
//                            binding.tvDistance.text = "📏 Distance: ${String.format("%.1f", distanceInKm)} km"
//                        }
//                        binding.tvEta.text = "⏱️ ETA: ~${String.format("%.0f", durationInMinutes)} min"
//
//                        when {
//                            distanceInKm < 0.5 -> binding.tvStatus.text = "🟢 Very close! Almost there!"
//                            distanceInKm < 2 -> binding.tvStatus.text = "🟡 Arriving in ${String.format("%.0f", durationInMinutes)} minutes"
//                            else -> binding.tvStatus.text = "🔴 On the way to patient"
//                        }
//
//                        Log.d("TRACKING", "Distance: ${distanceInMeters}m, Duration: ${road.mDuration}s")
//                    },
//
////                        // USE FALLBACK DISTANCE CALCULATION INSTEAD OF road.mLength
////                        val distanceMeters = calculateDistance(currentLocation!!, destinationLocation!!)
////                        val distanceInKm = distanceMeters / 1000.0
////                        val etaMinutes = (distanceMeters / 1000 / 40 * 60).toInt()
////
////                        binding.tvDistance.text = if (distanceMeters < 1000) {
////                            "📏 Distance: ${distanceMeters.toInt()} meters"
////                        } else {
////                            "📏 Distance: ${String.format("%.1f", distanceInKm)} km"
////                        }
////                        binding.tvEta.text = "⏱️ ETA: ~${String.format("%.0f", etaMinutes)} min"
////
////                        when {
////                            distanceInKm < 0.5 -> binding.tvStatus.text = "🟢 Very close! Almost there!"
////                            distanceInKm < 2 -> binding.tvStatus.text = "🟡 Arriving in ${String.format("%.0f", etaMinutes)} minutes"
////                            else -> binding.tvStatus.text = "🔴 On the way to patient"
////                        }
////
////                        Log.d("TRACKING", "Distance: ${distanceMeters}m, Duration: ${road.mDuration}s")
////                    },
//
//
//                    onError = { error ->
//                        Log.e("TRACKING", "Routing error: $error")
//                        drawStraightLine()
//                    }
//                )
//            }
//        }
//    }
//
//
//
//    private fun drawStraightLine() {
//        if (currentLocation != null && destinationLocation != null) {
//            routingHelper.clearRouteFromMap(binding.mapView)
//
//            val routeOverlay = Polyline()
//            routeOverlay.addPoint(currentLocation)
//            routeOverlay.addPoint(destinationLocation)
//            routeOverlay.color = Color.BLUE
//            routeOverlay.width = 8f
//            binding.mapView.overlays.add(routeOverlay)
//
//            val distance = calculateDistance(currentLocation!!, destinationLocation!!)
//            val etaMinutes = (distance / 40 * 60).toInt()
//
//            binding.tvEta.text = "⏱️ ETA: ~$etaMinutes min (approx)"
//            binding.tvDistance.text = "📏 Distance: ${String.format("%.1f", distance)} km (straight line)"
//
//            when {
//                distance < 0.5 -> binding.tvStatus.text = "🟢 Very close! Almost there!"
//                distance < 2 -> binding.tvStatus.text = "🟡 Arriving in $etaMinutes minutes"
//                else -> binding.tvStatus.text = "🔴 On the way to patient"
//            }
//        }
//    }
//
//    private fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Double {
//        val lat1 = Math.toRadians(point1.latitude)
//        val lat2 = Math.toRadians(point2.latitude)
//        val dLat = Math.toRadians(point2.latitude - point1.latitude)
//        val dLon = Math.toRadians(point2.longitude - point1.longitude)
//
//        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
//                Math.cos(lat1) * Math.cos(lat2) *
//                Math.sin(dLon / 2) * Math.sin(dLon / 2)
//        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
//
//        return 6371 * c
//    }
//
//    private fun updateDriverLocationInDatabase() {
//        lifecycleScope.launch {
//            try {
//                currentLocation?.let {
//                    if (driverId != null && requestId != -1 && isTrackingActive) {
//                        val locationString = "${it.latitude},${it.longitude}"
//                        ServiceLocator.driverRepository.updateLiveLocation(driverId!!, locationString)
//                        Log.d("DRIVER", "📍 Location saved: $locationString") // ADD THIS
//                    }
//                }
//            } catch (e: Exception) {
//                Log.e("DRIVER", "Error updating location: ${e.message}")
//            }
//        }
//    }
//
//    private fun checkLocationPermission() {
//        if (ContextCompat.checkSelfPermission(
//                this,
//                Manifest.permission.ACCESS_FINE_LOCATION
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            ActivityCompat.requestPermissions(
//                this,
//                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
//                LOCATION_PERMISSION_REQUEST
//            )
//        }
//    }
//
//    private fun checkIfReachedDestination() {
//        if (currentLocation != null && destinationLocation != null) {
//            val distance = calculateDistance(currentLocation!!, destinationLocation!!)
//            // If within 50 meters, consider as reached
//            if (distance < 0.05) {
//                onDestinationReached()
//            }
//        }
//    }
//
//    private fun onDestinationReached() {
//        if (!isTrackingActive) return
//
//        isTrackingActive = false
//        binding.tvStatus.text = "✅ Patient reached! Trip completed."
//        binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
//        binding.tvEta.text = "⏱️ Trip Completed"
//        binding.tvDistance.text = "📏 Destination reached"
//
//        // Show completion dialog
//        AlertDialog.Builder(this)
//            .setTitle("Trip Completed")
//            .setMessage("You have successfully reached the patient location. Mark this trip as completed?")
//            .setPositiveButton("Complete Trip") { _, _ ->
//                completeTrip()
//            }
//            .setNegativeButton("Not Yet") { _, _ ->
//                // Continue tracking if driver says not reached yet
//                isTrackingActive = true
//                binding.tvStatus.text = "🔴 On the way to patient"
//                binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#E53935"))
//                startLocationUpdates()
//            }
//            .setCancelable(false)
//            .show()
//    }
//
//    private fun completeTrip() {
//        lifecycleScope.launch {
//            try {
//                // Update request status to "completed"
//                ServiceLocator.requestRepository.updateRequestStatus(requestId, "completed", driverId)
//
//                // Clear saved request
//                clearSavedRequest()
//
//                Toast.makeText(this@DriverTrackingActivity,
//                    "Trip completed successfully! Thank you for your service.",
//                    Toast.LENGTH_LONG).show()
//
//                // Navigate back to main screen
//                val intent = Intent(this@DriverTrackingActivity, MainActivity::class.java)
//                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
//                startActivity(intent)
//                finish()
//            } catch (e: Exception) {
//                Toast.makeText(this@DriverTrackingActivity,
//                    "Error completing trip: ${e.message}", Toast.LENGTH_LONG).show()
//            }
//        }
//    }
//
//    override fun onRequestPermissionsResult(
//        requestCode: Int,
//        permissions: Array<out String>,
//        grantResults: IntArray
//    ) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode == LOCATION_PERMISSION_REQUEST) {
//            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                setupMap()
//                startLocationUpdates()
//            } else {
//                Toast.makeText(this, "Location permission required for tracking", Toast.LENGTH_LONG).show()
//                finish()
//            }
//        }
//    }
//
//    override fun onSaveInstanceState(outState: Bundle) {
//        super.onSaveInstanceState(outState)
//        outState.putInt("request_id", requestId)
//        outState.putString("patient_name", patientName)
//        outState.putString("patient_address", patientAddress)
//        outState.putString("contact_number", contactNumber)
//        destinationLocation?.let {
//            outState.putDouble("patient_lat", it.latitude)
//            outState.putDouble("patient_lng", it.longitude)
//        }
//    }
//
//    override fun onResume() {
//        super.onResume()
//        binding.mapView.onResume()
//        if (driverId != null && requestId != -1) {
//            lifecycleScope.launch {
//                verifyRequestStillActive()
//            }
//        }
//    }
//
//    override fun onPause() {
//        super.onPause()
//        binding.mapView.onPause()
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        isTrackingActive = false
//    }
//}