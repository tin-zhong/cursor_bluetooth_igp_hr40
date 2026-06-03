# dist APK 签名说明

| 文件 | 与 v3.4.5 同签名 | 说明 |
|------|------------------|------|
| `hr40-offline-fitness-v3.4.0.apk` … `v3.4.5.apk` | 是 | 可在同一构建环境连续覆盖安装 |
| `hr40-offline-fitness-v3.4.6.apk` | **否** | 误用临时 debug 密钥，与 v3.4.5 冲突，请勿分发 |
| `hr40-offline-fitness-v3.4.7.apk` | 见 PR #18 构建说明 | 正式发布请用 `build_dist_apk.sh` 配合同证书 |
| `hr40-offline-fitness-v3.4.8.apk` | 见 `build_dist_apk.sh` | 仅功能：力量训练重量允许为 0 |

期望证书 SHA-256：`87fbddbb5e436e533e70972f8b995e8c551667cde43d0df0a0cf6705babb897b`
