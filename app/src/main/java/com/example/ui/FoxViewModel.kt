package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.FoxNotificationEntity
import com.example.data.local.FoxOrderEntity
import com.example.data.local.FoxWalletTxEntity
import com.example.data.repository.FoxBackendStatus
import com.example.data.repository.FoxRepository
import com.example.data.repository.OrderCreationResult
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FoxServiceType(
  val route: String,
  val title: String,
  val subtitle: String,
  val badge: String,
) {
  RIDE("ride", "Đặt xe", "FOX Bike, Car 4 & Car 7", "Nhanh 3 phút"),
  DELIVERY("delivery", "Giao hàng", "Giao siêu tốc nội thành", "An toàn"),
  FOOD("food", "Đặt đồ ăn", "Món ngon giao nóng hổi", "Giảm 30%"),
  SHOPPING("shopping", "Mua sắm", "Siêu thị & gian hàng", "Freeship"),
}

enum class FoxRolePreview(val label: String, val summary: String) {
  CUSTOMER("Khách hàng", "Đang kích hoạt 4 luồng DEMO lưu vào Room SQLite"),
  DRIVER("Tài xế", "Nhận chuyến & đơn giao (sẽ mở khi kết nối backend/Auth)"),
  MERCHANT("Gian hàng", "Quản lý thực đơn & tồn kho (sẽ mở khi kết nối backend/Auth)"),
  ADMIN("Quản trị", "Điều phối & bảng giá (chỉ xác thực quyền từ server)"),
}

data class DemoPromoBanner(
  val id: String,
  val tag: String,
  val title: String,
  val subtitle: String,
  val targetService: FoxServiceType,
  val code: String,
)

data class DemoMenuDish(
  val id: String,
  val restaurantName: String,
  val dishName: String,
  val category: String,
  val rating: String,
  val priceVnd: Long,
  val deliveryFeeVnd: Long,
)

data class DemoShopProduct(
  val id: String,
  val storeName: String,
  val name: String,
  val category: String,
  val priceVnd: Long,
  val stock: Int,
  val rating: String,
)

private data class LocalUiSessionState(
  val searchQuery: String = "",
  val selectedRolePreview: FoxRolePreview = FoxRolePreview.CUSTOMER,
  val foodCart: Map<String, Int> = emptyMap(),
  val shopCart: Map<String, Int> = emptyMap(),
  val isSubmitting: Boolean = false,
  val lastActionNotice: String? = null,
)

data class FoxUiState(
  val searchQuery: String = "",
  val selectedRolePreview: FoxRolePreview = FoxRolePreview.CUSTOMER,
  val demoWalletBalanceVnd: Long = 0L,
  val orders: List<FoxOrderEntity> = emptyList(),
  val walletTransactions: List<FoxWalletTxEntity> = emptyList(),
  val notifications: List<FoxNotificationEntity> = emptyList(),
  val promos: List<DemoPromoBanner> = defaultPromos(),
  val foodMenu: List<DemoMenuDish> = defaultFoodMenu(),
  val foodCart: Map<String, Int> = emptyMap(),
  val shopProducts: List<DemoShopProduct> = defaultShopProducts(),
  val shopCart: Map<String, Int> = emptyMap(),
  val backendStatus: FoxBackendStatus = FoxBackendStatus(),
  val isSubmitting: Boolean = false,
  val lastActionNotice: String? = null,
) {
  val unreadNotificationsCount: Int
    get() = notifications.count { !it.isRead }

  val filteredServices: List<FoxServiceType>
    get() {
      val q = searchQuery.trim().lowercase(Locale.ROOT)
      if (q.isEmpty()) return FoxServiceType.entries
      return FoxServiceType.entries.filter {
        it.title.lowercase(Locale.ROOT).contains(q) ||
          it.subtitle.lowercase(Locale.ROOT).contains(q) ||
          it.badge.lowercase(Locale.ROOT).contains(q)
      }
    }
}

