package xyz.tracel.imtrack

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import xyz.tracel.imtrack.di.ApplicationScope
import xyz.tracel.imtrack.stopwatch.StopwatchNotifier
import javax.inject.Inject

@HiltAndroidApp
class ImtrackApplication : Application() {
    @Inject lateinit var stopwatchNotifier: StopwatchNotifier

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Reconciles on every app start, then on every Stopwatch change.
        stopwatchNotifier.watch(scope)
    }
}
