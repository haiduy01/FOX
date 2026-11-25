# FOX – Chạm là tới! | Hướng dẫn Triển khai Production V1.0 (Windows 10)

> **CẢNH BÁO AN TOÀN QUAN TRỌNG**
> - Tài liệu và bộ công cụ này dành cho việc đóng gói và triển khai phiên bản thương mại **Production V1.0** của ứng dụng **FOX – Chạm là tới!**.
> - **TUYỆT ĐỐI KHÔNG** trỏ nhầm vào môi trường Staging cũ (`fox-staging-db`) hay dự án thử nghiệm mẫu khi vận hành thực tế.
> - Bản Release sử dụng cấu hình bảo mật Production nghiêm ngặt (`BuildConfig.FOX_ENV = "PRODUCTION"`), yêu cầu khóa ký (signing key) chính thức được quản lý ngoài mã nguồn, cấu hình `google-services.json` đúng project Production, và không sử dụng dữ liệu giả lập (mock/demo fallback) cho các giao dịch tài chính.

---

## 1. Chuẩn bị Môi trường Build trên Windows 10

Mở **Windows PowerShell** tại thư mục gốc repository (ví dụ `C:\fox-android`) và kiểm tra phiên bản các công cụ:

```powershell
node -v          # Khuyến nghị Node.js v22+
npm -v
java -version    # JDK 17+
firebase --version
Test-Path .\gradlew.bat
Test-Path .\gradle\wrapper\gradle-wrapper.properties
```

Cài đặt các gói phụ thuộc Cloud Functions:
```powershell
npm --prefix functions install
npm --prefix functions run build
```

---

## 2. Các bước cấu hình trên Firebase Console (Production)

1. **Tạo Project Firebase Production riêng** (tên tùy chọn, ví dụ `fox-production-vn`).
2. **Thêm ứng dụng Android:**
   - Package name: `com.aistudio.fox.superapp`
   - Cấu hình SHA-1 và SHA-256 chính thức từ **Khóa ký Release (Release Keystore)** của doanh nghiệp (tuyệt đối không dùng `debug.keystore` cho bản phát hành CH Play / App Store).
3. **Tải tệp `google-services.json` chính thức** và đặt vào thư mục bảo mật riêng hoặc thay thế tạm thời trước khi build Release.
4. **Bật Authentication → Google Sign-In** (không bật Anonymous).
5. **Tạo Named Firestore Database** với ID chính xác (ví dụ `fox-prod-db`) tại vùng `asia-southeast1` (Singapore) ở chế độ Production.
6. **Bật Firebase Cloud Messaging (FCM API V1)**.

---

## 3. Lệnh Triển khai Backend Production (Rules, Indexes & Cloud Functions v2)

Sử dụng script bảo mật tích hợp sẵn trong thư mục `production/`:

```powershell
$PROJ_ID = "<YOUR_PRODUCTION_PROJECT_ID>"
$DB_ID = "fox-prod-db"
$ADMIN_EMAIL = "admin@fox-express.vn"

# 1. Kiểm tra Dry-Run (Không ghi dữ liệu)
.\production\deploy-production.ps1 -ProductionProjectId $PROJ_ID -ProductionDatabaseId $DB_ID -Target rules

# 2. Deploy Firestore Rules & Indexes (Không yêu cầu gói Blaze)
.\production\deploy-production.ps1 -ProductionProjectId $PROJ_ID -ProductionDatabaseId $DB_ID -Target rules -ConfirmDeploy

# 3. Deploy Cloud Functions v2 (Yêu cầu project đã bật gói Blaze và truyền -BlazeApproved)
.\production\deploy-production.ps1 -ProductionProjectId $PROJ_ID -ProductionDatabaseId $DB_ID -Target all -ConfirmDeploy -BlazeApproved
```

---

## 4. Ký và Build Release APK / AAB trên Windows 10

Bản Release thương mại **không bao giờ** dùng `debug.keystore`. Chủ dự án cần cung cấp tệp khóa ký Release (`release-key.jks`) được lưu trữ an toàn ngoài mã nguồn.

Build Release APK hoặc AAB bằng Gradle Wrapper:
```powershell
# Ví dụ build Release APK với khóa ký doanh nghiệp:
.\gradlew assembleRelease `
  -PfoxEnv=PRODUCTION `
  -PfoxProductionProjectId="<YOUR_PRODUCTION_PROJECT_ID>" `
  -PfoxProductionDbId="fox-prod-db" `
  -PKEYSTORE_PATH="C:\secure-keys\release-key.jks" `
  -PSTORE_PASSWORD="<store_password>" `
  -PKEY_PASSWORD="<key_password>"

# Hoặc build Android App Bundle (AAB) cho Google Play:
.\gradlew bundleRelease `
  -PfoxEnv=PRODUCTION `
  -PfoxProductionProjectId="<YOUR_PRODUCTION_PROJECT_ID>" `
  -PfoxProductionDbId="fox-prod-db"
```

Sản phẩm đầu ra được đặt tại:
- APK: `app\build\outputs\apk\release\app-release.apk`
- AAB: `app\build\outputs\bundle\release\app-release.aab`

---

## 5. Đóng gói Source ZIP Nghiệm thu (`FOX_PRODUCTION_V1.0_SOURCE.zip`)

Để đóng gói toàn bộ mã nguồn sạch (loại trừ `node_modules`, `build`, keystore cá nhân, `local.properties`), chạy script đóng gói:
```powershell
.\scripts\package-source-zip.ps1
```
Tệp nén `FOX_PRODUCTION_V1.0_SOURCE.zip` và tệp mã kiểm tra `SHA256SUMS.txt` sẽ được tạo tại thư mục gốc.
