const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require('@firebase/rules-unit-testing');
const { test, before, after, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const PROJECT_ID = process.env.GCP_PROJECT || 'demo-no-project';
const DB_ID = 'ai-studio-android-1a796de6-79f0-4283-8d0b-703e709b27e4';
const ADMIN_EMAIL = 'hahaiduy28@gmail.com';

let testEnv;

function getDb(context) {
  return context.firestore(DB_ID);
}

function nowDate() {
  return new Date(Date.now() - 1000);
}

function makeUserPayload(uid, overrides = {}) {
  const ts = nowDate();
  return {
    userId: uid,
    displayName: `User ${uid}`,
    email: `${uid}@fox.vn`,
    phone: '0901234567',
    defaultAddress: 'Quận 1, TP.HCM',
    role: 'customer',
    requestedRole: 'none',
    driverStatus: 'none',
    merchantStatus: 'none',
    accountStatus: 'active',
    vehicleType: '',
    vehiclePlate: '',
    storeName: '',
    storeCategory: '',
    driverOnline: false,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function makeOrderPayload(orderCode, customerId, serviceType = 'RIDE', overrides = {}) {
  const ts = nowDate();
  const dispatchTarget = serviceType === 'RIDE' || serviceType === 'DELIVERY' ? 'driver' : 'merchant';
  return {
    orderCode,
    serviceType,
    customerId,
    customerName: 'Khách FOX',
    driverId: '',
    driverName: '',
    merchantId: '',
    merchantName: '',
    title: `Đơn ${serviceType}`,
    primaryAddress: '12 Lê Lợi, Q.1',
    secondaryAddress: '88 Đồng Khởi, Q.1',
    detailSummary: 'Chi tiết đơn thử nghiệm',
    subtotalVnd: 50000,
    feeVnd: 15000,
    discountVnd: 0,
    totalVnd: 65000,
    paymentMethod: 'Tiền mặt DEMO',
    voucherCode: '',
    financialStatus: 'unverified_backend_required',
    status: 'pending',
    dispatchOpen: true,
    dispatchTarget,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: fs.readFileSync('firestore.rules', 'utf8'),
      host: '127.0.0.1',
      port: 8085,
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

test('1. Unauthenticated requests and client wallet/ledger writes are denied', async () => {
  const unauthDb = getDb(testEnv.unauthenticatedContext());
  const custDb = getDb(testEnv.authenticatedContext('cust_1'));
  await assertFails(unauthDb.collection('users').doc('u1').get());
  await assertFails(unauthDb.collection('orders').doc('FOX-RIDE-1001').set(makeOrderPayload('FOX-RIDE-1001', 'u1')));
  // Client cannot write to any wallet or financial ledger collection
  await assertFails(custDb.collection('wallets').doc('cust_1').set({ balanceVnd: 999999999 }));
  await assertFails(custDb.collection('wallet_transactions').doc('tx_1').set({ amountVnd: 500000 }));
});

test('2. Customer registration, PII isolation, anti-self-escalation & cross-customer order read blocking', async () => {
  const aliceDb = getDb(testEnv.authenticatedContext('alice'));
  const bobDb = getDb(testEnv.authenticatedContext('bob'));

  await assertSucceeds(aliceDb.collection('users').doc('alice').set(makeUserPayload('alice')));
  await assertSucceeds(bobDb.collection('users').doc('bob').set(makeUserPayload('bob')));

  // Bob cannot read Alice's PII profile
  await assertFails(bobDb.collection('users').doc('alice').get());

  // Alice cannot self-escalate to admin or self-approve driver
  await assertFails(aliceDb.collection('users').doc('alice').update({ role: 'admin', updatedAt: nowDate() }));
  await assertFails(
    aliceDb.collection('users').doc('alice').update({
      role: 'driver',
      driverStatus: 'approved',
      updatedAt: nowDate(),
    }),
  );

  // Alice creates a RIDE order; Bob (another customer) CANNOT get OR list Alice's order!
  await assertSucceeds(aliceDb.collection('orders').doc('FOX-RIDE-2001').set(makeOrderPayload('FOX-RIDE-2001', 'alice', 'RIDE')));
  await assertFails(bobDb.collection('orders').doc('FOX-RIDE-2001').get());
  await assertFails(
    bobDb.collection('orders')
      .where('dispatchOpen', '==', true)
      .where('dispatchTarget', '==', 'driver')
      .get(),
  );
  await assertFails(
    bobDb.collection('orders').where('customerId', '==', 'alice').get(),
  );
});

test('3. Verified Admin approves driver; spoofed unverified admin email is rejected', async () => {
  const driverDb = getDb(testEnv.authenticatedContext('drv_1'));
  const fakeAdminDb = getDb(
    testEnv.authenticatedContext('spoof', { email: ADMIN_EMAIL, email_verified: false }),
  );
  const realAdminDb = getDb(
    testEnv.authenticatedContext('admin_uid', { email: ADMIN_EMAIL, email_verified: true }),
  );

  await assertSucceeds(driverDb.collection('users').doc('drv_1').set(makeUserPayload('drv_1')));
  await assertSucceeds(
    driverDb.collection('users').doc('drv_1').update({
      requestedRole: 'driver',
      driverStatus: 'pending',
      vehicleType: 'FOX Bike',
      vehiclePlate: '59X3-88888',
      updatedAt: nowDate(),
    }),
  );

  // Unverified spoofed admin is blocked
  await assertFails(
    fakeAdminDb.collection('users').doc('drv_1').update({
      role: 'driver',
      driverStatus: 'approved',
      updatedAt: nowDate(),
    }),
  );

  // Pending (unapproved) driver CANNOT query open driver orders yet
  await assertFails(
    driverDb.collection('orders')
      .where('dispatchOpen', '==', true)
      .where('dispatchTarget', '==', 'driver')
      .get(),
  );

  // Verified Admin approves driver
  await assertSucceeds(
    realAdminDb.collection('users').doc('drv_1').update({
      role: 'driver',
      requestedRole: 'driver',
      driverStatus: 'approved',
      updatedAt: nowDate(),
    }),
  );

  // Approved driver CAN now query open driver orders
  const openSnap = await assertSucceeds(
    driverDb.collection('orders')
      .where('dispatchOpen', '==', true)
      .where('dispatchTarget', '==', 'driver')
      .get(),
  );
  assert.equal(openSnap.size, 0);
});

test('4. Full RIDE lifecycle (pending -> accepted -> arrived -> in_progress -> completed) & anti-double-accept', async () => {
  const custDb = getDb(testEnv.authenticatedContext('cust_1'));
  const drv1Db = getDb(testEnv.authenticatedContext('drv_1'));
  const drv2Db = getDb(testEnv.authenticatedContext('drv_2'));
  const adminDb = getDb(
    testEnv.authenticatedContext('admin_uid', { email: ADMIN_EMAIL, email_verified: true }),
  );

  await assertSucceeds(custDb.collection('users').doc('cust_1').set(makeUserPayload('cust_1')));
  await assertSucceeds(drv1Db.collection('users').doc('drv_1').set(makeUserPayload('drv_1')));
  await assertSucceeds(drv2Db.collection('users').doc('drv_2').set(makeUserPayload('drv_2')));
  for (const uid of ['drv_1', 'drv_2']) {
    await assertSucceeds(
      adminDb.collection('users').doc(uid).update({
        role: 'driver',
        driverStatus: 'approved',
        updatedAt: nowDate(),
      }),
    );
  }

  // Customer creates RIDE order
  const orderId = 'FOX-RIDE-3001';
  await assertSucceeds(custDb.collection('orders').doc(orderId).set(makeOrderPayload(orderId, 'cust_1', 'RIDE')));

  // Driver 1 sees order in driver queue
  const queueSnap = await assertSucceeds(
    drv1Db.collection('orders')
      .where('dispatchOpen', '==', true)
      .where('dispatchTarget', '==', 'driver')
      .get(),
  );
  assert.equal(queueSnap.size, 1);

  // Driver 1 atomically claims order via Transaction
  await assertSucceeds(
    drv1Db.runTransaction(async (tx) => {
      const ref = drv1Db.collection('orders').doc(orderId);
      const snap = await tx.get(ref);
      assert.equal(snap.data().driverId, '');
      tx.update(ref, {
        driverId: 'drv_1',
        driverName: 'Tài xế Một',
        status: 'driver_accepted',
        dispatchOpen: false,
        dispatchTarget: 'none',
        updatedAt: nowDate(),
      });
    }),
  );

  // Customer immediately sees updated Cloud status & driverName
  const custRead = await assertSucceeds(custDb.collection('orders').doc(orderId).get());
  assert.equal(custRead.data().status, 'driver_accepted');
  assert.equal(custRead.data().driverName, 'Tài xế Một');

  // Driver 2 CANNOT steal / double-accept the already claimed order
  await assertFails(
    drv2Db.collection('orders').doc(orderId).update({
      driverId: 'drv_2',
      driverName: 'Tài xế Hai',
      status: 'driver_accepted',
      dispatchOpen: false,
      dispatchTarget: 'none',
      updatedAt: nowDate(),
    }),
  );
  // Driver 2 also CANNOT read Driver 1's claimed private order
  await assertFails(drv2Db.collection('orders').doc(orderId).get());

  // Driver 1 CANNOT skip driver_arrived straight to in_progress
  await assertFails(
    drv1Db.collection('orders').doc(orderId).update({
      status: 'in_progress',
      updatedAt: nowDate(),
    }),
  );

  // Driver 1 CANNOT tamper with totalVnd or financialStatus
  await assertFails(
    drv1Db.collection('orders').doc(orderId).update({
      status: 'driver_arrived',
      totalVnd: 999000,
      updatedAt: nowDate(),
    }),
  );

  // Driver 1 advances step-by-step: driver_arrived -> in_progress -> completed
  await assertSucceeds(
    drv1Db.collection('orders').doc(orderId).update({ status: 'driver_arrived', updatedAt: nowDate() }),
  );
  // Customer CANNOT cancel after driver_arrived
  await assertFails(
    custDb.collection('orders').doc(orderId).update({
      status: 'cancelled',
      dispatchOpen: false,
      dispatchTarget: 'none',
      updatedAt: nowDate(),
    }),
  );
  await assertSucceeds(
    drv1Db.collection('orders').doc(orderId).update({ status: 'in_progress', updatedAt: nowDate() }),
  );
  // Driver CANNOT cancel once in_progress
  await assertFails(
    drv1Db.collection('orders').doc(orderId).update({
      status: 'cancelled',
      dispatchOpen: false,
      dispatchTarget: 'none',
      updatedAt: nowDate(),
    }),
  );
  await assertSucceeds(
    drv1Db.collection('orders').doc(orderId).update({ status: 'completed', updatedAt: nowDate() }),
  );
  // Terminal lock: cannot mutate completed order
  await assertFails(
    drv1Db.collection('orders').doc(orderId).update({ status: 'in_progress', updatedAt: nowDate() }),
  );
});

test('5. FOOD/SHOPPING Merchant handoff -> Driver pickup & cross-merchant product isolation', async () => {
  const custDb = getDb(testEnv.authenticatedContext('cust_2'));
  const merchDb = getDb(testEnv.authenticatedContext('merch_1'));
  const merch2Db = getDb(testEnv.authenticatedContext('merch_2'));
  const drvDb = getDb(testEnv.authenticatedContext('drv_3'));
  const adminDb = getDb(
    testEnv.authenticatedContext('admin_uid', { email: ADMIN_EMAIL, email_verified: true }),
  );

  await assertSucceeds(custDb.collection('users').doc('cust_2').set(makeUserPayload('cust_2')));
  await assertSucceeds(merchDb.collection('users').doc('merch_1').set(makeUserPayload('merch_1')));
  await assertSucceeds(merch2Db.collection('users').doc('merch_2').set(makeUserPayload('merch_2')));
  await assertSucceeds(drvDb.collection('users').doc('drv_3').set(makeUserPayload('drv_3')));

  await assertSucceeds(adminDb.collection('users').doc('merch_1').update({ role: 'merchant', merchantStatus: 'approved', updatedAt: nowDate() }));
  await assertSucceeds(adminDb.collection('users').doc('merch_2').update({ role: 'merchant', merchantStatus: 'approved', updatedAt: nowDate() }));
  await assertSucceeds(adminDb.collection('users').doc('drv_3').update({ role: 'driver', driverStatus: 'approved', updatedAt: nowDate() }));

  // Customer creates FOOD order -> dispatchTarget is 'merchant'
  const foodId = 'FOX-FOOD-4001';
  await assertSucceeds(custDb.collection('orders').doc(foodId).set(makeOrderPayload(foodId, 'cust_2', 'FOOD')));

  // Driver CANNOT claim FOOD order while it's pending in merchant queue
  await assertFails(
    drvDb.collection('orders').doc(foodId).update({
      driverId: 'drv_3',
      driverName: 'Tài xế Ba',
      status: 'driver_accepted',
      dispatchOpen: false,
      dispatchTarget: 'none',
      updatedAt: nowDate(),
    }),
  );

  // Merchant 1 confirms -> Customer can NO LONGER cancel FOOD after merchant_confirmed
  await assertSucceeds(
    merchDb.collection('orders').doc(foodId).update({
      merchantId: 'merch_1',
      merchantName: 'Phở FOX',
      status: 'merchant_confirmed',
      dispatchOpen: false,
      dispatchTarget: 'none',
      updatedAt: nowDate(),
    }),
  );
  await assertFails(
    custDb.collection('orders').doc(foodId).update({
      status: 'cancelled',
      dispatchOpen: false,
      dispatchTarget: 'none',
      updatedAt: nowDate(),
    }),
  );

  // Merchant 1 prepares -> marks ready_for_pickup (reopens dispatchTarget = 'driver')
  await assertSucceeds(
    merchDb.collection('orders').doc(foodId).update({
      status: 'preparing',
      dispatchOpen: false,
      dispatchTarget: 'none',
      updatedAt: nowDate(),
    }),
  );
  await assertSucceeds(
    merchDb.collection('orders').doc(foodId).update({
      status: 'ready_for_pickup',
      dispatchOpen: true,
      dispatchTarget: 'driver',
      updatedAt: nowDate(),
    }),
  );

  // Now approved Driver sees and claims the ready FOOD order -> arrives -> delivers -> completes
  await assertSucceeds(
    drvDb.collection('orders').doc(foodId).update({
      driverId: 'drv_3',
      driverName: 'Tài xế Ba',
      status: 'driver_accepted',
      dispatchOpen: false,
      dispatchTarget: 'none',
      updatedAt: nowDate(),
    }),
  );
  await assertSucceeds(drvDb.collection('orders').doc(foodId).update({ status: 'driver_arrived', updatedAt: nowDate() }));
  await assertSucceeds(drvDb.collection('orders').doc(foodId).update({ status: 'in_progress', updatedAt: nowDate() }));
  await assertSucceeds(drvDb.collection('orders').doc(foodId).update({ status: 'completed', updatedAt: nowDate() }));

  // Cross-merchant product write isolation
  await assertSucceeds(
    merchDb.collection('merchants').doc('merch_1').collection('products').doc('p1').set({
      merchantId: 'merch_1',
      storeName: 'Phở FOX',
      name: 'Phở đặc biệt',
      category: 'Món nước',
      priceVnd: 65000,
      stock: 15,
      active: true,
      createdAt: nowDate(),
      updatedAt: nowDate(),
    }),
  );
  await assertFails(
    merch2Db.collection('merchants').doc('merch_1').collection('products').doc('p1').update({
      priceVnd: 1000,
      updatedAt: nowDate(),
    }),
  );
});

test('6. Per-service cancellation: RIDE customer cancels before arrival & driver cancels at arrival', async () => {
  const custDb = getDb(testEnv.authenticatedContext('cust_3'));
  const drvDb = getDb(testEnv.authenticatedContext('drv_4'));
  const adminDb = getDb(
    testEnv.authenticatedContext('admin_uid', { email: ADMIN_EMAIL, email_verified: true }),
  );

  await assertSucceeds(custDb.collection('users').doc('cust_3').set(makeUserPayload('cust_3')));
  await assertSucceeds(drvDb.collection('users').doc('drv_4').set(makeUserPayload('drv_4')));
  await assertSucceeds(adminDb.collection('users').doc('drv_4').update({ role: 'driver', driverStatus: 'approved', updatedAt: nowDate() }));

  // Case A: Customer cancels RIDE after driver_accepted (allowed before driver_arrived)
  const rideA = 'FOX-RIDE-5001';
  await assertSucceeds(custDb.collection('orders').doc(rideA).set(makeOrderPayload(rideA, 'cust_3', 'RIDE')));
  await assertSucceeds(
    drvDb.collection('orders').doc(rideA).update({
      driverId: 'drv_4',
      driverName: 'Tài xế Bốn',
      status: 'driver_accepted',
      dispatchOpen: false,
      dispatchTarget: 'none',
      updatedAt: nowDate(),
    }),
  );
  await assertSucceeds(
    custDb.collection('orders').doc(rideA).update({
      status: 'cancelled',
      dispatchOpen: false,
      dispatchTarget: 'none',
      updatedAt: nowDate(),
    }),
  );

  // Case B: Driver cancels DELIVERY at driver_arrived (e.g. sender no-show)
  const delivB = 'FOX-DELIVERY-5002';
  await assertSucceeds(custDb.collection('orders').doc(delivB).set(makeOrderPayload(delivB, 'cust_3', 'DELIVERY')));
  await assertSucceeds(
    drvDb.collection('orders').doc(delivB).update({
      driverId: 'drv_4',
      driverName: 'Tài xế Bốn',
      status: 'driver_accepted',
      dispatchOpen: false,
      dispatchTarget: 'none',
      updatedAt: nowDate(),
    }),
  );
  await assertSucceeds(
    drvDb.collection('orders').doc(delivB).update({ status: 'driver_arrived', updatedAt: nowDate() }),
  );
  await assertSucceeds(
    drvDb.collection('orders').doc(delivB).update({
      status: 'cancelled',
      dispatchOpen: false,
      dispatchTarget: 'none',
      updatedAt: nowDate(),
    }),
  );
});

test('7. DEMO Wallets, Ledger, Platform Revenue read-only rules & server-enriched order compatibility', async () => {
  const custADb = getDb(testEnv.authenticatedContext('wallet_cust_a'));
  const custBDb = getDb(testEnv.authenticatedContext('wallet_cust_b'));
  const drvDb = getDb(testEnv.authenticatedContext('wallet_drv_1'));
  const adminDb = getDb(
    testEnv.authenticatedContext('admin_uid', { email: ADMIN_EMAIL, email_verified: true }),
  );
  const spoofAdminDb = getDb(
    testEnv.authenticatedContext('spoof_fin', { email: ADMIN_EMAIL, email_verified: false }),
  );

  // Seed wallet, transaction, platform revenue, idempotency lock, and server-verified order via Admin bypass
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const rawDb = getDb(ctx);
    await rawDb.collection('users').doc('wallet_drv_1').set({
      ...makeUserPayload('wallet_drv_1'),
      role: 'driver',
      driverStatus: 'approved',
    });
    await rawDb.collection('wallets').doc('wallet_cust_a').set({
      userId: 'wallet_cust_a',
      balanceVnd: 150000,
      currency: 'VND',
      isDemo: true,
      demoNotice: 'DEMO — KHÔNG PHẢI TIỀN THẬT',
      updatedAt: nowDate(),
    });
    await rawDb.collection('wallets').doc('wallet_cust_a').collection('transactions').doc('tx_1').set({
      txId: 'tx_1',
      userId: 'wallet_cust_a',
      txType: 'TOPUP_DEMO',
      title: 'Nạp DEMO',
      amountVnd: 150000,
      balanceAfterVnd: 150000,
      status: 'succeeded',
      idempotencyKey: 'seed_1',
      isDemo: true,
      createdAt: nowDate(),
    });
    await rawDb.collection('platform_revenue').doc('rev_1').set({
      orderId: 'FOX-RIDE-9001',
      serviceType: 'RIDE',
      grossTotalVnd: 50000,
      driverCommissionVnd: 40000,
      merchantCommissionVnd: 0,
      platformRevenueVnd: 10000,
      currency: 'VND',
      isDemo: true,
      createdAt: nowDate(),
    });
    await rawDb.collection('financial_idempotency').doc('lock_1').set({ scope: 'topup', createdAt: nowDate() });
    await rawDb.collection('orders').doc('FOX-RIDE-9001').set({
      ...makeOrderPayload('FOX-RIDE-9001', 'wallet_cust_a', 'RIDE'),
      financialStatus: 'verified_demo',
      paymentStatus: 'paid_demo',
      paymentId: 'TX-PAY-FOX-RIDE-9001',
      refundStatus: 'none',
      driverCommissionVnd: 0,
      merchantCommissionVnd: 0,
      platformRevenueVnd: 0,
      commissionSettled: false,
    });
  });

  // Owner & Admin CAN read own wallet and transactions; Customer B CANNOT
  await assertSucceeds(custADb.collection('wallets').doc('wallet_cust_a').get());
  await assertSucceeds(custADb.collection('wallets').doc('wallet_cust_a').collection('transactions').get());
  await assertSucceeds(adminDb.collection('wallets').doc('wallet_cust_a').get());
  await assertFails(custBDb.collection('wallets').doc('wallet_cust_a').get());
  await assertFails(custBDb.collection('wallets').doc('wallet_cust_a').collection('transactions').get());

  // NO client (owner OR admin!) can write/mutate/delete wallets, transactions, platform_revenue, or idempotency locks
  await assertFails(custADb.collection('wallets').doc('wallet_cust_a').update({ balanceVnd: 9999999 }));
  await assertFails(adminDb.collection('wallets').doc('wallet_cust_a').update({ balanceVnd: 9999999 }));
  await assertFails(custADb.collection('wallets').doc('wallet_cust_a').collection('transactions').doc('tx_fake').set({ amountVnd: 999999 }));
  await assertFails(adminDb.collection('platform_revenue').doc('rev_fake').set({ platformRevenueVnd: 0 }));
  await assertFails(adminDb.collection('financial_idempotency').doc('lock_1').get());
  await assertFails(custADb.collection('financial_idempotency').doc('lock_1').get());

  // Verified Admin CAN read platform_revenue; Customer & Spoof Admin CANNOT
  await assertSucceeds(adminDb.collection('platform_revenue').get());
  await assertFails(custADb.collection('platform_revenue').get());
  await assertFails(spoofAdminDb.collection('platform_revenue').get());

  // Approved Driver CAN claim server-enriched 31-field order, CANNOT tamper paymentStatus/totalVnd
  await assertSucceeds(
    drvDb.collection('orders').doc('FOX-RIDE-9001').update({
      driverId: 'wallet_drv_1',
      driverName: 'Tài xế Ví',
      status: 'driver_accepted',
      dispatchOpen: false,
      dispatchTarget: 'none',
      updatedAt: nowDate(),
    }),
  );
  await assertFails(
    drvDb.collection('orders').doc('FOX-RIDE-9001').update({
      status: 'driver_arrived',
      driverCommissionVnd: 999999,
      updatedAt: nowDate(),
    }),
  );
});

test('8. Cloud Merchant Store profiles, product edit/hide, customer catalog reads & cross-merchant order isolation', async () => {
  const custDb = getDb(testEnv.authenticatedContext('cat_cust_1'));
  const merchADb = getDb(testEnv.authenticatedContext('cat_merch_a'));
  const merchBDb = getDb(testEnv.authenticatedContext('cat_merch_b'));
  const adminDb = getDb(
    testEnv.authenticatedContext('admin_uid', { email: ADMIN_EMAIL, email_verified: true }),
  );

  await assertSucceeds(custDb.collection('users').doc('cat_cust_1').set(makeUserPayload('cat_cust_1')));
  await assertSucceeds(merchADb.collection('users').doc('cat_merch_a').set(makeUserPayload('cat_merch_a')));
  await assertSucceeds(merchBDb.collection('users').doc('cat_merch_b').set(makeUserPayload('cat_merch_b')));
  await assertSucceeds(adminDb.collection('users').doc('cat_merch_a').update({ role: 'merchant', merchantStatus: 'approved', updatedAt: nowDate() }));
  await assertSucceeds(adminDb.collection('users').doc('cat_merch_b').update({ role: 'merchant', merchantStatus: 'approved', updatedAt: nowDate() }));

  const ts = nowDate();
  // Merchant A creates its own store profile
  await assertSucceeds(
    merchADb.collection('merchants').doc('cat_merch_a').set({
      merchantId: 'cat_merch_a',
      ownerUid: 'cat_merch_a',
      storeType: 'FOOD',
      name: 'Quán Phở A',
      description: 'Phở bò gia truyền',
      category: 'Món nước',
      address: '10 Lê Lợi, Quận 1',
      phone: '0901111111',
      imageUrl: '',
      openingHours: '07:00 - 22:00',
      openNow: true,
      ratingTenths: 48,
      active: true,
      createdAt: ts,
      updatedAt: ts,
    }),
  );

  // Merchant B CANNOT create or edit Merchant A's store profile
  await assertFails(
    merchBDb.collection('merchants').doc('cat_merch_a').update({
      name: 'Hack Store A',
      openNow: false,
      updatedAt: nowDate(),
    }),
  );

  // Merchant A creates, edits, and hides a product
  const prodRefA = merchADb.collection('merchants').doc('cat_merch_a').collection('products').doc('pho_1');
  await assertSucceeds(
    prodRefA.set({
      merchantId: 'cat_merch_a',
      storeName: 'Quán Phở A',
      serviceType: 'FOOD',
      name: 'Phở Tái',
      description: 'Nước dùng đậm đà',
      imageUrl: '',
      category: 'Món nước',
      priceVnd: 60000,
      stock: 10,
      active: true,
      createdAt: ts,
      updatedAt: ts,
    }),
  );
  await assertSucceeds(
    prodRefA.update({
      priceVnd: 65000,
      stock: 12,
      updatedAt: nowDate(),
    }),
  );

  // Customer CAN read active store & active product
  const activeStores = await assertSucceeds(
    custDb.collection('merchants').where('active', '==', true).get(),
  );
  assert.equal(activeStores.size, 1);
  const activeProducts = await assertSucceeds(
    custDb.collection('merchants').doc('cat_merch_a').collection('products').where('active', '==', true).get(),
  );
  assert.equal(activeProducts.size, 1);
  assert.equal(activeProducts.docs[0].data().priceVnd, 65000);

  // Merchant B CANNOT edit, hide, or delete Merchant A's product
  await assertFails(
    merchBDb.collection('merchants').doc('cat_merch_a').collection('products').doc('pho_1').update({
      priceVnd: 1000,
      updatedAt: nowDate(),
    }),
  );
  await assertFails(
    merchBDb.collection('merchants').doc('cat_merch_a').collection('products').doc('pho_1').delete(),
  );

  // Merchant A hides product -> Customer CANNOT get hidden product, Merchant A STILL CAN
  await assertSucceeds(prodRefA.update({ active: false, updatedAt: nowDate() }));
  await assertFails(
    custDb.collection('merchants').doc('cat_merch_a').collection('products').doc('pho_1').get(),
  );
  await assertSucceeds(prodRefA.get());

  // Targeted order belonging to Merchant A: Merchant B CANNOT read, confirm, or cancel it!
  const targetedOrderId = 'FOX-FOOD-8801';
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await getDb(ctx).collection('orders').doc(targetedOrderId).set({
      ...makeOrderPayload(targetedOrderId, 'cat_cust_1', 'FOOD'),
      merchantId: 'cat_merch_a',
      merchantName: 'Quán Phở A',
      dispatchOpen: false,
      dispatchTarget: 'merchant',
      financialStatus: 'verified_demo',
      paymentStatus: 'paid_demo',
      stockDeducted: true,
      stockRestored: false,
    });
  });

  await assertFails(merchBDb.collection('orders').doc(targetedOrderId).get());
  await assertFails(
    merchBDb.collection('orders').doc(targetedOrderId).update({
      status: 'merchant_confirmed',
      dispatchOpen: false,
      dispatchTarget: 'none',
      updatedAt: nowDate(),
    }),
  );
  await assertFails(
    merchBDb.collection('orders').doc(targetedOrderId).update({
      status: 'cancelled',
      dispatchOpen: false,
      dispatchTarget: 'none',
      updatedAt: nowDate(),
    }),
  );

  // Merchant A CAN confirm, prepare, and mark ready its own targeted order
  await assertSucceeds(
    merchADb.collection('orders').doc(targetedOrderId).update({
      status: 'merchant_confirmed',
      dispatchOpen: false,
      dispatchTarget: 'none',
      updatedAt: nowDate(),
    }),
  );
  await assertSucceeds(
    merchADb.collection('orders').doc(targetedOrderId).update({
      status: 'preparing',
      dispatchOpen: false,
      dispatchTarget: 'none',
      updatedAt: nowDate(),
    }),
  );
  await assertSucceeds(
    merchADb.collection('orders').doc(targetedOrderId).update({
      status: 'ready_for_pickup',
      dispatchOpen: true,
      dispatchTarget: 'driver',
      updatedAt: nowDate(),
    }),
  );
});

test('9. Live Driver Location Rules: strict customer/driver isolation, rate-limiting, stop after completion & cleanup', async () => {
  const custADb = getDb(testEnv.authenticatedContext('loc_cust_a'));
  const custBDb = getDb(testEnv.authenticatedContext('loc_cust_b'));
  const drvADb = getDb(testEnv.authenticatedContext('loc_drv_a'));
  const drvBDb = getDb(testEnv.authenticatedContext('loc_drv_b'));
  const adminDb = getDb(
    testEnv.authenticatedContext('admin_uid', { email: ADMIN_EMAIL, email_verified: true }),
  );

  await assertSucceeds(custADb.collection('users').doc('loc_cust_a').set(makeUserPayload('loc_cust_a')));
  await assertSucceeds(custBDb.collection('users').doc('loc_cust_b').set(makeUserPayload('loc_cust_b')));
  await assertSucceeds(drvADb.collection('users').doc('loc_drv_a').set(makeUserPayload('loc_drv_a')));
  await assertSucceeds(drvBDb.collection('users').doc('loc_drv_b').set(makeUserPayload('loc_drv_b')));
  await assertSucceeds(adminDb.collection('users').doc('loc_drv_a').update({ role: 'driver', driverStatus: 'approved', updatedAt: nowDate() }));
  await assertSucceeds(adminDb.collection('users').doc('loc_drv_b').update({ role: 'driver', driverStatus: 'approved', updatedAt: nowDate() }));

  const orderId = 'FOX-RIDE-LOC1';
  await assertSucceeds(custADb.collection('orders').doc(orderId).set(makeOrderPayload(orderId, 'loc_cust_a', 'RIDE')));

  const locDocPath = (db) => db.collection('orders').doc(orderId).collection('live_location').doc('driver');
  const makeLocPayload = (driverId, updatedAt) => ({
    orderId,
    driverId,
    driverName: 'Tài xế A',
    lat: 10.7769,
    lng: 106.7009,
    bearing: 90,
    speedMps: 8.5,
    accuracyMeters: 12,
    isDemo: false,
    updatedAt,
  });

  // Cannot publish live location BEFORE driver claims the order (status == pending)
  await assertFails(locDocPath(drvADb).set(makeLocPayload('loc_drv_a', nowDate())));

  // Driver A claims the order -> driver_accepted
  await assertSucceeds(
    drvADb.collection('orders').doc(orderId).update({
      driverId: 'loc_drv_a',
      driverName: 'Tài xế A',
      status: 'driver_accepted',
      dispatchOpen: false,
      dispatchTarget: 'none',
      updatedAt: nowDate(),
    }),
  );

  // Unassigned Driver B CANNOT publish location for Driver A's order
  await assertFails(locDocPath(drvBDb).set(makeLocPayload('loc_drv_b', nowDate())));

  // Assigned Driver A CAN publish initial live location (single doc overwrite, no history collection)
  await assertSucceeds(locDocPath(drvADb).set(makeLocPayload('loc_drv_a', nowDate())));

  // Immediate rapid update (< 4 seconds) is BLOCKED by Firestore rate-limit rule!
  await assertFails(locDocPath(drvADb).set(makeLocPayload('loc_drv_a', nowDate())));

  // Assigned Customer A CAN read Driver A's live location; Customer B & Driver B CANNOT!
  await assertSucceeds(locDocPath(custADb).get());
  await assertSucceeds(locDocPath(drvADb).get());
  await assertSucceeds(locDocPath(adminDb).get());
  await assertFails(locDocPath(custBDb).get());
  await assertFails(locDocPath(drvBDb).get());
  // Collection list is strictly forbidden (zero history listing)
  await assertFails(custADb.collection('orders').doc(orderId).collection('live_location').get());

  // Complete order -> Driver A can no longer publish updates, and CAN delete live location doc
  await assertSucceeds(drvADb.collection('orders').doc(orderId).update({ status: 'driver_arrived', updatedAt: nowDate() }));
  await assertSucceeds(drvADb.collection('orders').doc(orderId).update({ status: 'in_progress', updatedAt: nowDate() }));
  await assertSucceeds(drvADb.collection('orders').doc(orderId).update({ status: 'completed', updatedAt: nowDate() }));

  await assertFails(locDocPath(drvADb).set(makeLocPayload('loc_drv_a', nowDate())));
  await assertSucceeds(locDocPath(drvADb).delete());
});

test('10. FCM Device Token Isolation & Role-Targeted Notification ReadFlag Rules', async () => {
  const userADb = getDb(testEnv.authenticatedContext('fcm_user_a'));
  const userBDb = getDb(testEnv.authenticatedContext('fcm_user_b'));
  const adminDb = getDb(
    testEnv.authenticatedContext('fcm_admin', { email: ADMIN_EMAIL, email_verified: true }),
  );

  const tokenRefA = (db) => db.collection('users').doc('fcm_user_a').collection('fcm_tokens').doc('tok_android_01');
  const validToken = {
    token: 'fcm_valid_registration_token_example_0123456789',
    platform: 'android',
    role: 'customer',
    enabled: true,
    updatedAt: nowDate(),
  };

  // Owner A can register, read, update, and delete own FCM token
  await assertSucceeds(tokenRefA(userADb).set(validToken));
  await assertSucceeds(tokenRefA(userADb).get());

  // User B AND even Admin cannot read or overwrite User A's private FCM token via client SDK
  await assertFails(tokenRefA(userBDb).get());
  await assertFails(tokenRefA(userBDb).set(validToken));
  await assertFails(tokenRefA(adminDb).get());

  // Invalid platform or short token is rejected
  await assertFails(
    tokenRefA(userADb).set({
      ...validToken,
      token: 'short',
    }),
  );
  await assertFails(
    tokenRefA(userADb).set({
      ...validToken,
      platform: 'web',
    }),
  );

  // Role-targeted notification with targetRole/eventType/orderId/serviceType can be marked read by owner
  const notifRef = (db) => db.collection('users').doc('fcm_user_a').collection('notifications').doc('n_role_1');
  await assertSucceeds(
    notifRef(adminDb).set({
      title: 'Tài xế đã nhận đơn',
      body: 'Tài xế đang tới điểm đón',
      relatedOrderCode: 'FOX-RIDE-9001',
      orderId: 'FOX-RIDE-9001',
      serviceType: 'RIDE',
      targetRole: 'customer',
      eventType: 'driver_accepted',
      readFlag: false,
      createdAt: nowDate(),
    }),
  );
  await assertSucceeds(notifRef(userADb).update({ readFlag: true }));
  await assertFails(notifRef(userBDb).get());

  // Owner A deletes token on sign-out
  await assertSucceeds(tokenRefA(userADb).delete());
});

test('11. Completed Order Customer Rating (single-submit 1..5) & Voucher Redemptions Client Write-Deny', async () => {
  const custADb = getDb(testEnv.authenticatedContext('rate_cust_a'));
  const custBDb = getDb(testEnv.authenticatedContext('rate_cust_b'));
  const drvDb = getDb(testEnv.authenticatedContext('rate_drv_1'));
  const adminDb = getDb(
    testEnv.authenticatedContext('rate_admin', { email: ADMIN_EMAIL, email_verified: true }),
  );

  await assertSucceeds(custADb.collection('users').doc('rate_cust_a').set(makeUserPayload('rate_cust_a')));
  await assertSucceeds(custBDb.collection('users').doc('rate_cust_b').set(makeUserPayload('rate_cust_b')));
  await assertSucceeds(drvDb.collection('users').doc('rate_drv_1').set(makeUserPayload('rate_drv_1')));
  await assertSucceeds(
    adminDb.collection('users').doc('rate_drv_1').update({
      role: 'driver',
      driverStatus: 'approved',
      updatedAt: nowDate(),
    }),
  );

  const orderId = 'FOX-RIDE-RATE01';
  await assertSucceeds(custADb.collection('orders').doc(orderId).set(makeOrderPayload(orderId, 'rate_cust_a', 'RIDE')));

  // Customer CANNOT rate before order is completed
  await assertFails(
    custADb.collection('orders').doc(orderId).update({
      customerRating: 5,
      customerReviewNote: 'Quá sớm',
      ratedAt: nowDate(),
      updatedAt: nowDate(),
    }),
  );

  // Driver completes full RIDE lifecycle
  await assertSucceeds(
    drvDb.collection('orders').doc(orderId).update({
      driverId: 'rate_drv_1',
      driverName: 'Tài xế Đánh Giá',
      status: 'driver_accepted',
      dispatchOpen: false,
      dispatchTarget: 'none',
      updatedAt: nowDate(),
    }),
  );
  await assertSucceeds(drvDb.collection('orders').doc(orderId).update({ status: 'driver_arrived', updatedAt: nowDate() }));
  await assertSucceeds(drvDb.collection('orders').doc(orderId).update({ status: 'in_progress', updatedAt: nowDate() }));
  await assertSucceeds(drvDb.collection('orders').doc(orderId).update({ status: 'completed', updatedAt: nowDate() }));

  // Driver and unrelated customer CANNOT rate
  await assertFails(
    drvDb.collection('orders').doc(orderId).update({
      customerRating: 5,
      customerReviewNote: 'Tự đánh giá',
      ratedAt: nowDate(),
      updatedAt: nowDate(),
    }),
  );
  await assertFails(
    custBDb.collection('orders').doc(orderId).update({
      customerRating: 1,
      customerReviewNote: 'Khách lạ',
      ratedAt: nowDate(),
      updatedAt: nowDate(),
    }),
  );

  // Out-of-range ratings (0 or 6) are REJECTED
  await assertFails(
    custADb.collection('orders').doc(orderId).update({
      customerRating: 6,
      customerReviewNote: 'Vượt giới hạn',
      ratedAt: nowDate(),
      updatedAt: nowDate(),
    }),
  );
  await assertFails(
    custADb.collection('orders').doc(orderId).update({
      customerRating: 0,
      customerReviewNote: 'Không hợp lệ',
      ratedAt: nowDate(),
      updatedAt: nowDate(),
    }),
  );

  // Customer CAN submit valid 5-star rating ONCE
  await assertSucceeds(
    custADb.collection('orders').doc(orderId).update({
      customerRating: 5,
      customerReviewNote: 'Tài xế thân thiện, chạy an toàn!',
      ratedAt: nowDate(),
      updatedAt: nowDate(),
    }),
  );

  // Double-rating / mutating rating after submission is REJECTED
  await assertFails(
    custADb.collection('orders').doc(orderId).update({
      customerRating: 2,
      customerReviewNote: 'Sửa lại',
      ratedAt: nowDate(),
      updatedAt: nowDate(),
    }),
  );

  // Voucher redemptions: client writes strictly denied; owner/admin can read, others denied
  const redemptionId = 'rate_cust_a_FOXRIDE25';
  await assertFails(
    custADb.collection('voucher_redemptions').doc(redemptionId).set({
      userId: 'rate_cust_a',
      voucherCode: 'FOXRIDE25',
      orderId,
      serviceType: 'RIDE',
      discountVnd: 10000,
      isDemo: true,
      createdAt: nowDate(),
    }),
  );
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await getDb(ctx).collection('voucher_redemptions').doc(redemptionId).set({
      userId: 'rate_cust_a',
      voucherCode: 'FOXRIDE25',
      orderId,
      serviceType: 'RIDE',
      discountVnd: 10000,
      isDemo: true,
      createdAt: nowDate(),
    });
  });
  await assertSucceeds(custADb.collection('voucher_redemptions').doc(redemptionId).get());
  await assertSucceeds(adminDb.collection('voucher_redemptions').doc(redemptionId).get());
  await assertFails(custBDb.collection('voucher_redemptions').doc(redemptionId).get());
  await assertFails(custADb.collection('voucher_redemptions').doc(redemptionId).delete());
});





