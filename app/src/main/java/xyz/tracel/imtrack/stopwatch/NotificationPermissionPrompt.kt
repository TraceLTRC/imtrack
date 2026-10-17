package xyz.tracel.imtrack.stopwatch

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The notification permission is asked once, on the first ▶; after that, never again on our own. */
class NotificationPermissionPrompt @Inject constructor(@param:ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("stopwatch", Context.MODE_PRIVATE)

    /** True only the first time it's called, ever: then it's time to ask. */
    fun shouldAskOnce(): Boolean {
        if (prefs.getBoolean(Asked, false)) return false
        prefs.edit { putBoolean(Asked, true) }
        return true
    }

    private companion object {
        const val Asked = "notification_permission_asked"
    }
}
