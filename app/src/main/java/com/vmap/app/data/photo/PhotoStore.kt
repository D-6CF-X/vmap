package com.vmap.app.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
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
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                importWithImageDecoder(uri, spotId)
            } else {
                importWithBitmapFactory(uri, spotId)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "照片导入失败 uri=$uri", e)
            null
        }
    }

    /**
     * ImageDecoder（API 28+）：对 HEIF/HEIC、WebP、EXIF 旋转都支持，比 BitmapFactory 可靠。
     * 现代手机默认存 HEIF，BitmapFactory 解不了就报「照片添加失败」。
     */
    private fun importWithImageDecoder(uri: Uri, spotId: String): Photo? {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val longest = max(w, h)
            if (longest > MAX_EDGE) {
                val ratio = MAX_EDGE.toFloat() / longest
                decoder.setTargetSize((w * ratio).toInt(), (h * ratio).toInt())
            }
        }
        return writeToDisk(bitmap, spotId)
    }

    /** API 26-27 兜底：BitmapFactory + 手动采样。 */
    private fun importWithBitmapFactory(uri: Uri, spotId: String): Photo? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val ins = context.contentResolver.openInputStream(uri)
            ?: run { Log.e(TAG, "无法打开 uri=$uri"); return null }
        ins.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.e(TAG, "解码失败 bounds=${bounds.outWidth}x${bounds.outHeight}（格式可能不支持）")
            return null
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = calcSampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: run { Log.e(TAG, "二次解码失败 uri=$uri"); return null }

        val scaled = scaleToMaxEdge(decoded)
        val photo = writeToDisk(scaled, spotId)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        return photo
    }

    private fun writeToDisk(bitmap: Bitmap, spotId: String): Photo? {
        val relative = newRelativePath()
        val target = absoluteFile(relative)
        target.parentFile?.mkdirs()
        target.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        }
        return Photo(
            spotId = spotId,
            path = relative,
            width = bitmap.width,
            height = bitmap.height,
            takenAt = System.currentTimeMillis(),
        )
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
        const val TAG = "PhotoStore"
        const val MAX_EDGE = 2048
        const val QUALITY = 85
    }
}
