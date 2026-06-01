package moe.shizuku.manager.starter

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import moe.shizuku.manager.AppConstants.EXTRA
import moe.shizuku.manager.R
import moe.shizuku.manager.adb.AdbKeyException
import moe.shizuku.manager.adb.AdbWirelessHelper
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.databinding.StarterActivityBinding
import rikka.lifecycle.Resource
import rikka.lifecycle.Status
import rikka.lifecycle.viewModels
import rikka.shizuku.Shizuku
import java.net.ConnectException
import javax.net.ssl.SSLProtocolException

private class NotRootedException : Exception()

class StarterActivity : AppBarActivity() {

    companion object {
        const val EXTRA_IS_ROOT = "$EXTRA.IS_ROOT"
        const val EXTRA_HOST = "$EXTRA.HOST"
        const val EXTRA_PORT = "$EXTRA.PORT"
        const val EXTRA_FORCE_RESTART = "$EXTRA.FORCE_RESTART"
    }

    private var waitingForServiceListener: Shizuku.OnBinderReceivedListener? = null

    private val viewModel by viewModels {
        ViewModel(
            this,
            intent.getBooleanExtra(EXTRA_IS_ROOT, true),
            intent.getStringExtra(EXTRA_HOST),
            intent.getIntExtra(EXTRA_PORT, 0)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close_24)

        val binding = StarterActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel.output.observe(this) {
            val output = it.data!!.trim()
            if (output.endsWith("info: shizuku_starter exit with 0")) {
                beginWaitingForService()
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

                if (message != 0) {
                    MaterialAlertDialogBuilder(this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
            binding.text1.text = output
        }
    }

    override fun onDestroy() {
        waitingForServiceListener?.let(Shizuku::removeBinderReceivedListener)
        waitingForServiceListener = null
        super.onDestroy()
    }

    private fun beginWaitingForService() {
        if (waitingForServiceListener != null) {
            return
        }

        viewModel.appendOutput("")
        viewModel.appendOutput("Waiting for service...")

        val listener = object : Shizuku.OnBinderReceivedListener {
            override fun onBinderReceived() {
                completeServiceStart(this)
            }
        }
        waitingForServiceListener = listener

        if (Shizuku.pingBinder()) {
            completeServiceStart(listener)
            return
        }

        Shizuku.addBinderReceivedListenerSticky(listener)
    }

    private fun completeServiceStart(listener: Shizuku.OnBinderReceivedListener) {
        if (waitingForServiceListener !== listener) {
            return
        }

        Shizuku.removeBinderReceivedListener(listener)
        waitingForServiceListener = null
        viewModel.appendOutput("Service started, this window will be automatically closed in 3 seconds")

        window?.decorView?.postDelayed({
            if (!isFinishing) finish()
        }, 3000)
    }

}

private class ViewModel(
    context: Context,
    root: Boolean,
    host: String?,
    port: Int
) : androidx.lifecycle.ViewModel() {
    private val sb = StringBuilder()
    private val _output = MutableLiveData<Resource<StringBuilder>>()
    private val adbWirelessHelper = AdbWirelessHelper()

    val output = _output as LiveData<Resource<StringBuilder>>

    init {
        context.applicationContext
        try {
            if (root) {
                startRoot()
            } else {
                startAdb(host!!, port)
            }
        } catch (e: Throwable) {
            postResult(e)
        }
    }

    fun appendOutput(line: String) {
        sb.appendLine(line)
        postResult()
    }

    private fun postResult(throwable: Throwable? = null) {
        if (throwable == null) {
            _output.postValue(Resource.success(sb))
        } else {
            _output.postValue(Resource.error(throwable, sb))
        }
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
        sb.append("Starting with wireless adb in port $port...").append('\n').append('\n')
        postResult()

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
}
