# Shizuku (Fork)

[中文版](./README.zh.md)

## Disclaimer

This is a **fork** of Shizuku. If you are looking for the official Shizuku developed by Rikka, please visit the [**Official Repository**](https://github.com/RikkaApps/Shizuku).

## Changes and Enhancements in this Fork

- **Core Fixes and Optimizations**:
  - ~~Randomize `/data/local/tmp/shizuku` directory name~~
  - ~~Automatically delete `/data/local/tmp/shizuku_starter` files~~
  - Enable ADB root permissions on userdebug ROMs.
  - Support for custom ADB TCP/IP ports, resolving conflicts when the default port 5555 is occupied.

- **Automation and Watchdog**:
  - **Start on Boot (Wireless ADB)**: Supports rooted devices and Android 11+ (Wireless ADB) to automatically start the service on boot without a computer.
  - **Auto Wake-up**: When an application requests Shizuku service, the Manager will attempt to automatically wake up the background service via Wireless ADB if it is not running.
  - **Watchdog Service**: Introduced a watchdog service for ADB mode that monitors the service status in real-time and automatically repairs disconnections, significantly improving stability.

- **Workflow Optimizations**:
  - **One-tap Notification Start**: Optimized the Wireless ADB pairing process. After successful pairing, users can start the service directly from the system notification without returning to the app.
  - **TV Device Optimization**: Tailored startup logic and UI layout for Android TV and set-top boxes, ensuring compatibility with remote control operations.

- **Modern Visual Experience**:
  - **Material 3 UI**: Completely rewritten settings and management interfaces using **Jetpack Compose**, featuring smoother animations and more intuitive interaction logic.
  - **Dynamic Colors (Material You)**: Full support for Android 12+ dynamic color systems; the interface tone automatically adjusts to your system wallpaper.
  - **Pure Black Dark Mode**: Added a "Pure Black" theme option for OLED screens, providing extreme visual contrast and effective power saving.

## Usage Guide

### Start on Boot (Wireless ADB)
1. Configure Shizuku following the Wireless ADB pairing process.
2. Enable `Start on boot (Wireless ADB)` in Settings.
   - This requires `WRITE_SECURE_SETTINGS` permission.
   - It can be granted automatically by the Manager when Shizuku starts (if already running), or manually via ADB:
     `adb shell pm grant moe.shizuku.privileged.api android.permission.WRITE_SECURE_SETTINGS`

> [!CAUTION]
> `WRITE_SECURE_SETTINGS` is a high-risk permission. Use it only if you understand the risks. The developers are not responsible for any consequences.

### Startup Support Details
- **Root Mode**: Supports most rooted devices to automatically load the service on boot.
- **Wireless ADB Mode**: For Android 11+. Uses `WRITE_SECURE_SETTINGS` to monitor network status and restart Shizuku automatically without a PC.
- **TV Devices**: Specifically optimized for stability in television environments.

## Background

When developing apps that require root, the most common method is to execute commands in a su shell. For example, some apps use the `pm enable/disable` command to enable or disable components.

This approach has significant drawbacks:
1. **Very slow** (multiple processes are created).
2. **Unreliable** (requires handling text output).
3. Restricted to existing commands.
4. Requires root even if ADB has sufficient permissions.

Shizuku uses a completely different approach. See below for details.

## Guide & Download
Official documentation and downloads: <https://shizuku.rikka.app/>

## How Shizuku Works?

Android uses `binder` for inter-process communication (IPC). Shizuku guides users to start a process (Shizuku server) with root or ADB privileges. When an application starts, a `binder` pointing to the Shizuku server is sent to the application.

Shizuku acts as an intermediary: it receives requests from applications, forwards them to the system server, and returns the results. This allows apps to use system APIs with higher privileges, which is almost identical to calling system APIs directly.

## Developer Guide
Refer to: <https://github.com/RikkaApps/Shizuku-API>

## Developing Shizuku

### Build
- Clone with `git clone --recurse-submodules`.
- Run gradle task `:manager:assembleDebug` or `:manager:assembleRelease`.

The `:manager:assembleDebug` task generates a debuggable server. You can attach a debugger to the `shizuku_server` process. Ensure "Always install with package manager" is checked in Android Studio settings.

## License
Licensed under Apache 2.0.

Under Apache 2.0 section 6:
* **FORBIDDEN** to use `ic_launcher` images unless for Shizuku itself.
* **FORBIDDEN** to use `Shizuku` as app name or `moe.shizuku.privileged.api` as ID.

## Credits
- [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)
- [yangFenTuoZi/Shizuku](https://github.com/yangFenTuoZi/Shizuku)
- [pixincreate/Shizuku](https://github.com/pixincreate/Shizuku)
- [thedjchi/Shizuku](https://github.com/thedjchi/Shizuku)
- ...
