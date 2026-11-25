#!/usr/bin/env bash
# Build isolated Android STAGING APK (Bash) without permanently altering app/google-services.json.
set -euo pipefail

LIVE_PROJECT_ID="gen-lang-client-0957111252"
LIVE_DATABASE_ID="ai-studio-android-1a796de6-79f0-4283-8d0b-703e709b27e4"
EXPECTED_PKG="com.aistudio.fox.superapp"

STAGING_PROJECT_ID=""
STAGING_DATABASE_ID="fox-staging-db"
STAGING_GS_PATH="staging/google-services.staging.json"
DRY_RUN=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project) STAGING_PROJECT_ID="${2:-}"; shift 2 ;;
    --database) STAGING_DATABASE_ID="${2:-}"; shift 2 ;;
    --google-services) STAGING_GS_PATH="${2:-}"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    *) echo "[ABORT] Unknown argument: $1"; exit 2 ;;
  esac
done

if [[ -z "${STAGING_PROJECT_ID}" || "${STAGING_PROJECT_ID}" == your-* ]]; then
  echo "[ABORT] Usage: bash staging/build-staging-apk.sh --project <STAGING_PROJECT_ID> [--database fox-staging-db] [--dry-run]"
  exit 2
fi
if [[ "${STAGING_PROJECT_ID}" == "${LIVE_PROJECT_ID}" ]]; then
  echo "[ABORT][GUARDRAIL] Refusing to build STAGING APK for LIVE project (${LIVE_PROJECT_ID})."
  exit 3
fi
if [[ -z "${STAGING_DATABASE_ID}" || "${STAGING_DATABASE_ID}" == "${LIVE_DATABASE_ID}" ]]; then
  echo "[ABORT][GUARDRAIL] Refusing to build STAGING APK for LIVE database (${LIVE_DATABASE_ID})."
  exit 3
fi
if [[ ! -f "${STAGING_GS_PATH}" ]]; then
  echo "[ABORT] Missing '${STAGING_GS_PATH}'. Download staging google-services.json from Firebase Console first."
  exit 5
fi

node -e '
const fs = require("fs");
const [gsPath, expectedProj, liveProj, expectedPkg] = process.argv.slice(1);
const json = JSON.parse(fs.readFileSync(gsPath, "utf8"));
const proj = json?.project_info?.project_id || "";
if (proj === liveProj) { console.error("[ABORT][GUARDRAIL] google-services file points to LIVE project!"); process.exit(3); }
if (proj !== expectedProj) { console.error(`[ABORT] project_id mismatch: got ${proj}, expected ${expectedProj}`); process.exit(6); }
const hasPkg = (json?.client || []).some(c => c?.client_info?.android_client_info?.package_name === expectedPkg);
if (!hasPkg) { console.error(`[ABORT] package_name ${expectedPkg} not found in ${gsPath}`); process.exit(6); }
console.log(`[OK] Verified ${gsPath}: project_id=${proj}, package=${expectedPkg}`);
' "${STAGING_GS_PATH}" "${STAGING_PROJECT_ID}" "${LIVE_PROJECT_ID}" "${EXPECTED_PKG}"

if [[ "${DRY_RUN}" -eq 1 ]]; then
  echo "[DRY-RUN] Validation PASS. Re-run without --dry-run to build staging/out/fox-staging-${STAGING_PROJECT_ID}-debug.apk."
  exit 0
fi

ORIG_GS="app/google-services.json"
TMP_BACKUP="$(mktemp)"
HAD_ORIG=0
if [[ -f "${ORIG_GS}" ]]; then
  cp "${ORIG_GS}" "${TMP_BACKUP}"
  HAD_ORIG=1
fi

restore_gs() {
  if [[ "${HAD_ORIG}" -eq 1 && -f "${TMP_BACKUP}" ]]; then
    cp "${TMP_BACKUP}" "${ORIG_GS}"
    rm -f "${TMP_BACKUP}"
    echo "[OK] Restored original app/google-services.json."
  fi
}
trap restore_gs EXIT

cp "${STAGING_GS_PATH}" "${ORIG_GS}"
gradle :app:assembleDebug \
  "-PfoxEnv=STAGING" \
  "-PfoxStagingProjectId=${STAGING_PROJECT_ID}" \
  "-PfoxStagingDbId=${STAGING_DATABASE_ID}"

mkdir -p staging/out
OUT_APK="staging/out/fox-staging-${STAGING_PROJECT_ID}-debug.apk"
cp "app/build/outputs/apk/debug/app-debug.apk" "${OUT_APK}"
echo "[PASS] Built isolated Staging APK -> ${OUT_APK}"
