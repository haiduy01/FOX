import { FieldValue, Firestore } from "firebase-admin/firestore";
import { getMessaging, MulticastMessage } from "firebase-admin/messaging";
import { HttpsError } from "firebase-functions/v2/https";
import { isVerifiedAdminCaller } from "./financeService";
import { BOOTSTRAPPED_ADMIN_EMAIL, CallerAuth } from "./types";

export type FoxOrderNotificationEvent =
  | "order_created"
  | "merchant_confirmed"
  | "preparing"
  | "ready_for_pickup"
  | "driver_accepted"
  | "driver_arrived"
  | "in_progress"
  | "completed"
  | "cancelled";

export type FoxPartnerReviewEvent =
  | "partner_application_submitted"
  | "partner_approved"
  | "partner_rejected";

export type FoxNotificationTargetRole = "customer" | "driver" | "merchant" | "admin";

export interface FcmDispatchPayload {
  tokens: string[];
  title: string;
  body: string;
  data: Record<string, string>;
}

export interface FcmSendOutcome {
  successCount: number;
  failureCount: number;
  invalidTokens: string[];
  skippedInEmulator?: boolean;
}

export type FcmMulticastSender = (payload: FcmDispatchPayload) => Promise<FcmSendOutcome>;

export interface NotificationDispatchSummary {
  eventType: string;
  recipientCount: number;
  recipientUids: string[];
  fcmAttemptedTokens: number;
  fcmSuccessCount: number;
  fcmFailureCount: number;
  cleanedInvalidTokens: number;
}

const INVALID_FCM_ERROR_CODES = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
  "messaging/mismatched-credential",
]);

export const defaultFcmSender: FcmMulticastSender = async (payload) => {
  if (payload.tokens.length === 0) {
    return { successCount: 0, failureCount: 0, invalidTokens: [] };
  }
  // Never attempt outbound live FCM network calls during local Firestore Emulator runs
  if (process.env.FIRESTORE_EMULATOR_HOST) {
    return {
      successCount: 0,
      failureCount: 0,
      invalidTokens: [],
      skippedInEmulator: true,
    };
  }
  const message: MulticastMessage = {
    tokens: payload.tokens,
    notification: {
      title: payload.title,
      body: payload.body,
    },
    data: payload.data,
    android: {
      priority: "high",
      notification: {
        channelId: channelForRole(payload.data.targetRole as FoxNotificationTargetRole),
      },
    },
  };
  const response = await getMessaging().sendEachForMulticast(message);
  const invalidTokens: string[] = [];
  response.responses.forEach((res, idx) => {
    if (!res.success) {
      const code = res.error?.code ?? "";
      if (INVALID_FCM_ERROR_CODES.has(code)) {
        invalidTokens.push(payload.tokens[idx]);
      }
    }
  });
  return {
    successCount: response.successCount,
    failureCount: response.failureCount,
    invalidTokens,
  };
};

export function channelForRole(role: FoxNotificationTargetRole | string | undefined): string {
  switch (role) {
    case "driver":
      return "fox_orders_driver";
    case "merchant":
      return "fox_orders_merchant";
    case "admin":
      return "fox_admin_alerts";
    default:
      return "fox_orders_customer";
  }
}

interface RecipientMessage {
  uid: string;
  targetRole: FoxNotificationTargetRole;
  title: string;
  body: string;
  orderId: string;
  orderCode: string;
  serviceType: string;
  eventType: string;
}

