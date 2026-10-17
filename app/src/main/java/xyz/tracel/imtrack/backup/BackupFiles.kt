package xyz.tracel.imtrack.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** Reads and writes the documents the system file pickers return (Storage Access Framework). */
class BackupFiles @Inject constructor(@param:ApplicationContext private val context: Context) {
    suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        // "wt" truncates, so overwriting a longer file leaves no old bytes behind.
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("can't open the file")
        stream.use { it.write(text.toByteArray()) }
    }

    suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("can't open the file")
        stream.use { it.readBytes().decodeToString() }
    }
}
