# BÁO CÁO TỔNG KIỂM THỬ & NGHIỆM THU — FOX FULL FEATURE PILOT

- **Ứng dụng:** FOX – Chạm là tới! (`applicationId`: `com.aistudio.fox.superapp`, `versionCode`: `1`, `versionName`: `1.0`)
- **Môi trường mặc định APK Debug:** `BuildConfig.FOX_ENV = "DEMO"` (qua `FoxEnvironmentConfig.kt`)
- **Tình trạng chốt FOX Full Feature Pilot:** **CHƯA ĐẠT CHỐT PILOT (NOT FINALIZED)**
  > *Lý do khách quan:* Toàn bộ kiểm thử tự động phía Android (21/21 PASS), Cloud Functions TypeScript build (PASS), Firestore Security Rules & Emulator E2E (40/40 PASS) và bản build Debug APK đều **PASS** trong môi trường Emulator/JVM. Tuy nhiên, **chưa triển khai lên dự án Firebase Staging riêng**, **chưa cài đặt APK lên thiết bị vật lý**, **chưa nghiệm thu đẩy thông báo FCM thật trên thiết bị vật lý**, **chưa cấu hình `MAPS_API_KEY` thật**, và **chưa hoàn tất kiểm thử đa thiết bị (2–4 máy thật)**.

---

## 1. Bảng tổng hợp tính năng (`PASS` / `FAIL` / `CHƯA KIỂM THỬ`)

