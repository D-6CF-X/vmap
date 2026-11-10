package com.vmap.app.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * 侧边栏壁纸的本地存储：裁剪结果存成 PNG 放在应用私有目录，
 * 不进相册、不占 SharedPreferences，换机/清数据自然消失。
 */
object DrawerWallpaper {

    private const val FILE_NAME = "drawer_wallpaper.png"

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun has(context: Context): Boolean = file(context).exists()

    fun load(context: Context): ImageBitmap? = runCatching {
        if (!has(context)) return null
        val source = ImageDecoder.createSource(file(context))
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        bitmap.asImageBitmap()
    }.getOrNull()

    fun save(context: Context, bitmap: Bitmap) {
        file(context).outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
