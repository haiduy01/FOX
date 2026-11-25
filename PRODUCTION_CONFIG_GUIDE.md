# FOX PRODUCTION V1.0 — HƯỚNG DẪN KÝ KEYSTORE, GOOGLE SIGN-IN, FCM VÀ MAPS

## 1. Tạo Release Keystore (Khóa ký chính thức)
Để phát hành ứng dụng thương mại lên Google Play, bạn cần tạo khóa ký Release (không dùng keystore debug):
```powershell
keytool -genkey -v -keystore fox-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias foxrelease
```
Lưu lại `fox-release-key.jks`, mật khẩu keystore và mật khẩu alias ở nơi an toàn ngoài mã nguồn.

## 2. Lấy SHA-1 và SHA-256 cho Firebase Console
Sau khi có tệp `fox-release-key.jks`, lấy dấu vân tay để đăng ký trong Firebase Project Production:
```powershell
keytool -list -v -keystore fox-release-key.jks -alias foxrelease
```
Copy mã **SHA-1** và **SHA-256** dán vào **Firebase Console -> Project settings -> Your apps -> Android app -> Add fingerprint**.

## 3. Cấu hình Google Sign-In & FCM
1. Tải tệp `google-services.json` mới nhất từ Firebase Console Production và đặt vào `app\google-services.json`.
2. Bật **Authentication -> Google Sign-In**.
3. Xác nhận **Firebase Cloud Messaging API (V1)** đã được kích hoạt.

## 4. Cấu hình Bản đồ (Google Maps)
Nếu ứng dụng sử dụng bản đồ thật, lấy `MAPS_API_KEY` từ Google Cloud Console (bật Maps SDK for Android và Places/Routes API nếu cần), sau đó đưa vào biến môi trường `.env` hoặc cấu hình chuỗi trong `gradle.properties`:
```properties
MAPS_API_KEY=AIzaSyYourProductionRealMapsApiKeyHere
```
*(Nếu chưa có API Key, hệ thống tự động chuyển sang chế độ Bản đồ DEMO an toàn).*
