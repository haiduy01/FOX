import { FieldValue, Firestore, Timestamp } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import {
  assertSafeIntVnd,
  assertValidIdempotencyKey,
  assertValidServiceType,
  buildQuoteFromResolvedSubtotal,
  calculateAuthoritativeQuote,
  calculateCommissionSplit,
  normalizeOrderItems,
} from "./pricing";
import {
  AdminFinancialOverviewResult,
  BOOTSTRAPPED_ADMIN_EMAIL,
  CallerAuth,
  CreateVerifiedOrderRequest,
  CreateVerifiedOrderResult,
  DEMO_CURRENCY_NOTICE,
  FoxPaymentMethod,
  FoxServiceType,
  MAX_TOPUP_SINGLE_VND,
  MAX_WALLET_BALANCE_VND,
  MIN_TOPUP_SINGLE_VND,
  PayOrderWithDemoWalletRequest,
  PayOrderWithDemoWalletResult,
  PlatformRevenueEntryDto,
  RefundOrderDemoRequest,
  RefundOrderDemoResult,
  SettleOrderCommissionRequest,
  SettleOrderCommissionResult,
  TopUpDemoWalletRequest,
  TopUpDemoWalletResult,
  WalletSummaryResult,
  WalletTransactionDto,
} from "./types";

function requireAuth(auth: CallerAuth | null | undefined): CallerAuth {
  if (!auth || !auth.uid || auth.uid.trim().length === 0) {
    throw new HttpsError("unauthenticated", "Vui lòng đăng nhập Google trước khi thực hiện giao dịch.");
  }
  return auth;
}

export async function isVerifiedAdminCaller(db: Firestore, auth: CallerAuth): Promise<boolean> {
  if (auth.email === BOOTSTRAPPED_ADMIN_EMAIL && auth.emailVerified === true) {
    return true;
  }
  const adminSnap = await db.collection("admins").doc(auth.uid).get();
  return adminSnap.exists;
}

async function assertVerifiedAdmin(db: Firestore, auth: CallerAuth): Promise<void> {
  const ok = await isVerifiedAdminCaller(db, auth);
  if (!ok) {
    throw new HttpsError(
      "permission-denied",
      "Từ chối truy cập: Chỉ quản trị viên được xác thực mới có quyền thực hiện thao tác này.",
    );
  }
}

async function assertActiveUserProfile(
  db: Firestore,
  uid: string,
): Promise<{ displayName: string; role: string }> {
  const snap = await db.collection("users").doc(uid).get();
  if (!snap.exists) {
    return { displayName: "Khách FOX", role: "customer" };
  }
  const data = snap.data()!;
  if (data.accountStatus === "suspended") {
    throw new HttpsError("permission-denied", "Tài khoản đang bị tạm khóa.");
  }
  return {
    displayName: typeof data.displayName === "string" && data.displayName.trim() ? data.displayName.trim() : "Khách FOX",
    role: typeof data.role === "string" ? data.role : "customer",
  };
}

function readSafeWalletInt(data: Record<string, unknown> | undefined, field: string): number {
  const raw = data?.[field];
  if (typeof raw === "number" && Number.isSafeInteger(raw) && raw >= 0) {
    return raw;
  }
  return 0;
}

function sanitizePaymentMethod(raw: unknown): FoxPaymentMethod {
  if (raw === "Ví FOX DEMO" || raw === "Tiền mặt DEMO") {
    return raw;
  }
  throw new HttpsError(
    "invalid-argument",
    "Phương thức thanh toán chỉ hỗ trợ 'Ví FOX DEMO' hoặc 'Tiền mặt DEMO' trong giai đoạn Pilot.",
  );
}

function toMillis(ts: unknown): number {
  if (ts instanceof Timestamp) {
    return ts.toMillis();
  }
  if (ts && typeof (ts as { toMillis?: () => number }).toMillis === "function") {
    return (ts as { toMillis: () => number }).toMillis();
  }
  return Date.now();
}

function makeOrderCode(serviceType: FoxServiceType, idempotencyKey: string): string {
  // Deterministic suffix derived from idempotencyKey so retries are traceable
  let hash = 0;
  for (let i = 0; i < idempotencyKey.length; i++) {
    hash = (hash * 31 + idempotencyKey.charCodeAt(i)) % 9000;
  }
  const suffix = String(1000 + Math.abs(hash)).padStart(4, "0");
  const stamp = String(Date.now() % 10000).padStart(4, "0");
  return `FOX-${serviceType}-${suffix}${stamp}`;
}

/**
 * 1. Controlled DEMO wallet top-up with Firestore Transaction + idempotency lock.
 * Rejects floats, out-of-range amounts, and exceeding MAX_WALLET_BALANCE_VND.
 */
