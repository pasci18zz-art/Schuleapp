package de.schulplaner.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/** Speichert Fotos und Dateien im App-Speicher. Fotos werden verkleinert. */
class AttachmentStore(private val context: Context) {
    private val dir = File(context.filesDir, "attachments").apply { mkdirs() }
    private val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
    private val authority = "${context.packageName}.files"

    fun fileFor(a: Attachment) = File(dir, a.fileName)

    fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, authority, file)

    /** Neue leere Datei + Uri für die Kamera-App. */
    fun newCameraTarget(): Pair<File, Uri> {
        val f = File(cameraDir, "foto_${System.currentTimeMillis()}.jpg")
        return f to uriFor(f)
    }

    suspend fun importCamera(file: File, ownerType: String, ownerId: String): Attachment? = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) return@withContext null
        val id = newId()
        val target = File(dir, "$id.jpg")
        runCatching { compressImage({ file.inputStream() }, target) }.getOrElse { return@withContext null }
        file.delete()
        Attachment(
            id = id, ownerType = ownerType, ownerId = ownerId, fileName = target.name,
            displayName = "Foto", mimeType = "image/jpeg", createdAt = System.currentTimeMillis(),
        )
    }

    suspend fun importUri(uri: Uri, ownerType: String, ownerId: String): Attachment? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "Datei"
        val id = newId()
        runCatching {
            if (mime.startsWith("image/") && mime != "image/gif") {
                val target = File(dir, "$id.jpg")
                compressImage({ resolver.openInputStream(uri) ?: error("Datei nicht lesbar") }, target)
                Attachment(
                    id = id, ownerType = ownerType, ownerId = ownerId, fileName = target.name,
                    displayName = name, mimeType = "image/jpeg", createdAt = System.currentTimeMillis(),
                )
            } else {
                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                    ?: name.substringAfterLast('.', "bin")
                val target = File(dir, "$id.$ext")
                resolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                    ?: error("Datei nicht lesbar")
                Attachment(
                    id = id, ownerType = ownerType, ownerId = ownerId, fileName = target.name,
                    displayName = name, mimeType = mime, createdAt = System.currentTimeMillis(),
                )
            }
        }.getOrNull()
    }

    fun open(context: Context, a: Attachment) {
        val file = fileFor(a)
        if (!file.exists()) {
            Toast.makeText(context, "Datei ist auf diesem Gerät nicht vorhanden", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uriFor(file), a.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Keine App zum Öffnen gefunden", Toast.LENGTH_SHORT).show()
        }
    }

    private fun compressImage(open: () -> InputStream, out: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
        val bitmap = open().use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Bild nicht lesbar")
        val orientation = runCatching {
            open().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val rotated = if (degrees != 0f) {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
        } else {
            bitmap
        }
        FileOutputStream(out).use { rotated.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    }
}
