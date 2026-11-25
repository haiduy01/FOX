package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.location.FoxMapRepository
import com.example.data.location.GeoPointData
import com.example.data.location.GpsLocationState
import com.example.data.location.LocationDataSource
import com.example.data.location.RouteComputationState
import com.example.data.location.VerifiedRouteInfo
import com.example.data.repository.FoxDemoPricingEngine
import com.example.ui.FoxCloudUiState
import com.example.ui.components.FoxInteractiveMap
import com.example.ui.components.MapApiStatusNoticeCard
import com.example.ui.formatVnd
import com.example.ui.theme.FoxBlue
import com.example.ui.theme.FoxGreen
import com.example.ui.theme.FoxOrange
import com.example.ui.theme.FoxOrangeDeep
import com.example.ui.theme.FoxRed
import com.example.ui.theme.FoxTextSecondary
import com.example.ui.theme.FoxTextStrong
import kotlinx.coroutines.launch

private enum class MapPickTarget {
  PICKUP,
  DROPOFF,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudRideScreen(
  cloudState: FoxCloudUiState,
  hasLocationPermission: () -> Boolean,
  onFetchCurrentGps: suspend () -> GpsLocationState,
  onSearchPlaces: suspend (query: String, useDemoMode: Boolean) -> List<GeoPointData>,
  onComputeRoute: suspend (origin: GeoPointData, destination: GeoPointData, vehicleType: String?) -> RouteComputationState,
  onOrderCreated: () -> Unit,
  onSubmitRide: (
    pickup: String,
    destination: String,
    vehicleType: String,
    distanceKm: Int,
    note: String,
    voucherCode: String?,
    paymentMethod: String,
    pickupCoords: GeoPointData?,
    dropoffCoords: GeoPointData?,
    onSuccess: () -> Unit,
  ) -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val scope = rememberCoroutineScope()

  var preferDemoMode by remember { mutableStateOf(!cloudState.mapConfigStatus.isRealMapsKeyConfigured) }
  var pickTarget by remember { mutableStateOf(MapPickTarget.DROPOFF) }

  val defaultPickup = remember { FoxMapRepository.DEMO_HCMC_PRESET_PLACES[0] }
  val defaultDropoff = remember { FoxMapRepository.DEMO_HCMC_PRESET_PLACES[2] }

  var pickupPoint by remember { mutableStateOf(defaultPickup) }
  var dropoffPoint by remember { mutableStateOf(defaultDropoff) }
  var pickupText by remember { mutableStateOf(defaultPickup.address) }
  var dropoffText by remember { mutableStateOf(defaultDropoff.address) }

  var searchQuery by remember { mutableStateOf("") }
  var searchResults by remember { mutableStateOf<List<GeoPointData>>(emptyList()) }
  var isSearching by remember { mutableStateOf(false) }

  var gpsState by remember { mutableStateOf<GpsLocationState>(GpsLocationState.Idle) }
  var routeState by remember { mutableStateOf<RouteComputationState>(RouteComputationState.Idle) }

  val vehicleTypes = listOf("FOX Bike", "FOX Car 4", "FOX Car 7")
  var selectedVehicle by remember { mutableStateOf("FOX Bike") }
  var voucherCode by remember { mutableStateOf<String?>(null) }
  var paymentMethod by remember { mutableStateOf("Tiền mặt DEMO") }
  var note by remember { mutableStateOf("") }

  fun requestRealGpsFetch() {
    scope.launch {
      gpsState = GpsLocationState.Fetching
      val res = onFetchCurrentGps()
      gpsState = res
      if (res is GpsLocationState.Success) {
        pickupPoint = res.location
        pickupText = res.location.address
      }
    }
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions(),
  ) { grants ->
    val granted =
      grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
        grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    if (granted) {
      requestRealGpsFetch()
    } else {
      gpsState = GpsLocationState.PermissionDenied
    }
  }

  LaunchedEffect(pickupPoint, dropoffPoint, selectedVehicle) {
    routeState = RouteComputationState.Loading
    routeState = onComputeRoute(pickupPoint, dropoffPoint, selectedVehicle)
  }

  val readyRoute = (routeState as? RouteComputationState.Ready)?.route
  val effectiveDistanceKm = readyRoute?.distanceKm ?: 4
  val quote = remember(selectedVehicle, effectiveDistanceKm, voucherCode) {
    FoxDemoPricingEngine.estimateRide(selectedVehicle, effectiveDistanceKm, voucherCode)
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            "FOX Đặt xe • Bản đồ & GPS",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = FoxTextStrong,
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("cloud_ride_back")) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = stringResource(R.string.cd_back),
              tint = FoxTextStrong,
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 640.dp),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          MapApiStatusNoticeCard(
            mapConfigStatus = cloudState.mapConfigStatus,
            preferDemoMode = preferDemoMode,
            onToggleDemoMode = { preferDemoMode = it },
          )
        }

        // Interactive Map
        item {
          FoxInteractiveMap(
            mapConfigStatus = cloudState.mapConfigStatus,
            preferDemoMode = preferDemoMode,
            pickup = pickupPoint,
            dropoff = dropoffPoint,
            routePoints = readyRoute?.routePoints.orEmpty(),
            hasLocationPermission = hasLocationPermission(),
            interactiveSelectionHint = if (pickTarget == MapPickTarget.PICKUP) "Chạm bản đồ chọn Điểm đón" else "Chạm bản đồ chọn Điểm đến",
            onMapTap = { lat, lng, isDemoCanvas ->
              val picked = GeoPointData(
                latitude = lat,
                longitude = lng,
                label = if (pickTarget == MapPickTarget.PICKUP) "Điểm đón trên bản đồ" else "Điểm đến trên bản đồ",
                address = "${if (isDemoCanvas) "[Bản đồ DEMO]" else "[Bản đồ]"} (${String.format("%.4f", lat)}, ${String.format("%.4f", lng)})",
                source = if (isDemoCanvas) LocationDataSource.DEMO_MAP_PICK else LocationDataSource.REAL_GEOCODER,
              )
              if (pickTarget == MapPickTarget.PICKUP) {
                pickupPoint = picked
                pickupText = picked.address
              } else {
                dropoffPoint = picked
                dropoffText = picked.address
              }
            },
          )
        }

        // Pin target switcher + explicit opt-in GPS button (NEVER auto-tracks without consent)
        item {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
              FilterChip(
                selected = pickTarget == MapPickTarget.PICKUP,
                onClick = { pickTarget = MapPickTarget.PICKUP },
                modifier = Modifier.testTag("ride_pick_pickup_chip"),
                label = { Text("Chọn điểm đón") },
              )
              FilterChip(
                selected = pickTarget == MapPickTarget.DROPOFF,
                onClick = { pickTarget = MapPickTarget.DROPOFF },
                modifier = Modifier.testTag("ride_pick_dropoff_chip"),
                label = { Text("Chọn điểm đến") },
              )
            }

            OutlinedButton(
              onClick = {
                if (hasLocationPermission()) {
                  requestRealGpsFetch()
                } else {
                  permissionLauncher.launch(
                    arrayOf(
                      Manifest.permission.ACCESS_FINE_LOCATION,
                      Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                  )
                }
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("ride_use_current_gps_button"),
              border = BorderStroke(1.2.dp, FoxBlue),
            ) {
              Icon(Icons.Rounded.MyLocation, contentDescription = null, tint = FoxBlue)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Lấy vị trí GPS hiện tại làm điểm đón", color = FoxBlue, fontWeight = FontWeight.Bold)
            }

            GpsStateBanner(gpsState = gpsState)
          }
        }

        // Address Search + Quick DEMO Presets
        item {
          PlaceSearchAndPresetsCard(
            pickTarget = pickTarget,
            preferDemoMode = preferDemoMode,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            isSearching = isSearching,
            searchResults = searchResults,
            onSearchClick = {
              scope.launch {
                isSearching = true
                searchResults = onSearchPlaces(searchQuery, preferDemoMode)
                isSearching = false
              }
            },
            onSelectPlace = { selected ->
              if (pickTarget == MapPickTarget.PICKUP) {
                pickupPoint = selected
                pickupText = selected.address
              } else {
                dropoffPoint = selected
                dropoffText = selected.address
              }
              searchResults = emptyList()
            },
          )
        }

        // Pickup / Dropoff address inputs
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Điểm đón & Điểm đến", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = FoxTextStrong)
                SourceBadge(isDemo = pickupPoint.isDemo && dropoffPoint.isDemo)
              }
              OutlinedTextField(
                value = pickupText,
                onValueChange = { pickupText = it },
                label = { Text("Địa chỉ đón") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("cloud_ride_pickup_input"),
              )
              OutlinedTextField(
                value = dropoffText,
                onValueChange = { dropoffText = it },
                label = { Text("Địa chỉ đến") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("cloud_ride_dropoff_input"),
              )
            }
          }
        }

        // Route Summary Card (km, ETA, Routes API vs DEMO disclaimer)
        item {
          RouteSummaryCard(routeState = routeState)
        }

        // Vehicle + Voucher + Payment + Server-Verified Checkout
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Text("Loại xe FOX", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = FoxTextStrong)
              LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vehicleTypes) { v ->
                  FilterChip(
                    selected = selectedVehicle == v,
                    onClick = { selectedVehicle = v },
                    modifier = Modifier.testTag("cloud_ride_vehicle_${v.lowercase().replace(" ", "_")}"),
                    label = { Text(v) },
                  )
                }
              }

              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                  selected = voucherCode == "FOXRIDE25",
                  onClick = { voucherCode = if (voucherCode == "FOXRIDE25") null else "FOXRIDE25" },
                  label = { Text("Mã FOXRIDE25 (-25%)") },
                )
              }

              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Tiền mặt DEMO", "Ví FOX DEMO").forEach { method ->
                  FilterChip(
                    selected = paymentMethod == method,
                    onClick = { paymentMethod = method },
                    label = { Text(method) },
                  )
                }
              }

              OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Ghi chú cho tài xế (tùy chọn)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )

              HorizontalDivider(color = MaterialTheme.colorScheme.outline)

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column {
                  Text(
                    text = if (readyRoute?.isServerVerified == true) "Giá dự kiến (server sẽ xác thực lại)" else "Giá ước tính DEMO",
                    style = MaterialTheme.typography.labelSmall,
                    color = FoxTextSecondary,
                  )
                  Text(
                    text = formatVnd(quote.totalVnd),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = FoxBlue,
                  )
                  if (quote.discountVnd > 0L) {
                    Text(
                      text = "Đã giảm ${formatVnd(quote.discountVnd)} • Quãng đường ~${effectiveDistanceKm} km",
                      style = MaterialTheme.typography.labelSmall,
                      color = FoxGreen,
                    )
                  }
                }
              }

              Button(
                onClick = {
                  onSubmitRide(
                    pickupText,
                    dropoffText,
                    selectedVehicle,
                    effectiveDistanceKm,
                    note,
                    voucherCode,
                    paymentMethod,
                    pickupPoint,
                    dropoffPoint,
                    onOrderCreated,
                  )
                },
                enabled = !cloudState.isBusy && pickupText.isNotBlank() && dropoffText.isNotBlank() && routeState !is RouteComputationState.Error,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(52.dp)
                  .testTag("cloud_ride_submit_button"),
                colors = ButtonDefaults.buttonColors(containerColor = FoxBlue),
                shape = RoundedCornerShape(14.dp),
              ) {
                Text(
                  text = if (cloudState.isBusy) "Đang xác thực lộ trình & giá..." else "Xác nhận đặt xe FOX",
                  fontWeight = FontWeight.ExtraBold,
                )
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudDeliveryScreen(
  cloudState: FoxCloudUiState,
  hasLocationPermission: () -> Boolean,
  onFetchCurrentGps: suspend () -> GpsLocationState,
  onSearchPlaces: suspend (query: String, useDemoMode: Boolean) -> List<GeoPointData>,
  onComputeRoute: suspend (origin: GeoPointData, destination: GeoPointData, vehicleType: String?) -> RouteComputationState,
  onOrderCreated: () -> Unit,
  onSubmitDelivery: (
    senderName: String,
    senderPhone: String,
    pickupAddress: String,
    receiverName: String,
    receiverPhone: String,
    dropoffAddress: String,
    itemType: String,
    weightTier: String,
    speed: String,
    note: String,
    voucherCode: String?,
    paymentMethod: String,
    distanceKm: Int?,
    pickupCoords: GeoPointData?,
    dropoffCoords: GeoPointData?,
    onSuccess: () -> Unit,
  ) -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val scope = rememberCoroutineScope()

  var preferDemoMode by remember { mutableStateOf(!cloudState.mapConfigStatus.isRealMapsKeyConfigured) }
  var pickTarget by remember { mutableStateOf(MapPickTarget.DROPOFF) }

  val defaultPickup = remember { FoxMapRepository.DEMO_HCMC_PRESET_PLACES[1] }
  val defaultDropoff = remember { FoxMapRepository.DEMO_HCMC_PRESET_PLACES[4] }

  var pickupPoint by remember { mutableStateOf(defaultPickup) }
  var dropoffPoint by remember { mutableStateOf(defaultDropoff) }
  var pickupAddress by remember { mutableStateOf(defaultPickup.address) }
  var dropoffAddress by remember { mutableStateOf(defaultDropoff.address) }

  var senderName by remember { mutableStateOf(cloudState.profile?.displayName?.ifBlank { "Người gửi FOX" } ?: "Người gửi FOX") }
  var senderPhone by remember { mutableStateOf(cloudState.profile?.phone?.ifBlank { "0901234567" } ?: "0901234567") }
  var receiverName by remember { mutableStateOf("") }
  var receiverPhone by remember { mutableStateOf("") }

  var searchQuery by remember { mutableStateOf("") }
  var searchResults by remember { mutableStateOf<List<GeoPointData>>(emptyList()) }
  var isSearching by remember { mutableStateOf(false) }

  var gpsState by remember { mutableStateOf<GpsLocationState>(GpsLocationState.Idle) }
  var routeState by remember { mutableStateOf<RouteComputationState>(RouteComputationState.Idle) }

  var itemType by remember { mutableStateOf("Tài liệu / Bưu phẩm") }
  var weightTier by remember { mutableStateOf("Dưới 2 kg") }
  var speed by remember { mutableStateOf("Tiêu chuẩn") }
  var voucherCode by remember { mutableStateOf<String?>(null) }
  var paymentMethod by remember { mutableStateOf("Tiền mặt DEMO") }
  var note by remember { mutableStateOf("") }

  fun requestRealGpsFetch() {
    scope.launch {
      gpsState = GpsLocationState.Fetching
      val res = onFetchCurrentGps()
      gpsState = res
      if (res is GpsLocationState.Success) {
        pickupPoint = res.location
        pickupAddress = res.location.address
      }
    }
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions(),
  ) { grants ->
    val granted =
      grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
        grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    if (granted) {
      requestRealGpsFetch()
    } else {
      gpsState = GpsLocationState.PermissionDenied
    }
  }

  LaunchedEffect(pickupPoint, dropoffPoint) {
    routeState = RouteComputationState.Loading
    routeState = onComputeRoute(pickupPoint, dropoffPoint, "FOX Bike")
  }

  val readyRoute = (routeState as? RouteComputationState.Ready)?.route
  val effectiveDistanceKm = readyRoute?.distanceKm ?: 5
  val quote = remember(weightTier, speed, voucherCode, effectiveDistanceKm) {
    FoxDemoPricingEngine.estimateDelivery(weightTier, speed, voucherCode, effectiveDistanceKm)
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            "FOX Giao hàng • Bản đồ & GPS",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = FoxTextStrong,
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("cloud_delivery_back")) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = stringResource(R.string.cd_back),
              tint = FoxTextStrong,
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 640.dp),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          MapApiStatusNoticeCard(
            mapConfigStatus = cloudState.mapConfigStatus,
            preferDemoMode = preferDemoMode,
            onToggleDemoMode = { preferDemoMode = it },
          )
        }

        item {
          FoxInteractiveMap(
            mapConfigStatus = cloudState.mapConfigStatus,
            preferDemoMode = preferDemoMode,
            pickup = pickupPoint,
            dropoff = dropoffPoint,
            routePoints = readyRoute?.routePoints.orEmpty(),
            hasLocationPermission = hasLocationPermission(),
            interactiveSelectionHint = if (pickTarget == MapPickTarget.PICKUP) "Chạm chọn Điểm lấy hàng" else "Chạm chọn Điểm giao hàng",
            onMapTap = { lat, lng, isDemoCanvas ->
              val picked = GeoPointData(
                latitude = lat,
                longitude = lng,
                label = if (pickTarget == MapPickTarget.PICKUP) "Điểm lấy hàng" else "Điểm giao hàng",
                address = "${if (isDemoCanvas) "[Bản đồ DEMO]" else "[Bản đồ]"} (${String.format("%.4f", lat)}, ${String.format("%.4f", lng)})",
                source = if (isDemoCanvas) LocationDataSource.DEMO_MAP_PICK else LocationDataSource.REAL_GEOCODER,
              )
              if (pickTarget == MapPickTarget.PICKUP) {
                pickupPoint = picked
                pickupAddress = picked.address
              } else {
                dropoffPoint = picked
                dropoffAddress = picked.address
              }
            },
          )
        }

        item {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilterChip(
                selected = pickTarget == MapPickTarget.PICKUP,
                onClick = { pickTarget = MapPickTarget.PICKUP },
                label = { Text("Chọn điểm lấy hàng") },
              )
              FilterChip(
                selected = pickTarget == MapPickTarget.DROPOFF,
                onClick = { pickTarget = MapPickTarget.DROPOFF },
                label = { Text("Chọn điểm giao hàng") },
              )
            }
            OutlinedButton(
              onClick = {
                if (hasLocationPermission()) {
                  requestRealGpsFetch()
                } else {
                  permissionLauncher.launch(
                    arrayOf(
                      Manifest.permission.ACCESS_FINE_LOCATION,
                      Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                  )
                }
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("delivery_use_current_gps_button"),
              border = BorderStroke(1.2.dp, FoxOrangeDeep),
            ) {
              Icon(Icons.Rounded.MyLocation, contentDescription = null, tint = FoxOrangeDeep)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Lấy vị trí GPS hiện tại làm điểm lấy hàng", color = FoxOrangeDeep, fontWeight = FontWeight.Bold)
            }
            GpsStateBanner(gpsState = gpsState)
          }
        }

        item {
          PlaceSearchAndPresetsCard(
            pickTarget = pickTarget,
            preferDemoMode = preferDemoMode,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            isSearching = isSearching,
            searchResults = searchResults,
            onSearchClick = {
              scope.launch {
                isSearching = true
                searchResults = onSearchPlaces(searchQuery, preferDemoMode)
                isSearching = false
              }
            },
            onSelectPlace = { selected ->
              if (pickTarget == MapPickTarget.PICKUP) {
                pickupPoint = selected
                pickupAddress = selected.address
              } else {
                dropoffPoint = selected
                dropoffAddress = selected.address
              }
              searchResults = emptyList()
            },
          )
        }

        item {
          RouteSummaryCard(routeState = routeState)
        }

        // Sender / Receiver Form
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Text("Thông tin lấy & giao hàng", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = FoxTextStrong)
              OutlinedTextField(
                value = pickupAddress,
                onValueChange = { pickupAddress = it },
                label = { Text("Địa chỉ lấy hàng") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("cloud_delivery_pickup_input"),
              )
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                  value = senderName,
                  onValueChange = { senderName = it },
                  label = { Text("Người gửi") },
                  singleLine = true,
                  modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                  value = senderPhone,
                  onValueChange = { senderPhone = it },
                  label = { Text("SĐT gửi") },
                  singleLine = true,
                  modifier = Modifier.weight(1f),
                )
              }
              OutlinedTextField(
                value = dropoffAddress,
                onValueChange = { dropoffAddress = it },
                label = { Text("Địa chỉ giao hàng") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("cloud_delivery_dropoff_input"),
              )
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                  value = receiverName,
                  onValueChange = { receiverName = it },
                  label = { Text("Người nhận *") },
                  singleLine = true,
                  modifier = Modifier.weight(1f).testTag("cloud_delivery_receiver_input"),
                )
                OutlinedTextField(
                  value = receiverPhone,
                  onValueChange = { receiverPhone = it },
                  label = { Text("SĐT nhận") },
                  singleLine = true,
                  modifier = Modifier.weight(1f),
                )
              }
              OutlinedTextField(
                value = itemType,
                onValueChange = { itemType = it },
                label = { Text("Loại hàng hóa") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )

              Text("Khối lượng & Tốc độ", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = FoxTextStrong)
              LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("Dưới 2 kg", "2–5 kg", "5–10 kg")) { w ->
                  FilterChip(selected = weightTier == w, onClick = { weightTier = w }, label = { Text(w) })
                }
              }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Tiêu chuẩn", "Nhanh").forEach { sp ->
                  FilterChip(selected = speed == sp, onClick = { speed = sp }, label = { Text(sp) })
                }
                FilterChip(
                  selected = voucherCode == "FOXSHIP15",
                  onClick = { voucherCode = if (voucherCode == "FOXSHIP15") null else "FOXSHIP15" },
                  label = { Text("FOXSHIP15") },
                )
              }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Tiền mặt DEMO", "Ví FOX DEMO").forEach { method ->
                  FilterChip(selected = paymentMethod == method, onClick = { paymentMethod = method }, label = { Text(method) })
                }
              }
              OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Ghi chú bưu kiện") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )

              HorizontalDivider(color = MaterialTheme.colorScheme.outline)

              Column {
                Text(
                  text = "Cước dự kiến theo ~${effectiveDistanceKm} km (phụ phí 4.000đ/km sau 3 km đầu)",
                  style = MaterialTheme.typography.labelSmall,
                  color = FoxTextSecondary,
                )
                Text(
                  text = formatVnd(quote.totalVnd),
                  style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                  color = FoxOrangeDeep,
                )
              }

              Button(
                onClick = {
                  onSubmitDelivery(
                    senderName,
                    senderPhone,
                    pickupAddress,
                    receiverName,
                    receiverPhone,
                    dropoffAddress,
                    itemType,
                    weightTier,
                    speed,
                    note,
                    voucherCode,
                    paymentMethod,
                    effectiveDistanceKm,
                    pickupPoint,
                    dropoffPoint,
                    onOrderCreated,
                  )
                },
                enabled = !cloudState.isBusy && pickupAddress.isNotBlank() && dropoffAddress.isNotBlank() && receiverName.isNotBlank() && routeState !is RouteComputationState.Error,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(52.dp)
                  .testTag("cloud_delivery_submit_button"),
                colors = ButtonDefaults.buttonColors(containerColor = FoxOrangeDeep),
                shape = RoundedCornerShape(14.dp),
              ) {
                Text(
                  text = if (cloudState.isBusy) "Đang xác thực quãng đường & tạo vận đơn..." else "Tạo vận đơn FOX Giao hàng",
                  fontWeight = FontWeight.ExtraBold,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun GpsStateBanner(gpsState: GpsLocationState) {
  when (gpsState) {
    GpsLocationState.Idle -> Unit
    GpsLocationState.Fetching -> {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = FoxBlue.copy(alpha = 0.08f),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = FoxBlue)
          Text("Đang lấy vị trí GPS thật từ Fused Location Provider...", style = MaterialTheme.typography.labelMedium, color = FoxBlue)
        }
      }
    }
    GpsLocationState.PermissionDenied -> {
      StatusNoticeStrip(
        icon = Icons.Rounded.GpsOff,
        text = "Chưa được cấp quyền vị trí (ACCESS_FINE_LOCATION / COARSE). Bạn vẫn có thể chọn địa điểm thủ công hoặc dùng Bản đồ DEMO.",
        tint = FoxRed,
        bg = FoxRed.copy(alpha = 0.10f),
      )
    }
    GpsLocationState.GpsDisabled -> {
      StatusNoticeStrip(
        icon = Icons.Rounded.WarningAmber,
        text = "GPS trên thiết bị đang tắt. Vui lòng bật Dịch vụ Vị trí trong Cài đặt để định vị chính xác.",
        tint = FoxOrangeDeep,
        bg = Color(0xFFFFF7ED),
      )
    }
    GpsLocationState.Offline -> {
      StatusNoticeStrip(
        icon = Icons.Rounded.WarningAmber,
        text = "Thiết bị đang mất mạng và chưa có tín hiệu GPS mới. Chuyển sang chọn điểm DEMO hoặc thử lại khi có mạng.",
        tint = FoxOrangeDeep,
        bg = Color(0xFFFFF7ED),
      )
    }
    is GpsLocationState.Success -> {
      StatusNoticeStrip(
        icon = Icons.Rounded.GpsFixed,
        text = "Đã định vị GPS thật (±${gpsState.location.accuracyMeters.toInt()}m): ${gpsState.location.address}",
        tint = FoxGreen,
        bg = FoxGreen.copy(alpha = 0.10f),
      )
    }
    is GpsLocationState.Error -> {
      StatusNoticeStrip(
        icon = Icons.Rounded.WarningAmber,
        text = gpsState.message,
        tint = FoxRed,
        bg = FoxRed.copy(alpha = 0.10f),
      )
    }
  }
}