export async function topUpDemoWalletTx(
  db: Firestore,
  callerAuth: CallerAuth | null | undefined,
  req: TopUpDemoWalletRequest,
): Promise<TopUpDemoWalletResult> {
  const auth = requireAuth(callerAuth);
  const amountVnd = assertSafeIntVnd(req?.amountVnd, "amountVnd");
  const idempotencyKey = assertValidIdempotencyKey(req?.idempotencyKey);

  if (amountVnd < MIN_TOPUP_SINGLE_VND || amountVnd > MAX_TOPUP_SINGLE_VND) {
    throw new HttpsError(
      "invalid-argument",
      `Số tiền nạp DEMO mỗi lần phải từ ${MIN_TOPUP_SINGLE_VND}đ đến ${MAX_TOPUP_SINGLE_VND}đ.`,
    );
  }
  if (amountVnd % 1_000 !== 0) {
    throw new HttpsError("invalid-argument", "Số tiền nạp DEMO phải là bội số của 1.000đ.");
  }

  await assertActiveUserProfile(db, auth.uid);

  const walletRef = db.collection("wallets").doc(auth.uid);
  const idemRef = db.collection("financial_idempotency").doc(`topup_${auth.uid}_${idempotencyKey}`);
  const txId = `TX-TOPUP-${idempotencyKey.slice(0, 24)}`;
  const txRef = walletRef.collection("transactions").doc(txId);

  return db.runTransaction(async (tx) => {
    const idemSnap = await tx.get(idemRef);
    if (idemSnap.exists) {
      const prev = idemSnap.data()!;
      return {
        txId: String(prev.txId),
        amountVnd: assertSafeIntVnd(prev.amountVnd, "cached.amountVnd"),
        balanceAfterVnd: assertSafeIntVnd(prev.balanceAfterVnd, "cached.balanceAfterVnd"),
        status: "succeeded",
        idempotentReplay: true,
        isDemo: true,
        demoNotice: DEMO_CURRENCY_NOTICE,
      };
    }

    const walletSnap = await tx.get(walletRef);
    const walletData = walletSnap.exists ? (walletSnap.data() as Record<string, unknown>) : undefined;
    const currentBalance = readSafeWalletInt(walletData, "balanceVnd");
    const totalTopUp = readSafeWalletInt(walletData, "totalTopUpVnd");
    const totalSpent = readSafeWalletInt(walletData, "totalSpentVnd");
    const totalEarned = readSafeWalletInt(walletData, "totalEarnedVnd");
    const totalRefunded = readSafeWalletInt(walletData, "totalRefundedVnd");

    const nextBalance = currentBalance + amountVnd;
    if (nextBalance > MAX_WALLET_BALANCE_VND) {
      throw new HttpsError(
        "failed-precondition",
        `Số dư Ví FOX DEMO không được vượt quá ${MAX_WALLET_BALANCE_VND}đ.`,
      );
    }

    const now = FieldValue.serverTimestamp();
    tx.set(
      walletRef,
      {
        userId: auth.uid,
        balanceVnd: nextBalance,
        totalTopUpVnd: totalTopUp + amountVnd,
        totalSpentVnd: totalSpent,
        totalEarnedVnd: totalEarned,
        totalRefundedVnd: totalRefunded,
        currency: "VND",
        isDemo: true,
        demoNotice: DEMO_CURRENCY_NOTICE,
        createdAt: walletSnap.exists ? walletData!.createdAt ?? now : now,
        updatedAt: now,
      },
      { merge: true },
    );

    tx.set(txRef, {
      txId,
      userId: auth.uid,
      txType: "TOPUP_DEMO",
      title: `Nạp Ví FOX DEMO (+${amountVnd}đ)`,
      amountVnd,
      balanceAfterVnd: nextBalance,
      relatedOrderCode: "",
      status: "succeeded",
      idempotencyKey,
      isDemo: true,
      demoNotice: DEMO_CURRENCY_NOTICE,
      createdAt: now,
    });

    tx.set(idemRef, {
      scope: "topup_demo",
      userId: auth.uid,
      txId,
      amountVnd,
      balanceAfterVnd: nextBalance,
      createdAt: now,
    });

    return {
      txId,
      amountVnd,
      balanceAfterVnd: nextBalance,
      status: "succeeded",
      idempotentReplay: false,
      isDemo: true,
      demoNotice: DEMO_CURRENCY_NOTICE,
    };
  });
}

/**
 * 2. Authoritative order creation + atomic DEMO wallet payment (if Ví FOX DEMO selected).
 * Server ignores any client prices, validates voucher strictly, and uses idempotency key.
 */
