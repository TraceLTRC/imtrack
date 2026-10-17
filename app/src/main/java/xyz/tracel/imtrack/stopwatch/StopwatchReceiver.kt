package xyz.tracel.imtrack.stopwatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import xyz.tracel.imtrack.di.ApplicationScope
import javax.inject.Inject

/**
 * The Stopwatch notification's Stop action and delete intent, plus BOOT_COMPLETED and app updates,
 * which clear notifications. Works with the app closed.
 */
@AndroidEntryPoint
class StopwatchReceiver : BroadcastReceiver() {
    @Inject lateinit var notifier: StopwatchNotifier

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val work: suspend () -> Unit = when (intent.action) {
            ActionStop -> notifier::stopFromNotification
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> notifier::reconcile
            else -> return
        }
        val pending = goAsync()
        scope.launch {
            try {
                work()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ActionStop = "xyz.tracel.imtrack.action.STOP_STOPWATCH"
    }
}
