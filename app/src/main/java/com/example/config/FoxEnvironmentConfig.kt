package com.example.config

import android.content.Context
import com.example.BuildConfig
import com.example.R
import com.google.firebase.FirebaseApp

enum class FoxEnvironment {
  DEMO,
  STAGING,
  PRODUCTION,
}

data class FoxEnvironmentDescriptor(
  val environment: FoxEnvironment,
  val labelVi: String,
  val shortBadge: String,
  val isDemoFinanceOnly: Boolean,
  val allowsLiveProductionWrites: Boolean,
)

/**
 * Centralized environment abstraction for DEMO / STAGING / PRODUCTION separation.
 * Prevents accidental writes to the live production Firebase project or database from staging/demo setups.
 * Staging project ID is NEVER hardcoded here — it is supplied at staging build time via Gradle properties
 * (`-PfoxEnv=STAGING -PfoxStagingProjectId=<id> -PfoxStagingDbId=fox-staging-db`) and staging google-services.json.
 */
object FoxEnvironmentConfig {
  const val PRODUCTION_FIREBASE_PROJECT_ID = "gen-lang-client-0957111252"
  const val PRODUCTION_FIRESTORE_DB_ID = "ai-studio-android-1a796de6-79f0-4283-8d0b-703e709b27e4"
  const val DEFAULT_STAGING_FIRESTORE_DB_ID = "fox-staging-db"

  fun resolve(rawEnv: String?): FoxEnvironmentDescriptor =
    when (rawEnv?.trim()?.uppercase()) {
      "STAGING" -> FoxEnvironmentDescriptor(
        environment = FoxEnvironment.STAGING,
        labelVi = "STAGING (Kiểm thử độc lập — Tiền DEMO)",
        shortBadge = "STAGING",
        isDemoFinanceOnly = true,
        allowsLiveProductionWrites = false,
      )
      "PRODUCTION" -> FoxEnvironmentDescriptor(
        environment = FoxEnvironment.PRODUCTION,
        labelVi = "PRODUCTION (Chưa kích hoạt cho Pilot)",
        shortBadge = "PROD",
        isDemoFinanceOnly = false,
        allowsLiveProductionWrites = true,
      )
      else -> FoxEnvironmentDescriptor(
        environment = FoxEnvironment.DEMO,
        labelVi = "DEMO / PILOT (Nội bộ — Không phải tiền thật)",
        shortBadge = "DEMO",
        isDemoFinanceOnly = true,
        allowsLiveProductionWrites = false,
      )
    }

  fun current(): FoxEnvironmentDescriptor = resolve(BuildConfig.FOX_ENV)

  fun configuredStagingProjectId(): String = BuildConfig.FOX_STAGING_PROJECT_ID.trim()

  fun configuredStagingDatabaseId(): String =
    BuildConfig.FOX_STAGING_DATABASE_ID.trim().ifEmpty { DEFAULT_STAGING_FIRESTORE_DB_ID }

  /**
   * Guardrail verifying that a proposed staging target does NOT point at the live production
   * Firebase project or named Firestore database.
   */
  fun isSafeStagingTarget(targetProjectId: String, targetDatabaseId: String): Boolean {
    val cleanProj = targetProjectId.trim()
    val cleanDb = targetDatabaseId.trim()
    if (cleanProj.isEmpty() || cleanDb.isEmpty()) return false
    if (cleanProj.startsWith("your-", ignoreCase = true)) return false
    if (cleanProj == PRODUCTION_FIREBASE_PROJECT_ID) return false
    if (cleanDb == PRODUCTION_FIRESTORE_DB_ID) return false
    return true
  }

  /**
   * Pure resolver for unit testing and runtime Firestore named-database selection.
   * In STAGING mode, refuses to target the live Firebase project or live Firestore database ID.
   */
  fun resolveFirestoreDatabaseId(
    rawEnv: String?,
    defaultResourceDbId: String,
    stagingDbId: String,
    runtimeFirebaseProjectId: String?,
  ): String {
    val env = resolve(rawEnv).environment
    if (env != FoxEnvironment.STAGING) {
      return defaultResourceDbId.trim()
    }
    val cleanStagingDb = stagingDbId.trim().ifEmpty { DEFAULT_STAGING_FIRESTORE_DB_ID }
    val cleanRuntimeProject = runtimeFirebaseProjectId?.trim().orEmpty()
    require(cleanRuntimeProject.isEmpty() || isSafeStagingTarget(cleanRuntimeProject, cleanStagingDb)) {
      "STAGING GUARDRAIL: Từ chối kết nối tới Firebase project/database live ($cleanRuntimeProject / $cleanStagingDb)."
    }
    require(cleanStagingDb != PRODUCTION_FIRESTORE_DB_ID) {
      "STAGING GUARDRAIL: Không được dùng Firestore Database ID của môi trường live trong bản STAGING."
    }
    return cleanStagingDb
  }

  fun resolveFirestoreDatabaseId(context: Context): String {
    val defaultDbId = context.applicationContext.getString(R.string.firestore_database_id)
    val runtimeProjectId = runCatching { FirebaseApp.getInstance().options.projectId }.getOrNull()
    return resolveFirestoreDatabaseId(
      rawEnv = BuildConfig.FOX_ENV,
      defaultResourceDbId = defaultDbId,
      stagingDbId = configuredStagingDatabaseId(),
      runtimeFirebaseProjectId = runtimeProjectId,
    )
  }
}
