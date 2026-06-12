# CLAUDE.md

本文件用于指导在本仓库中协作的 AI（Claude）。每次会话都应遵守以下约定。

## 项目简介

HR40 是连接 iGPSPORT/IGP HR40 蓝牙心率带（标准 BLE 心率服务 `0x2A37`）的原生 Android（Java）运动监测应用。
同一套核心代码通过 Gradle productFlavors 编译为两个版本：

- **offline**：纯本地，无网络依赖，包名 `com.cursor.hr40`
- **online**：在 offline 基础上增加 Supabase 云同步，包名 `com.cursor.hr40.online`（两版可共存）

另有独立的 `web/`（Nuxt 3 + Nuxt UI）网页端，与 Android online 版**共用同一套 Supabase 后端**。

## 开发指令路由约定（重要）

收到开发指令时，按以下关键词决定修改范围：

| 指令关键词 | 修改范围 | 对应路径 |
|------------|----------|----------|
| **online** | 仅在线版 | `app/src/online/...`（OnlineSyncManager、AuthActivity、SupabaseApiClient、OnlineFeatures 真实现等） |
| **offline** | 仅离线版 | `app/src/offline/...`（主要是 `OnlineFeatures` 空实现） |
| **app** / 样式·UI 相关 | **两个版本同时改** | `app/src/main/...`（共享核心代码、布局、`res/` 样式资源） |
| **web** | 网页端 | `web/`（Nuxt 3 + Nuxt UI） |
| 涉及数据库 | 直接在 Supabase 操作表 | 通过 Supabase MCP 工具（`list_tables` → `apply_migration` / SQL / RLS） |

### 执行细则

1. **样式/UI 改动**落在 `app/src/main/res/`（colors/styles/strings/布局），天然同时影响两个 flavor，符合"app 或样式同时改两个版本"。
2. **online 改动**若牵涉共享逻辑：UI/数据模型放 `main`，网络同步部分放 `online`，必须保证离线版仍能编译（依赖 `app/src/offline/.../OnlineFeatures.java` 空实现兜底）。涉及此类边界时先说明再动手。
3. **数据库操作**：先 `list_tables` 看现有结构，再用 `apply_migration` 做规范 migration（而非临时 SQL）。改动前说明动了哪张表、加了什么字段/RLS。当前为 Dev 环境（`HR40-Dev`），破坏性变更先与用户确认。
4. **数据一致性**：Android online 版与 web 端共用同一套 Supabase 表。改表结构后必须**同时检查 Android `online` 与 `web` 两端**是否需要同步修改，避免一端改了另一端读不到。

## 架构速查

```
app/src/main/   共享核心代码（UI Activity、BleHeartRateManager、EnergyEstimator、
                WorkoutRepository、db/Room 实体与 DAO、PdfReportExporter、res/ 资源）
app/src/offline/  OnlineFeatures 空实现
app/src/online/   OnlineFeatures 真实现 + Supabase 同步/登录/账户管理
web/              Nuxt 3 网页端（与 online 共享 Supabase 后端）
```

- 数据流：心率带 → `BleHeartRateManager`（订阅 `0x2A37`）→ 运动中写 JSON 缓冲 → 结束后归档 Room（`hr40_workouts.db`）→ 查看明细 / 导出 PDF /（online）同步 Supabase。
- 消耗算法：Keytel 公式 + 力量模式 `0.88` 修正系数（见 `EnergyEstimator`）。
- Supabase 表：`profiles`、`workout_records`、`heart_rate_samples`、`strength_sets`（开启 RLS，用户仅访问自己数据）。

## 构建环境

- AGP 8.7.3 / Gradle 8.10.2（wrapper 自动下载）/ JDK 17 / compileSdk 35 / minSdk 26 / targetSdk 35
- 依赖极简：offline 仅 AndroidX + Material + Room；online 额外 OkHttp

### 常用命令（Windows PowerShell）

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"; $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

.\gradlew.bat assembleOfflineDebug   # 构建离线版 debug
.\gradlew.bat assembleOnlineDebug    # 构建在线版 debug

# 安装到真机（adb 在 platform-tools 下）
adb install -r app\build\outputs\apk\offline\debug\app-offline-debug.apk
```

## 版本号规则（`app/build.gradle` 的 versionName / versionCode 同步更新）

格式 `x.y.z`：
- `x`：消耗算法升级/演进 +1
- `y`：新增功能 +1（同时 `z` 归零）
- `z`：仅页面/UI 调整 +1

## 签名约定

v3.5.0 起所有 `dist/` 安装包必须用仓库内固定密钥库 `keystore/hr40-distribution.keystore` 签名
（执行 `./scripts/build_dist_apk.sh`），不得改用本机 `debug.keystore`。详见 `keystore/README.md`、`dist/SIGNING.md`。
