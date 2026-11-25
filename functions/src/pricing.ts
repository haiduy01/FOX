import { Firestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import {
  CommissionBreakdown,
  DEMO_CURRENCY_NOTICE,
  FoxServiceType,
  OrderItemInput,
  QuoteRequest,
  QuoteResult,
  ResolvedOrderLine,
  VerifiedRouteResult,
  VoucherValidationResult,
} from "./types";
import { computeVerifiedRouteOp } from "./routeService";

/**
 * Authoritative server-side catalog matching FOX Pilot demo dishes & products.
 * Client-submitted prices are NEVER trusted; server resolves prices strictly from this catalog
 * or from Firestore /merchants/{merchantId}/products/{itemId}.
 */
const SERVER_FOOD_CATALOG: Record<string, { name: string; priceVnd: number }> = {
  f1: { name: "Phở Bò Tái Lăn Đặc Biệt", priceVnd: 65_000 },
  f2: { name: "Cơm Tấm Sườn Bì Chả Trứng", priceVnd: 58_000 },
  f3: { name: "Trà Đào Cam Sả Size L", priceVnd: 39_000 },
  f4: { name: "Bánh Mì Thập Cẩm Pate Bơ", priceVnd: 35_000 },
  f5: { name: "Mẹt Bún Đậu Tá Lả Đầy Đủ", priceVnd: 72_000 },
};

const SERVER_SHOP_CATALOG: Record<string, { name: string; priceVnd: number }> = {
  p1: { name: "Combo Trái Cây Nhập Khẩu 2kg", priceVnd: 185_000 },
  p2: { name: "Bình Giữ Nhiệt Inox 304 750ml", priceVnd: 229_000 },
  p3: { name: "Tai Nghe Bluetooth Chống Ồn", priceVnd: 350_000 },
  p4: { name: "Áo Polo Cotton Thoáng Mát", priceVnd: 245_000 },
  p5: { name: "Kem Chống Nắng SPF50+ 50ml", priceVnd: 165_000 },
};

interface RideRate {
  baseFareVnd: number;
  perKmVnd: number;
  minFareVnd: number;
}

const RIDE_RATES: Record<string, RideRate> = {
  "FOX Bike": { baseFareVnd: 12_000, perKmVnd: 4_500, minFareVnd: 15_000 },
  "FOX Car 4": { baseFareVnd: 24_000, perKmVnd: 11_000, minFareVnd: 30_000 },
  "FOX Car 7": { baseFareVnd: 30_000, perKmVnd: 14_000, minFareVnd: 40_000 },
};

const DELIVERY_WEIGHT_SURCHARGE: Record<string, number> = {
  "Dưới 2 kg": 0,
  "2–5 kg": 8_000,
  "2-5 kg": 8_000,
  "5–10 kg": 18_000,
  "5-10 kg": 18_000,
};

export function assertSafeIntVnd(value: unknown, fieldName: string): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < 0) {
    throw new HttpsError(
      "invalid-argument",
      `Giá trị ${fieldName} phải là số nguyên VND không âm (không dùng số thập phân).`,
    );
  }
  return value;
}

export function assertValidIdempotencyKey(key: unknown): string {
  if (typeof key !== "string") {
    throw new HttpsError("invalid-argument", "Thiếu idempotencyKey hợp lệ.");
  }
  const trimmed = key.trim();
  if (!/^[a-zA-Z0-9_:\-]{6,128}$/.test(trimmed)) {
    throw new HttpsError(
      "invalid-argument",
      "idempotencyKey phải dài 6–128 ký tự và chỉ chứa chữ, số, '_', ':', '-'.",
    );
  }
  return trimmed;
}

export function assertValidServiceType(serviceType: unknown): FoxServiceType {
  if (
    serviceType !== "RIDE" &&
    serviceType !== "DELIVERY" &&
    serviceType !== "FOOD" &&
    serviceType !== "SHOPPING"
  ) {
    throw new HttpsError("invalid-argument", "Loại dịch vụ không hợp lệ.");
  }
  return serviceType;
}