| Nhóm tính năng | Hạng mục kiểm thử | Trạng thái | Ghi chú & phạm vi xác minh |
| :--- | :--- | :---: | :--- |
| **Giao diện FOX & Offline DEMO** | Trang chủ 4 dịch vụ, điều hướng 4 tab, Room SQLite offline, sửa chống cắt chữ thẻ dịch vụ & thẻ thống kê màn hình nhỏ | **PASS** | Biên dịch APK & Robolectric khởi chạy thành công; `FoxServiceCard` dùng `heightIn(min = 192.dp)`, `AdminStatCard` chống tràn 3 cột |
| **Đặt xe (RIDE)** | Tạo đơn -> Tài xế duyệt nhận -> Đến đón -> Đang chạy -> Hoàn thành -> Thanh toán & hoa hồng -> **Đánh giá 1–5 sao (1 lần duy nhất)** | **PASS** | E2E 1 Emulator PASS; Rules Test 11 xác minh chỉ khách sở hữu đơn `completed` được đánh giá 1 lần; UI hộp thoại đánh giá đã tích hợp |
| **Giao hàng (DELIVERY)** | Tạo vận đơn -> Tài xế nhận -> Lấy hàng -> Đang giao -> Hoàn thành -> Đối soát lịch sử & sổ cái VND nguyên | **PASS** | E2E 2 Emulator PASS; chống gian lận quãng đường PASS (`routes.emulator.test.js`) |
| **Đặt đồ ăn (FOOD)** | Danh mục Cloud Firestore -> Giỏ hàng -> Trừ tồn kho nguyên tử -> Gian hàng chuẩn bị -> Tài xế giao -> Quyết toán 3 chiều | **PASS** | E2E 3 & `catalog.emulator.test.js` (6/6) PASS |
| **Mua sắm (SHOPPING)** | Danh mục Cloud -> Kiểm tra kho -> Gian hàng xác nhận -> Tài xế giao -> Hoàn thành -> Cập nhật kho & doanh thu DEMO | **PASS** | E2E 4 Emulator PASS |
| **Tài chính Ví DEMO (Server)** | 12 Cloud Functions v2, số nguyên VND, idempotency, chống số dư âm, hủy đơn hoàn tiền + hoàn kho, chống hoàn trùng | **PASS** | `finance.emulator.test.js` (9/9) + E2E 5 & E2E 8 PASS — gắn nhãn **DEMO — KHÔNG PHẢI TIỀN THẬT** |
| **Bảo mật & Phân quyền (RBAC)** | Chặn truy cập chưa đăng nhập/token rỗng, chặn tài xế chưa duyệt, chặn tự nâng quyền admin, chặn gian hàng sửa đơn gian hàng khác | **PASS** | `firestore.test.js` (11/11) + E2E 8 PASS; 0 `signInAnonymously` |
| **Chống xung đột & gian lận** | 2 tài xế tranh 1 đơn, 2 khách mua món tồn kho = 1, **chống dùng trùng 1 mã voucher/user**, chặn sửa giá/số thập phân | **PASS** | E2E 7 + Finance Test 4, 6, 7 PASS; `/voucher_redemptions/{uid}_{code}` khóa ghi client |
| **FCM — Logic & Bảo mật** | Lưu/làm mới token giữ nguyên vai trò thực, chọn đúng vai trò nhận, chặn client giả mạo đẩy push, dọn token hỏng, **0 PII trong payload**, xóa `FLUTTER_NOTIFICATION_CLICK`, deep-link foreground & background, Android 13+ `POST_NOTIFICATIONS` | **PASS** | `FoxFcmAndPilotTest` (6/6) + E2E 5 & E2E 6 PASS |
| **FCM — Đẩy thông báo thật trên thiết bị vật lý** | Nhận push khi app nền / tắt hẳn trên điện thoại Android thật qua Firebase Messaging thực tế | **CHƯA KIỂM THỬ** | Không có thiết bị vật lý/mạng FCM thật trong môi trường build; cần kiểm thử trên Staging |
| **GPS & Bản đồ** | Quyền vị trí đúng phạm vi (`FINE`/`COARSE`, **không** có `ACCESS_BACKGROUND_LOCATION`), chế độ Bản đồ DEMO có nhãn rõ ràng, khóa tính năng Google Maps SDK/Routes thật khi thiếu `MAPS_API_KEY` | **PASS** | `FoxLocationAndMapTest` (9/9) + `routes.emulator.test.js` (6/6) PASS |
| **Google Maps SDK & Routes API thật** | Hiển thị bản đồ Google Maps thật và tính lộ trình bằng Google Routes API | **CHƯA KIỂM THỬ** | Đang tạm khóa an toàn vì chưa có `MAPS_API_KEY` hợp lệ và chưa bật Billing |
| **Firebase Staging Isolation Kit** | Cấu hình tách biệt `fox-staging-db`, chốt chặn tự hủy (`exit 3`) khi trỏ nhầm Live (`gen-lang-client-0957111252`), dry-run seed/smoke/rollback | **PASS** | Đã xác minh thực tế guardrail thoát mã 3 và dry-run thoát mã 0 |
| **Triển khai Firebase Staging thật** | Deploy Firestore Rules, 12 Cloud Functions v2, seed DEMO và chạy smoke test trên dự án Staging thật | **CHƯA KIỂM THỬ** | Cần Chủ dự án tạo project Staging riêng và phê duyệt trước khi deploy |
| **Cài đặt APK & Kiểm thử đa thiết bị thật (2–4 máy)** | Cài `app-debug.apk` lên 2–4 máy Android thật (Khách hàng, Tài xế, Gian hàng, Admin) kiểm tra đồng bộ real-time & đăng nhập Google | **CHƯA KIỂM THỬ** | Môi trường hiện tại không có thiết bị thật hoặc ADB; đã build thành công APK 27 MB |

---

## 2. Các vấn đề đã phát hiện & khắc phục trong lượt tổng kiểm toán

