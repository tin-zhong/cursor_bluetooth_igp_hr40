# dist APK 签名说明

| 文件 | 与 v3.4.5 同签名 | 说明 |
|------|------------------|------|
| `hr40-offline-fitness-v3.4.0.apk` … `v3.4.5.apk` | 是 | 可在同一构建环境连续覆盖安装 |
| `hr40-offline-fitness-v3.4.6.apk` | **否** | 误用临时 debug 密钥，与 v3.4.5 冲突，请勿分发 |
| `hr40-offline-fitness-v3.4.7.apk` | **否**（当前 dist 为 Cloud 构建） | 功能测试可安装；从 v3.4.5 升级请用 `build_dist_apk.sh` 配合同证书重新打包 |

期望证书 SHA-256：`87fbddbb5e436e533e70972f8b995e8c551667cde43d0df0a0cf6705babb897b`
