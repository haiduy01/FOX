package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.FoxDatabase
import com.example.data.local.FoxNotificationEntity
import com.example.data.local.FoxOrderEntity
import com.example.data.repository.FoxBackendStatus
import com.example.data.repository.RoomDemoFoxRepository
import com.example.notifications.FoxNotificationExtras
import com.example.notifications.FoxNotificationHelper
import com.example.notifications.FoxNotificationRouter
import com.example.ui.FoxCloudViewModel
import com.example.ui.FoxServiceType
import com.example.ui.FoxUiState
import com.example.ui.FoxViewModel
import com.example.ui.auth.AuthGateState
import com.example.ui.auth.attemptSilentGoogleSignIn
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.performInteractiveGoogleSignIn
import com.example.ui.auth.signOutGoogleSafely
import com.example.ui.components.FoxBrandMark
import com.example.ui.screens.AccountScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CloudAccountScreen
import com.example.ui.screens.CloudDeliveryScreen
import com.example.ui.screens.CloudFoodScreen
import com.example.ui.screens.CloudNotificationsScreen
import com.example.ui.screens.CloudOrdersScreen
import com.example.ui.screens.CloudRideScreen
import com.example.ui.screens.CloudShoppingScreen
import com.example.ui.screens.CloudWalletScreen
import com.example.ui.screens.DeliveryScreen
import com.example.ui.screens.DriverDashboardScreen
import com.example.ui.screens.FoodScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MerchantDashboardScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.OrdersScreen
import com.example.ui.screens.RideScreen
import com.example.ui.screens.ShoppingScreen
import com.example.ui.screens.WalletScreen
import com.example.ui.theme.FoxBlue
import com.example.ui.theme.FoxTextSecondary
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

private const val ROUTE_HOME = "home"
private const val ROUTE_ORDERS = "orders"
private const val ROUTE_WALLET = "wallet"
private const val ROUTE_ACCOUNT = "account"
private const val ROUTE_NOTIFICATIONS = "notifications"
private const val ROUTE_DRIVER_DASHBOARD = "driver_dashboard"
private const val ROUTE_MERCHANT_DASHBOARD = "merchant_dashboard"
private const val ROUTE_ADMIN_DASHBOARD = "admin_dashboard"

private data class TopLevelDestination(
  val route: String,
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
)

private val TopLevelDestinations = listOf(
  TopLevelDestination(ROUTE_HOME, R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home),
  TopLevelDestination(ROUTE_ORDERS, R.string.nav_orders, Icons.Rounded.ReceiptLong, Icons.Outlined.ReceiptLong),
  TopLevelDestination(ROUTE_WALLET, R.string.nav_wallet, Icons.Rounded.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
  TopLevelDestination(ROUTE_ACCOUNT, R.string.nav_account, Icons.Rounded.Person, Icons.Outlined.Person),
)

class MainActivity : ComponentActivity() {
  private val pendingNotificationRoute = mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    FoxNotificationHelper.ensureChannelsCreated(this)
    pendingNotificationRoute.value = FoxNotificationRouter.extractRouteFromIntent(intent)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        FoxAuthGate(
          pendingNotificationRoute = pendingNotificationRoute.value,
          onConsumeNotificationRoute = { pendingNotificationRoute.value = null },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    val route = FoxNotificationRouter.extractRouteFromIntent(intent)
    if (!route.isNullOrBlank()) {
      pendingNotificationRoute.value = route
    }
  }
}

@Composable
fun FoxAuthGate(
  pendingNotificationRoute: String? = null,
  onConsumeNotificationRoute: () -> Unit = {},
) {
  val activityContext = LocalContext.current
  val authState by remember { Firebase.auth.authStateFlow() }
    .collectAsStateWithLifecycle(initialValue = AuthGateState.Loading)

  val coroutineScope = rememberCoroutineScope()
  var isSigningIn by remember { mutableStateOf(false) }
  var authError by remember { mutableStateOf<String?>(null) }
  var offlineDemoMode by remember { mutableStateOf(false) }
  var attemptedSilentRestore by remember { mutableStateOf(false) }

  LaunchedEffect(authState) {
    if (authState is AuthGateState.SignedOut && !attemptedSilentRestore) {
      attemptedSilentRestore = true
      attemptSilentGoogleSignIn(activityContext)
    }
  }

  when (val state = authState) {
    AuthGateState.Loading -> {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(FoxBlue),
        contentAlignment = Alignment.Center,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          FoxBrandMark(onDarkHeader = true)
          Spacer(modifier = Modifier.height(16.dp))
          CircularProgressIndicator(color = Color.White)
        }
      }
    }

    AuthGateState.SignedOut -> {
      if (offlineDemoMode) {
        FoxOfflineDemoApp(onReturnToCloudAuth = { offlineDemoMode = false })
      } else {
        AuthScreen(
          isLoading = isSigningIn,
          errorMessage = authError,
          onGoogleSignInClick = {
            coroutineScope.launch {
              isSigningIn = true
              authError = null
              val result = performInteractiveGoogleSignIn(activityContext)
              result.onFailure { err ->
                if (err !is GetCredentialCancellationException) {
                  authError = err.localizedMessage ?: "Không thể đăng nhập Google."
                }
              }
              isSigningIn = false
            }
          },
          onEnterOfflineDemoClick = { offlineDemoMode = true },
        )
      }
    }

    is AuthGateState.SignedIn -> {
      if (offlineDemoMode) {
        FoxOfflineDemoApp(onReturnToCloudAuth = { offlineDemoMode = false })
      } else {
        key(state.user.uid) {
          FoxAuthenticatedCloudApp(
            currentUser = state.user,
            pendingNotificationRoute = pendingNotificationRoute,
            onConsumeNotificationRoute = onConsumeNotificationRoute,
            onSwitchToOfflineDemo = { offlineDemoMode = true },
            onSignOut = {
              coroutineScope.launch {
                signOutGoogleSafely(activityContext)
              }
            },
          )
        }
      }
    }
  }
}

