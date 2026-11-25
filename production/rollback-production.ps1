<#
.SYNOPSIS
  FOX Production V1.0 — Rollback Script (Windows 10 PowerShell)
.DESCRIPTION
  Safely restores production firestore.rules from backups\ on production project only.
#>

param(
  [Parameter(Mandatory = $true)]
  [string]$ProductionProjectId,

  [string]$ProductionDatabaseId = "fox-prod-db",

  [string]$BackupRulesPath = "backups\firestore.production.rules.bak",

  [switch]$ExecuteRollback
)

$ErrorActionPreference = "Stop"

$LegacyTestProjectId = "gen-lang-client-0957111252"
$CleanProject = $ProductionProjectId.Trim()
$CleanDb = $ProductionDatabaseId.Trim()

if ($CleanProject -eq $LegacyTestProjectId) {
  Write-Error "[ABORT] Refusing rollback against legacy project."
  exit 3
}

Write-Host "=== FOX PRODUCTION V1.0 ROLLBACK PLAN ===" -ForegroundColor Cyan
Write-Host "Production Project : $CleanProject"
Write-Host "Production DB ID   : $CleanDb"
Write-Host "Rules Backup       : $BackupRulesPath"
Write-Host "ExecuteRollback    : $($ExecuteRollback.IsPresent)"

if (-not $ExecuteRollback) {
  Write-Host "[DRY-RUN] Re-run with -ExecuteRollback to restore rules on production." -ForegroundColor Yellow
  exit 0
}

if (-not (Test-Path $BackupRulesPath)) {
  Write-Error "[ABORT] Backup file not found: $BackupRulesPath"
  exit 4
}

Copy-Item -Path $BackupRulesPath -Destination "firestore.rules" -Force
& .\production\deploy-production.ps1 -ProductionProjectId $CleanProject -ProductionDatabaseId $CleanDb -Target rules -ConfirmDeploy
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "[PASS] Production Firestore Rules rolled back successfully from $BackupRulesPath." -ForegroundColor Green
