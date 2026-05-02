package com.example.ambuplus.utils

import com.example.ambuplus.data.AuthRepository
import com.example.ambuplus.data.DriverRepository
import com.example.ambuplus.data.RequestRepository
import com.example.ambuplus.data.SupabaseClientProvider

object ServiceLocator {
    val authRepository: AuthRepository by lazy { AuthRepository() }
    val requestRepository: RequestRepository by lazy { RequestRepository() }
    val driverRepository: DriverRepository by lazy { DriverRepository() }
    val supabaseClient by lazy { SupabaseClientProvider.client }
}