1. **Lỗ hổng dùng lại mã Voucher nhiều lần:** Trước đây chỉ kiểm tra tính hợp lệ của mã mà chưa khóa lượt dùng theo từng người dùng. **Đã sửa:** Bổ sung bản ghi nguyên tử `/voucher_redemptions/{uid}_{voucherCode}` trong transaction `createVerifiedOrderAndPayDemoTx` (`functions/src/financeService.ts`) và khóa hoàn toàn quyền ghi từ client trong `firestore.rules`.
2. **Thiếu luồng đánh giá sau hoàn thành đơn (RIDE & đơn hoàn tất):** **Đã sửa:** Bổ sung `customerRating` (1..5), `customerReviewNote` (≤ 300 ký tự), `ratedAt` trong `FoxCloudModels.kt`, transaction `rateCompletedOrder` trong `FoxCloudRepository.kt`, luật bảo mật chỉ cho chủ đơn đánh giá đúng 1 lần trong `firestore.rules`, và giao diện chọn 1–5 sao trong `CloudOrdersScreen`.
3. **Token FCM bị ghi đè vai trò thành `"customer"` khi làm mới (`onNewToken`):** **Đã sửa:** `FoxFirebaseMessagingService.onNewToken` truy vấn vai trò thực tế qua `fetchUserRoleOrDefault(user.uid)` thay vì hardcode `"customer"`.
4. **Rò rỉ PII & giá trị legacy trong push FCM:** `notificationService.ts` trước đây chứa `clickAction: "FLUTTER_NOTIFICATION_CLICK"` và đưa email/UID vào nội dung push duyệt đối tác. **Đã sửa:** Gỡ bỏ `FLUTTER_NOTIFICATION_CLICK` và loại bỏ toàn bộ email/UID/SĐT khỏi push payload.
5. **Deep-link thông báo FCM nền chưa xử lý raw extras:** **Đã sửa:** `FoxNotificationRouter.extractRouteFromIntent(intent)` nay hỗ trợ cả `EXTRA_TARGET_ROUTE` (foreground) lẫn cặp khóa dữ liệu FCM nền (`targetRole`, `eventType`, `serviceType`).
6. **Nguy cơ cắt chữ trên màn hình nhỏ / phông chữ lớn:** **Đã sửa:** Đổi `FoxServiceCard` từ `height(192.dp)` sang `heightIn(min = 192.dp)` và giới hạn `maxLines = 1` + `TextOverflow.Ellipsis` cho `AdminStatCard`.
7. **Cô lập môi trường Staging & tham số hóa Database ID cho Backend:** **Đã sửa:** `functions/src/types.ts` đọc `FOX_FIRESTORE_DATABASE_ID` cho Staging; bổ sung `FoxEnvironmentConfig.kt` + `BuildConfig.FOX_ENV` (không thêm Gradle `productFlavors` và không sửa `signingConfigs`); tạo trọn bộ `/staging/`.

---

## 3. Danh sách tệp đã thay đổi / tạo mới trong lượt kiểm toán

- **Backend & Rules:**
  - `functions/src/types.ts`
  - `functions/src/financeService.ts`
  - `functions/src/notificationService.ts`
  - `firestore.rules`
  - `firebase-blueprint.json`
- **Android App:**
  - `app/build.gradle.kts`
  - `app/src/main/java/com/example/config/FoxEnvironmentConfig.kt` *(mới)*
  - `app/src/main/java/com/example/data/remote/FoxCloudModels.kt`
  - `app/src/main/java/com/example/data/remote/FoxCloudRepository.kt`
  - `app/src/main/java/com/example/ui/FoxCloudViewModel.kt`
  - `app/src/main/java/com/example/notifications/FoxNotificationRouter.kt`
  - `app/src/main/java/com/example/notifications/FoxFirebaseMessagingService.kt`
  - `app/src/main/java/com/example/MainActivity.kt`
  - `app/src/main/java/com/example/ui/screens/CloudAccountAndOrdersScreens.kt`
  - `app/src/main/java/com/example/ui/screens/HomeScreen.kt`
  - `app/src/main/java/com/example/ui/screens/RoleWorkspacesScreen.kt`
- **Kiểm thử tự động:**
  - `firestore.test.js` (11 test cases)
  - `functions/test/e2e.pilot.emulator.test.js` (8 test cases)
  - `app/src/test/java/com/example/FoxFcmAndPilotTest.kt` (6 test cases)
