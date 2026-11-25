import { HttpsError } from "firebase-functions/v2/https";
import { ComputeRouteRequest, GeoCoords, VerifiedRouteResult } from "./types";

const EARTH_RADIUS_METERS = 6_371_000;
const URBAN_ROAD_FACTOR = 1.32;
const MAX_ALLOWED_DISTANCE_KM = 200;

export function assertValidGeoCoords(raw: unknown, fieldName: string): GeoCoords {
  if (!raw || typeof raw !== "object") {
    throw new HttpsError("invalid-argument", `Tọa độ ${fieldName} không hợp lệ.`);
  }
  const obj = raw as Record<string, unknown>;
  const lat = obj.lat;
  const lng = obj.lng;
  if (
    typeof lat !== "number" ||
    typeof lng !== "number" ||
    !Number.isFinite(lat) ||
    !Number.isFinite(lng) ||
    lat < -90 ||
    lat > 90 ||
    lng < -180 ||
    lng > 180
  ) {
    throw new HttpsError(
      "invalid-argument",
      `Tọa độ ${fieldName} (lat/lng) phải là số hợp lệ trong phạm vi [-90..90, -180..180].`,
    );
  }
  return {
    lat: Math.round(lat * 1_000_000) / 1_000_000,
    lng: Math.round(lng * 1_000_000) / 1_000_000,
  };
}

export function haversineMeters(a: GeoCoords, b: GeoCoords): number {
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const dLat = toRad(b.lat - a.lat);
  const dLng = toRad(b.lng - a.lng);
  const sinLat = Math.sin(dLat / 2);
  const sinLng = Math.sin(dLng / 2);
  const h =
    sinLat * sinLat +
    Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * sinLng * sinLng;
  const c = 2 * Math.atan2(Math.sqrt(h), Math.sqrt(Math.max(0, 1 - h)));
  return Math.round(EARTH_RADIUS_METERS * c);
}

function encodeSignedValue(value: number): string {
  let sgn = value << 1;
  if (value < 0) {
    sgn = ~sgn;
  }
  let encoded = "";
  while (sgn >= 0x20) {
    encoded += String.fromCharCode((0x20 | (sgn & 0x1f)) + 63);
    sgn >>= 5;
  }
  encoded += String.fromCharCode(sgn + 63);
  return encoded;
}

export function encodePolyline(points: GeoCoords[]): string {
  let lastLat = 0;
  let lastLng = 0;
  let result = "";
  for (const pt of points) {
    const latE5 = Math.round(pt.lat * 1e5);
    const lngE5 = Math.round(pt.lng * 1e5);
    result += encodeSignedValue(latE5 - lastLat);
    result += encodeSignedValue(lngE5 - lastLng);
    lastLat = latE5;
    lastLng = lngE5;
  }
  return result;
}

function parseGoogleDurationSeconds(durationStr: unknown): number {
  if (typeof durationStr !== "string") return 0;
  const match = /^(\d+(?:\.\d+)?)s$/.exec(durationStr.trim());
  if (!match) return 0;
  return Math.max(60, Math.round(Number(match[1])));
}

/**
 * Computes an authoritative route on the trusted backend.
 * - When GOOGLE_ROUTES_API_KEY is configured in server environment (Cloud Functions Secret / env),
 *   calls Google Routes API v2 directly from backend (API key NEVER exposed to Android APK).
 * - When GOOGLE_ROUTES_API_KEY is absent (pre-Billing / Emulator Pilot), computes deterministic
 *   server-side Haversine road-factor distance and explicitly marks isDemoRoute=true so the app
 *   never misrepresents fallback estimates as Google Routes API telemetry.
 */
