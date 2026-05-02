package com.example.ambuplus.utils

import retrofit2.http.GET
import retrofit2.http.Query

interface NominatimApiService {
    @GET("search")
    suspend fun searchAddress(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 5,
        @Query("addressdetails") addressDetails: Int = 1
    ): List<NominatimResult>

    // NEW: Reverse geocoding - get address from coordinates
    @GET("reverse")
    suspend fun reverseGeocode(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1
    ): NominatimResult?
}

data class NominatimResult(
    val lat: String,
    val lon: String,
    val display_name: String,
    val address: AddressDetails? = null
)

data class AddressDetails(
    val road: String? = null,
    val suburb: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val postcode: String? = null
)