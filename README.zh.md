# Shizuku (Fork)

[English](./README.md)

## 免责声明

此为 Shizuku 的 **分支版本**。若您需寻找 Rikka 开发的官方 Shizuku，此处并非正确渠道
请访问 [**_官方仓库_**](https://github.com/RikkaApps/Shizuku)

### 本仓库的变更与增强功能

- **核心修复与优化**：
  - ~~随机化 `/data/local/tmp/shizuku` 目录名称~~
  - ~~自动删除 `/data/local/tmp/shizuku_starter` 文件~~
  - 在 userdebug ROM 上启用 ADB root 权限
  - 支援自定义 ADB TCP/IP 端口，解决默认 5555 端口被占用的问题。

- **自动化启动与守护 (Watchdog)**：
  - **开机自启动 (无线侦错)**：支援在部分 Android 11+ 装置上，透过无线侦错环境实现免 Root 开机自动启动服务。
  - **自动唤醒**：当有应用程式请求 Shizuku 服务时，若服务未运行，Manager 将尝试透过无线侦错自动唤醒后台服务。
  - **Watchdog 守护进程**：新增 ADB 模式下的守护服务，实时监控服务状态并在断连时自动修复，显著提升连线稳定性。

- **操作流程优化**：
  - **一键式通知启动**：优化无线侦错配对流程，在成功配对后，使用者可直接从系统通知栏点击启动，无需返回应用介面。
  - **TV 装置适配**：针对 Android TV 与电视盒装置优化启动逻辑，并调整介面布局以适配遥控器操作。

- **全新现代化视觉体验**：使用 **Jetpack Compose** 完全重构设定与管理介面，带来更流畅的动画与更直观的操作逻辑。

### 自启动功能用法

1. 按照无线 ADB 配对流程配置 Shizuku
2. 在 `设置` 中启用 `开机启动（无线调试）`
   - 启用前需先授予 `WRITE_SECURE_SETTINGS` 权限（可通过 `rish` 或使用电脑通过 ADB 完成 / **在 Shizuku 启动时通过 Manager 自动授权**）
   - 执行以下命令 `adb shell pm grant moe.shizuku.privileged.api android.permission.WRITE_SECURE_SETTINGS`


> [!CAUTION]
> `WRITE_SECURE_SETTINGS` 为高危权限，仅建议明确风险后启用。开发者对后续可能产生的后果不承担责任。

> [!NOTE]
> 服务自动重启功能未经充分测试

### 启动支援详解

- **Root 模式**：支援绝大多数已 Root 的装置在开机时自动加载服务。
- **无线侦错模式 (ADB)**：适用于 Android 11 及以上版本。透过 `WRITE_SECURE_SETTINGS` 权限监听网路状态，实现无需连接电脑即可自动重新启动 Shizuku。
- **TV 装置**：特别优化了电视环境下的稳定性。

## 背景

当开发需要 root 的应用程式时，最常见的方法是在 su shell 中执行一些命令。举例来说，有些应用会使用 `pm enable/disable` 指令来启用或停用元件。

这种作法有非常明显的缺点：

1. **非常慢**（会建立多个程序）
2. 需要处理文字输出，**非常不可靠**
3. 可用范围只受限于现有命令
4. 即使 ADB 有足够权限，应用程式本身仍需要 root 权限才能执行

Shizuku 采用完全不同的方法。详情请见下方说明。

## 使用指南与下载

官方文档与下载：<https://shizuku.rikka.app/>

## Shizuku 如何运作？

首先，我们要先说明应用程式如何使用系统 API。举例来说，如果应用想取得已安装的应用程式，我们都知道应该使用 `PackageManager#getInstalledPackages()`。这其实是应用程序与系统伺服器程序之间的跨程序通讯（IPC），只是 Android 框架已经帮我们做好了底层工作。

Android 使用 `binder` 来处理这种 IPC。`Binder` 让伺服器端能够知道用户端的 uid 和 pid，因此系统伺服器可以检查该应用是否有权执行该操作。

通常，若某个「管理器」会提供给应用使用，例如 `PackageManager`，系统伺服器中就应该会有对应的「服务」，例如 `PackageManagerService`。你可以把这件事简化理解成：如果应用持有该「服务」的 `binder`，就能与它通讯。应用程序在启动时，也会接收到系统服务的 binder。

Shizuku 会引导使用者先以 root 或 ADB 启动一个程序，也就是 Shizuku server。当应用启动时，指向 Shizuku server 的 `binder` 也会一并送给应用。

Shizuku 最重要的功能，是扮演一个中介者：接收来自应用的请求，转送到系统伺服器，再把结果回传。你可以查看 `rikka.shizuku.server.ShizukuService` 的 `transactRemote` 方法，以及 `moe.shizuku.api.ShizukuBinderWrapper` 类别来了解细节。

如此一来，我们就达成了目标，也就是以更高的权限使用系统 API；对应用程式来说，这几乎和直接呼叫系统 API 没有差别。

## 开发者指南

### API 与范例

请参考：https://github.com/RikkaApps/Shizuku-API

### 从 v11 之前的版本迁移

> 当然，既有应用仍然可以正常运作。

详情请见：https://github.com/RikkaApps/Shizuku-API#migration-guide-for-existing-applications-use-shizuku-pre-v11

### 注意事项

1. **ADB 权限限制**
   ADB 的权限在不同系统版本上不尽相同。你可以在 [这里](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/packages/Shell/AndroidManifest.xml) 查看授予 ADB 的权限。
   在呼叫 API 之前，你可以使用 `ShizukuService#getUid` 确认 Shizuku 是否以使用者 ADB 执行，或使用 `ShizukuService#checkPermission` 检查服务是否具有足够权限。

2. **Android 9 的 Hidden API 限制**
   自 Android 9 起，正常应用对 hidden API 的使用受到限制。请改用其他方法（例如 <https://github.com/LSPosed/AndroidHiddenApiBypass>）。

3. **Android 8.0 与 ADB**
   目前 Shizuku service 取得应用程序的方式，是结合 `IActivityManager#registerProcessObserver` 和 `IActivityManager#registerUidObserver`（26+），以确保应用在启动时会被送入。
   但在 API 26 上，ADB 缺少使用 `registerUidObserver` 的权限，因此如果你需要在不一定由 Activity 启动的程序中使用 Shizuku，建议透过启动透明 Activity 来触发 binder 传送。

4. **直接使用 `transactRemote` 时需注意**
   - API 会因 Android 版本不同而有所差异，请务必仔细检查。另外，`android.app.IActivityManager` 在 API 26 之后才有 aidl 形式，而 `android.app.IActivityManager$Stub` 只存在于 API 26。
   - `SystemServiceHelper.getTransactionCode` 可能无法取得正确的交易码。这个状况目前已经处理，但不排除还有其他情形。使用 `ShizukuBinderWrapper` 时不会遇到这个问题。

## 开发 Shizuku

### 构建 (Build)

- 使用 `git clone --recurse-submodules` 复制仓库（包含子模组）。
- 在 Android Studio 中执行 Gradle 任务 `:manager:assembleDebug` 或 `:manager:assembleRelease`。

`:manager:assembleDebug` 任务会生成一个可调试的伺服器。您可以将调试器附加到 `shizuku_server` 程序进行调试。请注意，在 Android Studio 中应勾选 "Run/Debug configurations" -> "Always install with package manager"，以确保使用最新代码。

## 授权条款 (License)

本专案中的所有代码文件均根据 Apache 2.0 协议授权。

根据 Apache 2.0 协议第 6 条，特别声明：

* **禁止** 使用 `manager/src/main/res/mipmap*/ic_launcher*.png` 图像文件，除非用于显示 Shizuku 应用本身。

* **禁止** 使用 `Shizuku` 作为您的应用名称，或使用 `moe.shizuku.privileged.api` 作为 application id，亦或宣告 `moe.shizuku.manager.permission.*` 相关权限。

## 鸣谢

- [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)
- [yangFenTuoZi/Shizuku](https://github.com/yangFenTuoZi/Shizuku)
- [pixincreate/Shizuku](https://github.com/pixincreate/Shizuku)
- [thedjchi/Shizuku](https://github.com/thedjchi/Shizuku)
- ...
