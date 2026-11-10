package com.vmap.app.feature.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect as AndroidRect
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 取景框在屏幕上的几何信息，确认时据此反算原图区域。 */
private data class CropGeometry(
    val frame: Rect = Rect.Zero,
    val center: Offset = Offset.Zero,
    val effectiveScale: Float = 1f,
)

/**
 * 侧栏壁纸裁剪：固定 3:4 竖向取景框，拖动平移、双指缩放，确认后按框内内容生成图片。
 *
 * 顶栏 + 中间取景区 + 底部提示三段式，取景框在中间区域里居中，不会被系统栏挤偏。
 */
@Composable
fun WallpaperCropDialog(
    source: ImageBitmap,
    accent: Color,
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
) {
    var geometry by remember { mutableStateOf(CropGeometry()) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var minScale by remember { mutableFloatStateOf(1f) }
    var ready by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF080A0E))
                .statusBarsPadding(),
        ) {
            // 顶栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "取消",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Text(
                    text = "裁剪侧栏壁纸",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                )
                Text(
                    text = "完成",
                    color = Color(0xFF05222E),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (ready) accent else accent.copy(alpha = 0.4f))
                        .clickable(enabled = ready) {
                            onConfirm(cropToBitmap(source, geometry, offset))
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // 取景区：占满中间剩余空间，取景框在其中居中
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val w = constraints.maxWidth.toFloat()
                val h = constraints.maxHeight.toFloat()
                val iw = source.width.toFloat()
                val ih = source.height.toFloat()

                // Image 用 Crop 铺满这个区域，作为缩放的基准比例
                val cover = max(w / iw, h / ih)
                // 取景框：3:4，宽占 78%，高度不超过区域的 88%
                val frameW = min(w * 0.78f, h * 0.88f * 3f / 4f)
                val frameH = frameW * 4f / 3f
                val frame = Rect(
                    Offset((w - frameW) / 2f, (h - frameH) / 2f),
                    Size(frameW, frameH),
                )
                val minS = (max(frameW / iw, frameH / ih) / cover).coerceAtMost(1f)

                if (!ready) {
                    // 首帧把初始缩放设成"正好盖住取景框"
                    minScale = minS
                    scale = minS
                    ready = true
                }
                SideEffect {
                    val next = CropGeometry(frame, Offset(w / 2f, h / 2f), cover * scale)
                    if (next != geometry) geometry = next
                }

                fun clamp(tx: Float, ty: Float, k: Float): Offset {
                    val halfW = iw * cover * k / 2f
                    val halfH = ih * cover * k / 2f
                    val bx1 = frame.left - w / 2f + halfW
                    val bx2 = frame.right - w / 2f - halfW
                    val by1 = frame.top - h / 2f + halfH
                    val by2 = frame.bottom - h / 2f - halfH
                    return Offset(
                        tx.coerceIn(min(bx1, bx2), max(bx1, bx2)),
                        ty.coerceIn(min(by1, by2), max(by1, by2)),
                    )
                }

                Image(
                    bitmap = source,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val next = (scale * zoom).coerceIn(minScale, minScale * 6f)
                                val k = next / scale
                                val moved = Offset(
                                    centroid.x - w / 2f - (centroid.x - w / 2f - offset.x) * k,
                                    centroid.y - h / 2f - (centroid.y - h / 2f - offset.y) * k,
                                ) + pan
                                scale = next
                                offset = clamp(moved.x, moved.y, next)
                            }
                        },
                )

                // 取景框外压暗 + 白色框线与四角
                ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                    val shade = Color.Black.copy(alpha = 0.62f)
                    drawRect(shade, topLeft = Offset(0f, 0f), size = Size(size.width, frame.top))
                    drawRect(
                        shade,
                        topLeft = Offset(0f, frame.bottom),
                        size = Size(size.width, size.height - frame.bottom),
                    )
                    drawRect(shade, topLeft = Offset(0f, frame.top), size = Size(frame.left, frame.height))
                    drawRect(
                        shade,
                        topLeft = Offset(frame.right, frame.top),
                        size = Size(size.width - frame.right, frame.height),
                    )
                    drawRect(
                        color = Color.White.copy(alpha = 0.9f),
                        topLeft = frame.topLeft,
                        size = frame.size,
                        style = Stroke(width = 2f),
                    )
                    val arm = min(frame.width, frame.height) * 0.11f
                    val corners = listOf(
                        Triple(Offset(frame.left, frame.top), 1f, 1f),
                        Triple(Offset(frame.right, frame.top), -1f, 1f),
                        Triple(Offset(frame.left, frame.bottom), 1f, -1f),
                        Triple(Offset(frame.right, frame.bottom), -1f, -1f),
                    )
                    corners.forEach { (corner, dx, dy) ->
                        drawLine(Color.White, corner, Offset(corner.x + arm * dx, corner.y), strokeWidth = 5f)
                        drawLine(Color.White, corner, Offset(corner.x, corner.y + arm * dy), strokeWidth = 5f)
                    }
                }
            }

            Text(
                text = "拖动调整位置 · 双指缩放",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 22.dp, top = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** 把取景框内的画面画到目标 Bitmap（3:4，输出 900×1200）。 */
private fun cropToBitmap(
    source: ImageBitmap,
    geometry: CropGeometry,
    offset: Offset,
): Bitmap {
    val frame = geometry.frame
    val iw = source.width.toFloat()
    val ih = source.height.toFloat()
    val cx = geometry.center.x + offset.x
    val cy = geometry.center.y + offset.y
    val s = geometry.effectiveScale

    fun srcX(sx: Float) = ((sx - cx) / s + iw / 2f).coerceIn(0f, iw)
    fun srcY(sy: Float) = ((sy - cy) / s + ih / 2f).coerceIn(0f, ih)

    val left = srcX(frame.left).roundToInt()
    val top = srcY(frame.top).roundToInt()
    val right = srcX(frame.right).roundToInt().coerceAtLeast(left + 1)
    val bottom = srcY(frame.bottom).roundToInt().coerceAtLeast(top + 1)

    val outW = 900
    val outH = 1200
    val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    canvas.drawBitmap(
        source.asAndroidBitmap(),
        AndroidRect(left, top, right, bottom),
        AndroidRect(0, 0, outW, outH),
        paint,
    )
    return result
}
