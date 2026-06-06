# dist-online APK 签名说明

## Online v3.5.1+（当前推荐）

| 文件 | 签名线 |
|------|--------|
| `hr40-online-fitness-v3.5.1.apk` 及之后新版本 | **Online v3.5.1 线**（仓库 `dist-online/keystore/hr40-online-distribution.keystore`） |

证书 SHA-256：`27467f3745d55776eadc247ab7e2930388c7f120b980b098893b6a6600cfda71`

维护者发布新版本时，请始终执行 `./scripts/build_dist_online_apk.sh`，脚本会强制使用并校验上述密钥库，**不要**使用本机临时 debug 证书或离线版 `keystore/hr40-distribution.keystore`。

## 与离线版

Online 版使用独立密钥库与包名（`com.cursor.hr40.online`），与离线版 `com.cursor.hr40` **可同时安装**，签名线也相互独立。详见 `dist-online/keystore/README.md`。
