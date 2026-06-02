# HR40 Offline Fitness

一款面向 iGPSPORT/IGP HR40 心率带的原生 Android 离线运动监测应用。应用运行在支持 BLE 的 Android 设备上（例如一加平板 2 Pro），通过标准 Bluetooth Heart Rate Service 连接心率带，并在本机完成资料存储、运动记录、统计计算和 PDF 报告导出。

## 功能

- 首次打开应用录入运动人员姓名、性别、年龄、身高、体重，数据保存在设备本地应用私有存储中。
- 扫描并连接 HR40 心率带，订阅标准心率测量特征 `0x2A37`。
- 从“开始运动”到“结束运动”持续记录心率采样，运动数据以 JSON 保存在本机。
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
dist/hr40-offline-fitness-v2.1.2.apk
```

下载该 APK 到一加平板 2 Pro 后，允许“安装未知来源应用”即可安装测试。

## v2.1.2 更新

- 升级心率消耗估算算法（Keytel 公式 + 60 bpm 阈值 + 区间平均）
- 运动中实时显示累计估算消耗

## v2.1.2 更新

- 简洁主页面，开始运动后选择力量/有氧训练
- 动作管理支持添加和删除
- Material Components 原生界面美化

## 版本号规则

版本号格式为 `x.y.z`：

- `x`：消耗算法升级或演进时 +1
- `y`：新增功能时 +1
- `z`：仅页面/UI 调整时 +1

当前版本：`2.1.2`
- `x=2`：升级 Keytel 消耗算法（HR 区间平均、60 bpm 阈值、负数截断、性别公式）
- `y=1`：保留 v1.x 功能基线
- `z=2`：运动中实时显示估算消耗

## 安装注意

如果安装后界面没有变化，请先卸载旧版 App，再安装 `dist/hr40-offline-fitness-v2.1.2.apk`。
打开 App 后标题应显示 **HR40 离线运动监测 v2.1.2**，并能看到“运动时长”和“力量训练”区域。

## 权限

- Android 12+：`BLUETOOTH_SCAN`、`BLUETOOTH_CONNECT`
- Android 11 及以下：`ACCESS_FINE_LOCATION`（BLE 扫描要求）

所有运动和个人资料数据均保存在本机，不依赖网络服务。
