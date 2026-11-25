const { describe, it, before, after } = require("node:test");
const assert = require("node:assert/strict");
const { initializeApp, deleteApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

const {
  topUpDemoWalletTx,
  createVerifiedOrderAndPayDemoTx,
  payOrderWithDemoWalletTx,
  refundOrderDemoTx,
  settleOrderCommissionDemoTx,
  getWalletSummaryOp,
  getAdminFinancialOverviewOp,
} = require("../lib/financeService");
const { calculateAuthoritativeQuote, evaluateVoucher } = require("../lib/pricing");
const { FOX_DATABASE_ID, BOOTSTRAPPED_ADMIN_EMAIL } = require("../lib/types");

const PROJECT_ID = "gen-lang-client-0957111252";
const EMULATOR_HOST = process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085";

const AUTH_CUST_A = { uid: "fin_cust_a", email: "custa@fox.test", emailVerified: true };
const AUTH_CUST_B = { uid: "fin_cust_b", email: "custb@fox.test", emailVerified: true };
const AUTH_DRIVER = { uid: "fin_driver_1", email: "driver1@fox.test", emailVerified: true };
const AUTH_MERCHANT = { uid: "fin_merchant_1", email: "merchant1@fox.test", emailVerified: true };
const AUTH_ADMIN = { uid: "fin_admin_1", email: BOOTSTRAPPED_ADMIN_EMAIL, emailVerified: true };
const AUTH_SPOOF_ADMIN = { uid: "fin_spoof", email: BOOTSTRAPPED_ADMIN_EMAIL, emailVerified: false };

let app;
let db;

async function clearEmulatorDb() {
  const url = `http://${EMULATOR_HOST}/emulator/v1/projects/${PROJECT_ID}/databases/${FOX_DATABASE_ID}/documents`;
  const res = await fetch(url, { method: "DELETE" });
  if (!res.ok) {
    throw new Error(`Failed to clear Firestore emulator: ${res.status} ${await res.text()}`);
  }
}

async function seedUsers() {
  const now = FieldValue.serverTimestamp();
  const baseUser = (uid, displayName, role, extra = {}) => ({
    userId: uid,
    displayName,
    email: `${uid}@fox.test`,
    phone: "0900000000",
    defaultAddress: "Quận 1, TP.HCM",
    role,
    requestedRole: "none",
    driverStatus: role === "driver" ? "approved" : "none",
    merchantStatus: role === "merchant" ? "approved" : "none",
    accountStatus: "active",
    vehicleType: role === "driver" ? "FOX Bike" : "",
    vehiclePlate: role === "driver" ? "59X1-12345" : "",
    storeName: role === "merchant" ? "Phở Thìn FOX" : "",
    storeCategory: role === "merchant" ? "Đồ ăn" : "",
    driverOnline: role === "driver",
    createdAt: now,
    updatedAt: now,
    ...extra,
  });

  await Promise.all([
    db.collection("users").doc(AUTH_CUST_A.uid).set(baseUser(AUTH_CUST_A.uid, "Khách A", "customer")),
    db.collection("users").doc(AUTH_CUST_B.uid).set(baseUser(AUTH_CUST_B.uid, "Khách B", "customer")),
    db.collection("users").doc(AUTH_DRIVER.uid).set(baseUser(AUTH_DRIVER.uid, "Tài xế 1", "driver")),
    db.collection("users").doc(AUTH_MERCHANT.uid).set(baseUser(AUTH_MERCHANT.uid, "Gian hàng 1", "merchant")),
    db.collection("users").doc(AUTH_ADMIN.uid).set(baseUser(AUTH_ADMIN.uid, "Admin FOX", "admin")),
    db.collection("users").doc(AUTH_SPOOF_ADMIN.uid).set(baseUser(AUTH_SPOOF_ADMIN.uid, "Spoof Admin", "customer")),
  ]);
}

describe("FOX Cloud Functions v2 Financial Engine (Firestore Emulator)", () => {
  before(async () => {
    process.env.FIRESTORE_EMULATOR_HOST = EMULATOR_HOST;
    app = initializeApp({ projectId: PROJECT_ID }, "fox-finance-test-app");
    db = getFirestore(app, FOX_DATABASE_ID);
    await clearEmulatorDb();
    await seedUsers();
  });

  after(async () => {
    if (app) {
      await deleteApp(app);
    }
  });

  it("1. Thanh toán thành công + kiểm tra số nguyên VND & sổ cái nhất quán", async () => {
    const topup = await topUpDemoWalletTx(db, AUTH_CUST_A, {
      amountVnd: 200_000,
      idempotencyKey: "topup_success_001",
    });
    assert.equal(topup.balanceAfterVnd, 200_000);
    assert.equal(topup.idempotentReplay, false);
    assert.equal(topup.demoNotice, "DEMO — KHÔNG PHẢI TIỀN THẬT");

    // FOX Bike 5km: 12_000 + 4_500*5 = 34_500; voucher FOXRIDE25 -> 25% = 8_625 -> total = 25_875
    const orderRes = await createVerifiedOrderAndPayDemoTx(db, AUTH_CUST_A, {
      idempotencyKey: "order_ride_ok_001",
      serviceType: "RIDE",
      vehicleType: "FOX Bike",
      distanceKm: 5,
      voucherCode: "FOXRIDE25",
      paymentMethod: "Ví FOX DEMO",
      primaryAddress: "Bến Thành, Q1",
      secondaryAddress: "Sân bay Tân Sơn Nhất",
    });

    assert.equal(orderRes.quote.subtotalVnd, 34_500);
    assert.equal(orderRes.quote.discountVnd, 8_625);
    assert.equal(orderRes.quote.totalVnd, 25_875);
    assert.equal(orderRes.paymentStatus, "paid_demo");
    assert.equal(orderRes.walletBalanceAfterVnd, 200_000 - 25_875);

    const summary = await getWalletSummaryOp(db, AUTH_CUST_A);
    assert.equal(summary.balanceVnd, 174_125);
    assert.equal(summary.totalTopUpVnd, 200_000);
    assert.equal(summary.totalSpentVnd, 25_875);
    assert.equal(summary.transactions.length, 2);
  });

  it("2. Không đủ số dư — từ chối giao dịch, không cho số dư âm", async () => {
    // Customer B has 0 VND balance
    await assert.rejects(
      () =>
        createVerifiedOrderAndPayDemoTx(db, AUTH_CUST_B, {
          idempotencyKey: "order_insufficient_001",
          serviceType: "RIDE",
          vehicleType: "FOX Car 4",
          distanceKm: 10,
          paymentMethod: "Ví FOX DEMO",
          primaryAddress: "Quận 1",
          secondaryAddress: "Quận 7",
        }),
      (err) => err.code === "failed-precondition" && /không đủ/i.test(err.message),
    );

    const summaryB = await getWalletSummaryOp(db, AUTH_CUST_B);
    assert.equal(summaryB.balanceVnd, 0);
    assert.equal(summaryB.transactions.length, 0);
  });

  it("3. Gửi yêu cầu trùng (idempotencyKey) — không trừ tiền hoặc nạp tiền hai lần", async () => {
    const beforeSummary = await getWalletSummaryOp(db, AUTH_CUST_A);

    // Replay same topup key
    const replayTopup = await topUpDemoWalletTx(db, AUTH_CUST_A, {
      amountVnd: 200_000,
      idempotencyKey: "topup_success_001",
    });
    assert.equal(replayTopup.idempotentReplay, true);

    // Replay same create order & pay key
    const replayOrder = await createVerifiedOrderAndPayDemoTx(db, AUTH_CUST_A, {
      idempotencyKey: "order_ride_ok_001",
      serviceType: "RIDE",
      vehicleType: "FOX Bike",
      distanceKm: 5,
      voucherCode: "FOXRIDE25",
      paymentMethod: "Ví FOX DEMO",
      primaryAddress: "Bến Thành, Q1",
      secondaryAddress: "Sân bay Tân Sơn Nhất",
    });
    assert.equal(replayOrder.idempotentReplay, true);

    const afterSummary = await getWalletSummaryOp(db, AUTH_CUST_A);
    assert.equal(afterSummary.balanceVnd, beforeSummary.balanceVnd);
    assert.equal(afterSummary.transactions.length, beforeSummary.transactions.length);
  });

  it("4. Hai yêu cầu đồng thời — chặn trừ tiền trùng và chặn chi tiêu vượt số dư", async () => {
    // Top-up Customer B to exactly 60_000 VND
    await topUpDemoWalletTx(db, AUTH_CUST_B, {
      amountVnd: 60_000,
      idempotencyKey: "topup_b_race_001",
    });

    // 4a. Create 1 unpaid cash order (34_500 VND), then fire TWO concurrent wallet pay requests with DIFFERENT idempotency keys
    const cashOrder = await createVerifiedOrderAndPayDemoTx(db, AUTH_CUST_B, {
      idempotencyKey: "order_b_cash_001",
      serviceType: "RIDE",
      vehicleType: "FOX Bike",
      distanceKm: 5,
      paymentMethod: "Tiền mặt DEMO",
      primaryAddress: "Quận 3",
      secondaryAddress: "Quận 10",
    });
    assert.equal(cashOrder.paymentStatus, "pending_cash_demo");

    const payAttempts = await Promise.allSettled([
      payOrderWithDemoWalletTx(db, AUTH_CUST_B, {
        orderId: cashOrder.orderId,
        idempotencyKey: "race_pay_key_1",
      }),
      payOrderWithDemoWalletTx(db, AUTH_CUST_B, {
        orderId: cashOrder.orderId,
        idempotencyKey: "race_pay_key_2",
      }),
    ]);

    const fulfilledPays = payAttempts.filter((r) => r.status === "fulfilled");
    const rejectedPays = payAttempts.filter((r) => r.status === "rejected");
    assert.equal(fulfilledPays.length, 1, "Exactly ONE concurrent pay attempt must succeed");
    assert.equal(rejectedPays.length, 1, "Second concurrent pay attempt must fail");

    const midSummaryB = await getWalletSummaryOp(db, AUTH_CUST_B);
    // 60_000 - 34_500 = 25_500 remaining
    assert.equal(midSummaryB.balanceVnd, 25_500);

    // 4b. Fire TWO concurrent 20_000 VND delivery orders when only 25_500 VND remains -> strictly 1 succeeds, balance never negative!
    const concurrentOrders = await Promise.allSettled([
      createVerifiedOrderAndPayDemoTx(db, AUTH_CUST_B, {
        idempotencyKey: "race_order_b_1",
        serviceType: "DELIVERY",
        weightTier: "Dưới 2 kg",
        speed: "Tiêu chuẩn",
        paymentMethod: "Ví FOX DEMO",
        primaryAddress: "Q1",
        secondaryAddress: "Q2",
      }),
      createVerifiedOrderAndPayDemoTx(db, AUTH_CUST_B, {
        idempotencyKey: "race_order_b_2",
        serviceType: "DELIVERY",
        weightTier: "Dưới 2 kg",
        speed: "Tiêu chuẩn",
        paymentMethod: "Ví FOX DEMO",
        primaryAddress: "Q1",
        secondaryAddress: "Q3",
      }),
    ]);

    const okOrders = concurrentOrders.filter((r) => r.status === "fulfilled");
    const failOrders = concurrentOrders.filter((r) => r.status === "rejected");
    assert.equal(okOrders.length, 1, "Only ONE order fits inside 25_500 VND balance");
    assert.equal(failOrders.length, 1, "Second concurrent order must fail due to insufficient balance");

    const finalSummaryB = await getWalletSummaryOp(db, AUTH_CUST_B);
    assert.equal(finalSummaryB.balanceVnd, 5_500);
    assert.ok(finalSummaryB.balanceVnd >= 0);
  });

  it("5. Hoàn tiền hai lần — hoàn đúng 1 lần, chặn hoàn trùng (cả trùng key và khác key)", async () => {
    const order = await createVerifiedOrderAndPayDemoTx(db, AUTH_CUST_A, {
      idempotencyKey: "order_refund_test_001",
      serviceType: "DELIVERY",
      weightTier: "Dưới 2 kg",
      speed: "Nhanh", // 32_000 VND
      paymentMethod: "Ví FOX DEMO",
      primaryAddress: "Quận 1",
      secondaryAddress: "Quận 4",
    });
    const balanceAfterPay = order.walletBalanceAfterVnd;

    // First valid refund
    const refund1 = await refundOrderDemoTx(db, AUTH_CUST_A, {
      orderId: order.orderId,
      idempotencyKey: "refund_key_001",
    });
    assert.equal(refund1.refundedAmountVnd, 32_000);
    assert.equal(refund1.customerBalanceAfterVnd, balanceAfterPay + 32_000);
    assert.equal(refund1.refundStatus, "refunded_demo");
    assert.equal(refund1.idempotentReplay, false);

    // Replay with SAME key -> idempotent replay, NO second credit
    const refundReplay = await refundOrderDemoTx(db, AUTH_CUST_A, {
      orderId: order.orderId,
      idempotencyKey: "refund_key_001",
    });
    assert.equal(refundReplay.idempotentReplay, true);

    // Attempt second refund with DIFFERENT key -> REJECTED!
    await assert.rejects(
      () =>
        refundOrderDemoTx(db, AUTH_CUST_A, {
          orderId: order.orderId,
          idempotencyKey: "refund_key_002_attack",
        }),
      (err) => err.code === "already-exists",
    );

    const summaryAfter = await getWalletSummaryOp(db, AUTH_CUST_A);
    assert.equal(summaryAfter.balanceVnd, balanceAfterPay + 32_000);
  });

  it("6. Voucher không hợp lệ — bị từ chối khi sai mã, sai dịch vụ hoặc chưa đạt tối thiểu", async () => {
    const fakeEval = evaluateVoucher("RIDE", 50_000, "FAKE99");
    assert.equal(fakeEval.valid, false);

    const wrongServiceEval = evaluateVoucher("FOOD", 100_000, "FOXRIDE25");
    assert.equal(wrongServiceEval.valid, false);

    const lowSubtotalEval = evaluateVoucher("FOOD", 65_000, "FOXFOOD30");
    assert.equal(lowSubtotalEval.valid, false);

    await assert.rejects(
      () =>
        createVerifiedOrderAndPayDemoTx(db, AUTH_CUST_A, {
          idempotencyKey: "order_bad_voucher_001",
          serviceType: "FOOD",
          items: [{ itemId: "f1", quantity: 1 }], // 65_000 < 80_000 min
          voucherCode: "FOXFOOD30",
          paymentMethod: "Tiền mặt DEMO",
          primaryAddress: "Phở Thìn",
          secondaryAddress: "Quận 1",
        }),
      (err) => err.code === "invalid-argument" && /tối thiểu 80\.000/i.test(err.message),
    );
  });

  it("7. Người dùng sửa giá / gửi số thập phân — bị server phát hiện và từ chối", async () => {
    // 7a. Client claims tampered expectedTotalVnd = 1_000 on a 130_000+ FOOD order
    await assert.rejects(
      () =>
        calculateAuthoritativeQuote(db, {
          serviceType: "FOOD",
          items: [{ itemId: "f1", quantity: 2 }], // 2 * 65_000 = 130_000 + 15_000 fee = 145_000
          expectedTotalVnd: 1_000,
        }),
      (err) => err.code === "failed-precondition" && /không khớp/i.test(err.message),
    );

    // 7b. Client injects fake subtotalVnd/totalVnd fields -> server ignores them and computes exact 145_000
    const created = await createVerifiedOrderAndPayDemoTx(db, AUTH_CUST_A, {
      idempotencyKey: "order_tamper_ignore_001",
      serviceType: "FOOD",
      items: [{ itemId: "f1", quantity: 2 }],
      paymentMethod: "Tiền mặt DEMO",
      primaryAddress: "Phở Thìn",
      secondaryAddress: "Quận 1",
      // Malicious injected client fields:
      subtotalVnd: 1,
      totalVnd: 1,
      discountVnd: 999_999,
    });
    assert.equal(created.quote.subtotalVnd, 130_000);
    assert.equal(created.quote.feeVnd, 15_000);
    assert.equal(created.quote.totalVnd, 145_000);

    // 7c. Non-integer Double top-up rejected
    await assert.rejects(
      () =>
        topUpDemoWalletTx(db, AUTH_CUST_A, {
          amountVnd: 50_000.75,
          idempotencyKey: "topup_float_attack",
        }),
      (err) => err.code === "invalid-argument",
    );
  });

  it("8. Hoa hồng tài xế, hoa hồng gian hàng, doanh thu FOX DEMO & chống chi trùng", async () => {
    // Create FOOD order: 2 x f1 (130_000) + fee 15_000 = 145_000
    const foodOrder = await createVerifiedOrderAndPayDemoTx(db, AUTH_CUST_A, {
      idempotencyKey: "order_food_comm_001",
      serviceType: "FOOD",
      items: [{ itemId: "f1", quantity: 2 }],
      paymentMethod: "Ví FOX DEMO",
      primaryAddress: "Phở Thìn FOX",
      secondaryAddress: "123 Nguyễn Huệ, Q1",
    });

    // Simulate merchant + driver completing the order
    await db.collection("orders").doc(foodOrder.orderId).update({
      merchantId: AUTH_MERCHANT.uid,
      merchantName: "Phở Thìn FOX",
      driverId: AUTH_DRIVER.uid,
      driverName: "Tài xế 1",
      status: "completed",
      dispatchOpen: false,
      dispatchTarget: "none",
    });

    // Fire TWO concurrent settlement calls -> must credit Driver & Merchant strictly ONCE!
    const [s1, s2] = await Promise.all([
      settleOrderCommissionDemoTx(db, AUTH_DRIVER, { orderId: foodOrder.orderId }),
      settleOrderCommissionDemoTx(db, AUTH_ADMIN, { orderId: foodOrder.orderId }),
    ]);

    // Merchant = 85% of 130_000 = 110_500; Driver = 80% of 15_000 = 12_000; FOX = 145_000 - 110_500 - 12_000 = 22_500
    assert.equal(s1.merchantCommissionVnd, 110_500);
    assert.equal(s1.driverCommissionVnd, 12_000);
    assert.equal(s1.platformRevenueVnd, 22_500);
    assert.equal(s1.merchantCommissionVnd + s1.driverCommissionVnd + s1.platformRevenueVnd, 145_000);
    assert.equal(s1.idempotentReplay || s2.idempotentReplay, true);

    const driverWallet = await getWalletSummaryOp(db, AUTH_DRIVER);
    const merchantWallet = await getWalletSummaryOp(db, AUTH_MERCHANT);
    assert.equal(driverWallet.balanceVnd, 12_000, "Driver must be credited strictly ONCE");
    assert.equal(merchantWallet.balanceVnd, 110_500, "Merchant must be credited strictly ONCE");

    // Verified admin can inspect FOX DEMO platform revenue
    const overview = await getAdminFinancialOverviewOp(db, AUTH_ADMIN);
    assert.equal(overview.settledOrdersCount, 1);
    assert.equal(overview.totalPlatformRevenueVnd, 22_500);
    assert.equal(overview.totalDriverCommissionVnd, 12_000);
    assert.equal(overview.totalMerchantCommissionVnd, 110_500);
  });

  it("9. Người dùng giả mạo admin bị từ chối quyền quản trị tài chính", async () => {
    await assert.rejects(
      () => getAdminFinancialOverviewOp(db, AUTH_SPOOF_ADMIN),
      (err) => err.code === "permission-denied",
    );

    await assert.rejects(
      () => getAdminFinancialOverviewOp(db, AUTH_CUST_A),
      (err) => err.code === "permission-denied",
    );

    await assert.rejects(
      () => getAdminFinancialOverviewOp(db, null),
      (err) => err.code === "unauthenticated",
    );
  });
});
