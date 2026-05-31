package moe.shizuku.manager.settings

import android.os.Bundle
import androidx.activity.compose.setContent
import moe.shizuku.manager.app.AppActivity

class SettingsActivity : AppActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SettingsComposeScreen(
                onNavigateUp = { finish() },
                onRecreateRequested = { recreate() }
            )
        }
    }
}
