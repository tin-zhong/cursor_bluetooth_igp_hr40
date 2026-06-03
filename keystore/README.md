# HR40 分发签名证书

`dist/` 中的 APK 必须使用**同一套**签名密钥，否则无法从旧版本直接覆盖安装（会提示签名冲突）。

## 期望证书指纹（与 v3.0.0–v3.4.5 一致）

- SHA-256: `87fbddbb5e436e533e70972f8b995e8c551667cde43d0df0a0cf6705babb897b`

## 首次配置 / 恢复密钥

若本机 `~/.android/debug.keystore` 的 SHA-256 与上面一致，可执行：

```bash
./scripts/capture_distribution_keystore.sh
git add keystore/hr40-distribution.keystore
```

或在 Cursor Cloud Agent **Secrets** 中配置环境变量 `HR40_DISTRIBUTION_KEYSTORE_BASE64`（`debug.keystore` 文件的 Base64），构建脚本会自动写入 `keystore/hr40-distribution.keystore`。

## 构建发布包

```bash
./scripts/build_dist_apk.sh
```

脚本会校验输出 APK 的证书指纹，与期望不一致时构建失败。

## 说明

- v3.4.6 曾在新的构建环境中使用了临时 debug 密钥，与 v3.4.5 不一致，需卸载后才能安装；从 v3.4.7 起应使用本目录固定密钥。
- 密钥文件为开发分发用途，密码与别名见 `hr40-distribution.properties`（默认与 Android debug 一致：`android` / `androiddebugkey`）。勿用于 Play 商店正式上架。
