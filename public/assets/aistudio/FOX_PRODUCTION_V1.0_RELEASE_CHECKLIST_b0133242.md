# FOX – Chạm là tới! | Production V1.0

## Trạng thái và phạm vi
Tài liệu chuyển đổi từ FOX_STAGING_DEPLOY_CHECKLIST.md sang bản thương mại, dành cho môi trường Windows 10. Đây là yêu cầu triển khai và nghiệm thu, không phải báo cáo đã triển khai. Chưa có mã nguồn, Firebase credentials, cấu hình thanh toán hoặc khóa ký Release để xác minh/build tại đây. Kết quả 21/21 Android và 40/40 Firebase là số liệu được chủ dự án cung cấp cho bản trước; phải kiểm thử lại sau khi sửa.

## 1. Yêu cầu thực hiện trực tiếp trong AI Studio / môi trường có repository
- Sửa trên repository FOX hiện tại. Giữ nhận diện FOX, tiếng Việt có dấu, bốn dịch vụ RIDE, DELIVERY, FOOD, SHOPPING và các vai trò customer, driver, merchant, admin. Giữ applicationId `com.aistudio.fox.superapp` trừ khi chủ dự án duyệt thay đổi.
- Rà soát toàn bộ app, Gradle, FoxEnvironmentConfig.kt, Functions, Rules, Indexes và scripts. Tách production khỏi staging/emulator bằng cấu hình build, kiểm tra project/database ID theo allowlist và fail closed khi cấu hình sai.
- Không đưa ví DEMO, dữ liệu seed, tài khoản mẫu, danh mục Bếp Cáo Quán/FOX Mart Express, mã FOX10 mẫu, fake GPS, fake repository, nút DEMO và cơ chế tự tạo thành công giả vào bản Release. Không xóa dữ liệu cloud đang có. Giữ fixture, emulator, test và staging độc lập, không được gọi từ production.
- Room chỉ làm cache/draft hợp lệ theo người dùng; khi offline không giả xác nhận đơn, thanh toán hay nhận đơn. Xóa cache phiên khi đổi tài khoản, đồng bộ lại khi online.
- Các chức năng thiếu backend/cấu hình thật phải khóa giao dịch rõ ràng; không hiển thị thành công giả, không hardcode khóa bí mật.

## 2. Firebase và bảo vệ dữ liệu
- Không triển khai lên project `gen-lang-client-0957111252` hoặc database `ai-studio-android-1a796de6-79f0-4283-8d0b-703e709b27e4`. Chủ dự án chọn project Production riêng và database ID chính xác; không mặc định ID chưa tồn tại.
- Cấu hình Google Sign-In, SHA-1/SHA-256 của khóa Release, tải google-services.json đúng project; không dùng Anonymous nếu không được phê duyệt. Phân quyền admin bằng quy trình bootstrap server có kiểm soát, không tự cấp quyền từ client.
- Tạo Firestore ở chế độ production, vùng asia-southeast1 nếu phù hợp kế hoạch vận hành; cấu hình rõ named database cho SDK, Functions, Rules và Indexes. Sao lưu và dry-run trước khi ghi.
- Rà soát và triển khai 12 Cloud Functions v2 được nêu trong checklist gốc (xác minh tên, số lượng và chức năng thực tế từ source). FCM API V1; token theo phiên, không chứa PII trong push. Storage Rules nếu dùng Storage.
- Không bật Blaze, Maps/Routes trả phí hay dịch vụ thanh toán khi chưa có phê duyệt cụ thể. Budget alert không phải giới hạn cứng chi phí. Dùng secrets manager/biến môi trường thích hợp, không đóng gói Service Account JSON, mật khẩu hoặc keystore riêng.
- Thanh toán: không đổi nhãn DEMO thành tiền thật. Chỉ bật khi tích hợp nhà cung cấp hợp lệ, webhook xác thực, idempotency, ledger số nguyên VND, hoàn tiền/đối soát/hoa hồng server-side, kiểm soát pháp lý và quyền truy cập; có thể triển khai bản chưa bật thanh toán online nhưng phải ghi rõ phạm vi, không gọi là đã nghiệm thu đầy đủ.

