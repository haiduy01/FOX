<#
.SYNOPSIS
  Build isolated Android STAGING APK on Windows 10 without permanently touching app\google-services.json.
#>

param(
  [Parameter(Mandatory = $true)]
  [string]$StagingProjectId,

  [string]$StagingDatabaseId = "fox-staging-db",

  [string]$StagingGoogleServicesPath = "staging\google-services.staging.json",

  [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$LiveProjectId = "gen-lang-client-0957111252"
$LiveDatabaseId = "ai-studio-android-1a796de6-79f0-4283-8d0b-703e709b27e4"
$ExpectedPackage = "com.aistudio.fox.superapp"

$CleanProject = $StagingProjectId.Trim()
$CleanDb = $StagingDatabaseId.Trim()

if ([string]::IsNullOrWhiteSpace($CleanProject) -or $CleanProject.ToLower().StartsWith("your-")) {
  Write-Error "[ABORT] Provide a real isolated -StagingProjectId."
  exit 2
}
if ($CleanProject -eq $LiveProjectId) {
  Write-Error "[ABORT][GUARDRAIL] Refusing to build STAGING APK against LIVE project ($LiveProjectId)."
  exit 3
}
if ([string]::IsNullOrWhiteSpace($CleanDb) -or $CleanDb -eq $LiveDatabaseId) {
  Write-Error "[ABORT][GUARDRAIL] Refusing to build STAGING APK against LIVE database ($LiveDatabaseId)."
  exit 3
}
if (-not (Test-Path $StagingGoogleServicesPath)) {
  Write-Error "[ABORT] Missing '$StagingGoogleServicesPath'. Download google-services.json from your Staging Firebase Console and save it there."
  exit 5
}

$GsJson = Get-Content -Path $StagingGoogleServicesPath -Raw | ConvertFrom-Json
$GsProjectId = $GsJson.project_info.project_id
if ($GsProjectId -eq $LiveProjectId) {
  Write-Error "[ABORT][GUARDRAIL] '$StagingGoogleServicesPath' belongs to LIVE project ($LiveProjectId)!"
  exit 3
}
if ($GsProjectId -ne $CleanProject) {
  Write-Error "[ABORT] Mismatch: '$StagingGoogleServicesPath' has project_id='$GsProjectId', expected '$CleanProject'."
  exit 6
}
$PkgMatches = $GsJson.client | Where-Object { $_.client_info.android_client_info.package_name -eq $ExpectedPackage }
if (-not $PkgMatches) {
  Write-Error "[ABORT] '$StagingGoogleServicesPath' does not contain Android package '$ExpectedPackage'."
  exit 6
}

Write-Host "=== FOX ANDROID STAGING APK BUILD (Windows 10) ===" -ForegroundColor Cyan
Write-Host "Staging Project : $CleanProject"
Write-Host "Staging DB ID   : $CleanDb"
Write-Host "Package Name    : $ExpectedPackage"
Write-Host "Google Services : $StagingGoogleServicesPath (VERIFIED)"

if ($DryRun) {
  Write-Host "[DRY-RUN] Validation PASS. Re-run without -DryRun to build staging\out\fox-staging-$CleanProject-debug.apk." -ForegroundColor Yellow
  exit 0
}

$OrigGsPath = "app\google-services.json"
$BackupGsPath = Join-Path $env:TEMP ("fox-orig-google-services-" + (Get-Date -Format "yyyyMMdd_HHmmss") + ".json")
$HadOrig = Test-Path $OrigGsPath

try {
  if ($HadOrig) { Copy-Item -Path $OrigGsPath -Destination $BackupGsPath -Force }
  Copy-Item -Path $StagingGoogleServicesPath -Destination $OrigGsPath -Force
  Write-Host "[OK] Temporarily switched app\google-services.json -> staging config." -ForegroundColor Green

  $GradleCmd = if (Test-Path ".\gradlew.bat") { ".\gradlew.bat" } else { "gradle" }
  & $GradleCmd ":app:assembleDebug" "-PfoxEnv=STAGING" "-PfoxStagingProjectId=$CleanProject" "-PfoxStagingDbId=$CleanDb"
  if ($LASTEXITCODE -ne 0) { throw "Gradle assembleDebug failed with exit code $LASTEXITCODE" }

  $BuiltApk = "app\build\outputs\apk\debug\app-debug.apk"
  if (-not (Test-Path $BuiltApk)) { throw "Expected APK not found at $BuiltApk" }

  if (-not (Test-Path "staging\out")) { New-Item -ItemType Directory -Path "staging\out" | Out-Null }
  $OutApk = "staging\out\fox-staging-$CleanProject-debug.apk"
  Copy-Item -Path $BuiltApk -Destination $OutApk -Force
  Write-Host "[PASS] Built isolated Staging APK -> $OutApk" -ForegroundColor Green
} finally {
  if ($HadOrig -and (Test-Path $BackupGsPath)) {
    Copy-Item -Path $BackupGsPath -Destination $OrigGsPath -Force
    Remove-Item -Path $BackupGsPath -Force -ErrorAction SilentlyContinue
    Write-Host "[OK] Restored original app\google-services.json." -ForegroundColor Green
  }
}