class FoxViewModel(
  private val repository: FoxRepository,
) : ViewModel() {

  private val localSession = MutableStateFlow(LocalUiSessionState())

  val uiState: StateFlow<FoxUiState> =
    combine(
      localSession,
      repository.observeOrders(),
      repository.observeNotifications(),
      repository.observeWalletBalanceVnd(),
      repository.observeWalletTransactions(),
    ) { session, orders, notifications, balanceVnd, walletTxs ->
      FoxUiState(
        searchQuery = session.searchQuery,
        selectedRolePreview = session.selectedRolePreview,
        demoWalletBalanceVnd = balanceVnd,
        orders = orders,
        walletTransactions = walletTxs,
        notifications = notifications,
        foodCart = session.foodCart,
        shopCart = session.shopCart,
        backendStatus = repository.backendStatus,
        isSubmitting = session.isSubmitting,
        lastActionNotice = session.lastActionNotice,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = FoxUiState(backendStatus = repository.backendStatus),
    )

  init {
    viewModelScope.launch {
      repository.ensureDemoSeeded()
    }
  }

  fun updateSearchQuery(query: String) {
    localSession.update { it.copy(searchQuery = query) }
  }

  fun selectRolePreview(role: FoxRolePreview) {
    localSession.update { it.copy(selectedRolePreview = role) }
  }

  fun clearActionNotice() {
    localSession.update { it.copy(lastActionNotice = null) }
  }

  fun markNotificationRead(id: String) {
    viewModelScope.launch { repository.markNotificationRead(id) }
  }

  fun markAllNotificationsRead() {
    viewModelScope.launch { repository.markAllNotificationsRead() }
  }

  fun submitRideOrder(
    pickup: String,
    destination: String,
    vehicleType: String,
    distanceKm: Int,
    note: String,
    voucherCode: String?,
    paymentMethod: String,
    onResult: (OrderCreationResult) -> Unit,
  ) {
    viewModelScope.launch {
      localSession.update { it.copy(isSubmitting = true) }
      val result =
        repository.createRideOrder(
          pickup = pickup,
          destination = destination,
          vehicleType = vehicleType,
          distanceKm = distanceKm,
          note = note,
          voucherCode = voucherCode,
          paymentMethod = paymentMethod,
        )
      localSession.update { it.copy(isSubmitting = false, lastActionNotice = result.message) }
      onResult(result)
    }
  }

  fun submitDeliveryOrder(
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
    onResult: (OrderCreationResult) -> Unit,
  ) {
    viewModelScope.launch {
      localSession.update { it.copy(isSubmitting = true) }
      val result =
        repository.createDeliveryOrder(
          senderName = senderName,
          senderPhone = senderPhone,
          pickupAddress = pickupAddress,
          receiverName = receiverName,
          receiverPhone = receiverPhone,
          dropoffAddress = dropoffAddress,
          itemType = itemType,
          weightTier = weightTier,
          speed = speed,
          note = note,
          voucherCode = voucherCode,
          paymentMethod = paymentMethod,
        )
      localSession.update { it.copy(isSubmitting = false, lastActionNotice = result.message) }
      onResult(result)
    }
  }

  fun updateFoodCart(dishId: String, delta: Int) {
    localSession.update { state ->
      val current = state.foodCart[dishId] ?: 0
      val next = (current + delta).coerceAtLeast(0)
      val updated = state.foodCart.toMutableMap()
      if (next == 0) updated.remove(dishId) else updated[dishId] = next
      state.copy(foodCart = updated)
    }
  }

  fun submitFoodOrder(
    address: String,
    voucherCode: String?,
    paymentMethod: String,
    onResult: (OrderCreationResult) -> Unit,
  ) {
    val currentState = uiState.value
    if (currentState.foodCart.isEmpty()) return

    val itemLines = mutableListOf<Pair<String, Int>>()
    var subtotalVnd = 0L
    currentState.foodCart.forEach { (dishId, qty) ->
      val dish = currentState.foodMenu.firstOrNull { it.id == dishId }
      if (dish != null && qty > 0) {
        itemLines.add(dish.dishName to qty)
        subtotalVnd += dish.priceVnd * qty
      }
    }

    viewModelScope.launch {
      localSession.update { it.copy(isSubmitting = true) }
      val result =
        repository.createFoodOrder(
          address = address,
          itemLines = itemLines,
          subtotalVnd = subtotalVnd,
          voucherCode = voucherCode,
          paymentMethod = paymentMethod,
        )
      localSession.update {
        it.copy(
          isSubmitting = false,
          foodCart = if (result.success) emptyMap() else it.foodCart,
          lastActionNotice = result.message,
        )
      }
      onResult(result)
    }
  }

  fun updateShopCart(productId: String, delta: Int) {
    val product = uiState.value.shopProducts.firstOrNull { it.id == productId }
    val maxStock = product?.stock ?: 1
    localSession.update { state ->
      val current = state.shopCart[productId] ?: 0
      val next = (current + delta).coerceIn(0, maxStock)
      val updated = state.shopCart.toMutableMap()
      if (next == 0) updated.remove(productId) else updated[productId] = next
      state.copy(shopCart = updated)
    }
  }

  fun submitShoppingOrder(
    address: String,
    voucherCode: String?,
    paymentMethod: String,
    onResult: (OrderCreationResult) -> Unit,
  ) {
    val currentState = uiState.value
    if (currentState.shopCart.isEmpty()) return

    val itemLines = mutableListOf<Pair<String, Int>>()
    var subtotalVnd = 0L
    currentState.shopCart.forEach { (productId, qty) ->
      val product = currentState.shopProducts.firstOrNull { it.id == productId }
      if (product != null && qty > 0) {
        itemLines.add(product.name to qty)
        subtotalVnd += product.priceVnd * qty
      }
    }

    viewModelScope.launch {
      localSession.update { it.copy(isSubmitting = true) }
      val result =
        repository.createShoppingOrder(
          address = address,
          itemLines = itemLines,
          subtotalVnd = subtotalVnd,
          voucherCode = voucherCode,
          paymentMethod = paymentMethod,
        )
      localSession.update {
        it.copy(
          isSubmitting = false,
          shopCart = if (result.success) emptyMap() else it.shopCart,
          lastActionNotice = result.message,
        )
      }
      onResult(result)
    }
  }

  fun cancelDemoOrder(orderCode: String) {
    viewModelScope.launch {
      val result = repository.cancelPendingDemoOrder(orderCode)
      localSession.update { it.copy(lastActionNotice = result.message) }
    }
  }

  class Factory(private val repository: FoxRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      if (modelClass.isAssignableFrom(FoxViewModel::class.java)) {
        return FoxViewModel(repository) as T
      }
      throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
  }
}

fun formatVnd(amount: Long): String {
  val formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"))
  return "${formatter.format(amount)}đ"
}

fun formatOrderTime(timestampMs: Long): String {
  val formatter = SimpleDateFormat("HH:mm • dd/MM", Locale.forLanguageTag("vi-VN"))
  return formatter.format(Date(timestampMs))
}

fun mapServiceType(name: String): FoxServiceType =
  FoxServiceType.entries.firstOrNull { it.name == name } ?: FoxServiceType.RIDE

fun orderStatusLabel(status: String): String =
  when (status) {
    "pending_demo" -> "Chờ xử lý (DEMO)"
    "cancelled_demo" -> "Đã hủy (DEMO)"
    "completed_demo" -> "Hoàn tất (DEMO)"
    "pending" -> "Chờ tiếp nhận"
    "merchant_confirmed" -> "Gian hàng đã nhận đơn"
    "preparing" -> "Đang chuẩn bị"
    "ready_for_pickup" -> "Sẵn sàng chờ tài xế"
    "driver_accepted" -> "Tài xế đã nhận đơn"
    "driver_arrived" -> "Tài xế đã đến điểm lấy/đón"
    "in_progress" -> "Đang thực hiện"
    "completed" -> "Hoàn thành"
    "cancelled" -> "Đã hủy"
    else -> status
  }

private fun defaultPromos(): List<DemoPromoBanner> =
  listOf(
    DemoPromoBanner(
      id = "promo_ride",
      tag = "FOX RIDE • DEMO",
      title = "Giảm 25% chuyến đi đầu tiên",
      subtitle = "Tối đa 30.000đ cho FOX Bike, Car 4 và Car 7 nội thành",
      targetService = FoxServiceType.RIDE,
      code = "FOXRIDE25",
    ),
    DemoPromoBanner(
      id = "promo_food",
      tag = "FOX FOOD • DEMO",
      title = "Bữa trưa ngon giảm đến 30%",
      subtitle = "Áp dụng cho đơn từ 80.000đ, giao nhanh nóng hổi",
      targetService = FoxServiceType.FOOD,
      code = "FOXFOOD30",
    ),
    DemoPromoBanner(
      id = "promo_delivery",
      tag = "FOX DELIVERY • DEMO",
      title = "Giao hàng siêu tốc giảm 10K",
      subtitle = "Tạo vận đơn dễ dàng cho tài liệu, quần áo, đồ điện tử",
      targetService = FoxServiceType.DELIVERY,
      code = "FOXSHIP15",
    ),
    DemoPromoBanner(
      id = "promo_shop",
      tag = "FOX SHOPPING • DEMO",
      title = "Mua sắm giảm ngay 20.000đ",
      subtitle = "Freeship cho đơn từ 300.000đ tại FOX Mall DEMO",
      targetService = FoxServiceType.SHOPPING,
      code = "FOXSHOP20",
    ),
  )

private fun defaultFoodMenu(): List<DemoMenuDish> =
  listOf(
    DemoMenuDish(
      id = "f1",
      restaurantName = "Phở Thìn Phố Cổ",
      dishName = "Phở Bò Tái Lăn Đặc Biệt",
      category = "Món nước",
      rating = "4.9",
      priceVnd = 65_000L,
      deliveryFeeVnd = 15_000L,
    ),
    DemoMenuDish(
      id = "f2",
      restaurantName = "Cơm Tấm Sài Gòn Xưa",
      dishName = "Cơm Tấm Sườn Bì Chả Trứng",
      category = "Cơm",
      rating = "4.8",
      priceVnd = 58_000L,
      deliveryFeeVnd = 15_000L,
    ),
    DemoMenuDish(
      id = "f3",
      restaurantName = "Trà Sữa FOX Tea",
      dishName = "Trà Đào Cam Sả Size L",
      category = "Đồ uống",
      rating = "4.9",
      priceVnd = 39_000L,
      deliveryFeeVnd = 15_000L,
    ),
    DemoMenuDish(
      id = "f4",
      restaurantName = "Bánh Mì Chảo Hội An",
      dishName = "Bánh Mì Thập Cẩm Pate Bơ",
      category = "Ăn nhanh",
      rating = "4.7",
      priceVnd = 35_000L,
      deliveryFeeVnd = 15_000L,
    ),
    DemoMenuDish(
      id = "f5",
      restaurantName = "Bún Đậu Mẹt Phố Huế",
      dishName = "Mẹt Bún Đậu Tá Lả Đầy Đủ",
      category = "Cơm",
      rating = "4.8",
      priceVnd = 72_000L,
      deliveryFeeVnd = 15_000L,
    ),
  )

private fun defaultShopProducts(): List<DemoShopProduct> =
  listOf(
    DemoShopProduct(
      id = "p1",
      storeName = "FOX Mart Tươi Sống",
      name = "Combo Trái Cây Nhập Khẩu 2kg",
      category = "Thực phẩm",
      priceVnd = 185_000L,
      stock = 24,
      rating = "4.9",
    ),
    DemoShopProduct(
      id = "p2",
      storeName = "HomeStyle VN",
      name = "Bình Giữ Nhiệt Inox 304 750ml",
      category = "Gia dụng",
      priceVnd = 229_000L,
      stock = 15,
      rating = "4.8",
    ),
    DemoShopProduct(
      id = "p3",
      storeName = "FOX Tech Official",
      name = "Tai Nghe Bluetooth Chống Ồn",
      category = "Điện tử",
      priceVnd = 350_000L,
      stock = 9,
      rating = "4.9",
    ),
    DemoShopProduct(
      id = "p4",
      storeName = "Urban Wear VN",
      name = "Áo Polo Cotton Thoáng Mát",
      category = "Thời trang",
      priceVnd = 245_000L,
      stock = 32,
      rating = "4.7",
    ),
    DemoShopProduct(
      id = "p5",
      storeName = "Beauty Care VN",
      name = "Kem Chống Nắng SPF50+ 50ml",
      category = "Làm đẹp",
      priceVnd = 165_000L,
      stock = 18,
      rating = "4.8",
    ),
  )
