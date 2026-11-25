#!/usr/bin/env node
/**
 * FOX Full Feature Pilot — Isolated Staging DEMO Seed Script (Schema-Aligned 1:1 with firestore.rules)
 *
 * SAFETY GUARDRAILS:
 * - Hard-aborts (exit 3) if targeted at live project (`gen-lang-client-0957111252`)
 *   or live named database (`ai-studio-android-1a796de6-79f0-4283-8d0b-703e709b27e4`).
 * - Never deletes any documents (uses merge: true only).
 * - Defaults to --dry-run unless --confirm is explicitly provided.
 * - Writes exact FOX Firestore schema:
 *     /users/{uid} (17 fields)
 *     /admins/{uid} (2 fields)
 *     /merchants/{merchantId} (15 fields, merchantId == ownerUid)
 *     /merchants/{merchantId}/products/{productId} (subcollection, 12 fields)
 *     /wallets/{uid} (integer VND DEMO wallet)
 */

import { createRequire } from "module";

const LIVE_PROJECT_ID = "gen-lang-client-0957111252";
const LIVE_DATABASE_ID = "ai-studio-android-1a796de6-79f0-4283-8d0b-703e709b27e4";

function parseArgs(argv) {
  const args = {
    project: process.env.FOX_STAGING_PROJECT_ID || "",
    database: process.env.FOX_STAGING_DATABASE_ID || "fox-staging-db",
    adminEmail: process.env.FOX_STAGING_ADMIN_EMAIL || "hahaiduy28@gmail.com",
    confirm: false,
    dryRun: false,
  };
  for (let i = 2; i < argv.length; i += 1) {
    const token = argv[i];
    if (token === "--project" && argv[i + 1]) {
      args.project = argv[++i];
    } else if (token === "--database" && argv[i + 1]) {
      args.database = argv[++i];
    } else if (token === "--admin-email" && argv[i + 1]) {
      args.adminEmail = argv[++i];
    } else if (token === "--confirm") {
      args.confirm = true;
    } else if (token === "--dry-run") {
      args.dryRun = true;
    }
  }
  return args;
}

function assertSafeStagingTarget(projectId, databaseId) {
  const trimmedProject = (projectId || "").trim();
  const trimmedDb = (databaseId || "").trim();

  if (!trimmedProject || trimmedProject.toLowerCase().startsWith("your-")) {
    console.error(
      "[ABORT] Missing valid staging --project ID. Example: node staging/seed-demo-staging.mjs --project <STAGING_PROJECT_ID> --database fox-staging-db --confirm"
    );
    process.exit(2);
  }
  if (trimmedProject === LIVE_PROJECT_ID) {
    console.error(
      `[ABORT][GUARDRAIL] Refusing to seed LIVE Firebase project (${LIVE_PROJECT_ID}). Use an isolated staging project.`
    );
    process.exit(3);
  }
  if (!trimmedDb || trimmedDb === LIVE_DATABASE_ID) {
    console.error(
      `[ABORT][GUARDRAIL] Refusing to seed LIVE Firestore database (${LIVE_DATABASE_ID}). Use an isolated staging database (e.g. fox-staging-db).`
    );
    process.exit(3);
  }
}