/**
 * Exact integer basis-points calculation: floor(amount * bps / 10000).
 * Uses BigInt internally so intermediate products never overflow IEEE-754 double precision.
 */
export function mulBpsFloor(amountVnd: number, bps: number): number {
  assertSafeIntVnd(amountVnd, "amountVnd");
  assertSafeIntVnd(bps, "bps");
  const result = (BigInt(amountVnd) * BigInt(bps)) / 10_000n;
  return Number(result);
}

/**
 * Validates voucher code purely on the server using integer VND rules.
 */
export function evaluateVoucher(
  serviceType: FoxServiceType,
  subtotalVnd: number,
  rawVoucherCode?: string | null,
): VoucherValidationResult {
  const code = (rawVoucherCode ?? "").trim().toUpperCase();
  if (!code) {
    return {
      valid: true,
      voucherCode: "",
      discountVnd: 0,
      message: "Không áp dụng mã khuyến mãi.",
    };
  }

  switch (code) {
    case "FOXRIDE25": {
      if (serviceType !== "RIDE") {
        return {
          valid: false,
          voucherCode: code,
          discountVnd: 0,
          message: "Mã FOXRIDE25 chỉ áp dụng cho dịch vụ Đặt xe.",
        };
      }
      const discount = Math.min(mulBpsFloor(subtotalVnd, 2500), 30_000);
      return {
        valid: true,
        voucherCode: code,
        discountVnd: discount,
        message: "Đã áp dụng mã FOXRIDE25 (giảm 25%, tối đa 30.000đ).",
      };
    }
    case "FOXSHIP15": {
      if (serviceType !== "DELIVERY") {
        return {
          valid: false,
          voucherCode: code,
          discountVnd: 0,
          message: "Mã FOXSHIP15 chỉ áp dụng cho dịch vụ Giao hàng.",
        };
      }
      const discount = Math.min(10_000, subtotalVnd);
      return {
        valid: true,
        voucherCode: code,
        discountVnd: discount,
        message: "Đã áp dụng mã FOXSHIP15 (giảm 10.000đ).",
      };
    }
    case "FOXFOOD30": {
      if (serviceType !== "FOOD") {
        return {
          valid: false,
          voucherCode: code,
          discountVnd: 0,
          message: "Mã FOXFOOD30 chỉ áp dụng cho dịch vụ Đặt đồ ăn.",
        };
      }
      if (subtotalVnd < 80_000) {
        return {
          valid: false,
          voucherCode: code,
          discountVnd: 0,
          message: "Mã FOXFOOD30 yêu cầu đơn đồ ăn tối thiểu 80.000đ.",
        };
      }
      const discount = Math.min(mulBpsFloor(subtotalVnd, 3000), 30_000);
      return {
        valid: true,
        voucherCode: code,
        discountVnd: discount,
        message: "Đã áp dụng mã FOXFOOD30 (giảm 30%, tối đa 30.000đ).",
      };
    }
    case "FOXSHOP20": {
      if (serviceType !== "SHOPPING") {
        return {
          valid: false,
          voucherCode: code,
          discountVnd: 0,
          message: "Mã FOXSHOP20 chỉ áp dụng cho dịch vụ Mua sắm.",
        };
      }
      if (subtotalVnd < 150_000) {
        return {
          valid: false,
          voucherCode: code,
          discountVnd: 0,
          message: "Mã FOXSHOP20 yêu cầu đơn mua sắm tối thiểu 150.000đ.",
        };
      }
      return {
        valid: true,
        voucherCode: code,
        discountVnd: 20_000,
        message: "Đã áp dụng mã FOXSHOP20 (giảm 20.000đ).",
      };
    }
    default:
      return {
        valid: false,
        voucherCode: code,
        discountVnd: 0,
        message: `Mã khuyến mãi "${code}" không tồn tại hoặc đã hết hạn.`,
      };
  }
}

