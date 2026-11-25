package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Durable local DEMO order record stored in Room SQLite.
 * All monetary fields use Long (exact VND integer units, never Double).
 */
@Entity(tableName = "fox_orders")
data class FoxOrderEntity(
  @PrimaryKey val orderCode: String,
  val serviceType: String, // "RIDE", "DELIVERY", "FOOD", "SHOPPING"
  val title: String,
  val primaryAddress: String,
  val secondaryAddress: String,
  val detailSummary: String,
  val subtotalVnd: Long,
  val feeVnd: Long,
  val discountVnd: Long,
  val totalVnd: Long,
  val paymentMethod: String, // "Ví FOX DEMO", "Tiền mặt DEMO"
  val voucherCode: String?,
  val status: String, // "pending_demo", "cancelled_demo", "completed_demo"
  val idempotencyKey: String,
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "fox_notifications")
data class FoxNotificationEntity(
  @PrimaryKey val id: String,
  val title: String,
  val body: String,
  val relatedOrderCode: String? = null,
  val isRead: Boolean = false,
  val createdAt: Long = System.currentTimeMillis(),
)

/**
 * DEMO wallet ledger entry stored locally in Room.
 * Strictly labeled DEMO – not real money. Production balances must only be mutated by backend.
 */
@Entity(tableName = "fox_wallet_transactions")
data class FoxWalletTxEntity(
  @PrimaryKey val txId: String,
  val title: String,
  val amountVnd: Long, // positive for credit/refund, negative for payment
  val txType: String, // "SEED_DEMO", "PAYMENT_DEMO", "REFUND_DEMO"
  val relatedOrderCode: String? = null,
  val idempotencyKey: String,
  val createdAt: Long = System.currentTimeMillis(),
)
