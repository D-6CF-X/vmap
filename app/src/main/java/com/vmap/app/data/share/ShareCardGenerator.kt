package com.vmap.app.data.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import com.vmap.app.core.ui.accent
import com.vmap.app.domain.model.Section
import com.vmap.app.domain.model.Spot

/**
 * 把点亮数据画成一张可分享的卡片（1080x1440）。
 * 纯 android.graphics 绘制，不依赖 Compose，方便离线生成与保存。
 */
object ShareCardGenerator {

    fun generate(section: Section, spots: List<Spot>, dark: Boolean): Bitmap {
        val w = 1080
        val h = 1440
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val accent = section.accent.toArgb()
        val bg = if (dark) 0xFF0F1115.toInt() else 0xFFF5F6F8.toInt()
        val titleColor = if (dark) 0xFFE6E8EE.toInt() else 0xFF1A1D22.toInt()
        val subColor = if (dark) 0xFFA8B0BE.toInt() else 0xFF5A6170.toInt()

        canvas.drawColor(bg)

        // 顶部应援色光晕装饰
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; alpha = 40 }
        canvas.drawCircle(140f, 160f, 260f, glow)
        val glow2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; alpha = 90 }
        canvas.drawCircle(140f, 160f, 150f, glow2)

        // 板块名
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textSize = 96f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(section.displayName, 80f, 340f, title)

        // 副标题
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = subColor; textSize = 40f }
        canvas.drawText("为歌声走过的城市", 80f, 410f, sub)

        // 统计
        val cityCount = spots.map { it.adcode }.distinct().size
        val spotCount = spots.size
        val stat = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = titleColor; textSize = 64f; typeface = Typeface.DEFAULT_BOLD }
        canvas.drawText("$cityCount 座城市 · $spotCount 场演出", 80f, 520f, stat)

        // 分隔线
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; alpha = 120; strokeWidth = 3f }
        canvas.drawLine(80f, 580f, 1000f, 580f, line)

        // 城市列表
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        val cityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = titleColor; textSize = 44f }
        val yearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = subColor; textSize = 36f }

        var y = 680f
        spots.sortedBy { it.performedAt }.forEach { spot ->
            if (y > 1300f) return@forEach
            canvas.drawCircle(110f, y - 14f, 16f, dot)
            canvas.drawText(spot.cityName, 160f, y, cityPaint)
            val year = java.util.Calendar.getInstance().apply { timeInMillis = spot.performedAt }
                .get(java.util.Calendar.YEAR).toString()
            val yearWidth = yearPaint.measureText(year)
            canvas.drawText(year, 1000f - yearWidth, y, yearPaint)
            y += 90f
        }

        // 底部品牌
        val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = subColor; textSize = 34f }
        canvas.drawText("V-Map · 我的歌声足迹", 80f, 1380f, brand)

        return bitmap
    }

    private val Section.displayName: String
        get() = when (this) {
            Section.TIANYI -> "依你随行"
            Section.MIKU -> "因音而遇"
        }
}