/**
 * Normalizes and aggregates duplicate itemIds so a client cannot bypass stock limits
 * by splitting a single product into multiple line items.
 */
export function normalizeOrderItems(items: OrderItemInput[] | undefined): OrderItemInput[] {
  if (!Array.isArray(items) || items.length === 0) {
    throw new HttpsError("invalid-argument", "Vui lòng chọn ít nhất 1 món / sản phẩm.");
  }
  if (items.length > 50) {
    throw new HttpsError("invalid-argument", "Số lượng dòng sản phẩm vượt quá giới hạn (50).");
  }

  const aggregated = new Map<string, number>();
  for (const entry of items) {
    if (!entry || typeof entry.itemId !== "string" || !entry.itemId.trim()) {
      throw new HttpsError("invalid-argument", "Mã sản phẩm không hợp lệ.");
    }
    const itemId = entry.itemId.trim();
    if (!/^[a-zA-Z0-9_\-]{1,128}$/.test(itemId)) {
      throw new HttpsError("invalid-argument", `Mã sản phẩm "${itemId}" chứa ký tự không hợp lệ.`);
    }
    const quantity = assertSafeIntVnd(entry.quantity, `quantity(${itemId})`);
    if (quantity < 1 || quantity > 99) {
      throw new HttpsError("invalid-argument", `Số lượng của ${itemId} phải từ 1 đến 99.`);
    }
    const nextQty = (aggregated.get(itemId) ?? 0) + quantity;
    if (nextQty > 99) {
      throw new HttpsError("invalid-argument", `Tổng số lượng của ${itemId} không được vượt quá 99.`);
    }
    aggregated.set(itemId, nextQty);
  }

  return Array.from(aggregated.entries()).map(([itemId, quantity]) => ({ itemId, quantity }));
}

export function buildQuoteFromResolvedSubtotal(
  serviceType: FoxServiceType,
  subtotalVnd: number,
  feeVnd: number,
  resolvedItems: ResolvedOrderLine[],
  req: QuoteRequest,
): QuoteResult {
  const voucherEval = evaluateVoucher(serviceType, subtotalVnd, req.voucherCode);
  if (!voucherEval.valid && req.strictVoucher !== false) {
    throw new HttpsError("invalid-argument", voucherEval.message);
  }

  const discountVnd = voucherEval.valid ? voucherEval.discountVnd : 0;
  const totalVnd = Math.max(0, subtotalVnd + feeVnd - discountVnd);

  // Anti-tampering check: if client claims a specific expectedTotalVnd that doesn't match server calculation, reject!
  if (req.expectedTotalVnd !== undefined && req.expectedTotalVnd !== null) {
    const expected = assertSafeIntVnd(req.expectedTotalVnd, "expectedTotalVnd");
    if (expected !== totalVnd) {
      throw new HttpsError(
        "failed-precondition",
        `Giá do thiết bị gửi (${expected}đ) không khớp với giá xác thực từ server (${totalVnd}đ). Giao dịch bị từ chối.`,
      );
    }
  }

  return {
    serviceType,
    subtotalVnd,
    feeVnd,
    discountVnd,
    totalVnd,
    voucherCode: voucherEval.valid ? voucherEval.voucherCode : "",
    voucherApplied: voucherEval.valid && voucherEval.voucherCode.length > 0 && discountVnd > 0,
    voucherMessage: voucherEval.message,
    resolvedItems,
    currency: "VND",
    isDemo: true,
    demoNotice: DEMO_CURRENCY_NOTICE,
  };
}

