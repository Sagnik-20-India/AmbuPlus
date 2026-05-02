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

    // NEW: Get driver by driver ID
    suspend fun getDriverById(driverId: String): Driver? {
        return try {
            println("DEBUG: Fetching driver by ID: $driverId")
            val result = supabase.from("drivers").select {
                filter {
                    eq("id", driverId)
                }
            }.decodeSingleOrNull<Driver>()
            println("DEBUG: Driver found by ID: ${result != null}")
            result
        } catch (e: Exception) {
            println("DEBUG: Error in getDriverById: ${e.message}")
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

    // Update driver's live location for tracking
    suspend fun updateLiveLocation(driverId: String, locationString: String) {
        try {
            supabase.from("drivers").update(
                mapOf("current_location" to locationString)
            ) {
                filter {
                    eq("id", driverId)
                }
            }
            println("DEBUG: Updated live location for driver $driverId: $locationString")
        } catch (e: Exception) {
            println("DEBUG: Error updating live location: ${e.message}")
        }
    }

    // NEW: Get driver's current location
    suspend fun getDriverLocation(driverId: String): String? {
        return try {
            val driver = getDriverById(driverId)
            driver?.currentLocation
        } catch (e: Exception) {
            println("DEBUG: Error getting driver location: ${e.message}")
            null
        }
    }
}