export async function createVerifiedOrderAndPayDemoTx(
  db: Firestore,
  callerAuth: CallerAuth | null | undefined,
  req: CreateVerifiedOrderRequest,
): Promise<CreateVerifiedOrderResult> {
  const auth = requireAuth(callerAuth);
  const idempotencyKey = assertValidIdempotencyKey(req?.idempotencyKey);
  const paymentMethod = sanitizePaymentMethod(req?.paymentMethod);

  const primaryAddress = typeof req?.primaryAddress === "string" ? req.primaryAddress.trim() : "";
  const secondaryAddress = typeof req?.secondaryAddress === "string" ? req.secondaryAddress.trim() : "";
  if (primaryAddress.length < 1 || primaryAddress.length > 240 || secondaryAddress.length < 1 || secondaryAddress.length > 240) {
    throw new HttpsError("invalid-argument", "Địa chỉ điểm đón/lấy và điểm giao phải từ 1–240 ký tự.");
  }

  const profile = await assertActiveUserProfile(db, auth.uid);

  const serviceType = assertValidServiceType(req?.serviceType);
  const cleanMerchantId = typeof req?.merchantId === "string" ? req.merchantId.trim() : "";
  const isCloudMerchantOrder =
    (serviceType === "FOOD" || serviceType === "SHOPPING") && cleanMerchantId.length > 0;

  if (isCloudMerchantOrder && !/^[a-zA-Z0-9_\-]{1,128}$/.test(cleanMerchantId)) {
    throw new HttpsError("invalid-argument", "Mã gian hàng (merchantId) không hợp lệ.");
  }

  const normalizedMerchantItems = isCloudMerchantOrder ? normalizeOrderItems(req.items) : [];

  // For RIDE/DELIVERY or legacy catalog without merchantId, compute authoritative quote up-front.
  // For Cloud Merchant orders, prices + store open status + stock are read & locked atomically INSIDE the transaction.
  const precomputedQuote = isCloudMerchantOrder
    ? null
    : await calculateAuthoritativeQuote(db, {
        ...req,
        strictVoucher: true,
      });

  const walletRef = db.collection("wallets").doc(auth.uid);
  const idemRef = db.collection("financial_idempotency").doc(`create_order_${auth.uid}_${idempotencyKey}`);
  const orderCode = makeOrderCode(serviceType, idempotencyKey);
  const orderRef = db.collection("orders").doc(orderCode);
  const dispatchTarget = serviceType === "RIDE" || serviceType === "DELIVERY" ? "driver" : "merchant";

  return db.runTransaction(async (tx) => {
    const idemSnap = await tx.get(idemRef);
    if (idemSnap.exists) {
      const prev = idemSnap.data()!;
      return {
        orderId: String(prev.orderId),
        orderCode: String(prev.orderCode),
        merchantId: String(prev.merchantId || ""),
        merchantName: String(prev.merchantName || ""),
        quote: prev.quote as CreateVerifiedOrderResult["quote"],
        paymentMethod: prev.paymentMethod as FoxPaymentMethod,
        paymentStatus: String(prev.paymentStatus),
        financialStatus: String(prev.financialStatus),
        stockDeducted: prev.stockDeducted === true,
        walletBalanceAfterVnd:
          typeof prev.walletBalanceAfterVnd === "number" ? prev.walletBalanceAfterVnd : null,
        idempotentReplay: true,
        isDemo: true,
        demoNotice: DEMO_CURRENCY_NOTICE,
      };
    }

    // --- ALL TRANSACTION READS FIRST ---
    let quote = precomputedQuote;
    let resolvedMerchantId = "";
    let resolvedMerchantName = "";
    let resolvedPrimaryAddress = primaryAddress;
    const stockUpdates: Array<{
      ref: FirebaseFirestore.DocumentReference;
      nextStock: number;
    }> = [];

    if (isCloudMerchantOrder) {
      const storeRef = db.collection("merchants").doc(cleanMerchantId);
      const productRefs = normalizedMerchantItems.map((item) =>
        storeRef.collection("products").doc(item.itemId),
      );
      const [storeSnap, ...prodSnaps] = await Promise.all([
        tx.get(storeRef),
        ...productRefs.map((ref) => tx.get(ref)),
      ]);

      if (!storeSnap.exists) {
        throw new HttpsError("not-found", `Không tìm thấy gian hàng "${cleanMerchantId}".`);
      }
      const storeData = storeSnap.data()!;
      if (storeData.active !== true) {
        throw new HttpsError("failed-precondition", "Gian hàng hiện đang ngừng hoạt động.");
      }
      if (storeData.openNow !== true) {
        throw new HttpsError(
          "failed-precondition",
          `Gian hàng "${storeData.name || cleanMerchantId}" hiện đang đóng cửa.`,
        );
      }
      if (storeData.storeType && storeData.storeType !== serviceType) {
        throw new HttpsError("invalid-argument", "Gian hàng không phù hợp với dịch vụ đã chọn.");
      }

      resolvedMerchantId = cleanMerchantId;
      resolvedMerchantName = String(storeData.name || "Gian hàng FOX").slice(0, 80);
      if (typeof storeData.address === "string" && storeData.address.trim().length > 0) {
        resolvedPrimaryAddress = storeData.address.trim().slice(0, 240);
      }

      const resolvedLines = [];
      let subtotalVnd = 0;

      for (let i = 0; i < normalizedMerchantItems.length; i++) {
        const reqItem = normalizedMerchantItems[i];
        const prodSnap = prodSnaps[i];
        if (!prodSnap.exists) {
          throw new HttpsError(
            "not-found",
            `Sản phẩm "${reqItem.itemId}" không tồn tại trong gian hàng "${resolvedMerchantName}".`,
          );
        }
        const pData = prodSnap.data()!;
        if (pData.active !== true) {
          throw new HttpsError(
            "failed-precondition",
            `Sản phẩm "${pData.name || reqItem.itemId}" hiện đang ẩn hoặc ngừng bán.`,
          );
        }
        const currentStock = assertSafeIntVnd(pData.stock, `product.stock(${reqItem.itemId})`);
        if (currentStock < reqItem.quantity) {
          throw new HttpsError(
            "failed-precondition",
            `Sản phẩm "${pData.name || reqItem.itemId}" không đủ tồn kho (còn ${currentStock}, yêu cầu ${reqItem.quantity}).`,
          );
        }
        const unitPriceVnd = assertSafeIntVnd(pData.priceVnd, `product.priceVnd(${reqItem.itemId})`);
        const lineTotalVnd = unitPriceVnd * reqItem.quantity;
        subtotalVnd += lineTotalVnd;

        resolvedLines.push({
          itemId: reqItem.itemId,
          name: String(pData.name || reqItem.itemId),
          unitPriceVnd,
          quantity: reqItem.quantity,
          lineTotalVnd,
        });

        stockUpdates.push({
          ref: productRefs[i],
          nextStock: currentStock - reqItem.quantity,
        });
      }

      const feeVnd =
        serviceType === "FOOD"
          ? subtotalVnd > 0
            ? 15_000
            : 0
          : subtotalVnd >= 300_000 || subtotalVnd === 0
            ? 0
            : 22_000;

      quote = buildQuoteFromResolvedSubtotal(
        serviceType,
        subtotalVnd,
        feeVnd,
        resolvedLines,
        { ...req, strictVoucher: true },
      );
    }

    const finalQuote = quote!;
    const voucherRedemptionRef =
      finalQuote.voucherApplied && finalQuote.voucherCode.length > 0
        ? db.collection("voucher_redemptions").doc(`${auth.uid}_${finalQuote.voucherCode}`)
        : null;
    const [walletSnap, voucherRedemptionSnap] = await Promise.all([
      paymentMethod === "Ví FOX DEMO" ? tx.get(walletRef) : Promise.resolve(null),
      voucherRedemptionRef ? tx.get(voucherRedemptionRef) : Promise.resolve(null),
    ]);
    if (voucherRedemptionSnap?.exists) {
      throw new HttpsError(
        "already-exists",
        `Mã khuyến mãi "${finalQuote.voucherCode}" đã được tài khoản của bạn sử dụng trước đó.`,
      );
    }

    const title = (
      typeof req.title === "string" && req.title.trim().length > 0
        ? req.title.trim()
        : resolvedMerchantName
          ? `${resolvedMerchantName} (${finalQuote.resolvedItems.reduce((s, l) => s + l.quantity, 0)} món)`
          : `Đơn ${finalQuote.serviceType}`
    ).slice(0, 120);

    const computedSummary =
      finalQuote.resolvedItems.length > 0
        ? finalQuote.resolvedItems.map((l) => `${l.name} x${l.quantity}`).join(", ")
        : `${req.vehicleType || req.speed || finalQuote.serviceType}`;

    const detailSummary = (
      typeof req.detailSummary === "string" && req.detailSummary.trim().length > 0
        ? req.detailSummary.trim()
        : computedSummary
    ).slice(0, 400);

    // --- ALL TRANSACTION WRITES AFTER READS ---
    const now = FieldValue.serverTimestamp();
    let paymentStatus = "pending_cash_demo";
    let paymentId = "";
    let walletBalanceAfterVnd: number | null = null;

    if (paymentMethod === "Ví FOX DEMO") {
      const walletData = walletSnap?.exists ? (walletSnap.data() as Record<string, unknown>) : undefined;
      const currentBalance = readSafeWalletInt(walletData, "balanceVnd");
      if (currentBalance < finalQuote.totalVnd) {
        throw new HttpsError(
          "failed-precondition",
          `Số dư Ví FOX DEMO không đủ (${currentBalance}đ < ${finalQuote.totalVnd}đ). Vui lòng nạp DEMO hoặc chọn Tiền mặt DEMO.`,
        );
      }

      const nextBalance = currentBalance - finalQuote.totalVnd;
      const totalSpent = readSafeWalletInt(walletData, "totalSpentVnd");
      paymentId = `TX-PAY-${orderCode}`;
      paymentStatus = "paid_demo";
      walletBalanceAfterVnd = nextBalance;

      tx.set(
        walletRef,
        {
          userId: auth.uid,
          balanceVnd: nextBalance,
          totalTopUpVnd: readSafeWalletInt(walletData, "totalTopUpVnd"),
          totalSpentVnd: totalSpent + finalQuote.totalVnd,
          totalEarnedVnd: readSafeWalletInt(walletData, "totalEarnedVnd"),
          totalRefundedVnd: readSafeWalletInt(walletData, "totalRefundedVnd"),
          currency: "VND",
          isDemo: true,
          demoNotice: DEMO_CURRENCY_NOTICE,
          createdAt: walletSnap?.exists ? walletData!.createdAt ?? now : now,
          updatedAt: now,
        },
        { merge: true },
      );

      tx.set(walletRef.collection("transactions").doc(paymentId), {
        txId: paymentId,
        userId: auth.uid,
        txType: "PAYMENT_DEMO",
        title: `Thanh toán DEMO đơn ${orderCode}`,
        amountVnd: -finalQuote.totalVnd,
        balanceAfterVnd: nextBalance,
        relatedOrderCode: orderCode,
        status: "succeeded",
        idempotencyKey,
        isDemo: true,
        demoNotice: DEMO_CURRENCY_NOTICE,
        createdAt: now,
      });
    }

    // Atomically deduct product stocks inside the SAME transaction
    for (const upd of stockUpdates) {
      tx.update(upd.ref, {
        stock: upd.nextStock,
        updatedAt: now,
      });
    }

    const stockDeducted = stockUpdates.length > 0;
    // Targeted merchant orders are strictly isolated (dispatchOpen = false) so OTHER merchants cannot see or claim them!
    const dispatchOpen = !isCloudMerchantOrder;

    const routeFields = finalQuote.verifiedRoute
      ? {
          pickupLat: finalQuote.verifiedRoute.origin.lat,
          pickupLng: finalQuote.verifiedRoute.origin.lng,
          dropoffLat: finalQuote.verifiedRoute.destination.lat,
          dropoffLng: finalQuote.verifiedRoute.destination.lng,
          distanceMeters: finalQuote.verifiedRoute.distanceMeters,
          distanceKm: finalQuote.verifiedRoute.distanceKm,
          durationMinutes: finalQuote.verifiedRoute.durationMinutes,
          routeSource: finalQuote.verifiedRoute.routeSource,
          isDemoRoute: finalQuote.verifiedRoute.isDemoRoute,
          encodedPolyline: finalQuote.verifiedRoute.encodedPolyline,
        }
      : {};

    tx.set(orderRef, {
      orderCode,
      serviceType: finalQuote.serviceType,
      customerId: auth.uid,
      customerName: profile.displayName.slice(0, 80),
      driverId: "",
      driverName: "",
      merchantId: resolvedMerchantId,
      merchantName: resolvedMerchantName,
      title,
      primaryAddress: resolvedPrimaryAddress,
      secondaryAddress,
      detailSummary,
      subtotalVnd: finalQuote.subtotalVnd,
      feeVnd: finalQuote.feeVnd,
      discountVnd: finalQuote.discountVnd,
      totalVnd: finalQuote.totalVnd,
      paymentMethod,
      voucherCode: finalQuote.voucherCode,
      financialStatus: "verified_demo",
      paymentStatus,
      paymentId,
      refundStatus: "none",
      driverCommissionVnd: 0,
      merchantCommissionVnd: 0,
      platformRevenueVnd: 0,
      commissionSettled: false,
      stockDeducted,
      stockRestored: false,
      items: finalQuote.resolvedItems,
      ...routeFields,
      status: "pending",
      dispatchOpen,
      dispatchTarget,
      createdAt: now,
      updatedAt: now,
    });

    tx.set(db.collection("users").doc(auth.uid).collection("notifications").doc(`n_${orderCode}`), {
      title: `Đã tạo đơn ${orderCode} (Xác thực server)`,
      body:
        paymentStatus === "paid_demo"
          ? `${title} • Đã thanh toán ${finalQuote.totalVnd}đ qua Ví FOX DEMO (DEMO — KHÔNG PHẢI TIỀN THẬT).`
          : `${title} • Tổng ${finalQuote.totalVnd}đ (${paymentMethod}).`,
      relatedOrderCode: orderCode,
      readFlag: false,
      createdAt: now,
    });

    if (voucherRedemptionRef) {
      tx.set(voucherRedemptionRef, {
        userId: auth.uid,
        voucherCode: finalQuote.voucherCode,
        orderId: orderCode,
        orderCode,
        serviceType: finalQuote.serviceType,
        discountVnd: finalQuote.discountVnd,
        isDemo: true,
        createdAt: now,
      });
    }

    tx.set(idemRef, {
      scope: "create_order_demo",
      userId: auth.uid,
      orderId: orderCode,
      orderCode,
      merchantId: resolvedMerchantId,
      merchantName: resolvedMerchantName,
      quote: finalQuote,
      paymentMethod,
      paymentStatus,
      financialStatus: "verified_demo",
      stockDeducted,
      walletBalanceAfterVnd,
      createdAt: now,
    });

    return {
      orderId: orderCode,
      orderCode,
      merchantId: resolvedMerchantId,
      merchantName: resolvedMerchantName,
      quote: finalQuote,
      paymentMethod,
      paymentStatus,
      financialStatus: "verified_demo",
      stockDeducted,
      walletBalanceAfterVnd,
      idempotentReplay: false,
      isDemo: true,
      demoNotice: DEMO_CURRENCY_NOTICE,
    };
  });
}

