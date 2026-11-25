# Hướng dẫn Rollback Môi trường Production (FOX)

Khi phát sinh sự cố vận hành trên môi trường Production, thực hiện các bước sau để khôi phục an toàn:

## 1. Rollback Firestore Security Rules
Sử dụng bản sao lưu tự động được lưu trong thư mục `backups/` trước mỗi lần deploy:
```powershell
.\production\rollback-production.ps1 `
  -ProductionProjectId "<YOUR_PRODUCTION_PROJECT_ID>" `
  -ProductionDatabaseId "fox-prod-db" `
  -BackupRulesPath "backups\firestore.production.rules.<TIMESTAMP>.bak" `
  -ExecuteRollback
```

## 2. Rollback Cloud Functions v2
Nếu Cloud Functions gặp lỗi, checkout phiên bản commit ổn định trước đó trên git, build và deploy lại:
```powershell
git checkout <COMMIT_STABLE_HASH> -- functions/
npm --prefix functions run build
.\production\deploy-production.ps1 -ProductionProjectId "<YOUR_PRODUCTION_PROJECT_ID>" -ProductionDatabaseId "fox-prod-db" -Target functions -ConfirmDeploy -BlazeApproved
```
*(Lưu ý: Không ghi đè hoặc xóa dữ liệu giao dịch thực tế trên cơ sở dữ liệu Firestore trong quá trình rollback mã nguồn).*
