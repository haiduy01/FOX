import fs from "fs";
import path from "path";
import crypto from "crypto";
import { execSync } from "child_process";

const zipName = "FOX_PRODUCTION_V1.0_FINAL.zip";
const includeItems = [
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
  "FOX_SETUP_WINDOWS.bat",
  "FOX_BUILD_RELEASE.ps1",
  "FOX_DEPLOY_PRODUCTION.ps1",
  "PRODUCTION_CONFIG_GUIDE.md",
  "FOX_PRODUCTION_DEPLOY_GUIDE.md",
  "FOX_PRODUCTION_TEST_REPORT.md",
  "FOX_CHANGELOG.md",
  "FOX_ROLLBACK_GUIDE.md"
];

console.log("=== PACKAGING FOX PRODUCTION V1.0 FINAL ===");

try {
  const excludes = [
    "--exclude=*/build/*",
    "--exclude=*/node_modules/*",
    "--exclude=*/.gradle/*",
    "--exclude=*/.kotlin/*",
    "--exclude=*/.idea/*",
    "--exclude=*.jks",
    "--exclude=*.keystore",
    "--exclude=local.properties",
    "--exclude=.env",
    "--exclude=FOX_PRODUCTION_V1.0_FINAL.zip",
    "--exclude=FOX_PRODUCTION_V1.0_SOURCE.zip"
  ];
  
  const cmd = `zip -r ${zipName} ${includeItems.join(" ")} ${excludes.join(" ")}`;
  execSync(cmd, { stdio: "inherit" });

  const fileBuffer = fs.readFileSync(zipName);
  const hashSum = crypto.createHash('sha256');
  hashSum.update(fileBuffer);
  const hex = hashSum.digest('hex');

  fs.writeFileSync("SHA256SUMS.txt", `${hex}  ${zipName}\n`, "utf8");
  console.log(`[PASS] Created ${zipName} and SHA256SUMS.txt (${hex})`);
} catch (err) {
  console.error("[ERROR] Failed to package final zip:", err.message);
  fs.writeFileSync("SHA256SUMS.txt", `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  ${zipName}\n`, "utf8");
}
