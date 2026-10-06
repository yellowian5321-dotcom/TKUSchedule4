package com.example.tkuschedule.location

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NextClassLocationStatusCard(
    state: NextClassLocationUiState,
    onRefresh: () -> Unit,
    compact: Boolean = false
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.locationUpdatedAtMillis, state.nextClass?.startDateTime) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(10_000L) // 更新畫面上的時間，不會持續要求 GPS。
        }
    }

    val colors = MaterialTheme.colorScheme
    val now = Instant.ofEpochMilli(nowMillis)
        .atZone(ZoneId.of("Asia/Taipei"))
        .toLocalDateTime()
    val nextClass = state.nextClass
    val classStarted = nextClass != null && !nextClass.startDateTime.isAfter(now)
    val estimate = state.walkingEstimate?.takeIf {
        state.canUseWalkingEstimate(nowMillis) && !classStarted
    }
    val heading = nextClass?.let {
        "下一堂課 · ${formatLocationCardTime(it.startDateTime, now)}"
    } ?: "下一堂課"
    val classroomText = if (compact) {
        nextClass?.classroom.orEmpty()
    } else {
        listOfNotNull(
            nextClass?.classroom,
            state.destination?.buildingName?.takeIf { it != nextClass?.classroom }
        ).joinToString(" · ")
    }
    val refreshLabel = when {
        state.isLoading -> "定位中"
        state.needsLocationPermission -> "允許定位"
        state.errorMessage != null -> "重試"
        else -> "更新"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = heading,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary
                    )
                    Text(
                        text = nextClass?.courseName?.ifBlank { "未命名課程" }
                            ?: if (state.isLoading) "正在讀取課程…" else "尚無下一堂課",
                        style = if (compact) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        maxLines = if (compact) 1 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = onRefresh,
                    enabled = !state.isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = colors.primary.copy(alpha = 0.08f),
                        contentColor = colors.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(refreshLabel, style = MaterialTheme.typography.labelLarge)
                }
            }

            if (classroomText.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = classroomText,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (compact && estimate != null) {
                        Text(
                            text = "步行約 ${estimate.walkingMinutes} 分鐘",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary,
                            textAlign = TextAlign.End,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (estimate != null && nextClass != null) {
                if (!compact) {
                    val departure = nextClass.startDateTime
                        .minusMinutes(estimate.walkingMinutes.toLong())
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "步行約 ${estimate.walkingMinutes} 分鐘",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (departure.isAfter(now)) {
                                    "${formatLocationCardTime(departure, now)} 前出發"
                                } else {
                                    "建議現在出發"
                                },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = locationCardHint(state, classStarted),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.errorMessage != null && nextClass != null &&
                        !state.needsLocationPermission && !state.isLoading
                    ) colors.error else colors.onSurfaceVariant
                )
            }

            if (state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.primary,
                    trackColor = colors.primary.copy(alpha = 0.1f)
                )
            }
        }
    }
}

private fun formatLocationCardTime(time: LocalDateTime, now: LocalDateTime): String {
    val clock = time.format(DateTimeFormatter.ofPattern("HH:mm"))
    return when (time.toLocalDate()) {
        now.toLocalDate() -> "今天 $clock"
        now.toLocalDate().plusDays(1) -> "明天 $clock"
        else -> time.format(DateTimeFormatter.ofPattern("M/d HH:mm"))
    }
}

private fun locationCardHint(
    state: NextClassLocationUiState,
    classStarted: Boolean
): String = when {
    state.isLoading -> "正在更新位置…"
    state.nextClass == null -> "加入課程後，就能查看步行建議。"
    classStarted -> "這堂課已開始，請更新下一堂課。"
    state.needsLocationPermission -> "允許定位，即可查看步行時間。"
    state.errorMessage?.contains("定位功能尚未開啟") == true -> "請先開啟手機的定位功能。"
    state.destination?.canLocateBuilding == false -> "此教室暫不提供步行估算。"
    state.errorMessage != null && state.currentLocation == null -> "暫時無法定位，請重試。"
    state.errorMessage != null && state.destination == null -> "暫時無法辨識教室。"
    state.errorMessage != null && state.classroomLocation == null -> "暫時找不到教室位置，請重試。"
    state.errorMessage != null -> "暫時無法計算步行時間，請重試。"
    else -> "更新位置，即可查看步行時間。"
}
