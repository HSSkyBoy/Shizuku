package moe.shizuku.manager.adb

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ShizukuSettings.ADB_ROOT
import moe.shizuku.manager.ShizukuSettings.TCPIP_PORT
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.starter.StarterActivity
import java.net.Socket

class AdbWirelessHelper {
    companion object {
        private const val TCPIP_REBIND_TIMEOUT_MS = 5_000L
    }

    fun validateThenEnableWirelessAdb(
        contentResolver: ContentResolver,
        context: Context,
        wait: Boolean = false
    ): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (wait) {
            val timeoutMs = 20_000L
            val intervalMs = 1000L
            var elapsed = 0L

            runBlocking {
                while (elapsed < timeoutMs) {
                    if (hasWifiTransport(connectivityManager)) {
                        enableWirelessADB(contentResolver, context)
                        return@runBlocking
                    }
                    delay(intervalMs)
                    elapsed += intervalMs
                }
            }
        }

        if (hasWifiTransport(connectivityManager)) {
            enableWirelessADB(contentResolver, context)
            return true
        } else {
            Log.w(AppConstants.TAG, "Wireless ADB auto-start condition not met: Not on Wi-Fi.")
        }
        return false
    }

    private fun hasWifiTransport(connectivityManager: ConnectivityManager): Boolean {
        return connectivityManager.allNetworks.any { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    private fun enableWirelessADB(contentResolver: ContentResolver, context: Context) {
        // Enable wireless ADB
        try {
            Settings.Global.putInt(contentResolver, "adb_wifi_enabled", 1)
            Settings.Global.putInt(contentResolver, Settings.Global.ADB_ENABLED, 1)
            Settings.Global.putLong(contentResolver, "adb_allowed_connection_time", 0L)

            Log.i(AppConstants.TAG, "Wireless Debugging enabled via secure setting.")
            Toast.makeText(context, "Wireless Debugging enabled", Toast.LENGTH_SHORT).show()
        } catch (se: SecurityException) {
            Log.e(AppConstants.TAG, "Permission denied trying to enable wireless debugging.", se)
            throw se
        } catch (e: Exception) {
            Log.e(AppConstants.TAG, "Error enabling wireless debugging.", e)
            throw e
        }
    }

    fun disableWirelessAdb(contentResolver: ContentResolver) {
        try {
            Settings.Global.putInt(contentResolver, "adb_wifi_enabled", 0)
            Log.i(AppConstants.TAG, "Wireless Debugging disabled via secure setting.")
        } catch (se: SecurityException) {
            Log.e(AppConstants.TAG, "Permission denied trying to disable wireless debugging.", se)
        } catch (e: Exception) {
            Log.e(AppConstants.TAG, "Error disabling wireless debugging.", e)
        }
    }

    fun launchStarterActivity(
        context: Context,
        host: String,
        port: Int,
        forceRestart: Boolean = false
    ) {
        val intent = Intent(context, StarterActivity::class.java).apply {
            putExtra(StarterActivity.EXTRA_IS_ROOT, false)
            putExtra(StarterActivity.EXTRA_HOST, host)
            putExtra(StarterActivity.EXTRA_PORT, port)
            putExtra(StarterActivity.EXTRA_FORCE_RESTART, forceRestart)
        }
        context.startActivity(intent)
    }

    private fun executeAdbRootIfNeeded(
        host: String,
        port: Int,
        key: AdbKey,
        commandOutput: StringBuilder,
        onOutput: (String) -> Unit
    ): Boolean {
        if (!ShizukuSettings.getPreferences().getBoolean(ADB_ROOT, false)) {
            return false
        }

        AdbClient(host, port, key).use { client ->
            client.connect()

            var flag = false
            client.root {
                commandOutput.append(String(it).apply {
                    if (contains("adbd is already running as root") || contains("restarting adbd as root")) flag = true
                }).append("\n")
                onOutput(commandOutput.toString())
            }

            return flag
        }
    }


    fun getConfiguredTcpipPort(): Int? {
        val value = ShizukuSettings.getPreferences().getString(TCPIP_PORT, "")?.trim()
        if (value.isNullOrEmpty()) {
            return null
        }
        val port = value.toIntOrNull()
        return port?.takeIf { it in 1..65535 }
    }

    fun getStartableAdbPort(): Int? {
        val systemPort = moe.shizuku.manager.utils.EnvironmentUtils.getAdbTcpPort()
        if (systemPort in 1..65535) {
            return systemPort
        }
        return getConfiguredTcpipPort()
    }

    private fun changeTcpipPort(
        host: String,
        port: Int,
        newPort: Int,
        key: AdbKey,
        commandOutput: StringBuilder,
        onOutput: (String) -> Unit
    ): Boolean {
        AdbClient(host, port, key).use { client ->
            client.connect()

            var flag = false
            client.tcpip(newPort) {
                commandOutput.append(String(it).apply {
                    if (contains(Regex("restarting in TCP mode port: [0-9]*"))) flag = true
                }).append("\n")
                onOutput(commandOutput.toString())
            }

            return flag
        }
    }

    private fun waitForAdbPortAvailable(
        host: String,
        port: Int,
        timeoutMs: Long = 15000L
    ): Boolean {
        val intervalMs = 300L
        var elapsed = 0L
        while (elapsed < timeoutMs) {
            try {
                Socket(host, port).use {
                    return true
                }
            } catch (_: Exception) {
                Thread.sleep(intervalMs)
                elapsed += intervalMs
            }
        }
        return false
    }

    private fun changeTcpipPortAfterStartIfNeeded(
        host: String,
        currentPort: Int,
        key: AdbKey,
        commandOutput: StringBuilder,
        onOutput: (String) -> Unit
    ) {
        val newPort = getConfiguredTcpipPort()
        if (newPort == null || newPort == currentPort) {
            return
        }

        try {
            val confirmed = changeTcpipPort(host, currentPort, newPort, key, commandOutput, onOutput)
            if (!confirmed) {
                Log.w(AppConstants.TAG, "ADB did not confirm TCP/IP port switch to $newPort")
            }

            if (!waitForAdbPortAvailable(host, newPort, TCPIP_REBIND_TIMEOUT_MS)) {
                Log.w(AppConstants.TAG, "Timed out waiting for ADB to listen on TCP/IP port $newPort")
            }
        } catch (e: Throwable) {
            Log.w(AppConstants.TAG, "Failed to switch ADB TCP/IP port after Shizuku start", e)
        }
    }

    fun startShizukuViaAdb(
        host: String,
        port: Int,
        coroutineScope: CoroutineScope,
        onOutput: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onSuccess: () -> Unit = {}
    ) {
        coroutineScope.launch(Dispatchers.IO) {
            var attempt = 0
            while (attempt < 3) {
                try {
                    Log.d(AppConstants.TAG, "Attempting to start Shizuku via ADB on $host:$port (attempt $attempt)")

                    val key = try {
                        AdbKey(
                            PreferenceAdbKeyStore(ShizukuSettings.getPreferences()), "shizuku"
                        )
                    } catch (e: Throwable) {
                        Log.e(AppConstants.TAG, "ADB Key error", e)
                        onError(AdbKeyException(e))
                        return@launch
                    }

                    val commandOutput = StringBuilder()

                    executeAdbRootIfNeeded(host, port, key, commandOutput, onOutput)

                    AdbClient(host, port, key).use { client ->
                        try {
                            client.connect()
                            Log.i(
                                AppConstants.TAG,
                                "ADB connected to $host:$port. Executing starter command..."
                            )

                            client.shellCommand(Starter.internalCommand) { output ->
                                val outputString = String(output)
                                commandOutput.append(outputString)
                                onOutput(outputString)
                            }
                        } catch (e: Throwable) {
                            Log.e(AppConstants.TAG, "Error during ADB connection/command execution (attempt $attempt)", e)
                            if (attempt < 2) {
                                delay(1000L * (attempt + 1))
                                attempt++
                                return@use // continue while loop
                            }
                            onError(e)
                            return@launch
                        }
                    }

                    changeTcpipPortAfterStartIfNeeded(host, port, key, commandOutput, onOutput)

                    Log.i(AppConstants.TAG, "Shizuku start via ADB completed successfully")
                    onSuccess()
                    return@launch
                } catch (e: Throwable) {
                    Log.e(AppConstants.TAG, "Error in startShizukuViaAdb (attempt $attempt)", e)
                    if (attempt < 2) {
                        delay(1000L * (attempt + 1))
                        attempt++
                    } else {
                        onError(e)
                        return@launch
                    }
                }
            }
        }
    }
}
