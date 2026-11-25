const { describe, it, before, after } = require("node:test");
const assert = require("node:assert/strict");
const { getApps, initializeApp } = require("firebase-admin/app");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");

const {
  topUpDemoWalletTx,
  createVerifiedOrderAndPayDemoTx,
  payOrderWithDemoWalletTx,
  refundOrderDemoTx,
  settleOrderCommissionDemoTx,
  getWalletSummaryOp,
} = require("../lib/financeService");
const {
  dispatchOrderNotifications,
  notifyOrderEventOp,
  notifyPartnerReviewOp,
} = require("../lib/notificationService");
const { FOX_DATABASE_ID, BOOTSTRAPPED_ADMIN_EMAIL } = require("../lib/types");

if (!process.env.FIRESTORE_EMULATOR_HOST) {
  process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8085";
}

const PROJECT_ID = "gen-lang-client-0957111252";
const app = getApps().length > 0 ? getApps()[0] : initializeApp({ projectId: PROJECT_ID });
const db = getFirestore(app, FOX_DATABASE_ID);

async function clearEmulatorDb() {
  const host = process.env.FIRESTORE_EMULATOR_HOST;
  const url = `http://${host}/emulator/v1/projects/${PROJECT_ID}/databases/${FOX_DATABASE_ID}/documents`;
  const res = await fetch(url, { method: "DELETE" });
  if (!res.ok) {
    throw new Error(`Failed to clear Firestore emulator: ${res.status}`);
  }
}

function createSpyFcmSender(invalidTokensToSimulate = new Set()) {
  const sentPayloads = [];
  const sender = async (payload) => {
    sentPayloads.push(payload);
    const invalidTokens = [];
    let successCount = 0;
    let failureCount = 0;
    for (const t of payload.tokens) {
      if (invalidTokensToSimulate.has(t)) {
        failureCount += 1;
        invalidTokens.push(t);
      } else {
        successCount += 1;
      }
    }
    return { successCount, failureCount, invalidTokens };
  };
  return { sender, sentPayloads };
}

async function seedUser(uid, extra = {}, fcmToken = null) {
  const now = FieldValue.serverTimestamp();
  await db.collection("users").doc(uid).set({
    userId: uid,
    displayName: extra.displayName || uid,
    email: extra.email || `${uid}@fox.demo`,
    phone: "0901234567",
    defaultAddress: "Quận 1, TP.HCM",
    role: extra.role || "customer",
    requestedRole: extra.requestedRole || "none",
    driverStatus: extra.driverStatus || "none",
    merchantStatus: extra.merchantStatus || "none",
    accountStatus: extra.accountStatus || "active",
    vehicleType: extra.vehicleType || "",
    vehiclePlate: extra.vehiclePlate || "",
    storeName: extra.storeName || "",
    storeCategory: extra.storeCategory || "",
    driverOnline: extra.driverOnline === true,
    createdAt: now,
    updatedAt: now,
  });
  if (fcmToken) {
    await db.collection("users").doc(uid).collection("fcm_tokens").doc("tok_primary").set({
      token: fcmToken,
      platform: "android",
      role: extra.role || "customer",
      enabled: true,
      updatedAt: now,
    });
  }
}

