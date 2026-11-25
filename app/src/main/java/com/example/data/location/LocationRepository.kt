package com.example.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Abstraction over Android foreground GPS / Fused Location Provider.
 * Strictly separates real device telemetry from DEMO presets and enables clean unit testing.
 * NEVER requests or uses ACCESS_BACKGROUND_LOCATION.
 */
interface FoxLocationProvider {
  fun hasLocationPermission(): Boolean
  fun isGpsEnabled(): Boolean
  fun isNetworkAvailable(): Boolean
  suspend fun fetchCurrentLocationOnce(): GpsLocationState
  fun observeForegroundDriverLocation(intervalMs: Long = 5_000L): Flow<GpsLocationState>
}

class FusedFoxLocationRepository(
  context: Context,
) : FoxLocationProvider {
  private val appContext = context.applicationContext
  private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(appContext) }

  override fun hasLocationPermission(): Boolean {
    val fine =
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarse =
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    return fine || coarse
  }

  override fun isGpsEnabled(): Boolean {
    val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
      ?: return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      manager.isLocationEnabled
    } else {
      @Suppress("DEPRECATION")
      manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
  }

  override fun isNetworkAvailable(): Boolean {
    val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  @SuppressLint("MissingPermission")
  override suspend fun fetchCurrentLocationOnce(): GpsLocationState {
    if (!hasLocationPermission()) {
      return GpsLocationState.PermissionDenied
    }
    if (!isGpsEnabled()) {
      return GpsLocationState.GpsDisabled
    }

    val cancellation = CancellationTokenSource()
    return try {
      val current: Location? =
        try {
          fusedClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellation.token,
          ).await()
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          null
        }

      val resolved = current ?: fusedClient.lastLocation.await()
      if (resolved != null) {
        GpsLocationState.Success(
          GeoPointData(
            latitude = resolved.latitude,
            longitude = resolved.longitude,
            label = "Vị trí GPS hiện tại",
            address = "GPS (${String.format("%.5f", resolved.latitude)}, ${String.format("%.5f", resolved.longitude)})",
            accuracyMeters = resolved.accuracy,
            source = LocationDataSource.REAL_GPS,
          )
        )
      } else if (!isNetworkAvailable()) {
        GpsLocationState.Offline
      } else {
        GpsLocationState.Error("Chưa nhận được tín hiệu GPS. Vui lòng di chuyển ra nơi thoáng hoặc thử lại.")
      }
    } catch (e: CancellationException) {
      cancellation.cancel()
      throw e
    } catch (e: SecurityException) {
      GpsLocationState.PermissionDenied
    } catch (e: Exception) {
      GpsLocationState.Error(e.localizedMessage ?: "Không thể lấy vị trí GPS hiện tại.")
    }
  }

  /**
   * Foreground-only continuous location stream for an approved Driver who explicitly opted in.
   * Stops immediately when collected coroutine is cancelled (awaitClose removes updates).
   */
  @SuppressLint("MissingPermission")
  override fun observeForegroundDriverLocation(intervalMs: Long): Flow<GpsLocationState> = callbackFlow {
    if (!hasLocationPermission()) {
      trySend(GpsLocationState.PermissionDenied)
      close()
      return@callbackFlow
    }
    if (!isGpsEnabled()) {
      trySend(GpsLocationState.GpsDisabled)
      close()
      return@callbackFlow
    }

    val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs.coerceAtLeast(4_000L))
      .setMinUpdateIntervalMillis(4_000L)
      .setMinUpdateDistanceMeters(10f)
      .build()

    val callback = object : LocationCallback() {
      override fun onLocationResult(result: LocationResult) {
        if (!hasLocationPermission()) {
          trySend(GpsLocationState.PermissionDenied)
          close()
          return
        }
        if (!isGpsEnabled()) {
          trySend(GpsLocationState.GpsDisabled)
          close()
          return
        }
        val loc = result.lastLocation ?: return
        trySend(
          GpsLocationState.Success(
            GeoPointData(
              latitude = loc.latitude,
              longitude = loc.longitude,
              label = "GPS tài xế trực tiếp",
              address = "GPS (${String.format("%.5f", loc.latitude)}, ${String.format("%.5f", loc.longitude)})",
              accuracyMeters = loc.accuracy,
              source = LocationDataSource.REAL_GPS,
            )
          )
        )
      }
    }

    try {
      fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
    } catch (_: SecurityException) {
      trySend(GpsLocationState.PermissionDenied)
      close()
      return@callbackFlow
    } catch (e: Exception) {
      trySend(GpsLocationState.Error(e.localizedMessage ?: "Không thể khởi động theo dõi GPS."))
      close()
      return@callbackFlow
    }

    awaitClose {
      fusedClient.removeLocationUpdates(callback)
    }
  }
}
