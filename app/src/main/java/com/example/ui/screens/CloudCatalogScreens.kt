package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.remote.CloudMerchantProduct
import com.example.data.remote.CloudMerchantStore
import com.example.data.remote.CloudMerchantWithProducts
import com.example.data.remote.DEMO_CURRENCY_NOTICE
import com.example.data.remote.OrderItemParam
import com.example.data.repository.FoxDemoPricingEngine
import com.example.ui.FoxCloudUiState
import com.example.ui.FoxServiceType
import com.example.ui.components.FoxServiceIllustration
import com.example.ui.formatVnd
import com.example.ui.theme.FoxBlue
import com.example.ui.theme.FoxGreen
import com.example.ui.theme.FoxOrange
import com.example.ui.theme.FoxOrangeDeep
import com.example.ui.theme.FoxRed
import com.example.ui.theme.FoxTextSecondary
import com.example.ui.theme.FoxTextStrong
import java.util.Locale

@Composable
fun CloudFoodScreen(
  cloudState: FoxCloudUiState,
  onOrderCreated: () -> Unit,
  onSubmitFood: (
    merchantId: String,
    merchantName: String,
    merchantAddress: String,
    address: String,
    itemLines: List<Pair<String, Int>>,
    itemParams: List<OrderItemParam>,
    subtotalVnd: Long,
    voucherCode: String?,
    paymentMethod: String,
    onSuccess: () -> Unit,
  ) -> Unit,
  onBack: () -> Unit,
) {
  CloudCatalogOrderScreen(
    title = "Đặt đồ ăn (Cloud)",
    serviceType = FoxServiceType.FOOD,
    voucherCode = "FOXFOOD30",
    voucherLabel = "Mã FOXFOOD30 (-30.000đ đơn từ 90k)",
    catalog = cloudState.foodCatalog,
    cloudState = cloudState,
    onOrderCreated = onOrderCreated,
    onSubmitOrder = onSubmitFood,
    onBack = onBack,
  )
}

