package xyz.tracel.imtrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import xyz.tracel.imtrack.navigation.ImtrackApp
import xyz.tracel.imtrack.ui.theme.ImtrackTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ImtrackTheme {
                ImtrackApp()
            }
        }
    }
}
