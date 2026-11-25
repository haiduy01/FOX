import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { CallableRequest, onCall } from "firebase-functions/v2/https";
import {
  createVerifiedOrderAndPayDemoTx,
  getAdminFinancialOverviewOp,
  getWalletSummaryOp,
  payOrderWithDemoWalletTx,
  refundOrderDemoTx,
  settleOrderCommissionDemoTx,
  topUpDemoWalletTx,
} from "./financeService";
import {
  dispatchOrderNotifications,
  notifyOrderEventOp,
  notifyPartnerReviewOp,
} from "./notificationService";
import { assertSafeIntVnd, assertValidServiceType, calculateAuthoritativeQuote, evaluateVoucher } from "./pricing";
import { computeVerifiedRouteOp } from "./routeService";
import {
  CallerAuth,
  ComputeRouteRequest,
  CreateVerifiedOrderRequest,
  FOX_DATABASE_ID,
  PayOrderWithDemoWalletRequest,
  QuoteRequest,
  RefundOrderDemoRequest,
  SettleOrderCommissionRequest,
  TopUpDemoWalletRequest,
} from "./types";

const app = getApps().length > 0 ? getApps()[0] : initializeApp();
const db = getFirestore(app, FOX_DATABASE_ID);

const CALLABLE_OPTIONS = {
  region: "asia-southeast1",
  cors: false,
} as const;

function extractAuth(request: CallableRequest<unknown>): CallerAuth | null {
  if (!request.auth) return null;
  return {
    uid: request.auth.uid,
    email: typeof request.auth.token?.email === "string" ? request.auth.token.email : null,
    emailVerified: request.auth.token?.email_verified === true,
  };
}

export const calculateOrderQuote = onCall(CALLABLE_OPTIONS, async (request) => {
  const data = (request.data ?? {}) as QuoteRequest;
  return calculateAuthoritativeQuote(db, { ...data, strictVoucher: data.strictVoucher ?? false });
});

export const validateVoucher = onCall(CALLABLE_OPTIONS, async (request) => {
  const data = (request.data ?? {}) as {
    serviceType?: unknown;
    subtotalVnd?: unknown;
    voucherCode?: string | null;
  };
  const serviceType = assertValidServiceType(data.serviceType);
  const subtotalVnd = assertSafeIntVnd(data.subtotalVnd, "subtotalVnd");
  return evaluateVoucher(serviceType, subtotalVnd, data.voucherCode);
});

export const createVerifiedOrderAndPayDemo = onCall(CALLABLE_OPTIONS, async (request) => {
  const result = await createVerifiedOrderAndPayDemoTx(
    db,
    extractAuth(request),
    request.data as CreateVerifiedOrderRequest,
  );
  if (!result.idempotentReplay) {
    const orderSnap = await db.collection("orders").doc(result.orderId).get();
    if (orderSnap.exists) {
      await dispatchOrderNotifications(db, result.orderId, orderSnap.data()!, "order_created").catch(() => {});
    }
  }
  return result;
});

export const payOrderWithDemoWallet = onCall(CALLABLE_OPTIONS, async (request) => {
  return payOrderWithDemoWalletTx(db, extractAuth(request), request.data as PayOrderWithDemoWalletRequest);
});

export const topUpDemoWallet = onCall(CALLABLE_OPTIONS, async (request) => {
  return topUpDemoWalletTx(db, extractAuth(request), request.data as TopUpDemoWalletRequest);
});

export const refundOrderDemo = onCall(CALLABLE_OPTIONS, async (request) => {
  const result = await refundOrderDemoTx(db, extractAuth(request), request.data as RefundOrderDemoRequest);
  if (!result.idempotentReplay) {
    const orderSnap = await db.collection("orders").doc(result.orderId).get();
    if (orderSnap.exists) {
      await dispatchOrderNotifications(db, result.orderId, orderSnap.data()!, "cancelled").catch(() => {});
    }
  }
  return result;
});

export const settleOrderCommissionDemo = onCall(CALLABLE_OPTIONS, async (request) => {
  const result = await settleOrderCommissionDemoTx(
    db,
    extractAuth(request),
    request.data as SettleOrderCommissionRequest,
  );
  if (!result.idempotentReplay) {
    const orderSnap = await db.collection("orders").doc(result.orderId).get();
    if (orderSnap.exists) {
      await dispatchOrderNotifications(db, result.orderId, orderSnap.data()!, "completed").catch(() => {});
    }
  }
  return result;
});

export const getWalletSummary = onCall(CALLABLE_OPTIONS, async (request) => {
  return getWalletSummaryOp(db, extractAuth(request));
});

export const getAdminFinancialOverview = onCall(CALLABLE_OPTIONS, async (request) => {
  return getAdminFinancialOverviewOp(db, extractAuth(request));
});

export const computeVerifiedRoute = onCall(CALLABLE_OPTIONS, async (request) => {
  return computeVerifiedRouteOp((request.data ?? {}) as ComputeRouteRequest);
});

export const notifyOrderEvent = onCall(CALLABLE_OPTIONS, async (request) => {
  return notifyOrderEventOp(
    db,
    extractAuth(request),
    (request.data ?? {}) as { orderId?: string; eventType?: string },
  );
});

export const notifyPartnerReview = onCall(CALLABLE_OPTIONS, async (request) => {
  return notifyPartnerReviewOp(
    db,
    extractAuth(request),
    (request.data ?? {}) as { targetUid?: string; partnerType?: string; eventType?: string },
  );
});
