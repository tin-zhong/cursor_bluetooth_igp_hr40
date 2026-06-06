# HR40 Online APK

Online 版本安装包目录，与 `dist/` 同级。

## 直接使用

### 分发策略（debug 优先）

当前阶段 **只发布 debug 包**，待功能验收通过后再生成正式版 `v{version}.apk`（与 Offline 相同策略）。

| 类型 | 路径 | 说明 |
|------|------|------|
| **测试用（当前推荐）** | `hr40-online-fitness-debug.apk` | 构建脚本默认只更新此文件 |
| 正式版（暂缓） | `hr40-online-fitness-v{versionName}.apk` | 需显式执行 `RELEASE_DIST_APK=1 ./scripts/build_dist_online_apk.sh` |

下载 debug 包：仓库 `main` 分支 → `dist-online/hr40-online-fitness-debug.apk` → **Download raw file**。

签名说明见 [SIGNING.md](./SIGNING.md)，密钥库见 [keystore/README.md](./keystore/README.md)。

## 发布新版本

版本号与 offline 保持一致（见 `app/build.gradle`）。发版时执行：

```bash
# 日常测试：只更新 debug 包
bash scripts/build_dist_online_apk.sh
git add dist-online/hr40-online-fitness-debug.apk
git commit -m "Update Online debug APK"

# 正式版（仅在明确要求后执行）
RELEASE_DIST_APK=1 bash scripts/build_dist_online_apk.sh
git add dist-online/
git commit -m "Release v{version} Online dist APK"
```

GitHub Actions 的 **Build dist Online APK** 仅作备用（本地无法构建时使用），产物在 Artifacts 中。

## Online 版功能

- 云端登录注册、资料填写、用户管理
- 动作名称与网页端 `exercises` 表同步
- 训练记录结束后自动上传 Supabase（可在网页查看）
