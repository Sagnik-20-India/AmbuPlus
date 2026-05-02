package com.example.ambuplus.uiactivities.driver

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.ambuplus.databinding.ItemRequestBinding
import com.example.ambuplus.models.Request
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.Locale

class RequestAdapter(
    private val onAcceptClick: (Request) -> Unit,
    private val onDenyClick: (Request) -> Unit
) : RecyclerView.Adapter<RequestAdapter.RequestViewHolder>() {

    private var requests: List<Request> = emptyList()
    private var driverLocation: GeoPoint? = null

    // ADD THIS FUNCTION - Returns current list of requests
    fun getCurrentList(): List<Request> {
        return requests
    }

    fun submitList(newList: List<Request>, driverLoc: GeoPoint? = null) {
        requests = newList
        driverLocation = driverLoc
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val binding = ItemRequestBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RequestViewHolder(binding, onAcceptClick, onDenyClick)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        holder.bind(requests[position], driverLocation)
    }

    override fun getItemCount(): Int = requests.size

    class RequestViewHolder(
        private val binding: ItemRequestBinding,
        private val onAcceptClick: (Request) -> Unit,
        private val onDenyClick: (Request) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(request: Request, driverLocation: GeoPoint?) {
            val patientName = request.patientName ?: "Not provided"
            binding.tvPatientName.text = "Patient: $patientName"

            // Display location
            val address = request.location.split("|").firstOrNull() ?: request.location
            binding.tvLocation.text = "📍 ${address.take(60)}"

            // Calculate and show distance
            if (driverLocation != null) {
                val distance = calculateDistanceToPatient(request, driverLocation)
                if (distance != Double.MAX_VALUE) {
                    val distanceText = when {
                        distance < 1 -> "${String.format("%.0f", distance * 1000)} m away"
                        else -> "${String.format("%.1f", distance)} km away"
                    }
                    binding.tvDistance.text = "🚗 $distanceText"
                    binding.tvDistance.visibility = android.view.View.VISIBLE
                } else {
                    binding.tvDistance.visibility = android.view.View.GONE
                }
            } else {
                binding.tvDistance.visibility = android.view.View.GONE
            }

            // Emergency level
            val emergencyLevel = request.emergencyLevel.uppercase()
            binding.tvEmergencyLevel.text = "⚠️ $emergencyLevel"

            val color = when (request.emergencyLevel.lowercase()) {
                "high" -> android.graphics.Color.RED
                "medium" -> android.graphics.Color.parseColor("#FF9800")
                else -> android.graphics.Color.parseColor("#4CAF50")
            }
            binding.tvEmergencyLevel.setTextColor(color)

            // Contact number
            binding.tvContactNumber.text = "📞 ${request.contactNumber}"

            // Format timestamp
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val date = request.timestamp?.let { inputFormat.parse(it) }
                val outputFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                binding.tvTimestamp.text = date?.let { outputFormat.format(it) } ?: request.timestamp
            } catch (e: Exception) {
                binding.tvTimestamp.text = request.timestamp
            }

            binding.btnAccept.setOnClickListener {
                onAcceptClick(request)
            }

            binding.btnDeny.setOnClickListener {
                onDenyClick(request)
            }
        }

        private fun calculateDistanceToPatient(request: Request, driverLocation: GeoPoint): Double {
            try {
                val coords = extractCoordinatesFromLocation(request.location)
                if (coords != null) {
                    val patientLocation = GeoPoint(coords.first, coords.second)
                    return calculateDistance(driverLocation, patientLocation)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return Double.MAX_VALUE
        }

        private fun extractCoordinatesFromLocation(location: String): Pair<Double, Double>? {
            return try {
                if (location.contains("|")) {
                    val parts = location.split("|")
                    if (parts.size == 2) {
                        val coords = parts[1].split(",")
                        if (coords.size == 2) {
                            Pair(coords[0].toDouble(), coords[1].toDouble())
                        } else null
                    } else null
                } else null
            } catch (e: Exception) {
                null
            }
        }

        private fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Double {
            val lat1 = Math.toRadians(point1.latitude)
            val lat2 = Math.toRadians(point2.latitude)
            val dLat = Math.toRadians(point2.latitude - point1.latitude)
            val dLon = Math.toRadians(point2.longitude - point1.longitude)

            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(lat1) * Math.cos(lat2) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

            return 6371 * c
        }
    }
}