async function deliverMessagesWithFcm(
  db: Firestore,
  messages: RecipientMessage[],
  fcmSender: FcmMulticastSender,
): Promise<NotificationDispatchSummary> {
  if (messages.length === 0) {
    return {
      eventType: "",
      recipientCount: 0,
      recipientUids: [],
      fcmAttemptedTokens: 0,
      fcmSuccessCount: 0,
      fcmFailureCount: 0,
      cleanedInvalidTokens: 0,
    };
  }

  const now = FieldValue.serverTimestamp();
  const seenUids = new Set<string>();
  const uniqueMessages: RecipientMessage[] = [];
  for (const msg of messages) {
    if (!msg.uid || seenUids.has(msg.uid)) continue;
    seenUids.add(msg.uid);
    uniqueMessages.push(msg);
  }

  let fcmAttemptedTokens = 0;
  let fcmSuccessCount = 0;
  let fcmFailureCount = 0;
  let cleanedInvalidTokens = 0;

  for (const msg of uniqueMessages) {
    const safeEvent = msg.eventType.replace(/[^a-zA-Z0-9_]/g, "").slice(0, 32);
    const safeOrder = msg.orderCode.replace(/[^a-zA-Z0-9_\-]/g, "").slice(0, 32) || "sys";
    const notifId = `n_${safeEvent}_${safeOrder}_${Date.now() % 100000}`;

    await db.collection("users").doc(msg.uid).collection("notifications").doc(notifId).set({
      title: msg.title.slice(0, 120),
      body: msg.body.slice(0, 400),
      relatedOrderCode: msg.orderCode.slice(0, 32),
      orderId: msg.orderId.slice(0, 128),
      serviceType: msg.serviceType.slice(0, 20),
      targetRole: msg.targetRole,
      eventType: safeEvent,
      readFlag: false,
      createdAt: now,
    });

    const tokenSnap = await db
      .collection("users")
      .doc(msg.uid)
      .collection("fcm_tokens")
      .where("enabled", "==", true)
      .get();

    if (tokenSnap.empty) continue;

    const tokenToDocRef = new Map<string, FirebaseFirestore.DocumentReference>();
    const tokens: string[] = [];
    for (const doc of tokenSnap.docs) {
      const tokenVal = doc.data()?.token;
      if (typeof tokenVal === "string" && tokenVal.trim().length >= 16) {
        tokens.push(tokenVal.trim());
        tokenToDocRef.set(tokenVal.trim(), doc.ref);
      }
    }

    if (tokens.length === 0) continue;

    fcmAttemptedTokens += tokens.length;
    const outcome = await fcmSender({
      tokens,
      title: msg.title.slice(0, 120),
      body: msg.body.slice(0, 400),
      data: {
        title: msg.title.slice(0, 120),
        body: msg.body.slice(0, 400),
        orderId: msg.orderId,
        orderCode: msg.orderCode,
        serviceType: msg.serviceType,
        targetRole: msg.targetRole,
        eventType: safeEvent,
        channelId: channelForRole(msg.targetRole),
      },
    });

    fcmSuccessCount += outcome.successCount;
    fcmFailureCount += outcome.failureCount;

    for (const badToken of outcome.invalidTokens) {
      const ref = tokenToDocRef.get(badToken);
      if (ref) {
        await ref.delete();
        cleanedInvalidTokens += 1;
      }
    }
  }

  return {
    eventType: uniqueMessages[0].eventType,
    recipientCount: uniqueMessages.length,
    recipientUids: uniqueMessages.map((m) => m.uid),
    fcmAttemptedTokens,
    fcmSuccessCount,
    fcmFailureCount,
    cleanedInvalidTokens,
  };
}

async function listOnlineApprovedDriverUids(db: Firestore): Promise<string[]> {
  const snap = await db
    .collection("users")
    .where("role", "==", "driver")
    .where("driverStatus", "==", "approved")
    .where("driverOnline", "==", true)
    .get();
  return snap.docs
    .filter((d) => d.data()?.accountStatus !== "suspended")
    .map((d) => d.id);
}

async function listVerifiedAdminUids(db: Firestore): Promise<string[]> {
  const uids = new Set<string>();
  const adminsSnap = await db.collection("admins").get();
  adminsSnap.docs.forEach((d) => uids.add(d.id));

  const usersAdminSnap = await db.collection("users").where("role", "==", "admin").get();
  usersAdminSnap.docs.forEach((d) => uids.add(d.id));

  const bootstrapSnap = await db.collection("users").where("email", "==", BOOTSTRAPPED_ADMIN_EMAIL).get();
  bootstrapSnap.docs.forEach((d) => uids.add(d.id));

  return Array.from(uids);
}

