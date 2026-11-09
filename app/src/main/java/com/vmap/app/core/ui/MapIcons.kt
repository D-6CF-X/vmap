package com.vmap.app.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.BitmapDescriptorFactory
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 地图上用得上的位图。两个引擎（高德 / osmdroid）共用同一套 Bitmap，
 * 只有最后包装成各自的类型不同。
 */
object MapIcons {

    /** 点亮的城市：应援色光晕 + 实心点 + 白描边，在暗色地图上很显眼。 */
    fun cityDotBitmap(color: Int, sizePx: Int = 96): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = sizePx / 2f
        val radius = sizePx * 0.24f

        canvas.drawCircle(
            center, center, radius * 2.0f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = 48 },
        )
        canvas.drawCircle(
            center, center, radius * 1.45f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = 90 },
        )
        canvas.drawCircle(
            center, center, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color },
        )
        canvas.drawCircle(
            center, center, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = sizePx * 0.045f
                this.color = Color.WHITE
            },
        )
        return bitmap
    }

    /**
     * 路线箭头。直接在位图上按 [angleDeg] 旋转，不依赖 SDK 的 marker 旋转接口
     * （高德 9.x 的 Marker 没有可写 rotate 属性，靠它会出现方向错误或编译不过）。
     */
    fun arrowBitmap(color: Int, angleDeg: Float, sizePx: Int = 48): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val path = Path().apply {
            moveTo(sizePx * 0.50f, sizePx * 0.08f)
            lineTo(sizePx * 0.80f, sizePx * 0.74f)
            lineTo(sizePx * 0.50f, sizePx * 0.56f)
            lineTo(sizePx * 0.20f, sizePx * 0.74f)
            close()
        }
        canvas.save()
        canvas.rotate(angleDeg, sizePx / 2f, sizePx / 2f)
        canvas.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.FILL
            },
        )
        canvas.restore()
        return bitmap
    }

    fun cityDot(color: Int, sizePx: Int = 96): BitmapDescriptor =
        BitmapDescriptorFactory.fromBitmap(cityDotBitmap(color, sizePx))

    fun arrow(color: Int, angleDeg: Float, sizePx: Int = 48): BitmapDescriptor =
        BitmapDescriptorFactory.fromBitmap(arrowBitmap(color, angleDeg, sizePx))

    /**
     * 从 (fromLat, fromLng) 到 (toLat, toLng) 的方位角，正北为 0，顺时针为正。
     * 两个引擎都用这个，所以入参是裸 double，不绑任何 SDK 类型。
     */
    fun bearing(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): Float {
        val lat1 = Math.toRadians(fromLat)
        val lat2 = Math.toRadians(toLat)
        val dLon = Math.toRadians(toLng - fromLng)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    }
}
