<#
.SYNOPSIS
  FOX Production V1.0 — Firebase Production Deploy Script (Windows 10 PowerShell)
.DESCRIPTION
  Deploys Firestore Rules, Indexes, and Cloud Functions v2 with strict cost guardrails and project verification.
#>

param(
  [Parameter(Mandatory = $true)]
  [string]$ProductionProjectId,

  [string]$ProductionDatabaseId = "fox-prod-db",

  [string]$ProductionAdminEmail = "admin@fox-express.vn",

  [ValidateSet("rules", "functions", "all")]
  [string]$Target = "rules",

  [switch]$ConfirmDeploy,

  [switch]$BlazeApproved
)

$ErrorActionPreference = "Stop"

$CleanProject = $ProductionProjectId.Trim()
$CleanDb = $ProductionDatabaseId.Trim()

if ([string]::IsNullOrWhiteSpace($CleanProject) -or $CleanProject -eq "gen-lang-client-0957111252") {
  Write-Error "[ABORT] Cần cung cấp ProductionProjectId thực tế."
  exit 2
}

if (($Target -eq "functions" -or $Target -eq "all") -and (-not $BlazeApproved)) {
  Write-Error "[ABORT][BILLING GUARDRAIL] Triển khai Cloud Functions v2 bắt buộc phải có cờ xác nhận Blaze (-BlazeApproved). Không có thay đổi nào được thực hiện."
  exit 4
}

Write-Host "=== TRIỂN KHAI FIREBASE PRODUCTION (FOX V1.0) ===" -ForegroundColor Cyan
Write-Host "Project ID : $CleanProject"
Write-Host "Database ID: $CleanDb"
Write-Host "Target     : $Target"
Write-Host "Confirm    : $($ConfirmDeploy.IsPresent)"

if (-not $ConfirmDeploy) {
  Write-Host "[DRY-RUN] Chưa thực hiện thay đổi từ xa. Thêm -ConfirmDeploy để thực thi." -ForegroundColor Yellow
  exit 0
}

if (-not (Test-Path "backups")) { New-Item -ItemType Directory -Path "backups" | Out-Null }
$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$BackupFile = "backups\firestore.production.$Timestamp.bak"
Copy-Item -Path "firestore.rules" -Destination $BackupFile -Force

$TempConfig = Join-Path $env:TEMP "fox-prod-deploy-$Timestamp.json"
$ConfigJson = @"
{
  "firestore": {
    "database": "$CleanDb",
    "rules": "firestore.rules",
    "indexes": "firestore.indexes.json"
  },
  "functions": [
    {
      "source": "functions",
      "codebase": "fox-production",
      "ignore": ["node_modules", ".git", "firebase-debug.log", "*.local"],
      "predeploy": ["npm --prefix `"`$RESOURCE_DIR`" run build"]
    }
  ]
}
"@

try {
  Set-Content -Path $TempConfig -Value $ConfigJson -Encoding UTF8

  if ($Target -eq "rules" -or $Target -eq "all") {
    & firebase deploy --only "firestore:rules,firestore:indexes" --project $CleanProject --config $TempConfig
    if ($LASTEXITCODE -ne 0) { throw "Deploy rules thất bại." }
  }

  if ($Target -eq "functions" -or $Target -eq "all") {
    $EnvFile = "functions\.env.$CleanProject"
    Set-Content -Path $EnvFile -Value "FOX_FIRESTORE_DATABASE_ID=$CleanDb`nFOX_ADMIN_EMAIL=$ProductionAdminEmail`n" -Encoding UTF8
    & npm --prefix functions run build
    & firebase deploy --only functions --project $CleanProject --config $TempConfig
    if ($LASTEXITCODE -ne 0) { throw "Deploy functions thất bại." }
  }

  Write-Host "[PASS] Triển khai Production thành công!" -ForegroundColor Green
} finally {
  if (Test-Path $TempConfig) { Remove-Item -Path $TempConfig -Force -ErrorAction SilentlyContinue }
}
