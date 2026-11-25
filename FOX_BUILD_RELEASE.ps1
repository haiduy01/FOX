<#
.SYNOPSIS
  FOX Production V1.0 — Safe Release APK & AAB Builder (Windows 10 PowerShell)
.DESCRIPTION
  Builds both APK and AAB for Production V1.0 with secure interactive or parameterized keystore credentials.
#>

param(
  [Parameter(Mandatory = $true)]
  [string]$ProductionProjectId,

  [string]$ProductionDatabaseId = "fox-prod-db",

  [string]$KeystorePath = "",

  [string]$StorePassword = "",

  [string]$KeyPassword = ""
)

$ErrorActionPreference = "Stop"

$CleanProject = $ProductionProjectId.Trim()
$CleanDb = $ProductionDatabaseId.Trim()

if ([string]::IsNullOrWhiteSpace($CleanProject) -or $CleanProject -eq "gen-lang-client-0957111252") {
  Write-Error "[ABORT] Cần cung cấp ProductionProjectId hợp lệ (không dùng project test cũ)."
  exit 2
}

if ([string]::IsNullOrWhiteSpace($KeystorePath)) {
  $KeystorePath = Read-Host "Nhập đường dẫn tuyệt đối tới tệp Keystore Release (.jks)"
}
if (-not (Test-Path $KeystorePath)) {
  Write-Error "[ABORT] Không tìm thấy tệp keystore tại: $KeystorePath"
  exit 3
}

if ([string]::IsNullOrWhiteSpace($StorePassword)) {
  $StorePassword = Read-Host -AsSecureString "Nhập Store Password"
  $BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($StorePassword)
  $StorePassword = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
}

if ([string]::IsNullOrWhiteSpace($KeyPassword)) {
  $KeyPassword = Read-Host -AsSecureString "Nhập Key Password"
  $BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($KeyPassword)
  $KeyPassword = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
}

Write-Host "=== BẮT ĐẦU BUILD RELEASE APK & AAB (FOX V1.0) ===" -ForegroundColor Cyan
Write-Host "Project ID : $CleanProject"
Write-Host "Database ID: $CleanDb"
Write-Host "Keystore   : $KeystorePath"

$GradleCmd = if (Test-Path ".\gradlew.bat") { ".\gradlew.bat" } else { "gradle" }

& $GradleCmd ":app:assembleRelease" ":app:bundleRelease" `
  "-PfoxEnv=PRODUCTION" `
  "-PfoxProductionProjectId=$CleanProject" `
  "-PfoxProductionDbId=$CleanDb" `
  "-PKEYSTORE_PATH=$KeystorePath" `
  "-PSTORE_PASSWORD=$StorePassword" `
  "-PKEY_PASSWORD=$KeyPassword"

if ($LASTEXITCODE -ne 0) {
  Write-Error "[FAIL] Quá trình build Gradle Release thất bại."
  exit $LASTEXITCODE
}

$ApkOut = "app\build\outputs\apk\release\app-release.apk"
$AabOut = "app\build\outputs\bundle\release\app-release.aab"

Write-Host ""
Write-Host "[PASS] Đã build thành công bản Release thương mại!" -ForegroundColor Green
if (Test-Path $ApkOut) { Write-Host " - APK: $ApkOut" }
if (Test-Path $AabOut) { Write-Host " - AAB: $AabOut" }
