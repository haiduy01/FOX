#!/usr/bin/env bash
# FOX Full Feature Pilot — Safe Isolated Firebase Staging Deploy Script (Bash)
set -euo pipefail

LIVE_PROJECT_ID="gen-lang-client-0957111252"
LIVE_DATABASE_ID="ai-studio-android-1a796de6-79f0-4283-8d0b-703e709b27e4"

STAGING_PROJECT_ID=""
STAGING_DATABASE_ID="fox-staging-db"
STAGING_ADMIN_EMAIL="hahaiduy28@gmail.com"
TARGET="rules" # rules | functions | all
CONFIRM=0
BLAZE_APPROVED=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project) STAGING_PROJECT_ID="${2:-}"; shift 2 ;;
    --database) STAGING_DATABASE_ID="${2:-}"; shift 2 ;;
    --admin-email) STAGING_ADMIN_EMAIL="${2:-}"; shift 2 ;;
    --target) TARGET="${2:-rules}"; shift 2 ;;
    --confirm) CONFIRM=1; shift ;;
    --blaze-approved) BLAZE_APPROVED=1; shift ;;
    *) echo "[ABORT] Unknown argument: $1"; exit 2 ;;
  esac
done

if [[ -z "${STAGING_PROJECT_ID}" || "${STAGING_PROJECT_ID}" == your-* ]]; then
  echo "[ABORT] Missing explicit staging --project <STAGING_PROJECT_ID>."
  exit 2
fi
if [[ "${STAGING_PROJECT_ID}" == "${LIVE_PROJECT_ID}" ]]; then
  echo "[ABORT][GUARDRAIL] Refusing to deploy to LIVE Firebase project (${LIVE_PROJECT_ID})."
  exit 3
fi
if [[ -z "${STAGING_DATABASE_ID}" || "${STAGING_DATABASE_ID}" == "${LIVE_DATABASE_ID}" ]]; then
  echo "[ABORT][GUARDRAIL] Refusing to target LIVE Firestore database (${LIVE_DATABASE_ID})."
  exit 3
fi
if [[ "${TARGET}" != "rules" && "${TARGET}" != "functions" && "${TARGET}" != "all" ]]; then
  echo "[ABORT] Invalid --target '${TARGET}'. Allowed: rules | functions | all"
  exit 2
fi
if [[ ( "${TARGET}" == "functions" || "${TARGET}" == "all" ) && "${BLAZE_APPROVED}" -ne 1 ]]; then
  echo "[ABORT][BILLING GUARDRAIL] Deploying Cloud Functions v2 requires explicit owner Blaze approval (--blaze-approved). No billing changes were made."
  exit 4
fi

TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_FILE="backups/firestore.rules.${TIMESTAMP}.bak"

echo "=== FOX STAGING DEPLOYMENT PLAN ==="
echo "Target Project  : ${STAGING_PROJECT_ID}"
echo "Target Database : ${STAGING_DATABASE_ID}"
echo "Deploy Target   : ${TARGET}"
echo "Region          : asia-southeast1"
echo "Rules Backup    : ${BACKUP_FILE}"
echo "Confirm Deploy  : ${CONFIRM} (0 = DRY-RUN, 1 = EXECUTE)"
echo "Blaze Approved  : ${BLAZE_APPROVED}"

if [[ "${CONFIRM}" -ne 1 ]]; then
  echo "[DRY-RUN] No remote Firebase changes performed."
  exit 0
fi

mkdir -p backups
cp firestore.rules "${BACKUP_FILE}"
echo "[OK] Backed up firestore.rules -> ${BACKUP_FILE}"

DB_LIST="$(firebase firestore:databases:list --project "${STAGING_PROJECT_ID}" 2>&1)"
if ! echo "${DB_LIST}" | grep -q "${STAGING_DATABASE_ID}"; then
  echo "[ABORT] Target Firestore database '${STAGING_DATABASE_ID}' not found in project '${STAGING_PROJECT_ID}'."
  exit 5
fi
echo "[OK] Verified target database '${STAGING_DATABASE_ID}' exists."

TMP_CONFIG="$(mktemp)"
trap 'rm -f "${TMP_CONFIG}"' EXIT
cat > "${TMP_CONFIG}" <<EOF
{
  "firestore": {
    "database": "${STAGING_DATABASE_ID}",
    "rules": "firestore.rules",
    "indexes": "firestore.indexes.json"
  },
  "functions": [
    {
      "source": "functions",
      "codebase": "fox-pilot-staging",
      "ignore": ["node_modules", ".git", "firebase-debug.log", "firebase-debug.*.log", "*.local", "test/**"],
      "predeploy": ["npm --prefix \"\$RESOURCE_DIR\" run build"]
    }
  ]
}
EOF

if [[ "${TARGET}" == "rules" || "${TARGET}" == "all" ]]; then
  firebase deploy --only firestore:rules,firestore:indexes --project "${STAGING_PROJECT_ID}" --config "${TMP_CONFIG}"
fi

if [[ "${TARGET}" == "functions" || "${TARGET}" == "all" ]]; then
  ENV_FILE="functions/.env.${STAGING_PROJECT_ID}"
  cat > "${ENV_FILE}" <<EOF
FOX_FIRESTORE_DATABASE_ID=${STAGING_DATABASE_ID}
FOX_ADMIN_EMAIL=${STAGING_ADMIN_EMAIL}
EOF
  npm --prefix functions run build
  firebase deploy --only functions --project "${STAGING_PROJECT_ID}" --config "${TMP_CONFIG}"
fi

echo "[PASS] Staging deployment completed safely for project '${STAGING_PROJECT_ID}' (backup: ${BACKUP_FILE})."