@Composable
private fun StatusNoticeStrip(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  text: String,
  tint: Color,
  bg: Color,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = bg,
    border = BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
      Text(text, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = tint)
    }
  }
}

@Composable
private fun SourceBadge(isDemo: Boolean) {
  Surface(
    shape = RoundedCornerShape(50),
    color = if (isDemo) FoxOrange.copy(alpha = 0.15f) else FoxGreen.copy(alpha = 0.15f),
  ) {
    Text(
      text = if (isDemo) "Tọa độ DEMO" else "Có tọa độ thật",
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
      color = if (isDemo) FoxOrangeDeep else FoxGreen,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
    )
  }
}

@Composable
private fun PlaceSearchAndPresetsCard(
  pickTarget: MapPickTarget,
  preferDemoMode: Boolean,
  searchQuery: String,
  onSearchQueryChange: (String) -> Unit,
  isSearching: Boolean,
  searchResults: List<GeoPointData>,
  onSearchClick: () -> Unit,
  onSelectPlace: (GeoPointData) -> Unit,
) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        text = if (pickTarget == MapPickTarget.PICKUP) "Tìm địa chỉ / điểm mẫu cho ĐIỂM ĐÓN" else "Tìm địa chỉ / điểm mẫu cho ĐIỂM ĐẾN",
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = FoxTextStrong,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = onSearchQueryChange,
          placeholder = { Text(if (preferDemoMode) "Tìm điểm DEMO TP.HCM..." else "Nhập tên đường, địa điểm...") },
          singleLine = true,
          modifier = Modifier.weight(1f).testTag("map_place_search_input"),
        )
        Button(
          onClick = onSearchClick,
          enabled = !isSearching,
          modifier = Modifier.height(52.dp).testTag("map_place_search_button"),
          colors = ButtonDefaults.buttonColors(containerColor = FoxBlue),
        ) {
          Icon(Icons.Rounded.Search, contentDescription = "Tìm địa chỉ")
        }
      }

      if (searchResults.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          searchResults.forEach { place ->
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectPlace(place) },
              shape = RoundedCornerShape(10.dp),
              color = Color(0xFFF8FAFC),
              border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            ) {
              Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = if (place.isDemo) FoxOrangeDeep else FoxBlue, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                  Text(place.label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = FoxTextStrong)
                  Text(place.address, style = MaterialTheme.typography.labelSmall, color = FoxTextSecondary)
                }
              }
            }
          }
        }
      }

      Text("Điểm mẫu TP.HCM (DEMO):", style = MaterialTheme.typography.labelSmall, color = FoxTextSecondary)
      LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(FoxMapRepository.DEMO_HCMC_PRESET_PLACES) { preset ->
          FilterChip(
            selected = false,
            onClick = { onSelectPlace(preset) },
            label = { Text(preset.label.removeSuffix(" (DEMO)")) },
          )
        }
      }
    }
  }
}

