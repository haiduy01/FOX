# FOX FULL FEATURE PILOT — CHECKLIST TRIỂN KHAI STAGING TRÊN WINDOWS 10

> **TRẠNG THÁI NGHIỆM THU HIỆN TẠI:** **CHƯA ĐẠT CHỐT PILOT (STAGING RELEASE PREPARATION READY)**
> **QUY TẮC BẮT BUỘC:**
> - **TUYỆT ĐỐI KHÔNG** triển khai lên dự án hiện tại `gen-lang-client-0957111252` hoặc database `ai-studio-android-1a796de6-79f0-4283-8d0b-703e709b27e4`.
> - **KHÔNG** bật Billing (Blaze) hoặc bất kỳ dịch vụ trả phí nào khi chưa có quyết định phê duyệt trực tiếp từ Chủ dự án.
> - **KHÔNG** xóa dữ liệu hiện có, **KHÔNG** thay đổi giao diện thương hiệu FOX, **KHÔNG** dùng tiền thật (mọi giao dịch đều gắn nhãn `DEMO — KHÔNG PHẢI TIỀN THẬT`).

---

## PHẦN A — DANH SÁCH FILE CẦN XUẤT TỪ AI STUDIO VỀ MÁY WINDOWS 10

Xuất toàn bộ dự án ZIP (hoặc đẩy GitHub rồi clone về Windows 10, ví dụ `C:\fox-android`). Bảo đảm các thư mục/tệp cốt lõi sau có đầy đủ:

1. **Mã nguồn Android & Gradle:**
   - `app/` (bao gồm `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/example/config/FoxEnvironmentConfig.kt`, `app/src/main/res/`)
   - `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`
   - `debug.keystore.base64` (để giữ nguyên SHA-1/SHA-256 nhất quán nếu build debug trên Windows 10)
2. **Backend Firebase & Security Rules:**
   - `firestore.rules`
   - `firestore.indexes.json`
   - `firebase-blueprint.json`
   - `functions/` (`package.json`, `tsconfig.json`, `src/`, `test/`, `.env.staging.example`)
3. **Bộ triển khai Staging cô lập (`staging/`):**
   - `staging/.firebaserc.staging.example`
   - `staging/firebase.staging.json`
   - `staging/google-services.staging.json.example`
   - `staging/deploy-staging.ps1` (Windows 10 PowerShell) & `staging/deploy-staging.sh`
   - `staging/build-staging-apk.ps1` (Windows 10 PowerShell) & `staging/build-staging-apk.sh`
   - `staging/seed-demo-staging.mjs`
   - `staging/smoke-test-staging.mjs`
   - `staging/rollback-staging.ps1` (Windows 10 PowerShell) & `staging/rollback-staging.sh`
   - `staging/STAGING_SETUP_GUIDE.md`
4. **Tài liệu kiểm thử & nghiệm thu:**
   - `FOX_STAGING_DEPLOY_CHECKLIST.md` (tệp này)
   - `FOX_PILOT_ACCEPTANCE.md`
   - `firestore.test.js`

---

## PHẦN B — CHUẨN BỊ MÔI TRƯỜNG WINDOWS 10 (CHỈ LÀM 1 LẦN)

Mở **Windows PowerShell** (không cần quyền Administrator) tại thư mục dự án (ví dụ `C:\fox-android`):

```powershell
# 1. Kiểm tra công cụ bắt buộc (Node 22+, Java 17+, Firebase CLI, Gradle/Android Studio)
node -v          # Khuyến nghị v22.x
npm -v
java -version    # Khuyến nghị JDK 17 trở lên
firebase --version

# Nếu chưa có Firebase CLI:
npm install -g firebase-tools

# 2. Cho phép chạy script PowerShell cục bộ trong phiên hiện tại
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass

# 3. Cài đặt dependencies cho Cloud Functions & kiểm tra biên dịch TypeScript
npm --prefix functions install
npm --prefix functions run build

# 4. Đăng nhập Firebase CLI bằng tài khoản Google quản trị dự án
firebase login
```

### Khôi phục `debug.keystore` và xác minh SHA-1 / SHA-256 trên Windows 10

