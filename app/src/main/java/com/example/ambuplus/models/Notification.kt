package com.example.ambuplus.models

import kotlinx.serialization.Serializable

@Serializable
data class Notification(
    val id: Int? = null,
    val driverId: String,
    val requestId: Int,
    val message: String,
    val isRead: Boolean = false,
    val createdAt: String? = null
)