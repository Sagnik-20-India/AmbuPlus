package com.example.ambuplus.data

import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.gotrue.user.UserInfo
import io.github.jan.supabase.gotrue.auth
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthRepository {

    private val supabase = SupabaseClientProvider.client
    private val auth = supabase.auth

    // Sign up user - Get user from current session
    suspend fun signUp(email: String, password: String, name: String): Result<UserInfo> {
        return try {
            auth.signUpWith(io.github.jan.supabase.gotrue.providers.builtin.Email) {
                this.email = email
                this.password = password
                data = buildJsonObject {
                    put("full_name", name)
                }
            }

            // Get user from current session after signup
            val user = auth.currentUserOrNull()
                ?: throw Exception("Signup successful but user session not found")

            Result.success(user)
        } catch (e: RestException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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