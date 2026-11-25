# Hướng dẫn Chuẩn bị & Triển khai Môi trường Firebase Staging Riêng (FOX Pilot)

> **NGUYÊN TẮC AN TOÀN TUYỆT ĐỐI**
> - **KHÔNG** triển khai lên Firebase Live (`gen-lang-client-0957111252` / database `ai-studio-android-1a796de6-79f0-4283-8d0b-703e709b27e4`).
> - **KHÔNG** bật Billing (Blaze) hoặc API có phí (Google Maps SDK / Routes API / Cloud Functions trả phí) khi **chưa được Chủ dự án phê duyệt**.
> - Mọi số tiền trong giai đoạn Staging Pilot đều là **VÍ DEMO — KHÔNG PHẢI TIỀN THẬT**.
> - Dành cho thao tác từng bước trên **Windows 10 PowerShell**, xem thêm tài liệu đầy đủ tại **`FOX_STAGING_DEPLOY_CHECKLIST.md`**.

---

## 1. Kiến trúc cô lập môi trường (DEMO / STAGING / PRODUCTION)

Android tách biệt môi trường qua `FoxEnvironmentConfig.kt` và Gradle BuildConfig (`-PfoxEnv`, `-PfoxStagingProjectId`, `-PfoxStagingDbId`) mà **không cần sửa `signingConfigs` hay thêm `productFlavors`**:

| Môi trường | `BuildConfig.FOX_ENV` | Dự án Firebase | Firestore Database ID | Ghi chú |
| :--- | :--- | :--- | :--- | :--- |
| **DEMO (mặc định)** | `DEMO` | Local Emulator / Room DEMO | Từ `firebase_applet_config.xml` | Mặc định khi build thông thường |
| **STAGING (riêng)** | `STAGING` | Truyền qua `-PfoxStagingProjectId` + `staging/google-services.staging.json` | `fox-staging-db` (mặc định) | Tự động **chặn runtime** nếu phát hiện trỏ nhầm `gen-lang-client-0957111252` |
| **PRODUCTION (khóa)** | `PRODUCTION` | `gen-lang-client-0957111252` | `ai-studio-android-1a796de6-79f0-4283-8d0b-703e709b27e4` | **ĐANG KHÓA** — Mọi script Staging đều tự hủy (`exit 3`) nếu trỏ vào đây |

---

## 2. Danh sách công cụ trong thư mục `staging/`

| Tệp | Chức năng | Cơ chế an toàn |
| :--- | :--- | :--- |
| `staging/.firebaserc.staging.example` | Mẫu alias dự án Staging (không gán cứng ID thật) | Chỉ chứa placeholder |
| `staging/firebase.staging.json` | Cấu hình Firestore (`fox-staging-db`, `firestore.rules`, `firestore.indexes.json`) & Functions v2 | Không chứa project live |
| `staging/google-services.staging.json.example` | Mẫu vị trí đặt tệp `staging/google-services.staging.json` (git-ignored) | Không chứa khóa bí mật |
| `functions/.env.staging.example` | Mẫu biến môi trường `FOX_FIRESTORE_DATABASE_ID=fox-staging-db` cho Cloud Functions v2 | Git-ignored khi tạo `functions/.env.<staging-id>` |
| `staging/deploy-staging.ps1` / `.sh` | Triển khai Rules/Indexes/Functions lên Staging | Mặc định dry-run; yêu cầu `--project`; tự hủy `exit 3` nếu gặp live; kiểm tra DB đích tồn tại; tự sao lưu `firestore.rules`; yêu cầu `-BlazeApproved` khi deploy Functions |
| `staging/seed-demo-staging.mjs` | Nạp 5 tài khoản mẫu, 1 admin, 2 Ví DEMO, 2 gian hàng & 4 sản phẩm subcollection `/merchants/{id}/products` khớp 1:1 `firestore.rules` | Mặc định dry-run; dùng `merge: true` không xóa dữ liệu; tự hủy `exit 3` nếu gặp live |
| `staging/smoke-test-staging.mjs` | Kiểm tra nhanh dữ liệu, subcollection sản phẩm và tính nguyên vẹn số nguyên VND sau khi deploy | Tự hủy `exit 3` nếu gặp live |
| `staging/build-staging-apk.ps1` / `.sh` | Build APK Staging cô lập (`staging/out/fox-staging-<id>-debug.apk`) | Kiểm tra `google-services.staging.json` khớp `com.aistudio.fox.superapp` & khác live; luôn khôi phục `app/google-services.json` gốc |
| `staging/rollback-staging.ps1` / `.sh` | Khôi phục `firestore.rules` từ bản sao lưu `backups/` lên Staging | Mặc định dry-run; tự hủy `exit 3` nếu gặp live |
