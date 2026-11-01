package org.dyslectoreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import org.dyslectoreader.ui.AboutScreen
import org.dyslectoreader.ui.HomeScreen
import org.dyslectoreader.ui.SettingsScreen
import org.dyslectoreader.ui.theme.DyslectoReaderTheme

enum class Screen { Home, Settings, About }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DyslectoReaderTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.Home) }
                BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }
                when (screen) {
                    Screen.Home -> HomeScreen(
                        onOpenSettings = { screen = Screen.Settings },
                        onOpenAbout = { screen = Screen.About },
                    )
                    Screen.Settings -> SettingsScreen(
                        onBack = { screen = Screen.Home },
                        onOpenAbout = { screen = Screen.About },
                    )
                    Screen.About -> AboutScreen(onBack = { screen = Screen.Home })
                }
            }
        }
    }
}
