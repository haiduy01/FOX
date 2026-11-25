package com.example.data.location

import com.example.BuildConfig
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Strictly separates REAL device GPS / geocoder coordinates from DEMO preset / map-tap data.
 * DEMO coordinates MUST NEVER be presented as real GPS or Google Routes API telemetry.
 */
enum class LocationDataSource {
  REAL_GPS,
  REAL_GEOCODER,
  DEMO_PRESET,
  DEMO_MAP_PICK,
}

enum class MapRenderMode {
  REAL_GOOGLE_MAPS,
  DEMO_INTERACTIVE_MAP,
}

data class MapConfigStatus(
  val isRealMapsKeyConfigured: Boolean,
  val activeRenderMode: MapRenderMode,
  val statusBannerTitle: String,
  val statusBannerMessage: String,
  val isRealGoogleMapsFeatureLocked: Boolean = !isRealMapsKeyConfigured,
  val isRealRoutesApiFeatureLocked: Boolean = !isRealMapsKeyConfigured,
  val lockedFeatureSummary: String = if (isRealMapsKeyConfigured) {
    "Google Maps SDK đã sẵn sàng."
  } else {
    "Google Maps SDK & Routes API thật: TẠM KHÓA (chưa có MAPS_API_KEY)."
  },
) {
  companion object {
    const val PLACEHOLDER_KEY = "MAPS_API_KEY_NOT_CONFIGURED"

    fun fromApiKey(rawKey: String?): MapConfigStatus {
      val trimmed = rawKey?.trim().orEmpty()
      val isConfigured =
        trimmed.length >= 20 &&
          trimmed != PLACEHOLDER_KEY &&
          !trimmed.startsWith("YOUR_", ignoreCase = true) &&
          !trimmed.contains("PLACEHOLDER", ignoreCase = true) &&
          !trimmed.contains("NOT_CONFIGURED", ignoreCase = true)

      return if (isConfigured) {
        MapConfigStatus(
          isRealMapsKeyConfigured = true,
          activeRenderMode = MapRenderMode.REAL_GOOGLE_MAPS,
          statusBannerTitle = "Google Maps SDK đã cấu hình API Key",
          statusBannerMessage =
            "Bản đồ tương tác Google Maps SDK sẵn sàng. Bạn vẫn có thể chuyển sang Bản đồ DEMO bất cứ lúc nào.",
          isRealGoogleMapsFeatureLocked = false,
          isRealRoutesApiFeatureLocked = false,
          lockedFeatureSummary = "Google Maps SDK đã sẵn sàng.",
        )
      } else {
        MapConfigStatus(
          isRealMapsKeyConfigured = false,
          activeRenderMode = MapRenderMode.DEMO_INTERACTIVE_MAP,
          statusBannerTitle = "BẢN ĐỒ DEMO — CHƯA CẤU HÌNH MAPS_API_KEY",
          statusBannerMessage =
            "Chưa có MAPS_API_KEY nên Google Maps SDK và Google Routes API thật đang TẠM KHÓA. Ứng dụng giữ nguyên module GPS/bản đồ và chạy chế độ Bản đồ DEMO có nhãn rõ ràng.",
          isRealGoogleMapsFeatureLocked = true,
          isRealRoutesApiFeatureLocked = true,
          lockedFeatureSummary = "Google Maps SDK & Routes API thật: TẠM KHÓA (chưa có MAPS_API_KEY).",
        )
      }
    }

    fun fromBuildConfig(): MapConfigStatus = fromApiKey(BuildConfig.MAPS_API_KEY)
  }
}

data class GeoPointData(
  val latitude: Double,
  val longitude: Double,
  val label: String = "",
  val address: String = "",
  val accuracyMeters: Float = 0f,
  val source: LocationDataSource = LocationDataSource.DEMO_PRESET,
) {
  val isDemo: Boolean
    get() = source == LocationDataSource.DEMO_PRESET || source == LocationDataSource.DEMO_MAP_PICK

  fun isValidCoordinate(): Boolean =
    latitude.isFinite() &&
      longitude.isFinite() &&
      latitude in -90.0..90.0 &&
      longitude in -180.0..180.0
}

sealed interface GpsLocationState {
  data object Idle : GpsLocationState
  data object Fetching : GpsLocationState
  data object PermissionDenied : GpsLocationState
  data object GpsDisabled : GpsLocationState
  data object Offline : GpsLocationState
  data class Success(val location: GeoPointData) : GpsLocationState
  data class Error(val message: String) : GpsLocationState
}

data class VerifiedRouteInfo(
  val origin: GeoPointData,
  val destination: GeoPointData,
  val distanceMeters: Int,
  val distanceKm: Int,
  val durationMinutes: Int,
  val encodedPolyline: String,
  val routePoints: List<GeoPointData>,
  val routeSource: String, // "google_routes_v2" | "server_haversine_demo_estimate" | "client_demo_estimate"
  val isDemoRoute: Boolean,
  val isServerVerified: Boolean,
  val disclaimer: String,
)

sealed interface RouteComputationState {
  data object Idle : RouteComputationState
  data object Loading : RouteComputationState
  data class Ready(val route: VerifiedRouteInfo) : RouteComputationState
  data class Error(val message: String) : RouteComputationState
}

