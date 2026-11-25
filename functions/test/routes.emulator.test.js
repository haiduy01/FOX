const test = require("node:test");
const assert = require("node:assert/strict");
const { getApps, initializeApp, deleteApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");

const { computeVerifiedRouteOp } = require("../lib/routeService");
const { createVerifiedOrderAndPayDemoTx, topUpDemoWalletTx } = require("../lib/financeService");
const { FOX_DATABASE_ID } = require("../lib/types");

const PROJECT_ID = process.env.GCLOUD_PROJECT || "gen-lang-client-0957111252";

// District 1 Ben Thanh -> Tan Son Nhat Airport (HCMC)
const BEN_THANH = { lat: 10.7721, lng: 106.6983 };
const TAN_SON_NHAT = { lat: 10.8185, lng: 106.6588 };

if (!process.env.FIRESTORE_EMULATOR_HOST) {
  console.error("ERROR: FIRESTORE_EMULATOR_HOST must be set (e.g. 127.0.0.1:8085)");
  process.exit(1);
}

let app;
let db;

test.describe("FOX Backend Routes, Distance Verification & Anti-Tampering (Firestore Emulator)", () => {
  test.before(async () => {
    app = getApps().length > 0 ? getApps()[0] : initializeApp({ projectId: PROJECT_ID }, "fox-routes-test");
    db = getFirestore(app, FOX_DATABASE_ID);
  });

  test.after(async () => {
    if (app) await deleteApp(app);
  });

  test("1. Backend computeVerifiedRoute tính khoảng cách/ETA xác thực & gắn cờ DEMO minh bạch khi thiếu API key", async () => {
    const route = await computeVerifiedRouteOp({
      origin: BEN_THANH,
      destination: TAN_SON_NHAT,
      vehicleType: "FOX Car 4",
    });

    assert.equal(route.serverVerified, true);
    assert.equal(route.isDemoRoute, true);
    assert.equal(route.routeSource, "server_haversine_demo_estimate");
    assert.ok(route.distanceMeters > 5000 && route.distanceMeters < 15000, `Unexpected meters: ${route.distanceMeters}`);
    assert.ok(route.distanceKm >= 6 && route.distanceKm <= 12, `Unexpected km: ${route.distanceKm}`);
    assert.ok(route.durationMinutes >= 10, `Unexpected ETA mins: ${route.durationMinutes}`);
    assert.ok(route.encodedPolyline.length > 4);
  });

  test("2. Từ chối tọa độ không hợp lệ (NaN, ngoài phạm vi hoặc quá gần < 20m)", async () => {
    await assert.rejects(
      () =>
        computeVerifiedRouteOp({
          origin: { lat: 99.0, lng: 106.7 },
          destination: TAN_SON_NHAT,
        }),
      /phạm vi/i,
    );
    await assert.rejects(
      () =>
        computeVerifiedRouteOp({
          origin: BEN_THANH,
          destination: BEN_THANH,
        }),
      /quá gần/i,
    );
  });

  test("3. Đặt xe RIDE kèm tọa độ: server tự tính quãng đường & giá cước VND nguyên, lưu route metadata lên Firestore", async () => {
    const uid = `route_ride_cust_${Date.now()}`;
    const auth = { uid, email: "ride_route@test.local", emailVerified: true };

    await topUpDemoWalletTx(db, auth, { amountVnd: 500_000, idempotencyKey: `topup_${uid}` });
    const route = await computeVerifiedRouteOp({
      origin: BEN_THANH,
      destination: TAN_SON_NHAT,
      vehicleType: "FOX Car 4",
    });

    // Expected: 24,000 + 11,000 * distanceKm, voucher FOXRIDE25 = min(25%, 30,000)
    const rawSubtotal = 24_000 + 11_000 * route.distanceKm;
    const expectedDiscount = Math.min(Math.floor((rawSubtotal * 2500) / 10000), 30_000);
    const expectedTotal = rawSubtotal - expectedDiscount;

    const created = await createVerifiedOrderAndPayDemoTx(db, auth, {
      idempotencyKey: `ride_route_${uid}`,
      serviceType: "RIDE",
      paymentMethod: "Ví FOX DEMO",
      primaryAddress: "Chợ Bến Thành, Q.1",
      secondaryAddress: "Sân bay Tân Sơn Nhất",
      vehicleType: "FOX Car 4",
      pickupCoords: BEN_THANH,
      dropoffCoords: TAN_SON_NHAT,
      voucherCode: "FOXRIDE25",
      expectedTotalVnd: expectedTotal,
    });

    assert.equal(created.paymentStatus, "paid_demo");
    assert.equal(created.quote.totalVnd, expectedTotal);
    assert.equal(created.quote.verifiedRoute.distanceKm, route.distanceKm);

    const orderDoc = (await db.collection("orders").doc(created.orderId).get()).data();
    assert.equal(orderDoc.distanceKm, route.distanceKm);
    assert.equal(orderDoc.distanceMeters, route.distanceMeters);
    assert.equal(orderDoc.pickupLat, BEN_THANH.lat);
    assert.equal(orderDoc.dropoffLat, TAN_SON_NHAT.lat);
    assert.equal(orderDoc.routeSource, "server_haversine_demo_estimate");
  });

  test("4. Chặn gian lận quãng đường: client gửi distanceKm=1km cho lộ trình ~9km bị server từ chối", async () => {
    const uid = `route_tamper_${Date.now()}`;
    const auth = { uid, email: "tamper_route@test.local", emailVerified: true };

    await assert.rejects(
      () =>
        createVerifiedOrderAndPayDemoTx(db, auth, {
          idempotencyKey: `tamper_km_${uid}`,
          serviceType: "RIDE",
          paymentMethod: "Tiền mặt DEMO",
          primaryAddress: "Chợ Bến Thành",
          secondaryAddress: "Sân bay TSN",
          vehicleType: "FOX Car 4",
          distanceKm: 1,
          pickupCoords: BEN_THANH,
          dropoffCoords: TAN_SON_NHAT,
        }),
      /không khớp với tuyến đường xác thực/i,
    );
  });

  test("5. Giao hàng DELIVERY kèm tọa độ tính phụ phí theo km xác thực server (nguyên VND)", async () => {
    const uid = `route_deliv_${Date.now()}`;
    const auth = { uid, email: "deliv_route@test.local", emailVerified: true };

    const route = await computeVerifiedRouteOp({
      origin: BEN_THANH,
      destination: TAN_SON_NHAT,
      vehicleType: "FOX Bike",
    });

    // Nhanh (32,000) + 2-5 kg (8,000) + max(0, distanceKm - 3) * 4,000 - FOXSHIP15 (10,000)
    const expectedSubtotal = 32_000 + 8_000 + Math.max(0, route.distanceKm - 3) * 4_000;
    const expectedTotal = expectedSubtotal - 10_000;

    const created = await createVerifiedOrderAndPayDemoTx(db, auth, {
      idempotencyKey: `deliv_route_${uid}`,
      serviceType: "DELIVERY",
      paymentMethod: "Tiền mặt DEMO",
      primaryAddress: "Chợ Bến Thành",
      secondaryAddress: "Sân bay TSN",
      speed: "Nhanh",
      weightTier: "2–5 kg",
      pickupCoords: BEN_THANH,
      dropoffCoords: TAN_SON_NHAT,
      voucherCode: "FOXSHIP15",
      expectedTotalVnd: expectedTotal,
    });

    assert.equal(created.quote.subtotalVnd, expectedSubtotal);
    assert.equal(created.quote.totalVnd, expectedTotal);
    assert.equal(created.quote.verifiedRoute.distanceKm, route.distanceKm);
  });

  test("6. Tương thích ngược: đơn RIDE/DELIVERY không truyền tọa độ vẫn hoạt động bình thường", async () => {
    const uid = `route_legacy_${Date.now()}`;
    const auth = { uid, email: "legacy@test.local", emailVerified: true };

    const deliv = await createVerifiedOrderAndPayDemoTx(db, auth, {
      idempotencyKey: `legacy_deliv_${uid}`,
      serviceType: "DELIVERY",
      paymentMethod: "Tiền mặt DEMO",
      primaryAddress: "Quận 1",
      secondaryAddress: "Quận 3",
      speed: "Tiêu chuẩn",
      weightTier: "Dưới 2 kg",
    });
    assert.equal(deliv.quote.totalVnd, 20_000);
    assert.equal(deliv.quote.verifiedRoute, null);
  });
});