Dự án FOX đi kèm `debug.keystore.base64` với dấu vân tay đã xác minh:
- **SHA-1:** `FD:E3:E6:38:5C:D1:36:2A:5B:6D:79:7B:11:0E:33:E1:5A:33:2B:45`
- **SHA-256:** `C5:A7:BC:FF:43:70:58:35:C6:51:E2:A8:66:C2:CE:4F:6A:4B:A2:E5:F5:E1:BB:C6:60:A9:A6:10:7A:F8:28:2F`

Chạy lệnh sau trong PowerShell để giải mã `debug.keystore` (nếu chưa có) và kiểm chứng lại vân tay:

```powershell
if (-not (Test-Path ".\debug.keystore")) {
  [IO.File]::WriteAllBytes(
    (Join-Path (Get-Location) "debug.keystore"),
    [Convert]::FromBase64String((Get-Content ".\debug.keystore.base64" -Raw))
  )
}

keytool -list -v -keystore ".\debug.keystore" -alias androiddebugkey -storepass android -keypass android | Select-String "SHA1:|SHA256:"
```

*(Lưu ý: Nếu bạn ký APK bằng keystore riêng của máy Windows khác, hãy lấy thêm SHA-1 và SHA-256 của keystore đó rồi dán vào Firebase Console Staging.)*

---

## PHẦN C — THAO TÁC BẮT BUỘC CHỦ DỰ ÁN PHẢI LÀM TRONG FIREBASE CONSOLE

> **Chưa gán cứng Project ID staging trong mã nguồn.** Hãy đặt tên project mới rõ ràng, ví dụ: `fox-pilot-staging-01`.

