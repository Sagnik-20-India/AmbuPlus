//package com.example.ambuplus.uiactivities.driver
//
//import android.os.Bundle
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import androidx.lifecycle.lifecycleScope
//import androidx.recyclerview.widget.LinearLayoutManager
//import com.example.ambuplus.databinding.ActivityDriverRequestsBinding
//import com.example.ambuplus.models.Request
//import com.example.ambuplus.utils.ServiceLocator
//import kotlinx.coroutines.launch
//
//class DriverRequestsActivity : AppCompatActivity() {
//
//    private lateinit var binding: ActivityDriverRequestsBinding
//    private val driverRepository = ServiceLocator.driverRepository
//    private val requestRepository = ServiceLocator.requestRepository
//    private lateinit var requestsAdapter: RequestsAdapter
//    private var currentDriverId: String? = null
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding = ActivityDriverRequestsBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        setupRecyclerView()
//        setupClickListeners()
//        loadDriverData()
//        loadPendingRequests()
//
//        // Start listening for real-time updates
//        setupRealtimeListener()
//    }
//
//    private fun setupRecyclerView() {
//        requestsAdapter = RequestsAdapter { request ->
//            acceptRequest(request)
//        }
//        binding.rvRequests.layoutManager = LinearLayoutManager(this)
//        binding.rvRequests.adapter = requestsAdapter
//    }
//
//    private fun setupClickListeners() {
//        binding.btnRefresh.setOnClickListener {
//            loadPendingRequests()
//        }
//    }
//
//    private fun loadDriverData() {
//        val currentUser = ServiceLocator.authRepository.currentUser()
//        currentUser?.let { user ->
//            lifecycleScope.launch {
//                val driver = driverRepository.getDriverByUserId(user.id)
//                currentDriverId = driver?.id
//            }
//        }
//    }
//
//    private fun loadPendingRequests() {
//        binding.progressBar.visibility = android.view.View.VISIBLE
//        binding.tvEmpty.visibility = android.view.View.GONE
//
//        lifecycleScope.launch {
//            try {
//                val requests = driverRepository.getPendingRequests()
//                if (requests.isEmpty()) {
//                    binding.tvEmpty.visibility = android.view.View.VISIBLE
//                    binding.rvRequests.visibility = android.view.View.GONE
//                } else {
//                    binding.tvEmpty.visibility = android.view.View.GONE
//                    binding.rvRequests.visibility = android.view.View.VISIBLE
//                    requestsAdapter.submitList(requests)
//
//                    // Show notification for new requests
//                    if (requests.isNotEmpty()) {
//                        Toast.makeText(this@DriverRequestsActivity,
//                            "${requests.size} new request(s) available!",
//                            Toast.LENGTH_SHORT).show()
//                    }
//                }
//            } catch (e: Exception) {
//                Toast.makeText(this@DriverRequestsActivity, "Error loading requests: ${e.message}", Toast.LENGTH_SHORT).show()
//            } finally {
//                binding.progressBar.visibility = android.view.View.GONE
//            }
//        }
//    }
//
//    private fun setupRealtimeListener() {
//        driverRepository.listenForNewRequests { newRequest ->
//            // New request received in real-time
//            runOnUiThread {
//                Toast.makeText(this, "New ambulance request received!", Toast.LENGTH_LONG).show()
//                loadPendingRequests() // Refresh the list
//            }
//        }
//    }
//
//    private fun acceptRequest(request: Request) {
//        val driverId = currentDriverId
//        if (driverId == null) {
//            Toast.makeText(this, "Driver profile not found", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        lifecycleScope.launch {
//            try {
//                driverRepository.acceptRequest(request.id!!, driverId)
//                Toast.makeText(this@DriverRequestsActivity, "Request accepted successfully!", Toast.LENGTH_SHORT).show()
//                loadPendingRequests() // Refresh list
//            } catch (e: Exception) {
//                Toast.makeText(this@DriverRequestsActivity, "Failed to accept request: ${e.message}", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        // Clean up real-time listeners if needed
//    }
//}