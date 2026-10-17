package xyz.tracel.imtrack.settings

import android.database.SQLException
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import xyz.tracel.imtrack.backup.Backup
import xyz.tracel.imtrack.backup.BackupDecoding
import xyz.tracel.imtrack.backup.BackupError
import xyz.tracel.imtrack.backup.BackupFiles
import xyz.tracel.imtrack.backup.BackupFormat
import xyz.tracel.imtrack.backup.BackupRepository
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** A valid backup waiting for the `Replace all data?` dialog. Counts leave out soft-deleted rows. */
data class ImportConfirm(val projectCount: Int, val sessionCount: Int)

/** Snackbar confirmations; the screen turns them into copy. */
sealed interface SettingsMessage {
    data class Exported(val projectCount: Int, val sessionCount: Int) : SettingsMessage
    data class ExportFailed(val reason: String?) : SettingsMessage
    data class Imported(val projectCount: Int, val sessionCount: Int) : SettingsMessage
    data class ImportFailed(val error: BackupError) : SettingsMessage
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val files: BackupFiles,
    private val clock: Clock,
) : ViewModel() {
    private var pendingImport: Backup? = null
    private val _importConfirm = MutableStateFlow<ImportConfirm?>(null)
    val importConfirm: StateFlow<ImportConfirm?> = _importConfirm.asStateFlow()
    private val _messages = Channel<SettingsMessage>(Channel.BUFFERED)
    val messages: Flow<SettingsMessage> = _messages.receiveAsFlow()

    /** The name the "create document" picker suggests, such as `imtrack-backup-2026-10-04.json`. */
    fun exportFileName(): String = "imtrack-backup-${LocalDate.now(clock)}.json"

    /** Writes every row to the document the picker returned. */
    fun export(uri: Uri) {
        viewModelScope.launch {
            val message = try {
                val backup = backupRepository.snapshot()
                files.write(uri, BackupFormat.encode(backup))
                SettingsMessage.Exported(backup.liveProjectCount, backup.liveSessionCount)
            } catch (e: IOException) {
                SettingsMessage.ExportFailed(e.message)
            } catch (e: SecurityException) {
                SettingsMessage.ExportFailed(e.message)
            } catch (e: SQLException) {
                SettingsMessage.ExportFailed(e.message)
            }
            _messages.send(message)
        }
    }

    /** Reads and validates the document the picker returned; a valid one opens the confirm dialog. */
    fun import(uri: Uri) {
        viewModelScope.launch {
            val decoding = try {
                BackupFormat.decode(files.read(uri))
            } catch (e: IOException) {
                BackupDecoding.Invalid(BackupError.Failed(e.message))
            } catch (e: SecurityException) {
                BackupDecoding.Invalid(BackupError.Failed(e.message))
            }
            when (decoding) {
                is BackupDecoding.Invalid -> _messages.send(SettingsMessage.ImportFailed(decoding.error))
                is BackupDecoding.Valid -> {
                    pendingImport = decoding.backup
                    _importConfirm.value = ImportConfirm(decoding.backup.liveProjectCount, decoding.backup.liveSessionCount)
                }
            }
        }
    }

    fun cancelImport() {
        pendingImport = null
        _importConfirm.value = null
    }

    /** Replace in the confirm dialog: swaps every row for the file's. */
    fun confirmImport() {
        val backup = pendingImport ?: return
        cancelImport()
        viewModelScope.launch {
            // The Stopwatch (#11) must stop here without saving.
            val message = try {
                backupRepository.replaceAll(backup)
                SettingsMessage.Imported(backup.liveProjectCount, backup.liveSessionCount)
            } catch (e: SQLException) {
                // The transaction rolled back, so the old data is untouched.
                SettingsMessage.ImportFailed(BackupError.Failed(e.message))
            }
            _messages.send(message)
        }
    }
}