describe("FOX Full Feature Pilot End-to-End & Security Audit Suites (Firestore Emulator)", () => {
  before(async () => {
    await clearEmulatorDb();
  });

  after(async () => {
    await clearEmulatorDb();
  });

  it("E2E 1 (RIDE): Đặt xe trọn luồng -> tài xế nhận -> đến đón -> đang chạy -> hoàn thành -> thanh toán & hoa hồng -> đánh giá 5 sao", async () => {
    const custAuth = { uid: "e2e_cust_1", email: "c1@fox.demo", emailVerified: true };
    const drvOnlineAuth = { uid: "e2e_drv_online", email: "d1@fox.demo", emailVerified: true };
    const drvOfflineAuth = { uid: "e2e_drv_offline", email: "d2@fox.demo", emailVerified: true };
    const adminAuth = { uid: "e2e_admin", email: BOOTSTRAPPED_ADMIN_EMAIL, emailVerified: true };

    await seedUser(custAuth.uid, { displayName: "Khách Minh", role: "customer" }, "fcm_token_customer_000001");
    await seedUser(
      drvOnlineAuth.uid,
      { displayName: "Tài xế Hùng", role: "driver", driverStatus: "approved", driverOnline: true, vehicleType: "FOX Bike" },
      "fcm_token_driver_online_001",
    );
    await seedUser(
      drvOfflineAuth.uid,
      { displayName: "Tài xế Nghỉ", role: "driver", driverStatus: "approved", driverOnline: false, vehicleType: "FOX Bike" },
      "fcm_token_driver_offline_02",
    );
    await seedUser(adminAuth.uid, { displayName: "Admin FOX", role: "admin", email: BOOTSTRAPPED_ADMIN_EMAIL }, "fcm_token_admin_000000001");

    await topUpDemoWalletTx(db, custAuth, { amountVnd: 200_000, idempotencyKey: "e2e_topup_ride_1" });

    const created = await createVerifiedOrderAndPayDemoTx(db, custAuth, {
      idempotencyKey: "e2e_ride_order_1",
      serviceType: "RIDE",
      paymentMethod: "Ví FOX DEMO",
      title: "Đặt xe FOX Bike",
      primaryAddress: "Chợ Bến Thành, Quận 1",
      secondaryAddress: "Landmark 81, Bình Thạnh",
      detailSummary: "FOX Bike • Lộ trình DEMO",
      vehicleType: "FOX Bike",
      pickupCoords: { lat: 10.7725, lng: 106.6980 },
      dropoffCoords: { lat: 10.7950, lng: 106.7218 },
    });
    assert.equal(created.paymentStatus, "paid_demo");
    assert.ok(Number.isSafeInteger(created.quote.totalVnd) && created.quote.totalVnd > 0);

    // Dispatch order_created -> online driver gets FCM, offline driver does NOT
    const spyCreate = createSpyFcmSender();
    const orderSnap1 = await db.collection("orders").doc(created.orderId).get();
    const resCreate = await dispatchOrderNotifications(db, created.orderId, orderSnap1.data(), "order_created", spyCreate.sender);
    assert.ok(resCreate.recipientUids.includes(custAuth.uid));
    assert.ok(resCreate.recipientUids.includes(drvOnlineAuth.uid));
    assert.ok(!resCreate.recipientUids.includes(drvOfflineAuth.uid));
    assert.equal(resCreate.fcmSuccessCount, 2);

    // Driver accepts -> arrives -> in_progress -> completed
    await db.collection("orders").doc(created.orderId).update({
      driverId: drvOnlineAuth.uid,
      driverName: "Tài xế Hùng",
      status: "driver_accepted",
      dispatchOpen: false,
      dispatchTarget: "none",
      updatedAt: FieldValue.serverTimestamp(),
    });
    const spyAccept = createSpyFcmSender();
    const resAccept = await notifyOrderEventOp(db, drvOnlineAuth, { orderId: created.orderId, eventType: "driver_accepted" }, spyAccept.sender);
    assert.deepEqual(resAccept.recipientUids, [custAuth.uid]);
    assert.equal(spyAccept.sentPayloads[0].data.targetRole, "customer");
    assert.equal(spyAccept.sentPayloads[0].data.channelId, "fox_orders_customer");

    await db.collection("orders").doc(created.orderId).update({ status: "driver_arrived", updatedAt: FieldValue.serverTimestamp() });
    await db.collection("orders").doc(created.orderId).update({ status: "in_progress", updatedAt: FieldValue.serverTimestamp() });
    await db.collection("orders").doc(created.orderId).update({ status: "completed", updatedAt: FieldValue.serverTimestamp() });

    const settled = await settleOrderCommissionDemoTx(db, drvOnlineAuth, { orderId: created.orderId });
    assert.equal(settled.driverCommissionVnd + settled.platformRevenueVnd, settled.grossTotalVnd);
    assert.equal(settled.merchantCommissionVnd, 0);

    // Customer submits 5-star rating on completed ride
    await db.collection("orders").doc(created.orderId).update({
      customerRating: 5,
      customerReviewNote: "Chuyến đi êm ái, đúng giờ!",
      ratedAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    const ratedSnap = await db.collection("orders").doc(created.orderId).get();
    assert.equal(ratedSnap.data().customerRating, 5);
    assert.equal(ratedSnap.data().commissionSettled, true);

    const spyDone = createSpyFcmSender();
    const resDone = await dispatchOrderNotifications(db, created.orderId, ratedSnap.data(), "completed", spyDone.sender);
    assert.ok(resDone.recipientUids.includes(custAuth.uid));
    assert.ok(resDone.recipientUids.includes(drvOnlineAuth.uid));
    assert.ok(resDone.recipientUids.includes(adminAuth.uid));
  });

  it("E2E 2 (DELIVERY): Giao hàng trọn luồng -> tạo vận đơn -> tài xế nhận -> lấy hàng -> giao hàng -> hoàn thành & đối soát lịch sử", async () => {
    const custAuth = { uid: "e2e_cust_deliv", email: "cdeliv@fox.demo", emailVerified: true };
    const drvAuth = { uid: "e2e_drv_deliv", email: "ddeliv@fox.demo", emailVerified: true };

    await seedUser(custAuth.uid, { displayName: "Khách Giao Hàng", role: "customer" }, "fcm_token_deliv_cust_01");
    await seedUser(
      drvAuth.uid,
      { displayName: "Tài xế Giao Nhanh", role: "driver", driverStatus: "approved", driverOnline: true, vehicleType: "FOX Bike" },
      "fcm_token_deliv_drv_001",
    );

    await topUpDemoWalletTx(db, custAuth, { amountVnd: 150_000, idempotencyKey: "e2e_topup_deliv_1" });

    const order = await createVerifiedOrderAndPayDemoTx(db, custAuth, {
      idempotencyKey: "e2e_deliv_order_1",
      serviceType: "DELIVERY",
      weightTier: "2–5 kg",
      speed: "Nhanh",
      voucherCode: "FOXSHIP15",
      paymentMethod: "Ví FOX DEMO",
      title: "Giao hàng Nhanh",
      primaryAddress: "12 Lê Lợi, Q.1",
      secondaryAddress: "250 Điện Biên Phủ, Bình Thạnh",
      detailSummary: "Hồ sơ tài liệu (2–5 kg, Nhanh)",
      pickupCoords: { lat: 10.7725, lng: 106.6980 },
      dropoffCoords: { lat: 10.8012, lng: 106.7115 },
    });
    assert.equal(order.paymentStatus, "paid_demo");
    assert.equal(order.quote.voucherCode, "FOXSHIP15");
    assert.equal(order.quote.discountVnd, 10_000);

    // Driver claims -> arrives -> picks up & delivers -> completes
    await db.collection("orders").doc(order.orderId).update({
      driverId: drvAuth.uid,
      driverName: "Tài xế Giao Nhanh",
      status: "driver_accepted",
      dispatchOpen: false,
      dispatchTarget: "none",
      updatedAt: FieldValue.serverTimestamp(),
    });
    await db.collection("orders").doc(order.orderId).update({ status: "driver_arrived", updatedAt: FieldValue.serverTimestamp() });
    await db.collection("orders").doc(order.orderId).update({ status: "in_progress", updatedAt: FieldValue.serverTimestamp() });
    await db.collection("orders").doc(order.orderId).update({ status: "completed", updatedAt: FieldValue.serverTimestamp() });

    const settled = await settleOrderCommissionDemoTx(db, drvAuth, { orderId: order.orderId });
    assert.equal(settled.driverCommissionVnd + settled.platformRevenueVnd, order.quote.totalVnd);
    assert.equal(settled.merchantCommissionVnd, 0);

    // Verify driver wallet ledger & customer wallet ledger persisted accurately
    const drvWallet = await getWalletSummaryOp(db, drvAuth);
    assert.equal(drvWallet.balanceVnd, settled.driverCommissionVnd);
    assert.equal(drvWallet.transactions.length, 1);
    assert.equal(drvWallet.transactions[0].txType, "DRIVER_COMMISSION_DEMO");

    const revDoc = await db.collection("platform_revenue").doc(`rev_${order.orderId}`).get();
    assert.equal(revDoc.exists, true);
    assert.equal(revDoc.data().serviceType, "DELIVERY");
    assert.equal(revDoc.data().platformRevenueVnd, settled.platformRevenueVnd);
  });

  it("E2E 3 (FOOD): Đặt đồ ăn Cloud Catalog 3 bên (Khách -> Gian hàng -> Tài xế) + trừ tồn kho nguyên tử + FCM + quyết toán 3 chiều", async () => {
    const custAuth = { uid: "e2e_cust_food", email: "cf@fox.demo", emailVerified: true };
    const merchAuth = { uid: "e2e_merch_1", email: "m1@fox.demo", emailVerified: true };
    const drvAuth = { uid: "e2e_drv_food", email: "df@fox.demo", emailVerified: true };

    await seedUser(custAuth.uid, { displayName: "Khách Lan", role: "customer" }, "fcm_token_food_customer_1");
    await seedUser(
      merchAuth.uid,
      { displayName: "Bếp Phở FOX", role: "merchant", merchantStatus: "approved", storeName: "Phở FOX Q1" },
      "fcm_token_food_merchant_1",
    );
    await seedUser(
      drvAuth.uid,
      { displayName: "Tài xế Giao Đồ Ăn", role: "driver", driverStatus: "approved", driverOnline: true },
      "fcm_token_food_driver_001",
    );

    const now = FieldValue.serverTimestamp();
    await db.collection("merchants").doc(merchAuth.uid).set({
      merchantId: merchAuth.uid,
      ownerUid: merchAuth.uid,
      storeType: "FOOD",
      name: "Phở FOX Q1",
      description: "Phở bò gia truyền",
      category: "Món nước",
      address: "12 Lê Lợi, Q1",
      phone: "0909000111",
      imageUrl: "",
      openingHours: "06:00 - 22:00",
      openNow: true,
      ratingTenths: 49,
      active: true,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("merchants").doc(merchAuth.uid).collection("products").doc("pho_dac_biet").set({
      merchantId: merchAuth.uid,
      storeName: "Phở FOX Q1",
      serviceType: "FOOD",
      name: "Phở Đặc Biệt",
      description: "Tái nạm gầu gân",
      imageUrl: "",
      category: "Phở",
      priceVnd: 65_000,
      stock: 5,
      active: true,
      createdAt: now,
      updatedAt: now,
    });

    await topUpDemoWalletTx(db, custAuth, { amountVnd: 300_000, idempotencyKey: "e2e_topup_food_1" });

    const order = await createVerifiedOrderAndPayDemoTx(db, custAuth, {
      idempotencyKey: "e2e_food_order_1",
      serviceType: "FOOD",
      paymentMethod: "Ví FOX DEMO",
      title: "Phở FOX Q1 (2 món)",
      primaryAddress: "12 Lê Lợi, Q1",
      secondaryAddress: "99 Nguyễn Huệ, Q1",
      detailSummary: "Phở Đặc Biệt x2",
      merchantId: merchAuth.uid,
      items: [{ itemId: "pho_dac_biet", quantity: 2 }],
    });
    assert.equal(order.stockDeducted, true);
    assert.equal(order.quote.subtotalVnd, 130_000);
    assert.equal(order.quote.feeVnd, 15_000);
    assert.equal(order.quote.totalVnd, 145_000);

    const stockSnap = await db.collection("merchants").doc(merchAuth.uid).collection("products").doc("pho_dac_biet").get();
    assert.equal(stockSnap.data().stock, 3);

    const spyCreated = createSpyFcmSender();
    const ordSnap = await db.collection("orders").doc(order.orderId).get();
    const resCreated = await dispatchOrderNotifications(db, order.orderId, ordSnap.data(), "order_created", spyCreated.sender);
    assert.ok(resCreated.recipientUids.includes(merchAuth.uid));
    assert.ok(resCreated.recipientUids.includes(custAuth.uid));
    assert.ok(!resCreated.recipientUids.includes(drvAuth.uid));

    await db.collection("orders").doc(order.orderId).update({ status: "merchant_confirmed", updatedAt: FieldValue.serverTimestamp() });
    await db.collection("orders").doc(order.orderId).update({ status: "preparing", updatedAt: FieldValue.serverTimestamp() });
    await db.collection("orders").doc(order.orderId).update({
      status: "ready_for_pickup",
      dispatchOpen: true,
      dispatchTarget: "driver",
      updatedAt: FieldValue.serverTimestamp(),
    });
    const spyReady = createSpyFcmSender();
    const resReady = await notifyOrderEventOp(db, merchAuth, { orderId: order.orderId, eventType: "ready_for_pickup" }, spyReady.sender);
    assert.ok(resReady.recipientUids.includes(drvAuth.uid));
    assert.ok(resReady.recipientUids.includes(custAuth.uid));

    await db.collection("orders").doc(order.orderId).update({
      driverId: drvAuth.uid,
      driverName: "Tài xế Giao Đồ Ăn",
      status: "completed",
      dispatchOpen: false,
      dispatchTarget: "none",
      updatedAt: FieldValue.serverTimestamp(),
    });
    const split = await settleOrderCommissionDemoTx(db, drvAuth, { orderId: order.orderId });
    assert.equal(split.driverCommissionVnd + split.merchantCommissionVnd + split.platformRevenueVnd, split.grossTotalVnd);
    assert.ok(split.driverCommissionVnd > 0 && split.merchantCommissionVnd > 0 && split.platformRevenueVnd > 0);
  });

  it("E2E 4 (SHOPPING): Mua sắm trọn luồng -> kiểm tra kho -> gian hàng nhận -> tài xế giao -> hoàn thành -> cập nhật kho & doanh thu", async () => {
    const custAuth = { uid: "e2e_cust_shop", email: "cshop@fox.demo", emailVerified: true };
    const merchAuth = { uid: "e2e_merch_shop", email: "mshop@fox.demo", emailVerified: true };
    const drvAuth = { uid: "e2e_drv_shop", email: "dshop@fox.demo", emailVerified: true };

    await seedUser(custAuth.uid, { displayName: "Khách Mua Sắm", role: "customer" }, "fcm_token_shop_cust_01");
    await seedUser(merchAuth.uid, { displayName: "FOX Tech Mart", role: "merchant", merchantStatus: "approved" }, "fcm_token_shop_merch_1");
    await seedUser(drvAuth.uid, { displayName: "Tài xế Giao Hàng Shop", role: "driver", driverStatus: "approved", driverOnline: true }, "fcm_token_shop_drv_01");

    const now = FieldValue.serverTimestamp();
    await db.collection("merchants").doc(merchAuth.uid).set({
      merchantId: merchAuth.uid,
      ownerUid: merchAuth.uid,
      storeType: "SHOPPING",
      name: "FOX Tech Mart",
      description: "Phụ kiện chính hãng",
      category: "Công nghệ",
      address: "88 Cách Mạng Tháng 8, Q3",
      phone: "0908111333",
      imageUrl: "",
      openingHours: "08:00 - 22:00",
      openNow: true,
      ratingTenths: 49,
      active: true,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("merchants").doc(merchAuth.uid).collection("products").doc("binh_giu_nhiet").set({
      merchantId: merchAuth.uid,
      storeName: "FOX Tech Mart",
      serviceType: "SHOPPING",
      name: "Bình Giữ Nhiệt Inox 750ml",
      description: "Giữ nóng lạnh 12h",
      imageUrl: "",
      category: "Gia dụng",
      priceVnd: 200_000,
      stock: 6,
      active: true,
      createdAt: now,
      updatedAt: now,
    });

    await topUpDemoWalletTx(db, custAuth, { amountVnd: 500_000, idempotencyKey: "e2e_topup_shop_1" });

    // Order 2 units (subtotal = 400,000 -> free shipping >= 300,000, voucher FOXSHOP20 -> -20,000 -> total = 380,000)
    const order = await createVerifiedOrderAndPayDemoTx(db, custAuth, {
      idempotencyKey: "e2e_shop_order_full_1",
      serviceType: "SHOPPING",
      paymentMethod: "Ví FOX DEMO",
      voucherCode: "FOXSHOP20",
      title: "FOX Tech Mart (2 sản phẩm)",
      primaryAddress: "88 Cách Mạng Tháng 8, Q3",
      secondaryAddress: "45 Lý Tự Trọng, Q1",
      merchantId: merchAuth.uid,
      items: [{ itemId: "binh_giu_nhiet", quantity: 2 }],
    });
    assert.equal(order.stockDeducted, true);
    assert.equal(order.quote.subtotalVnd, 400_000);
    assert.equal(order.quote.feeVnd, 0);
    assert.equal(order.quote.discountVnd, 20_000);
    assert.equal(order.quote.totalVnd, 380_000);

    // Merchant confirms -> prepares -> ready_for_pickup -> Driver delivers -> completes
    await db.collection("orders").doc(order.orderId).update({ status: "merchant_confirmed", updatedAt: FieldValue.serverTimestamp() });
    await db.collection("orders").doc(order.orderId).update({ status: "preparing", updatedAt: FieldValue.serverTimestamp() });
    await db.collection("orders").doc(order.orderId).update({
      status: "ready_for_pickup",
      dispatchOpen: true,
      dispatchTarget: "driver",
      updatedAt: FieldValue.serverTimestamp(),
    });
    await db.collection("orders").doc(order.orderId).update({
      driverId: drvAuth.uid,
      driverName: "Tài xế Giao Hàng Shop",
      status: "driver_accepted",
      dispatchOpen: false,
      dispatchTarget: "none",
      updatedAt: FieldValue.serverTimestamp(),
    });
    await db.collection("orders").doc(order.orderId).update({ status: "in_progress", updatedAt: FieldValue.serverTimestamp() });
    await db.collection("orders").doc(order.orderId).update({ status: "completed", updatedAt: FieldValue.serverTimestamp() });

    const split = await settleOrderCommissionDemoTx(db, merchAuth, { orderId: order.orderId });
    assert.equal(split.merchantCommissionVnd + split.driverCommissionVnd + split.platformRevenueVnd, 380_000);

    // Verify stock stays at 4 and merchant wallet & platform revenue recorded
    const prodAfter = await db.collection("merchants").doc(merchAuth.uid).collection("products").doc("binh_giu_nhiet").get();
    assert.equal(prodAfter.data().stock, 4);
    const merchWallet = await getWalletSummaryOp(db, merchAuth);
    assert.equal(merchWallet.balanceVnd, split.merchantCommissionVnd);
    const revSnap = await db.collection("platform_revenue").doc(`rev_${order.orderId}`).get();
    assert.equal(revSnap.exists, true);
    assert.equal(revSnap.data().serviceType, "SHOPPING");
  });

  it("E2E 5: Hủy đơn hợp lệ hoàn tiền Ví DEMO + hoàn tồn kho + FCM & chặn bên thứ ba giả mạo gửi thông báo", async () => {
    const custAuth = { uid: "e2e_cust_cancel", email: "cc@fox.demo", emailVerified: true };
    const merchAuth = { uid: "e2e_merch_cancel", email: "mc@fox.demo", emailVerified: true };
    const attackerAuth = { uid: "e2e_attacker", email: "atk@fox.demo", emailVerified: true };

    await seedUser(custAuth.uid, { displayName: "Khách Hủy", role: "customer" }, "fcm_token_cancel_cust_001");
    await seedUser(merchAuth.uid, { displayName: "Shop FOX", role: "merchant", merchantStatus: "approved" }, "fcm_token_cancel_merch_01");
    await seedUser(attackerAuth.uid, { displayName: "Kẻ lạ", role: "customer" });

    const now = FieldValue.serverTimestamp();
    await db.collection("merchants").doc(merchAuth.uid).set({
      merchantId: merchAuth.uid,
      ownerUid: merchAuth.uid,
      storeType: "SHOPPING",
      name: "FOX Mart",
      description: "Tạp hóa nhanh",
      category: "Bách hóa",
      address: "55 Võ Văn Tần, Q3",
      phone: "0909111222",
      imageUrl: "",
      openingHours: "07:00 - 23:00",
      openNow: true,
      ratingTenths: 48,
      active: true,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("merchants").doc(merchAuth.uid).collection("products").doc("nuoc_suoi").set({
      merchantId: merchAuth.uid,
      storeName: "FOX Mart",
      serviceType: "SHOPPING",
      name: "Thùng Nước Suối",
      description: "24 chai",
      imageUrl: "",
      category: "Đồ uống",
      priceVnd: 100_000,
      stock: 4,
      active: true,
      createdAt: now,
      updatedAt: now,
    });

    await topUpDemoWalletTx(db, custAuth, { amountVnd: 200_000, idempotencyKey: "e2e_topup_cancel_1" });
    const order = await createVerifiedOrderAndPayDemoTx(db, custAuth, {
      idempotencyKey: "e2e_shop_cancel_1",
      serviceType: "SHOPPING",
      paymentMethod: "Ví FOX DEMO",
      title: "FOX Mart (1 sản phẩm)",
      primaryAddress: "55 Võ Văn Tần, Q3",
      secondaryAddress: "10 Pasteur, Q1",
      detailSummary: "Thùng Nước Suối x1",
      merchantId: merchAuth.uid,
      items: [{ itemId: "nuoc_suoi", quantity: 1 }],
    });

    await assert.rejects(
      () => notifyOrderEventOp(db, attackerAuth, { orderId: order.orderId, eventType: "cancelled" }),
      /permission-denied|không có quyền/i,
    );

    const refund = await refundOrderDemoTx(db, custAuth, {
      orderId: order.orderId,
      idempotencyKey: "e2e_refund_cancel_1",
      reason: "Đổi ý",
    });
    assert.equal(refund.refundedAmountVnd, order.quote.totalVnd);
    assert.equal(refund.customerBalanceAfterVnd, 200_000);
    assert.equal(refund.stockRestored, true);

    const stockAfter = await db.collection("merchants").doc(merchAuth.uid).collection("products").doc("nuoc_suoi").get();
    assert.equal(stockAfter.data().stock, 4);

    const spyCancel = createSpyFcmSender();
    const resCancel = await notifyOrderEventOp(db, custAuth, { orderId: order.orderId, eventType: "cancelled" }, spyCancel.sender);
    assert.ok(resCancel.recipientUids.includes(custAuth.uid));
    assert.ok(resCancel.recipientUids.includes(merchAuth.uid));
  });

  it("E2E 6: Duyệt đối tác qua FCM + dọn token FCM hỏng + kiểm toán KHÔNG rò rỉ PII (email/SĐT/token) trong push payload", async () => {
    const applicantAuth = { uid: "e2e_new_drv", email: "secret_applicant@fox.demo", emailVerified: true };
    const adminAuth = { uid: "e2e_admin_2", email: BOOTSTRAPPED_ADMIN_EMAIL, emailVerified: true };

    const staleToken = "fcm_token_stale_unregistered_999";
    await seedUser(applicantAuth.uid, { displayName: "Ứng viên Tài xế", role: "customer", email: applicantAuth.email }, staleToken);
    await seedUser(adminAuth.uid, { displayName: "Admin FOX", role: "admin", email: BOOTSTRAPPED_ADMIN_EMAIL }, "fcm_token_admin_healthy_01");

    const spySub = createSpyFcmSender();
    const resSub = await notifyPartnerReviewOp(
      db,
      applicantAuth,
      { targetUid: applicantAuth.uid, partnerType: "driver", eventType: "partner_application_submitted" },
      spySub.sender,
    );
    assert.ok(resSub.recipientUids.includes(adminAuth.uid));

    // Verify zero PII leaks into FCM push payload!
    for (const p of spySub.sentPayloads) {
      const serialized = JSON.stringify({ title: p.title, body: p.body, data: p.data });
      assert.ok(!serialized.includes("secret_applicant@fox.demo"), "Push payload MUST NOT leak user email PII");
      assert.ok(!serialized.includes("0901234567"), "Push payload MUST NOT leak phone number PII");
      assert.ok(!serialized.includes(staleToken), "Push payload MUST NOT echo FCM tokens inside data/body");
    }

    await assert.rejects(
      () =>
        notifyPartnerReviewOp(
          db,
          applicantAuth,
          { targetUid: applicantAuth.uid, partnerType: "driver", eventType: "partner_approved" },
          spySub.sender,
        ),
      /permission-denied|Quản trị viên/i,
    );

    const spyApprove = createSpyFcmSender(new Set([staleToken]));
    const resApprove = await notifyPartnerReviewOp(
      db,
      adminAuth,
      { targetUid: applicantAuth.uid, partnerType: "driver", eventType: "partner_approved" },
      spyApprove.sender,
    );
    assert.deepEqual(resApprove.recipientUids, [applicantAuth.uid]);
    assert.equal(resApprove.fcmFailureCount, 1);
    assert.equal(resApprove.cleanedInvalidTokens, 1);

    const remainingTokens = await db.collection("users").doc(applicantAuth.uid).collection("fcm_tokens").get();
    assert.equal(remainingTokens.size, 0);
  });

  it("E2E 7 (Audit): Chống dùng trùng voucher + 2 tài xế nhận 1 đơn đồng thời + 2 khách mua sản phẩm cuối cùng đồng thời", async () => {
    const cust1 = { uid: "audit_cust_1", email: "ac1@fox.demo", emailVerified: true };
    const cust2 = { uid: "audit_cust_2", email: "ac2@fox.demo", emailVerified: true };
    const merch = { uid: "audit_merch_1", email: "am1@fox.demo", emailVerified: true };

    await seedUser(cust1.uid, { displayName: "Audit Khách 1", role: "customer" });
    await seedUser(cust2.uid, { displayName: "Audit Khách 2", role: "customer" });
    await seedUser(merch.uid, { displayName: "Audit Shop", role: "merchant", merchantStatus: "approved" });

    // 7a. Duplicate voucher reuse by SAME user is REJECTED; DIFFERENT user CAN still use it!
    const firstVoucherOrder = await createVerifiedOrderAndPayDemoTx(db, cust1, {
      idempotencyKey: "audit_voucher_ord_1",
      serviceType: "RIDE",
      vehicleType: "FOX Bike",
      distanceKm: 5,
      voucherCode: "FOXRIDE25",
      paymentMethod: "Tiền mặt DEMO",
      primaryAddress: "Quận 1",
      secondaryAddress: "Quận 3",
    });
    assert.equal(firstVoucherOrder.quote.voucherApplied, true);

    await assert.rejects(
      () =>
        createVerifiedOrderAndPayDemoTx(db, cust1, {
          idempotencyKey: "audit_voucher_ord_2",
          serviceType: "RIDE",
          vehicleType: "FOX Bike",
          distanceKm: 5,
          voucherCode: "FOXRIDE25",
          paymentMethod: "Tiền mặt DEMO",
          primaryAddress: "Quận 1",
          secondaryAddress: "Quận 5",
        }),
      (err) => err.code === "already-exists" && /đã được tài khoản của bạn sử dụng/i.test(err.message),
    );

    const otherUserVoucherOrder = await createVerifiedOrderAndPayDemoTx(db, cust2, {
      idempotencyKey: "audit_voucher_ord_cust2",
      serviceType: "RIDE",
      vehicleType: "FOX Bike",
      distanceKm: 5,
      voucherCode: "FOXRIDE25",
      paymentMethod: "Tiền mặt DEMO",
      primaryAddress: "Quận 1",
      secondaryAddress: "Quận 7",
    });
    assert.equal(otherUserVoucherOrder.quote.voucherApplied, true);

    // 7b. Two drivers concurrently claiming the SAME order via atomic Firestore Transaction -> strictly 1 wins!
    const claimOrderRef = db.collection("orders").doc(firstVoucherOrder.orderId);
    const attemptClaim = (driverId, driverName) =>
      db.runTransaction(async (tx) => {
        const snap = await tx.get(claimOrderRef);
        if (snap.data().driverId !== "" || snap.data().status !== "pending") {
          throw new Error("ORDER_ALREADY_CLAIMED");
        }
        tx.update(claimOrderRef, {
          driverId,
          driverName,
          status: "driver_accepted",
          dispatchOpen: false,
          dispatchTarget: "none",
          updatedAt: FieldValue.serverTimestamp(),
        });
        return driverId;
      });

    const claimResults = await Promise.allSettled([
      attemptClaim("audit_drv_a", "Tài xế A"),
      attemptClaim("audit_drv_b", "Tài xế B"),
    ]);
    assert.equal(claimResults.filter((r) => r.status === "fulfilled").length, 1);
    assert.equal(claimResults.filter((r) => r.status === "rejected").length, 1);

    // 7c. Two customers concurrently buying the LAST 1 unit (stock = 1) -> strictly 1 succeeds, stock == 0!
    const now = FieldValue.serverTimestamp();
    await db.collection("merchants").doc(merch.uid).set({
      merchantId: merch.uid,
      ownerUid: merch.uid,
      storeType: "SHOPPING",
      name: "Audit Flash Sale",
      description: "Limited edition",
      category: "Khuyến mãi",
      address: "Quận 1",
      phone: "0900123456",
      imageUrl: "",
      openingHours: "08:00 - 22:00",
      openNow: true,
      ratingTenths: 48,
      active: true,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("merchants").doc(merch.uid).collection("products").doc("last_item").set({
      merchantId: merch.uid,
      storeName: "Audit Flash Sale",
      serviceType: "SHOPPING",
      name: "Tai Nghe Giới Hạn",
      description: "Chỉ còn 1 chiếc",
      imageUrl: "",
      category: "Công nghệ",
      priceVnd: 150_000,
      stock: 1,
      active: true,
      createdAt: now,
      updatedAt: now,
    });

    const racePurchases = await Promise.allSettled([
      createVerifiedOrderAndPayDemoTx(db, cust1, {
        idempotencyKey: "audit_last_item_c1",
        serviceType: "SHOPPING",
        paymentMethod: "Tiền mặt DEMO",
        primaryAddress: "Quận 1",
        secondaryAddress: "Nhà Khách 1",
        merchantId: merch.uid,
        items: [{ itemId: "last_item", quantity: 1 }],
      }),
      createVerifiedOrderAndPayDemoTx(db, cust2, {
        idempotencyKey: "audit_last_item_c2",
        serviceType: "SHOPPING",
        paymentMethod: "Tiền mặt DEMO",
        primaryAddress: "Quận 1",
        secondaryAddress: "Nhà Khách 2",
        merchantId: merch.uid,
        items: [{ itemId: "last_item", quantity: 1 }],
      }),
    ]);
    assert.equal(racePurchases.filter((r) => r.status === "fulfilled").length, 1, "Only 1 buyer can purchase the last item");
    assert.equal(racePurchases.filter((r) => r.status === "rejected").length, 1, "Second buyer must be rejected out-of-stock");

    const lastItemSnap = await db.collection("merchants").doc(merch.uid).collection("products").doc("last_item").get();
    assert.equal(lastItemSnap.data().stock, 0, "Stock must be exactly 0 and never negative");
  });

  it("E2E 8 (Security Audit): Chặn token hết hạn/rỗng, thanh toán đơn người khác, hủy/hoàn trùng & quyết toán trái phép", async () => {
    const owner = { uid: "sec_owner", email: "sec_owner@fox.demo", emailVerified: true };
    const intruder = { uid: "sec_intruder", email: "sec_intruder@fox.demo", emailVerified: true };
    await seedUser(owner.uid, { displayName: "Chủ đơn", role: "customer" });
    await seedUser(intruder.uid, { displayName: "Kẻ xâm nhập", role: "customer" });

    // Null / expired auth context rejected across sensitive callables
    await assert.rejects(
      () => topUpDemoWalletTx(db, null, { amountVnd: 50_000, idempotencyKey: "sec_noauth_topup" }),
      (err) => err.code === "unauthenticated",
    );
    await assert.rejects(
      () => notifyOrderEventOp(db, { uid: "", email: null, emailVerified: false }, { orderId: "ORD-X", eventType: "cancelled" }),
      (err) => err.code === "unauthenticated",
    );

    await topUpDemoWalletTx(db, owner, { amountVnd: 200_000, idempotencyKey: "sec_owner_topup" });
    await topUpDemoWalletTx(db, intruder, { amountVnd: 200_000, idempotencyKey: "sec_intruder_topup" });

    const unpaid = await createVerifiedOrderAndPayDemoTx(db, owner, {
      idempotencyKey: "sec_unpaid_order",
      serviceType: "RIDE",
      vehicleType: "FOX Bike",
      distanceKm: 4,
      paymentMethod: "Tiền mặt DEMO",
      primaryAddress: "Q1",
      secondaryAddress: "Q3",
    });

    // Intruder cannot pay or cancel/refund owner's order, and cannot settle commission
    await assert.rejects(
      () => payOrderWithDemoWalletTx(db, intruder, { orderId: unpaid.orderId, idempotencyKey: "sec_pay_hijack" }),
      (err) => err.code === "permission-denied",
    );
    await assert.rejects(
      () => refundOrderDemoTx(db, intruder, { orderId: unpaid.orderId, idempotencyKey: "sec_ref_hijack" }),
      (err) => err.code === "permission-denied",
    );
    await assert.rejects(
      () => settleOrderCommissionDemoTx(db, intruder, { orderId: unpaid.orderId }),
      (err) => err.code === "permission-denied",
    );

    // Owner pays once -> second payment with different key is rejected
    await payOrderWithDemoWalletTx(db, owner, { orderId: unpaid.orderId, idempotencyKey: "sec_pay_valid_1" });
    await assert.rejects(
      () => payOrderWithDemoWalletTx(db, owner, { orderId: unpaid.orderId, idempotencyKey: "sec_pay_dup_2" }),
      (err) => err.code === "already-exists",
    );
  });
});