async function resolveCatalogItems(
  db: Firestore,
  serviceType: "FOOD" | "SHOPPING",
  items: OrderItemInput[] | undefined,
  merchantId?: string,
): Promise<{ lines: ResolvedOrderLine[]; subtotalVnd: number }> {
  const normalizedItems = normalizeOrderItems(items);
  const cleanMerchantId = typeof merchantId === "string" ? merchantId.trim() : "";

  if (cleanMerchantId.length > 0) {
    if (!/^[a-zA-Z0-9_\-]{1,128}$/.test(cleanMerchantId)) {
      throw new HttpsError("invalid-argument", "Mã gian hàng (merchantId) không hợp lệ.");
    }
    const storeSnap = await db.collection("merchants").doc(cleanMerchantId).get();
    if (storeSnap.exists) {
      const store = storeSnap.data()!;
      if (store.active !== true) {
        throw new HttpsError("failed-precondition", "Gian hàng hiện đang tạm ngừng hoạt động.");
      }
      if (store.openNow !== true) {
        throw new HttpsError("failed-precondition", `Gian hàng "${store.name || cleanMerchantId}" hiện đang đóng cửa.`);
      }
      if (store.storeType && store.storeType !== serviceType) {
        throw new HttpsError("invalid-argument", "Gian hàng không thuộc loại dịch vụ đã chọn.");
      }
    }
  }

  const fallbackCatalog = serviceType === "FOOD" ? SERVER_FOOD_CATALOG : SERVER_SHOP_CATALOG;
  const lines: ResolvedOrderLine[] = [];
  let subtotalVnd = 0;

  for (const entry of normalizedItems) {
    const { itemId, quantity } = entry;
    let resolvedName: string | null = null;
    let resolvedUnitPrice: number | null = null;

    if (cleanMerchantId.length > 0) {
      const prodSnap = await db
        .collection("merchants")
        .doc(cleanMerchantId)
        .collection("products")
        .doc(itemId)
        .get();
      if (!prodSnap.exists) {
        throw new HttpsError(
          "not-found",
          `Sản phẩm "${itemId}" không tồn tại trong gian hàng "${cleanMerchantId}".`,
        );
      }
      const data = prodSnap.data()!;
      if (data.active !== true) {
        throw new HttpsError("failed-precondition", `Sản phẩm "${data.name || itemId}" hiện đang bị ẩn hoặc ngừng bán.`);
      }
      const stock = assertSafeIntVnd(data.stock, `product.stock(${itemId})`);
      if (stock < quantity) {
        throw new HttpsError(
          "failed-precondition",
          `Sản phẩm "${data.name || itemId}" không đủ tồn kho (còn ${stock}, yêu cầu ${quantity}).`,
        );
      }
      resolvedName = String(data.name || itemId);
      resolvedUnitPrice = assertSafeIntVnd(data.priceVnd, `product.priceVnd(${itemId})`);
    } else {
      const catalogItem = fallbackCatalog[itemId];
      if (!catalogItem) {
        throw new HttpsError(
          "invalid-argument",
          `Sản phẩm "${itemId}" không tồn tại trong bảng giá đáng tin cậy của server.`,
        );
      }
      resolvedName = catalogItem.name;
      resolvedUnitPrice = catalogItem.priceVnd;
    }

    const lineTotalVnd = resolvedUnitPrice * quantity;
    subtotalVnd += lineTotalVnd;
    lines.push({
      itemId,
      name: resolvedName!,
      unitPriceVnd: resolvedUnitPrice,
      quantity,
      lineTotalVnd,
    });
  }

  return { lines, subtotalVnd };
}

/**
 * Authoritative server-side order pricing engine.
 * NEVER trusts client-supplied subtotal/fee/discount/total values.
 * If client supplies optional expectedTotalVnd and it diverges, rejects as price mismatch/tampering.
 */
