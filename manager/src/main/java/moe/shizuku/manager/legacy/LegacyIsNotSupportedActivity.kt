package moe.shizuku.manager.legacy

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import moe.shizuku.manager.MainActivity
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import rikka.html.text.HtmlCompat

class LegacyIsNotSupportedActivity : AppActivity() {

    companion object {

        /**
         * Activity result: user denied request (only API pre-23).
         */
        private inline val RESULT_CANCELED get() = Activity.RESULT_CANCELED

        /**
         * Activity result: error, such as manager app itself not authorized.
         */
        private const val RESULT_ERROR = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callingComponent = callingActivity
        if (callingComponent == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val ai = try {
            packageManager.getApplicationInfo(callingComponent.packageName, PackageManager.GET_META_DATA)
        } catch (e: Throwable) {
            finish()
            return
        }

        val label = try {
            ai.loadLabel(packageManager)
        } catch (e: Exception) {
            ai.packageName
        }

        val v3Support = ai.metaData?.getBoolean("moe.shizuku.client.V3_SUPPORT") == true
        if (v3Support) {
            setContent {
                LegacyNoticeComposeScreen(
                    title = getString(R.string.dialog_requesting_legacy_title, label),
                    message = HtmlCompat.fromHtml(
                        getString(R.string.dialog_requesting_legacy_message, label),
                        HtmlCompat.FROM_HTML_OPTION_TRIM_WHITESPACE
                    ).toString(),
                    primaryLabel = getString(android.R.string.ok),
                    secondaryLabel = getString(R.string.dialog_requesting_legacy_button_open_shizuku),
                    onPrimary = {
                        setResult(RESULT_ERROR)
                        finish()
                    },
                    onSecondary = {
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                )
            }
        } else {
            setContent {
                LegacyNoticeComposeScreen(
                    title = getString(R.string.dialog_legacy_not_support_title, label),
                    message = HtmlCompat.fromHtml(
                        getString(R.string.dialog_legacy_not_support_message, label),
                        HtmlCompat.FROM_HTML_OPTION_TRIM_WHITESPACE
                    ).toString(),
                    primaryLabel = getString(android.R.string.ok),
                    onPrimary = {
                        setResult(RESULT_ERROR)
                        finish()
                    }
                )
            }
        }
    }
}
