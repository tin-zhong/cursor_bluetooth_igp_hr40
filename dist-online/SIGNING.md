# dist-online APK 签名说明

Online 版与离线版使用**同一套分发密钥库**（`keystore/hr40-distribution.keystore`）。

证书 SHA-256：`7ca2098facb2297a447c1730d1731a3f89b74cb35f9f46ca8dc12bc10f02dd51`

发布新版本时，请执行：

```bash
bash scripts/build_dist_online_apk.sh
```

脚本会校验签名指纹，通过后将 APK 提交到本目录（与 `dist/` 离线包相同流程）。

## 包名

Online 版包名为 `com.cursor.hr40.online`，与离线版 `com.cursor.hr40` **可同时安装**，互不影响。