export async function calculateAuthoritativeQuote(
  db: Firestore,
  req: QuoteRequest,
): Promise<QuoteResult> {
  const serviceType = assertValidServiceType(req.serviceType);
  let subtotalVnd = 0;
  let feeVnd = 0;
  let resolvedItems: ResolvedOrderLine[] = [];
  let verifiedRoute: VerifiedRouteResult | null = null;

  const hasCoords = Boolean(req.pickupCoords && req.dropoffCoords);

  if (serviceType === "RIDE") {
    const vehicleType = typeof req.vehicleType === "string" ? req.vehicleType.trim() : "";
    const rate = RIDE_RATES[vehicleType];
    if (!rate) {
      throw new HttpsError(
        "invalid-argument",
        `Loại xe "${vehicleType}" không hợp lệ (chỉ chấp nhận FOX Bike, FOX Car 4, FOX Car 7).`,
      );
    }

    let authoritativeDistanceKm: number;
    if (hasCoords) {
      verifiedRoute = await computeVerifiedRouteOp({
        origin: req.pickupCoords!,
        destination: req.dropoffCoords!,
        vehicleType,
      });
      authoritativeDistanceKm = verifiedRoute.distanceKm;
      if (req.distanceKm !== undefined && req.distanceKm !== null) {
        const clientKm = assertSafeIntVnd(req.distanceKm, "distanceKm");
        if (Math.abs(clientKm - authoritativeDistanceKm) > 1) {
          throw new HttpsError(
            "failed-precondition",
            `Quãng đường do thiết bị gửi (${clientKm} km) không khớp với tuyến đường xác thực từ server (${authoritativeDistanceKm} km). Giao dịch bị từ chối.`,
          );
        }
      }
    } else {
      authoritativeDistanceKm = assertSafeIntVnd(req.distanceKm, "distanceKm");
      if (authoritativeDistanceKm < 1 || authoritativeDistanceKm > 200) {
        throw new HttpsError("invalid-argument", "Quãng đường phải từ 1 đến 200 km.");
      }
    }

    subtotalVnd = Math.max(rate.baseFareVnd + rate.perKmVnd * authoritativeDistanceKm, rate.minFareVnd);
    feeVnd = 0;
  } else if (serviceType === "DELIVERY") {
    const speed = typeof req.speed === "string" ? req.speed.trim() : "Tiêu chuẩn";
    if (speed !== "Nhanh" && speed !== "Tiêu chuẩn") {
      throw new HttpsError("invalid-argument", "Tốc độ giao hàng phải là 'Nhanh' hoặc 'Tiêu chuẩn'.");
    }
    const weightTier = typeof req.weightTier === "string" ? req.weightTier.trim() : "Dưới 2 kg";
    const weightSurcharge = DELIVERY_WEIGHT_SURCHARGE[weightTier];
    if (weightSurcharge === undefined) {
      throw new HttpsError("invalid-argument", `Hạng cân nặng "${weightTier}" không hợp lệ.`);
    }
    const baseFee = speed === "Nhanh" ? 32_000 : 20_000;
    let distanceSurcharge = 0;
    if (hasCoords) {
      verifiedRoute = await computeVerifiedRouteOp({
        origin: req.pickupCoords!,
        destination: req.dropoffCoords!,
        vehicleType: "FOX Bike",
      });
      if (req.distanceKm !== undefined && req.distanceKm !== null) {
        const clientKm = assertSafeIntVnd(req.distanceKm, "distanceKm");
        if (Math.abs(clientKm - verifiedRoute.distanceKm) > 1) {
          throw new HttpsError(
            "failed-precondition",
            `Quãng đường giao hàng từ thiết bị (${clientKm} km) không khớp với server (${verifiedRoute.distanceKm} km).`,
          );
        }
      }
      // Extra 4,000 VND per km beyond the first 3 km (exact integer VND)
      distanceSurcharge = Math.max(0, verifiedRoute.distanceKm - 3) * 4_000;
    }
    subtotalVnd = baseFee + weightSurcharge + distanceSurcharge;
    feeVnd = 0;
  } else if (serviceType === "FOOD") {
    const resolved = await resolveCatalogItems(db, "FOOD", req.items, req.merchantId);
    resolvedItems = resolved.lines;
    subtotalVnd = resolved.subtotalVnd;
    feeVnd = subtotalVnd > 0 ? 15_000 : 0;
  } else {
    const resolved = await resolveCatalogItems(db, "SHOPPING", req.items, req.merchantId);
    resolvedItems = resolved.lines;
    subtotalVnd = resolved.subtotalVnd;
    feeVnd = subtotalVnd >= 300_000 || subtotalVnd === 0 ? 0 : 22_000;
  }

  const voucherEval = evaluateVoucher(serviceType, subtotalVnd, req.voucherCode);
  if (!voucherEval.valid && req.strictVoucher !== false) {
    throw new HttpsError("invalid-argument", voucherEval.message);
  }

  const discountVnd = voucherEval.valid ? voucherEval.discountVnd : 0;
  const totalVnd = Math.max(0, subtotalVnd + feeVnd - discountVnd);

  // Anti-tampering check: if client claims a specific expectedTotalVnd that doesn't match server calculation, reject!
  if (req.expectedTotalVnd !== undefined && req.expectedTotalVnd !== null) {
    const expected = assertSafeIntVnd(req.expectedTotalVnd, "expectedTotalVnd");
    if (expected !== totalVnd) {
      throw new HttpsError(
        "failed-precondition",
        `Giá do thiết bị gửi (${expected}đ) không khớp với giá xác thực từ server (${totalVnd}đ). Giao dịch bị từ chối.`,
      );
    }
  }

  return {
    serviceType,
    subtotalVnd,
    feeVnd,
    discountVnd,
    totalVnd,
    voucherCode: voucherEval.valid ? voucherEval.voucherCode : "",
    voucherApplied: voucherEval.valid && voucherEval.voucherCode.length > 0 && discountVnd > 0,
    voucherMessage: voucherEval.message,
    resolvedItems,
    verifiedRoute,
    currency: "VND",
    isDemo: true,
    demoNotice: DEMO_CURRENCY_NOTICE,
  };
}

