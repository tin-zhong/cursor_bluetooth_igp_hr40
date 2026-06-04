# dist APK 签名说明

| 文件 | 与 v3.4.8 同签名 | 说明 |
|------|------------------|------|
| `hr40-offline-fitness-v3.5.0.apk` | **须为是** | 应用内运动明细 + 训练倒计时（当前推荐安装包） |
| `hr40-offline-fitness-debug.apk` | 与 v3.5.0 一致 | 与正式包同一次构建的调试副本 |

## 证书指纹

| 产品线 | SHA-256 |
|--------|---------|
| **v3.4.6–v3.4.8、v3.5.0（推荐）** | `4cfb9b4041f293901fbffacfc51e31f4d35061f9ec27b8d906a54bf5dbdc4810` |
| v3.4.0–v3.4.5 | `87fbddbb5e436e533e70972f8b995e8c551667cde43d0df0a0cf6705babb897b` |

## v3.5.0 与 v3.4.8 签名冲突

若安装 `v3.5.0` 时提示与已安装的 **v3.4.8** 签名冲突，说明该 APK 使用了错误的构建证书。请维护者：

1. 在本机运行 `./scripts/check_keystore_matches_v348.sh` 确认密钥；
2. 配置 `HR40_DISTRIBUTION_KEYSTORE_BASE64` 后运行 `./scripts/build_dist_apk.sh`；
3. 将新生成的 `dist/hr40-offline-fitness-v3.5.0.apk` 提交到仓库。

**不要**使用未通过 `build_dist_apk.sh` 校验的云 Agent 临时 debug 证书构建的包。
