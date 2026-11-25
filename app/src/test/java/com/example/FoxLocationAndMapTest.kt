package com.example

import com.example.data.location.DriverLocationRateLimiter
import com.example.data.location.FoxLocationProvider
import com.example.data.location.FoxMapGeometry
import com.example.data.location.FoxMapRepository
import com.example.data.location.GeoPointData
import com.example.data.location.GpsLocationState
import com.example.data.location.LocationDataSource
import com.example.data.location.MapConfigStatus
import com.example.data.location.MapRenderMode
import com.example.data.location.RouteComputationState
import com.example.data.remote.CloudOrder
import com.example.data.repository.FoxDemoPricingEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeFoxLocationProvider(
  private val permissionGranted: Boolean = false,
  private val gpsEnabled: Boolean = true,
  private val networkAvailable: Boolean = true,
  private val fakePoint: GeoPointData? = null,
) : FoxLocationProvider {
  override fun hasLocationPermission(): Boolean = permissionGranted
  override fun isGpsEnabled(): Boolean = gpsEnabled
  override fun isNetworkAvailable(): Boolean = networkAvailable

  override suspend fun fetchCurrentLocationOnce(): GpsLocationState {
    if (!permissionGranted) return GpsLocationState.PermissionDenied
    if (!gpsEnabled) return GpsLocationState.GpsDisabled
    if (fakePoint != null) return GpsLocationState.Success(fakePoint)
    if (!networkAvailable) return GpsLocationState.Offline
    return GpsLocationState.Error("No GPS fix")
  }

  override fun observeForegroundDriverLocation(intervalMs: Long): Flow<GpsLocationState> {
    if (!permissionGranted) return flowOf(GpsLocationState.PermissionDenied)
    if (!gpsEnabled) return flowOf(GpsLocationState.GpsDisabled)
    return if (fakePoint != null) flowOf(GpsLocationState.Success(fakePoint)) else flowOf(GpsLocationState.Offline)
  }
}

class FoxLocationAndMapTest {

  @Test
  fun gpsPermissionDenied_neverFakesRealGpsCoordinates() = runBlocking {
    val provider = FakeFoxLocationProvider(permissionGranted = false, gpsEnabled = true)
    val state = provider.fetchCurrentLocationOnce()
    assertEquals(GpsLocationState.PermissionDenied, state)
  }

  @Test
  fun gpsDisabledAndOffline_returnExplicitHonestStates() = runBlocking {
    val gpsOff = FakeFoxLocationProvider(permissionGranted = true, gpsEnabled = false)
    assertEquals(GpsLocationState.GpsDisabled, gpsOff.fetchCurrentLocationOnce())

    val offline = FakeFoxLocationProvider(permissionGranted = true, gpsEnabled = true, networkAvailable = false, fakePoint = null)
    assertEquals(GpsLocationState.Offline, offline.fetchCurrentLocationOnce())
  }

  @Test
  fun realGpsVsDemoPreset_areStrictlySeparatedBySource() = runBlocking {
    val realGps = GeoPointData(
      latitude = 10.7769,
      longitude = 106.7009,
      label = "Vị trí GPS hiện tại",
      address = "Quận 1, TP.HCM",
      accuracyMeters = 12f,
      source = LocationDataSource.REAL_GPS,
    )
    val provider = FakeFoxLocationProvider(permissionGranted = true, gpsEnabled = true, fakePoint = realGps)
    val fetched = provider.fetchCurrentLocationOnce() as GpsLocationState.Success
    assertFalse("REAL GPS must not be tagged as DEMO", fetched.location.isDemo)
    assertEquals(LocationDataSource.REAL_GPS, fetched.location.source)

    val mapRepo = FoxMapRepository(appContext = null, functionsClient = null, locationProvider = provider)
    val demoMatches = mapRepo.searchPlaces("Landmark", useDemoMode = true)
    assertTrue("DEMO search must return HCMC preset", demoMatches.isNotEmpty())
    assertTrue("DEMO search results must always carry isDemo = true", demoMatches.all { it.isDemo })
    assertTrue(demoMatches.all { it.source == LocationDataSource.DEMO_PRESET })
  }

  @Test
  fun mapConfigStatus_switchesCleanlyBetweenDemoAndRealGoogleMaps() {
    val unconfigured = MapConfigStatus.fromApiKey(MapConfigStatus.PLACEHOLDER_KEY)
    assertFalse(unconfigured.isRealMapsKeyConfigured)
    assertEquals(MapRenderMode.DEMO_INTERACTIVE_MAP, unconfigured.activeRenderMode)
    assertTrue(unconfigured.statusBannerTitle.contains("BẢN ĐỒ DEMO"))

    val blankKey = MapConfigStatus.fromApiKey("")
    assertFalse(blankKey.isRealMapsKeyConfigured)
    assertEquals(MapRenderMode.DEMO_INTERACTIVE_MAP, blankKey.activeRenderMode)

    val validKey = MapConfigStatus.fromApiKey("AIzaSyFakeValidFormatKeyForUnitTest12345")
    assertTrue(validKey.isRealMapsKeyConfigured)
    assertEquals(MapRenderMode.REAL_GOOGLE_MAPS, validKey.activeRenderMode)
  }