## 3. Kiểm thử lại và điều kiện phát hành
- Chạy lại 21 Android Unit & Robolectric Tests, 40 Firebase Emulator Tests và các test mới; ghi số thực tế, commit, lệnh, exit code. Không bỏ test để đạt PASS.
- Kiểm thử 2–4 thiết bị thật: đăng nhập Google, phục hồi phiên/đăng xuất, 4 dịch vụ, tài xế nhận đơn độc quyền, merchant xác nhận/tồn kho, admin duyệt đối tác, FCM foreground/background, định vị/định tuyến, mạng lỗi và khôi phục, phân quyền, dữ liệu tài chính, không lẫn phiên. Kiểm thử các trạng thái tài chính bằng sandbox trước; chỉ giao dịch thật khi chủ dự án phê duyệt hạn mức và tích hợp hợp lệ.
- Kiểm thử release artifact không chứa demo, không kết nối emulator, không kết nối project cũ; xác minh Rules chống sửa vai trò/số dư trực tiếp, chống nhận trùng đơn và bán vượt tồn kho.
- Nghiệm thu trên staging cô lập trước, sau đó triển khai production đúng đích; không chạy seed-demo trên production. Nếu thiếu điều kiện, chỉ đóng gói Release Candidate và ghi BLOCKED cho phần chưa đạt.

## 4. Chuẩn bị Windows 10
Tại thư mục repository (ví dụ C:\fox-android), kiểm tra môi trường:

```powershell
node -v
npm -v
java -version
firebase --version
Test-Path .\gradlew.bat
Test-Path .\gradle\wrapper\gradle-wrapper.properties
```

Theo checklist gốc: Node 22+, Java 17+; đối chiếu phiên bản thực tế với Gradle/AGP và runtime Functions của repository. Cài dependencies từ lockfile nếu có, build Functions, chạy test theo scripts thực tế. Dùng Gradle Wrapper và Android SDK phù hợp. Không sử dụng debug.keystore.base64 để ký Release. Tạo hoặc dùng khóa Release do chủ dự án quản lý ngoài repository; không chia sẻ khóa hoặc mật khẩu. Kiểm tra fingerprint của app signing key và upload key theo phương thức phân phối.

AI Studio phải tạo và kiểm thử scripts production riêng có dry-run, explicit project/database, xác nhận triển khai và guardrails; không tái sử dụng script staging bằng cách xóa guardrail. Cung cấp lệnh thực tế theo cấu trúc repo, không bịa tên task/flavor hoặc giả định đã có script.

## 5. Đóng gói đầu ra
- FOX_PRODUCTION_V1.0_SOURCE.zip — source Android + Gradle Wrapper + resources + Firebase Functions + Rules/Indexes + cấu hình mẫu + scripts deploy/build/rollback + test.
- FOX_PRODUCTION_V1.0_RELEASE.apk — APK ký Release nếu có khóa và build thành công.
- FOX_PRODUCTION_V1.0_RELEASE.aab — AAB ký phù hợp phương thức phát hành.
- FOX_PRODUCTION_DEPLOY_GUIDE.md — lệnh Windows 10 đúng repo; các bước Firebase Console, signing, Maps, thanh toán, ngân sách, xác nhận đích.
- FOX_PRODUCTION_TEST_REPORT.md — kết quả thực tế, các mục PASS/FAIL/BLOCKED, phiên bản và commit.
- FOX_CHANGELOG.md — danh sách thay đổi, file, chức năng và tương thích dữ liệu.
- FOX_ROLLBACK_GUIDE.md — phục hồi Rules/Functions theo bản sao lưu, rollback code có kiểm soát; không ghi đè dữ liệu giao dịch mới bằng snapshot cũ.
- SHA256SUMS.txt — SHA-256 của các gói đã tạo, kiểm tra ZIP giải nén được và không thiếu tệp bắt buộc.

Loại trừ secrets, keystore cá nhân, local.properties chứa đường dẫn riêng, build cache, node_modules và dữ liệu khách hàng khỏi ZIP. Giữ các file cấu hình mẫu với hướng dẫn tạo file thật. Không tạo APK/AAB giả, file rỗng hay đổi tên APK debug thành Release.

## 6. Tiêu chí chốt
Chỉ báo Production Ready khi backend triển khai đúng đích, bản ký Release chạy được trên thiết bị thật, nghiệm thu bảo mật và các chức năng công bố đều đạt; nếu thiếu Billing, Maps, khóa ký, tài khoản hoặc thanh toán, ghi rõ phần nào chưa thể bật. Báo cáo chính xác tệp đã tạo, lệnh đã chạy và mục chưa hoàn thành. Không tự ý phát hành công khai hoặc dùng tiền thật ngoài phạm vi đã duyệt.