/**
 * 3. Pay an existing unpaid order with Ví FOX DEMO atomically.
 * Protects against insufficient balance, duplicate idempotencyKey, AND concurrent double-pay.
 */
export async function payOrderWithDemoWalletTx(
  db: Firestore,
  callerAuth: CallerAuth | null | undefined,
  req: PayOrderWithDemoWalletRequest,
): Promise<PayOrderWithDemoWalletResult> {
  const auth = requireAuth(callerAuth);
  const orderId = typeof req?.orderId === "string" ? req.orderId.trim() : "";
  if (!/^[a-zA-Z0-9_\-]{1,128}$/.test(orderId)) {
    throw new HttpsError("invalid-argument", "Mã đơn hàng không hợp lệ.");
  }
  const idempotencyKey = assertValidIdempotencyKey(req?.idempotencyKey);

  await assertActiveUserProfile(db, auth.uid);

  const orderRef = db.collection("orders").doc(orderId);
  const walletRef = db.collection("wallets").doc(auth.uid);
  const idemRef = db.collection("financial_idempotency").doc(`pay_${auth.uid}_${idempotencyKey}`);

  return db.runTransaction(async (tx) => {
    const idemSnap = await tx.get(idemRef);
    if (idemSnap.exists) {
      const prev = idemSnap.data()!;
      return {
        orderId: String(prev.orderId),
        txId: String(prev.txId),
        amountVnd: assertSafeIntVnd(prev.amountVnd, "cached.amountVnd"),
        balanceAfterVnd: assertSafeIntVnd(prev.balanceAfterVnd, "cached.balanceAfterVnd"),
        paymentStatus: "paid_demo",
        status: "succeeded",
        idempotentReplay: true,
        isDemo: true,
        demoNotice: DEMO_CURRENCY_NOTICE,
      };
    }

    const orderSnap = await tx.get(orderRef);
    if (!orderSnap.exists) {
      throw new HttpsError("not-found", "Không tìm thấy đơn hàng.");
    }
    const order = orderSnap.data()!;
    if (order.customerId !== auth.uid) {
      throw new HttpsError("permission-denied", "Bạn chỉ có thể thanh toán đơn hàng của chính mình.");
    }
    if (order.status === "cancelled") {
      throw new HttpsError("failed-precondition", "Không thể thanh toán đơn hàng đã hủy.");
    }
    if (order.paymentStatus === "paid_demo" || order.paymentStatus === "refunded_demo") {
      throw new HttpsError("already-exists", "Đơn hàng này đã được thanh toán Ví FOX DEMO trước đó.");
    }

    const amountVnd = assertSafeIntVnd(order.totalVnd, "order.totalVnd");
    const walletSnap = await tx.get(walletRef);
    const walletData = walletSnap.exists ? (walletSnap.data() as Record<string, unknown>) : undefined;
    const currentBalance = readSafeWalletInt(walletData, "balanceVnd");
    if (currentBalance < amountVnd) {
      throw new HttpsError(
        "failed-precondition",
        `Số dư Ví FOX DEMO không đủ (${currentBalance}đ < ${amountVnd}đ).`,
      );
    }

    const nextBalance = currentBalance - amountVnd;
    const txId = `TX-PAY-${orderId}`;
    const now = FieldValue.serverTimestamp();

    tx.set(
      walletRef,
      {
        userId: auth.uid,
        balanceVnd: nextBalance,
        totalTopUpVnd: readSafeWalletInt(walletData, "totalTopUpVnd"),
        totalSpentVnd: readSafeWalletInt(walletData, "totalSpentVnd") + amountVnd,
        totalEarnedVnd: readSafeWalletInt(walletData, "totalEarnedVnd"),
        totalRefundedVnd: readSafeWalletInt(walletData, "totalRefundedVnd"),
        currency: "VND",
        isDemo: true,
        demoNotice: DEMO_CURRENCY_NOTICE,
        createdAt: walletSnap.exists ? walletData!.createdAt ?? now : now,
        updatedAt: now,
      },
      { merge: true },
    );

    tx.set(walletRef.collection("transactions").doc(txId), {
      txId,
      userId: auth.uid,
      txType: "PAYMENT_DEMO",
      title: `Thanh toán DEMO đơn ${order.orderCode || orderId}`,
      amountVnd: -amountVnd,
      balanceAfterVnd: nextBalance,
      relatedOrderCode: String(order.orderCode || orderId),
      status: "succeeded",
      idempotencyKey,
      isDemo: true,
      demoNotice: DEMO_CURRENCY_NOTICE,
      createdAt: now,
    });

    tx.update(orderRef, {
      paymentMethod: "Ví FOX DEMO",
      paymentStatus: "paid_demo",
      paymentId: txId,
      financialStatus: "verified_demo",
      updatedAt: now,
    });

    tx.set(idemRef, {
      scope: "pay_order_demo",
      userId: auth.uid,
      orderId,
      txId,
      amountVnd,
      balanceAfterVnd: nextBalance,
      createdAt: now,
    });

    return {
      orderId,
      txId,
      amountVnd,
      balanceAfterVnd: nextBalance,
      paymentStatus: "paid_demo",
      status: "succeeded",
      idempotentReplay: false,
      isDemo: true,
      demoNotice: DEMO_CURRENCY_NOTICE,
    };
  });
}

