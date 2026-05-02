package com.example.ambuplus.uiactivities.main

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import android.widget.LinearLayout
import android.widget.Button
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.ambuplus.R
import com.example.ambuplus.databinding.ActivityMainBinding
import com.example.ambuplus.uiactivities.login.LoginActivity
import com.example.ambuplus.uiactivities.request.RequestActivity
import com.example.ambuplus.uiactivities.request.RequestDetailActivity
import com.example.ambuplus.uiactivities.driver.DriverRegistrationActivity
import com.example.ambuplus.uiactivities.driver.DriverRequestsActivity
import com.example.ambuplus.uiactivities.driver.DriverProfileActivity
import com.example.ambuplus.uiactivities.driver.DriverRatingsActivity
import com.example.ambuplus.uiactivities.driver.DriverTrackingActivity
import com.example.ambuplus.uiactivities.patient.PatientTrackingActivity
import com.example.ambuplus.models.AuthViewModel
import com.example.ambuplus.models.Request
import com.example.ambuplus.models.RequestViewModel
import com.example.ambuplus.utils.ServiceLocator
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var authViewModel: AuthViewModel
    private lateinit var requestViewModel: RequestViewModel
    private var isPatientMode = true // Default mode

    private lateinit var patientView: LinearLayout
    private lateinit var driverView: LinearLayout
    private lateinit var btnPatientMode: Button
    private lateinit var btnDriverMode: Button
    private lateinit var cardRequestAmbulance: CardView
    private lateinit var btnEmergency: Button
    private lateinit var cardEmergencyContacts: CardView
    private lateinit var cardRequestHistory: CardView
    private lateinit var cardProfile: CardView
    private lateinit var cardViewRequests: CardView
    private lateinit var cardRatings: CardView
    private lateinit var cardDriveHistory: CardView
    private lateinit var cardDriverProfile: CardView
    private lateinit var progressBar: android.widget.ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeViews()

        val authFactory = AuthViewModelFactory(ServiceLocator.authRepository)
        authViewModel = ViewModelProvider(this, authFactory)[AuthViewModel::class.java]

        val requestFactory = RequestViewModelFactory(ServiceLocator.requestRepository)
        requestViewModel = ViewModelProvider(this, requestFactory)[RequestViewModel::class.java]

        setupObservers()
        setupClickListeners()
        updateToggleUI()
    }

    private fun initializeViews() {
        patientView = findViewById(R.id.patientView)
        driverView = findViewById(R.id.driverView)
        btnPatientMode = findViewById(R.id.btnPatientMode)
        btnDriverMode = findViewById(R.id.btnDriverMode)
        cardRequestAmbulance = findViewById(R.id.cardRequestAmbulance)
        btnEmergency = findViewById(R.id.btnEmergency)
        cardEmergencyContacts = findViewById(R.id.cardEmergencyContacts)
        cardRequestHistory = findViewById(R.id.cardRequestHistory)
        cardProfile = findViewById(R.id.cardProfile)
        cardViewRequests = findViewById(R.id.cardViewRequests)
        cardRatings = findViewById(R.id.cardRatings)
        cardDriveHistory = findViewById(R.id.cardDriveHistory)
        cardDriverProfile = findViewById(R.id.cardDriverProfile)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            authViewModel.currentUser.collect { user ->
                if (user == null) {
                    navigateToLogin()
                } else {
                    if (isPatientMode) {
                        requestViewModel.loadUserRequests(user.id)
                    }
                }
            }
        }

        lifecycleScope.launch {
            requestViewModel.requests.collect { requests ->
                if (requests.isNotEmpty() && isPatientMode) {
                    Toast.makeText(this@MainActivity, "You have ${requests.size} recent requests", Toast.LENGTH_SHORT).show()
                }
            }
        }

        lifecycleScope.launch {
            requestViewModel.isLoading.collect { isLoading ->
                progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }
    }

    private fun setupClickListeners() {
        btnPatientMode.setOnClickListener {
            if (!isPatientMode) {
                switchToPatientMode()
            }
        }

        btnDriverMode.setOnClickListener {
            if (isPatientMode) {
                switchToDriverMode()
            }
        }

        cardRequestAmbulance.setOnClickListener {
            if (isPatientMode) {
                navigateToRequestAmbulance()
            }
        }

        btnEmergency.setOnClickListener {
            if (isPatientMode) {
                navigateToRequestAmbulance()
            }
        }

        cardEmergencyContacts.setOnClickListener {
            if (isPatientMode) {
                Toast.makeText(this, "Emergency Contacts - Coming Soon", Toast.LENGTH_SHORT).show()
            }
        }

        cardRequestHistory.setOnClickListener {
            if (isPatientMode) {
                Toast.makeText(this, "Request History - Coming Soon", Toast.LENGTH_SHORT).show()
            }
        }

        cardProfile.setOnClickListener {
            if (isPatientMode) {
                Toast.makeText(this, "Profile - Coming Soon", Toast.LENGTH_SHORT).show()
            }
        }

        cardViewRequests.setOnClickListener {
            if (!isPatientMode) {
                val intent = Intent(this, DriverRequestsActivity::class.java)
                startActivity(intent)
            }
        }

        cardDriveHistory.setOnClickListener {
            if (!isPatientMode) {
                Toast.makeText(this, "Drive History - Coming Soon", Toast.LENGTH_SHORT).show()
            }
        }

        cardDriverProfile.setOnClickListener {
            if (!isPatientMode) {
                val intent = Intent(this, DriverProfileActivity::class.java)
                startActivity(intent)
            }
        }

        cardRatings.setOnClickListener {
            if (!isPatientMode) {
                val intent = Intent(this, DriverRatingsActivity::class.java)
                startActivity(intent)
            }
        }
    }

    private fun switchToPatientMode() {
        isPatientMode = true
        updateToggleUI()
        patientView.visibility = View.VISIBLE
        driverView.visibility = View.GONE
        authViewModel.currentUser.value?.let { user ->
            requestViewModel.loadUserRequests(user.id)
        }
        Toast.makeText(this, "Patient Mode Activated", Toast.LENGTH_SHORT).show()
    }

    private fun switchToDriverMode() {
        val currentUser = authViewModel.currentUser.value
        if (currentUser == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                val isRegistered = ServiceLocator.driverRepository.isUserRegisteredAsDriver(currentUser.id)
                println("DEBUG: Driver registration check - User: ${currentUser.id}, Registered: $isRegistered")

                if (isRegistered) {
                    isPatientMode = false
                    updateToggleUI()
                    binding.patientView.visibility = View.GONE
                    binding.driverView.visibility = View.VISIBLE
                    Toast.makeText(this@MainActivity, "Driver Mode Activated", Toast.LENGTH_SHORT).show()
                } else {
                    println("DEBUG: Navigating to driver registration")
                    navigateToDriverRegistration()
                }
            } catch (e: Exception) {
                println("DEBUG: Error checking driver status: ${e.message}")
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "Error checking driver status: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun navigateToDriverRegistration() {
        val intent = Intent(this, DriverRegistrationActivity::class.java)
        startActivity(intent)
    }

    private fun updateToggleUI() {
        if (isPatientMode) {
            btnPatientMode.setBackgroundColor(getColor(R.color.primary_color))
            btnPatientMode.setTextColor(getColor(android.R.color.white))
            btnDriverMode.setBackgroundColor(getColor(android.R.color.white))
            btnDriverMode.setTextColor(getColor(R.color.primary_color))
        } else {
            btnDriverMode.setBackgroundColor(getColor(R.color.primary_color))
            btnDriverMode.setTextColor(getColor(android.R.color.white))
            btnPatientMode.setBackgroundColor(getColor(android.R.color.white))
            btnPatientMode.setTextColor(getColor(R.color.primary_color))
        }
    }

    private fun navigateToRequestAmbulance() {
        val intent = Intent(this, RequestActivity::class.java)
        startActivity(intent)
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_logout -> {
                authViewModel.logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

class AuthViewModelFactory(private val authRepository: com.example.ambuplus.data.AuthRepository) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class RequestViewModelFactory(private val requestRepository: com.example.ambuplus.data.RequestRepository) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RequestViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RequestViewModel(requestRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}