/**
 * Authenticated multi-device Cloud Firestore mode.
 * Strictly separated from local Room SQLite DEMO storage (never auto-syncs local DEMO into Firestore).
 */
@Composable
private fun FoxAuthenticatedCloudApp(
  currentUser: FirebaseUser,
  pendingNotificationRoute: String?,
  onConsumeNotificationRoute: () -> Unit,
  onSwitchToOfflineDemo: () -> Unit,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current.applicationContext
  val cloudViewModel: FoxCloudViewModel = viewModel(
    key = "fox_cloud_${currentUser.uid}",
    factory = FoxCloudViewModel.Factory(context, currentUser),
  )
  val cloudState by cloudViewModel.uiState.collectAsStateWithLifecycle()

  var searchQuery by remember { mutableStateOf("") }

  val baseCatalog = remember { FoxUiState() }

  val bridgedUiState = remember(cloudState, searchQuery) {
    val mappedRecentOrders = cloudState.customerOrders.map { co ->
      FoxOrderEntity(
        orderCode = co.orderCode.ifBlank { co.id },
        serviceType = co.serviceType,
        title = co.title,
        primaryAddress = co.primaryAddress,
        secondaryAddress = co.secondaryAddress,
        detailSummary = co.detailSummary,
        subtotalVnd = co.subtotalVnd,
        feeVnd = co.feeVnd,
        discountVnd = co.discountVnd,
        totalVnd = co.totalVnd,
        paymentMethod = co.paymentMethod,
        voucherCode = co.voucherCode.ifBlank { null },
        status = co.status,
        idempotencyKey = co.id,
        createdAt = co.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
      )
    }
    val mappedNotifs = cloudState.cloudNotifications.map { cn ->
      FoxNotificationEntity(
        id = cn.id,
        title = cn.title,
        body = cn.body,
        createdAt = cn.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
        isRead = cn.readFlag,
      )
    }
    baseCatalog.copy(
      searchQuery = searchQuery,
      demoWalletBalanceVnd = cloudState.cloudWallet.balanceVnd,
      orders = mappedRecentOrders,
      notifications = mappedNotifs,
      backendStatus = FoxBackendStatus(
        isProductionBackendConnected = true,
        activeStorageLabel = "Cloud Firestore (${cloudState.profile?.role ?: "customer"})",
        plannedSharedBackend = "Firebase Auth + Cloud Firestore + Cloud Functions v2 (DEMO)",
      ),
      isSubmitting = cloudState.isBusy,
    )
  }

  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_HOME
  val snackbarHostState = remember { SnackbarHostState() }
  val showPrimaryNavigation = TopLevelDestinations.any { it.route == currentRoute }

  LaunchedEffect(cloudState.actionNotice) {
    val notice = cloudState.actionNotice
    if (notice != null) {
      snackbarHostState.showSnackbar(notice)
      cloudViewModel.clearNotice()
    }
  }

  LaunchedEffect(cloudState.cloudError) {
    val err = cloudState.cloudError
    if (err != null) {
      snackbarHostState.showSnackbar(err)
      cloudViewModel.clearError()
    }
  }

  val coroutineScope = rememberCoroutineScope()

  fun navigateToTopLevel(route: String) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  fun openRouteFromNotification(route: String) {
    when (route) {
      ROUTE_ORDERS, ROUTE_HOME, ROUTE_WALLET, ROUTE_ACCOUNT -> navigateToTopLevel(route)
      ROUTE_DRIVER_DASHBOARD, ROUTE_MERCHANT_DASHBOARD, ROUTE_ADMIN_DASHBOARD, ROUTE_NOTIFICATIONS -> {
        navController.navigate(route) { launchSingleTop = true }
      }
      else -> navController.navigate(ROUTE_NOTIFICATIONS) { launchSingleTop = true }
    }
  }

  LaunchedEffect(pendingNotificationRoute) {
    if (!pendingNotificationRoute.isNullOrBlank()) {
      openRouteFromNotification(pendingNotificationRoute)
      onConsumeNotificationRoute()
    }
  }

  FoxNavigationShell(
    currentRoute = currentRoute,
    showPrimaryNavigation = showPrimaryNavigation,
    snackbarHostState = snackbarHostState,
    onNavigateTopLevel = ::navigateToTopLevel,
  ) { modifier ->
    NavHost(
      navController = navController,
      startDestination = ROUTE_HOME,
      modifier = modifier,
    ) {
      composable(ROUTE_HOME) {
        HomeScreen(
          uiState = bridgedUiState,
          onSearchQueryChange = { searchQuery = it },
          onServiceClick = { service -> navController.navigate(service.route) },
          onNotificationsClick = { navController.navigate(ROUTE_NOTIFICATIONS) },
          onWalletClick = { navigateToTopLevel(ROUTE_WALLET) },
          onViewAllOrdersClick = { navigateToTopLevel(ROUTE_ORDERS) },
        )
      }

      composable(ROUTE_ORDERS) {
        CloudOrdersScreen(
          cloudState = cloudState,
          onStartService = { service -> navController.navigate(service.route) },
          onPayOrderWithDemoWallet = cloudViewModel::payOrderWithDemoWallet,
          onCancelCloudOrder = cloudViewModel::cancelCloudOrder,
          onRateOrder = cloudViewModel::rateCloudOrder,
        )
      }

      composable(ROUTE_WALLET) {
        CloudWalletScreen(
          cloudState = cloudState,
          onTopUpDemo = cloudViewModel::topUpDemoWallet,
          onPayOrderWithDemoWallet = cloudViewModel::payOrderWithDemoWallet,
          onRefundOrderDemo = { orderId -> cloudViewModel.refundOrderDemo(orderId) },
          onRefreshWallet = cloudViewModel::refreshWalletSummary,
          onOpenOfflineDemo = onSwitchToOfflineDemo,
        )
      }

      composable(ROUTE_ACCOUNT) {
        CloudAccountScreen(
          cloudState = cloudState,
          onSaveProfile = cloudViewModel::updatePersonalProfile,
          onApplyDriver = cloudViewModel::applyAsDriver,
          onApplyMerchant = cloudViewModel::applyAsMerchant,
          onOpenDriverDashboard = { navController.navigate(ROUTE_DRIVER_DASHBOARD) },
          onOpenMerchantDashboard = { navController.navigate(ROUTE_MERCHANT_DASHBOARD) },
          onOpenAdminDashboard = { navController.navigate(ROUTE_ADMIN_DASHBOARD) },
          onSwitchToOfflineDemo = onSwitchToOfflineDemo,
          onSignOut = {
            coroutineScope.launch {
              cloudViewModel.cleanupFcmTokenBeforeSignOut()
              onSignOut()
            }
          },
        )
      }

      composable(ROUTE_NOTIFICATIONS) {
        CloudNotificationsScreen(
          cloudState = cloudState,
          onMarkRead = cloudViewModel::markCloudNotificationRead,
          onMarkAllRead = cloudViewModel::markAllCloudNotificationsRead,
          onSyncFcmToken = { cloudViewModel.syncFcmToken() },
          onNotificationClick = { notif ->
            val targetRoute = FoxNotificationRouter.routeForNotification(
              targetRole = notif.targetRole,
              eventType = notif.eventType,
              serviceType = notif.serviceType,
            )
            if (targetRoute != ROUTE_NOTIFICATIONS) {
              openRouteFromNotification(targetRoute)
            }
          },
          onBack = { navController.popBackStack() },
        )
      }

      composable(ROUTE_DRIVER_DASHBOARD) {
        DriverDashboardScreen(
          cloudState = cloudState,
          hasLocationPermission = cloudViewModel::hasLocationPermission,
          onToggleOnline = cloudViewModel::setDriverOnline,
          onClaimOrder = cloudViewModel::claimOrderAsDriver,
          onAdvanceOrder = cloudViewModel::advanceDriverOrder,
          onCancelOrder = cloudViewModel::cancelCloudOrder,
          onSettleCommission = cloudViewModel::settleOrderCommissionDemo,
          onStartLocationSharing = cloudViewModel::startDriverLocationSharing,
          onStopLocationSharing = { cloudViewModel.stopDriverLocationSharing() },
          onSyncFcmToken = { cloudViewModel.syncFcmToken() },
          onBack = { navController.popBackStack() },
        )
      }

      composable(ROUTE_MERCHANT_DASHBOARD) {
        MerchantDashboardScreen(
          cloudState = cloudState,
          onSaveStoreProfile = cloudViewModel::saveMerchantStoreProfile,
          onToggleStoreOpen = cloudViewModel::setMerchantStoreOpen,
          onClaimOrder = cloudViewModel::claimOrderAsMerchant,
          onConfirmAssignedOrder = cloudViewModel::confirmAssignedMerchantOrder,
          onAdvanceOrder = cloudViewModel::advanceMerchantOrder,
          onCancelOrder = cloudViewModel::cancelCloudOrder,
          onAddProduct = cloudViewModel::addMerchantProduct,
          onUpdateProduct = cloudViewModel::updateMerchantProduct,
          onToggleProductActive = cloudViewModel::setMerchantProductActive,
          onSyncFcmToken = { cloudViewModel.syncFcmToken() },
          onBack = { navController.popBackStack() },
        )
      }

      composable(ROUTE_ADMIN_DASHBOARD) {
        AdminDashboardScreen(
          cloudState = cloudState,
          onApproveDriver = cloudViewModel::adminApproveDriver,
          onApproveMerchant = cloudViewModel::adminApproveMerchant,
          onSetAccountActive = cloudViewModel::adminSetAccountActive,
          onCancelOrder = cloudViewModel::cancelCloudOrder,
          onBack = { navController.popBackStack() },
        )
      }

      composable(FoxServiceType.RIDE.route) {
        CloudRideScreen(
          cloudState = cloudState,
          hasLocationPermission = cloudViewModel::hasLocationPermission,
          onFetchCurrentGps = cloudViewModel::fetchCurrentGpsWithAddress,
          onSearchPlaces = cloudViewModel::searchPlaces,
          onComputeRoute = cloudViewModel::computeRouteForBooking,
          onOrderCreated = {
            navController.popBackStack()
            navigateToTopLevel(ROUTE_ORDERS)
          },
          onSubmitRide = { pickup, destination, vehicle, km, note, voucher, payment, pickupCoords, dropoffCoords, onSuccess ->
            cloudViewModel.submitCloudRideOrder(
              pickup = pickup,
              destination = destination,
              vehicleType = vehicle,
              distanceKm = km,
              note = note,
              voucherCode = voucher,
              paymentMethod = payment,
              pickupCoords = pickupCoords,
              dropoffCoords = dropoffCoords,
              onSuccess = onSuccess,
            )
          },
          onBack = { navController.popBackStack() },
        )
      }

      composable(FoxServiceType.DELIVERY.route) {
        CloudDeliveryScreen(
          cloudState = cloudState,
          hasLocationPermission = cloudViewModel::hasLocationPermission,
          onFetchCurrentGps = cloudViewModel::fetchCurrentGpsWithAddress,
          onSearchPlaces = cloudViewModel::searchPlaces,
          onComputeRoute = cloudViewModel::computeRouteForBooking,
          onOrderCreated = {
            navController.popBackStack()
            navigateToTopLevel(ROUTE_ORDERS)
          },
          onSubmitDelivery = { sName, sPhone, pAddr, rName, rPhone, dAddr, itemType, weight, speed, note, voucher, payment, distanceKm, pickupCoords, dropoffCoords, onSuccess ->
            cloudViewModel.submitCloudDeliveryOrder(
              senderName = sName,
              senderPhone = sPhone,
              pickupAddress = pAddr,
              receiverName = rName,
              receiverPhone = rPhone,
              dropoffAddress = dAddr,
              itemType = itemType,
              weightTier = weight,
              speed = speed,
              note = note,
              voucherCode = voucher,
              paymentMethod = payment,
              distanceKm = distanceKm,
              pickupCoords = pickupCoords,
              dropoffCoords = dropoffCoords,
              onSuccess = onSuccess,
            )
          },
          onBack = { navController.popBackStack() },
        )
      }

      composable(FoxServiceType.FOOD.route) {
        CloudFoodScreen(
          cloudState = cloudState,
          onOrderCreated = {
            navController.popBackStack()
            navigateToTopLevel(ROUTE_ORDERS)
          },
          onSubmitFood = cloudViewModel::submitCloudFoodOrder,
          onBack = { navController.popBackStack() },
        )
      }

      composable(FoxServiceType.SHOPPING.route) {
        CloudShoppingScreen(
          cloudState = cloudState,
          onOrderCreated = {
            navController.popBackStack()
            navigateToTopLevel(ROUTE_ORDERS)
          },
          onSubmitShopping = cloudViewModel::submitCloudShoppingOrder,
          onBack = { navController.popBackStack() },
        )
      }
    }
  }
}