@Composable
fun CloudShoppingScreen(
  cloudState: FoxCloudUiState,
  onOrderCreated: () -> Unit,
  onSubmitShopping: (
    merchantId: String,
    merchantName: String,
    merchantAddress: String,
    address: String,
    itemLines: List<Pair<String, Int>>,
    itemParams: List<OrderItemParam>,
    subtotalVnd: Long,
    voucherCode: String?,
    paymentMethod: String,
    onSuccess: () -> Unit,
  ) -> Unit,
  onBack: () -> Unit,
) {
  CloudCatalogOrderScreen(
    title = "Mua sắm (Cloud)",
    serviceType = FoxServiceType.SHOPPING,
    voucherCode = "FOXSHOP20",
    voucherLabel = "Mã FOXSHOP20 (-20.000đ đơn từ 150k)",
    catalog = cloudState.shoppingCatalog,
    cloudState = cloudState,
    onOrderCreated = onOrderCreated,
    onSubmitOrder = onSubmitShopping,
    onBack = onBack,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloudCatalogOrderScreen(
  title: String,
  serviceType: FoxServiceType,
  voucherCode: String,
  voucherLabel: String,
  catalog: List<CloudMerchantWithProducts>,
  cloudState: FoxCloudUiState,
  onOrderCreated: () -> Unit,
  onSubmitOrder: (
    merchantId: String,
    merchantName: String,
    merchantAddress: String,
    address: String,
    itemLines: List<Pair<String, Int>>,
    itemParams: List<OrderItemParam>,
    subtotalVnd: Long,
    voucherCode: String?,
    paymentMethod: String,
    onSuccess: () -> Unit,
  ) -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)

  var searchQuery by remember { mutableStateOf("") }
  var openOnly by remember { mutableStateOf(false) }
  var selectedCategory by remember { mutableStateOf<String?>(null) }
  var selectedMerchantId by remember { mutableStateOf<String?>(null) }
  var cart by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
  var cartNotice by remember { mutableStateOf<String?>(null) }
  var detailProduct by remember { mutableStateOf<CloudMerchantProduct?>(null) }
  var deliveryAddress by remember(cloudState.profile?.defaultAddress) {
    mutableStateOf(
      cloudState.profile?.defaultAddress?.takeIf { it.isNotBlank() }
        ?: "123 Nguyễn Huệ, Quận 1, TP.HCM"
    )
  }
  var useVoucher by remember { mutableStateOf(false) }
  var paymentMethod by remember { mutableStateOf("Ví FOX DEMO") }

  val categories = remember(catalog) {
    buildSet {
      catalog.forEach { entry ->
        if (entry.store.category.isNotBlank()) add(entry.store.category)
        entry.products.forEach { p -> if (p.category.isNotBlank()) add(p.category) }
      }
    }.toList().sorted()
  }

  val normalizedQuery = remember(searchQuery) { searchQuery.trim().lowercase(Locale.getDefault()) }

  val filteredStores = remember(catalog, normalizedQuery, openOnly, selectedCategory) {
    catalog.mapNotNull { entry ->
      if (openOnly && !entry.store.openNow) return@mapNotNull null
      val storeMatchesQuery = normalizedQuery.isBlank() ||
        entry.store.name.lowercase(Locale.getDefault()).contains(normalizedQuery) ||
        entry.store.description.lowercase(Locale.getDefault()).contains(normalizedQuery) ||
        entry.store.category.lowercase(Locale.getDefault()).contains(normalizedQuery)

      val matchingProducts = entry.products.filter { prod ->
        val catOk = selectedCategory == null ||
          prod.category.equals(selectedCategory, ignoreCase = true) ||
          entry.store.category.equals(selectedCategory, ignoreCase = true)
        val qOk = storeMatchesQuery ||
          prod.name.lowercase(Locale.getDefault()).contains(normalizedQuery) ||
          prod.description.lowercase(Locale.getDefault()).contains(normalizedQuery) ||
          prod.category.lowercase(Locale.getDefault()).contains(normalizedQuery)
        catOk && qOk
      }

      if (matchingProducts.isEmpty() && (normalizedQuery.isNotBlank() || selectedCategory != null)) {
        null
      } else {
        entry.copy(products = matchingProducts)
      }
    }
  }

  LaunchedEffect(filteredStores) {
    val stillExists = filteredStores.any { it.store.effectiveMerchantId == selectedMerchantId }
    if (!stillExists) {
      val nextId = filteredStores.firstOrNull()?.store?.effectiveMerchantId
      if (selectedMerchantId != null && selectedMerchantId != nextId && cart.isNotEmpty()) {
        cart = emptyMap()
      }
      selectedMerchantId = nextId
    }
  }

  val selectedEntry = remember(filteredStores, selectedMerchantId) {
    filteredStores.firstOrNull { it.store.effectiveMerchantId == selectedMerchantId }
      ?: filteredStores.firstOrNull()
  }

  // Clamp cart quantities whenever live Firestore stock updates!
  LaunchedEffect(selectedEntry?.products) {
    val productsById = selectedEntry?.products?.associateBy { it.id }.orEmpty()
    if (cart.isNotEmpty() && productsById.isNotEmpty()) {
      val clamped = buildMap {
        cart.forEach { (prodId, qty) ->
          val liveStock = productsById[prodId]?.stock?.toInt() ?: 0
          val bounded = qty.coerceAtMost(liveStock)
          if (bounded > 0) put(prodId, bounded)
        }
      }
      if (clamped != cart) cart = clamped
    }
  }

  fun updateQuantity(product: CloudMerchantProduct, delta: Int) {
    val maxStock = product.stock.toInt().coerceAtLeast(0)
    val current = cart[product.id] ?: 0
    val next = (current + delta).coerceIn(0, maxStock)
    val updated = cart.toMutableMap()
    if (next == 0) updated.remove(product.id) else updated[product.id] = next
    cart = updated
    cartNotice = null
  }

  val selectedProductsMap = remember(selectedEntry) {
    selectedEntry?.products?.associateBy { it.id }.orEmpty()
  }

  val cartItemLines = remember(cart, selectedProductsMap) {
    cart.entries.mapNotNull { (prodId, qty) ->
      val prod = selectedProductsMap[prodId] ?: return@mapNotNull null
      if (qty > 0) Triple(prod, qty, prod.priceVnd * qty) else null
    }
  }

  val subtotalVnd = remember(cartItemLines) { cartItemLines.sumOf { it.third } }
  val totalItemsCount = remember(cartItemLines) { cartItemLines.sumOf { it.second } }
  val activeVoucher = if (useVoucher) voucherCode else null
  val quote = remember(subtotalVnd, activeVoucher, serviceType) {
    if (serviceType == FoxServiceType.FOOD) {
      FoxDemoPricingEngine.estimateFood(subtotalVnd, activeVoucher)
    } else {
      FoxDemoPricingEngine.estimateShopping(subtotalVnd, activeVoucher)
    }
  }

  val tagPrefix = if (serviceType == FoxServiceType.FOOD) "cloud_food" else "cloud_shop"

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            FoxServiceIllustration(serviceType = serviceType, size = 32.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = title,
              style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
              color = FoxTextStrong,
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("${tagPrefix}_back")) {
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
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // Cloud Live Status Banner
        item {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = FoxBlue.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, FoxBlue.copy(alpha = 0.22f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = FoxBlue)
              Spacer(modifier = Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Danh mục trực tiếp từ Cloud Firestore",
                  style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                  color = FoxBlue,
                )
                Text(
                  text = "Giá & tồn kho cập nhật thời gian thực, xác thực nguyên tử qua Cloud Functions v2.",
                  style = MaterialTheme.typography.labelSmall,
                  color = FoxTextSecondary,
                )
              }
            }
          }
        }

        // Search + Filter Chips
        item {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = searchQuery,
              onValueChange = { searchQuery = it },
              placeholder = {
                Text(
                  if (serviceType == FoxServiceType.FOOD) {
                    "Tìm quán ăn, món phở, cơm tấm, đồ uống..."
                  } else {
                    "Tìm gian hàng, trái cây, phụ kiện, gia dụng..."
                  }
                )
              },
              leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = FoxBlue) },
              singleLine = true,
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier.fillMaxWidth().testTag("${tagPrefix}_search_input"),
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              item {
                FilterChip(
                  selected = openOnly,
                  onClick = { openOnly = !openOnly },
                  modifier = Modifier.testTag("${tagPrefix}_filter_open"),
                  label = { Text("Chỉ đang mở") },
                )
              }
              item {
                FilterChip(
                  selected = selectedCategory == null,
                  onClick = { selectedCategory = null },
                  modifier = Modifier.testTag("${tagPrefix}_cat_all"),
                  label = { Text("Tất cả") },
                )
              }
              items(categories) { category ->
                FilterChip(
                  selected = selectedCategory == category,
                  onClick = { selectedCategory = if (selectedCategory == category) null else category },
                  label = { Text(category) },
                )
              }
            }
          }
        }

        // Loading / Empty states
        if (cloudState.isLoadingCatalog && catalog.isEmpty()) {
          item {
            Card(
              shape = RoundedCornerShape(20.dp),
              colors = CardDefaults.cardColors(containerColor = Color.White),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
              ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = FoxBlue, strokeWidth = 2.5.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Đang tải danh mục gian hàng từ Cloud Firestore...", color = FoxTextSecondary)
              }
            }
          }
        } else if (filteredStores.isEmpty()) {
          item {
            Card(
              shape = RoundedCornerShape(20.dp),
              colors = CardDefaults.cardColors(containerColor = Color.White),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  text = if (catalog.isEmpty()) {
                    "Chưa có gian hàng nào trên Cloud Firestore"
                  } else {
                    "Không tìm thấy gian hàng hoặc sản phẩm phù hợp"
                  },
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = FoxTextStrong,
                )
                Text(
                  text = if (catalog.isEmpty()) {
                    "Màn hình Cloud không dùng danh sách tĩnh. Hãy tạo gian hàng & sản phẩm trong Bảng điều khiển Gian hàng hoặc chạy script seed DEMO (scripts/seed-demo-catalog.js) trên môi trường mong muốn."
                  } else {
                    "Thử xóa từ khóa tìm kiếm hoặc tắt bộ lọc danh mục / trạng thái mở cửa."
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = FoxTextSecondary,
                )
              }
            }
          }
        } else {
          // Store selector section
          item {
            Text(
              text = if (serviceType == FoxServiceType.FOOD) {
                "Nhà hàng & Quán ăn (${filteredStores.size})"
              } else {
                "Gian hàng FOX Cloud (${filteredStores.size})"
              },
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
              color = FoxTextStrong,
            )
          }

          item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              items(filteredStores, key = { it.store.effectiveMerchantId }) { entry ->
                val isSelected = entry.store.effectiveMerchantId == selectedEntry?.store?.effectiveMerchantId
                CloudStoreSelectorCard(
                  store = entry.store,
                  productCount = entry.products.size,
                  serviceType = serviceType,
                  selected = isSelected,
                  onClick = {
                    val newId = entry.store.effectiveMerchantId
                    if (newId != selectedMerchantId) {
                      if (cart.isNotEmpty()) {
                        cart = emptyMap()
                        cartNotice = "Đã đổi sang \"${entry.store.name}\" — giỏ hàng cũ đã được làm mới."
                      }
                      selectedMerchantId = newId
                    }
                  },
                )
              }
            }
          }

          if (cartNotice != null) {
            item {
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = FoxOrange.copy(alpha = 0.14f),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = cartNotice ?: "",
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                  color = FoxOrangeDeep,
                  modifier = Modifier.padding(12.dp),
                )
              }
            }
          }

          // Selected store details + products
          if (selectedEntry != null) {
            val activeStore = selectedEntry.store
            item {
              CloudSelectedStoreHeader(store = activeStore, serviceType = serviceType)
            }

            if (selectedEntry.products.isEmpty()) {
              item {
                Card(
                  shape = RoundedCornerShape(18.dp),
                  colors = CardDefaults.cardColors(containerColor = Color.White),
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                  Text(
                    text = "Gian hàng \"${activeStore.name}\" hiện chưa có sản phẩm nào đang hiển thị.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FoxTextSecondary,
                    modifier = Modifier.padding(16.dp),
                  )
                }
              }
            } else {
              items(selectedEntry.products, key = { "${activeStore.effectiveMerchantId}_${it.id}" }) { product ->
                val qty = cart[product.id] ?: 0
                CloudProductCard(
                  product = product,
                  storeOpen = activeStore.openNow,
                  quantityInCart = qty,
                  serviceType = serviceType,
                  onOpenDetail = { detailProduct = product },
                  onIncrement = { updateQuantity(product, +1) },
                  onDecrement = { updateQuantity(product, -1) },
                )
              }
            }

            // Checkout & Cart Card
            item {
              Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, FoxBlue.copy(alpha = 0.25f)),
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(16.dp),
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(Icons.Rounded.ShoppingCart, contentDescription = null, tint = FoxBlue)
                      Spacer(modifier = Modifier.width(8.dp))
                      Text(
                        text = "Giỏ hàng • $totalItemsCount sản phẩm",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = FoxTextStrong,
                      )
                    }
                    Surface(shape = RoundedCornerShape(50), color = FoxOrange.copy(alpha = 0.14f)) {
                      Text(
                        text = DEMO_CURRENCY_NOTICE,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = FoxOrangeDeep,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                      )
                    }
                  }

                  if (cartItemLines.isEmpty()) {
                    Text(
                      text = "Chọn món/sản phẩm ở trên để thêm vào giỏ hàng của ${activeStore.name}.",
                      style = MaterialTheme.typography.bodySmall,
                      color = FoxTextSecondary,
                    )
                  } else {
                    cartItemLines.forEach { (prod, qty, lineTotal) ->
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                      ) {
                        Text(
                          text = "${prod.name} x$qty",
                          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                          color = FoxTextStrong,
                          modifier = Modifier.weight(1f),
                        )
                        Text(
                          text = formatVnd(lineTotal),
                          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                          color = FoxBlue,
                        )
                      }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    OutlinedTextField(
                      value = deliveryAddress,
                      onValueChange = { deliveryAddress = it },
                      label = { Text("Địa chỉ giao hàng") },
                      leadingIcon = { Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = FoxOrangeDeep) },
                      singleLine = true,
                      modifier = Modifier.fillMaxWidth().testTag("${tagPrefix}_address_input"),
                    )

                    FilterChip(
                      selected = useVoucher,
                      onClick = { useVoucher = !useVoucher },
                      leadingIcon = {
                        Icon(Icons.Rounded.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(18.dp))
                      },
                      label = { Text(voucherLabel) },
                      modifier = Modifier.testTag("${tagPrefix}_voucher_chip"),
                    )

                    Text(
                      text = "Phương thức thanh toán DEMO",
                      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                      color = FoxTextStrong,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                      FilterChip(
                        selected = paymentMethod == "Ví FOX DEMO",
                        onClick = { paymentMethod = "Ví FOX DEMO" },
                        modifier = Modifier.testTag("${tagPrefix}_pay_wallet"),
                        label = {
                          Text("Ví FOX DEMO (${formatVnd(cloudState.cloudWallet.balanceVnd)})")
                        },
                      )
                      FilterChip(
                        selected = paymentMethod == "Tiền mặt DEMO",
                        onClick = { paymentMethod = "Tiền mặt DEMO" },
                        modifier = Modifier.testTag("${tagPrefix}_pay_cash"),
                        label = { Text("Tiền mặt DEMO") },
                      )
                    }

                    Surface(
                      shape = RoundedCornerShape(14.dp),
                      color = Color(0xFFF6F8FD),
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                      ) {
                        QuoteRow("Tạm tính", formatVnd(quote.subtotalVnd))
                        QuoteRow("Phí giao hàng", formatVnd(quote.feeVnd))
                        if (quote.discountVnd > 0L) {
                          QuoteRow("Giảm giá voucher", "-${formatVnd(quote.discountVnd)}", FoxGreen)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                          modifier = Modifier.fillMaxWidth(),
                          horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                          Text(
                            text = "Tổng thanh toán (Server xác thực)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = FoxTextStrong,
                          )
                          Text(
                            text = formatVnd(quote.totalVnd),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                            color = FoxBlue,
                          )
                        }
                      }
                    }

                    Button(
                      onClick = {
                        val lines = cartItemLines.map { it.first.name to it.second }
                        val params = cartItemLines.map { OrderItemParam(it.first.id, it.second) }
                        onSubmitOrder(
                          activeStore.effectiveMerchantId,
                          activeStore.name,
                          activeStore.address,
                          deliveryAddress,
                          lines,
                          params,
                          subtotalVnd,
                          activeVoucher,
                          paymentMethod,
                        ) {
                          cart = emptyMap()
                          onOrderCreated()
                        }
                      },
                      enabled = !cloudState.isBusy && activeStore.openNow && deliveryAddress.isNotBlank() && cartItemLines.isNotEmpty(),
                      modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("${tagPrefix}_submit_button"),
                      shape = RoundedCornerShape(16.dp),
                      colors = ButtonDefaults.buttonColors(containerColor = FoxBlue),
                    ) {
                      Text(
                        text = if (!activeStore.openNow) {
                          "Gian hàng đang đóng cửa"
                        } else if (cloudState.isBusy) {
                          "Đang xác thực tồn kho & tạo đơn..."
                        } else {
                          "Xác nhận đặt đơn • ${formatVnd(quote.totalVnd)}"
                        },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  // Product Detail Modal Dialog
  detailProduct?.let { product ->
    val store = selectedEntry?.store
    val liveProduct = selectedProductsMap[product.id] ?: product
    val qtyInCart = cart[liveProduct.id] ?: 0
    val canAdd = (store?.openNow == true) && qtyInCart < liveProduct.stock.toInt()
    AlertDialog(
      onDismissRequest = { detailProduct = null },
      title = {
        Text(
          text = liveProduct.name,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
          color = FoxTextStrong,
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          ProductImageThumb(
            imageUrl = liveProduct.imageUrl,
            serviceType = serviceType,
            contentDescription = liveProduct.name,
            modifier = Modifier
              .fillMaxWidth()
              .height(150.dp)
              .clip(RoundedCornerShape(14.dp)),
          )
          Text(
            text = liveProduct.description.ifBlank { "Sản phẩm chính hãng từ ${liveProduct.storeName}." },
            style = MaterialTheme.typography.bodyMedium,
            color = FoxTextStrong,
          )
          Text(
            text = "Gian hàng: ${store?.name ?: liveProduct.storeName} • Giờ mở: ${store?.openingHours ?: "07:00 - 22:00"}",
            style = MaterialTheme.typography.labelMedium,
            color = FoxTextSecondary,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = formatVnd(liveProduct.priceVnd),
              style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
              color = FoxBlue,
            )
            StockBadge(stock = liveProduct.stock)
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            IconButton(
              onClick = { updateQuantity(liveProduct, -1) },
              enabled = qtyInCart > 0,
              modifier = Modifier.testTag("detail_dec_${liveProduct.id}"),
            ) {
              Icon(Icons.Rounded.Remove, contentDescription = "Giảm số lượng", tint = FoxBlue)
            }
            Text(
              text = "$qtyInCart",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
              modifier = Modifier.padding(horizontal = 16.dp),
            )
            IconButton(
              onClick = { updateQuantity(liveProduct, +1) },
              enabled = canAdd,
              modifier = Modifier.testTag("detail_inc_${liveProduct.id}"),
            ) {
              Icon(Icons.Rounded.Add, contentDescription = "Tăng số lượng", tint = FoxBlue)
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { detailProduct = null }) {
          Text("Đóng", fontWeight = FontWeight.Bold, color = FoxBlue)
        }
      },
    )
  }
}

@Composable
private fun CloudStoreSelectorCard(
  store: CloudMerchantStore,
  productCount: Int,
  serviceType: FoxServiceType,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Card(
    modifier = Modifier
      .width(235.dp)
      .clickable(onClick = onClick)
      .testTag("store_card_${store.effectiveMerchantId}"),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (selected) FoxBlue.copy(alpha = 0.08f) else Color.White,
    ),
    border = BorderStroke(
      width = if (selected) 2.dp else 1.dp,
      color = if (selected) FoxBlue else MaterialTheme.colorScheme.outline,
    ),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        ProductImageThumb(
          imageUrl = store.imageUrl,
          serviceType = serviceType,
          contentDescription = store.name,
          modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = store.name,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = FoxTextStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Star, contentDescription = null, tint = FoxOrange, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = "${store.formattedRating} • $productCount món",
              style = MaterialTheme.typography.labelSmall,
              color = FoxTextSecondary,
            )
          }
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        StoreOpenBadge(openNow = store.openNow)
        Text(
          text = store.openingHours,
          style = MaterialTheme.typography.labelSmall,
          color = FoxTextSecondary,
        )
      }
    }
  }
}

