# Hướng dẫn Thiết lập & Vận hành Môi trường Production V1.0 (FOX)

## 1. Nguyên tắc cốt lõi
- Môi trường thương mại **Production V1.0** yêu cầu cách ly hoàn toàn với các dự án thử nghiệm (DEMO / Staging).
- Sử dụng cấu hình `BuildConfig.FOX_ENV = "PRODUCTION"`.
- Không sử dụng dữ liệu giả lập (mock/demo fallback), không chứa ví DEMO hay seed data thử nghiệm trong bản phát hành chính thức.

## 2. Quy trình thiết lập Firebase Console (Production)
1. Tạo Firebase Project mới dành riêng cho thương mại (ví dụ `fox-commercial-prod`).
2. Thêm Android App với package `com.aistudio.fox.superapp` và cấu hình **SHA-1 / SHA-256 chính thức từ khóa ký Release** của doanh nghiệp.
3. Bật **Authentication → Google Sign-In**. Tải tệp `google-services.json` đúng project và lưu trữ bảo mật.
4. Tạo Named Firestore Database với ID cố định (ví dụ `fox-prod-db`) tại vùng `asia-southeast1` ở chế độ Production.
5. Bật FCM API v1.

## 3. Triển khai Backend & Ký App
- Sử dụng script `production\deploy-production.ps1` để deploy Rules, Indexes và Functions v2.
- Ký Release APK/AAB bằng tệp khóa doanh nghiệp (`release-key.jks`) thông qua Gradle properties.