/**
 * Isolated offline Room SQLite DEMO mode (zero Firestore listeners, preserves all existing Room records).
 */
@Composable
fun FoxOfflineDemoApp(
  onReturnToCloudAuth: () -> Unit,
) {
  val context = LocalContext.current.applicationContext
  val repository = remember(context) {
    RoomDemoFoxRepository(FoxDatabase.getInstance(context))
  }
  val foxViewModel: FoxViewModel = viewModel(factory = FoxViewModel.Factory(repository))
  val uiState by foxViewModel.uiState.collectAsStateWithLifecycle()

  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_HOME
  val snackbarHostState = remember { SnackbarHostState() }
  val showPrimaryNavigation = TopLevelDestinations.any { it.route == currentRoute }

  LaunchedEffect(uiState.lastActionNotice) {
    val notice = uiState.lastActionNotice
    if (notice != null) {
      snackbarHostState.showSnackbar(notice)
      foxViewModel.clearActionNotice()
    }
  }

  fun navigateToTopLevel(route: String) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  FoxNavigationShell(
    currentRoute = currentRoute,
    showPrimaryNavigation = showPrimaryNavigation,
    snackbarHostState = snackbarHostState,
    onNavigateTopLevel = ::navigateToTopLevel,
  ) { modifier ->
    NavHost(
      navController = navController,
      startDestination = ROUTE_HOME,
      modifier = modifier,
    ) {
      composable(ROUTE_HOME) {
        HomeScreen(
          uiState = uiState,
          onSearchQueryChange = foxViewModel::updateSearchQuery,
          onServiceClick = { service -> navController.navigate(service.route) },
          onNotificationsClick = { navController.navigate(ROUTE_NOTIFICATIONS) },
          onWalletClick = { navigateToTopLevel(ROUTE_WALLET) },
          onViewAllOrdersClick = { navigateToTopLevel(ROUTE_ORDERS) },
        )
      }

      composable(ROUTE_ORDERS) {
        OrdersScreen(
          uiState = uiState,
          onStartService = { service -> navController.navigate(service.route) },
          onCancelOrder = foxViewModel::cancelDemoOrder,
        )
      }

      composable(ROUTE_WALLET) {
        WalletScreen(uiState = uiState)
      }

      composable(ROUTE_ACCOUNT) {
        AccountScreen(
          uiState = uiState,
          onSelectRolePreview = foxViewModel::selectRolePreview,
          onOpenCloudSignIn = onReturnToCloudAuth,
        )
      }

      composable(ROUTE_NOTIFICATIONS) {
        NotificationsScreen(
          uiState = uiState,
          onMarkRead = foxViewModel::markNotificationRead,
          onMarkAllRead = foxViewModel::markAllNotificationsRead,
          onBack = { navController.popBackStack() },
        )
      }

      composable(FoxServiceType.RIDE.route) {
        RideScreen(
          uiState = uiState,
          onBack = { navController.popBackStack() },
          onOrderCreated = {
            navController.popBackStack()
            navigateToTopLevel(ROUTE_ORDERS)
          },
          onSubmitRide = { pickup, destination, vehicle, km, note, voucher, payment, onSuccess ->
            foxViewModel.submitRideOrder(
              pickup = pickup,
              destination = destination,
              vehicleType = vehicle,
              distanceKm = km,
              note = note,
              voucherCode = voucher,
              paymentMethod = payment,
            ) { result -> if (result.success) onSuccess() }
          },
        )
      }

      composable(FoxServiceType.DELIVERY.route) {
        DeliveryScreen(
          uiState = uiState,
          onBack = { navController.popBackStack() },
          onOrderCreated = {
            navController.popBackStack()
            navigateToTopLevel(ROUTE_ORDERS)
          },
          onSubmitDelivery = { sName, sPhone, pAddr, rName, rPhone, dAddr, itemType, weight, speed, note, voucher, payment, onSuccess ->
            foxViewModel.submitDeliveryOrder(
              senderName = sName,
              senderPhone = sPhone,
              pickupAddress = pAddr,
              receiverName = rName,
              receiverPhone = rPhone,
              dropoffAddress = dAddr,
              itemType = itemType,
              weightTier = weight,
              speed = speed,
              note = note,
              voucherCode = voucher,
              paymentMethod = payment,
            ) { result -> if (result.success) onSuccess() }
          },
        )
      }

      composable(FoxServiceType.FOOD.route) {
        FoodScreen(
          uiState = uiState,
          onUpdateCart = foxViewModel::updateFoodCart,
          onOrderCreated = {
            navController.popBackStack()
            navigateToTopLevel(ROUTE_ORDERS)
          },
          onSubmitFood = { address, voucher, payment, onSuccess ->
            foxViewModel.submitFoodOrder(address, voucher, payment) { result ->
              if (result.success) onSuccess()
            }
          },
          onBack = { navController.popBackStack() },
        )
      }

      composable(FoxServiceType.SHOPPING.route) {
        ShoppingScreen(
          uiState = uiState,
          onUpdateCart = foxViewModel::updateShopCart,
          onOrderCreated = {
            navController.popBackStack()
            navigateToTopLevel(ROUTE_ORDERS)
          },
          onSubmitShopping = { address, voucher, payment, onSuccess ->
            foxViewModel.submitShoppingOrder(address, voucher, payment) { result ->
              if (result.success) onSuccess()
            }
          },
          onBack = { navController.popBackStack() },
        )
      }
    }
  }
}