function buildSeedPayload(adminEmail) {
  const foodMerchantUid = "staging_merchant_food";
  const shopMerchantUid = "staging_merchant_shop";

  return {
    users: [
      {
        userId: "staging_customer_demo",
        displayName: "Khách hàng DEMO Staging",
        email: "customer.staging@fox-demo.local",
        phone: "0901000001",
        defaultAddress: "120 Nguyễn Huệ, Quận 1, TP.HCM",
        role: "customer",
        requestedRole: "none",
        driverStatus: "none",
        merchantStatus: "none",
        accountStatus: "active",
        vehicleType: "",
        vehiclePlate: "",
        storeName: "",
        storeCategory: "",
        driverOnline: false,
      },
      {
        userId: "staging_driver_demo",
        displayName: "Tài xế DEMO Staging",
        email: "driver.staging@fox-demo.local",
        phone: "0901000002",
        defaultAddress: "Quận 1, TP.HCM",
        role: "driver",
        requestedRole: "none",
        driverStatus: "approved",
        merchantStatus: "none",
        accountStatus: "active",
        vehicleType: "FOX Bike",
        vehiclePlate: "59X3-888.88",
        storeName: "",
        storeCategory: "",
        driverOnline: true,
      },
      {
        userId: foodMerchantUid,
        displayName: "Chủ Bếp Cáo Quán (DEMO)",
        email: "merchant.food.staging@fox-demo.local",
        phone: "0901000003",
        defaultAddress: "120 Nguyễn Huệ, Quận 1, TP.HCM",
        role: "merchant",
        requestedRole: "none",
        driverStatus: "none",
        merchantStatus: "approved",
        accountStatus: "active",
        vehicleType: "",
        vehiclePlate: "",
        storeName: "Bếp Cáo Quán — Cơm & Phở (DEMO)",
        storeCategory: "Đồ ăn & Đồ uống",
        driverOnline: false,
      },
      {
        userId: shopMerchantUid,
        displayName: "Chủ FOX Mart Express (DEMO)",
        email: "merchant.shop.staging@fox-demo.local",
        phone: "0901000004",
        defaultAddress: "45 Lê Lợi, Quận 1, TP.HCM",
        role: "merchant",
        requestedRole: "none",
        driverStatus: "none",
        merchantStatus: "approved",
        accountStatus: "active",
        vehicleType: "",
        vehiclePlate: "",
        storeName: "FOX Mart Express — Bách hóa (DEMO)",
        storeCategory: "Siêu thị & Bách hóa",
        driverOnline: false,
      },
      {
        userId: "staging_admin_demo",
        displayName: "Quản trị viên Staging",
        email: adminEmail,
        phone: "0901000009",
        defaultAddress: "Trụ sở FOX Pilot Staging",
        role: "admin",
        requestedRole: "none",
        driverStatus: "none",
        merchantStatus: "none",
        accountStatus: "active",
        vehicleType: "",
        vehiclePlate: "",
        storeName: "",
        storeCategory: "",
        driverOnline: false,
      },
    ],
    admins: [{ uid: "staging_admin_demo" }],
    wallets: [
      {
        userId: "staging_customer_demo",
        balanceVnd: 500000,
        totalTopUpVnd: 500000,
        totalSpentVnd: 0,
        totalEarnedVnd: 0,
        totalRefundedVnd: 0,
        currency: "VND",
        isDemo: true,
      },
      {
        userId: "staging_driver_demo",
        balanceVnd: 200000,
        totalTopUpVnd: 200000,
        totalSpentVnd: 0,
        totalEarnedVnd: 0,
        totalRefundedVnd: 0,
        currency: "VND",
        isDemo: true,
      },
    ],
    merchants: [
      {
        merchantId: foodMerchantUid,
        ownerUid: foodMerchantUid,
        storeType: "FOOD",
        name: "Bếp Cáo Quán — Cơm & Phở (DEMO)",
        description: "Gian hàng đồ ăn thử nghiệm Staging — dữ liệu DEMO.",
        category: "Món Việt",
        address: "120 Nguyễn Huệ, Quận 1, TP.HCM",
        phone: "0901000003",
        imageUrl: "",
        openingHours: "07:00 - 22:00",
        openNow: true,
        ratingTenths: 48,
        active: true,
      },
      {
        merchantId: shopMerchantUid,
        ownerUid: shopMerchantUid,
        storeType: "SHOPPING",
        name: "FOX Mart Express — Bách hóa (DEMO)",
        description: "Siêu thị tiện lợi thử nghiệm Staging — dữ liệu DEMO.",
        category: "Bách hóa",
        address: "45 Lê Lợi, Quận 1, TP.HCM",
        phone: "0901000004",
        imageUrl: "",
        openingHours: "06:30 - 23:00",
        openNow: true,
        ratingTenths: 49,
        active: true,
      },
    ],
    products: [
      {
        productId: "prod_food_staging_pho",
        merchantId: foodMerchantUid,
        storeName: "Bếp Cáo Quán — Cơm & Phở (DEMO)",
        serviceType: "FOOD",
        name: "Phở bò đặc biệt FOX (DEMO)",
        description: "Nước dùng hầm xương 12 giờ, thịt tái nạm gầu.",
        imageUrl: "",
        category: "Món nước",
        priceVnd: 55000,
        stock: 25,
        active: true,
      },
      {
        productId: "prod_food_staging_comtam",
        merchantId: foodMerchantUid,
        storeName: "Bếp Cáo Quán — Cơm & Phở (DEMO)",
        serviceType: "FOOD",
        name: "Cơm tấm sườn bì chả (DEMO)",
        description: "Sườn nướng mật ong, bì thính, chả trứng.",
        imageUrl: "",
        category: "Cơm trưa",
        priceVnd: 48000,
        stock: 30,
        active: true,
      },
      {
        productId: "prod_shop_staging_milk",
        merchantId: shopMerchantUid,
        storeName: "FOX Mart Express — Bách hóa (DEMO)",
        serviceType: "SHOPPING",
        name: "Sữa tươi ít đường 1L (DEMO)",
        description: "Giao nhanh nội thành trong 30 phút.",
        imageUrl: "",
        category: "Sữa & Đồ uống",
        priceVnd: 36000,
        stock: 20,
        active: true,
      },
      {
        productId: "prod_shop_staging_limited",
        merchantId: shopMerchantUid,
        storeName: "FOX Mart Express — Bách hóa (DEMO)",
        serviceType: "SHOPPING",
        name: "Combo trái cây nhập khẩu (Tồn kho 1 - Test Race)",
        description: "Sản phẩm kiểm thử chống mua trùng tồn kho cuối trên 2 thiết bị.",
        imageUrl: "",
        category: "Trái cây",
        priceVnd: 95000,
        stock: 1,
        active: true,
      },
    ],
  };
}