@Composable
private fun CloudSelectedStoreHeader(
  store: CloudMerchantStore,
  serviceType: FoxServiceType,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Icon(
            imageVector = Icons.Rounded.Storefront,
            contentDescription = null,
            tint = if (serviceType == FoxServiceType.FOOD) FoxOrangeDeep else FoxBlue,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = store.name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = FoxTextStrong,
          )
        }
        StoreOpenBadge(openNow = store.openNow)
      }
      if (store.description.isNotBlank()) {
        Text(store.description, style = MaterialTheme.typography.bodySmall, color = FoxTextStrong)
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = FoxTextSecondary, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(store.address, style = MaterialTheme.typography.labelSmall, color = FoxTextSecondary)
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.AccessTime, contentDescription = null, tint = FoxTextSecondary, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = "Giờ hoạt động: ${store.openingHours} • Đánh giá ${store.formattedRating}★",
          style = MaterialTheme.typography.labelSmall,
          color = FoxTextSecondary,
        )
      }
    }
  }
}

@Composable
private fun CloudProductCard(
  product: CloudMerchantProduct,
  storeOpen: Boolean,
  quantityInCart: Int,
  serviceType: FoxServiceType,
  onOpenDetail: () -> Unit,
  onIncrement: () -> Unit,
  onDecrement: () -> Unit,
) {
  val outOfStock = product.stock <= 0L
  val canIncrement = storeOpen && !outOfStock && quantityInCart < product.stock.toInt()
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onOpenDetail)
      .testTag("cloud_product_${product.id}"),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(
      width = if (quantityInCart > 0) 1.5.dp else 1.dp,
      color = if (quantityInCart > 0) FoxBlue else MaterialTheme.colorScheme.outline,
    ),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ProductImageThumb(
        imageUrl = product.imageUrl,
        serviceType = serviceType,
        contentDescription = product.name,
        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)),
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          text = product.name,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = FoxTextStrong,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        if (product.description.isNotBlank()) {
          Text(
            text = product.description,
            style = MaterialTheme.typography.labelSmall,
            color = FoxTextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = formatVnd(product.priceVnd),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = FoxBlue,
          )
          StockBadge(stock = product.stock)
        }
      }
      Spacer(modifier = Modifier.width(6.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (quantityInCart > 0) {
          IconButton(
            onClick = onDecrement,
            modifier = Modifier.size(48.dp).testTag("cart_dec_${product.id}"),
          ) {
            Surface(shape = CircleShape, color = FoxBlue.copy(alpha = 0.12f), modifier = Modifier.size(32.dp)) {
              Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Remove, contentDescription = "Giảm ${product.name}", tint = FoxBlue, modifier = Modifier.size(18.dp))
              }
            }
          }
          Text(
            text = "$quantityInCart",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = FoxTextStrong,
          )
        }
        IconButton(
          onClick = onIncrement,
          enabled = canIncrement,
          modifier = Modifier.size(48.dp).testTag("cart_inc_${product.id}"),
        ) {
          Surface(
            shape = CircleShape,
            color = if (canIncrement) FoxBlue else Color(0xFFE0E5EE),
            modifier = Modifier.size(32.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                Icons.Rounded.Add,
                contentDescription = "Thêm ${product.name}",
                tint = if (canIncrement) Color.White else FoxTextSecondary,
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ProductImageThumb(
  imageUrl: String,
  serviceType: FoxServiceType,
  contentDescription: String,
  modifier: Modifier = Modifier,
) {
  if (imageUrl.isNotBlank() && (imageUrl.startsWith("https://") || imageUrl.startsWith("http://"))) {
    AsyncImage(
      model = imageUrl,
      contentDescription = contentDescription,
      contentScale = ContentScale.Crop,
      modifier = modifier.background(Color(0xFFF3F6FC)),
    )
  } else {
    Box(
      modifier = modifier.background(
        if (serviceType == FoxServiceType.FOOD) FoxOrange.copy(alpha = 0.12f) else FoxBlue.copy(alpha = 0.10f)
      ),
      contentAlignment = Alignment.Center,
    ) {
      FoxServiceIllustration(serviceType = serviceType, size = 40.dp)
    }
  }
}

@Composable
private fun StoreOpenBadge(openNow: Boolean) {
  Surface(
    shape = RoundedCornerShape(50),
    color = if (openNow) FoxGreen.copy(alpha = 0.14f) else FoxRed.copy(alpha = 0.12f),
  ) {
    Text(
      text = if (openNow) "Đang mở" else "Đóng cửa",
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
      color = if (openNow) FoxGreen else FoxRed,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
    )
  }
}

@Composable
private fun StockBadge(stock: Long) {
  Surface(
    shape = RoundedCornerShape(50),
    color = if (stock > 0L) Color(0xFFEDF4FF) else FoxRed.copy(alpha = 0.12f),
  ) {
    Text(
      text = if (stock > 0L) "Còn $stock" else "Hết hàng",
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
      color = if (stock > 0L) FoxBlue else FoxRed,
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
    )
  }
}

@Composable
private fun QuoteRow(label: String, value: String, valueColor: Color = FoxTextStrong) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = FoxTextSecondary)
    Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = valueColor)
  }
}