/**
 * Core role-targeted order notification router.
 * Strictly separates notifications for Customer, Online Approved Drivers, Merchant, and Admin.
 */
export async function dispatchOrderNotifications(
  db: Firestore,
  orderId: string,
  order: Record<string, unknown>,
  eventType: FoxOrderNotificationEvent,
  fcmSender: FcmMulticastSender = defaultFcmSender,
): Promise<NotificationDispatchSummary> {
  const orderCode = String(order.orderCode || orderId);
  const serviceType = String(order.serviceType || "RIDE");
  const titleText = String(order.title || orderCode);
  const customerId = String(order.customerId || "");
  const driverId = String(order.driverId || "");
  const driverName = String(order.driverName || "Tài xế FOX");
  const merchantId = String(order.merchantId || "");
  const merchantName = String(order.merchantName || "Gian hàng FOX");
  const totalVnd = typeof order.totalVnd === "number" ? order.totalVnd : 0;

  const messages: RecipientMessage[] = [];
  const pushMsg = (uid: string, targetRole: FoxNotificationTargetRole, title: string, body: string) => {
    if (!uid) return;
    messages.push({
      uid,
      targetRole,
      title,
      body,
      orderId,
      orderCode,
      serviceType,
      eventType,
    });
  };

  switch (eventType) {
    case "order_created": {
      pushMsg(
        customerId,
        "customer",
        `Đã tạo đơn ${orderCode}`,
        `${titleText} • Tổng ${totalVnd}đ (DEMO). Đang chuyển đơn tới đối tác FOX.`,
      );
      if (serviceType === "RIDE" || serviceType === "DELIVERY") {
        const onlineDrivers = await listOnlineApprovedDriverUids(db);
        for (const drvUid of onlineDrivers) {
          if (drvUid === customerId) continue;
          pushMsg(
            drvUid,
            "driver",
            `Đơn ${serviceType} mới: ${orderCode}`,
            `${titleText} • Giá ước tính ${totalVnd}đ. Mở FOX Tài xế để nhận đơn ngay!`,
          );
        }
      } else if (merchantId) {
        pushMsg(
          merchantId,
          "merchant",
          `Gian hàng có đơn mới ${orderCode}`,
          `${titleText} • Tổng ${totalVnd}đ. Vui lòng xác nhận và chuẩn bị đơn.`,
        );
      }
      break;
    }

    case "merchant_confirmed": {
      pushMsg(
        customerId,
        "customer",
        `${merchantName} đã xác nhận ${orderCode}`,
        `Gian hàng đang chuẩn bị đơn ${titleText} của bạn.`,
      );
      break;
    }

    case "preparing": {
      pushMsg(
        customerId,
        "customer",
        `Đang chuẩn bị đơn ${orderCode}`,
        `${merchantName} đang đóng gói/chế biến món cho bạn.`,
      );
      break;
    }

    case "ready_for_pickup": {
      pushMsg(
        customerId,
        "customer",
        `Đơn ${orderCode} sẵn sàng giao tài xế`,
        `${merchantName} đã chuẩn bị xong và đang điều phối tài xế FOX.`,
      );
      const onlineDrivers = await listOnlineApprovedDriverUids(db);
      for (const drvUid of onlineDrivers) {
        if (drvUid === customerId || drvUid === merchantId) continue;
        pushMsg(
          drvUid,
          "driver",
          `Đơn ${serviceType} sẵn sàng lấy: ${orderCode}`,
          `Lấy tại ${merchantName} • Tổng ${totalVnd}đ. Mở FOX Tài xế để nhận giao!`,
        );
      }
      break;
    }

    case "driver_accepted": {
      pushMsg(
        customerId,
        "customer",
        `Tài xế ${driverName} đã nhận đơn ${orderCode}`,
        `Tài xế đang di chuyển tới điểm đón/lấy hàng.`,
      );
      if (merchantId) {
        pushMsg(
          merchantId,
          "merchant",
          `Tài xế ${driverName} đã nhận lấy ${orderCode}`,
          `Vui lòng sẵn sàng bàn giao đơn hàng cho tài xế.`,
        );
      }
      break;
    }

    case "driver_arrived": {
      pushMsg(
        customerId,
        "customer",
        `Tài xế đã đến điểm lấy/đón (${orderCode})`,
        `${driverName} đã có mặt cho đơn ${titleText}.`,
      );
      break;
    }

    case "in_progress": {
      pushMsg(
        customerId,
        "customer",
        `Đơn ${orderCode} đang thực hiện`,
        `${driverName} đang thực hiện chuyến đi / giao hàng tới điểm đến.`,
      );
      break;
    }

    case "completed": {
      pushMsg(
        customerId,
        "customer",
        `Hoàn thành đơn ${orderCode}`,
        `Cảm ơn bạn đã sử dụng FOX – Chạm là tới! Tổng thanh toán DEMO: ${totalVnd}đ.`,
      );
      if (driverId) {
        pushMsg(
          driverId,
          "driver",
          `Hoàn thành đơn ${orderCode}`,
          `Đơn ${orderCode} đã hoàn tất. Kiểm tra Ví FOX DEMO để xem hoa hồng quyết toán.`,
        );
      }
      if (merchantId) {
        pushMsg(
          merchantId,
          "merchant",
          `Giao thành công đơn ${orderCode}`,
          `Đơn ${orderCode} của gian hàng đã hoàn tất.`,
        );
      }
      const adminUids = await listVerifiedAdminUids(db);
      for (const adminUid of adminUids) {
        pushMsg(
          adminUid,
          "admin",
          `Đơn hoàn thành ${orderCode}`,
          `${serviceType} • ${totalVnd}đ DEMO đã hoàn thành và ghi nhận sổ đối soát.`,
        );
      }
      break;
    }

    case "cancelled": {
      pushMsg(
        customerId,
        "customer",
        `Đơn ${orderCode} đã bị hủy`,
        `Đơn ${titleText} đã chuyển sang trạng thái đã hủy.`,
      );
      if (driverId) {
        pushMsg(
          driverId,
          "driver",
          `Đơn ${orderCode} đã hủy`,
          `Đơn ${orderCode} đã bị hủy và tự động dừng chia sẻ vị trí.`,
        );
      }
      if (merchantId) {
        pushMsg(
          merchantId,
          "merchant",
          `Đơn ${orderCode} đã bị hủy`,
          `Đơn ${orderCode} đã hủy (tồn kho được hoàn tự động nếu có giữ kho).`,
        );
      }
      break;
    }
  }

  return deliverMessagesWithFcm(db, messages, fcmSender);
}

