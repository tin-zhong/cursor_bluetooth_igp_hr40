# dist APK 签名说明

| 文件 | 与 v3.4.5 同签名 | 说明 |
|------|------------------|------|
| `hr40-offline-fitness-v3.4.0.apk` … `v3.4.5.apk` | 是 | 可在同一构建环境连续覆盖安装 |
| `hr40-offline-fitness-v3.4.6.apk` | **否** | 误用临时 debug 密钥，与 v3.4.5 冲突，请勿分发 |
| `hr40-offline-fitness-v3.4.7.apk` | 见 PR #18 构建说明 | 正式发布请用 `build_dist_apk.sh` 配合同证书 |
| `hr40-offline-fitness-v3.4.8.apk` | **否** | 与 v3.4.5 证书不一致 |
| `hr40-offline-fitness-v3.10.0.apk` | **否**（云构建） | 应用内运动明细 + 训练倒计时；云 Agent 无分发密钥时使用临时 debug 证书 |

期望证书 SHA-256（与 v3.4.5 一致，可覆盖升级）：`87fbddbb5e436e533e70972f8b995e8c551667cde43d0df0a0cf6705babb897b`

维护者在本机配置 `HR40_DISTRIBUTION_KEYSTORE_BASE64` 或 `~/.android/debug.keystore`（指纹与上表一致）后运行 `./scripts/build_dist_apk.sh`，可重新生成与 v3.4.5 同签名的 `v3.10.0` 包。