- **Bộ chuẩn bị Firebase Staging cô lập & Nghiệm thu:**
  - `firestore.indexes.json` *(mới — composite index cho truy vấn đơn chờ tài xế/gian hàng)*
  - `functions/.env.staging.example` *(mới — mẫu biến môi trường `FOX_FIRESTORE_DATABASE_ID=fox-staging-db`)*
  - `staging/.firebaserc.staging.example` *(mới)*
  - `staging/firebase.staging.json` *(mới)*
  - `staging/google-services.staging.json.example` *(mới — mẫu cấu hình Android Staging)*
  - `staging/deploy-staging.ps1` & `staging/deploy-staging.sh` *(mới — yêu cầu `--project` tường minh, chặn live `exit 3`, kiểm tra DB đích, tự sao lưu `firestore.rules`, chặn deploy Functions nếu chưa có `-BlazeApproved` `exit 4`, mặc định dry-run)*
  - `staging/build-staging-apk.ps1` & `staging/build-staging-apk.sh` *(mới — build Android Staging APK tách biệt qua `-PfoxEnv=STAGING` & tự khôi phục `app/google-services.json`)*
  - `staging/seed-demo-staging.mjs` *(khớp 1:1 schema `firestore.rules` và đã kiểm chứng PASS trên Firestore Emulator cục bộ)*
  - `staging/smoke-test-staging.mjs` *(đã kiểm chứng PASS trên Firestore Emulator cục bộ)*
  - `staging/rollback-staging.ps1` & `staging/rollback-staging.sh` *(mới)*
  - `staging/STAGING_SETUP_GUIDE.md` *(mới)*
  - `FOX_STAGING_DEPLOY_CHECKLIST.md` *(mới — hướng dẫn đầy đủ cho Windows 10 & 9 hạng mục kiểm thử thực tế)*
  - `FOX_PILOT_ACCEPTANCE.md` *(tệp này)*

---

## 4. Lệnh kiểm thử tái lập & Kết quả đo thực tế

