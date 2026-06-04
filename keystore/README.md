# HR40 分发签名证书

`dist/` 中的 APK 必须使用**与已安装版本一致**的签名密钥，否则无法覆盖安装（会提示签名冲突）。

## 当前推荐指纹（v3.4.6–v3.4.8 / v3.5.0 分发线）

- SHA-256: `4cfb9b4041f293901fbffacfc51e31f4d35061f9ec27b8d906a54bf5dbdc4810`
- 对应 `dist/hr40-offline-fitness-v3.4.6.apk` … `v3.4.8.apk`

## 旧版指纹（v3.4.0–v3.4.5）

- SHA-256: `87fbddbb5e436e533e70972f8b995e8c551667cde43d0df0a0cf6705babb897b`

## 检查本机密钥是否匹配 v3.4.8

```bash
./scripts/check_keystore_matches_v348.sh
```

若输出 `MATCH`，即可构建与 v3.4.8 同签名的 v3.10.0。

## 配置密钥

**方式 A — Cursor / CI Secret（推荐）**

在 Secrets 中设置 `HR40_DISTRIBUTION_KEYSTORE_BASE64`（`debug.keystore` 文件的 Base64，无换行）：

```bash
base64 -w0 ~/.android/debug.keystore   # Linux
# macOS: base64 -i ~/.android/debug.keystore | tr -d '\n'
```

**方式 B — 本机 debug.keystore**

若 `~/.android/debug.keystore` 指纹与 v3.4.8 一致：

```bash
./scripts/capture_distribution_keystore.sh
```

**方式 C — 提交到仓库（可选）**

```bash
cp ~/.android/debug.keystore keystore/hr40-distribution.keystore
git add -f keystore/hr40-distribution.keystore
```

## 构建发布包

```bash
./scripts/build_dist_apk.sh
```

脚本要求输出 APK 的证书指纹为 **v3.4.8 线**（`4cfb9b40…`），否则构建失败，避免再次发布无法覆盖安装的包。

密码与别名见 `hr40-distribution.properties`（默认 `android` / `androiddebugkey`）。
