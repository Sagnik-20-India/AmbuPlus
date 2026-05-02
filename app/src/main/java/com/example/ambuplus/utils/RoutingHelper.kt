
package com.example.ambuplus.utils

import android.content.Context
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.bonuspack.routing.GraphHopperRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

class RoutingHelper(private val context: Context) {

    companion object {
        private const val TAG = "RoutingHelper"
        // REPLACE THIS WITH YOUR ACTUAL GRAPHHOPPER API KEY
        private const val GRAPHHOPPER_API_KEY = "52cbb22e-9350-46a1-abdc-3390c3bf1b3b"
    }

    private val roadManager: GraphHopperRoadManager by lazy {
        Log.d(TAG, "Initializing GraphHopperRoadManager")
        GraphHopperRoadManager(GRAPHHOPPER_API_KEY, true).apply {
            addRequestOption("vehicle=car")
            addRequestOption("weighting=fastest")
            addRequestOption("locale=en")
            // Add user agent to avoid being blocked
            addRequestOption("key=${GRAPHHOPPER_API_KEY}")
        }
    }

    suspend fun getRoute(
        start: GeoPoint,
        end: GeoPoint,
        onSuccess: (Road) -> Unit,
        onError: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Getting route from: ${start.latitude},${start.longitude} to: ${end.latitude},${end.longitude}")

                val waypoints = ArrayList<GeoPoint>().apply {
                    add(start)
                    add(end)
                }

                val road = roadManager.getRoad(waypoints)

                withContext(Dispatchers.Main) {
                    // CHANGED: Also check if length > 0
                    if (road.mStatus == Road.STATUS_OK && road.mLength > 0) {
                        Log.d(TAG, "✅ Route success! Length: ${road.mLength}m")
                        onSuccess(road)
                    } else {
                        // CHANGED: More detailed error messages
                        val errorMsg = when (road.mStatus) {
                            Road.STATUS_INVALID -> "Invalid route (points too close or no road connection)"
                            Road.STATUS_TECHNICAL_ISSUE -> "Technical issue - Likely API key invalid or network blocked"
                            else -> "Unknown error (status: ${road.mStatus})"
                        }

                        Log.e(TAG, "❌ Routing failed: $errorMsg")
                        onError(errorMsg)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Routing exception: ${e.message}")
                withContext(Dispatchers.Main) {
                    onError("Exception: ${e.message}")
                }
            }
        }
    }

    fun drawRoadOnMap(map: MapView, road: Road) {
        clearRouteFromMap(map)
        val roadOverlay = org.osmdroid.bonuspack.routing.RoadManager.buildRoadOverlay(road)
        roadOverlay.color = Color.BLUE
        roadOverlay.width = 14f
        map.overlays.add(roadOverlay)
        map.invalidate()
        map.zoomToBoundingBox(road.mBoundingBox, true)
        Log.d(TAG, "Road drawn on map")
    }

    fun clearRouteFromMap(map: MapView) {
        map.overlays.removeAll { it is Polyline }
        map.invalidate()
    }
}








//package com.example.ambuplus.utils
//
//import android.content.Context
//import android.graphics.Color
//import android.util.Log
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//import org.osmdroid.bonuspack.routing.OSRMRoadManager
//import org.osmdroid.bonuspack.routing.Road
//import org.osmdroid.util.GeoPoint
//import org.osmdroid.views.MapView
//import org.osmdroid.views.overlay.Polyline
//
//class RoutingHelper(private val context: Context) {
//
//    companion object {
//        private const val TAG = "RoutingHelper"
//
//        // Public OSRM server (no API key required)
//        private const val OSRM_URL = "https://routing.openstreetmap.de/routed-car/route/v1/driving/"
//    }
//
//    private val roadManager: OSRMRoadManager by lazy {
//        Log.d(TAG, "Initializing OSRMRoadManager")
//        OSRMRoadManager(context, "AmbuPlus-App").apply {
//            setService(OSRM_URL)
//            addRequestOption("overview=full")      // ✅ full geometry
//            addRequestOption("geometries=polyline") // ✅ better path
//            addRequestOption("steps=false")        // ✅ faster response
//        }
//    }
//
//    suspend fun getRoute(
//        start: GeoPoint,
//        end: GeoPoint,
//        onSuccess: (Road) -> Unit,
//        onError: (String) -> Unit
//    ) {
//        withContext(Dispatchers.IO) {
//
//            var road: Road? = null
//            var errorMsg = ""
//
//            repeat(2) { attempt ->   // ✅ retry 2 times
//                try {
//                    Log.d(
//                        TAG,
//                        "Attempt $attempt → ${start.latitude},${start.longitude} → ${end.latitude},${end.longitude}"
//                    )
//
//                    val waypoints = arrayListOf(start, end)
//                    val result = roadManager.getRoad(waypoints)
//
//                    if (result.mStatus == Road.STATUS_OK && result.mLength > 0) {
//                        road = result
//                        return@repeat
//                    } else {
//                        errorMsg = "Status: ${result.mStatus}"
//                    }
//
//                } catch (e: Exception) {
//                    errorMsg = e.message ?: "Unknown error"
//                }
//
//                Thread.sleep(800) // small delay before retry
//            }
//
//            withContext(Dispatchers.Main) {
//                if (road != null) {
//                    Log.d("ROUTE_DEBUG", "✅ Route success: ${road!!.mLength} meters")
//                    onSuccess(road!!)
//                } else {
//                    Log.e("ROUTE_DEBUG", "❌ OSRM ERROR: $errorMsg")  // 👈 ADD HERE
//                    onError(errorMsg)
//                }
//            }
//        }
//    }
//
//    fun drawRoadOnMap(map: MapView, road: Road) {
//        clearRouteFromMap(map)
//
//        val roadOverlay = org.osmdroid.bonuspack.routing.RoadManager.buildRoadOverlay(road)
//        roadOverlay.color = Color.BLUE
//        roadOverlay.width = 14f
//
//        map.overlays.add(roadOverlay)
//        map.invalidate()
//
//        Log.d(TAG, "Road drawn")
//    }
//
//    fun clearRouteFromMap(map: MapView) {
//        map.overlays.removeAll { it is Polyline }
//        map.invalidate()
//    }
//
//
//}
//
//