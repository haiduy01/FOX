package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.location.GeoPointData
import com.example.data.location.MapConfigStatus
import com.example.data.location.MapRenderMode
import com.example.data.remote.CloudDriverLiveLocation
import com.example.ui.theme.FoxBlue
import com.example.ui.theme.FoxGreen
import com.example.ui.theme.FoxOrange
import com.example.ui.theme.FoxOrangeDeep
import com.example.ui.theme.FoxTextSecondary
import com.example.ui.theme.FoxTextStrong
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlin.math.max
import kotlin.math.min

// Default Ho Chi Minh City bounding box for interactive DEMO map projection
private const val HCMC_MIN_LAT = 10.7150
private const val HCMC_MAX_LAT = 10.8350
private const val HCMC_MIN_LNG = 106.6350
private const val HCMC_MAX_LNG = 106.7450

/**
 * Unified FOX Interactive Map component.
 * - Renders REAL Google Maps SDK (maps-compose) ONLY when MAPS_API_KEY is configured AND user chose REAL mode.
 * - Renders transparent, clearly-badged DEMO interactive map when API key is missing or user chose DEMO mode.
 * NEVER disguises the DEMO canvas map as live Google Maps SDK tiles.
 */
@Composable
fun FoxInteractiveMap(
  mapConfigStatus: MapConfigStatus,
  preferDemoMode: Boolean,
  pickup: GeoPointData?,
  dropoff: GeoPointData?,
  routePoints: List<GeoPointData> = emptyList(),
  driverLocation: CloudDriverLiveLocation? = null,
  hasLocationPermission: Boolean = false,
  interactiveSelectionHint: String? = null,
  height: Dp = 240.dp,
  onMapTap: ((lat: Double, lng: Double, isDemoCanvas: Boolean) -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val useRealGoogleMaps =
    mapConfigStatus.isRealMapsKeyConfigured && !preferDemoMode &&
      mapConfigStatus.activeRenderMode == MapRenderMode.REAL_GOOGLE_MAPS

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .height(height)
      .clip(RoundedCornerShape(20.dp))
      .testTag(if (useRealGoogleMaps) "fox_real_google_map" else "fox_demo_interactive_map"),
    shape = RoundedCornerShape(20.dp),
    border = BorderStroke(1.5.dp, if (useRealGoogleMaps) FoxBlue.copy(alpha = 0.35f) else FoxOrange.copy(alpha = 0.55f)),
    color = Color(0xFFF1F5F9),
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      if (useRealGoogleMaps) {
        RealGoogleMapContent(
          pickup = pickup,
          dropoff = dropoff,
          routePoints = routePoints,
          driverLocation = driverLocation,
          hasLocationPermission = hasLocationPermission,
          onMapTap = onMapTap,
        )
      } else {
        DemoInteractiveCanvasMap(
          pickup = pickup,
          dropoff = dropoff,
          routePoints = routePoints,
          driverLocation = driverLocation,
          onMapTap = onMapTap,
        )
      }

      // Top-left honest mode badge
      Surface(
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(10.dp),
        shape = RoundedCornerShape(50),
        color = if (useRealGoogleMaps) FoxBlue.copy(alpha = 0.92f) else FoxOrangeDeep.copy(alpha = 0.94f),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Rounded.Map,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp),
          )
          Spacer(modifier = Modifier.width(5.dp))
          Text(
            text = if (useRealGoogleMaps) "Google Maps SDK" else "BẢN ĐỒ DEMO",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = Color.White,
          )
        }
      }

      // Bottom hint / legend bar
      Surface(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.93f),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendDot(color = FoxGreen, label = "Đón/Lấy")
            LegendDot(color = FoxOrangeDeep, label = "Đến/Giao")
            if (driverLocation != null) {
              LegendDot(color = FoxBlue, label = if (driverLocation.isDemo) "Tài xế DEMO" else "Tài xế GPS")
            }
          }
          if (!interactiveSelectionHint.isNullOrBlank()) {
            Text(
              text = interactiveSelectionHint,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = FoxBlue,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun LegendDot(color: Color, label: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
      modifier = Modifier
        .size(9.dp)
        .clip(RoundedCornerShape(50))
        .background(color),
    )
    Spacer(modifier = Modifier.width(4.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
      color = FoxTextStrong,
    )
  }
}

@Composable
private fun RealGoogleMapContent(
  pickup: GeoPointData?,
  dropoff: GeoPointData?,
  routePoints: List<GeoPointData>,
  driverLocation: CloudDriverLiveLocation?,
  hasLocationPermission: Boolean,
  onMapTap: ((lat: Double, lng: Double, isDemoCanvas: Boolean) -> Unit)?,
) {
  val initialTarget = remember {
    val p = driverLocation?.let { LatLng(it.lat, it.lng) }
      ?: pickup?.let { LatLng(it.latitude, it.longitude) }
      ?: LatLng(10.7769, 106.7009)
    p
  }
  val cameraPositionState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(initialTarget, 13.2f)
  }

  LaunchedEffect(pickup?.latitude, pickup?.longitude, dropoff?.latitude, dropoff?.longitude, driverLocation?.lat, driverLocation?.lng) {
    val focus = when {
      driverLocation != null -> LatLng(driverLocation.lat, driverLocation.lng)
      pickup != null && dropoff != null -> LatLng(
        (pickup.latitude + dropoff.latitude) / 2.0,
        (pickup.longitude + dropoff.longitude) / 2.0,
      )
      pickup != null -> LatLng(pickup.latitude, pickup.longitude)
      dropoff != null -> LatLng(dropoff.latitude, dropoff.longitude)
      else -> null
    }
    if (focus != null) {
      try {
        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(focus, 13.0f), durationMs = 450)
      } catch (_: Exception) {
        cameraPositionState.position = CameraPosition.fromLatLngZoom(focus, 13.0f)
      }
    }
  }

  GoogleMap(
    modifier = Modifier.fillMaxSize(),
    cameraPositionState = cameraPositionState,
    properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
    uiSettings = MapUiSettings(
      zoomControlsEnabled = false,
      myLocationButtonEnabled = false,
      compassEnabled = true,
      mapToolbarEnabled = false,
    ),
    onMapClick = { latLng ->
      onMapTap?.invoke(latLng.latitude, latLng.longitude, false)
    },
  ) {
    if (pickup != null) {
      Marker(
        state = MarkerState(position = LatLng(pickup.latitude, pickup.longitude)),
        title = pickup.label.ifBlank { "Điểm đón / lấy hàng" },
        snippet = pickup.address,
        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN),
      )
    }
    if (dropoff != null) {
      Marker(
        state = MarkerState(position = LatLng(dropoff.latitude, dropoff.longitude)),
        title = dropoff.label.ifBlank { "Điểm đến / giao hàng" },
        snippet = dropoff.address,
        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE),
      )
    }
    if (driverLocation != null) {
      Marker(
        state = MarkerState(position = LatLng(driverLocation.lat, driverLocation.lng)),
        title = if (driverLocation.isDemo) "Tài xế (DEMO): ${driverLocation.driverName}" else "Tài xế: ${driverLocation.driverName}",
        snippet = if (driverLocation.isDemo) "Vị trí mô phỏng DEMO" else "Vị trí GPS trực tiếp",
        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
      )
    }
    val polyLatLngs = routePoints.map { LatLng(it.latitude, it.longitude) }
    if (polyLatLngs.size >= 2) {
      Polyline(
        points = polyLatLngs,
        color = FoxBlue,
        width = 11f,
      )
    }
  }
}

