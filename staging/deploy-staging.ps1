<#
.SYNOPSIS
  FOX Full Feature Pilot — Safe Isolated Firebase Staging Deploy Script (Windows 10 PowerShell)
.DESCRIPTION
  - Requires explicit -StagingProjectId (never hardcoded).
  - Hard-aborts (exit 3) on live project `gen-lang-client-0957111252` or live DB `ai-studio-android-1a796de6-79f0-4283-8d0b-703e709b27e4`.
  - Verifies target Firestore database exists before deploying.
  - Backs up firestore.rules automatically to backups\ before deploy.
  - NEVER enables Billing/Blaze and NEVER deletes data.
  - Defaults to DRY-RUN unless -ConfirmDeploy is passed.
  - Deploying functions (`-Target functions` or `-Target all`) requires `-BlazeApproved`.
#>

param(
  [Parameter(Mandatory = $true)]
  [string]$StagingProjectId,

  [string]$StagingDatabaseId = "fox-staging-db",

  [string]$StagingAdminEmail = "hahaiduy28@gmail.com",

  [ValidateSet("rules", "functions", "all")]
  [string]$Target = "rules",

  [switch]$ConfirmDeploy,

  [switch]$BlazeApproved
)

$ErrorActionPreference = "Stop"

$LiveProjectId = "gen-lang-client-0957111252"
$LiveDatabaseId = "ai-studio-android-1a796de6-79f0-4283-8d0b-703e709b27e4"

$CleanProject = $StagingProjectId.Trim()
$CleanDb = $StagingDatabaseId.Trim()

if ([string]::IsNullOrWhiteSpace($CleanProject) -or $CleanProject.ToLower().StartsWith("your-")) {
  Write-Error "[ABORT] Provide a real isolated -StagingProjectId (not placeholder)."
  exit 2
}
if ($CleanProject -eq $LiveProjectId) {
  Write-Error "[ABORT][GUARDRAIL] Refusing to deploy to LIVE Firebase project ($LiveProjectId)."
  exit 3
}
if ([string]::IsNullOrWhiteSpace($CleanDb) -or $CleanDb -eq $LiveDatabaseId) {
  Write-Error "[ABORT][GUARDRAIL] Refusing to target LIVE Firestore database ($LiveDatabaseId)."
  exit 3
}
if (($Target -eq "functions" -or $Target -eq "all") -and (-not $BlazeApproved)) {
  Write-Error "[ABORT][BILLING GUARDRAIL] Deploying Cloud Functions v2 requires explicit owner Blaze approval (-BlazeApproved). No billing changes were made."
  exit 4
}

$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$BackupFile = "backups\firestore.rules.$Timestamp.bak"

Write-Host "=== FOX STAGING DEPLOYMENT PLAN (Windows 10) ===" -ForegroundColor Cyan
Write-Host "Target Project  : $CleanProject"
Write-Host "Target Database : $CleanDb"
Write-Host "Deploy Target   : $Target"
Write-Host "Region          : asia-southeast1"
Write-Host "Rules Backup    : $BackupFile"
Write-Host "Confirm Deploy  : $($ConfirmDeploy.IsPresent) (False = DRY-RUN)"
Write-Host "Blaze Approved  : $($BlazeApproved.IsPresent)"

if (-not $ConfirmDeploy) {
  Write-Host "[DRY-RUN] No remote Firebase changes performed." -ForegroundColor Yellow
  exit 0
}

if (-not (Test-Path "backups")) { New-Item -ItemType Directory -Path "backups" | Out-Null }
Copy-Item -Path "firestore.rules" -Destination $BackupFile -Force
Write-Host "[OK] Backed up firestore.rules -> $BackupFile" -ForegroundColor Green

$DbList = (& firebase firestore:databases:list --project $CleanProject 2>&1) | Out-String
if ($LASTEXITCODE -ne 0 -or ($DbList -notmatch [regex]::Escape($CleanDb))) {
  Write-Error "[ABORT] Target Firestore database '$CleanDb' not found in project '$CleanProject'. Create it in Firebase Console first (asia-southeast1)."
  exit 5
}
Write-Host "[OK] Verified target database '$CleanDb' exists." -ForegroundColor Green

$TempConfig = Join-Path $env:TEMP "fox-firebase-staging-$Timestamp.json"
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
      "codebase": "fox-pilot-staging",
      "ignore": ["node_modules", ".git", "firebase-debug.log", "firebase-debug.*.log", "*.local", "test/**"],
      "predeploy": ["npm --prefix `"`$RESOURCE_DIR`" run build"]
    }
  ]
}
"@

try {
  Set-Content -Path $TempConfig -Value $ConfigJson -Encoding UTF8

  if ($Target -eq "rules" -or $Target -eq "all") {
    & firebase deploy --only "firestore:rules,firestore:indexes" --project $CleanProject --config $TempConfig
    if ($LASTEXITCODE -ne 0) { throw "Firebase rules/indexes deploy failed." }
  }

  if ($Target -eq "functions" -or $Target -eq "all") {
    $EnvFile = "functions\.env.$CleanProject"
    Set-Content -Path $EnvFile -Value "FOX_FIRESTORE_DATABASE_ID=$CleanDb`nFOX_ADMIN_EMAIL=$StagingAdminEmail`n" -Encoding UTF8
    & npm --prefix functions run build
    if ($LASTEXITCODE -ne 0) { throw "Cloud Functions build failed." }
    & firebase deploy --only functions --project $CleanProject --config $TempConfig
    if ($LASTEXITCODE -ne 0) { throw "Firebase functions deploy failed." }
  }

  Write-Host "[PASS] Staging deployment completed safely for project '$CleanProject' (backup: $BackupFile)." -ForegroundColor Green
} finally {
  if (Test-Path $TempConfig) { Remove-Item -Path $TempConfig -Force -ErrorAction SilentlyContinue }
}