  @Test
  fun routeComputation_rejectsInvalidOrTooClosePoints_andComputesHonestFallbackWhenOffline() = runBlocking {
    val provider = FakeFoxLocationProvider(permissionGranted = true, gpsEnabled = true, networkAvailable = false)
    val mapRepo = FoxMapRepository(appContext = null, functionsClient = null, locationProvider = provider)

    val invalid = GeoPointData(latitude = 120.0, longitude = 106.7, source = LocationDataSource.DEMO_PRESET)
    val benThanh = FoxMapRepository.DEMO_HCMC_PRESET_PLACES[0]
    val airport = FoxMapRepository.DEMO_HCMC_PRESET_PLACES[3]

    assertTrue(mapRepo.computeRoute(invalid, airport, "FOX Bike") is RouteComputationState.Error)
    assertTrue(mapRepo.computeRoute(benThanh, benThanh, "FOX Bike") is RouteComputationState.Error)

    val result = mapRepo.computeRoute(benThanh, airport, "FOX Bike")
    assertTrue(result is RouteComputationState.Ready)
    val route = (result as RouteComputationState.Ready).route
    assertTrue("Distance Ben Thanh -> Tan Son Nhat should be 7-11 km", route.distanceKm in 7..11)
    assertTrue("ETA should be positive", route.durationMinutes >= 10)
    assertTrue("Offline route must be marked DEMO", route.isDemoRoute)
    assertFalse("Offline route must not claim server verification", route.isServerVerified)
  }

  @Test
  fun polylineDecoder_accuratelyRestoresCoordinates() {
    // Standard Google Encoded Polyline example: (38.5, -120.2), (40.7, -120.95), (43.252, -126.453)
    val decoded = FoxMapGeometry.decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
    assertEquals(3, decoded.size)
    assertEquals(38.5, decoded[0].latitude, 0.001)
    assertEquals(-120.2, decoded[0].longitude, 0.001)
    assertEquals(40.7, decoded[1].latitude, 0.001)
    assertEquals(-120.95, decoded[1].longitude, 0.001)
    assertEquals(43.252, decoded[2].latitude, 0.001)
    assertEquals(-126.453, decoded[2].longitude, 0.001)
  }

  @Test
  fun deliveryPricingWithDistanceKm_matchesServerAndPreservesLegacy3ArgCompatibility() {
    // Legacy 3-arg call (offline DEMO & backward compatibility)
    val legacy = FoxDemoPricingEngine.estimateDelivery("Dưới 2 kg", "Tiêu chuẩn", null)
    assertEquals(20_000L, legacy.totalVnd)

    // <= 3 km has no distance surcharge
    val shortDist = FoxDemoPricingEngine.estimateDelivery("Dưới 2 kg", "Tiêu chuẩn", null, distanceKm = 3)
    assertEquals(20_000L, shortDist.totalVnd)

    // 10 km adds (10 - 3) * 4_000 = 28_000 -> 20_000 + 8_000 (2-5kg) + 28_000 = 56_000
    val longDist = FoxDemoPricingEngine.estimateDelivery("2–5 kg", "Tiêu chuẩn", null, distanceKm = 10)
    assertEquals(56_000L, longDist.totalVnd)

    // Voucher FOXSHIP15 deducts 10_000
    val withVoucher = FoxDemoPricingEngine.estimateDelivery("2–5 kg", "Tiêu chuẩn", "FOXSHIP15", distanceKm = 10)
    assertEquals(46_000L, withVoucher.totalVnd)
  }

  @Test
  fun driverLocationRateLimiter_blocksRapidWritesAndSmallJitter() {
    val limiter = DriverLocationRateLimiter(minIntervalMs = 5_000L, minDistanceMeters = 15.0)
    val t0 = 1_000_000L

    // First publish is always allowed
    assertTrue(limiter.shouldPublish(10.7725, 106.6980, t0))
    limiter.recordPublished(10.7725, 106.6980, t0)

    // 2 seconds later even with big movement -> blocked by 5s rate limit
    assertFalse(limiter.shouldPublish(10.7750, 106.7000, t0 + 2_000L))

    // 6 seconds later with tiny 2m jitter -> blocked by 15m threshold
    assertFalse(limiter.shouldPublish(10.77251, 106.69801, t0 + 6_000L))

    // 6 seconds later with > 50m movement -> allowed
    assertTrue(limiter.shouldPublish(10.7732, 106.6985, t0 + 6_000L))
    limiter.recordPublished(10.7732, 106.6985, t0 + 6_000L)

    // Stationary heartbeat allowed after >= 20s (4x minInterval)
    assertTrue(limiter.shouldPublish(10.7732, 106.6985, t0 + 26_500L))

    // Reset allows immediate next publish
    limiter.reset()
    assertTrue(limiter.shouldPublish(10.7732, 106.6985, t0 + 26_600L))
  }

  @Test
  fun orderLiveTrackingEligibility_stopsAfterCompletionOrCancellation() {
    val accepted = CloudOrder(id = "ORD-1", driverId = "drv_1", status = "driver_accepted")
    val arrived = CloudOrder(id = "ORD-1", driverId = "drv_1", status = "driver_arrived")
    val inProgress = CloudOrder(id = "ORD-1", driverId = "drv_1", status = "in_progress")
    val pending = CloudOrder(id = "ORD-1", driverId = "", status = "pending")
    val completed = CloudOrder(id = "ORD-1", driverId = "drv_1", status = "completed")
    val cancelled = CloudOrder(id = "ORD-1", driverId = "drv_1", status = "cancelled")

    assertTrue(accepted.isActiveDriverTrackingEligible())
    assertTrue(arrived.isActiveDriverTrackingEligible())
    assertTrue(inProgress.isActiveDriverTrackingEligible())

    assertFalse(pending.isActiveDriverTrackingEligible())
    assertFalse("Must stop tracking after completion", completed.isActiveDriverTrackingEligible())
    assertFalse("Must stop tracking after cancellation", cancelled.isActiveDriverTrackingEligible())
  }
}