### Bước 1 — Tạo Firebase Project Staging mới (tách biệt hoàn toàn)
1. Truy cập [https://console.firebase.google.com/](https://console.firebase.google.com/) -> chọn **Add project**.
2. Đặt tên project mới (ví dụ `fox-pilot-staging-01`) — **tuyệt đối khác `gen-lang-client-0957111252`**.
3. Ghi lại **Project ID Staging** chính xác (đặt vào biến PowerShell `$STAGING_ID = "<PROJECT_ID_CUA_BAN>"`).

### Bước 2 — Thêm ứng dụng Android Staging + SHA-1 / SHA-256
1. Trong Project Staging -> **Project settings -> Your apps -> Add app -> Android**.
2. Nhập chính xác:
   - **Android package name:** `com.aistudio.fox.superapp`
   - **App nickname:** `FOX Pilot Staging`
   - **Debug signing certificate SHA-1:** `FD:E3:E6:38:5C:D1:36:2A:5B:6D:79:7B:11:0E:33:E1:5A:33:2B:45`
3. Sau khi tạo xong, bấm **Add fingerprint** để thêm tiếp **SHA-256:**
   `C5:A7:BC:FF:43:70:58:35:C6:51:E2:A8:66:C2:CE:4F:6A:4B:A2:E5:F5:E1:BB:C6:60:A9:A6:10:7A:F8:28:2F`
4. Tải tệp `google-services.json` vừa tạo và lưu vào đúng đường dẫn:
   `C:\fox-android\staging\google-services.staging.json` *(tệp này đã có trong `.gitignore`, không bị commit lên Git)*.

### Bước 3 — Bật Authentication (Google Sign-In)
1. Vào **Build -> Authentication -> Get started**.
2. Mở tab **Sign-in method -> Google -> Enable**.
3. Chọn **Project support email** (ví dụ email quản trị của bạn) -> **Save**.
4. **Tải lại** `google-services.json` từ **Project settings** sau khi bật Google Sign-In (để tệp có đầy đủ `oauth_client` loại 3 cho `default_web_client_id`) và ghi đè lại vào `staging\google-services.staging.json`.
5. **KHÔNG** bật Anonymous Sign-In.

### Bước 4 — Tạo Named Firestore Database `fox-staging-db` tại `asia-southeast1`
1. Vào **Build -> Firestore Database -> Create database**.
2. Chọn **Database ID:** `fox-staging-db` *(để tách biệt hoàn toàn với database live `ai-studio-android-1a796de6-79f0-4283-8d0b-703e709b27e4`)*.
3. Chọn **Location:** `asia-southeast1` (Singapore).
4. Chọn **Start in production mode** (mặc định chặn toàn bộ cho đến khi deploy `firestore.rules`).

### Bước 5 — Kiểm tra Firebase Cloud Messaging (FCM API V1)
1. Vào **Project settings -> Cloud Messaging**.
2. Xác nhận **Firebase Cloud Messaging API (V1)** đang ở trạng thái **Enabled** (không dùng Legacy server key).

### Bước 6 — Điểm kiểm soát chi phí (Billing / Blaze) cho 12 Cloud Functions v2
- **Nếu CHƯA duyệt bật Blaze:** Bạn vẫn có thể deploy **Firestore Rules & Indexes** (`-Target rules`), seed dữ liệu và kiểm tra đăng nhập/Firestore mà **KHÔNG phát sinh chi phí**.
- **Nếu ĐÃ duyệt bật Blaze riêng cho project Staging:**
  1. Vào **Usage and billing -> Modify plan -> Blaze (Pay as you go)** chỉ cho project Staging.
  2. Thiết lập **Google Cloud Budget Alert** ngay lập tức (ví dụ cảnh báo ở mức 1 USD, 3 USD, 5 USD/tháng).
  3. **KHÔNG** bật Google Maps SDK / Routes API trả phí trừ khi Chủ dự án quyết định bổ sung `MAPS_API_KEY` riêng.

### Bước 7 — Chuẩn bị 4 tài khoản Google thử nghiệm (cho 2–4 thiết bị thật)
| Vai trò kiểm thử | Email Google dùng trên máy thật | Quyền trong Staging |
| :--- | :--- | :--- |
| **Admin (Quản trị)** | `hahaiduy28@gmail.com` (hoặc truyền `-StagingAdminEmail` khi deploy) | Đăng nhập lần đầu tự nhận quyền `admin` (email đã xác thực) |
| **Customer (Khách hàng)** | Tài khoản Google thử nghiệm #1 | Mặc định `customer` |
| **Driver (Tài xế)** | Tài khoản Google thử nghiệm #2 | Đăng ký tài xế trên app -> Admin duyệt -> `driverStatus = approved` |
| **Merchant (Gian hàng)** | Tài khoản Google thử nghiệm #3 | Đăng ký gian hàng trên app -> Admin duyệt -> `merchantStatus = approved` |

*(Ngoài ra script seed sẽ nạp sẵn 2 gian hàng DEMO `Bếp Cáo Quán` & `FOX Mart Express` kèm 4 sản phẩm mẫu và sản phẩm tồn kho = 1 để test ngay.)*

---

## PHẦN D — LỆNH TRIỂN KHAI STAGING AN TOÀN TRÊN WINDOWS 10 POWERSHELL

Thay `<STAGING_PROJECT_ID>` bằng Project ID bạn vừa tạo ở Phần C:

```powershell
$STAGING_ID = "<STAGING_PROJECT_ID>"
$STAGING_DB = "fox-staging-db"

# 1. Kiểm tra chốt chặn bảo vệ Production (PHẢI báo ABORT / exit code 3)
.\staging\deploy-staging.ps1 -StagingProjectId "gen-lang-client-0957111252" -Target rules -ConfirmDeploy
# Kỳ vọng: [ABORT][GUARDRAIL] Refusing to deploy to LIVE Firebase project

# 2. Chạy Dry-Run để rà soát kế hoạch deploy (KHÔNG ghi lên Firebase)
.\staging\deploy-staging.ps1 -StagingProjectId $STAGING_ID -StagingDatabaseId $STAGING_DB -Target rules

# 3. Deploy Firestore Rules & Composite Indexes lên Staging (tự sao lưu backups\firestore.rules.<ts>.bak)
.\staging\deploy-staging.ps1 -StagingProjectId $STAGING_ID -StagingDatabaseId $STAGING_DB -Target rules -ConfirmDeploy

# 4. [CHỈ CHẠY SAU KHI CHỦ DỰ ÁN DUYỆT BẬT BLAZE CHO PROJECT STAGING]
#    Deploy 12 Cloud Functions v2 lên vùng asia-southeast1 kết nối fox-staging-db:
.\staging\deploy-staging.ps1 -StagingProjectId $STAGING_ID -StagingDatabaseId $STAGING_DB -Target all -ConfirmDeploy -BlazeApproved
```

### Nạp dữ liệu mẫu DEMO & Chạy Smoke Test trên Staging

Để script Node (`firebase-admin`) kết nối tới project Staging trên Windows 10, tải tệp Service Account JSON từ **Firebase Console Staging -> Project settings -> Service accounts -> Generate new private key** lưu vào thư mục ngoài repo (ví dụ `C:\secrets\fox-staging-sa.json`, **KHÔNG** lưu trong repo Git):

```powershell
$env:GOOGLE_APPLICATION_CREDENTIALS = "C:\secrets\fox-staging-sa.json"

# 1. Dry-run kiểm tra payload seed trước:
node staging/seed-demo-staging.mjs --project $STAGING_ID --database $STAGING_DB --dry-run

# 2. Thực thi nạp dữ liệu mẫu DEMO (dùng merge: true, không xóa dữ liệu):
node staging/seed-demo-staging.mjs --project $STAGING_ID --database $STAGING_DB --confirm

# 3. Chạy Smoke Test xác nhận gian hàng, subcollection sản phẩm và Ví DEMO số nguyên VND:
node staging/smoke-test-staging.mjs --project $STAGING_ID --database $STAGING_DB
```

### Build Android Staging APK (Tách biệt hoàn toàn DEMO & Production)

```powershell
# 1. Kiểm tra xác thực staging\google-services.staging.json (Dry-Run)
.\staging\build-staging-apk.ps1 -StagingProjectId $STAGING_ID -StagingDatabaseId $STAGING_DB -DryRun

# 2. Build APK Staging (tự tạm đổi app\google-services.json, build với FOX_ENV=STAGING,
#    xuất file staging\out\fox-staging-<STAGING_ID>-debug.apk và TỰ KHÔI PHỤC file gốc)
.\staging\build-staging-apk.ps1 -StagingProjectId $STAGING_ID -StagingDatabaseId $STAGING_DB
```

Khi mở APK Staging trên điện thoại và vào tab **Tài khoản**, ứng dụng sẽ hiển thị rõ nhãn cam:
`Môi trường: STAGING • DB: fox-staging-db • Project: <STAGING_PROJECT_ID>`
(và tự động chặn runtime nếu phát hiện ghép nhầm tệp `google-services.json` của dự án live `gen-lang-client-0957111252`).

---

## PHẦN E — KỊCH BẢN KIỂM THỬ THỰC TẾ TRÊN 2–4 THIẾT BỊ ANDROID THẬT

> **Mọi hạng mục bên dưới hiện đang ở trạng thái `CHƯA KIỂM THỬ` cho đến khi hoàn tất trên điện thoại Android thật.**

| # | Hạng mục kiểm thử thực tế | Cách thực hiện & Tiêu chí ĐẠT | Kết quả thực tế |
| :-: | :--- | :--- | :---: |
| **1** | **Khách hàng tạo đơn cả 4 dịch vụ** | Trên Máy 1 (Khách): nạp Ví DEMO -> tạo đơn **Đặt xe (RIDE)** (áp mã `FOX10`), **Giao hàng (DELIVERY)**, **Đặt đồ ăn (FOOD)** từ danh mục Cloud, **Mua sắm (SHOPPING)** từ danh mục Cloud. Xác nhận giá VND nguyên khớp server, lần dùng `FOX10` thứ 2 bị từ chối. | `CHƯA KIỂM THỬ` |
| **2** | **Tài xế nhận & hoàn thành đơn** | Trên Máy 2 (Tài xế đã duyệt): bật trực tuyến -> thấy đơn RIDE/DELIVERY chờ -> nhận đơn -> cập nhật tuần tự `Đến đón -> Đang chạy -> Hoàn thành`. Máy 1 thấy trạng thái cập nhật real-time và gửi được đánh giá 1–5 sao đúng 1 lần. | `CHƯA KIỂM THỬ` |
| **3** | **Gian hàng xác nhận, chuẩn bị & giao đơn** | Trên Máy 3 (Gian hàng đã duyệt): nhận đơn FOOD/SHOPPING -> `Xác nhận -> Đang chuẩn bị -> Sẵn sàng lấy hàng` -> Máy 2 (Tài xế) nhận giao -> hoàn thành. Tồn kho giảm chính xác trên Firestore. | `CHƯA KIỂM THỬ` |
| **4** | **Quản trị duyệt đối tác, xử lý đơn & báo cáo** | Trên Máy 4 (Admin): duyệt hồ sơ đăng ký tài xế/gian hàng mới -> xem tổng quan doanh thu DEMO, số đơn hoàn thành, sổ cái `platform_revenue` (tổng bằng số nguyên VND). | `CHƯA KIỂM THỬ` |
| **5** | **Thanh toán, hoàn tiền & hoa hồng DEMO** | Kiểm tra thanh toán Ví DEMO trừ đúng tổng VND; hủy đơn hợp lệ hoàn đúng số tiền + hoàn tồn kho; hoàn thành đơn chia đúng hoa hồng tài xế, gian hàng và doanh thu FOX DEMO (không chi trùng, không số dư âm). | `CHƯA KIỂM THỬ` |
| **6** | **FCM nhận thông báo thực tế** | Cấp quyền `POST_NOTIFICATIONS` (Android 13+). Kiểm tra nhận push thật khi app đang mở, đang ở nền và đã tắt hẳn -> bấm thông báo mở đúng màn hình theo vai trò -> xác nhận nội dung push **không chứa** email/SĐT/UID. | `CHƯA KIỂM THỬ` |
| **7** | **Đăng xuất / đăng nhập & khôi phục phiên** | Đóng app mở lại (khôi phục phiên đăng nhập tự động) -> bấm Đăng xuất (xác nhận dọn token FCM) -> đăng nhập lại bằng tài khoản Google khác -> không lẫn dữ liệu/listener của phiên cũ. | `CHƯA KIỂM THỬ` |
| **8** | **Kiểm tra dữ liệu khi mất mạng** | Tắt Wi-Fi/4G trên điện thoại -> xác nhận app không crash, hiển thị lỗi mạng rõ ràng khi thao tác Cloud, và **Chế độ DEMO offline (Room SQLite)** vẫn hoạt động độc lập; bật mạng lại -> dữ liệu Cloud đồng bộ đúng trạng thái mới nhất. | `CHƯA KIỂM THỬ` |
| **9** | **Phân quyền & tính toàn vẹn tài chính** | Thử tài xế chưa duyệt (không thấy/không nhận được đơn), 2 tài xế bấm nhận 1 đơn cùng lúc (chỉ 1 người thành công), 2 khách cùng mua `prod_shop_staging_limited` tồn kho = 1 (chỉ 1 đơn thành công), khách không sửa được vai trò admin hay số dư ví. | `CHƯA KIỂM THỬ` |

---

## PHẦN F — QUY TRÌNH ROLLBACK STAGING TRÊN WINDOWS 10

Nếu phát hiện lỗi sau khi deploy lên Staging, chạy rollback **chỉ trên Staging**:

```powershell
# 1. Dry-run kiểm tra bản sao lưu trước khi khôi phục
.\staging\rollback-staging.ps1 `
  -StagingProjectId $STAGING_ID `
  -StagingDatabaseId $STAGING_DB `
  -BackupRulesPath "backups\firestore.rules.<TIMESTAMP>.bak"

# 2. Thực thi khôi phục Firestore Rules trên Staging
.\staging\rollback-staging.ps1 `
  -StagingProjectId $STAGING_ID `
  -StagingDatabaseId $STAGING_DB `
  -BackupRulesPath "backups\firestore.rules.<TIMESTAMP>.bak" `
  -ExecuteRollback

# 3. Rollback Cloud Functions v2 trên Staging (nếu cần):
git checkout <COMMIT_ON_DINH> -- functions/
npm --prefix functions run build
.\staging\deploy-staging.ps1 -StagingProjectId $STAGING_ID -StagingDatabaseId $STAGING_DB -Target functions -ConfirmDeploy -BlazeApproved
```
