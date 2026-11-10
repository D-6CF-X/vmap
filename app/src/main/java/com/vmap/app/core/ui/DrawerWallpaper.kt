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
 *
 * 按板块分开存（tianyi / miku / all），每个板块可以有自己的壁纸。
 */
object DrawerWallpaper {

    private fun file(context: Context, key: String): File =
        File(context.filesDir, "drawer_wallpaper_$key.png")

    fun has(context: Context, key: String): Boolean = file(context, key).exists()

    fun load(context: Context, key: String): ImageBitmap? = runCatching {
        if (!has(context, key)) return null
        val source = ImageDecoder.createSource(file(context, key))
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        bitmap.asImageBitmap()
    }.getOrNull()

    fun save(context: Context, key: String, bitmap: Bitmap) {
        file(context, key).outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    fun clear(context: Context, key: String) {
        runCatching { file(context, key).delete() }
    }
}
