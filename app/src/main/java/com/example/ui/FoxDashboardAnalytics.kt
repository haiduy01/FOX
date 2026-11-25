package com.example.ui

import com.example.data.remote.CloudMerchantProduct
import com.example.data.remote.CloudOrder
import com.example.data.remote.CloudPlatformRevenueEntry
import com.example.data.remote.CloudUserProfile

data class DriverKpiSummary(
  val activeOrdersCount: Int,
  val completedOrdersCount: Int,
  val cancelledOrdersCount: Int,
  val settledCommissionVnd: Long,
  val unsettledCompletedCount: Int,
)

data class MerchantKpiSummary(
  val pendingOrActiveOrdersCount: Int,
  val completedOrdersCount: Int,
  val settledRevenueVnd: Long,
  val activeProductsCount: Int,
  val lowStockProductsCount: Int,
  val outOfStockProductsCount: Int,
)

data class AdminKpiSummary(
  val totalUsers: Int,
  val pendingPartnersCount: Int,
  val approvedDriversCount: Int,
  val onlineDriversCount: Int,
  val approvedMerchantsCount: Int,
  val suspendedUsersCount: Int,
  val activeOrdersCount: Int,
  val completedOrdersCount: Int,
  val cancelledOrdersCount: Int,
  val grossCompletedVolumeVnd: Long,
  val platformRevenueVnd: Long,
)

/**
 * Pure integer-VND analytics engine for FOX Driver, Merchant, and Admin dashboards.
 * Zero Double arithmetic — all currency sums stay strictly in Long VND.
 */
object FoxDashboardAnalytics {
  const val LOW_STOCK_THRESHOLD = 5L

  fun computeDriverKpis(assignedOrders: List<CloudOrder>): DriverKpiSummary {
    val active = assignedOrders.count { it.status in listOf("driver_accepted", "driver_arrived", "in_progress") }
    val completed = assignedOrders.filter { it.status == "completed" }
    val cancelled = assignedOrders.count { it.status == "cancelled" }
    val commissionSum = completed.filter { it.commissionSettled }.sumOf { it.driverCommissionVnd }
    val unsettled = completed.count { !it.commissionSettled }
    return DriverKpiSummary(
      activeOrdersCount = active,
      completedOrdersCount = completed.size,
      cancelledOrdersCount = cancelled,
      settledCommissionVnd = commissionSum,
      unsettledCompletedCount = unsettled,
    )
  }

  fun computeMerchantKpis(
    assignedOrders: List<CloudOrder>,
    products: List<CloudMerchantProduct>,
  ): MerchantKpiSummary {
    val activeOrders = assignedOrders.count {
      it.status in listOf("pending", "merchant_confirmed", "preparing", "ready_for_pickup", "driver_accepted", "driver_arrived", "in_progress")
    }
    val completed = assignedOrders.filter { it.status == "completed" }
    val revenue = completed.filter { it.commissionSettled }.sumOf { it.merchantCommissionVnd }
    val activeProducts = products.filter { it.active }
    val lowStock = activeProducts.count { it.stock in 1L..LOW_STOCK_THRESHOLD }
    val outOfStock = activeProducts.count { it.stock <= 0L }
    return MerchantKpiSummary(
      pendingOrActiveOrdersCount = activeOrders,
      completedOrdersCount = completed.size,
      settledRevenueVnd = revenue,
      activeProductsCount = activeProducts.size,
      lowStockProductsCount = lowStock,
      outOfStockProductsCount = outOfStock,
    )
  }

  fun computeAdminKpis(
    users: List<CloudUserProfile>,
    orders: List<CloudOrder>,
    revenueEntries: List<CloudPlatformRevenueEntry>,
  ): AdminKpiSummary {
    val pendingPartners = users.count { it.driverStatus == "pending" || it.merchantStatus == "pending" }
    val approvedDrivers = users.filter { it.isApprovedDriver() }
    val onlineDrivers = approvedDrivers.count { it.driverOnline }
    val approvedMerchants = users.count { it.isApprovedMerchant() }
    val suspendedUsers = users.count { it.accountStatus == "suspended" }

    val completedOrders = orders.filter { it.status == "completed" }
    val cancelledOrders = orders.count { it.status == "cancelled" }
    val activeOrders = orders.size - completedOrders.size - cancelledOrders

    return AdminKpiSummary(
      totalUsers = users.size,
      pendingPartnersCount = pendingPartners,
      approvedDriversCount = approvedDrivers.size,
      onlineDriversCount = onlineDrivers,
      approvedMerchantsCount = approvedMerchants,
      suspendedUsersCount = suspendedUsers,
      activeOrdersCount = activeOrders.coerceAtLeast(0),
      completedOrdersCount = completedOrders.size,
      cancelledOrdersCount = cancelledOrders,
      grossCompletedVolumeVnd = completedOrders.sumOf { it.totalVnd },
      platformRevenueVnd = revenueEntries.sumOf { it.platformRevenueVnd },
    )
  }
}
