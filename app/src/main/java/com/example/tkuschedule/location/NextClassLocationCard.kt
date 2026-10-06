package com.example.tkuschedule.location

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tkuschedule.data.Course
import kotlinx.coroutines.awaitCancellation
import java.time.format.DateTimeFormatter

@Composable
fun NextClassLocationCard(
    courses: List<Course>,
    modifier: Modifier = Modifier,
    viewModel: NextClassLocationViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onLocationPermissionResult(
            it[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    it[Manifest.permission.ACCESS_COARSE_LOCATION] == true, courses
        )
    }
    LaunchedEffect(courses, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                viewModel.refresh(courses)
                awaitCancellation()
            } finally {
                viewModel.cancelRefresh()
            }
        }
    }
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    )) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("下一堂課", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(enabled = !state.isLoading && courses.isNotEmpty(), onClick = {
                    if (state.needsLocationPermission) permission.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
                    )) else viewModel.refresh(courses, force = true)
                }) { Text(if (state.needsLocationPermission) "允許定位" else "重新定位") }
            }
            state.nextClass?.let { next ->
                Text(next.courseName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("${next.startDateTime.format(DateTimeFormatter.ofPattern("M/d HH:mm"))}  ·  ${next.classroom}",
                    style = MaterialTheme.typography.bodyMedium)
            }
            when {
                state.isLoading -> Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("正在估算步行時間…", style = MaterialTheme.typography.bodySmall)
                }
                state.walkingEstimate != null && state.nextClass != null -> {
                    val estimate = requireNotNull(state.walkingEstimate)
                    val departure = requireNotNull(state.nextClass).startDateTime
                        .minusMinutes(estimate.walkingMinutes.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))
                    Text("步行約 ${estimate.walkingMinutes} 分鐘  ·  建議 $departure 出發",
                        style = MaterialTheme.typography.bodyMedium)
                    if (!estimate.canArriveOnTime) Text("時間有點趕，請儘快出發",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                else -> Text(state.errorMessage ?: "匯入課表後可查看下一堂課",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}