const VALID_ORDER_EVENTS = new Set<string>([
  "order_created",
  "merchant_confirmed",
  "preparing",
  "ready_for_pickup",
  "driver_accepted",
  "driver_arrived",
  "in_progress",
  "completed",
  "cancelled",
]);

/**
 * Authenticated callable: allows a verified participant of an order (customer, assigned driver,
 * assigned merchant, or verified admin) to trigger server-side role notifications + FCM pushes.
 * Strictly blocks unrelated third-party callers!
 */
export async function notifyOrderEventOp(
  db: Firestore,
  callerAuth: CallerAuth | null | undefined,
  req: { orderId?: string; eventType?: string },
  fcmSender: FcmMulticastSender = defaultFcmSender,
): Promise<NotificationDispatchSummary> {
  if (!callerAuth || !callerAuth.uid) {
    throw new HttpsError("unauthenticated", "Vui lòng đăng nhập Google.");
  }
  const orderId = typeof req?.orderId === "string" ? req.orderId.trim() : "";
  const eventType = typeof req?.eventType === "string" ? req.eventType.trim() : "";
  if (!/^[a-zA-Z0-9_\-]{1,128}$/.test(orderId)) {
    throw new HttpsError("invalid-argument", "Mã đơn hàng không hợp lệ.");
  }
  if (!VALID_ORDER_EVENTS.has(eventType)) {
    throw new HttpsError("invalid-argument", `Sự kiện thông báo không hợp lệ: ${eventType}`);
  }

  const orderSnap = await db.collection("orders").doc(orderId).get();
  if (!orderSnap.exists) {
    throw new HttpsError("not-found", "Không tìm thấy đơn hàng.");
  }
  const order = orderSnap.data()!;
  const isAdmin = await isVerifiedAdminCaller(db, callerAuth);
  const isParticipant =
    isAdmin ||
    order.customerId === callerAuth.uid ||
    (order.driverId && order.driverId === callerAuth.uid) ||
    (order.merchantId && order.merchantId === callerAuth.uid);

  if (!isParticipant) {
    throw new HttpsError(
      "permission-denied",
      "Bạn không có quyền gửi thông báo cho đơn hàng không thuộc về mình.",
    );
  }

  return dispatchOrderNotifications(db, orderId, order, eventType as FoxOrderNotificationEvent, fcmSender);
}

