package xyz.tracel.imtrack.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The target of Undo last Session: the ID of the Session most recently created in this app run.
 * Held in memory only, so restarting the app clears it.
 */
@Singleton
class LastCreatedSession @Inject constructor() {
    private val _id = MutableStateFlow<String?>(null)
    val id: StateFlow<String?> = _id.asStateFlow()

    fun set(id: String) {
        _id.value = id
    }

    /** Returns the target and clears it, so Undo is one step only. */
    fun take(): String? = _id.getAndUpdate { null }

    fun clear() {
        _id.value = null
    }
}
