package com.vmap.app.data.song

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * 曲绘落盘。数据库只存相对路径，文件按歌曲 id 命名，重选封面直接覆盖，不留垃圾。
 *
 * 解码优先走 ImageDecoder（HEIF/HEIC、WebP、EXIF 旋转都认），老系统退回 BitmapFactory。
 */
class CoverStore(private val context: Context) {

    fun absoluteFile(relativePath: String): File = File(context.filesDir, relativePath)

    /** 把选中的图片压到长边 [MAX_EDGE] 存成 JPEG，返回相对路径；失败返回 null。 */
    suspend fun import(uri: Uri, songId: String): String? = withContext(Dispatchers.IO) {
        try {
            val bitmap = (if (Build.VERSION.SDK_INT >= 28) {
                decodeWithImageDecoder(uri)
            } else {
                decodeWithFactory(uri)
            }) ?: return@withContext null
            val relative = "covers/$songId.jpg"
            val target = absoluteFile(relative)
            target.parentFile?.mkdirs()
            target.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            }
            if (!bitmap.isRecycled) bitmap.recycle()
            relative
        } catch (e: Throwable) {
            Log.e(TAG, "封面导入失败 uri=$uri", e)
            null
        }
    }

    /**
     * 把内置 assets 里的曲绘复制成正式的封面文件（首次导入巡演曲目时用）。
     * 复制的目的只有一个：让后面的加载路径和用户手动选的图完全一致。
     */
    suspend fun importAsset(
        assetPath: String,
        songId: String,
        force: Boolean = false,
    ): String? = withContext(Dispatchers.IO) {
        if (assetPath.isBlank()) return@withContext null
        val relative = "covers/$songId.jpg"
        val target = absoluteFile(relative)
        if (!force && target.exists() && target.length() > 0) return@withContext relative
        runCatching {
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { out -> input.copyTo(out) }
            }
            relative
        }.getOrElse {
            Log.e(TAG, "内置曲绘导入失败 asset=$assetPath", it)
            null
        }
    }

    suspend fun load(relativePath: String): ImageBitmap? = withContext(Dispatchers.IO) {        if (relativePath.isBlank()) return@withContext null
        val file = absoluteFile(relativePath)
        if (!file.exists()) return@withContext null
        runCatching {
            val bitmap = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                BitmapFactory.decodeFile(file.absolutePath)
            }
            bitmap?.asImageBitmap()
        }.getOrNull()
    }

    suspend fun delete(relativePath: String) = withContext(Dispatchers.IO) {
        if (relativePath.isNotBlank()) runCatching { absoluteFile(relativePath).delete() }
        Unit
    }

    private fun decodeWithImageDecoder(uri: Uri): Bitmap? {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val longest = max(w, h)
            if (longest > MAX_EDGE) {
                val ratio = MAX_EDGE.toFloat() / longest
                decoder.setTargetSize((w * ratio).toInt(), (h * ratio).toInt())
            }
        }
    }

    private fun decodeWithFactory(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        val longest = max(decoded.width, decoded.height)
        if (longest <= MAX_EDGE) return decoded
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * MAX_EDGE / longest.toFloat()).toInt(),
            (decoded.height * MAX_EDGE / longest.toFloat()).toInt(),
            true,
        )
        decoded.recycle()
        return scaled
    }

    private companion object {
        const val TAG = "CoverStore"
        const val MAX_EDGE = 900
        const val QUALITY = 88
    }
}
