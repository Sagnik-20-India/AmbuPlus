package com.example.ambuplus.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class Driver(
    val id: String? = null,
    @SerialName("user_id")
    val userId: String,
    val name: String,
    val phone: String,
    @SerialName("vehicle_number")
    val vehicleNumber: String,
    @SerialName("license_number")
    val licenseNumber: String,
    @SerialName("vehicle_type")
    val vehicleType: String,
    val available: Boolean = false,
    @SerialName("current_location")
    val currentLocation: String? = null,
    @SerialName("is_verified")
    val isVerified: Boolean = false,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)