/**
 * Authoritative integer VND commission split.
 * Invariant: driverCommissionVnd + merchantCommissionVnd + platformRevenueVnd === totalVnd (zero rounding loss).
 */
export function calculateCommissionSplit(
  serviceType: FoxServiceType,
  subtotalVnd: number,
  feeVnd: number,
  totalVnd: number,
): CommissionBreakdown {
  assertSafeIntVnd(subtotalVnd, "subtotalVnd");
  assertSafeIntVnd(feeVnd, "feeVnd");
  assertSafeIntVnd(totalVnd, "totalVnd");

  if (serviceType === "RIDE" || serviceType === "DELIVERY") {
    // Driver earns 80% (8000 bps), FOX DEMO platform keeps remaining 20%
    const driverCommissionVnd = mulBpsFloor(totalVnd, 8000);
    const platformRevenueVnd = totalVnd - driverCommissionVnd;
    return {
      grossTotalVnd: totalVnd,
      driverCommissionVnd,
      merchantCommissionVnd: 0,
      platformRevenueVnd,
    };
  }

  // FOOD / SHOPPING: Merchant earns 85% of subtotal (capped by totalVnd),
  // Driver earns 80% of delivery fee (capped by remainder), FOX DEMO keeps exact remainder.
  const merchantCommissionVnd = Math.min(totalVnd, mulBpsFloor(subtotalVnd, 8500));
  const afterMerchant = totalVnd - merchantCommissionVnd;
  const driverCommissionVnd = Math.min(afterMerchant, mulBpsFloor(feeVnd, 8000));
  const platformRevenueVnd = totalVnd - merchantCommissionVnd - driverCommissionVnd;

  return {
    grossTotalVnd: totalVnd,
    driverCommissionVnd,
    merchantCommissionVnd,
    platformRevenueVnd,
  };
}