/**
 * Authenticated callable for Partner onboarding notifications:
 * - Applicant submits driver/merchant application -> notifies verified Admins.
 * - Verified Admin approves/rejects application -> notifies applicant.
 */
export async function notifyPartnerReviewOp(
  db: Firestore,
  callerAuth: CallerAuth | null | undefined,
  req: { targetUid?: string; partnerType?: string; eventType?: string },
  fcmSender: FcmMulticastSender = defaultFcmSender,
): Promise<NotificationDispatchSummary> {
  if (!callerAuth || !callerAuth.uid) {
    throw new HttpsError("unauthenticated", "Vui lòng đăng nhập Google.");
  }
  const targetUid = typeof req?.targetUid === "string" ? req.targetUid.trim() : callerAuth.uid;
  const partnerType = req?.partnerType === "merchant" ? "merchant" : "driver";
  const partnerLabel = partnerType === "merchant" ? "Gian hàng" : "Tài xế";
  const eventType = typeof req?.eventType === "string" ? req.eventType.trim() : "";

  if (!["partner_application_submitted", "partner_approved", "partner_rejected"].includes(eventType)) {
    throw new HttpsError("invalid-argument", "Loại thông báo hồ sơ đối tác không hợp lệ.");
  }

  const messages: RecipientMessage[] = [];

  if (eventType === "partner_application_submitted") {
    if (targetUid !== callerAuth.uid) {
      throw new HttpsError("permission-denied", "Chỉ chủ tài khoản mới được gửi thông báo hồ sơ của mình.");
    }
    const adminUids = await listVerifiedAdminUids(db);
    for (const adminUid of adminUids) {
      messages.push({
        uid: adminUid,
        targetRole: "admin",
        title: `Hồ sơ đăng ký ${partnerLabel} mới`,
        body: `Có hồ sơ đăng ký ${partnerLabel} mới đang chờ Quản trị viên xét duyệt trên Bảng điều khiển Admin.`,
        orderId: "",
        orderCode: `PARTNER-${partnerType.toUpperCase()}`,
        serviceType: "ADMIN",
        eventType,
      });
    }
  } else {
    const isAdmin = await isVerifiedAdminCaller(db, callerAuth);
    if (!isAdmin) {
      throw new HttpsError("permission-denied", "Chỉ Quản trị viên xác thực mới được gửi thông báo duyệt đối tác.");
    }
    const approved = eventType === "partner_approved";
    messages.push({
      uid: targetUid,
      targetRole: partnerType === "merchant" ? "merchant" : "driver",
      title: approved ? `Đã duyệt hồ sơ ${partnerLabel} FOX` : `Hồ sơ ${partnerLabel} chưa được duyệt`,
      body: approved
        ? `Chúc mừng! Tài khoản của bạn đã được mở quyền ${partnerLabel} trên hệ thống Cloud FOX.`
        : `Hồ sơ đăng ký ${partnerLabel} đã bị từ chối. Bạn có thể cập nhật thông tin và gửi lại.`,
      orderId: "",
      orderCode: `PARTNER-${partnerType.toUpperCase()}`,
      serviceType: "ADMIN",
      eventType,
    });
  }

  return deliverMessagesWithFcm(db, messages, fcmSender);
}
