<#
.SYNOPSIS
  FOX Production V1.0 — Source ZIP Packager & Checksum Generator (Windows 10 PowerShell)
.DESCRIPTION
  Packages clean repository source files into FOX_PRODUCTION_V1.0_SOURCE.zip
  and generates SHA256SUMS.txt, excluding build artifacts, node_modules, and local secrets.
#>

$ErrorActionPreference = "Stop"

$ZipName = "FOX_PRODUCTION_V1.0_SOURCE.zip"
$TempDir = Join-Path $env:TEMP "fox-source-pkg-$(Get-Date -Format 'yyyyMMdd_HHmmss')"
$TargetFolder = Join-Path $TempDir "FOX_PRODUCTION_V1.0_SOURCE"

Write-Host "=== PACKAGING FOX PRODUCTION V1.0 SOURCE ===" -ForegroundColor Cyan

if (Test-Path $ZipName) {
  Remove-Item -Path $ZipName -Force
}
if (Test-Path $TempDir) {
  Remove-Item -Path $TempDir -Recurse -Force
}

New-Item -ItemType Directory -Path $TargetFolder | Out-Null

$IncludeItems = @(
  "app",
  "functions",
  "staging",
  "production",
  "scripts",
  "gradle",
  "public",
  "build.gradle.kts",
  "settings.gradle.kts",
  "gradle.properties",
  "firestore.rules",
  "firestore.indexes.json",
  "firebase.json",
  "firebase-blueprint.json",
  "firestore.test.js",
  "metadata.json",
  "package.json",
  "package-lock.json",
  "debug.keystore.base64",
  "FOX_PRODUCTION_V1.0_RELEASE_CHECKLIST.md",
  "FOX_PRODUCTION_DEPLOY_GUIDE.md",
  "FOX_PRODUCTION_TEST_REPORT.md",
  "FOX_CHANGELOG.md",
  "FOX_ROLLBACK_GUIDE.md",
  "README.md"
)

foreach ($item in $IncludeItems) {
  if (Test-Path $item) {
    Write-Host "Including: $item"
    Copy-Item -Path $item -Destination $TargetFolder -Recurse -Force
  }
}

# Clean excluded artifacts inside copied folders (build, node_modules, .gradle, etc.)
Get-ChildItem -Path $TargetFolder -Recurse -Directory -Include "build", "node_modules", ".gradle", ".kotlin", ".idea" | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
Get-ChildItem -Path $TargetFolder -Recurse -File -Include "*.iml", "local.properties", ".env", "*.jks", "*.keystore" | Remove-Item -Force -ErrorAction SilentlyContinue

Write-Host "Compressing to $ZipName..." -ForegroundColor Cyan
Compress-Archive -Path "$TargetFolder\*" -DestinationPath $ZipName -Force

$Hash = (Get-Hash -Algorithm SHA256 -Path $ZipName).Hash
$ShaContent = "$Hash  $ZipName`n"
Set-Content -Path "SHA256SUMS.txt" -Value $ShaContent -Encoding UTF8

Write-Host "[PASS] Successfully created $ZipName and SHA256SUMS.txt" -ForegroundColor Green

Remove-Item -Path $TempDir -Recurse -Force -ErrorAction SilentlyContinue
