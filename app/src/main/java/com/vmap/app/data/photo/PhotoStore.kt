package com.vmap.app.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.vmap.app.domain.model.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.math.max

/**
 * 照片落盘。数据库只存相对路径，文件按年月分目录，避免单目录上万文件。
 */
class PhotoStore(private val context: Context) {

    fun absoluteFile(relativePath: String): File = File(context.filesDir, relativePath)

    /**
     * 把用户选中的图片导入到应用私有目录，压到长边 [MAX_EDGE]，质量 85。
     * 导入失败返回 null，调用方决定是否提示。
     */
    suspend fun import(uri: Uri, spotId: String): Photo? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: return@withContext null

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            val opts = BitmapFactory.Options().apply {
                inSampleSize = calcSampleSize(bounds.outWidth, bounds.outHeight)
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return@withContext null

            val scaled = scaleToMaxEdge(decoded)

            val relative = newRelativePath()
            val target = absoluteFile(relative)
            target.parentFile?.mkdirs()
            target.outputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            }

            Photo(
                spotId = spotId,
                path = relative,
                width = scaled.width,
                height = scaled.height,
                takenAt = System.currentTimeMillis(),
            ).also {
                if (scaled !== decoded) scaled.recycle()
                decoded.recycle()
            }
        }.getOrNull()
    }

    suspend fun delete(relativePath: String) = withContext(Dispatchers.IO) {
        runCatching { absoluteFile(relativePath).delete() }
        Unit
    }

    private fun newRelativePath(): String {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = String.format(Locale.US, "%02d", cal.get(Calendar.MONTH) + 1)
        return "photos/$year/$month/${UUID.randomUUID()}.jpg"
    }

    private fun calcSampleSize(width: Int, height: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (max(w, h) / 2 >= MAX_EDGE) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleToMaxEdge(source: Bitmap): Bitmap {
        val longest = max(source.width, source.height)
        if (longest <= MAX_EDGE) return source
        val ratio = MAX_EDGE.toFloat() / longest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt(),
            (source.height * ratio).toInt(),
            true,
        )
    }

    private companion object {
        const val MAX_EDGE = 2048
        const val QUALITY = 85
    }
}
