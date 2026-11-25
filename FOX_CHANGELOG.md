# FOX – Chạm là tới! | Nhật ký Thay đổi (Changelog)

## V1.0.0 (Production Commercial Release)
- Hoàn thiện toàn bộ hệ thống siêu ứng dụng (Super App) đa dịch vụ: Đặt xe (RIDE), Giao hàng (DELIVERY), Đặt đồ ăn (FOOD), Mua sắm (SHOPPING).
- Xây dựng kiến trúc Cloud Backend với Firestore, 12 Cloud Functions v2 (TypeScript), sổ cái tài chính số nguyên VND, idempotency chống trùng giao dịch, cơ chế nguyên tử trừ tồn kho và phân quyền 4 vai trò (customer, driver, merchant, admin).
- Tách biệt hoàn toàn môi trường DEMO / Staging khỏi Production thông qua `FoxEnvironmentConfig.kt` và các script triển khai có guardrail chống trỏ nhầm dự án.
- Bổ sung bộ công cụ đóng gói source ZIP, build Release APK/AAB và hướng dẫn vận hành chuẩn thương mại trên Windows 10.
