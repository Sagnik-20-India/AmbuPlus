package com.example.ambuplus.uiactivities.login

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ambuplus.R
import com.example.ambuplus.databinding.ActivityResetPasswordBinding
import com.example.ambuplus.utils.ServiceLocator
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.OtpType
import kotlinx.coroutines.launch
import android.util.Log

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResetPasswordBinding
    private lateinit var etNewPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnUpdatePassword: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        etNewPassword = binding.etNewPassword
        etConfirmPassword = binding.etConfirmPassword
        btnUpdatePassword = binding.btnUpdatePassword
        progressBar = binding.progressBar

        Toast.makeText(this, "Enter your new password", Toast.LENGTH_LONG).show()

        setupClickListeners()
    }

    private fun setupClickListeners() {
        btnUpdatePassword.setOnClickListener {
            updatePassword()
        }
    }

    private fun updatePassword() {
        val newPassword = etNewPassword.text.toString().trim()
        val confirmPassword = etConfirmPassword.text.toString().trim()

        if (!validatePasswords(newPassword, confirmPassword)) {
            return
        }

        progressBar.visibility = android.view.View.VISIBLE
        btnUpdatePassword.isEnabled = false

        lifecycleScope.launch {
            try {
                val supabase = ServiceLocator.supabaseClient

                // ✅ CRITICAL FIX: Check session before updating password
                val session = supabase.auth.currentSessionOrNull()

                if (session == null) {
                    Toast.makeText(
                        this@ResetPasswordActivity,
                        "Session expired. Please login again.",
                        Toast.LENGTH_LONG
                    ).show()

                    progressBar.visibility = android.view.View.GONE
                    btnUpdatePassword.isEnabled = true
                    return@launch
                }

                // ✅ Update password only if session exists
                supabase.auth.updateUser {
                    password = newPassword
                }

                Toast.makeText(
                    this@ResetPasswordActivity,
                    "Password updated successfully! Please login with your new password.",
                    Toast.LENGTH_LONG
                ).show()

                // Sign out after password change
                supabase.auth.signOut()

                finish()

            } catch (e: Exception) {
                Log.e("ResetPassword", "Error", e)

                Toast.makeText(
                    this@ResetPasswordActivity,
                    "Failed to update password: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                progressBar.visibility = android.view.View.GONE
                btnUpdatePassword.isEnabled = true
            }
        }
    }

    private fun validatePasswords(newPassword: String, confirmPassword: String): Boolean {
        if (newPassword.isEmpty()) {
            Toast.makeText(this, "Please enter a new password", Toast.LENGTH_SHORT).show()
            return false
        }

        if (newPassword.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            return false
        }

        if (newPassword != confirmPassword) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }
}



//package com.example.ambuplus.uiactivities.login
//
//import android.os.Bundle
//import android.widget.Button
//import android.widget.EditText
//import android.widget.ProgressBar
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import androidx.lifecycle.lifecycleScope
//import com.example.ambuplus.R
//import com.example.ambuplus.databinding.ActivityResetPasswordBinding
//import com.example.ambuplus.utils.ServiceLocator
//import io.github.jan.supabase.gotrue.Auth
//import io.github.jan.supabase.gotrue.auth
//import io.github.jan.supabase.gotrue.OtpType
//import kotlinx.coroutines.launch
//import android.util.Log
//
//class ResetPasswordActivity : AppCompatActivity() {
//
//    private lateinit var binding: ActivityResetPasswordBinding
//    private lateinit var etNewPassword: EditText
//    private lateinit var etConfirmPassword: EditText
//    private lateinit var btnUpdatePassword: Button
//    private lateinit var progressBar: ProgressBar
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        etNewPassword = binding.etNewPassword
//        etConfirmPassword = binding.etConfirmPassword
//        btnUpdatePassword = binding.btnUpdatePassword
//        progressBar = binding.progressBar
//
//        // Show that the activity is ready
//        Toast.makeText(this, "Enter your new password", Toast.LENGTH_LONG).show()
//
//        setupClickListeners()
//    }
//
//    private fun setupClickListeners() {
//        btnUpdatePassword.setOnClickListener {
//            updatePassword()
//        }
//    }
//
//    private fun updatePassword() {
//        val newPassword = etNewPassword.text.toString().trim()
//        val confirmPassword = etConfirmPassword.text.toString().trim()
//
//        if (!validatePasswords(newPassword, confirmPassword)) {
//            return
//        }
//
//        progressBar.visibility = android.view.View.VISIBLE
//        btnUpdatePassword.isEnabled = false
//
//        lifecycleScope.launch {
//            try {
//                val supabase = ServiceLocator.supabaseClient
//
//                // Just update the password - Supabase handles the session automatically
//                supabase.auth.updateUser {
//                    password = newPassword
//                }
//
//                Toast.makeText(
//                    this@ResetPasswordActivity,
//                    "Password updated successfully! Please login with your new password.",
//                    Toast.LENGTH_LONG
//                ).show()
//
//                // Sign out to ensure user logs in with new password
//                supabase.auth.signOut()
//
//                // Navigate back to login screen
//                finish()
//            } catch (e: Exception) {
//                Toast.makeText(
//                    this@ResetPasswordActivity,
//                    "Failed to update password: ${e.message}",
//                    Toast.LENGTH_LONG
//                ).show()
//            } finally {
//                progressBar.visibility = android.view.View.GONE
//                btnUpdatePassword.isEnabled = true
//            }
//        }
//    }
//
//    private fun validatePasswords(newPassword: String, confirmPassword: String): Boolean {
//        if (newPassword.isEmpty()) {
//            Toast.makeText(this, "Please enter a new password", Toast.LENGTH_SHORT).show()
//            return false
//        }
//
//        if (newPassword.length < 6) {
//            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
//            return false
//        }
//
//        if (newPassword != confirmPassword) {
//            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
//            return false
//        }
//
//        return true
//    }
//}