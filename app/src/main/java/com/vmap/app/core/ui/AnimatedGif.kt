package com.vmap.app.core.ui

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 播 assets 里的 GIF。
 *
 * 项目没引 coil-gif（多一个依赖不值），直接用系统解码：API 28+ 走 AnimatedImageDrawable
 * 能真播动图，更老的系统退化成首帧静图——不至于空着。
 */
@Composable
fun AnimatedGif(assetPath: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = {
            ImageView(it).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    runCatching {
                        val source = ImageDecoder.createSource(context.assets, assetPath)
                        val drawable = ImageDecoder.decodeDrawable(source)
                        (drawable as? AnimatedImageDrawable)?.repeatCount =
                            AnimatedImageDrawable.REPEAT_INFINITE
                        setImageDrawable(drawable)
                        (drawable as? AnimatedImageDrawable)?.start()
                        true
                    }.getOrDefault(false)
                } else {
                    false
                }
                if (!ok) {
                    runCatching {
                        context.assets.open(assetPath).use { input ->
                            setImageBitmap(BitmapFactory.decodeStream(input))
                        }
                    }
                }
            }
        },
    )
}
