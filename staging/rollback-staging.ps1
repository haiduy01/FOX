<#
.SYNOPSIS
  FOX Full Feature Pilot — Isolated Staging Rollback Script (Windows 10 PowerShell)
#>

param(
  [Parameter(Mandatory = $true)]
  [string]$StagingProjectId,

  [string]$StagingDatabaseId = "fox-staging-db",

  [string]$BackupRulesPath = "backups\firestore.rules.bak",

  [switch]$ExecuteRollback
)

$ErrorActionPreference = "Stop"

$LiveProjectId = "gen-lang-client-0957111252"
$LiveDatabaseId = "ai-studio-android-1a796de6-79f0-4283-8d0b-703e709b27e4"

$CleanProject = $StagingProjectId.Trim()
$CleanDb = $StagingDatabaseId.Trim()

if ([string]::IsNullOrWhiteSpace($CleanProject) -or $CleanProject.ToLower().StartsWith("your-")) {
  Write-Error "[ABORT] Provide a valid -StagingProjectId."
  exit 2
}
if ($CleanProject -eq $LiveProjectId -or $CleanDb -eq $LiveDatabaseId) {
  Write-Error "[ABORT][GUARDRAIL] Refusing to run rollback against LIVE Firebase project/database ($LiveProjectId / $LiveDatabaseId)."
  exit 3
}

Write-Host "=== FOX STAGING ROLLBACK PLAN (Windows 10) ===" -ForegroundColor Cyan
Write-Host "Staging Project : $CleanProject"
Write-Host "Staging DB ID   : $CleanDb"
Write-Host "Rules Backup    : $BackupRulesPath"
Write-Host "ExecuteRollback : $($ExecuteRollback.IsPresent) (False = DRY-RUN)"

if (-not (Test-Path $BackupRulesPath)) {
  Write-Warning "Backup file '$BackupRulesPath' not found. Pick an existing backups\firestore.rules.*.bak snapshot."
}

if (-not $ExecuteRollback) {
  Write-Host "[DRY-RUN] No remote changes performed. Re-run with -ExecuteRollback to restore $BackupRulesPath on STAGING ONLY." -ForegroundColor Yellow
  exit 0
}

if (-not (Test-Path $BackupRulesPath)) {
  Write-Error "[ABORT] Cannot execute rollback because '$BackupRulesPath' does not exist."
  exit 4
}

Copy-Item -Path $BackupRulesPath -Destination "firestore.rules" -Force
& .\staging\deploy-staging.ps1 -StagingProjectId $CleanProject -StagingDatabaseId $CleanDb -Target rules -ConfirmDeploy
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "[PASS] Staging Firestore Rules rolled back safely from $BackupRulesPath." -ForegroundColor Green
