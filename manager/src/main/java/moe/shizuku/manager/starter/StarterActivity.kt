package moe.shizuku.manager.starter

import android.content.Context
import android.os.Bundle
import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import moe.shizuku.manager.AppConstants.EXTRA
import moe.shizuku.manager.R
import moe.shizuku.manager.adb.AdbKeyException
import moe.shizuku.manager.adb.AdbMdns
import moe.shizuku.manager.adb.AdbWirelessHelper
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.utils.EnvironmentUtils
import rikka.lifecycle.Resource
import rikka.lifecycle.Status
import rikka.lifecycle.viewModels
import rikka.shizuku.Shizuku
import java.net.ConnectException
import javax.net.ssl.SSLProtocolException

private class NotRootedException : Exception()

class StarterActivity : AppActivity() {

    private val viewModel by viewModels {
        ViewModel(
            this,
            intent.getBooleanExtra(EXTRA_IS_ROOT, true),
            intent.getStringExtra(EXTRA_HOST),
            intent.getIntExtra(EXTRA_PORT, 0),
            intent.getBooleanExtra(EXTRA_FORCE_RESTART, false)
        )
    }

    private val message = MutableLiveData<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.output.observe(this) {
            val output = it.data!!.trim()
            if (output.endsWith("info: shizuku_starter exit with 0")) {
                viewModel.appendOutput("")
                viewModel.appendOutput("Waiting for service...")

                Shizuku.addBinderReceivedListener(object : Shizuku.OnBinderReceivedListener {
                    override fun onBinderReceived() {
                        Shizuku.removeBinderReceivedListener(this)
                        viewModel.appendOutput("Service started, this window will be automatically closed soon")

                        window?.decorView?.postDelayed({
                            if (!isFinishing) finish()
                        }, 800)
                    }
                })
            } else if (it.status == Status.ERROR) {
                var message = 0
                when (it.error) {
                    is AdbKeyException -> {
                        message = R.string.adb_error_key_store
                    }
                    is NotRootedException -> {
                        message = R.string.start_with_root_failed
                    }
                    is ConnectException -> {
                        message = R.string.cannot_connect_port
                    }
                    is SSLProtocolException -> {
                        message = R.string.adb_pair_required
                    }
                }

                if (message != 0) this.message.value = message
            }
        }

        setContent {
            val outputState by viewModel.output.observeAsState()
            val messageState by message.observeAsState()
            StarterComposeScreen(
                output = outputState?.data?.toString()?.trim().orEmpty(),
                titleRes = R.string.starter,
                messageRes = messageState,
                onNavigateUp = { finish() },
                onDismissMessage = { message.value = null }
            )
        }
    }

    companion object {

        const val EXTRA_IS_ROOT = "$EXTRA.IS_ROOT"
        const val EXTRA_HOST = "$EXTRA.HOST"
        const val EXTRA_PORT = "$EXTRA.PORT"
        const val EXTRA_FORCE_RESTART = "$EXTRA.FORCE_RESTART"
    }
}

