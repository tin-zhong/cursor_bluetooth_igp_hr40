# HR40 Online APK

Online 版本安装包输出目录，与 `dist/` 同级。

构建命令：

```bash
bash scripts/build_dist_online_apk.sh
```

版本号与 offline 版本保持一致（见 `app/build.gradle` 中的 `versionName` / `versionCode`）。

## Online 版功能

- 云端登录注册、资料填写、账户管理
- 动作名称与网页端 `exercises` 表同步
- 训练记录结束后自动上传 Supabase（可在网页查看）
