package com.example.data.location

import android.content.Context
import android.location.Geocoder
import com.example.data.remote.FoxFunctionsClient
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Map & Routing repository with strict separation between:
 *  - REAL data (Android Geocoder + Cloud Functions computeVerifiedRoute / Google Routes API v2)
 *  - DEMO data (curated HCMC preset landmarks + transparent Haversine preview, NEVER labeled as real GPS/Routes)
 */
class FoxMapRepository(
  private val appContext: Context?,
  private val functionsClient: FoxFunctionsClient?,
  private val locationProvider: FoxLocationProvider,
  val mapConfigStatus: MapConfigStatus = MapConfigStatus.fromBuildConfig(),
) {
  companion object {
    /**
     * Explicit DEMO preset landmarks in Ho Chi Minh City for offline/unconfigured API key testing.
     * Every entry is explicitly tagged with LocationDataSource.DEMO_PRESET.
     */
    val DEMO_HCMC_PRESET_PLACES: List<GeoPointData> = listOf(
      GeoPointData(
        latitude = 10.7725,
        longitude = 106.6980,
        label = "Chợ Bến Thành (DEMO)",
        address = "Chợ Bến Thành, Lê Lợi, Quận 1, TP.HCM",
        source = LocationDataSource.DEMO_PRESET,
      ),
      GeoPointData(
        latitude = 10.7798,
        longitude = 106.6990,
        label = "Nhà thờ Đức Bà (DEMO)",
        address = "01 Công xã Paris, Bến Nghé, Quận 1, TP.HCM",
        source = LocationDataSource.DEMO_PRESET,
      ),
      GeoPointData(
        latitude = 10.7950,
        longitude = 106.7218,
        label = "Landmark 81 (DEMO)",
        address = "720A Điện Biên Phủ, Bình Thạnh, TP.HCM",
        source = LocationDataSource.DEMO_PRESET,
      ),
      GeoPointData(
        latitude = 10.8188,
        longitude = 106.6519,
        label = "Sân bay Tân Sơn Nhất (DEMO)",
        address = "Trường Sơn, Phường 2, Tân Bình, TP.HCM",
        source = LocationDataSource.DEMO_PRESET,
      ),
      GeoPointData(
        latitude = 10.7295,
        longitude = 106.7219,
        label = "Crescent Mall Quận 7 (DEMO)",
        address = "101 Tôn Dật Tiên, Tân Phú, Quận 7, TP.HCM",
        source = LocationDataSource.DEMO_PRESET,
      ),
      GeoPointData(
        latitude = 10.7626,
        longitude = 106.6602,
        label = "ĐH Bách Khoa TP.HCM (DEMO)",
        address = "268 Lý Thường Kiệt, Quận 10, TP.HCM",
        source = LocationDataSource.DEMO_PRESET,
      ),
    )
  }

  /**
   * Reverse-geocodes real GPS coordinates into a human-readable address when online.
   * Preserves LocationDataSource.REAL_GPS so callers know coordinates came from real hardware GPS.
   */
  suspend fun reverseGeocodeRealLocation(point: GeoPointData): GeoPointData {
    if (point.isDemo || appContext == null || !locationProvider.isNetworkAvailable()) {
      return point
    }
    return withContext(Dispatchers.IO) {
      try {
        if (!Geocoder.isPresent()) return@withContext point
        val geocoder = Geocoder(appContext, Locale("vi", "VN"))
        @Suppress("DEPRECATION")
        val results = geocoder.getFromLocation(point.latitude, point.longitude, 1)
        val addr = results?.firstOrNull()?.getAddressLine(0)?.trim()
        if (!addr.isNullOrBlank()) {
          point.copy(label = "Vị trí GPS hiện tại", address = addr)
        } else {
          point
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        point
      }
    }
  }

  /**
   * Searches addresses:
   * - In DEMO mode (or offline / unavailable Geocoder): searches DEMO_HCMC_PRESET_PLACES (tagged DEMO_PRESET).
   * - In REAL mode (online + Geocoder present): queries Android Geocoder (tagged REAL_GEOCODER).
   */
  suspend fun searchPlaces(query: String, useDemoMode: Boolean): List<GeoPointData> {
    val clean = query.trim()
    if (clean.isBlank()) {
      return if (useDemoMode) DEMO_HCMC_PRESET_PLACES else emptyList()
    }

    if (useDemoMode || appContext == null || !locationProvider.isNetworkAvailable()) {
      return DEMO_HCMC_PRESET_PLACES.filter { place ->
        place.label.contains(clean, ignoreCase = true) ||
          place.address.contains(clean, ignoreCase = true)
      }
    }

    return withContext(Dispatchers.IO) {
      try {
        if (!Geocoder.isPresent()) return@withContext emptyList()
        val geocoder = Geocoder(appContext, Locale("vi", "VN"))
        @Suppress("DEPRECATION")
        val matches = geocoder.getFromLocationName("$clean, Ho Chi Minh City, Vietnam", 5)
          .orEmpty()
        matches.map { addr ->
          val fullLine = addr.getAddressLine(0)?.trim().orEmpty()
          val title = addr.featureName?.takeIf { it.isNotBlank() && it != fullLine }
            ?: fullLine.substringBefore(",")
          GeoPointData(
            latitude = addr.latitude,
            longitude = addr.longitude,
            label = title.ifBlank { clean },
            address = fullLine.ifBlank { "$clean (${String.format("%.4f", addr.latitude)}, ${String.format("%.4f", addr.longitude)})" },
            source = LocationDataSource.REAL_GEOCODER,
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        emptyList()
      }
    }
  }

  /**
   * Computes route distance, ETA, and polyline.
   * When allowServerCall is true and online, calls Cloud Functions `computeVerifiedRoute`
   * (which calls Google Routes API v2 if server key is configured, or returns an honest server DEMO estimate).
   * If Cloud Functions are not yet deployed or offline, falls back to a clearly marked DEMO preview
   * (isServerVerified = false, isDemoRoute = true) and NEVER claims it is a Google Routes API result.
   */
  suspend fun computeRoute(
    origin: GeoPointData,
    destination: GeoPointData,
    vehicleType: String?,
    allowServerCall: Boolean = true,
  ): RouteComputationState {
    if (!origin.isValidCoordinate() || !destination.isValidCoordinate()) {
      return RouteComputationState.Error("Tọa độ điểm đón hoặc điểm đến không hợp lệ.")
    }
    val straightMeters = FoxMapGeometry.haversineMeters(
      origin.latitude,
      origin.longitude,
      destination.latitude,
      destination.longitude,
    )
    if (straightMeters < 20.0) {
      return RouteComputationState.Error("Điểm đón và điểm đến quá gần (< 20m). Vui lòng chọn hai điểm khác nhau.")
    }

    if (allowServerCall && functionsClient != null && locationProvider.isNetworkAvailable()) {
      val serverResult = functionsClient.computeVerifiedRoute(
        originLat = origin.latitude,
        originLng = origin.longitude,
        destLat = destination.latitude,
        destLng = destination.longitude,
        vehicleType = vehicleType,
      )
      serverResult.onSuccess { res ->
        val decoded = FoxMapGeometry.decodePolyline(
          encoded = res.encodedPolyline,
          source = if (res.isDemoRoute) LocationDataSource.DEMO_PRESET else LocationDataSource.REAL_GEOCODER,
        )
        val polylinePoints = if (decoded.size >= 2) decoded else listOf(origin, destination)
        return RouteComputationState.Ready(
          VerifiedRouteInfo(
            origin = origin,
            destination = destination,
            distanceMeters = res.distanceMeters,
            distanceKm = res.distanceKm,
            durationMinutes = res.durationMinutes,
            encodedPolyline = res.encodedPolyline,
            routePoints = polylinePoints,
            routeSource = res.routeSource,
            isDemoRoute = res.isDemoRoute,
            isServerVerified = true,
            disclaimer = res.disclaimer,
          )
        )
      }
    }

    val fallbackNotice = if (!locationProvider.isNetworkAvailable()) {
      "Đang ngoại tuyến: hiển thị lộ trình ước tính DEMO cục bộ (không phải Google Routes API)."
    } else {
      "Cloud Function computeVerifiedRoute chưa triển khai: hiển thị quãng đường ước tính DEMO (giá cuối cùng sẽ do server xác thực)."
    }

    return RouteComputationState.Ready(
      FoxMapGeometry.estimateDemoPreviewRoute(
        origin = origin,
        destination = destination,
        vehicleType = vehicleType,
        disclaimer = fallbackNotice,
      )
    )
  }
}