private class ViewModel(
    context: Context,
    root: Boolean,
    host: String?,
    port: Int,
    forceRestart: Boolean
) : androidx.lifecycle.ViewModel() {
    companion object {
        private const val MDNS_DISCOVERY_TIMEOUT_MS = 3_000L
        private const val FORCE_RESTART_TIMEOUT_MS = 1_500L
        private const val FORCE_RESTART_POLL_INTERVAL_MS = 50L
    }

    private val sb = StringBuilder()
    private val _output = MutableLiveData<Resource<StringBuilder>>()
    private val adbWirelessHelper = AdbWirelessHelper()
    private val appContext = context.applicationContext
    private var adbMdns: AdbMdns? = null
    private var mdnsFallbackJob: Job? = null
    private var startRequested = false

    val output = _output as LiveData<Resource<StringBuilder>>

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (forceRestart && Shizuku.pingBinder()) {
                    appendOutput("Shizuku is running, force restarting...")
                    appendOutput("")
                    runCatching { Shizuku.exit() }
                    waitForBinderGone()
                }
                if (root) {
                    startRoot()
                } else {
                    startAdb(host!!, port)
                }
            } catch (e: Throwable) {
                postResult(e)
            }
        }
    }

    fun appendOutput(line: String) {
        sb.appendLine(line)
        postResult()
    }

    private fun postResult(throwable: Throwable? = null) {
        if (throwable == null)
            _output.postValue(Resource.success(sb))
        else
            _output.postValue(Resource.error(throwable, sb))
    }

    private fun startRoot() {
        sb.append("Starting with root...").append('\n').append('\n')
        postResult()

        GlobalScope.launch(Dispatchers.IO) {
            if (!Shell.getShell().isRoot) {
                Shell.getCachedShell()?.close()
                sb.append('\n').append("Can't open root shell, try again...").append('\n')

                postResult()
                if (!Shell.getShell().isRoot) {
                    sb.append('\n').append("Still not :(").append('\n')
                    postResult(NotRootedException())
                    return@launch
                }
            }

            Shell.cmd(Starter.internalCommand).to(object : CallbackList<String?>() {
                override fun onAddElement(s: String?) {
                    sb.append(s).append('\n')
                    postResult()
                }
            }).submit {
                if (it.code != 0) {
                    sb.append('\n').append("Send this to developer may help solve the problem.")
                    postResult()
                }
            }
        }
    }

    private fun startAdb(host: String, port: Int) {
        if (port > 0) {
            appendOutput("Starting with wireless adb in port $port...")
            appendOutput("")
            connectAdb(host, port)
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            postResult(ConnectException("Wireless debugging port unavailable"))
            return
        }

        val propertyPort = EnvironmentUtils.getAdbTcpPort()
        if (propertyPort > 0) {
            appendOutput("Starting with wireless adb in port $propertyPort...")
            appendOutput("")
            connectAdb(host, propertyPort)
            return
        }

        appendOutput("Searching for wireless debugging service...")
        appendOutput("")

        val observer = object : Observer<Int> {
            override fun onChanged(foundPort: Int) {
                if (foundPort !in 1..65535 || startRequested) {
                    return
                }
                startRequested = true
                adbMdns?.stop()
                appendOutput("Wireless debugging service found on port $foundPort.")
                appendOutput("")
                connectAdb(host, foundPort)
            }
        }

        adbMdns = AdbMdns(appContext, AdbMdns.TLS_CONNECT, observer).apply { start() }
        mdnsFallbackJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(MDNS_DISCOVERY_TIMEOUT_MS)
            if (startRequested) {
                return@launch
            }
            adbMdns?.stop()
            val fallbackPort = EnvironmentUtils.getAdbTcpPort()
            if (fallbackPort > 0) {
                startRequested = true
                appendOutput("Using fallback wireless adb port $fallbackPort.")
                appendOutput("")
                connectAdb(host, fallbackPort)
            } else {
                postResult(ConnectException("Cannot find wireless debugging service"))
            }
        }
    }

    private fun connectAdb(host: String, port: Int) {
        adbWirelessHelper.startShizukuViaAdb(
            host = host,
            port = port,
            coroutineScope = viewModelScope,
            onOutput = { outputString ->
                sb.append(outputString)
                postResult()
            },
            onError = { e -> postResult(e) })
    }

    private suspend fun waitForBinderGone() {
        var elapsed = 0L
        while (elapsed < FORCE_RESTART_TIMEOUT_MS) {
            if (!Shizuku.pingBinder()) {
                return
            }
            kotlinx.coroutines.delay(FORCE_RESTART_POLL_INTERVAL_MS)
            elapsed += FORCE_RESTART_POLL_INTERVAL_MS
        }
    }

    override fun onCleared() {
        adbMdns?.stop()
        mdnsFallbackJob?.cancel()
        super.onCleared()
    }
}