function canCallerCancelOrder(
  order: Record<string, unknown>,
  uid: string,
  isAdmin: boolean,
): boolean {
  if (isAdmin) return order.status !== "completed";
  const serviceType = String(order.serviceType || "");
  const status = String(order.status || "");

  if (order.customerId === uid) {
    if (serviceType === "RIDE" || serviceType === "DELIVERY") {
      return status === "pending" || status === "driver_accepted";
    }
    if (serviceType === "FOOD" || serviceType === "SHOPPING") {
      return status === "pending";
    }
  }
  if (order.driverId === uid) {
    return (
      (serviceType === "RIDE" || serviceType === "DELIVERY") &&
      (status === "driver_accepted" || status === "driver_arrived")
    );
  }
  if (order.merchantId === uid) {
    return (
      (serviceType === "FOOD" || serviceType === "SHOPPING") &&
      (status === "pending" || status === "merchant_confirmed")
    );
  }
  return false;
}

/**
 * 4. Atomic Cancel + Refund DEMO wallet + Restore Cloud Merchant Stock transaction.
 * Guarantees an order can NEVER be refunded twice and stock can NEVER be restored twice!
 */
export async function refundOrderDemoTx(
  db: Firestore,
  callerAuth: CallerAuth | null | undefined,
  req: RefundOrderDemoRequest,
): Promise<RefundOrderDemoResult> {
  const auth = requireAuth(callerAuth);
  const orderId = typeof req?.orderId === "string" ? req.orderId.trim() : "";
  if (!/^[a-zA-Z0-9_\-]{1,128}$/.test(orderId)) {
    throw new HttpsError("invalid-argument", "Mã đơn hàng không hợp lệ.");
  }
  const idempotencyKey = assertValidIdempotencyKey(req?.idempotencyKey);
  const isAdmin = await isVerifiedAdminCaller(db, auth);

  const orderRef = db.collection("orders").doc(orderId);
  const idemRef = db.collection("financial_idempotency").doc(`refund_${orderId}_${idempotencyKey}`);

  return db.runTransaction(async (tx) => {
    const idemSnap = await tx.get(idemRef);
    if (idemSnap.exists) {
      const prev = idemSnap.data()!;
      return {
        orderId: String(prev.orderId),
        txId: prev.txId ? String(prev.txId) : null,
        refundedAmountVnd: assertSafeIntVnd(prev.refundedAmountVnd, "cached.refundedAmountVnd"),
        customerBalanceAfterVnd:
          typeof prev.customerBalanceAfterVnd === "number" ? prev.customerBalanceAfterVnd : null,
        orderStatus: "cancelled",
        paymentStatus: String(prev.paymentStatus),
        refundStatus: String(prev.refundStatus),
        stockRestored: prev.stockRestored === true,
        idempotentReplay: true,
        isDemo: true,
        demoNotice: DEMO_CURRENCY_NOTICE,
      };
    }

    const orderSnap = await tx.get(orderRef);
    if (!orderSnap.exists) {
      throw new HttpsError("not-found", "Không tìm thấy đơn hàng.");
    }
    const order = orderSnap.data()!;
    const customerId = String(order.customerId || "");
    const merchantId = String(order.merchantId || "");
    const isParticipant =
      customerId === auth.uid || order.driverId === auth.uid || merchantId === auth.uid || isAdmin;
    if (!isParticipant) {
      throw new HttpsError("permission-denied", "Bạn không có quyền hủy hoặc hoàn tiền đơn hàng này.");
    }

    if (order.refundStatus === "refunded_demo") {
      throw new HttpsError("already-exists", "Đơn hàng này đã được hoàn tiền DEMO trước đó.");
    }
    if (order.status === "cancelled" && order.stockRestored === true && order.paymentStatus !== "paid_demo") {
      throw new HttpsError("already-exists", "Đơn hàng này đã được hủy và hoàn tồn kho trước đó.");
    }
    if (order.status === "completed") {
      throw new HttpsError("failed-precondition", "Đơn hàng đã hoàn thành, không thể hủy/hoàn tiền.");
    }
    if (order.status !== "cancelled" && !canCallerCancelOrder(order, auth.uid, isAdmin)) {
      throw new HttpsError(
        "failed-precondition",
        `Không thể hủy đơn ở trạng thái hiện tại (${order.status}) theo quy tắc dịch vụ ${order.serviceType}.`,
      );
    }

    // --- PERFORM ALL READS BEFORE ANY WRITE ---
    const shouldRefundWallet =
      order.paymentStatus === "paid_demo" && assertSafeIntVnd(order.totalVnd, "order.totalVnd") > 0;
    const customerWalletRef = shouldRefundWallet ? db.collection("wallets").doc(customerId) : null;
    const walletSnap = customerWalletRef ? await tx.get(customerWalletRef) : null;

    const rawItems = Array.isArray(order.items) ? (order.items as Array<Record<string, unknown>>) : [];
    const shouldRestoreStock =
      order.stockDeducted === true &&
      order.stockRestored !== true &&
      merchantId.length > 0 &&
      rawItems.length > 0;

    const stockRestoreOps: Array<{
      ref: FirebaseFirestore.DocumentReference;
      nextStock: number;
    }> = [];

    if (shouldRestoreStock) {
      const prodReads = rawItems
        .filter((it) => typeof it.itemId === "string" && it.itemId.trim().length > 0)
        .map((it) => ({
          itemId: String(it.itemId).trim(),
          quantity: assertSafeIntVnd(it.quantity, `order.items.quantity(${it.itemId})`),
          ref: db.collection("merchants").doc(merchantId).collection("products").doc(String(it.itemId).trim()),
        }));
      const prodSnaps = await Promise.all(prodReads.map((p) => tx.get(p.ref)));
      for (let i = 0; i < prodReads.length; i++) {
        const snap = prodSnaps[i];
        if (snap.exists) {
          const currentStock = readSafeWalletInt(snap.data() as Record<string, unknown>, "stock");
          stockRestoreOps.push({
            ref: prodReads[i].ref,
            nextStock: currentStock + prodReads[i].quantity,
          });
        }
      }
    }

    // --- PERFORM ALL WRITES ---
    const now = FieldValue.serverTimestamp();
    let txId: string | null = null;
    let refundedAmountVnd = 0;
    let customerBalanceAfterVnd: number | null = null;
    let nextPaymentStatus = String(order.paymentStatus || "pending_cash_demo");
    let nextRefundStatus = "not_applicable";

    if (shouldRefundWallet && customerWalletRef) {
      refundedAmountVnd = assertSafeIntVnd(order.totalVnd, "order.totalVnd");
      const walletData = walletSnap?.exists ? (walletSnap.data() as Record<string, unknown>) : undefined;
      const currentBalance = readSafeWalletInt(walletData, "balanceVnd");
      customerBalanceAfterVnd = currentBalance + refundedAmountVnd;
      txId = `TX-REF-${orderId}`;
      nextPaymentStatus = "refunded_demo";
      nextRefundStatus = "refunded_demo";

      tx.set(
        customerWalletRef,
        {
          userId: customerId,
          balanceVnd: customerBalanceAfterVnd,
          totalTopUpVnd: readSafeWalletInt(walletData, "totalTopUpVnd"),
          totalSpentVnd: readSafeWalletInt(walletData, "totalSpentVnd"),
          totalEarnedVnd: readSafeWalletInt(walletData, "totalEarnedVnd"),
          totalRefundedVnd: readSafeWalletInt(walletData, "totalRefundedVnd") + refundedAmountVnd,
          currency: "VND",
          isDemo: true,
          demoNotice: DEMO_CURRENCY_NOTICE,
          createdAt: walletSnap?.exists ? walletData!.createdAt ?? now : now,
          updatedAt: now,
        },
        { merge: true },
      );

      tx.set(customerWalletRef.collection("transactions").doc(txId), {
        txId,
        userId: customerId,
        txType: "REFUND_DEMO",
        title: `Hoàn tiền DEMO đơn ${order.orderCode || orderId}`,
        amountVnd: refundedAmountVnd,
        balanceAfterVnd: customerBalanceAfterVnd,
        relatedOrderCode: String(order.orderCode || orderId),
        status: "succeeded",
        idempotencyKey,
        isDemo: true,
        demoNotice: DEMO_CURRENCY_NOTICE,
        createdAt: now,
      });

      tx.set(db.collection("users").doc(customerId).collection("notifications").doc(`n_ref_${orderId}`), {
        title: `Đã hoàn tiền DEMO đơn ${order.orderCode || orderId}`,
        body: `Đã hoàn +${refundedAmountVnd}đ về Ví FOX DEMO (DEMO — KHÔNG PHẢI TIỀN THẬT).`,
        relatedOrderCode: String(order.orderCode || orderId),
        readFlag: false,
        createdAt: now,
      });
    }

    for (const op of stockRestoreOps) {
      tx.update(op.ref, {
        stock: op.nextStock,
        updatedAt: now,
      });
    }

    const stockRestored = stockRestoreOps.length > 0;

    tx.update(orderRef, {
      status: "cancelled",
      dispatchOpen: false,
      dispatchTarget: "none",
      paymentStatus: nextPaymentStatus,
      refundStatus: nextRefundStatus,
      stockRestored: order.stockRestored === true || stockRestored,
      updatedAt: now,
    });

    tx.set(idemRef, {
      scope: "refund_order_demo",
      userId: auth.uid,
      orderId,
      txId,
      refundedAmountVnd,
      customerBalanceAfterVnd,
      paymentStatus: nextPaymentStatus,
      refundStatus: nextRefundStatus,
      stockRestored,
      createdAt: now,
    });

    return {
      orderId,
      txId,
      refundedAmountVnd,
      customerBalanceAfterVnd,
      orderStatus: "cancelled",
      paymentStatus: nextPaymentStatus,
      refundStatus: nextRefundStatus,
      stockRestored,
      idempotentReplay: false,
      isDemo: true,
      demoNotice: DEMO_CURRENCY_NOTICE,
    };
  });
}

