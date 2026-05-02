package com.example.ambuplus.data

import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.gotrue.user.UserInfo
import io.github.jan.supabase.gotrue.auth
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthRepository {

    private val supabase = SupabaseClientProvider.client
    private val auth = supabase.auth

    // Simple email validation - checks if email has @ and a valid domain
    fun isValidEmail(email: String): Boolean {
        // Must contain @ and have at least one dot after the @
        if (!email.contains("@")) return false
        val domain = email.substringAfter("@")
        return domain.contains(".") && domain.length >= 4
    }

    // Sign up user - Get user from current session
    suspend fun signUp(email: String, password: String, name: String): Result<UserInfo> {
        // First, validate email format
        if (!isValidEmail(email)) {
            return Result.failure(Exception("Please enter a valid email address (e.g., name@gmail.com)"))
        }

        return try {
            auth.signUpWith(io.github.jan.supabase.gotrue.providers.builtin.Email) {
                this.email = email
                this.password = password
                data = buildJsonObject {
                    put("full_name", name)
                }
            }

            // Check if user was created and confirmed
            val user = auth.currentUserOrNull()

            // If email confirmation is enabled, user might be null initially
            // Return success with user (which might be null)
            if (user == null) {
                // Signup successful but waiting for email confirmation
                // We still return a successful Result but with null user
                // The ViewModel will handle this case separately
                @Suppress("UNCHECKED_CAST")
                return Result.success(null as UserInfo)
            }

            Result.success(user)
        } catch (e: RestException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
//    suspend fun signUp(email: String, password: String, name: String): Result<UserInfo> {
//        // First, validate email format
//        if (!isValidEmail(email)) {
//            return Result.failure(Exception("Please enter a valid email address (e.g., name@gmail.com)"))
//        }
//
//        return try {
//            auth.signUpWith(io.github.jan.supabase.gotrue.providers.builtin.Email) {
//                this.email = email
//                this.password = password
//                data = buildJsonObject {
//                    put("full_name", name)
//                }
//            }
//
//            // Get user from current session after signup
//            val user = auth.currentUserOrNull()
//                ?: throw Exception("Signup successful but user session not found")
//
//            Result.success(user)
//        } catch (e: RestException) {
//            Result.failure(e)
//        } catch (e: Exception) {
//            Result.failure(e)
//        }
//    }

    // Login user - Get user from current session
    suspend fun login(email: String, password: String): Result<UserInfo> {
        return try {
            auth.signInWith(io.github.jan.supabase.gotrue.providers.builtin.Email) {
                this.email = email
                this.password = password
            }

            // Get user from current session after login
            val user = auth.currentUserOrNull()
                ?: throw Exception("Login successful but user session not found")

            Result.success(user)
        } catch (e: RestException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Add this function to your AuthRepository.kt
    suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            auth.resetPasswordForEmail(email)
            Result.success(Unit)
        } catch (e: RestException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Logout user
    suspend fun logout() {
        auth.signOut()
    }

    // Get current user
    fun currentUser(): UserInfo? = auth.currentUserOrNull()

    // Update profile - will be used later for profile editing features
    @Suppress("UNUSED")
    suspend fun updateProfile(name: String?, phone: String?) {
        auth.updateUser {
            data = buildJsonObject {
                put("full_name", name ?: "")
                put("phone", phone ?: "")
            }
        }
    }
}