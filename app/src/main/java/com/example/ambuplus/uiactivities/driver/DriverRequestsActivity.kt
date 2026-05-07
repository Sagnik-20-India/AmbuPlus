package com.example.ambuplus.uiactivities.driver

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ambuplus.databinding.ActivityDriverRequestsBinding
import com.example.ambuplus.models.Request
import com.example.ambuplus.utils.ServiceLocator
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

class DriverRequestsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDriverRequestsBinding
    private val requestRepository = ServiceLocator.requestRepository
    private lateinit var requestsAdapter: RequestAdapter
    private var currentDriverId: String? = null
    private var currentDriverLocation: GeoPoint? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sharedPreferences: android.content.SharedPreferences
    private lateinit var btnResumeTrip: Button

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1002
        private const val PREFS_NAME = "AmbuPlus"
        private const val KEY_ACTIVE_DRIVER_REQUEST_ID = "active_driver_request_id"
        private const val KEY_ACTIVE_DRIVER_ID = "active_driver_id"
        private const val KEY_DENIED_REQUESTS = "denied_requests"

        // DYNAMIC RADIUS LIMITS BASED ON EMERGENCY LEVEL
        private const val MAX_DISTANCE_HIGH_EMERGENCY = 5.0   // 5 km for life-threatening
        private const val MAX_DISTANCE_MEDIUM_EMERGENCY = 10.0 // 10 km for serious but stable
        private const val MAX_DISTANCE_LOW_EMERGENCY = 25.0    // 25 km for non-emergency
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDriverRequestsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        btnResumeTrip = findViewById(com.example.ambuplus.R.id.btnResumeTrip)

        // IMMEDIATELY hide the resume trip button
        btnResumeTrip.visibility = android.view.View.GONE

        setupRecyclerView()
        setupClickListeners()
        loadDriverData()
        checkLocationPermission()

        // Force clear and check
        lifecycleScope.launch {
            // Wait a bit for driver data to load
            delay(1000)
            forceClearAndCheckActiveTrip()
        }
    }

    private suspend fun forceClearAndCheckActiveTrip() {
        println("DEBUG: ====== FORCE CLEAR AND CHECK ======")

        // First, clear any saved request that might be invalid
        val savedRequestId = sharedPreferences.getInt(KEY_ACTIVE_DRIVER_REQUEST_ID, -1)
        println("DEBUG: Found saved request ID: $savedRequestId")

        if (savedRequestId != -1) {
            try {
                val request = requestRepository.getRequestById(savedRequestId)
                println("DEBUG: Request status: ${request?.status}")

                // If request doesn't exist or is not accepted, clear it
                if (request == null || request.status != "accepted") {
                    println("DEBUG: Clearing invalid saved request")
                    clearSavedRequest()
                } else {
                    // Request is valid, show resume button
                    println("DEBUG: Valid active request found")
                    showResumeButton(request)
                    return
                }
            } catch (e: Exception) {
                println("DEBUG: Error checking request, clearing saved data")
                clearSavedRequest()
            }
        }

        // If we have driver ID, check database for active request
        if (currentDriverId != null) {
            println("DEBUG: Checking database for active request for driver: $currentDriverId")
            val activeRequest = requestRepository.getActiveRequestForDriver(currentDriverId!!)
            println("DEBUG: Database active request: ${activeRequest?.id}, status: ${activeRequest?.status}")

            if (activeRequest != null && activeRequest.status == "accepted") {
                println("DEBUG: Found active request in database")
                showResumeButton(activeRequest)
                // Save to SharedPreferences
                sharedPreferences.edit().putInt(KEY_ACTIVE_DRIVER_REQUEST_ID, activeRequest.id ?: -1).apply()
                sharedPreferences.edit().putString(KEY_ACTIVE_DRIVER_ID, currentDriverId).apply()
            } else {
                println("DEBUG: No active request found in database")
                btnResumeTrip.visibility = android.view.View.GONE
            }
        } else {
            println("DEBUG: No driver ID available")
            btnResumeTrip.visibility = android.view.View.GONE
        }

        // Also force refresh the pending requests list
        loadPendingRequests()
    }

    private fun showResumeButton(request: Request) {
        runOnUiThread {
            btnResumeTrip.visibility = android.view.View.VISIBLE
            btnResumeTrip.text = "Resume Active Trip: ${request.patientName ?: "Patient"}"
            btnResumeTrip.setOnClickListener {
                resumeActiveTrip(request)
            }
            println("DEBUG: Resume button shown for: ${request.patientName}")
        }
    }

    private fun clearSavedRequest() {
        sharedPreferences.edit().remove(KEY_ACTIVE_DRIVER_REQUEST_ID).apply()
        sharedPreferences.edit().remove(KEY_ACTIVE_DRIVER_ID).apply()
        println("DEBUG: Cleared saved request from SharedPreferences")
    }

    private fun resumeActiveTrip(request: Request) {
        val coords = extractCoordinatesFromLocation(request.location)
        val intent = Intent(this, DriverTrackingActivity::class.java).apply {
            putExtra("request_id", request.id ?: -1)
            putExtra("patient_lat", coords?.first ?: 0.0)
            putExtra("patient_lng", coords?.second ?: 0.0)
            putExtra("patient_address", request.location.split("|").firstOrNull() ?: request.location)
            putExtra("patient_name", request.patientName ?: "Patient")
            putExtra("contact_number", request.contactNumber ?: "")
        }
        startActivity(intent)
        finish()
    }

    private fun setupRecyclerView() {
        requestsAdapter = RequestAdapter(
            onAcceptClick = { request -> acceptRequest(request) },
            onDenyClick = { request -> denyRequest(request) }
        )
        binding.rvRequests.layoutManager = LinearLayoutManager(this)
        binding.rvRequests.adapter = requestsAdapter
    }

    private fun setupClickListeners() {
        binding.btnRefresh.setOnClickListener {
            lifecycleScope.launch {
                forceClearAndCheckActiveTrip()
            }
        }
    }

    private fun checkLocationPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            getCurrentLocation()
            true
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            false
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

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    currentDriverLocation = GeoPoint(it.latitude, it.longitude)
                    Log.d("DriverRequests", "Driver location: ${currentDriverLocation}")
                    loadPendingRequests()
                }
            }
            .addOnFailureListener { e ->
                Log.e("DriverRequests", "Error getting location: ${e.message}")
            }
    }

    private fun loadDriverData() {
        val currentUser = ServiceLocator.authRepository.currentUser()
        currentUser?.let { user ->
            lifecycleScope.launch {
                try {
                    val driver = ServiceLocator.driverRepository.getDriverByUserId(user.id)
                    currentDriverId = driver?.id
                    Log.d("DriverRequests", "Driver ID: $currentDriverId")
                } catch (e: Exception) {
                    Log.e("DriverRequests", "Error loading driver: ${e.message}")
                }
            }
        }
    }

    private fun loadPendingRequests() {
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.tvEmpty.visibility = android.view.View.GONE
        binding.rvRequests.visibility = android.view.View.VISIBLE

        lifecycleScope.launch {
            try {
                Log.d("DriverRequests", "Fetching pending requests...")

                // Get denied requests set from SharedPreferences
                val deniedRequestIds = sharedPreferences.getStringSet(KEY_DENIED_REQUESTS, mutableSetOf()) ?: emptySet()

                // Get only pending requests (status = "pending")
                val pendingRequests = requestRepository.getPendingRequests()

                // Filter out requests that this driver has denied
                val filteredByDenied = pendingRequests.filter {
                    it.status == "pending" && !deniedRequestIds.contains(it.id.toString())
                }

                // Calculate distance for each request
                val requestsWithDistance = mutableListOf<Pair<Request, Double>>()
                for (request in filteredByDenied) {
                    val distance = calculateDistanceToPatient(request)
                    if (distance != Double.MAX_VALUE) {
                        requestsWithDistance.add(request to distance)
                    }
                }

                // FILTER BY EMERGENCY LEVEL AND DISTANCE
                val filteredRequests = requestsWithDistance.filter { (request, distance) ->
                    val isWithinRange = when (request.emergencyLevel.lowercase()) {
                        "high" -> distance <= MAX_DISTANCE_HIGH_EMERGENCY
                        "medium" -> distance <= MAX_DISTANCE_MEDIUM_EMERGENCY
                        "low" -> distance <= MAX_DISTANCE_LOW_EMERGENCY
                        else -> distance <= MAX_DISTANCE_MEDIUM_EMERGENCY
                    }

                    // Log if filtered out
                    if (!isWithinRange) {
                        Log.d("DriverRequests", "Filtered out: ${request.patientName} (${request.emergencyLevel}) at ${String.format("%.1f", distance)} km")
                    }
                    isWithinRange
                }.sortedBy { it.second }

                Log.d("DriverRequests", "Original: ${pendingRequests.size}, After deny filter: ${filteredByDenied.size}, After distance filter: ${filteredRequests.size}")

                if (filteredRequests.isEmpty()) {
                    binding.tvEmpty.visibility = android.view.View.VISIBLE
                    val message = if (pendingRequests.isEmpty()) {
                        "No pending requests available"
                    } else if (filteredByDenied.isEmpty() && pendingRequests.isNotEmpty()) {
                        "You have denied all available requests.\nNew requests may appear later."
                    } else {
                        "No requests within your service area.\n\n" +
                                "🚨 HIGH EMERGENCY: within ${MAX_DISTANCE_HIGH_EMERGENCY} km\n" +
                                "⚠️ MEDIUM EMERGENCY: within ${MAX_DISTANCE_MEDIUM_EMERGENCY} km\n" +
                                "🟢 LOW EMERGENCY: within ${MAX_DISTANCE_LOW_EMERGENCY} km"
                    }
                    binding.tvEmpty.text = message
                    binding.rvRequests.visibility = android.view.View.GONE
                } else {
                    binding.tvEmpty.visibility = android.view.View.GONE
                    binding.rvRequests.visibility = android.view.View.VISIBLE
                    requestsAdapter.submitList(filteredRequests.map { it.first }, currentDriverLocation)

                    // Show warning if any request is near the limit
                    val nearLimitRequests = filteredRequests.filter { (request, distance) ->
                        when (request.emergencyLevel.lowercase()) {
                            "high" -> distance > MAX_DISTANCE_HIGH_EMERGENCY * 0.7
                            "medium" -> distance > MAX_DISTANCE_MEDIUM_EMERGENCY * 0.7
                            "low" -> distance > MAX_DISTANCE_LOW_EMERGENCY * 0.7
                            else -> false
                        }
                    }
                    if (nearLimitRequests.isNotEmpty()) {
                        Toast.makeText(this@DriverRequestsActivity,
                            "⚠️ Some requests are at your maximum range. Consider response time!",
                            Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                Log.e("DriverRequests", "Error loading requests: ${e.message}")
                e.printStackTrace()
                Toast.makeText(this@DriverRequestsActivity,
                    "Error loading requests: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = android.view.View.GONE
            }
        }
    }

    private fun calculateDistanceToPatient(request: Request): Double {
        try {
            val coords = extractCoordinatesFromLocation(request.location)
            if (coords != null && currentDriverLocation != null) {
                val patientLocation = GeoPoint(coords.first, coords.second)
                return calculateDistance(currentDriverLocation!!, patientLocation)
            }
        } catch (e: Exception) {
            Log.e("DriverRequests", "Error calculating distance: ${e.message}")
        }
        return Double.MAX_VALUE
    }

    private fun extractCoordinatesFromLocation(location: String): Pair<Double, Double>? {
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

    private fun acceptRequest(request: Request) {
        val driverId = currentDriverId
        if (driverId == null) {
            Toast.makeText(this, "Driver profile not found", Toast.LENGTH_LONG).show()
            return
        }

        if (request.id == null) {
            Toast.makeText(this, "Invalid request", Toast.LENGTH_SHORT).show()
            return
        }

        // Double-check distance before accepting
        val distance = calculateDistanceToPatient(request)
        val maxDistance = when (request.emergencyLevel.lowercase()) {
            "high" -> MAX_DISTANCE_HIGH_EMERGENCY
            "medium" -> MAX_DISTANCE_MEDIUM_EMERGENCY
            "low" -> MAX_DISTANCE_LOW_EMERGENCY
            else -> MAX_DISTANCE_MEDIUM_EMERGENCY
        }

        if (distance > maxDistance) {
            Toast.makeText(this,
                "This request is outside your service area (${String.format("%.1f", distance)} km away)",
                Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            try {
                println("DEBUG: ====== ACCEPTING REQUEST ======")
                println("DEBUG: Request ID: ${request.id}")
                println("DEBUG: Driver ID: $driverId")

                requestRepository.updateRequestStatus(request.id, "accepted", driverId)

                // Clear any existing saved request first
                clearSavedRequest()

                // Save the new request
                sharedPreferences.edit().putInt(KEY_ACTIVE_DRIVER_REQUEST_ID, request.id).apply()
                sharedPreferences.edit().putString(KEY_ACTIVE_DRIVER_ID, driverId).apply()
                println("DEBUG: Saved request ID ${request.id} to SharedPreferences")

                Toast.makeText(this@DriverRequestsActivity,
                    "✅ Request accepted! Starting navigation...", Toast.LENGTH_LONG).show()

                val patientCoords = extractCoordinatesFromLocation(request.location)
                println("DEBUG: Patient coordinates: ${patientCoords?.first}, ${patientCoords?.second}")

                val intent = Intent(this@DriverRequestsActivity, DriverTrackingActivity::class.java).apply {
                    putExtra("request_id", request.id)
                    putExtra("driver_id", driverId)
                    putExtra("patient_lat", patientCoords?.first ?: 0.0)
                    putExtra("patient_lng", patientCoords?.second ?: 0.0)
                    putExtra("patient_address", request.location.split("|").firstOrNull() ?: request.location)
                    putExtra("patient_name", request.patientName ?: "Patient")
                    putExtra("contact_number", request.contactNumber)
                }
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                println("DEBUG: Error accepting request: ${e.message}")
                e.printStackTrace()
                Toast.makeText(this@DriverRequestsActivity,
                    "Failed to accept request: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun denyRequest(request: Request) {
        if (request.id == null) {
            Toast.makeText(this, "Invalid request", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                // IMPORTANT: Do NOT change the request status in database
                // Just track that this driver denied this request
                val deniedRequests = sharedPreferences.getStringSet(KEY_DENIED_REQUESTS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                deniedRequests.add(request.id.toString())
                sharedPreferences.edit().putStringSet(KEY_DENIED_REQUESTS, deniedRequests).apply()

                // Remove from local list immediately
                val currentList = requestsAdapter.getCurrentList()
                val newList = currentList.filter { it.id != request.id }
                requestsAdapter.submitList(newList, currentDriverLocation)

                Toast.makeText(this@DriverRequestsActivity,
                    "❌ Request denied. Other drivers will see it.",
                    Toast.LENGTH_SHORT).show()

                // If this was the active trip, clear saved request
                val savedId = sharedPreferences.getInt(KEY_ACTIVE_DRIVER_REQUEST_ID, -1)
                if (savedId == request.id) {
                    clearSavedRequest()
                }

                // Check if list becomes empty
                if (newList.isEmpty()) {
                    binding.tvEmpty.visibility = android.view.View.VISIBLE
                    binding.rvRequests.visibility = android.view.View.GONE
                    binding.tvEmpty.text = "No pending requests available"
                }

            } catch (e: Exception) {
                Toast.makeText(this@DriverRequestsActivity,
                    "Failed to deny request: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
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
                Toast.makeText(this, "Location permission needed to see distances", Toast.LENGTH_SHORT).show()
            }
        }
    }
}









//package com.example.ambuplus.uiactivities.driver
//
//import android.Manifest
//import android.content.Context
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.location.Location
//import android.os.Bundle
//import android.util.Log
//import android.widget.Button
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.app.ActivityCompat
//import androidx.core.content.ContextCompat
//import androidx.lifecycle.lifecycleScope
//import androidx.recyclerview.widget.LinearLayoutManager
//import com.example.ambuplus.databinding.ActivityDriverRequestsBinding
//import com.example.ambuplus.models.Request
//import com.example.ambuplus.utils.ServiceLocator
//import com.google.android.gms.location.FusedLocationProviderClient
//import com.google.android.gms.location.LocationServices
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.launch
//import org.osmdroid.util.GeoPoint
//
//class DriverRequestsActivity : AppCompatActivity() {
//
//    private lateinit var binding: ActivityDriverRequestsBinding
//    private val requestRepository = ServiceLocator.requestRepository
//    private lateinit var requestsAdapter: RequestAdapter
//    private var currentDriverId: String? = null
//    private var currentDriverLocation: GeoPoint? = null
//    private lateinit var fusedLocationClient: FusedLocationProviderClient
//    private lateinit var sharedPreferences: android.content.SharedPreferences
//    private lateinit var btnResumeTrip: Button
//
//    companion object {
//        private const val LOCATION_PERMISSION_REQUEST_CODE = 1002
//        private const val PREFS_NAME = "AmbuPlus"
//        private const val KEY_ACTIVE_DRIVER_REQUEST_ID = "active_driver_request_id"
//        private const val KEY_ACTIVE_DRIVER_ID = "active_driver_id"
//        private const val KEY_DENIED_REQUESTS = "denied_requests"
//
//        // DYNAMIC RADIUS LIMITS BASED ON EMERGENCY LEVEL
//        private const val MAX_DISTANCE_HIGH_EMERGENCY = 5.0   // 5 km for life-threatening
//        private const val MAX_DISTANCE_MEDIUM_EMERGENCY = 10.0 // 10 km for serious but stable
//        private const val MAX_DISTANCE_LOW_EMERGENCY = 25.0    // 25 km for non-emergency
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding = ActivityDriverRequestsBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
//        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
//
//        btnResumeTrip = findViewById(com.example.ambuplus.R.id.btnResumeTrip)
//
//        // IMMEDIATELY hide the resume trip button
//        btnResumeTrip.visibility = android.view.View.GONE
//
//        setupRecyclerView()
//        setupClickListeners()
//        loadDriverData()
//        checkLocationPermission()
//
//        // Setup debug clear button
//        setupClearStuckTripButton()
//
//        // Force clear and check
//        lifecycleScope.launch {
//            // Wait a bit for driver data to load
//            delay(1000)
//            forceClearAndCheckActiveTrip()
//        }
//    }
//
//    private fun setupClearStuckTripButton() {
//        // Find the debug button (you need to add this to your XML layout)
//        val btnClearStuckTrip = findViewById<Button>(com.example.ambuplus.R.id.btnClearStuckTrip)
//        btnClearStuckTrip?.setOnClickListener {
//            // CLEAR ALL SharedPreferences related to driver trips
//            sharedPreferences.edit().clear().apply()
//
//            // Also clear emergency prefs
//            val emergencyPrefs = getSharedPreferences("EmergencyPrefs", Context.MODE_PRIVATE)
//            emergencyPrefs.edit().clear().apply()
//
//            // Force hide the resume button
//            btnResumeTrip.visibility = android.view.View.GONE
//
//            // Force refresh the page
//            loadPendingRequests()
//
//            Toast.makeText(this, "✅ All stored trip data cleared! Refresh to see changes.", Toast.LENGTH_LONG).show()
//        }
//    }
//
//    private suspend fun forceClearAndCheckActiveTrip() {
//        println("DEBUG: ====== FORCE CLEAR AND CHECK ======")
//
//        // First, clear any saved request that might be invalid
//        val savedRequestId = sharedPreferences.getInt(KEY_ACTIVE_DRIVER_REQUEST_ID, -1)
//        println("DEBUG: Found saved request ID: $savedRequestId")
//
//        if (savedRequestId != -1) {
//            try {
//                val request = requestRepository.getRequestById(savedRequestId)
//                println("DEBUG: Request status: ${request?.status}")
//
//                // If request doesn't exist or is not accepted, clear it
//                if (request == null || request.status != "accepted") {
//                    println("DEBUG: Clearing invalid saved request")
//                    clearSavedRequest()
//                } else {
//                    // Request is valid, show resume button
//                    println("DEBUG: Valid active request found")
//                    showResumeButton(request)
//                    return
//                }
//            } catch (e: Exception) {
//                println("DEBUG: Error checking request, clearing saved data")
//                clearSavedRequest()
//            }
//        }
//
//        // If we have driver ID, check database for active request
//        if (currentDriverId != null) {
//            println("DEBUG: Checking database for active request for driver: $currentDriverId")
//            val activeRequest = requestRepository.getActiveRequestForDriver(currentDriverId!!)
//            println("DEBUG: Database active request: ${activeRequest?.id}, status: ${activeRequest?.status}")
//
//            if (activeRequest != null && activeRequest.status == "accepted") {
//                println("DEBUG: Found active request in database")
//                showResumeButton(activeRequest)
//                // Save to SharedPreferences
//                sharedPreferences.edit().putInt(KEY_ACTIVE_DRIVER_REQUEST_ID, activeRequest.id ?: -1).apply()
//                sharedPreferences.edit().putString(KEY_ACTIVE_DRIVER_ID, currentDriverId).apply()
//            } else {
//                println("DEBUG: No active request found in database")
//                btnResumeTrip.visibility = android.view.View.GONE
//            }
//        } else {
//            println("DEBUG: No driver ID available")
//            btnResumeTrip.visibility = android.view.View.GONE
//        }
//
//        // Also force refresh the pending requests list
//        loadPendingRequests()
//    }
//
//    private fun showResumeButton(request: Request) {
//        runOnUiThread {
//            btnResumeTrip.visibility = android.view.View.VISIBLE
//            btnResumeTrip.text = "Resume Active Trip: ${request.patientName ?: "Patient"}"
//            btnResumeTrip.setOnClickListener {
//                resumeActiveTrip(request)
//            }
//            println("DEBUG: Resume button shown for: ${request.patientName}")
//        }
//    }
//
//    private fun clearSavedRequest() {
//        sharedPreferences.edit().remove(KEY_ACTIVE_DRIVER_REQUEST_ID).apply()
//        sharedPreferences.edit().remove(KEY_ACTIVE_DRIVER_ID).apply()
//        println("DEBUG: Cleared saved request from SharedPreferences")
//    }
//
//    private fun resumeActiveTrip(request: Request) {
//        val coords = extractCoordinatesFromLocation(request.location)
//        val intent = Intent(this, DriverTrackingActivity::class.java).apply {
//            putExtra("request_id", request.id ?: -1)
//            putExtra("patient_lat", coords?.first ?: 0.0)
//            putExtra("patient_lng", coords?.second ?: 0.0)
//            putExtra("patient_address", request.location.split("|").firstOrNull() ?: request.location)
//            putExtra("patient_name", request.patientName ?: "Patient")
//            putExtra("contact_number", request.contactNumber ?: "")
//        }
//        startActivity(intent)
//        finish()
//    }
//
//    private fun setupRecyclerView() {
//        requestsAdapter = RequestAdapter(
//            onAcceptClick = { request -> acceptRequest(request) },
//            onDenyClick = { request -> denyRequest(request) }
//        )
//        binding.rvRequests.layoutManager = LinearLayoutManager(this)
//        binding.rvRequests.adapter = requestsAdapter
//    }
//
//    private fun setupClickListeners() {
//        binding.btnRefresh.setOnClickListener {
//            lifecycleScope.launch {
//                forceClearAndCheckActiveTrip()
//            }
//        }
//    }
//
//    private fun checkLocationPermission(): Boolean {
//        return if (ContextCompat.checkSelfPermission(
//                this,
//                Manifest.permission.ACCESS_FINE_LOCATION
//            ) == PackageManager.PERMISSION_GRANTED
//        ) {
//            getCurrentLocation()
//            true
//        } else {
//            ActivityCompat.requestPermissions(
//                this,
//                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
//                LOCATION_PERMISSION_REQUEST_CODE
//            )
//            false
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
//        fusedLocationClient.lastLocation
//            .addOnSuccessListener { location: Location? ->
//                location?.let {
//                    currentDriverLocation = GeoPoint(it.latitude, it.longitude)
//                    Log.d("DriverRequests", "Driver location: ${currentDriverLocation}")
//                    loadPendingRequests()
//                }
//            }
//            .addOnFailureListener { e ->
//                Log.e("DriverRequests", "Error getting location: ${e.message}")
//            }
//    }
//
//    private fun loadDriverData() {
//        val currentUser = ServiceLocator.authRepository.currentUser()
//        currentUser?.let { user ->
//            lifecycleScope.launch {
//                try {
//                    val driver = ServiceLocator.driverRepository.getDriverByUserId(user.id)
//                    currentDriverId = driver?.id
//                    Log.d("DriverRequests", "Driver ID: $currentDriverId")
//                } catch (e: Exception) {
//                    Log.e("DriverRequests", "Error loading driver: ${e.message}")
//                }
//            }
//        }
//    }
//
//    private fun loadPendingRequests() {
//        binding.progressBar.visibility = android.view.View.VISIBLE
//        binding.tvEmpty.visibility = android.view.View.GONE
//        binding.rvRequests.visibility = android.view.View.VISIBLE
//
//        lifecycleScope.launch {
//            try {
//                Log.d("DriverRequests", "Fetching pending requests...")
//
//                // Get denied requests set from SharedPreferences
//                val deniedRequestIds = sharedPreferences.getStringSet(KEY_DENIED_REQUESTS, mutableSetOf()) ?: emptySet()
//
//                // Get only pending requests (status = "pending")
//                val pendingRequests = requestRepository.getPendingRequests()
//
//                // Filter out requests that this driver has denied
//                val filteredByDenied = pendingRequests.filter {
//                    it.status == "pending" && !deniedRequestIds.contains(it.id.toString())
//                }
//
//                // Calculate distance for each request
//                val requestsWithDistance = mutableListOf<Pair<Request, Double>>()
//                for (request in filteredByDenied) {
//                    val distance = calculateDistanceToPatient(request)
//                    if (distance != Double.MAX_VALUE) {
//                        requestsWithDistance.add(request to distance)
//                    }
//                }
//
//                // FILTER BY EMERGENCY LEVEL AND DISTANCE
//                val filteredRequests = requestsWithDistance.filter { (request, distance) ->
//                    val isWithinRange = when (request.emergencyLevel.lowercase()) {
//                        "high" -> distance <= MAX_DISTANCE_HIGH_EMERGENCY
//                        "medium" -> distance <= MAX_DISTANCE_MEDIUM_EMERGENCY
//                        "low" -> distance <= MAX_DISTANCE_LOW_EMERGENCY
//                        else -> distance <= MAX_DISTANCE_MEDIUM_EMERGENCY
//                    }
//
//                    // Log if filtered out
//                    if (!isWithinRange) {
//                        Log.d("DriverRequests", "Filtered out: ${request.patientName} (${request.emergencyLevel}) at ${String.format("%.1f", distance)} km")
//                    }
//                    isWithinRange
//                }.sortedBy { it.second }
//
//                Log.d("DriverRequests", "Original: ${pendingRequests.size}, After deny filter: ${filteredByDenied.size}, After distance filter: ${filteredRequests.size}")
//
//                if (filteredRequests.isEmpty()) {
//                    binding.tvEmpty.visibility = android.view.View.VISIBLE
//                    val message = if (pendingRequests.isEmpty()) {
//                        "No pending requests available"
//                    } else if (filteredByDenied.isEmpty() && pendingRequests.isNotEmpty()) {
//                        "You have denied all available requests.\nNew requests may appear later."
//                    } else {
//                        "No requests within your service area.\n\n" +
//                                "🚨 HIGH EMERGENCY: within ${MAX_DISTANCE_HIGH_EMERGENCY} km\n" +
//                                "⚠️ MEDIUM EMERGENCY: within ${MAX_DISTANCE_MEDIUM_EMERGENCY} km\n" +
//                                "🟢 LOW EMERGENCY: within ${MAX_DISTANCE_LOW_EMERGENCY} km"
//                    }
//                    binding.tvEmpty.text = message
//                    binding.rvRequests.visibility = android.view.View.GONE
//                } else {
//                    binding.tvEmpty.visibility = android.view.View.GONE
//                    binding.rvRequests.visibility = android.view.View.VISIBLE
//                    requestsAdapter.submitList(filteredRequests.map { it.first }, currentDriverLocation)
//
//                    // Show warning if any request is near the limit
//                    val nearLimitRequests = filteredRequests.filter { (request, distance) ->
//                        when (request.emergencyLevel.lowercase()) {
//                            "high" -> distance > MAX_DISTANCE_HIGH_EMERGENCY * 0.7
//                            "medium" -> distance > MAX_DISTANCE_MEDIUM_EMERGENCY * 0.7
//                            "low" -> distance > MAX_DISTANCE_LOW_EMERGENCY * 0.7
//                            else -> false
//                        }
//                    }
//                    if (nearLimitRequests.isNotEmpty()) {
//                        Toast.makeText(this@DriverRequestsActivity,
//                            "⚠️ Some requests are at your maximum range. Consider response time!",
//                            Toast.LENGTH_LONG).show()
//                    }
//                }
//
//            } catch (e: Exception) {
//                Log.e("DriverRequests", "Error loading requests: ${e.message}")
//                e.printStackTrace()
//                Toast.makeText(this@DriverRequestsActivity,
//                    "Error loading requests: ${e.message}", Toast.LENGTH_LONG).show()
//            } finally {
//                binding.progressBar.visibility = android.view.View.GONE
//            }
//        }
//    }
//
//    private fun calculateDistanceToPatient(request: Request): Double {
//        try {
//            val coords = extractCoordinatesFromLocation(request.location)
//            if (coords != null && currentDriverLocation != null) {
//                val patientLocation = GeoPoint(coords.first, coords.second)
//                return calculateDistance(currentDriverLocation!!, patientLocation)
//            }
//        } catch (e: Exception) {
//            Log.e("DriverRequests", "Error calculating distance: ${e.message}")
//        }
//        return Double.MAX_VALUE
//    }
//
//    private fun extractCoordinatesFromLocation(location: String): Pair<Double, Double>? {
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
//    private fun acceptRequest(request: Request) {
//        val driverId = currentDriverId
//        if (driverId == null) {
//            Toast.makeText(this, "Driver profile not found", Toast.LENGTH_LONG).show()
//            return
//        }
//
//        if (request.id == null) {
//            Toast.makeText(this, "Invalid request", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        // Double-check distance before accepting
//        val distance = calculateDistanceToPatient(request)
//        val maxDistance = when (request.emergencyLevel.lowercase()) {
//            "high" -> MAX_DISTANCE_HIGH_EMERGENCY
//            "medium" -> MAX_DISTANCE_MEDIUM_EMERGENCY
//            "low" -> MAX_DISTANCE_LOW_EMERGENCY
//            else -> MAX_DISTANCE_MEDIUM_EMERGENCY
//        }
//
//        if (distance > maxDistance) {
//            Toast.makeText(this,
//                "This request is outside your service area (${String.format("%.1f", distance)} km away)",
//                Toast.LENGTH_LONG).show()
//            return
//        }
//
//        lifecycleScope.launch {
//            try {
//                println("DEBUG: ====== ACCEPTING REQUEST ======")
//                println("DEBUG: Request ID: ${request.id}")
//                println("DEBUG: Driver ID: $driverId")
//
//                requestRepository.updateRequestStatus(request.id, "accepted", driverId)
//
//                // Clear any existing saved request first
//                clearSavedRequest()
//
//                // Save the new request
//                sharedPreferences.edit().putInt(KEY_ACTIVE_DRIVER_REQUEST_ID, request.id).apply()
//                sharedPreferences.edit().putString(KEY_ACTIVE_DRIVER_ID, driverId).apply()
//                println("DEBUG: Saved request ID ${request.id} to SharedPreferences")
//
//                Toast.makeText(this@DriverRequestsActivity,
//                    "✅ Request accepted! Starting navigation...", Toast.LENGTH_LONG).show()
//
//                val patientCoords = extractCoordinatesFromLocation(request.location)
//                println("DEBUG: Patient coordinates: ${patientCoords?.first}, ${patientCoords?.second}")
//
//                val intent = Intent(this@DriverRequestsActivity, DriverTrackingActivity::class.java).apply {
//                    putExtra("request_id", request.id)
//                    putExtra("driver_id", driverId)
//                    putExtra("patient_lat", patientCoords?.first ?: 0.0)
//                    putExtra("patient_lng", patientCoords?.second ?: 0.0)
//                    putExtra("patient_address", request.location.split("|").firstOrNull() ?: request.location)
//                    putExtra("patient_name", request.patientName ?: "Patient")
//                    putExtra("contact_number", request.contactNumber)
//                }
//                startActivity(intent)
//                finish()
//            } catch (e: Exception) {
//                println("DEBUG: Error accepting request: ${e.message}")
//                e.printStackTrace()
//                Toast.makeText(this@DriverRequestsActivity,
//                    "Failed to accept request: ${e.message}", Toast.LENGTH_LONG).show()
//            }
//        }
//    }
//
//    private fun denyRequest(request: Request) {
//        if (request.id == null) {
//            Toast.makeText(this, "Invalid request", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        lifecycleScope.launch {
//            try {
//                // IMPORTANT: Do NOT change the request status in database
//                // Just track that this driver denied this request
//                val deniedRequests = sharedPreferences.getStringSet(KEY_DENIED_REQUESTS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
//                deniedRequests.add(request.id.toString())
//                sharedPreferences.edit().putStringSet(KEY_DENIED_REQUESTS, deniedRequests).apply()
//
//                // Remove from local list immediately
//                val currentList = requestsAdapter.getCurrentList()
//                val newList = currentList.filter { it.id != request.id }
//                requestsAdapter.submitList(newList, currentDriverLocation)
//
//                Toast.makeText(this@DriverRequestsActivity,
//                    "❌ Request denied. Other drivers will see it.",
//                    Toast.LENGTH_SHORT).show()
//
//                // If this was the active trip, clear saved request
//                val savedId = sharedPreferences.getInt(KEY_ACTIVE_DRIVER_REQUEST_ID, -1)
//                if (savedId == request.id) {
//                    clearSavedRequest()
//                }
//
//                // Check if list becomes empty
//                if (newList.isEmpty()) {
//                    binding.tvEmpty.visibility = android.view.View.VISIBLE
//                    binding.rvRequests.visibility = android.view.View.GONE
//                    binding.tvEmpty.text = "No pending requests available"
//                }
//
//            } catch (e: Exception) {
//                Toast.makeText(this@DriverRequestsActivity,
//                    "Failed to deny request: ${e.message}", Toast.LENGTH_LONG).show()
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
//        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
//            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                getCurrentLocation()
//            } else {
//                Toast.makeText(this, "Location permission needed to see distances", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
//}