@Composable
private fun FoxNavigationShell(
  currentRoute: String,
  showPrimaryNavigation: Boolean,
  snackbarHostState: SnackbarHostState,
  onNavigateTopLevel: (String) -> Unit,
  content: @Composable (Modifier) -> Unit,
) {
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavigationRail = maxWidth >= 600.dp

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      bottomBar = {
        if (showPrimaryNavigation && !useNavigationRail) {
          NavigationBar(
            containerColor = Color.White,
            windowInsets = WindowInsets.navigationBars,
            tonalElevation = 6.dp,
          ) {
            TopLevelDestinations.forEach { destination ->
              val selected = currentRoute == destination.route
              val label = stringResource(destination.labelRes)
              NavigationBarItem(
                selected = selected,
                onClick = { onNavigateTopLevel(destination.route) },
                modifier = Modifier.testTag("nav_tab_${destination.route}"),
                icon = {
                  Icon(
                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                    contentDescription = label,
                  )
                },
                label = {
                  Text(
                    text = label,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                  )
                },
                colors = NavigationBarItemDefaults.colors(
                  selectedIconColor = FoxBlue,
                  selectedTextColor = FoxBlue,
                  unselectedIconColor = FoxTextSecondary,
                  unselectedTextColor = FoxTextSecondary,
                  indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        if (showPrimaryNavigation && useNavigationRail) {
          NavigationRail(containerColor = Color.White) {
            TopLevelDestinations.forEach { destination ->
              val selected = currentRoute == destination.route
              val label = stringResource(destination.labelRes)
              NavigationRailItem(
                selected = selected,
                onClick = { onNavigateTopLevel(destination.route) },
                modifier = Modifier.testTag("nav_rail_${destination.route}"),
                icon = {
                  Icon(
                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                    contentDescription = label,
                  )
                },
                label = { Text(label) },
              )
            }
          }
        }
        content(Modifier.weight(1f))
      }
    }
  }
}
