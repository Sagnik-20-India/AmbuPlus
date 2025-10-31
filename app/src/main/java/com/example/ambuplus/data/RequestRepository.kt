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
        val updateData = mutableMapOf<String, Any>("status" to status)
        driverId?.let { updateData["driver_id"] = it }

        supabase.from("requests").update(updateData) {
            filter {
                eq("id", requestId)
            }
        }
    }
}