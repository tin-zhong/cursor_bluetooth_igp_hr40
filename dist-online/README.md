# HR40 Online APK

Online 版本安装包输出目录，与 `dist/` 同级。

## 为什么仓库里只有 README？

APK **不会提交到 Git**（与 `dist/` 离线包相同）。本目录只保留说明文件；安装包需本地构建，或通过 GitHub Actions 下载。

常见原因：

1. **尚未成功构建** — Online 版曾存在编译错误，构建脚本未跑完就不会生成 APK。
2. **未运行构建脚本** — 需主动执行下方命令。
3. **未触发 CI** — 在 GitHub Actions 中手动运行 **Build dist Online APK**，从 Artifacts 下载。

## 构建命令

```bash
bash scripts/build_dist_online_apk.sh
```

成功后会生成：

- `hr40-online-fitness-v{versionName}.apk`
- `hr40-online-fitness-debug.apk`

版本号与 offline 版本保持一致（见 `app/build.gradle` 中的 `versionName` / `versionCode`）。

## Online 版功能

- 云端登录注册、资料填写、账户管理
- 动作名称与网页端 `exercises` 表同步
- 训练记录结束后自动上传 Supabase（可在网页查看）
