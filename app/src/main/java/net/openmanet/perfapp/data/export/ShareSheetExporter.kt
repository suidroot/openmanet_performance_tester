package net.openmanet.perfapp.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands exported CSVs to Android's share sheet via a FileProvider content:// URI - the
 * always-available, fully-offline export path (save to a USB drive, share over Bluetooth/local
 * file manager, no internet needed), independent of UploadService's optional cloud upload.
 */
@Singleton
class ShareSheetExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun share(files: List<File>) {
        if (files.isEmpty()) return
        val uris = ArrayList(files.map { file ->
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/csv"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Export session data").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
