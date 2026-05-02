package com.example.ambuplus.data

import com.example.ambuplus.models.Request
import io.github.jan.supabase.postgrest.from

class RequestRepository {

    private val supabase = SupabaseClientProvider.client

    suspend fun addRequest(request: Request) {
        supabase.from("requests").insert(request)
    }

    suspend fun getAllRequests(): List<Request> {
        return supabase.from("requests").select().decodeList()
    }

    suspend fun getRequestsByUser(userId: String): List<Request> {
        return supabase.from("requests").select {
            filter {
                eq("user_id", userId)
            }
        }.decodeList()
    }

    suspend fun getPendingRequests(): List<Request> {
        return supabase.from("requests").select {
            filter {
                eq("status", "pending")  // Only pending, not cancelled or accepted
            }
        }.decodeList()
    }

    suspend fun getRequestById(requestId: Int): Request? {
        return supabase.from("requests").select {
            filter {
                eq("id", requestId)
            }
        }.decodeSingleOrNull()
    }

    // Get active request (pending or accepted) for a user
    suspend fun getActiveRequest(userId: String): Request? {
        return try {
            val requests = supabase.from("requests").select {
                filter {
                    eq("user_id", userId)
                    and {
                        or {
                            eq("status", "pending")
                            eq("status", "accepted")
                        }
                    }
                }
            }.decodeList<Request>()

            // Return the most recent active request
            requests.maxByOrNull { it.timestamp ?: "" }
        } catch (e: Exception) {
            println("DEBUG: Error checking active request: ${e.message}")
            null
        }
    }

    // Get active request for a driver (accepted request they are handling - NOT cancelled)
    suspend fun getActiveRequestForDriver(driverId: String): Request? {
        return try {
            println("DEBUG: Getting active request for driver ID: $driverId")
            val requests = supabase.from("requests").select {
                filter {
                    eq("driver_id", driverId)
                    eq("status", "accepted")  // Only get accepted, not cancelled
                }
            }.decodeList<Request>()

            println("DEBUG: Found ${requests.size} accepted requests for driver")
            requests.maxByOrNull { it.timestamp ?: "" }
        } catch (e: Exception) {
            println("DEBUG: Error checking driver active request: ${e.message}")
            null
        }
    }

    suspend fun updateRequestStatus(requestId: Int, status: String, driverId: String? = null) {
        try {
            val updateData = mutableMapOf<String, String>(
                "status" to status
            )

            driverId?.let {
                updateData["driver_id"] = it
            }

            supabase.from("requests").update(updateData) {
                filter {
                    eq("id", requestId)
                }
            }
            println("DEBUG: Successfully updated request $requestId to status: $status")
        } catch (e: Exception) {
            println("DEBUG: Error updating request: ${e.message}")
            throw e
        }
    }
}