# HR40 分发签名证书

从 **v3.5.0** 起，所有 `dist/` 发布包必须使用仓库内固定的二进制密钥库：

```text
cursor_bluetooth_igp_hr40/keystore/hr40-distribution.keystore
```

该文件已纳入版本控制。之后每一个新版本的安装包都**只能**通过此密钥库签名（运行 `./scripts/build_dist_apk.sh`），禁止再使用各机器自带的 `~/.android/debug.keystore`，以避免再次出现签名冲突、无法覆盖安装的问题。

## 当前分发线指纹（v3.5.0+）

- SHA-256: `7ca2098facb2297a447c1730d1731a3f89b74cb35f9f46ca8dc12bc10f02dd51`
- 别名 / 密码：见 `hr40-distribution.properties`（`androiddebugkey` / `android`）

## 检查密钥库

```bash
./scripts/check_keystore_matches_distribution.sh
```

输出 `MATCH` 表示本机 `keystore/hr40-distribution.keystore` 与仓库一致。

## 构建发布包

```bash
./scripts/build_dist_apk.sh
```

脚本会校验输出 APK 的证书指纹为 **v3.5.0 线**（`7ca2098f…`），否则构建失败。

## CI 可选覆盖

若需在 CI 中覆盖仓库内密钥库，可在 Secrets 中设置 `HR40_DISTRIBUTION_KEYSTORE_BASE64`（须与上述指纹一致）：

```bash
base64 -w0 keystore/hr40-distribution.keystore   # Linux
```

## 从旧版升级

若设备已安装 v3.4.8、v3.10.0 等**旧签名线**的 APK，安装 v3.5.0 前需**先卸载**旧版（签名不同，无法直接覆盖）。详见根目录 `README.md` 与 `dist/SIGNING.md`。

## 历史指纹（只读参考）

| 产品线 | SHA-256 |
|--------|---------|
| v3.4.6–v3.4.8、v3.10.0 | `4cfb9b4041f293901fbffacfc51e31f4d35061f9ec27b8d906a54bf5dbdc4810` |
| v3.4.0–v3.4.5 | `87fbddbb5e436e533e70972f8b995e8c551667cde43d0df0a0cf6705babb897b` |
