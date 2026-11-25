const { describe, it, before, after } = require("node:test");
const assert = require("node:assert/strict");
const { initializeApp, deleteApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

const {
  topUpDemoWalletTx,
  createVerifiedOrderAndPayDemoTx,
  refundOrderDemoTx,
  getWalletSummaryOp,
} = require("../lib/financeService");
const { calculateAuthoritativeQuote } = require("../lib/pricing");
const { FOX_DATABASE_ID } = require("../lib/types");
const { seedDemoCatalog } = require("../../scripts/seed-demo-catalog");

const PROJECT_ID = "gen-lang-client-0957111252";
const EMULATOR_HOST = process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085";

const AUTH_CUST_1 = { uid: "cat_cust_1", email: "cust1@fox.test", emailVerified: true };
const AUTH_CUST_2 = { uid: "cat_cust_2", email: "cust2@fox.test", emailVerified: true };
const AUTH_MERCH_A = { uid: "demo_rest_pho_fox", email: "pho@fox.test", emailVerified: true };
const AUTH_MERCH_B = { uid: "demo_rest_com_tam_fox", email: "comtam@fox.test", emailVerified: true };

let app;
let db;

async function clearEmulatorDb() {
  const url = `http://${EMULATOR_HOST}/emulator/v1/projects/${PROJECT_ID}/databases/${FOX_DATABASE_ID}/documents`;
  const res = await fetch(url, { method: "DELETE" });
  if (!res.ok) {
    throw new Error(`Failed to clear Firestore emulator: ${res.status} ${await res.text()}`);
  }
}

describe("FOX Cloud Catalog, Merchant Stock & Concurrency Tests (Firestore Emulator)", () => {
  before(async () => {
    process.env.FIRESTORE_EMULATOR_HOST = EMULATOR_HOST;
    app = initializeApp({ projectId: PROJECT_ID }, "fox-catalog-test-app");
    db = getFirestore(app, FOX_DATABASE_ID);
    await clearEmulatorDb();

    const now = FieldValue.serverTimestamp();
    await Promise.all([
      db.collection("users").doc(AUTH_CUST_1.uid).set({
        userId: AUTH_CUST_1.uid,
        displayName: "Khách Hàng 1",
        email: AUTH_CUST_1.email,
        role: "customer",
        accountStatus: "active",
        createdAt: now,
        updatedAt: now,
      }),
      db.collection("users").doc(AUTH_CUST_2.uid).set({
        userId: AUTH_CUST_2.uid,
        displayName: "Khách Hàng 2",
        email: AUTH_CUST_2.email,
        role: "customer",
        accountStatus: "active",
        createdAt: now,
        updatedAt: now,
      }),
    ]);

    // Fund both customers with DEMO wallets
    await topUpDemoWalletTx(db, AUTH_CUST_1, { amountVnd: 1_000_000, idempotencyKey: "cat_seed_topup_1" });
    await topUpDemoWalletTx(db, AUTH_CUST_2, { amountVnd: 1_000_000, idempotencyKey: "cat_seed_topup_2" });
  });

  after(async () => {
    if (app) await deleteApp(app);
  });

  it("1. Script seed 2 nhà hàng + 2 gian hàng DEMO chạy lại an toàn, không trùng dữ liệu", async () => {
    const run1 = await seedDemoCatalog(db);
    const run2 = await seedDemoCatalog(db);

    assert.equal(run1.storesUpserted, 4);
    assert.equal(run2.storesUpserted, 4);

    const storesSnap = await db.collection("merchants").get();
    assert.equal(storesSnap.size, 4, "Must have strictly 4 merchants (2 FOOD + 2 SHOPPING), zero duplicates");

    const foodStores = storesSnap.docs.filter((d) => d.data().storeType === "FOOD");
    const shopStores = storesSnap.docs.filter((d) => d.data().storeType === "SHOPPING");
    assert.equal(foodStores.length, 2);
    assert.equal(shopStores.length, 2);

    let totalProducts = 0;
    for (const storeDoc of storesSnap.docs) {
      const prodSnap = await storeDoc.ref.collection("products").get();
      totalProducts += prodSnap.size;
    }
    assert.equal(totalProducts, 16, "Must have strictly 16 unique products across 4 stores");
  });

  it("2. Khách đọc danh mục Cloud, giá khớp server & tự động trừ tồn kho trong Transaction", async () => {
    const prodRef = db
      .collection("merchants")
      .doc("demo_rest_pho_fox")
      .collection("products")
      .doc("pho_tai_lan");
    const beforeSnap = await prodRef.get();
    const initialStock = beforeSnap.data().stock;
    const unitPrice = beforeSnap.data().priceVnd;
    assert.equal(unitPrice, 65_000);

    // Quote from Cloud catalog: 2 x 65_000 = 130_000, fee = 15_000, FOXFOOD30 = 30% -> 30_000 max -> total = 115_000
    const quote = await calculateAuthoritativeQuote(db, {
      serviceType: "FOOD",
      merchantId: "demo_rest_pho_fox",
      items: [{ itemId: "pho_tai_lan", quantity: 2 }],
      voucherCode: "FOXFOOD30",
    });
    assert.equal(quote.subtotalVnd, 130_000);
    assert.equal(quote.feeVnd, 15_000);
    assert.equal(quote.discountVnd, 30_000);
    assert.equal(quote.totalVnd, 115_000);

    const created = await createVerifiedOrderAndPayDemoTx(db, AUTH_CUST_1, {
      idempotencyKey: "cat_food_order_001",
      serviceType: "FOOD",
      merchantId: "demo_rest_pho_fox",
      items: [{ itemId: "pho_tai_lan", quantity: 2 }],
      voucherCode: "FOXFOOD30",
      expectedTotalVnd: 115_000,
      paymentMethod: "Ví FOX DEMO",
      primaryAddress: "42 Lê Thánh Tôn, Quận 1",
      secondaryAddress: "123 Nguyễn Huệ, Quận 1",
    });

    assert.equal(created.merchantId, "demo_rest_pho_fox");
    assert.equal(created.quote.totalVnd, 115_000);
    assert.equal(created.stockDeducted, true);
    assert.equal(created.paymentStatus, "paid_demo");

    const afterSnap = await prodRef.get();
    assert.equal(afterSnap.data().stock, initialStock - 2, "Stock must decrease atomically by 2");
  });

  it("3. Chặn đặt vượt tồn kho, chặn sản phẩm đã ẩn và chặn gian hàng đang đóng cửa", async () => {
    const techStoreRef = db.collection("merchants").doc("demo_shop_tech_fox");
    const headphoneRef = techStoreRef.collection("products").doc("tai_nghe_anc_fox");
    await headphoneRef.update({ stock: 2, active: true });

    // Exceeds stock (request 5 when only 2 left) -> rejected
    await assert.rejects(
      () =>
        createVerifiedOrderAndPayDemoTx(db, AUTH_CUST_1, {
          idempotencyKey: "cat_overstock_001",
          serviceType: "SHOPPING",
          merchantId: "demo_shop_tech_fox",
          items: [{ itemId: "tai_nghe_anc_fox", quantity: 5 }],
          paymentMethod: "Ví FOX DEMO",
          primaryAddress: "68 Nguyễn Trãi, Q5",
          secondaryAddress: "Quận 1",
        }),
      (err) => err.code === "failed-precondition" && /không đủ tồn kho/i.test(err.message),
    );

    // Duplicate itemId split attack (2 + 2 = 4 > 2 stock) -> rejected
    await assert.rejects(
      () =>
        createVerifiedOrderAndPayDemoTx(db, AUTH_CUST_1, {
          idempotencyKey: "cat_split_attack_001",
          serviceType: "SHOPPING",
          merchantId: "demo_shop_tech_fox",
          items: [
            { itemId: "tai_nghe_anc_fox", quantity: 2 },
            { itemId: "tai_nghe_anc_fox", quantity: 2 },
          ],
          paymentMethod: "Ví FOX DEMO",
          primaryAddress: "68 Nguyễn Trãi, Q5",
          secondaryAddress: "Quận 1",
        }),
      (err) => err.code === "failed-precondition" && /không đủ tồn kho/i.test(err.message),
    );

    // Hidden product -> rejected
    await headphoneRef.update({ active: false });
    await assert.rejects(
      () =>
        createVerifiedOrderAndPayDemoTx(db, AUTH_CUST_1, {
          idempotencyKey: "cat_hidden_prod_001",
          serviceType: "SHOPPING",
          merchantId: "demo_shop_tech_fox",
          items: [{ itemId: "tai_nghe_anc_fox", quantity: 1 }],
          paymentMethod: "Tiền mặt DEMO",
          primaryAddress: "68 Nguyễn Trãi, Q5",
          secondaryAddress: "Quận 1",
        }),
      (err) => err.code === "failed-precondition" && /ẩn|ngừng bán/i.test(err.message),
    );
    await headphoneRef.update({ active: true });

    // Closed store -> rejected
    await techStoreRef.update({ openNow: false });
    await assert.rejects(
      () =>
        createVerifiedOrderAndPayDemoTx(db, AUTH_CUST_1, {
          idempotencyKey: "cat_closed_store_001",
          serviceType: "SHOPPING",
          merchantId: "demo_shop_tech_fox",
          items: [{ itemId: "tai_nghe_anc_fox", quantity: 1 }],
          paymentMethod: "Tiền mặt DEMO",
          primaryAddress: "68 Nguyễn Trãi, Q5",
          secondaryAddress: "Quận 1",
        }),
      (err) => err.code === "failed-precondition" && /đóng cửa/i.test(err.message),
    );
    await techStoreRef.update({ openNow: true });
  });

  it("4. Chặn sửa giá trên sản phẩm Cloud (tampered expectedTotalVnd)", async () => {
    await assert.rejects(
      () =>
        createVerifiedOrderAndPayDemoTx(db, AUTH_CUST_1, {
          idempotencyKey: "cat_price_tamper_001",
          serviceType: "SHOPPING",
          merchantId: "demo_shop_fresh_fox",
          items: [{ itemId: "combo_trai_cay_2kg", quantity: 1 }], // 185_000 + 22_000 = 207_000
          expectedTotalVnd: 10_000,
          paymentMethod: "Tiền mặt DEMO",
          primaryAddress: "215 Hai Bà Trưng, Q1",
          secondaryAddress: "Quận 1",
        }),
      (err) => err.code === "failed-precondition" && /không khớp/i.test(err.message),
    );
  });

  it("5. Hai khách hàng đặt đồng thời (Promise.all) vượt tồn kho — chỉ 1 đơn thành công, tồn kho không âm", async () => {
    const prodRef = db
      .collection("merchants")
      .doc("demo_shop_tech_fox")
      .collection("products")
      .doc("binh_giu_nhiet_750ml");

    // Set exact stock = 3; Customer 1 and Customer 2 simultaneously order 2 units each (total demand = 4 > 3)
    await prodRef.update({ stock: 3, active: true });

    const results = await Promise.allSettled([
      createVerifiedOrderAndPayDemoTx(db, AUTH_CUST_1, {
        idempotencyKey: "cat_race_cust1_001",
        serviceType: "SHOPPING",
        merchantId: "demo_shop_tech_fox",
        items: [{ itemId: "binh_giu_nhiet_750ml", quantity: 2 }],
        paymentMethod: "Ví FOX DEMO",
        primaryAddress: "68 Nguyễn Trãi, Q5",
        secondaryAddress: "Quận 1",
      }),
      createVerifiedOrderAndPayDemoTx(db, AUTH_CUST_2, {
        idempotencyKey: "cat_race_cust2_001",
        serviceType: "SHOPPING",
        merchantId: "demo_shop_tech_fox",
        items: [{ itemId: "binh_giu_nhiet_750ml", quantity: 2 }],
        paymentMethod: "Ví FOX DEMO",
        primaryAddress: "68 Nguyễn Trãi, Q5",
        secondaryAddress: "Quận 3",
      }),
    ]);

    const succeeded = results.filter((r) => r.status === "fulfilled");
    const failed = results.filter((r) => r.status === "rejected");

    assert.equal(succeeded.length, 1, "Strictly ONE concurrent order can succeed when stock=3 and each requests 2");
    assert.equal(failed.length, 1, "Second concurrent order must fail cleanly due to insufficient stock");

    const afterStock = (await prodRef.get()).data().stock;
    assert.equal(afterStock, 1, "Remaining stock must be strictly 1 (3 - 2 = 1, never negative)");
  });

  it("6. Hủy đơn hợp lệ hoàn tiền Ví DEMO + hoàn tồn kho chính xác 1 lần & chặn gian hàng B hủy đơn gian hàng A", async () => {
    const prodRef = db
      .collection("merchants")
      .doc("demo_rest_pho_fox")
      .collection("products")
      .doc("pho_dac_biet");

    await prodRef.update({ stock: 10, active: true });
    const walletBefore = (await getWalletSummaryOp(db, AUTH_CUST_2)).balanceVnd;

    const order = await createVerifiedOrderAndPayDemoTx(db, AUTH_CUST_2, {
      idempotencyKey: "cat_cancel_restore_001",
      serviceType: "FOOD",
      merchantId: "demo_rest_pho_fox",
      items: [{ itemId: "pho_dac_biet", quantity: 3 }], // 3 * 75_000 = 225_000 + 15_000 = 240_000
      paymentMethod: "Ví FOX DEMO",
      primaryAddress: "42 Lê Thánh Tôn, Q1",
      secondaryAddress: "Quận 3",
    });

    assert.equal((await prodRef.get()).data().stock, 7);

    // Merchant B (different merchant!) CANNOT cancel/refund Merchant A's order
    await assert.rejects(
      () =>
        refundOrderDemoTx(db, AUTH_MERCH_B, {
          orderId: order.orderId,
          idempotencyKey: "cat_merch_b_attack",
        }),
      (err) => err.code === "permission-denied",
    );

    // Valid cancellation by Merchant A (or customer while pending) refunds DEMO wallet AND restores stock to 10!
    const refundRes = await refundOrderDemoTx(db, AUTH_MERCH_A, {
      orderId: order.orderId,
      idempotencyKey: "cat_cancel_ok_001",
      reason: "Khách đổi ý trước khi chế biến",
    });

    assert.equal(refundRes.orderStatus, "cancelled");
    assert.equal(refundRes.refundedAmountVnd, 240_000);
    assert.equal(refundRes.stockRestored, true);
    assert.equal((await prodRef.get()).data().stock, 10, "Stock must be atomically restored back to 10");
    assert.equal((await getWalletSummaryOp(db, AUTH_CUST_2)).balanceVnd, walletBefore);

    // Replaying or calling refund again with a new key MUST NOT restore stock twice!
    await assert.rejects(
      () =>
        refundOrderDemoTx(db, AUTH_CUST_2, {
          orderId: order.orderId,
          idempotencyKey: "cat_cancel_double_restore_attack",
        }),
      (err) => err.code === "already-exists",
    );
    assert.equal((await prodRef.get()).data().stock, 10, "Stock must remain strictly 10 (no double restore)");
  });
});
