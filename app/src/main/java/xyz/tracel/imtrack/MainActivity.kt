package xyz.tracel.imtrack

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import xyz.tracel.imtrack.navigation.ImtrackApp
import xyz.tracel.imtrack.stopwatch.StopwatchNotifier
import xyz.tracel.imtrack.ui.theme.ImtrackTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var stopwatchNotifier: StopwatchNotifier

    /** Taps on the Stopwatch notification while the app is already open. */
    private val openHomeRequests = Channel<Unit>(Channel.CONFLATED)
    private val openHomeFlow = openHomeRequests.receiveAsFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ImtrackTheme {
                ImtrackApp(openHomeRequests = openHomeFlow)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ActionOpenHome) openHomeRequests.trySend(Unit)
    }

    override fun onResume() {
        super.onResume()
        // Notifications may have been allowed meanwhile (the permission dialog, or system settings).
        lifecycleScope.launch { stopwatchNotifier.reconcile() }
    }

    companion object {
        /** Opens the app on Home; a fresh start lands there anyway. */
        const val ActionOpenHome = "xyz.tracel.imtrack.action.OPEN_HOME"
    }
}
