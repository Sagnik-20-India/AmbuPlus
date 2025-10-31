package com.example.ambuplus.utils

import com.example.ambuplus.data.AuthRepository
import com.example.ambuplus.data.DriverRepository
import com.example.ambuplus.data.RequestRepository

object ServiceLocator {
    val authRepository: AuthRepository by lazy { AuthRepository() }
    val requestRepository: RequestRepository by lazy { RequestRepository() }
    val driverRepository: DriverRepository by lazy { DriverRepository() }
}