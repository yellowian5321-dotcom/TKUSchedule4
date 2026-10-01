package com.example.tkuschedule.location

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tkuschedule.data.Course
import kotlin.math.abs

@Composable
fun NextClassLocationCard(
    courses: List<Course>,
    modifier: Modifier = Modifier,
    viewModel:
    NextClassLocationViewModel =
        viewModel()
) {
    val state by viewModel
        .uiState
        .collectAsStateWithLifecycle()

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .RequestMultiplePermissions()
        ) { permissions ->

            val fineLocationGranted =
                permissions[
                    Manifest.permission
                        .ACCESS_FINE_LOCATION
                ] == true

            val coarseLocationGranted =
                permissions[
                    Manifest.permission
                        .ACCESS_COARSE_LOCATION
                ] == true

            viewModel
                .onLocationPermissionResult(
                    granted =
                        fineLocationGranted ||
                                coarseLocationGranted,
                    courses = courses
                )
        }

    LaunchedEffect(courses) {
        viewModel.refresh(courses)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme
                    .colorScheme
                    .surfaceVariant
                    .copy(alpha = 0.55f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color =
                MaterialTheme
                    .colorScheme
                    .outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "下一堂課與步行時間",
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                fontWeight = FontWeight.Bold
            )

            when {
                state.isLoading -> {
                    LoadingContent()
                }

                state.needsLocationPermission -> {
                    LocationPermissionContent(
                        message =
                            state.errorMessage
                                ?: "需要位置權限",
                        onRequestPermission = {
                            permissionLauncher
                                .launch(
                                    arrayOf(
                                        Manifest.permission
                                            .ACCESS_FINE_LOCATION,
                                        Manifest.permission
                                            .ACCESS_COARSE_LOCATION
                                    )
                                )
                        }
                    )
                }

                state.hasResult -> {
                    LocationResultContent(
                        state = state,
                        onRefresh = {
                            viewModel.refresh(
                                courses
                            )
                        }
                    )
                }

                else -> {
                    ErrorContent(
                        message =
                            state.errorMessage
                                ?: "尚未取得下一堂課資訊",
                        onRetry = {
                            viewModel.refresh(
                                courses
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.Center
    ) {
        CircularProgressIndicator()

        Spacer(
            modifier = Modifier.padding(6.dp)
        )

        Text(
            text = "正在計算下一堂課與步行時間…"
        )
    }
}

@Composable
private fun LocationPermissionContent(
    message: String,
    onRequestPermission: () -> Unit
) {
    Text(
        text = message,
        style =
            MaterialTheme
                .typography
                .bodyMedium,
        color =
            MaterialTheme
                .colorScheme
                .onSurfaceVariant
    )

    Button(
        onClick = onRequestPermission,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("允許位置並開始計算")
    }
}

@Composable
private fun LocationResultContent(
    state: NextClassLocationUiState,
    onRefresh: () -> Unit
) {
    val nextClass =
        state.nextClass
            ?: return

    val destination =
        state.destination
            ?: return

    val walkingEstimate =
        state.walkingEstimate
            ?: return

    InformationRow(
        label = "課程",
        value = nextClass.courseName
    )

    InformationRow(
        label = "教師",
        value = nextClass.teacher
    )

    InformationRow(
        label = "上課時間",
        value =
            nextClass.startTimeText
    )

    InformationRow(
        label = "教室",
        value =
            destination.displayName
    )

    InformationRow(
        label = "原始教室資料",
        value =
            destination.originalClassroom
    )

    InformationRow(
        label = "距離上課",
        value =
            formatRemainingTime(
                nextClass.minutesUntilClass
            )
    )

    InformationRow(
        label = "預估距離",
        value =
            formatDistance(
                walkingEstimate
                    .distanceMeters
            )
    )

    InformationRow(
        label = "步行時間",
        value =
            "${walkingEstimate.walkingMinutes} 分鐘"
    )

    Spacer(
        modifier = Modifier.height(2.dp)
    )

    ArrivalSuggestion(
        estimate = walkingEstimate
    )

    OutlinedButton(
        onClick = onRefresh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("重新取得位置並計算")
    }
}

@Composable
private fun InformationRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$label：",
            fontWeight = FontWeight.Bold,
            modifier =
                Modifier.weight(0.35f)
        )

        Text(
            text = value,
            modifier =
                Modifier.weight(0.65f)
        )
    }
}

@Composable
private fun ArrivalSuggestion(
    estimate: WalkingEstimate
) {
    val backgroundColor: Color
    val textColor: Color
    val message: String

    if (estimate.canArriveOnTime) {
        backgroundColor =
            Color(0xFFE8F5E9)

        textColor =
            Color(0xFF1B5E20)

        message =
            when {
                estimate
                    .suggestedDepartureMinutes <= 3 -> {
                    "建議現在出發，避免遲到。"
                }

                else -> {
                    "目前還來得及，建議在 " +
                            "${estimate.suggestedDepartureMinutes} " +
                            "分鐘內出發。"
                }
            }
    } else {
        backgroundColor =
            Color(0xFFFFEBEE)

        textColor =
            Color(0xFFB71C1C)

        message =
            "依照目前位置，預估可能遲到 " +
                    "${abs(estimate.suggestedDepartureMinutes)} " +
                    "分鐘，建議立即出發。"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor =
                backgroundColor
        )
    ) {
        Text(
            text = message,
            color = textColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Text(
        text = message,
        color =
            MaterialTheme
                .colorScheme
                .error
    )

    OutlinedButton(
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("重新嘗試")
    }
}

private fun formatDistance(
    distanceMeters: Int
): String {
    return if (distanceMeters < 1000) {
        "$distanceMeters 公尺"
    } else {
        String.format(
            "%.1f 公里",
            distanceMeters / 1000.0
        )
    }
}

private fun formatRemainingTime(
    minutes: Long
): String {
    if (minutes < 60) {
        return "$minutes 分鐘"
    }

    val days = minutes / (24 * 60)
    val remainingAfterDays =
        minutes % (24 * 60)

    val hours =
        remainingAfterDays / 60

    val remainingMinutes =
        remainingAfterDays % 60

    return buildString {
        if (days > 0) {
            append("${days}天 ")
        }

        if (hours > 0) {
            append("${hours}小時 ")
        }

        if (remainingMinutes > 0) {
            append("${remainingMinutes}分鐘")
        }
    }.trim()
}