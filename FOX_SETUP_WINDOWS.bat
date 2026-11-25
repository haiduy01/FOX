@echo off
chcp 65001 >nul
echo ========================================================
echo   FOX PRODUCTION V1.0 — MÔI TRƯỜNG WINDOWS 10 CHECKER
echo ========================================================
echo.

echo [1/4] Kiểm tra Node.js...
node -v
if %errorlevel% neq 0 (
  echo [CẢNH BÁO] Node.js chưa được cài đặt hoặc chưa thêm vào PATH. Vui lòng cài đặt Node 22+.
) else (
  echo [OK] Node.js đã sẵn sàng.
)
echo.

echo [2/4] Kiểm tra Java (JDK 17+)...
java -version
if %errorlevel% neq 0 (
  echo [CẢNH BÁO] Java JDK chưa được cài đặt. Cần cài đặt JDK 17 cho Android Build.
) else (
  echo [OK] Java đã sẵn sàng.
)
echo.

echo [3/4] Kiểm tra Firebase CLI...
firebase --version
if %errorlevel% neq 0 (
  echo [HƯỚNG DẪN] Cài đặt Firebase CLI bằng lệnh: npm install -g firebase-tools
) else (
  echo [OK] Firebase CLI đã sẵn sàng.
)
echo.

echo [4/4] Kiểm tra Gradle Wrapper...
if exist gradlew.bat (
  echo [OK] gradlew.bat tồn tại.
) else (
  echo [CẢNH BÁO] Không tìm thấy gradlew.bat tại thư mục hiện tại.
)

echo.
echo ========================================================
echo HƯỚNG DẪN CẤU HÌNH NHANH TRÊN WINDOWS 10:
echo 1. Chạy lệnh: npm --prefix functions install
echo 2. Đặt google-services.json vào thư mục app\google-services.json
echo 3. Cấu hình khóa ký Release và chạy FOX_BUILD_RELEASE.ps1
echo ========================================================
pause