| Hạng mục | Lệnh tái lập | Kết quả thực tế |
| :--- | :--- | :--- |
| **Build Cloud Functions v2 (TypeScript)** | `npm --prefix functions run build` | **PASS** (exit 0, 0 lỗi) |
| **5 bộ kiểm thử Firebase Emulator (Rules + Finance + Catalog + Routes + E2E Pilot)** | `FIRESTORE_EMULATOR_HOST=127.0.0.1:8085 node --test --test-concurrency=1 firestore.test.js functions/test/finance.emulator.test.js functions/test/catalog.emulator.test.js functions/test/routes.emulator.test.js functions/test/e2e.pilot.emulator.test.js` | **40/40 PASS** (0 fail, 0 skipped): `firestore.test.js` 11/11, `finance` 9/9, `catalog` 6/6, `routes` 6/6, `e2e.pilot` 8/8 |
| **Build Android Debug APK** | `compile_applet` (Gradle debug assemble) | **PASS** (`app/build/outputs/apk/debug/app-debug.apk`) |
| **Android Unit & Robolectric Tests** | `gradle :app:testDebugUnitTest --rerun-tasks` | **21/21 PASS** (0 fail, 0 errors): `ExampleRobolectricTest` 1, `ExampleUnitTest` 1, `FoxDemoPricingEngineTest` 4, `FoxFcmAndPilotTest` 6 (gồm guardrail STAGING DB ID), `FoxLocationAndMapTest` 9 |
| **Kiểm toán bảo mật & quyền tĩnh** | `grep -rInE "ACCESS_BACKGROUND_LOCATION\|AIza[0-9A-Za-z_-]{20,}\|signInAnonymously\|FLUTTER_NOTIFICATION_CLICK\|FirebaseFirestore\.getInstance\(\)" app/src functions/src` | **PASS** — 0 vi phạm trong mã production (`AndroidManifest.xml` chỉ gồm `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, `POST_NOTIFICATIONS`) |
| **Chốt chặn bảo vệ Live & Blaze (Staging Guardrails)** | `deploy-staging.sh`, `build-staging-apk.sh`, `seed-demo-staging.mjs`, `smoke-test-staging.mjs`, `rollback-staging.sh` trỏ vào `gen-lang-client-0957111252` và `deploy-staging.sh --target all` không có `--blaze-approved` | **PASS** — Cả 5 script tự hủy `exit 3` khi gặp dự án live; deploy Functions thiếu duyệt Blaze tự hủy `exit 4` |
| **Seed & Smoke Test Staging trên Firestore Emulator cục bộ** | `FIRESTORE_EMULATOR_HOST=127.0.0.1:8085 node staging/seed-demo-staging.mjs --project fox-pilot-staging --database fox-staging-db --confirm && node staging/smoke-test-staging.mjs --project fox-pilot-staging --database fox-staging-db` | **PASS** — 2 gian hàng, 4 sản phẩm subcollection VND nguyên, 5 hồ sơ mẫu, 1 admin & 2 Ví DEMO xác minh thành công (0 ghi lên cloud) |

---

## 5. Trạng thái các phân hệ trọng yếu

- **Firebase Live (`gen-lang-client-0957111252` / `ai-studio-android-1a796de6-79f0-4283-8d0b-703e709b27e4`):** **KHÔNG BỊ DEPLOY, KHÔNG BỊ GHI DỮ LIỆU, KHÔNG BẬT BILLING.**
- **Firebase Staging:** Đã hoàn tất bộ cấu hình, kịch bản seed, smoke test, rollback và hướng dẫn tại `staging/STAGING_SETUP_GUIDE.md`; **chưa triển khai lên cloud** vì chờ Chủ dự án tạo project Staging riêng.
- **GPS & Bản đồ:** GPS thật qua Fused Location Provider chỉ kích hoạt khi người dùng cấp quyền; không yêu cầu vị trí nền. Chế độ **Bản đồ DEMO** hiển thị nhãn minh bạch; các tính năng Google Maps SDK / Routes API thật đang **tạm khóa an toàn** cho tới khi có `MAPS_API_KEY` hợp lệ.
- **FCM (Thông báo đẩy):** Logic điều phối, phân vai trò, chống giả mạo, dọn token hỏng, bảo vệ PII và deep-link đều **PASS** trong kiểm thử tự động; **đẩy push vật lý trên thiết bị thật đang ở trạng thái `CHƯA KIỂM THỬ`**.
- **Tài chính Ví FOX:** Hoạt động hoàn toàn theo chế độ **VÍ DEMO — KHÔNG PHẢI TIỀN THẬT**, số nguyên VND, chưa kết nối cổng thanh toán thật.

---

## 6. Checklist kiểm thử đa thiết bị thật (2–4 máy Android) trên Staging

Thực hiện sau khi triển khai Staging và cài APK Staging lên tối thiểu 2–4 thiết bị Android thật:

- [ ] **Thiết bị 1 (Khách hàng):** Đăng nhập Google -> Nạp Ví DEMO -> Đặt đơn **Đặt xe (RIDE)** có áp mã voucher `FOX10` -> Xác nhận lần dùng lại `FOX10` thứ hai bị từ chối.
- [ ] **Thiết bị 2 (Tài xế đã duyệt):** Nhận thông báo FCM thật khi có đơn mới -> Nhấn vào thông báo mở đúng màn hình Tài xế -> Nhận đơn -> Đổi trạng thái `Đến đón -> Đang chạy -> Hoàn thành`.
- [ ] **Thiết bị 1 (Khách hàng):** Quan sát trạng thái cập nhật real-time không cần tải lại -> Sau khi đơn `Hoàn thành`, gửi đánh giá 5 sao -> Xác nhận không thể đánh giá lần thứ 2.
- [ ] **Kiểm thử tranh chấp tài xế (2 máy Tài xế):** 2 máy tài xế cùng bấm nhận 1 đơn `Giao hàng (DELIVERY)` đồng thời -> Xác nhận đúng 1 máy thành công, máy còn lại báo đơn đã có người nhận.
- [ ] **Thiết bị 3 (Gian hàng) + Thiết bị 1 (Khách hàng) + Thiết bị 2 (Tài xế):** Chạy trọn luồng **Đặt đồ ăn (FOOD)** và **Mua sắm (SHOPPING)** -> Xác nhận trừ kho chính xác, gian hàng xác nhận -> tài xế giao -> đối soát hoa hồng tài xế, hoa hồng gian hàng và doanh thu FOX DEMO.
- [ ] **Kiểm thử tồn kho cuối (2 máy Khách hàng):** 2 máy cùng đặt sản phẩm `prod_shop_staging_limited` (tồn kho = 1) -> Xác nhận đúng 1 đơn thành công, đơn còn lại bị từ chối hết hàng và không bị trừ Ví DEMO.
- [ ] **Thiết bị 4 (Quản trị viên):** Duyệt đơn đăng ký đối tác mới -> Kiểm tra thiết bị đối tác nhận FCM duyệt hồ sơ (và payload push không lộ email/SĐT) -> Kiểm tra thống kê doanh thu DEMO & sổ cái.
- [ ] **Kiểm thử FCM nền & tắt ứng dụng:** Đưa ứng dụng về nền và đóng hẳn tiến trình trên Android 13+ (đã cấp `POST_NOTIFICATIONS`) -> Gửi sự kiện đơn -> Xác nhận thông báo hiển thị và nhấn vào mở đúng màn hình đích.
- [ ] **Kiểm thử hủy đơn & hoàn tiền:** Khách hủy đơn ở trạng thái hợp lệ -> Kiểm tra Ví DEMO hoàn đúng số tiền nguyên VND và tồn kho được cộng trả lại.

---

## 7. Việc Chủ dự án cần làm & Điều kiện còn thiếu trước khi chốt Pilot

### Việc Chủ dự án cần thực hiện
1. Tạo một **Firebase Project Staging riêng** (tách biệt hoàn toàn khỏi `gen-lang-client-0957111252`) theo `staging/STAGING_SETUP_GUIDE.md`.
2. Đăng ký Android app `com.aistudio.fox.superapp` kèm SHA-1/SHA-256 trên project Staging và bật Google Sign-In + FCM API v1.
3. Quyết định phê duyệt ngân sách & bật gói Blaze (kèm Budget Alert) **riêng cho project Staging** nếu muốn triển khai 12 Cloud Functions v2 lên Staging.
4. Quyết định có cung cấp `MAPS_API_KEY` (Google Maps SDK + Routes API) cho giai đoạn Pilot hay tiếp tục giữ chế độ **Bản đồ DEMO**.
5. Thực hiện toàn bộ **Checklist đa thiết bị thật (Mục 6)** và ghi nhận kết quả thực tế.

### Điều kiện bắt buộc còn thiếu trước khi đổi trạng thái sang `ĐẠT CHỐT PILOT`
1. Triển khai thành công Rules + 12 Cloud Functions v2 lên dự án Firebase Staging riêng và `smoke-test-staging.mjs` đạt 100% **PASS**.
2. Cài đặt APK Staging trên 2–4 thiết bị Android thật và hoàn tất 100% **Checklist Mục 6** (0 lỗi nghiêm trọng).
3. Xác nhận thực tế nhận thông báo **FCM thật** trên thiết bị Android vật lý (cả foreground lẫn background/killed) và không có rò rỉ dữ liệu nhạy cảm.
4. Xác nhận chính thức từ Chủ dự án về việc chấp nhận chạy Pilot bằng **Ví DEMO** và **Bản đồ DEMO** (hoặc đã tích hợp `MAPS_API_KEY` thật và kiểm thử lại).
