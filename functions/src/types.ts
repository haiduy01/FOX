export const FOX_DATABASE_ID =
  process.env.FOX_FIRESTORE_DATABASE_ID ||
  process.env.FOX_DATABASE_ID ||
  "ai-studio-android-1a796de6-79f0-4283-8d0b-703e709b27e4";

export const BOOTSTRAPPED_ADMIN_EMAIL =
  process.env.FOX_ADMIN_EMAIL?.trim() || "hahaiduy28@gmail.com";
export const DEMO_CURRENCY_NOTICE = "DEMO — KHÔNG PHẢI TIỀN THẬT";

export const MIN_TOPUP_SINGLE_VND = 10_000;
export const MAX_TOPUP_SINGLE_VND = 2_000_000;
export const MAX_WALLET_BALANCE_VND = 10_000_000;

export type FoxServiceType = "RIDE" | "DELIVERY" | "FOOD" | "SHOPPING";
export type FoxPaymentMethod = "Ví FOX DEMO" | "Tiền mặt DEMO";

export interface CallerAuth {
  uid: string;
  email: string | null;
  emailVerified: boolean;
}

export interface OrderItemInput {
  itemId: string;
  quantity: number;
}

export interface GeoCoords {
  lat: number;
  lng: number;
}

export interface ComputeRouteRequest {
  origin: GeoCoords;
  destination: GeoCoords;
  vehicleType?: string;
}

export interface VerifiedRouteResult {
  origin: GeoCoords;
  destination: GeoCoords;
  distanceMeters: number;
  distanceKm: number;
  durationSeconds: number;
  durationMinutes: number;
  encodedPolyline: string;
  routeSource: "google_routes_api_v2" | "server_haversine_demo_estimate";
  isDemoRoute: boolean;
  serverVerified: true;
  billingNotice: string;
}

export interface QuoteRequest {
  serviceType: FoxServiceType;
  // RIDE
  vehicleType?: string;
  distanceKm?: number;
  // Optional verified coordinates for RIDE / DELIVERY
  pickupCoords?: GeoCoords;
  dropoffCoords?: GeoCoords;
  // DELIVERY
  weightTier?: string;
  speed?: string;
  // FOOD / SHOPPING
  merchantId?: string;
  items?: OrderItemInput[];
  voucherCode?: string | null;
  strictVoucher?: boolean;
  // Optional client-side display check — rejected if tampered/mismatched
  expectedTotalVnd?: number;
}

export interface ResolvedOrderLine {
  itemId: string;
  name: string;
  unitPriceVnd: number;
  quantity: number;
  lineTotalVnd: number;
}

export interface QuoteResult {
  serviceType: FoxServiceType;
  subtotalVnd: number;
  feeVnd: number;
  discountVnd: number;
  totalVnd: number;
  voucherCode: string;
  voucherApplied: boolean;
  voucherMessage: string;
  resolvedItems: ResolvedOrderLine[];
  verifiedRoute?: VerifiedRouteResult | null;
  currency: "VND";
  isDemo: true;
  demoNotice: string;
}

export interface VoucherValidationResult {
  valid: boolean;
  voucherCode: string;
  discountVnd: number;
  message: string;
}

export interface CreateVerifiedOrderRequest extends QuoteRequest {
  idempotencyKey: string;
  paymentMethod: FoxPaymentMethod;
  title?: string;
  primaryAddress: string;
  secondaryAddress: string;
  detailSummary?: string;
  note?: string;
}

export interface CreateVerifiedOrderResult {
  orderId: string;
  orderCode: string;
  merchantId: string;
  merchantName: string;
  quote: QuoteResult;
  paymentMethod: FoxPaymentMethod;
  paymentStatus: string;
  financialStatus: string;
  stockDeducted: boolean;
  walletBalanceAfterVnd: number | null;
  idempotentReplay: boolean;
  isDemo: true;
  demoNotice: string;
}

export interface TopUpDemoWalletRequest {
  amountVnd: number;
  idempotencyKey: string;
  note?: string;
}

export interface TopUpDemoWalletResult {
  txId: string;
  amountVnd: number;
  balanceAfterVnd: number;
  status: "succeeded";
  idempotentReplay: boolean;
  isDemo: true;
  demoNotice: string;
}

export interface PayOrderWithDemoWalletRequest {
  orderId: string;
  idempotencyKey: string;
}

export interface PayOrderWithDemoWalletResult {
  orderId: string;
  txId: string;
  amountVnd: number;
  balanceAfterVnd: number;
  paymentStatus: "paid_demo";
  status: "succeeded";
  idempotentReplay: boolean;
  isDemo: true;
  demoNotice: string;
}

export interface RefundOrderDemoRequest {
  orderId: string;
  idempotencyKey: string;
  reason?: string;
}

export interface RefundOrderDemoResult {
  orderId: string;
  txId: string | null;
  refundedAmountVnd: number;
  customerBalanceAfterVnd: number | null;
  orderStatus: "cancelled";
  paymentStatus: string;
  refundStatus: string;
  stockRestored: boolean;
  idempotentReplay: boolean;
  isDemo: true;
  demoNotice: string;
}

export interface SettleOrderCommissionRequest {
  orderId: string;
  idempotencyKey?: string;
}

export interface CommissionBreakdown {
  grossTotalVnd: number;
  driverCommissionVnd: number;
  merchantCommissionVnd: number;
  platformRevenueVnd: number;
}

export interface SettleOrderCommissionResult extends CommissionBreakdown {
  orderId: string;
  settled: true;
  idempotentReplay: boolean;
  isDemo: true;
  demoNotice: string;
}

export interface WalletTransactionDto {
  txId: string;
  userId: string;
  txType: string;
  title: string;
  amountVnd: number;
  balanceAfterVnd: number;
  relatedOrderCode: string;
  status: string;
  idempotencyKey: string;
  isDemo: boolean;
  createdAtMillis: number;
}

export interface WalletSummaryResult {
  userId: string;
  balanceVnd: number;
  totalTopUpVnd: number;
  totalSpentVnd: number;
  totalEarnedVnd: number;
  totalRefundedVnd: number;
  currency: "VND";
  isDemo: true;
  demoNotice: string;
  transactions: WalletTransactionDto[];
}

export interface PlatformRevenueEntryDto {
  id: string;
  orderId: string;
  serviceType: string;
  grossTotalVnd: number;
  driverCommissionVnd: number;
  merchantCommissionVnd: number;
  platformRevenueVnd: number;
  createdAtMillis: number;
}

export interface AdminFinancialOverviewResult {
  settledOrdersCount: number;
  totalGrossVnd: number;
  totalDriverCommissionVnd: number;
  totalMerchantCommissionVnd: number;
  totalPlatformRevenueVnd: number;
  currency: "VND";
  isDemo: true;
  demoNotice: string;
  recentEntries: PlatformRevenueEntryDto[];
}
