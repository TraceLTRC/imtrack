package xyz.tracel.imtrack.stopwatch

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationPermissionPromptTest {
    @Test
    fun onlyTheFirstAskEverIsTaken() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val first = NotificationPermissionPrompt(context).shouldAskOnce()
        // A new instance, as after an app restart.
        val later = List(2) { NotificationPermissionPrompt(context).shouldAskOnce() }

        assertEquals(listOf(true, false, false), listOf(first) + later)
    }
}