/**
 * 5. Atomic commission & FOX DEMO platform revenue settlement on completed orders.
 * Strictly server-computed integer splits; NEVER pays commission twice!
 */
export async function settleOrderCommissionDemoTx(
  db: Firestore,
  callerAuth: CallerAuth | null | undefined,
  req: SettleOrderCommissionRequest,
): Promise<SettleOrderCommissionResult> {
  const auth = requireAuth(callerAuth);
  const orderId = typeof req?.orderId === "string" ? req.orderId.trim() : "";
  if (!/^[a-zA-Z0-9_\-]{1,128}$/.test(orderId)) {
    throw new HttpsError("invalid-argument", "Mã đơn hàng không hợp lệ.");
  }

  const isAdmin = await isVerifiedAdminCaller(db, auth);
  const orderRef = db.collection("orders").doc(orderId);
  const settlementIdemRef = db.collection("financial_idempotency").doc(`settle_${orderId}`);
  const revenueRef = db.collection("platform_revenue").doc(`rev_${orderId}`);

  return db.runTransaction(async (tx) => {
    const idemSnap = await tx.get(settlementIdemRef);
    if (idemSnap.exists) {
      const prev = idemSnap.data()!;
      return {
        orderId,
        grossTotalVnd: assertSafeIntVnd(prev.grossTotalVnd, "cached.grossTotalVnd"),
        driverCommissionVnd: assertSafeIntVnd(prev.driverCommissionVnd, "cached.driverCommissionVnd"),
        merchantCommissionVnd: assertSafeIntVnd(prev.merchantCommissionVnd, "cached.merchantCommissionVnd"),
        platformRevenueVnd: assertSafeIntVnd(prev.platformRevenueVnd, "cached.platformRevenueVnd"),
        settled: true,
        idempotentReplay: true,
        isDemo: true,
        demoNotice: DEMO_CURRENCY_NOTICE,
      };
    }

    const orderSnap = await tx.get(orderRef);
    if (!orderSnap.exists) {
      throw new HttpsError("not-found", "Không tìm thấy đơn hàng.");
    }
    const order = orderSnap.data()!;
    const driverId = String(order.driverId || "");
    const merchantId = String(order.merchantId || "");

    const canSettle = isAdmin || (driverId && driverId === auth.uid) || (merchantId && merchantId === auth.uid);
    if (!canSettle) {
      throw new HttpsError(
        "permission-denied",
        "Chỉ Tài xế/Gian hàng phụ trách đơn hoặc Quản trị viên mới được quyết toán hoa hồng.",
      );
    }

    if (order.commissionSettled === true) {
      return {
        orderId,
        grossTotalVnd: assertSafeIntVnd(order.totalVnd, "order.totalVnd"),
        driverCommissionVnd: assertSafeIntVnd(order.driverCommissionVnd ?? 0, "order.driverCommissionVnd"),
        merchantCommissionVnd: assertSafeIntVnd(order.merchantCommissionVnd ?? 0, "order.merchantCommissionVnd"),
        platformRevenueVnd: assertSafeIntVnd(order.platformRevenueVnd ?? 0, "order.platformRevenueVnd"),
        settled: true,
        idempotentReplay: true,
        isDemo: true,
        demoNotice: DEMO_CURRENCY_NOTICE,
      };
    }

    if (order.status !== "completed") {
      throw new HttpsError(
        "failed-precondition",
        `Chỉ có thể chia hoa hồng khi đơn đã hoàn thành (hiện tại: ${order.status}).`,
      );
    }

    const serviceType = assertValidServiceType(order.serviceType);
    const subtotalVnd = assertSafeIntVnd(order.subtotalVnd, "order.subtotalVnd");
    const feeVnd = assertSafeIntVnd(order.feeVnd, "order.feeVnd");
    const totalVnd = assertSafeIntVnd(order.totalVnd, "order.totalVnd");

    const split = calculateCommissionSplit(serviceType, subtotalVnd, feeVnd, totalVnd);

    // Perform ALL reads before writes inside Firestore Transaction
    const driverWalletRef = driverId ? db.collection("wallets").doc(driverId) : null;
    const merchantWalletRef =
      merchantId && merchantId !== driverId ? db.collection("wallets").doc(merchantId) : null;

    const driverWalletSnap = driverWalletRef && split.driverCommissionVnd > 0 ? await tx.get(driverWalletRef) : null;
    const merchantWalletSnap =
      merchantWalletRef && split.merchantCommissionVnd > 0 ? await tx.get(merchantWalletRef) : null;

    const now = FieldValue.serverTimestamp();

    if (driverWalletRef && split.driverCommissionVnd > 0) {
      const wData = driverWalletSnap?.exists ? (driverWalletSnap.data() as Record<string, unknown>) : undefined;
      const nextDriverBalance = readSafeWalletInt(wData, "balanceVnd") + split.driverCommissionVnd;
      const driverTxId = `TX-DRV-${orderId}`;
      tx.set(
        driverWalletRef,
        {
          userId: driverId,
          balanceVnd: nextDriverBalance,
          totalTopUpVnd: readSafeWalletInt(wData, "totalTopUpVnd"),
          totalSpentVnd: readSafeWalletInt(wData, "totalSpentVnd"),
          totalEarnedVnd: readSafeWalletInt(wData, "totalEarnedVnd") + split.driverCommissionVnd,
          totalRefundedVnd: readSafeWalletInt(wData, "totalRefundedVnd"),
          currency: "VND",
          isDemo: true,
          demoNotice: DEMO_CURRENCY_NOTICE,
          createdAt: driverWalletSnap?.exists ? wData!.createdAt ?? now : now,
          updatedAt: now,
        },
        { merge: true },
      );
      tx.set(driverWalletRef.collection("transactions").doc(driverTxId), {
        txId: driverTxId,
        userId: driverId,
        txType: "DRIVER_COMMISSION_DEMO",
        title: `Hoa hồng Tài xế DEMO đơn ${order.orderCode || orderId}`,
        amountVnd: split.driverCommissionVnd,
        balanceAfterVnd: nextDriverBalance,
        relatedOrderCode: String(order.orderCode || orderId),
        status: "succeeded",
        idempotencyKey: `settle_drv_${orderId}`,
        isDemo: true,
        demoNotice: DEMO_CURRENCY_NOTICE,
        createdAt: now,
      });
    }

    if (merchantWalletRef && split.merchantCommissionVnd > 0) {
      const mData = merchantWalletSnap?.exists ? (merchantWalletSnap.data() as Record<string, unknown>) : undefined;
      const nextMerchantBalance = readSafeWalletInt(mData, "balanceVnd") + split.merchantCommissionVnd;
      const merchantTxId = `TX-MER-${orderId}`;
      tx.set(
        merchantWalletRef,
        {
          userId: merchantId,
          balanceVnd: nextMerchantBalance,
          totalTopUpVnd: readSafeWalletInt(mData, "totalTopUpVnd"),
          totalSpentVnd: readSafeWalletInt(mData, "totalSpentVnd"),
          totalEarnedVnd: readSafeWalletInt(mData, "totalEarnedVnd") + split.merchantCommissionVnd,
          totalRefundedVnd: readSafeWalletInt(mData, "totalRefundedVnd"),
          currency: "VND",
          isDemo: true,
          demoNotice: DEMO_CURRENCY_NOTICE,
          createdAt: merchantWalletSnap?.exists ? mData!.createdAt ?? now : now,
          updatedAt: now,
        },
        { merge: true },
      );
      tx.set(merchantWalletRef.collection("transactions").doc(merchantTxId), {
        txId: merchantTxId,
        userId: merchantId,
        txType: "MERCHANT_COMMISSION_DEMO",
        title: `Doanh thu Gian hàng DEMO đơn ${order.orderCode || orderId}`,
        amountVnd: split.merchantCommissionVnd,
        balanceAfterVnd: nextMerchantBalance,
        relatedOrderCode: String(order.orderCode || orderId),
        status: "succeeded",
        idempotencyKey: `settle_mer_${orderId}`,
        isDemo: true,
        demoNotice: DEMO_CURRENCY_NOTICE,
        createdAt: now,
      });
    }

    tx.set(revenueRef, {
      orderId,
      orderCode: String(order.orderCode || orderId),
      serviceType,
      customerId: String(order.customerId || ""),
      driverId,
      merchantId,
      grossTotalVnd: split.grossTotalVnd,
      driverCommissionVnd: split.driverCommissionVnd,
      merchantCommissionVnd: split.merchantCommissionVnd,
      platformRevenueVnd: split.platformRevenueVnd,
      currency: "VND",
      isDemo: true,
      demoNotice: DEMO_CURRENCY_NOTICE,
      createdAt: now,
    });

    const nextPaymentStatus =
      order.paymentStatus === "paid_demo" ? "paid_demo" : "settled_cash_demo";

    tx.update(orderRef, {
      driverCommissionVnd: split.driverCommissionVnd,
      merchantCommissionVnd: split.merchantCommissionVnd,
      platformRevenueVnd: split.platformRevenueVnd,
      commissionSettled: true,
      paymentStatus: nextPaymentStatus,
      financialStatus: "settled_demo",
      updatedAt: now,
    });

    tx.set(settlementIdemRef, {
      scope: "settle_commission_demo",
      orderId,
      ...split,
      settledBy: auth.uid,
      createdAt: now,
    });

    return {
      orderId,
      ...split,
      settled: true,
      idempotentReplay: false,
      isDemo: true,
      demoNotice: DEMO_CURRENCY_NOTICE,
    };
  });
}

