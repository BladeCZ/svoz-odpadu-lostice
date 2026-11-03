package cz.lostice.svozodpadu

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import cz.lostice.svozodpadu.data.WasteRepository
import cz.lostice.svozodpadu.model.AppThemeMode
import cz.lostice.svozodpadu.ui.MainScreen
import cz.lostice.svozodpadu.ui.theme.SvozOdpaduTheme
import cz.lostice.svozodpadu.worker.WasteNotificationWorker

class MainActivity : ComponentActivity() {

    private lateinit var repository: WasteRepository
    private var themeModeState by mutableStateOf(AppThemeMode.SYSTEM)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        WasteNotificationWorker.schedule(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = WasteRepository(applicationContext)
        themeModeState = repository.getThemeMode()

        // Pokud uživatel otevřel aplikaci kliknutím na (případně trvalé) oznámení, oznámení zrušíme
        repository.cancelActiveNotifications()

        requestNotificationPermissionIfNeeded()
        WasteNotificationWorker.schedule(applicationContext)

        setContent {
            SvozOdpaduTheme(themeMode = themeModeState) {
                MainScreen(
                    repository = repository,
                    currentThemeMode = themeModeState,
                    onThemeModeChange = { newMode ->
                        themeModeState = newMode
                        repository.setThemeMode(newMode)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (::repository.isInitialized) {
            repository.cancelActiveNotifications()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::repository.isInitialized) {
            repository.cancelActiveNotifications()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
