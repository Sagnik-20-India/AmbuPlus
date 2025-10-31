package com.example.ambuplus.data

import com.example.ambuplus.models.Driver
import io.github.jan.supabase.postgrest.from

class DriverRepository {

    private val supabase = SupabaseClientProvider.client

    suspend fun registerDriver(driver: Driver): Driver {
        return supabase.from("drivers").insert(driver).decodeSingle()
    }

    suspend fun isUserRegisteredAsDriver(userId: String): Boolean {
        return try {
            println("DEBUG: Checking if user $userId is registered as driver")
            val driver = getDriverByUserId(userId)
            println("DEBUG: Driver found: ${driver != null}")
            driver != null
        } catch (e: Exception) {
            println("DEBUG: Error in isUserRegisteredAsDriver: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    suspend fun getDriverByUserId(userId: String): Driver? {
        return try {
            println("DEBUG: Fetching driver for user ID: $userId")
            val result = supabase.from("drivers").select {
                filter {
                    eq("user_id", userId)
                }
            }.decodeSingleOrNull<Driver>()
            println("DEBUG: Driver query result: $result")
            result
        } catch (e: Exception) {
            println("DEBUG: Error in getDriverByUserId: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    suspend fun updateDriverAvailability(driverId: String, available: Boolean) {
        supabase.from("drivers").update(
            mapOf("available" to available)
        ) {
            filter {
                eq("id", driverId)
            }
        }
    }

    suspend fun updateDriverProfile(driver: Driver) {
        supabase.from("drivers").update(
            mapOf(
                "name" to driver.name,
                "phone" to driver.phone,
                "license_number" to driver.licenseNumber,
                "vehicle_number" to driver.vehicleNumber,
                "vehicle_type" to driver.vehicleType
            )
        ) {
            filter {
                eq("id", driver.id!!)
            }
        }
    }

    // Get available drivers (for patient requests)
    suspend fun getAvailableDrivers(): List<Driver> {
        return supabase.from("drivers").select {
            filter {
                eq("available", true)
                eq("is_verified", true)
            }
        }.decodeList()
    }

//    // Add to DriverRepository.kt
//    suspend fun getPendingRequests(): List<Request> {
//        return supabase.from("requests").select {
//            filter {
//                eq("status", "pending")
//            }
//            orderBy("timestamp", ascending = false)
//        }.decodeList()
//    }
//
//    suspend fun acceptRequest(requestId: Int, driverId: String) {
//        supabase.from("requests").update(
//            mapOf(
//                "status" to "accepted",
//                "driver_id" to driverId
//            )
//        ) {
//            filter {
//                eq("id", requestId)
//            }
//        }
//    }
//
//    // Real-time listener for new requests
//    fun listenForNewRequests(onNewRequest: (Request) -> Unit) {
//        supabase.postgrest["requests"].select().subscribe {
//            it.exception?.let { error ->
//                println("Realtime error: ${error.message}")
//            }
//
//            it.data?.let { data ->
//                when (data) {
//                    is PostgresAction.INSERT -> {
//                        val newRequest = data.record.decode<Request>()
//                        if (newRequest.status == "pending") {
//                            onNewRequest(newRequest)
//                        }
//                    }
//                    else -> {}
//                }
//            }
//        }
//    }

}