/**
 * 6. Fetch caller's authoritative wallet balance and ledger transaction history.
 */
export async function getWalletSummaryOp(
  db: Firestore,
  callerAuth: CallerAuth | null | undefined,
): Promise<WalletSummaryResult> {
  const auth = requireAuth(callerAuth);
  const walletRef = db.collection("wallets").doc(auth.uid);
  const [walletSnap, txSnap] = await Promise.all([
    walletRef.get(),
    walletRef.collection("transactions").orderBy("createdAt", "desc").limit(40).get(),
  ]);

  const wData = walletSnap.exists ? (walletSnap.data() as Record<string, unknown>) : undefined;
  const transactions: WalletTransactionDto[] = txSnap.docs.map((doc) => {
    const d = doc.data();
    return {
      txId: String(d.txId || doc.id),
      userId: String(d.userId || auth.uid),
      txType: String(d.txType || ""),
      title: String(d.title || ""),
      amountVnd: typeof d.amountVnd === "number" && Number.isSafeInteger(d.amountVnd) ? d.amountVnd : 0,
      balanceAfterVnd: readSafeWalletInt(d, "balanceAfterVnd"),
      relatedOrderCode: String(d.relatedOrderCode || ""),
      status: String(d.status || "succeeded"),
      idempotencyKey: String(d.idempotencyKey || ""),
      isDemo: d.isDemo !== false,
      createdAtMillis: toMillis(d.createdAt),
    };
  });

  return {
    userId: auth.uid,
    balanceVnd: readSafeWalletInt(wData, "balanceVnd"),
    totalTopUpVnd: readSafeWalletInt(wData, "totalTopUpVnd"),
    totalSpentVnd: readSafeWalletInt(wData, "totalSpentVnd"),
    totalEarnedVnd: readSafeWalletInt(wData, "totalEarnedVnd"),
    totalRefundedVnd: readSafeWalletInt(wData, "totalRefundedVnd"),
    currency: "VND",
    isDemo: true,
    demoNotice: DEMO_CURRENCY_NOTICE,
    transactions,
  };
}

