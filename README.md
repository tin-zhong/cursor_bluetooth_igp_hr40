# HR40 Offline Fitness

一款面向 iGPSPORT/IGP HR40 心率带的原生 Android 离线运动监测应用。应用运行在支持 BLE 的 Android 设备上（例如一加平板 2 Pro），通过标准 Bluetooth Heart Rate Service 连接心率带，并在本机完成资料存储、运动记录、统计计算和 PDF 报告导出。

## 功能

- 首次打开应用录入运动人员姓名、性别、年龄、身高、体重，数据保存在设备本地应用私有存储中。
- 扫描并连接 HR40 心率带，订阅标准心率测量特征 `0x2A37`。
- 从“开始运动”到“结束运动”持续记录心率采样，运动中数据先写入 JSON 缓冲，结束运动后归档到 Room 数据库（SQLite）。
- 离线计算平均/最高/最低心率、心率区间、估算能量消耗和采样数量。
- 运动结束后导出 PDF 报告，包含运动概览、心率曲线、消耗和心率区间图表。
- PDF 写入设备 `Downloads/HR40` 目录，并可通过系统分享面板发送。

## 构建

项目是无第三方运行时依赖的原生 Android/Java 应用，可用 Android Studio 打开根目录后同步并运行，也可以直接使用仓库内的 Gradle Wrapper 构建。

```bash
./scripts/setup_android_env.sh
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$HOME/android-sdk"
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"
./gradlew assembleDebug
```

为了便于平板测试，当前分支还提交了一份已构建的 Debug APK：

```text
dist/hr40-offline-fitness-v3.3.0.apk
```

下载该 APK 到一加平板 2 Pro 后，允许“安装未知来源应用”即可安装测试。

## v3.3.0 更新

- 新增运动暂停功能：开始后按钮切换为“暂停运动”，暂停后可“继续运动”
- 暂停期间不累计运动时长，不写入心率采样

## v3.2.1 更新

- 运动中实时写入 JSON 缓冲；结束运动后自动写入 Room 数据库并清理对应 JSON 文件

## v3.2.0 更新

- 训练记录改为：运动中 JSON + 结束后 Room 归档
- 首次启动自动把遗留 JSON 训练记录迁移进数据库

## v3.1.0 更新

- 每周一首次打开 App 强制更新体重，用于新一周消耗估算
- 引入 Room 本地数据库，训练结束后写入一条结构化记录（含心率采样与力量组）
- 新增“导出原始训练数据(JSON)”按钮，可分享数据库原始数据

## v3.0.0 更新

- 消耗算法新增“力量模式修正系数”，力量训练按 `0.88` 系数衰减（有氧模式保持原算法）

## v2.2.0 更新

- 力量训练 PDF 导出支持完整分页，不再只显示前 9 条
- 主界面心率与时长固定在顶部，避免滚动导致看不到关键数据
- “结束运动”与“导出 PDF”分离，支持选择任意历史记录导出

## v2.1.4 更新

- 心率带断开、扫描或连接中时，心率显示 `--` 而非保留上次数值
- 增加顶部系统栏安全间距，标题不再紧贴状态栏

## v2.1.3 更新

- 升级心率消耗估算算法（Keytel 公式 + 60 bpm 阈值 + 区间平均）
- 运动中实时显示累计估算消耗
- 简洁主页面，开始运动后选择力量/有氧训练
- 动作管理支持添加和删除
- Material Components 原生界面美化

## 版本号规则

版本号格式为 `x.y.z`：

- `x`：消耗算法升级或演进时 +1
- `y`：新增功能时 +1
- `z`：仅页面/UI 调整时 +1

当前版本：`3.3.0`
- `x=3`：Keytel 算法增加“力量模式 0.88 修正系数”
- `y=2`：训练记录全面切换为 Room 存储（含 JSON 迁移）
- `z=0`：无单独 UI 版本变化

## 安装注意

如果安装后界面没有变化，请先卸载旧版 App，再安装 `dist/hr40-offline-fitness-v3.3.0.apk`。
打开 App 后标题应显示 **HR40 离线运动监测 v3.2.0**。

## 权限

- Android 12+：`BLUETOOTH_SCAN`、`BLUETOOTH_CONNECT`
- Android 11 及以下：`ACCESS_FINE_LOCATION`（BLE 扫描要求）

所有运动和个人资料数据均保存在本机，不依赖网络服务。
