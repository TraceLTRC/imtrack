package xyz.tracel.imtrack.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ReminderSettingsTest {
    @get:Rule val folder = TemporaryFolder()
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    @Test
    fun theDefaultIsFourHours() = runTest {
        assertEquals(4, settings(folder.root.resolve("a.preferences_pb")).reminderAfterHours.first())
    }

    @Test
    fun aValueSurvivesARestart() = runTest {
        val file = folder.root.resolve("b.preferences_pb")
        assertTrue(settings(file).setReminderAfterHours(7))
        scopes.forEach { it.cancel() }

        assertEquals(7, settings(file).reminderAfterHours.first())
    }

    @Test
    fun valuesUnderOneAreRejected() = runTest {
        val settings = settings(folder.root.resolve("c.preferences_pb"))
        settings.setReminderAfterHours(2)

        assertFalse(settings.setReminderAfterHours(0))
        assertFalse(settings.setReminderAfterHours(-3))

        assertEquals(2, settings.reminderAfterHours.first())
    }

    /** A fresh DataStore on [file], as a new process would open it. */
    private fun settings(file: File): ReminderSettings {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        return ReminderSettings(PreferenceDataStoreFactory.create(scope = scope) { file })
    }
}