/**
 * 7. Verified Admin-only financial overview across FOX DEMO platform revenue & commissions.
 */
export async function getAdminFinancialOverviewOp(
  db: Firestore,
  callerAuth: CallerAuth | null | undefined,
): Promise<AdminFinancialOverviewResult> {
  const auth = requireAuth(callerAuth);
  await assertVerifiedAdmin(db, auth);

  const snap = await db.collection("platform_revenue").orderBy("createdAt", "desc").limit(100).get();
  let totalGrossVnd = 0;
  let totalDriverCommissionVnd = 0;
  let totalMerchantCommissionVnd = 0;
  let totalPlatformRevenueVnd = 0;

  const recentEntries: PlatformRevenueEntryDto[] = snap.docs.map((doc) => {
    const d = doc.data();
    const gross = readSafeWalletInt(d, "grossTotalVnd");
    const drv = readSafeWalletInt(d, "driverCommissionVnd");
    const mer = readSafeWalletInt(d, "merchantCommissionVnd");
    const rev = readSafeWalletInt(d, "platformRevenueVnd");
    totalGrossVnd += gross;
    totalDriverCommissionVnd += drv;
    totalMerchantCommissionVnd += mer;
    totalPlatformRevenueVnd += rev;
    return {
      id: doc.id,
      orderId: String(d.orderId || doc.id),
      serviceType: String(d.serviceType || ""),
      grossTotalVnd: gross,
      driverCommissionVnd: drv,
      merchantCommissionVnd: mer,
      platformRevenueVnd: rev,
      createdAtMillis: toMillis(d.createdAt),
    };
  });

  return {
    settledOrdersCount: recentEntries.length,
    totalGrossVnd,
    totalDriverCommissionVnd,
    totalMerchantCommissionVnd,
    totalPlatformRevenueVnd,
    currency: "VND",
    isDemo: true,
    demoNotice: DEMO_CURRENCY_NOTICE,
    recentEntries: recentEntries.slice(0, 20),
  };
}
