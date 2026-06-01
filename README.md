# Shizuku

## 免责声明

此为 Shizuku 的 **分支版本**。若您需寻找 Rikka 开发的官方 Shizuku，此处并非正确渠道。  
请访问 [**_官方仓库_**](https://github.com/RikkaApps/Shizuku)

### 本仓库的变更

- ~~随机化 `/data/local/tmp/shizuku` 目录名称~~
- ~~自动删除 `/data/local/tmp/shizuku_starter` 文件~~
- 在 userdebug ROM 上启用 ADB root 权限
- 支持非 Root 设备自启动
- 支持自定义 ADB TCP/IP 端口
- 優化無線偵錯配對流程: 配對成功後可直接從通知啟動服務。
- 自動化啟動與守護: 支援透過無線偵錯自動啟動，並在應用程式請求時自動喚醒服務。
- TV 裝置支援: 針對電視裝置優化啟動邏輯與介面。

### 自启动功能用法

1. 按照无线 ADB 配对流程配置 Shizuku
2. 在 `设置` 中启用 `开机启动（无线调试）`
   - 启用前需先授予 `WRITE_SECURE_SETTINGS` 权限（可通过 `rish` 或使用电脑通过 ADB 完成 / **在 Shizuku 启动时通过 Manager 自动授权**）
   - 执行以下命令 `adb shell pm grant moe.shizuku.privileged.api android.permission.WRITE_SECURE_SETTINGS`


> [!CAUTION]
> `WRITE_SECURE_SETTINGS` 为高危权限，仅建议明确风险后启用。开发者对后续可能产生的后果不承担责任。

> [!NOTE]
> 服务自动重启功能未经充分测试

### 啟動支援

- `Start on boot` 可用於已 root 裝置、Android R 以上版本，以及受支援的 TV 裝置。
- `Start on boot (wireless ADB)` 仍需要 `WRITE_SECURE_SETTINGS` 權限。

## 背景

當開發需要 root 的應用程式時，最常見的方法是在 su shell 中執行一些命令。舉例來說，有些應用會使用 `pm enable/disable` 指令來啟用或停用元件。

這種作法有非常明顯的缺點：

1. **非常慢**（會建立多個程序）
2. 需要處理文字輸出，**非常不可靠**
3. 可用範圍只受限於現有命令
4. 即使 ADB 有足夠權限，應用程式本身仍需要 root 權限才能執行

Shizuku 採用完全不同的方法。詳情請見下方說明。

## 使用指南與下載

<https://shizuku.rikka.app/>

## Shizuku 如何運作？

首先，我們要先說明應用程式如何使用系統 API。舉例來說，如果應用想取得已安裝的應用程式，我們都知道應該使用 `PackageManager#getInstalledPackages()`。這其實是應用程序與系統伺服器程序之間的跨程序通訊（IPC），只是 Android 框架已經幫我們做好了底層工作。

Android 使用 `binder` 來處理這種 IPC。`Binder` 讓伺服器端能夠知道用戶端的 uid 和 pid，因此系統伺服器可以檢查該應用是否有權執行該操作。

通常，若某個「管理器」會提供給應用使用，例如 `PackageManager`，系統伺服器中就應該會有對應的「服務」，例如 `PackageManagerService`。你可以把這件事簡化理解成：如果應用持有該「服務」的 `binder`，就能與它通訊。應用程序在啟動時，也會接收到系統服務的 binder。

Shizuku 會引導使用者先以 root 或 ADB 啟動一個程序，也就是 Shizuku server。當應用啟動時，指向 Shizuku server 的 `binder` 也會一併送給應用。

Shizuku 最重要的功能，是扮演一個中介者：接收來自應用的請求，轉送到系統伺服器，再把結果回傳。你可以查看 `rikka.shizuku.server.ShizukuService` 的 `transactRemote` 方法，以及 `moe.shizuku.api.ShizukuBinderWrapper` 類別來了解細節。

如此一來，我們就達成了目標，也就是以更高的權限使用系統 API；對應用程式來說，這幾乎和直接呼叫系統 API 沒有差別。

## 開發者指南

### API 與範例

https://github.com/RikkaApps/Shizuku-API

### 從 v11 之前的版本遷移

> 當然，既有應用仍然可以正常運作。

https://github.com/RikkaApps/Shizuku-API#migration-guide-for-existing-applications-use-shizuku-pre-v11

### 注意事項

1. ADB 權限有限

   ADB 的權限在不同系統版本上不盡相同。你可以在 [這裡](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/packages/Shell/AndroidManifest.xml) 查看授予 ADB 的權限。

   在呼叫 API 之前，你可以使用 `ShizukuService#getUid` 確認 Shizuku 是否以使用者 ADB 執行，或使用 `ShizukuService#checkPermission` 檢查服務是否具有足夠權限。

2. Android 9 的 Hidden API 限制

   自 Android 9 起，正常應用對 hidden API 的使用受到限制。請改用其他方法（例如 <https://github.com/LSPosed/AndroidHiddenApiBypass>）。

3. Android 8.0 與 ADB

   目前 Shizuku service 取得應用程序的方式，是結合 `IActivityManager#registerProcessObserver` 和 `IActivityManager#registerUidObserver`（26+），以確保應用在啟動時會被送入。  
   但在 API 26 上，ADB 缺少使用 `registerUidObserver` 的權限，因此如果你需要在不一定由 Activity 啟動的程序中使用 Shizuku，建議透過啟動透明 Activity 來觸發 binder 傳送。

4. 直接使用 `transactRemote` 時需注意

   - API 會因 Android 版本不同而有所差異，請務必仔細檢查。另外，`android.app.IActivityManager` 在 API 26 之後才有 aidl 形式，而 `android.app.IActivityManager$Stub` 只存在於 API 26。
   - `SystemServiceHelper.getTransactionCode` 可能無法取得正確的交易碼，例如 `android.content.pm.IPackageManager$Stub.TRANSACTION_getInstalledPackages` 在 API 25 不存在，而是 `android.content.pm.IPackageManager$Stub.TRANSACTION_getInstalledPackages_47`。這個狀況目前已經處理，但不排除還有其他情形。使用 `ShizukuBinderWrapper` 時不會遇到這個問題。

## Developing Shizuku itself

### Build

- Clone with `git clone --recurse-submodules`
- Run gradle task `:manager:assembleDebug` or `:manager:assembleRelease`

The `:manager:assembleDebug` task generates a debuggable server. You can attach a debugger to `shizuku_server` to debug the server. Be aware that, in Android Studio, "Run/Debug configurations" - "Always install with package manager" should be checked, so that the server will use the latest code.

## License

All code files in this project are licensed under Apache 2.0

Under Apache 2.0 section 6, specifically:

* You are **FORBIDDEN** to use `manager/src/main/res/mipmap*/ic_launcher*.png` image files, unless for displaying Shizuku itself.

* You are **FORBIDDEN** to use `Shizuku` as app name or use `moe.shizuku.privileged.api` as application id or declare `moe.shizuku.manager.permission.*` permission.

## 鸣谢

- [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)
- [pixincreate/Shizuku](https://github.com/pixincreate/Shizuku)
- [thedjchi/Shizuku](https://github.com/thedjchi/Shizuku)
- ...