enum class DriverTrackingStopReason(val labelVi: String) {
  USER_STOPPED("Tài xế đã chủ động tắt chia sẻ vị trí."),
  ORDER_COMPLETED("Đơn hàng đã hoàn thành — tự động dừng chia sẻ và xóa vị trí trực tiếp."),
  ORDER_CANCELLED("Đơn hàng đã hủy — tự động dừng chia sẻ và xóa vị trí trực tiếp."),
  PERMISSION_REVOKED("Quyền vị trí đã bị tắt — dừng chia sẻ vị trí tức thì."),
  GPS_DISABLED("GPS trên thiết bị đang tắt — tạm dừng chia sẻ vị trí."),
  DRIVER_OFFLINE("Tài xế đã chuyển ngoại tuyến — dừng chia sẻ vị trí."),
  SIGNED_OUT("Đã đăng xuất — dừng chia sẻ vị trí."),
}

/**
 * Client-side rate limiter matching Firestore Rules (server enforces >= 4s; client throttles to >= 5s or >= 15m).
 * Prevents high-frequency Firestore write storms and excessive battery/network usage.
 */
class DriverLocationRateLimiter(
  private val minIntervalMs: Long = 5_000L,
  private val minDistanceMeters: Double = 15.0,
) {
  private var lastPublishedTimeMs: Long = 0L
  private var lastLat: Double? = null
  private var lastLng: Double? = null

  fun shouldPublish(
    latitude: Double,
    longitude: Double,
    nowMs: Long,
    force: Boolean = false,
  ): Boolean {
    if (lastPublishedTimeMs == 0L || lastLat == null || lastLng == null) {
      return true
    }
    val elapsed = nowMs - lastPublishedTimeMs
    if (elapsed < minIntervalMs) {
      return false
    }
    if (force) return true
    val movedMeters = FoxMapGeometry.haversineMeters(lastLat!!, lastLng!!, latitude, longitude)
    // Allow heartbeat publish after 20s even if stationary, otherwise require >= minDistanceMeters
    return movedMeters >= minDistanceMeters || elapsed >= (minIntervalMs * 4L)
  }

  fun recordPublished(latitude: Double, longitude: Double, nowMs: Long) {
    lastPublishedTimeMs = nowMs
    lastLat = latitude
    lastLng = longitude
  }

  fun reset() {
    lastPublishedTimeMs = 0L
    lastLat = null
    lastLng = null
  }
}

object FoxMapGeometry {
  private const val EARTH_RADIUS_METERS = 6_371_000.0
  private const val ROAD_FACTOR = 1.25

  fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val rLat1 = Math.toRadians(lat1)
    val rLat2 = Math.toRadians(lat2)
    val a =
      sin(dLat / 2.0) * sin(dLat / 2.0) +
        cos(rLat1) * cos(rLat2) * sin(dLng / 2.0) * sin(dLng / 2.0)
    val c = 2.0 * atan2(sqrt(a), sqrt(max(0.0, 1.0 - a)))
    return EARTH_RADIUS_METERS * c
  }

  /**
   * Decodes Google Encoded Polyline Algorithm Format into GeoPointData vertices.
   */
  fun decodePolyline(encoded: String, source: LocationDataSource = LocationDataSource.DEMO_PRESET): List<GeoPointData> {
    if (encoded.isBlank()) return emptyList()
    val result = mutableListOf<GeoPointData>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0

    while (index < len) {
      var b: Int
      var shift = 0
      var delta = 0
      do {
        if (index >= len) break
        b = encoded[index++].code - 63
        delta = delta or ((b and 0x1f) shl shift)
        shift += 5
      } while (b >= 0x20 && index < len)
      val dLat = if ((delta and 1) != 0) (delta shr 1).inv() else (delta shr 1)
      lat += dLat

      shift = 0
      delta = 0
      do {
        if (index >= len) break
        b = encoded[index++].code - 63
        delta = delta or ((b and 0x1f) shl shift)
        shift += 5
      } while (b >= 0x20 && index < len)
      val dLng = if ((delta and 1) != 0) (delta shr 1).inv() else (delta shr 1)
      lng += dLng

      result.add(
        GeoPointData(
          latitude = lat / 1e5,
          longitude = lng / 1e5,
          source = source,
        )
      )
    }
    return result
  }

  /**
   * Builds a transparent DEMO route preview using the exact same Haversine + 1.25 road factor
   * as functions/src/routeService.ts so previews stay consistent when Cloud Functions or Google Routes API are unavailable.
   */
  fun estimateDemoPreviewRoute(
    origin: GeoPointData,
    destination: GeoPointData,
    vehicleType: String?,
    disclaimer: String,
  ): VerifiedRouteInfo {
    val straightMeters = haversineMeters(
      origin.latitude,
      origin.longitude,
      destination.latitude,
      destination.longitude,
    )
    val distanceMeters = max(100, (straightMeters * ROAD_FACTOR).roundToInt())
    val distanceKm = max(1, ceil(distanceMeters / 1000.0).toInt())
    val speedKmh = if (vehicleType?.contains("Car", ignoreCase = true) == true) 22.0 else 28.0
    val durationMinutes = max(2, ceil((distanceMeters / 1000.0 / speedKmh) * 60.0).toInt())

    val midLat = (origin.latitude + destination.latitude) / 2.0 + 0.0018
    val midLng = (origin.longitude + destination.longitude) / 2.0 - 0.0012
    val pts = listOf(
      origin,
      GeoPointData(midLat, midLng, source = LocationDataSource.DEMO_PRESET),
      destination,
    )

    return VerifiedRouteInfo(
      origin = origin,
      destination = destination,
      distanceMeters = distanceMeters,
      distanceKm = distanceKm,
      durationMinutes = durationMinutes,
      encodedPolyline = "",
      routePoints = pts,
      routeSource = "client_demo_estimate",
      isDemoRoute = true,
      isServerVerified = false,
      disclaimer = disclaimer,
    )
  }
}
