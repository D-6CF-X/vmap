package com.vmap.app.data.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/** 把分享卡片保存到相册并弹出系统分享。 */
object ShareHelper {

    fun saveAndShare(context: Context, bitmap: Bitmap, title: String) {
        val uri = if (Build.VERSION.SDK_INT >= 29) {
            saveToMediaStore(context, bitmap, title)
        } else {
            saveToCache(context, bitmap, title)
        }
        uri?.let { share(context, it, title) }
    }

    private fun saveToMediaStore(context: Context, bitmap: Bitmap, title: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$title.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/V-Map")
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = context.contentResolver.insert(collection, values) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        return uri
    }

    private fun saveToCache(context: Context, bitmap: Bitmap, title: String): Uri {
        val file = File(context.cacheDir, "$title.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun share(context: Context, uri: Uri, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
}
