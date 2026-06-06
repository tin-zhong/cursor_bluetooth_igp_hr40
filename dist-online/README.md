# HR40 Online APK

Online 版本安装包目录，与 `dist/` 同级。

## 直接使用

仓库中会提交当前版本的安装包，可直接下载安装：

- `hr40-online-fitness-v{versionName}.apk` — 正式分发包
- `hr40-online-fitness-debug.apk` — 与正式包同签名，便于调试

签名说明见 [SIGNING.md](./SIGNING.md)，密钥库见 [keystore/README.md](./keystore/README.md)。

## 发布新版本

版本号与 offline 保持一致（见 `app/build.gradle`）。发版时执行：

```bash
bash scripts/build_dist_online_apk.sh
git add dist-online/
git commit -m "Release v{version} Online dist APK"
```

与 `dist/` 离线包流程相同：**构建完成后提交到 Git**，无需每次去 Actions 下载。

GitHub Actions 的 **Build dist Online APK** 仅作备用（本地无法构建时使用），产物在 Artifacts 中。

## Online 版功能

- 云端登录注册、资料填写、账户管理
- 动作名称与网页端 `exercises` 表同步
- 训练记录结束后自动上传 Supabase（可在网页查看）