export async function computeVerifiedRouteOp(req: ComputeRouteRequest): Promise<VerifiedRouteResult> {
  const origin = assertValidGeoCoords(req?.origin, "origin");
  const destination = assertValidGeoCoords(req?.destination, "destination");
  const vehicleType = typeof req?.vehicleType === "string" ? req.vehicleType.trim() : "FOX Car 4";
  const travelMode = vehicleType === "FOX Bike" ? "TWO_WHEELER" : "DRIVE";

  const straightMeters = haversineMeters(origin, destination);
  if (straightMeters < 20) {
    throw new HttpsError(
      "invalid-argument",
      "Điểm đón/lấy hàng và điểm đến quá gần nhau (< 20m). Vui lòng chọn hai vị trí khác nhau.",
    );
  }
  const maxStraightMeters = MAX_ALLOWED_DISTANCE_KM * 1000;
  if (straightMeters > maxStraightMeters) {
    throw new HttpsError(
      "invalid-argument",
      `Khoảng cách vượt quá giới hạn dịch vụ (${MAX_ALLOWED_DISTANCE_KM} km).`,
    );
  }

  const serverRoutesKey = (process.env.GOOGLE_ROUTES_API_KEY || "").trim();
  const hasRealServerRoutesKey =
    serverRoutesKey.length >= 20 &&
    !serverRoutesKey.includes("NOT_CONFIGURED") &&
    !serverRoutesKey.includes("YOUR_");

  if (hasRealServerRoutesKey && !process.env.FIRESTORE_EMULATOR_HOST) {
    try {
      const response = await fetch("https://routes.googleapis.com/directions/v2:computeRoutes", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-Goog-Api-Key": serverRoutesKey,
          "X-Goog-FieldMask": "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline",
        },
        body: JSON.stringify({
          origin: {
            location: { latLng: { latitude: origin.lat, longitude: origin.lng } },
          },
          destination: {
            location: { latLng: { latitude: destination.lat, longitude: destination.lng } },
          },
          travelMode,
          routingPreference: "TRAFFIC_AWARE",
        }),
      });

      if (response.ok) {
        const json = (await response.json()) as {
          routes?: Array<{
            distanceMeters?: number;
            duration?: string;
            polyline?: { encodedPolyline?: string };
          }>;
        };
        const topRoute = json.routes?.[0];
        if (topRoute && typeof topRoute.distanceMeters === "number" && topRoute.distanceMeters > 0) {
          const distanceMeters = Math.round(topRoute.distanceMeters);
          const distanceKm = Math.max(1, Math.min(MAX_ALLOWED_DISTANCE_KM, Math.ceil(distanceMeters / 1000)));
          const durationSeconds = parseGoogleDurationSeconds(topRoute.duration);
          const durationMinutes = Math.max(2, Math.ceil(durationSeconds / 60));
          return {
            origin,
            destination,
            distanceMeters,
            distanceKm,
            durationSeconds,
            durationMinutes,
            encodedPolyline: topRoute.polyline?.encodedPolyline || encodePolyline([origin, destination]),
            routeSource: "google_routes_api_v2",
            isDemoRoute: false,
            serverVerified: true,
            billingNotice: "Tuyến đường thực từ Google Routes API v2 (xác thực qua backend).",
          };
        }
      }
    } catch {
      // Fall through cleanly to deterministic server Haversine estimate with explicit isDemoRoute flag
    }
  }

  // Deterministic server-side Haversine road-factor estimate when Google Routes API key/Billing is not configured
  const distanceMeters = Math.max(200, Math.round(straightMeters * URBAN_ROAD_FACTOR));
  const distanceKm = Math.max(1, Math.min(MAX_ALLOWED_DISTANCE_KM, Math.ceil(distanceMeters / 1000)));
  const avgSpeedKmh = travelMode === "TWO_WHEELER" ? 28 : 24;
  const durationSeconds = Math.max(180, Math.round((distanceMeters / 1000 / avgSpeedKmh) * 3600));
  const durationMinutes = Math.max(3, Math.ceil(durationSeconds / 60));

  // Build 3-point curved preview polyline for clear map visualization
  const midPoint: GeoCoords = {
    lat: (origin.lat + destination.lat) / 2 + (destination.lng - origin.lng) * 0.08,
    lng: (origin.lng + destination.lng) / 2 - (destination.lat - origin.lat) * 0.08,
  };

  return {
    origin,
    destination,
    distanceMeters,
    distanceKm,
    durationSeconds,
    durationMinutes,
    encodedPolyline: encodePolyline([origin, midPoint, destination]),
    routeSource: "server_haversine_demo_estimate",
    isDemoRoute: true,
    serverVerified: true,
    billingNotice:
      "Chưa cấu hình GOOGLE_ROUTES_API_KEY phía backend (chưa bật Billing Routes API). Đang dùng khoảng cách ước tính Haversine xác thực server (DEMO).",
  };
}
