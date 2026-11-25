#!/usr/bin/env node
/**
 * FOX Full Feature Pilot — Isolated Staging Post-Deploy Smoke Test (Schema-Aligned)
 *
 * SAFETY GUARDRAIL:
 * - Hard-aborts (exit 3) if targeted at live project (`gen-lang-client-0957111252`)
 *   or live database (`ai-studio-android-1a796de6-79f0-4283-8d0b-703e709b27e4`).
 */

import { createRequire } from "module";

const LIVE_PROJECT_ID = "gen-lang-client-0957111252";
const LIVE_DATABASE_ID = "ai-studio-android-1a796de6-79f0-4283-8d0b-703e709b27e4";

function parseArgs(argv) {
  const args = {
    project: process.env.FOX_STAGING_PROJECT_ID || "",
    database: process.env.FOX_STAGING_DATABASE_ID || "fox-staging-db",
    dryRun: false,
  };
  for (let i = 2; i < argv.length; i += 1) {
    const token = argv[i];
    if (token === "--project" && argv[i + 1]) {
      args.project = argv[++i];
    } else if (token === "--database" && argv[i + 1]) {
      args.database = argv[++i];
    } else if (token === "--dry-run") {
      args.dryRun = true;
    }
  }
  return args;
}

function assertSafeStagingTarget(projectId, databaseId) {
  const trimmedProject = (projectId || "").trim();
  const trimmedDb = (databaseId || "").trim();

  if (!trimmedProject || trimmedProject.toLowerCase().startsWith("your-")) {
    console.error(
      "[ABORT] Provide staging --project ID. Example: node staging/smoke-test-staging.mjs --project <STAGING_PROJECT_ID> --database fox-staging-db"
    );
    process.exit(2);
  }
  if (trimmedProject === LIVE_PROJECT_ID) {
    console.error(
      `[ABORT][GUARDRAIL] Refusing to run smoke test against LIVE Firebase project (${LIVE_PROJECT_ID}).`
    );
    process.exit(3);
  }
  if (!trimmedDb || trimmedDb === LIVE_DATABASE_ID) {
    console.error(
      `[ABORT][GUARDRAIL] Refusing to run smoke test against LIVE Firestore database (${LIVE_DATABASE_ID}).`
    );
    process.exit(3);
  }
}

async function main() {
  const args = parseArgs(process.argv);
  assertSafeStagingTarget(args.project, args.database);

  const checks = [];
  const record = (name, status, note) => checks.push({ name, status, note });

  if (args.dryRun) {
    record("Staging Target Guardrail", "PASS", `Isolated project=${args.project}, db=${args.database}`);
    record("Firestore Seed & Subcollection Products", "CHƯA KIỂM THỬ", "Dry-run mode — run without --dry-run after staging seed");
    record("Cloud Functions v2 (12 callables, asia-southeast1)", "CHƯA KIỂM THỬ", "Run against deployed staging project (requires Blaze approval)");
    record("Multi-Device Real FCM Push (2-4 devices)", "CHƯA KIỂM THỬ", "Verify manually on real Android phones");
    printReport(args, checks);
    return;
  }

  const require = createRequire(new URL("../functions/package.json", import.meta.url));
  let getApps;
  let initializeApp;
  let getFirestore;
  try {
    ({ getApps, initializeApp } = require("firebase-admin/app"));
    ({ getFirestore } = require("firebase-admin/firestore"));
  } catch (err) {
    console.error("[ABORT] Thiếu firebase-admin trong functions/node_modules. Hãy chạy: npm --prefix functions install");
    console.error(err?.message || err);
    process.exit(5);
  }

  const app = getApps().length ? getApps()[0] : initializeApp({ projectId: args.project });
  const db = getFirestore(app, args.database);

  const merchantsSnap = await db.collection("merchants").where("active", "==", true).limit(10).get();
  record(
    "Staging Merchants Catalog (/merchants)",
    merchantsSnap.size >= 2 ? "PASS" : "FAIL",
    `Found ${merchantsSnap.size} active merchant(s)`
  );

  let totalProducts = 0;
  let allIntPrices = true;
  for (const merchantDoc of merchantsSnap.docs) {
    const prodSnap = await merchantDoc.ref.collection("products").where("active", "==", true).get();
    totalProducts += prodSnap.size;
    prodSnap.forEach((doc) => {
      const data = doc.data();
      if (!Number.isInteger(data?.priceVnd) || data.priceVnd < 1000 || !Number.isInteger(data?.stock) || data.stock < 0) {
        allIntPrices = false;
      }
    });
  }
  record(
    "Staging Products Subcollection & Integer VND Invariant",
    totalProducts >= 4 && allIntPrices ? "PASS" : "FAIL",
    `Found ${totalProducts} product(s) under /merchants/{id}/products, allIntVnd=${allIntPrices}`
  );

  const demoUserSnap = await db.collection("users").doc("staging_customer_demo").get();
  const demoWalletSnap = await db.collection("wallets").doc("staging_customer_demo").get();
  const demoAdminSnap = await db.collection("admins").doc("staging_admin_demo").get();
  const walletBalance = demoWalletSnap.data()?.balanceVnd;
  record(
    "Staging DEMO Personas & Integer VND Wallet",
    demoUserSnap.exists &&
      demoUserSnap.data()?.role === "customer" &&
      demoWalletSnap.exists &&
      demoWalletSnap.data()?.isDemo === true &&
      Number.isInteger(walletBalance) &&
      demoAdminSnap.exists
      ? "PASS"
      : "FAIL",
    demoWalletSnap.exists
      ? `customerRole=${demoUserSnap.data()?.role}, walletBalanceVnd=${walletBalance}, adminSeeded=${demoAdminSnap.exists}`
      : "Not seeded"
  );

  const hasFailure = checks.some((c) => c.status === "FAIL");
  printReport(args, checks);
  process.exit(hasFailure ? 1 : 0);
}

function printReport(args, checks) {
  console.log("\n## Báo cáo Smoke Test Staging FOX");
  console.log(`- Thời điểm: ${new Date().toISOString()}`);
  console.log(`- Staging Project ID: \`${args.project}\``);
  console.log(`- Staging Database ID: \`${args.database}\``);
  console.log(`- Chế độ tài chính: DEMO — KHÔNG PHẢI TIỀN THẬT\n`);
  console.log("| Hạng mục | Trạng thái | Ghi chú |");
  console.log("| :--- | :---: | :--- |");
  for (const item of checks) {
    console.log(`| ${item.name} | **${item.status}** | ${item.note} |`);
  }
}

main().catch((err) => {
  console.error("[FOX STAGING SMOKE TEST] FAIL:", err?.message || err);
  process.exit(1);
});