async function main() {
  const args = parseArgs(process.argv);
  assertSafeStagingTarget(args.project, args.database);

  const payload = buildSeedPayload(args.adminEmail);
  console.log(
    `[FOX STAGING SEED] Target project=${args.project}, database=${args.database}, emulator=${process.env.FIRESTORE_EMULATOR_HOST || "NONE (Cloud Staging)"}`
  );
  console.log(
    `[FOX STAGING SEED] Schema-aligned payload: ${payload.users.length} users, ${payload.admins.length} admin, ${payload.wallets.length} DEMO wallets, ${payload.merchants.length} merchants, ${payload.products.length} subcollection products.`
  );

  if (args.dryRun || !args.confirm) {
    console.log(
      "[FOX STAGING SEED] Dry-run mode (no writes performed, zero deletions). Pass --confirm to seed the isolated staging database."
    );
    return;
  }

  const require = createRequire(new URL("../functions/package.json", import.meta.url));
  let getApps;
  let initializeApp;
  let getFirestore;
  let FieldValue;
  try {
    ({ getApps, initializeApp } = require("firebase-admin/app"));
    ({ getFirestore, FieldValue } = require("firebase-admin/firestore"));
  } catch (err) {
    console.error("[ABORT] Thiếu firebase-admin trong functions/node_modules. Hãy chạy: npm --prefix functions install");
    console.error(err?.message || err);
    process.exit(5);
  }

  const app = getApps().length ? getApps()[0] : initializeApp({ projectId: args.project });
  const db = getFirestore(app, args.database);

  const batch = db.batch();
  for (const user of payload.users) {
    batch.set(
      db.collection("users").doc(user.userId),
      {
        ...user,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true }
    );
  }
  for (const adm of payload.admins) {
    batch.set(
      db.collection("admins").doc(adm.uid),
      {
        uid: adm.uid,
        grantedAt: FieldValue.serverTimestamp(),
      },
      { merge: true }
    );
  }
  for (const wallet of payload.wallets) {
    batch.set(
      db.collection("wallets").doc(wallet.userId),
      {
        ...wallet,
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true }
    );
  }
  for (const merchant of payload.merchants) {
    batch.set(
      db.collection("merchants").doc(merchant.merchantId),
      {
        ...merchant,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true }
    );
  }
  for (const product of payload.products) {
    const { productId, ...productData } = product;
    batch.set(
      db
        .collection("merchants")
        .doc(product.merchantId)
        .collection("products")
        .doc(productId),
      {
        ...productData,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true }
    );
  }

  await batch.commit();
  console.log("[FOX STAGING SEED] PASS — Schema-aligned DEMO personas, wallets, merchants & subcollection products seeded to staging.");
}

main().catch((err) => {
  console.error("[FOX STAGING SEED] FAIL:", err?.message || err);
  process.exit(1);
});
