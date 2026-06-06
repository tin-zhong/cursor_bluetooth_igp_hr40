# HR40 Online 分发签名证书

从 **v3.5.1 Online** 起，所有 `dist-online/` 发布包必须使用本目录固定的二进制密钥库：

```text
cursor_bluetooth_igp_hr40/dist-online/keystore/hr40-online-distribution.keystore
```

该文件已纳入版本控制。之后每一个 Online 新版本的安装包都**只能**通过此密钥库签名（运行 `./scripts/build_dist_online_apk.sh`），禁止再使用各机器自带的 `~/.android/debug.keystore` 或离线版 `keystore/hr40-distribution.keystore`。

## 当前分发线指纹（Online v3.5.1+）

- SHA-256: `27467f3745d55776eadc247ab7e2930388c7f120b980b098893b6a6600cfda71`
- 别名 / 密码：见 `hr40-online-distribution.properties`（`androiddebugkey` / `android`）

## 与离线版的区别

| 项目 | 离线版 | Online 版 |
|------|--------|-----------|
| 密钥库路径 | `keystore/hr40-distribution.keystore` | `dist-online/keystore/hr40-online-distribution.keystore` |
| 包名 | `com.cursor.hr40` | `com.cursor.hr40.online` |
| 输出目录 | `dist/` | `dist-online/` |

两套密钥库相互独立，包名也不同，**可同时安装**。

## 检查密钥库

```bash
./scripts/check_keystore_matches_online_distribution.sh
```

输出 `MATCH` 表示本机密钥库与仓库一致。

## 构建发布包

```bash
./scripts/build_dist_online_apk.sh
```

脚本会通过 `-PuseOnlineDistributionKeystore` 强制使用本目录密钥库，并校验输出 APK 的证书指纹为 **Online v3.5.1 线**（`27467f3…`），否则构建失败。

## CI 可选覆盖

若需在 CI 中覆盖仓库内密钥库，可在 Secrets 中设置 `HR40_ONLINE_DISTRIBUTION_KEYSTORE_BASE64`（须与上述指纹一致）：

```bash
base64 -w0 dist-online/keystore/hr40-online-distribution.keystore   # Linux
```
