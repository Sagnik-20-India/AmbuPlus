package com.example.ambuplus.uiactivities.login

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.ambuplus.databinding.ActivityLoginBinding
import com.example.ambuplus.uiactivities.main.MainActivity
import com.example.ambuplus.models.AuthViewModel
import com.example.ambuplus.utils.ServiceLocator
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(ServiceLocator.authRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        // Observe currentUser StateFlow
        lifecycleScope.launch {
            authViewModel.currentUser.collect { user ->
                if (user != null) {
                    navigateToMain()
                }
            }
        }

        // Observe isLoading StateFlow
        lifecycleScope.launch {
            authViewModel.isLoading.collect { isLoading ->
                binding.progressBar.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
                binding.btnLogin.isEnabled = !isLoading
            }
        }

        // Observe errorMessage StateFlow
        lifecycleScope.launch {
            authViewModel.errorMessage.collect { error ->
                if (error != null) {
                    binding.tvError.text = error
                    binding.tvError.visibility = android.view.View.VISIBLE

                } else {
                    binding.tvError.visibility = android.view.View.GONE
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (validateInputs(email, password)) {
                authViewModel.login(email, password)
            }
        }

        binding.tvSignUp.setOnClickListener {
            showSimpleSignUpDialog()
        }

        binding.tvError.setOnClickListener {
            authViewModel.clearError()
        }
    }

    private fun showSimpleSignUpDialog() {
        // Create a simple dialog with input fields programmatically
        val inputLayout = android.widget.LinearLayout(this)
        inputLayout.orientation = android.widget.LinearLayout.VERTICAL
        inputLayout.setPadding(50, 20, 50, 20)

        val etName = EditText(this)
        etName.hint = "Full Name"
        inputLayout.addView(etName)

        val etEmail = EditText(this)
        etEmail.hint = "Email"
        etEmail.inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        inputLayout.addView(etEmail)

        val etPassword = EditText(this)
        etPassword.hint = "Password"
        etPassword.inputType = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        inputLayout.addView(etPassword)

        val etConfirmPassword = EditText(this)
        etConfirmPassword.hint = "Confirm Password"
        etConfirmPassword.inputType = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        inputLayout.addView(etConfirmPassword)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Create Account")
            .setView(inputLayout)
            .setPositiveButton("Sign Up") { dialog, _ ->
                val name = etName.text.toString().trim()
                val email = etEmail.text.toString().trim()
                val password = etPassword.text.toString().trim()
                val confirmPassword = etConfirmPassword.text.toString().trim()

                if (validateSignUpInputs(name, email, password, confirmPassword)) {
                    authViewModel.signUp(email, password, name)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun validateInputs(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            showError("Please enter your email")
            return false
        }

        if (password.isEmpty()) {
            showError("Please enter your password")
            return false
        }

        if (password.length < 6) {
            showError("Password must be at least 6 characters")
            return false
        }

        return true
    }

    private fun validateSignUpInputs(name: String, email: String, password: String, confirmPassword: String): Boolean {
        if (name.isEmpty()) {
            showError("Please enter your name")
            return false
        }

        if (email.isEmpty()) {
            showError("Please enter your email")
            return false
        }

        if (password.isEmpty()) {
            showError("Please enter your password")
            return false
        }

        if (password.length < 6) {
            showError("Password must be at least 6 characters")
            return false
        }

        if (password != confirmPassword) {
            showError("Passwords do not match")
            return false
        }

        return true
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}

// Factory class for AuthViewModel
class AuthViewModelFactory(private val authRepository: com.example.ambuplus.data.AuthRepository) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}