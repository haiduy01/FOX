package com.example.model

data class User(
  val id: String,
  val name: String,
  val phone: String,
  val email: String,
  val role: String, // "customer", "driver", "merchant", "admin"
  val avatarUrl: String = "",
  val defaultAddress: String = "123 Nguyễn Huệ, Q.1, TP.HCM"
)

data class RideBooking(
  val id: String,
  val customerId: String,
  val customerName: String,
  val pickup: String,
  val destination: String,
  val vehicleType: String, // "FOX Bike", "FOX Car 4", "FOX Car 7"
  val fare: Double,
  val note: String,
  val status: String, // "pending", "accepted", "arriving", "arrived", "in_progress", "completed", "cancelled"
  val driverId: String? = null,
  val driverName: String? = null,
  val driverPhone: String? = null,
  val vehiclePlate: String? = null,
  val createdAt: Long = System.currentTimeMillis()
)

data class DeliveryOrder(
  val id: String,
  val customerId: String,
  val senderName: String,
  val senderPhone: String,
  val pickupAddress: String,
  val receiverName: String,
  val receiverPhone: String,
  val dropoffAddress: String,
  val itemType: String, // "Tài liệu", "Quần áo", "Đồ điện tử", "Khác"
  val weightKg: Double,
  val declaredValue: Double,
  val serviceType: String, // "Tiêu chuẩn", "Nhanh"
  val note: String,
  val fee: Double,
  val status: String, // "pending", "accepted", "picked_up", "delivering", "completed", "cancelled"
  val driverId: String? = null,
  val createdAt: Long = System.currentTimeMillis()
)

data class Restaurant(
  val id: String,
  val name: String,
  val category: String,
  val rating: Double,
  val address: String,
  val isOpen: Boolean,
  val deliveryFee: Double,
  val imageUrl: String
)

data class FoodItem(
  val id: String,
  val restaurantId: String,
  val name: String,
  val price: Double,
  val description: String,
  val imageUrl: String
)

data class FoodOrder(
  val id: String,
  val customerId: String,
  val restaurantId: String,
  val restaurantName: String,
  val items: List<CartFoodItem>,
  val totalAmount: Double,
  val address: String,
  val status: String, // "pending", "confirmed", "preparing", "delivering", "completed", "cancelled"
  val createdAt: Long = System.currentTimeMillis()
)

data class CartFoodItem(
  val foodId: String,
  val name: String,
  val price: Double,
  val quantity: Int
)

data class Product(
  val id: String,
  val merchantId: String,
  val merchantName: String,
  val name: String,
  val category: String,
  val price: Double,
  val stock: Int,
  val description: String,
  val imageUrl: String
)

data class ShoppingOrder(
  val id: String,
  val customerId: String,
  val items: List<CartProductItem>,
  val totalAmount: Double,
  val address: String,
  val status: String, // "pending", "processing", "shipping", "completed", "cancelled"
  val createdAt: Long = System.currentTimeMillis()
)

data class CartProductItem(
  val productId: String,
  val name: String,
  val price: Double,
  val quantity: Int
)

data class WalletTransaction(
  val id: String,
  val userId: String,
  val title: String,
  val amount: Double,
  val type: String, // "deposit", "payment", "refund"
  val timestamp: Long = System.currentTimeMillis()
)

data class Voucher(
  val id: String,
  val code: String,
  val title: String,
  val discountPercent: Int,
  val maxDiscount: Double,
  val minOrder: Double,
  val serviceScope: String // "all", "ride", "delivery", "food", "shopping"
)

data class NotificationItem(
  val id: String,
  val userId: String,
  val title: String,
  val message: String,
  val timestamp: Long = System.currentTimeMillis(),
  var isRead: Boolean = false
)

data class SupportTicket(
  val id: String,
  val userId: String,
  val orderId: String,
  val title: String,
  val description: String,
  val status: String, // "open", "in_progress", "resolved"
  val response: String = ""
)
