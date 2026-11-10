package com.vmap.app.feature.achievement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vmap.app.core.ui.GradientTitle
import com.vmap.app.core.ui.accent
import com.vmap.app.domain.model.Section
import com.vmap.app.domain.model.Spot
import com.vmap.app.feature.map.MapViewModel

/** 一条成就。条件函数收全部打卡（不分板块），返回是否达成。 */
data class Achievement(
    val emoji: String,
    val title: String,
    val description: String,
    val condition: (List<Spot>) -> Boolean,
)

private val ACHIEVEMENTS = listOf(
    Achievement("🎤", "首场演出", "点亮你的第一座城市") { it.isNotEmpty() },
    Achievement("🗺️", "巡演五城", "点亮 5 座城市") { it.map { s -> s.adcode }.distinct().size >= 5 },
    Achievement("🌟", "十城巡礼", "点亮 10 座城市") { it.map { s -> s.adcode }.distinct().size >= 10 },
    Achievement("🏙️", "踏遍直辖市", "点亮北京、上海、天津、重庆") {
        val lit = it.map { s -> s.adcode.take(2) }.toSet()
        listOf("11", "31", "12", "50").all(lit::contains)
    },
    Achievement("🚩", "跨省巡演", "走过 5 个省份") { it.map { s -> s.provinceName }.distinct().size >= 5 },
    Achievement("🎶", "双担", "两个板块都点亮过") {
        it.map { s -> s.section }.toSet().size >= 2
    },
)

@Composable
fun AchievementScreen(viewModel: MapViewModel) {
    val allSpots by viewModel.allSpots.collectAsStateWithLifecycle()
    val section by viewModel.section.collectAsStateWithLifecycle()
    val accent = section.accent

    val achievedCount = ACHIEVEMENTS.count { it.condition(allSpots) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 84.dp, bottom = 24.dp),
    ) {
        GradientTitle(
            text = "成就徽章",
            accent = accent,
            fontSize = 24.sp,
        )
        Text(
            text = "已解锁 $achievedCount / ${ACHIEVEMENTS.size}",
            color = accent,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(20.dp))

        ACHIEVEMENTS.forEach { achievement ->
            val achieved = achievement.condition(allSpots)
            AchievementCard(
                achievement = achievement,
                achieved = achieved,
                accent = accent,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AchievementCard(
    achievement: Achievement,
    achieved: Boolean,
    accent: Color,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (achieved) 0.78f else 0.5f),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = achievement.emoji,
                fontSize = 28.sp,
                modifier = Modifier.alpha(if (achieved) 1f else 0.35f),
            )
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = achievement.title,
                    color = if (achieved) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = achievement.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (achieved) 1f else 0.6f),
                    fontSize = 12.sp,
                )
            }
            if (achieved) {
                Surface(color = accent, shape = MaterialTheme.shapes.small) {
                    Text(
                        text = "已达成",
                        color = Color(0xFF06151B),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            } else {
                Text(
                    text = "未解锁",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}