@Composable
private fun DemoInteractiveCanvasMap(
  pickup: GeoPointData?,
  dropoff: GeoPointData?,
  routePoints: List<GeoPointData>,
  driverLocation: CloudDriverLiveLocation?,
  onMapTap: ((lat: Double, lng: Double, isDemoCanvas: Boolean) -> Unit)?,
) {
  var canvasSize by remember { mutableStateOf(IntSize.Zero) }

  // Dynamically expand viewport bounds if real GPS coordinates fall outside default HCMC viewport
  val bounds = remember(pickup, dropoff, driverLocation) {
    val allLats = mutableListOf(HCMC_MIN_LAT, HCMC_MAX_LAT)
    val allLngs = mutableListOf(HCMC_MIN_LNG, HCMC_MAX_LNG)
    pickup?.let { allLats += it.latitude; allLngs += it.longitude }
    dropoff?.let { allLats += it.latitude; allLngs += it.longitude }
    driverLocation?.let { allLats += it.lat; allLngs += it.lng }

    val minLat = (allLats.minOrNull() ?: HCMC_MIN_LAT) - 0.008
    val maxLat = (allLats.maxOrNull() ?: HCMC_MAX_LAT) + 0.008
    val minLng = (allLngs.minOrNull() ?: HCMC_MIN_LNG) - 0.008
    val maxLng = (allLngs.maxOrNull() ?: HCMC_MAX_LNG) + 0.008
    DemoMapBounds(minLat, maxLat, minLng, maxLng)
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .onSizeChanged { canvasSize = it }
      .pointerInput(bounds, onMapTap) {
        if (onMapTap != null) {
          detectTapGestures { tapOffset ->
            if (canvasSize.width > 0 && canvasSize.height > 0) {
              val normX = (tapOffset.x / canvasSize.width.toFloat()).coerceIn(0.02f, 0.98f)
              val normY = (tapOffset.y / canvasSize.height.toFloat()).coerceIn(0.02f, 0.98f)
              val lng = bounds.minLng + normX * (bounds.maxLng - bounds.minLng)
              val lat = bounds.maxLat - normY * (bounds.maxLat - bounds.minLat)
              onMapTap(lat, lng, true)
            }
          }
        }
      },
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height

      // Soft urban land background
      drawRect(color = Color(0xFFEEF3FA))

      // Subtle park patches
      drawRoundRect(
        color = Color(0xFFD9F2E4),
        topLeft = Offset(w * 0.48f, h * 0.36f),
        size = androidx.compose.ui.geometry.Size(w * 0.14f, h * 0.16f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f),
      )
      drawRoundRect(
        color = Color(0xFFD9F2E4),
        topLeft = Offset(w * 0.16f, h * 0.58f),
        size = androidx.compose.ui.geometry.Size(w * 0.16f, h * 0.14f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f),
      )

      // Stylized Saigon River arc
      val riverPath = Path().apply {
        moveTo(w * 0.88f, 0f)
        cubicTo(w * 0.72f, h * 0.25f, w * 0.84f, h * 0.62f, w * 0.68f, h)
      }
      drawPath(
        path = riverPath,
        color = Color(0xFFBFDBFE),
        style = Stroke(width = max(14f, w * 0.045f), cap = StrokeCap.Round),
      )

      // Clean city street grid
      val gridColor = Color(0xFFDCE4F2)
      for (i in 1..6) {
        val x = w * (i / 7f)
        drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 2f)
      }
      for (j in 1..4) {
        val y = h * (j / 5f)
        drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 2f)
      }

      // Main arterial avenues
      drawLine(Color.White, Offset(w * 0.12f, h * 0.15f), Offset(w * 0.85f, h * 0.78f), strokeWidth = 8f, cap = StrokeCap.Round)
      drawLine(Color.White, Offset(w * 0.22f, h * 0.85f), Offset(w * 0.75f, h * 0.18f), strokeWidth = 7f, cap = StrokeCap.Round)

      fun project(lat: Double, lng: Double): Offset {
        val nx = ((lng - bounds.minLng) / max(0.0001, bounds.maxLng - bounds.minLng)).toFloat().coerceIn(0.06f, 0.94f)
        val ny = ((bounds.maxLat - lat) / max(0.0001, bounds.maxLat - bounds.minLat)).toFloat().coerceIn(0.14f, 0.82f)
        return Offset(nx * w, ny * h)
      }

      // Draw route polyline
      val effectivePoints =
        when {
          routePoints.size >= 2 -> routePoints
          pickup != null && dropoff != null -> listOf(pickup, dropoff)
          else -> emptyList()
        }
      if (effectivePoints.size >= 2) {
        val routePath = Path().apply {
          val first = project(effectivePoints.first().latitude, effectivePoints.first().longitude)
          moveTo(first.x, first.y)
          for (idx in 1 until effectivePoints.size) {
            val pt = project(effectivePoints[idx].latitude, effectivePoints[idx].longitude)
            lineTo(pt.x, pt.y)
          }
        }
        // Soft halo
        drawPath(
          path = routePath,
          color = FoxBlue.copy(alpha = 0.22f),
          style = Stroke(width = 18f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        // Main route stroke
        drawPath(
          path = routePath,
          color = FoxBlue,
          style = Stroke(
            width = 8f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f),
          ),
        )
      }

      // Pickup pin (Green)
      pickup?.let { pt ->
        val pos = project(pt.latitude, pt.longitude)
        drawCircle(color = FoxGreen.copy(alpha = 0.22f), radius = 24f, center = pos)
        drawCircle(color = Color.White, radius = 14f, center = pos)
        drawCircle(color = FoxGreen, radius = 9f, center = pos)
      }

      // Dropoff pin (FOX Orange)
      dropoff?.let { pt ->
        val pos = project(pt.latitude, pt.longitude)
        drawCircle(color = FoxOrange.copy(alpha = 0.25f), radius = 24f, center = pos)
        drawCircle(color = Color.White, radius = 14f, center = pos)
        drawCircle(color = FoxOrangeDeep, radius = 9f, center = pos)
      }

      // Live Driver Marker (FOX Blue pulsing halo)
      driverLocation?.let { drv ->
        val pos = project(drv.lat, drv.lng)
        drawCircle(color = FoxBlue.copy(alpha = 0.24f), radius = 28f, center = pos)
        drawCircle(color = FoxBlue, radius = 15f, center = pos)
        drawCircle(color = Color.White, radius = 6f, center = pos)
      }
    }

    // Top-right coordinate telemetry readout
    val focusPoint = driverLocation?.let { it.lat to it.lng }
      ?: pickup?.let { it.latitude to it.longitude }
    if (focusPoint != null) {
      Surface(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(10.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
      ) {
        Text(
          text = "${String.format("%.4f", focusPoint.first)}, ${String.format("%.4f", focusPoint.second)}",
          style = MaterialTheme.typography.labelSmall,
          color = FoxTextSecondary,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
      }
    }
  }
}

