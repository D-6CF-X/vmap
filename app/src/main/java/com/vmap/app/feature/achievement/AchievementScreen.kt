package com.vmap.app.feature.achievement

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vmap.app.core.ui.GradientTitle
import com.vmap.app.core.ui.accent
import com.vmap.app.core.ui.glassPanel
import com.vmap.app.core.ui.gradientTextStyle
import com.vmap.app.domain.model.Spot
import com.vmap.app.feature.map.MapViewModel

/** 一条成就：进度函数收全部打卡（不分板块），配合 [target] 判断是否达成。 */
data class Achievement(
    val emoji: String,
    val title: String,
    val description: String,
    val target: Int,
    val progress: (List<Spot>) -> Int,
) {
    fun current(spots: List<Spot>): Int = progress(spots).coerceIn(0, target)
    fun done(spots: List<Spot>): Boolean = current(spots) >= target
}

private fun citiesOf(spots: List<Spot>): Int = spots.map { it.adcode }.distinct().size

private val ACHIEVEMENTS = listOf(
    Achievement("🎤", "首场演出", "点亮你的第一座城市", 1) { it.size.coerceAtMost(1) },
    Achievement("🗺️", "巡演五城", "点亮 5 座城市", 5) { citiesOf(it) },
    Achievement("🌟", "十城巡礼", "点亮 10 座城市", 10) { citiesOf(it) },
    Achievement("🏙️", "踏遍直辖市", "点亮北京、上海、天津、重庆", 4) {
        val lit = it.map { s -> s.adcode.take(2) }.toSet()
        listOf("11", "31", "12", "50").count(lit::contains)
    },
    Achievement("🚩", "跨省巡演", "走过 5 个省份", 5) { it.map { s -> s.provinceName }.distinct().size },
    Achievement("🎶", "双担", "两个板块都点亮过", 2) { it.map { s -> s.section }.distinct().size },
)

@Composable
fun AchievementScreen(viewModel: MapViewModel, isDark: Boolean = true) {
    val allSpots by viewModel.allSpots.collectAsStateWithLifecycle()
    val section by viewModel.section.collectAsStateWithLifecycle()
    val accent = section.accent

    val achieved = ACHIEVEMENTS.count { it.done(allSpots) }
    val next = ACHIEVEMENTS.firstOrNull { !it.done(allSpots) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 96.dp, bottom = 28.dp),
    ) {
        // 头部：进度环 + 标题
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                ratio = if (ACHIEVEMENTS.isEmpty()) 0f else achieved.toFloat() / ACHIEVEMENTS.size,
                accent = accent,
                isDark = isDark,
            )
            Spacer(Modifier.width(16.dp))
            Column {
                GradientTitle(text = "成就徽章", accent = accent, fontSize = 24.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "已解锁 $achieved / ${ACHIEVEMENTS.size}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }

        if (next != null) {
            Spacer(Modifier.height(18.dp))
            NextGoalCard(next = next, spots = allSpots, accent = accent, isDark = isDark)
        }

        Spacer(Modifier.height(18.dp))

        ACHIEVEMENTS.forEach { achievement ->
            AchievementCard(
                achievement = achievement,
                spots = allSpots,
                accent = accent,
                isDark = isDark,
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** 环形进度：底环 + 应援色弧 + 中间百分比。 */
@Composable
private fun ProgressRing(ratio: Float, accent: Color, isDark: Boolean) {
    Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 7f
            val inset = stroke / 2f
            drawArc(
                color = Color.White.copy(alpha = if (isDark) 0.10f else 0.45f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (ratio > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(accent.copy(alpha = 0.55f), accent, lerp(accent, Color.White, 0.6f)),
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * ratio,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text = "${(ratio * 100).toInt()}%",
            style = gradientTextStyle(accent, 15.sp),
        )
    }
}

/** 下一个目标：告诉用户离哪个徽章最近、还差多少。 */
@Composable
private fun NextGoalCard(next: Achievement, spots: List<Spot>, accent: Color, isDark: Boolean) {
    val shape = RoundedCornerShape(18.dp)
    val current = next.current(spots)
    val remain = (next.target - current).coerceAtLeast(0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(shape = shape, isDark = isDark, accent = accent, elevation = 10.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("🎯", fontSize = 15.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "下一个：${next.title}",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${next.description} · 还差 $remain",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
        Text(
            text = "$current/${next.target}",
            color = if (isDark) lerp(accent, Color.White, 0.3f) else accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AchievementCard(
    achievement: Achievement,
    spots: List<Spot>,
    accent: Color,
    isDark: Boolean,
) {
    val achieved = achievement.done(spots)
    val current = achievement.current(spots)
    val ratio = if (achievement.target == 0) 0f else current.toFloat() / achievement.target
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(
                shape = shape,
                isDark = isDark,
                accent = if (achieved) accent else lerp(accent, Color.Gray, 0.6f),
                elevation = if (achieved) 10.dp else 4.dp,
                base = if (achieved) 0.72f else 0.5f,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = achievement.emoji,
                fontSize = 26.sp,
                modifier = Modifier.alpha(if (achieved) 1f else 0.4f),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = achievement.title,
                    color = if (achieved) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = achievement.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (achieved) 1f else 0.55f),
                    fontSize = 11.sp,
                )
            }
            if (achieved) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(accent)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "已达成",
                        color = Color(0xFF06151B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                Text(
                    text = "$current/${achievement.target}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                )
            }
        }

        if (!achieved) {
            Spacer(Modifier.height(10.dp))
            // 细进度条：还差多少一眼能看出来
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (isDark) 0.08f else 0.35f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ratio.coerceIn(0f, 1f))
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.75f)),
                )
            }
        }
    }
}
