package com.example.ambuplus.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class Request(
    val id: Int? = null,
    @SerialName("user_id")
    val userId: String,
    @SerialName("patient_name")
    val patientName: String? = null,
    @SerialName("patient_age")
    val patientAge: Int? = null,
    @SerialName("contact_number")
    val contactNumber: String,
    val location: String,
    @SerialName("medical_notes")
    val medicalNotes: String? = null,
    @SerialName("emergency_level")
    val emergencyLevel: String = "medium",
    val status: String = "pending",
    @SerialName("driver_id")
    val driverId: String? = null,
    val timestamp: String,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)