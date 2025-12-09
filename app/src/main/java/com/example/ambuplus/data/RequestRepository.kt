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

    suspend fun updateRequestStatus(requestId: Int, status: String, driverId: String? = null) {
        // Build the update data based on whether driverId is provided
        if (driverId != null) {
            // Update both status and driver_id
            supabase.from("requests").update(
                mapOf(
                    "status" to status,
                    "driver_id" to driverId
                )
            ) {
                filter {
                    eq("id", requestId)
                }
            }
        } else {
            // Update only status
            supabase.from("requests").update(
                mapOf("status" to status)
            ) {
                filter {
                    eq("id", requestId)
                }
            }
        }
    }
}