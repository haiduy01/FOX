package com.example

import com.example.config.FoxEnvironment
import com.example.config.FoxEnvironmentConfig
import com.example.data.location.MapConfigStatus
import com.example.data.location.MapRenderMode
import com.example.data.remote.CloudMerchantProduct
import com.example.data.remote.CloudOrder
import com.example.data.remote.CloudPlatformRevenueEntry
import com.example.data.remote.CloudUserProfile
import com.example.data.remote.canCustomerRateOrder
import com.example.notifications.FoxFcmTokenHasher
import com.example.notifications.FoxNotificationChannels
import com.example.notifications.FoxNotificationRouter
import com.example.ui.FoxDashboardAnalytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoxFcmAndPilotTest {

  @Test
  fun mapConfigStatus_locksRealMapsAndRoutesWhenApiKeyUnconfigured() {
    val unconfigured = MapConfigStatus.fromApiKey(MapConfigStatus.PLACEHOLDER_KEY)
    assertFalse(unconfigured.isRealMapsKeyConfigured)
    assertEquals(MapRenderMode.DEMO_INTERACTIVE_MAP, unconfigured.activeRenderMode)
    assertTrue(unconfigured.isRealGoogleMapsFeatureLocked)
    assertTrue(unconfigured.isRealRoutesApiFeatureLocked)
    assertTrue(unconfigured.lockedFeatureSummary.contains("TẠM KHÓA"))

    val blankKey = MapConfigStatus.fromApiKey("   ")
    assertTrue(blankKey.isRealGoogleMapsFeatureLocked)
    assertTrue(blankKey.isRealRoutesApiFeatureLocked)

    val configured = MapConfigStatus.fromApiKey("VALID_TEST_KEY_1234567890_ABCDEF")
    assertTrue(configured.isRealMapsKeyConfigured)
    assertEquals(MapRenderMode.REAL_GOOGLE_MAPS, configured.activeRenderMode)
    assertFalse(configured.isRealGoogleMapsFeatureLocked)
    assertFalse(configured.isRealRoutesApiFeatureLocked)
  }

  @Test
  fun notificationRouter_mapsAllFourRolesToChannelsAndRoutes() {
    assertEquals(FoxNotificationChannels.CUSTOMER_ORDERS, FoxNotificationRouter.channelForRole("customer"))
    assertEquals(FoxNotificationChannels.DRIVER_ORDERS, FoxNotificationRouter.channelForRole("driver"))
    assertEquals(FoxNotificationChannels.MERCHANT_ORDERS, FoxNotificationRouter.channelForRole("merchant"))
    assertEquals(FoxNotificationChannels.ADMIN_ALERTS, FoxNotificationRouter.channelForRole("admin"))
    assertEquals(FoxNotificationChannels.CUSTOMER_ORDERS, FoxNotificationRouter.channelForRole(null))

    assertEquals(FoxNotificationRouter.ROUTE_ORDERS, FoxNotificationRouter.routeForNotification("customer", "driver_accepted", "RIDE"))
    assertEquals(FoxNotificationRouter.ROUTE_DRIVER_DASHBOARD, FoxNotificationRouter.routeForNotification("driver", "order_created", "RIDE"))
    assertEquals(FoxNotificationRouter.ROUTE_MERCHANT_DASHBOARD, FoxNotificationRouter.routeForNotification("merchant", "order_created", "FOOD"))
    assertEquals(FoxNotificationRouter.ROUTE_ADMIN_DASHBOARD, FoxNotificationRouter.routeForNotification("admin", "partner_application_submitted", null))
    assertEquals(FoxNotificationRouter.ROUTE_ADMIN_DASHBOARD, FoxNotificationRouter.routeForNotification(null, "partner_application_submitted", null))

    assertEquals("Tài xế", FoxNotificationRouter.roleBadgeLabel("driver"))
    assertEquals("Gian hàng", FoxNotificationRouter.roleBadgeLabel("merchant"))
    assertEquals("Quản trị", FoxNotificationRouter.roleBadgeLabel("admin"))
    assertEquals("Khách hàng", FoxNotificationRouter.roleBadgeLabel("customer"))
  }

  @Test
  fun fcmTokenHasher_producesDeterministicFirestoreSafeDocIds() {
    val rawA = "fcm_token_example_android_device_alpha_9988776655"
    val rawB = "fcm_token_example_android_device_beta_1122334455"

    val docA1 = FoxFcmTokenHasher.tokenDocId(rawA)
    val docA2 = FoxFcmTokenHasher.tokenDocId("  $rawA  ")
    val docB = FoxFcmTokenHasher.tokenDocId(rawB)

    assertEquals(docA1, docA2)
    assertNotEquals(docA1, docB)
    assertTrue(docA1.startsWith("tok_"))
    assertTrue(FoxFcmTokenHasher.isValidFirestoreTokenDocId(docA1))
    assertTrue(FoxFcmTokenHasher.isValidFirestoreTokenDocId(docB))
  }

  @Test
  fun dashboardAnalytics_computesExactIntegerVndKpisForDriverMerchantAdmin() {
    val orders = listOf(
      CloudOrder(
        id = "ORD-1",
        serviceType = "RIDE",
        status = "completed",
        totalVnd = 50_000L,
        driverCommissionVnd = 40_000L,
        platformRevenueVnd = 10_000L,
        commissionSettled = true,
      ),
      CloudOrder(
        id = "ORD-2",
        serviceType = "DELIVERY",
        status = "in_progress",
        totalVnd = 35_000L,
        commissionSettled = false,
      ),
      CloudOrder(
        id = "ORD-3",
        serviceType = "FOOD",
        status = "completed",
        totalVnd = 120_000L,
        driverCommissionVnd = 18_000L,
        merchantCommissionVnd = 84_000L,
        platformRevenueVnd = 18_000L,
        commissionSettled = true,
      ),
      CloudOrder(
        id = "ORD-4",
        serviceType = "FOOD",
        status = "pending",
        totalVnd = 65_000L,
      ),
      CloudOrder(
        id = "ORD-5",
        serviceType = "SHOPPING",
        status = "cancelled",
        totalVnd = 90_000L,
      ),
    )

    val driverKpis = FoxDashboardAnalytics.computeDriverKpis(orders)
    assertEquals(1, driverKpis.activeOrdersCount)
    assertEquals(2, driverKpis.completedOrdersCount)
    assertEquals(1, driverKpis.cancelledOrdersCount)
    assertEquals(58_000L, driverKpis.settledCommissionVnd)
    assertEquals(0, driverKpis.unsettledCompletedCount)

    val products = listOf(
      CloudMerchantProduct(id = "p1", name = "Phở Bò", priceVnd = 55_000L, stock = 20L, active = true),
      CloudMerchantProduct(id = "p2", name = "Bún Bò", priceVnd = 50_000L, stock = 3L, active = true),
      CloudMerchantProduct(id = "p3", name = "Trà Đá", priceVnd = 10_000L, stock = 0L, active = true),
      CloudMerchantProduct(id = "p4", name = "Món ẩn", priceVnd = 40_000L, stock = 0L, active = false),
    )
    val merchantKpis = FoxDashboardAnalytics.computeMerchantKpis(orders, products)
    assertEquals(2, merchantKpis.pendingOrActiveOrdersCount)
    assertEquals(2, merchantKpis.completedOrdersCount)
    assertEquals(84_000L, merchantKpis.settledRevenueVnd)
    assertEquals(3, merchantKpis.activeProductsCount)
    assertEquals(1, merchantKpis.lowStockProductsCount)
    assertEquals(1, merchantKpis.outOfStockProductsCount)

    val users = listOf(
      CloudUserProfile(userId = "u1", role = "customer", driverStatus = "pending", accountStatus = "active"),
      CloudUserProfile(userId = "u2", role = "driver", driverStatus = "approved", driverOnline = true, accountStatus = "active"),
      CloudUserProfile(userId = "u3", role = "driver", driverStatus = "approved", driverOnline = false, accountStatus = "active"),
      CloudUserProfile(userId = "u4", role = "merchant", merchantStatus = "approved", accountStatus = "active"),
      CloudUserProfile(userId = "u5", role = "customer", accountStatus = "suspended"),
    )
    val revenueEntries = listOf(
      CloudPlatformRevenueEntry(orderId = "ORD-1", platformRevenueVnd = 10_000L),
      CloudPlatformRevenueEntry(orderId = "ORD-3", platformRevenueVnd = 18_000L),
    )
    val adminKpis = FoxDashboardAnalytics.computeAdminKpis(users, orders, revenueEntries)
    assertEquals(5, adminKpis.totalUsers)
    assertEquals(1, adminKpis.pendingPartnersCount)
    assertEquals(2, adminKpis.approvedDriversCount)
    assertEquals(1, adminKpis.onlineDriversCount)
    assertEquals(1, adminKpis.approvedMerchantsCount)
    assertEquals(1, adminKpis.suspendedUsersCount)
    assertEquals(2, adminKpis.activeOrdersCount)
    assertEquals(2, adminKpis.completedOrdersCount)
    assertEquals(1, adminKpis.cancelledOrdersCount)
    assertEquals(170_000L, adminKpis.grossCompletedVolumeVnd)
    assertEquals(28_000L, adminKpis.platformRevenueVnd)
  }

  @Test
  fun environmentConfig_isolatesDemoStagingProductionAndGuardsLiveProject() {
    val demo = FoxEnvironmentConfig.resolve("DEMO")
    assertEquals(FoxEnvironment.DEMO, demo.environment)
    assertTrue(demo.isDemoFinanceOnly)
    assertFalse(demo.allowsLiveProductionWrites)

    val staging = FoxEnvironmentConfig.resolve("STAGING")
    assertEquals(FoxEnvironment.STAGING, staging.environment)
    assertTrue(staging.isDemoFinanceOnly)
    assertFalse(staging.allowsLiveProductionWrites)

    val prod = FoxEnvironmentConfig.resolve("PRODUCTION")
    assertEquals(FoxEnvironment.PRODUCTION, prod.environment)
    assertFalse(prod.isDemoFinanceOnly)
    assertTrue(prod.allowsLiveProductionWrites)

    // Guardrail MUST reject the live production project ID and named database ID
    assertFalse(
      FoxEnvironmentConfig.isSafeStagingTarget(
        FoxEnvironmentConfig.PRODUCTION_FIREBASE_PROJECT_ID,
        "fox-staging-db",
      )
    )
    assertFalse(
      FoxEnvironmentConfig.isSafeStagingTarget(
        "fox-pilot-staging",
        FoxEnvironmentConfig.PRODUCTION_FIRESTORE_DB_ID,
      )
    )
    assertFalse(FoxEnvironmentConfig.isSafeStagingTarget("", "fox-staging-db"))
    assertFalse(FoxEnvironmentConfig.isSafeStagingTarget("your-fox-pilot-staging", "fox-staging-db"))
    assertTrue(FoxEnvironmentConfig.isSafeStagingTarget("fox-pilot-staging", "fox-staging-db"))

    // Staging mode resolves to staging DB ID instead of live resource DB ID
    assertEquals(
      "fox-staging-db",
      FoxEnvironmentConfig.resolveFirestoreDatabaseId(
        rawEnv = "STAGING",
        defaultResourceDbId = FoxEnvironmentConfig.PRODUCTION_FIRESTORE_DB_ID,
        stagingDbId = "fox-staging-db",
        runtimeFirebaseProjectId = "fox-pilot-staging",
      )
    )
    // DEMO mode keeps default resource DB ID
    assertEquals(
      FoxEnvironmentConfig.PRODUCTION_FIRESTORE_DB_ID,
      FoxEnvironmentConfig.resolveFirestoreDatabaseId(
        rawEnv = "DEMO",
        defaultResourceDbId = FoxEnvironmentConfig.PRODUCTION_FIRESTORE_DB_ID,
        stagingDbId = "fox-staging-db",
        runtimeFirebaseProjectId = FoxEnvironmentConfig.PRODUCTION_FIREBASE_PROJECT_ID,
      )
    )
    // STAGING mode MUST throw if accidentally paired with the live Firebase project ID
    var blockedLiveProject = false
    try {
      FoxEnvironmentConfig.resolveFirestoreDatabaseId(
        rawEnv = "STAGING",
        defaultResourceDbId = FoxEnvironmentConfig.PRODUCTION_FIRESTORE_DB_ID,
        stagingDbId = "fox-staging-db",
        runtimeFirebaseProjectId = FoxEnvironmentConfig.PRODUCTION_FIREBASE_PROJECT_ID,
      )
    } catch (_: IllegalArgumentException) {
      blockedLiveProject = true
    }
    assertTrue("Staging mode must refuse to connect to live Firebase project", blockedLiveProject)
  }

  @Test
  fun notificationRouter_extractsForegroundAndBackgroundFcmRoutesAndRatingEligibility() {
    // Explicit foreground route takes precedence
    assertEquals(
      FoxNotificationRouter.ROUTE_DRIVER_DASHBOARD,
      FoxNotificationRouter.extractRouteFromExtras(
        explicitRoute = FoxNotificationRouter.ROUTE_DRIVER_DASHBOARD,
        targetRole = "customer",
        eventType = "order_created",
        serviceType = "RIDE",
      )
    )
    // Background FCM tap without explicit route resolves from raw data keys
    assertEquals(
      FoxNotificationRouter.ROUTE_MERCHANT_DASHBOARD,
      FoxNotificationRouter.extractRouteFromExtras(
        explicitRoute = null,
        targetRole = "merchant",
        eventType = "order_created",
        serviceType = "FOOD",
      )
    )
    assertEquals(
      FoxNotificationRouter.ROUTE_ADMIN_DASHBOARD,
      FoxNotificationRouter.extractRouteFromExtras(
        explicitRoute = null,
        targetRole = null,
        eventType = "partner_application_submitted",
        serviceType = null,
      )
    )
    assertNull(FoxNotificationRouter.extractRouteFromExtras(null, null, null, null))

    // Completed order rating eligibility check
    assertTrue(canCustomerRateOrder(CloudOrder(status = "completed", customerRating = 0)))
    assertFalse(canCustomerRateOrder(CloudOrder(status = "completed", customerRating = 5)))
    assertFalse(canCustomerRateOrder(CloudOrder(status = "in_progress", customerRating = 0)))
    assertFalse(canCustomerRateOrder(CloudOrder(status = "cancelled", customerRating = 0)))
  }
}
