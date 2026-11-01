package moe.shizuku.manager.model

import rikka.shizuku.Shizuku

data class ServiceStatus(
        val uid: Int = -1,
        val apiVersion: Int = -1,
        val patchVersion: Int = -1,
        val seContext: String? = null,
        val canGrantRuntimePermission: Boolean = false
) {
    val isRunning: Boolean
        get() = uid != -1 && Shizuku.pingBinder()

    val isRoot: Boolean
        get() = uid == 0

    val canManageApps: Boolean
        get() = isRunning

    val canUseTerminal: Boolean
        get() = isRunning

    val showLimitedAdbHint: Boolean
        get() = isRunning && !canGrantRuntimePermission

    val versionName: String
        get() {
            val patch = if (patchVersion >= 0) patchVersion else 6
            return "$apiVersion.$patch"
        }

    val hasCompleteVersion: Boolean
        get() = apiVersion >= 0
}
