#!/usr/bin/env bash
# FOX Full Feature Pilot — Isolated Staging Rollback Script
# SAFETY: Hard-aborts if pointed at live project `gen-lang-client-0957111252`.

set -euo pipefail

LIVE_PROJECT_ID="gen-lang-client-0957111252"
STAGING_PROJECT_ID="${1:-${FOX_STAGING_PROJECT_ID:-}}"
BACKUP_RULES_PATH="${2:-backups/firestore.rules.bak}"
DRY_RUN="${DRY_RUN:-1}"

if [[ -z "${STAGING_PROJECT_ID}" || "${STAGING_PROJECT_ID}" == your-* ]]; then
  echo "[ABORT] Usage: bash staging/rollback-staging.sh <staging-project-id> [backup-rules-path]"
  exit 2
fi

if [[ "${STAGING_PROJECT_ID}" == "${LIVE_PROJECT_ID}" ]]; then
  echo "[ABORT][GUARDRAIL] Refusing to run rollback against LIVE project (${LIVE_PROJECT_ID})."
  exit 3
fi

echo "=== FOX STAGING ROLLBACK PLAN ==="
echo "Staging Project : ${STAGING_PROJECT_ID}"
echo "Rules Backup    : ${BACKUP_RULES_PATH}"
echo "DRY_RUN         : ${DRY_RUN} (set DRY_RUN=0 to execute commands)"
echo ""

echo "Step 1: Verify rules backup exists"
if [[ ! -f "${BACKUP_RULES_PATH}" ]]; then
  echo "[WARN] Backup file '${BACKUP_RULES_PATH}' not found. Ensure you have a snapshot before rolling back rules."
fi

echo "Step 2: Restore previous Firestore Rules to STAGING ONLY:"
echo "  cp \"${BACKUP_RULES_PATH}\" firestore.rules"
echo "  firebase deploy --only firestore:rules --project \"${STAGING_PROJECT_ID}\" --config staging/firebase.staging.json"

echo "Step 3: Roll back Cloud Functions v2 on STAGING ONLY (checkout previous git tag/commit first):"
echo "  git checkout <previous-known-good-tag> -- functions/"
echo "  npm --prefix functions run build"
echo "  firebase deploy --only functions --project \"${STAGING_PROJECT_ID}\" --config staging/firebase.staging.json"

echo "Step 4: Optional — disable broken callable or trigger without deleting data:"
echo "  firebase functions:delete <functionName> --region asia-southeast1 --project \"${STAGING_PROJECT_ID}\""

if [[ "${DRY_RUN}" == "0" ]]; then
  if [[ ! -f "${BACKUP_RULES_PATH}" ]]; then
    echo "[ABORT] Cannot execute rollback without existing backup file: ${BACKUP_RULES_PATH}"
    exit 4
  fi
  cp "${BACKUP_RULES_PATH}" firestore.rules
  firebase deploy --only firestore:rules --project "${STAGING_PROJECT_ID}" --config staging/firebase.staging.json
  echo "[OK] Staging Firestore Rules restored from ${BACKUP_RULES_PATH}."
else
  echo ""
  echo "[DRY-RUN COMPLETE] No remote Firebase changes were executed."
fi