@Composable
private fun RouteSummaryCard(routeState: RouteComputationState) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, FoxBlue.copy(alpha = 0.25f)),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Rounded.Route, contentDescription = null, tint = FoxBlue)
        Text("Quãng đường & Thời gian dự kiến", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = FoxTextStrong)
      }

      when (routeState) {
        RouteComputationState.Idle,
        RouteComputationState.Loading,
        -> {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = FoxBlue)
            Text("Đang tính lộ trình...", style = MaterialTheme.typography.bodySmall, color = FoxTextSecondary)
          }
        }
        is RouteComputationState.Error -> {
          Text(routeState.message, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = FoxRed)
        }
        is RouteComputationState.Ready -> {
          val info: VerifiedRouteInfo = routeState.route
          val kmFormatted = String.format("%.1f km", info.distanceMeters / 1000.0)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "$kmFormatted (tính cước ${info.distanceKm} km) • ~${info.durationMinutes} phút",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
              color = FoxBlue,
            )
            Surface(
              shape = RoundedCornerShape(50),
              color = if (info.isDemoRoute) FoxOrange.copy(alpha = 0.14f) else FoxGreen.copy(alpha = 0.14f),
            ) {
              Text(
                text = if (info.isDemoRoute) "Lộ trình DEMO" else "Google Routes v2",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (info.isDemoRoute) FoxOrangeDeep else FoxGreen,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              )
            }
          }
          Text(
            text = info.disclaimer,
            style = MaterialTheme.typography.labelSmall,
            color = FoxTextSecondary,
          )
        }
      }
    }
  }
}