private data class DemoMapBounds(
  val minLat: Double,
  val maxLat: Double,
  val minLng: Double,
  val maxLng: Double,
)

/**
 * Compact honest status card explaining Google Maps / Routes API Key & Billing state.
 */
@Composable
fun MapApiStatusNoticeCard(
  mapConfigStatus: MapConfigStatus,
  preferDemoMode: Boolean,
  onToggleDemoMode: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = if (!mapConfigStatus.isRealMapsKeyConfigured || preferDemoMode) Color(0xFFFFF7ED) else Color(0xFFEFF6FF),
    border = BorderStroke(
      1.dp,
      if (!mapConfigStatus.isRealMapsKeyConfigured || preferDemoMode) FoxOrange.copy(alpha = 0.45f) else FoxBlue.copy(alpha = 0.3f),
    ),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Icon(
            imageVector = if (mapConfigStatus.isRealMapsKeyConfigured && !preferDemoMode) Icons.Rounded.MyLocation else Icons.Rounded.Place,
            contentDescription = null,
            tint = if (mapConfigStatus.isRealMapsKeyConfigured && !preferDemoMode) FoxBlue else FoxOrangeDeep,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (preferDemoMode && mapConfigStatus.isRealMapsKeyConfigured) {
              "Đang xem Bản đồ DEMO (có thể chuyển Google Maps)"
            } else {
              mapConfigStatus.statusBannerTitle
            },
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = if (mapConfigStatus.isRealMapsKeyConfigured && !preferDemoMode) FoxBlue else FoxOrangeDeep,
          )
        }
        if (mapConfigStatus.isRealMapsKeyConfigured) {
          Surface(
            onClick = { onToggleDemoMode(!preferDemoMode) },
            shape = RoundedCornerShape(50),
            color = FoxBlue.copy(alpha = 0.12f),
            modifier = Modifier.testTag("toggle_map_render_mode"),
          ) {
            Text(
              text = if (preferDemoMode) "Dùng Maps thật" else "Chuyển DEMO",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = FoxBlue,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
          }
        } else {
          Surface(
            shape = RoundedCornerShape(50),
            color = FoxOrange.copy(alpha = 0.22f),
            modifier = Modifier.testTag("maps_feature_locked_badge"),
          ) {
            Text(
              text = "TẠM KHÓA",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
              color = FoxOrangeDeep,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }
        }
      }
      Text(
        text = mapConfigStatus.statusBannerMessage,
        style = MaterialTheme.typography.labelSmall,
        color = FoxTextSecondary,
      )
      if (mapConfigStatus.isRealGoogleMapsFeatureLocked || mapConfigStatus.isRealRoutesApiFeatureLocked) {
        Text(
          text = mapConfigStatus.lockedFeatureSummary,
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = FoxOrangeDeep,
          modifier = Modifier.testTag("maps_locked_feature_summary"),
        )
      }
    }
  }
}
