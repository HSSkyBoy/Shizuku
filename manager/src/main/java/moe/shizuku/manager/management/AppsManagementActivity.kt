package moe.shizuku.manager.management

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import android.widget.Toast
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.authorization.AuthorizationManager
import rikka.lifecycle.Status
import rikka.shizuku.Shizuku
import java.util.Objects

class AppsManagementActivity : AppActivity() {

    private val viewModel by appsViewModel()

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        if (!isFinishing) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Shizuku.pingBinder()) {
            finish()
            return
        }

        if (viewModel.packages.value == null) {
            viewModel.load()
        }

        viewModel.packages.observe(this) {
            if (it.status == Status.ERROR) {
                finish()
                val tr = it.error
                Toast.makeText(this, Objects.toString(tr, "unknown"), Toast.LENGTH_SHORT).show()
                tr.printStackTrace()
            }
        }

        setContent {
            val packagesState by viewModel.packages.observeAsState()
            ApplicationManagementComposeScreen(
                packages = packagesState?.data ?: emptyList(),
                onNavigateUp = { finish() },
                onTogglePackage = { packageInfo ->
                    val applicationInfo = packageInfo.applicationInfo ?: return@ApplicationManagementComposeScreen ToggleResult.Success
                    try {
                        val uid = applicationInfo.uid
                        if (AuthorizationManager.granted(packageInfo.packageName, uid)) {
                            AuthorizationManager.revoke(packageInfo.packageName, uid)
                        } else {
                            AuthorizationManager.grant(packageInfo.packageName, uid)
                        }
                        viewModel.load(true)
                        ToggleResult.Success
                    } catch (e: SecurityException) {
                        val shizukuUid = try {
                            Shizuku.getUid()
                        } catch (_: Throwable) {
                            return@ApplicationManagementComposeScreen ToggleResult.Success
                        }
                        if (shizukuUid != 0) {
                            ToggleResult.AdbLimited
                        } else {
                            ToggleResult.Success
                        }
                    }
                }
            )
        }

        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    override fun onDestroy() {
        super.onDestroy()

        Shizuku.removeBinderDeadListener(binderDeadListener)
    }

}
