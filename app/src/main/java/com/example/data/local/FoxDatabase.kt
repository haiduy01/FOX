package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface FoxOrderDao {
  @Query("SELECT * FROM fox_orders ORDER BY createdAt DESC")
  fun observeAllOrders(): Flow<List<FoxOrderEntity>>

  @Query("SELECT COUNT(*) FROM fox_orders")
  suspend fun countOrders(): Int

  @Query("SELECT * FROM fox_orders WHERE idempotencyKey = :key LIMIT 1")
  suspend fun findByIdempotencyKey(key: String): FoxOrderEntity?

  @Query("SELECT * FROM fox_orders WHERE orderCode = :code LIMIT 1")
  suspend fun findByCode(code: String): FoxOrderEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertOrder(order: FoxOrderEntity)

  @Query("UPDATE fox_orders SET status = :newStatus WHERE orderCode = :code")
  suspend fun updateOrderStatus(code: String, newStatus: String)
}

@Dao
interface FoxNotificationDao {
  @Query("SELECT * FROM fox_notifications ORDER BY createdAt DESC")
  fun observeNotifications(): Flow<List<FoxNotificationEntity>>

  @Query("SELECT COUNT(*) FROM fox_notifications")
  suspend fun countNotifications(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertNotification(item: FoxNotificationEntity)

  @Query("UPDATE fox_notifications SET isRead = 1 WHERE id = :id")
  suspend fun markRead(id: String)

  @Query("UPDATE fox_notifications SET isRead = 1")
  suspend fun markAllRead()
}

@Dao
interface FoxWalletDao {
  @Query("SELECT * FROM fox_wallet_transactions ORDER BY createdAt DESC")
  fun observeTransactions(): Flow<List<FoxWalletTxEntity>>

  @Query("SELECT COALESCE(SUM(amountVnd), 0) FROM fox_wallet_transactions")
  fun observeBalanceVnd(): Flow<Long>

  @Query("SELECT COALESCE(SUM(amountVnd), 0) FROM fox_wallet_transactions")
  suspend fun getCurrentBalanceVnd(): Long

  @Query("SELECT COUNT(*) FROM fox_wallet_transactions")
  suspend fun countTransactions(): Int

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertTransaction(tx: FoxWalletTxEntity)
}

@Database(
  entities = [
    FoxOrderEntity::class,
    FoxNotificationEntity::class,
    FoxWalletTxEntity::class,
  ],
  version = 1,
  exportSchema = false,
)
abstract class FoxDatabase : RoomDatabase() {
  abstract fun orderDao(): FoxOrderDao
  abstract fun notificationDao(): FoxNotificationDao
  abstract fun walletDao(): FoxWalletDao

  companion object {
    @Volatile
    private var INSTANCE: FoxDatabase? = null

    fun getInstance(context: Context): FoxDatabase =
      INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          FoxDatabase::class.java,
          "fox_demo_pilot.db",
        ).build().also { INSTANCE = it }
      }
  }
}
