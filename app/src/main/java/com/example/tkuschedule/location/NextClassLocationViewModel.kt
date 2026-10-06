package com.example.tkuschedule.location

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tkuschedule.data.Course
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

data class NextClassLocationUiState(
    val isLoading: Boolean = false,
    val needsLocationPermission: Boolean = false,
    val nextClass: NextClassInfo? = null,
    val destination: ClassroomDestination? = null,
    val currentLocation: GeoPoint? = null,
    val classroomLocation: GeoPoint? = null,
    val walkingEstimate: WalkingEstimate? = null,
    val walkingSource: String? = null,
    val routesError: String? = null,
    val errorMessage: String? = null,
    val locationUpdatedAtMillis: Long? = null,
    val locationAccuracyMeters: Float? = null,
    val isStale: Boolean = false
) {
    val hasResult: Boolean
        get() = nextClass != null && destination != null && walkingEstimate != null

    fun hasFreshLocation(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val timestamp = locationUpdatedAtMillis ?: return false
        return currentLocation != null && nowMillis - timestamp in 0L..120_000L
    }

    fun canUseWalkingEstimate(nowMillis: Long = System.currentTimeMillis()): Boolean =
        hasResult && hasFreshLocation(nowMillis) && !isLoading && !isStale && errorMessage == null
}

class NextClassLocationViewModel(application: Application) : AndroidViewModel(application) {
    private val locationRepository = LocationRepository(application)
    private val classroomGeocoder = ClassroomGeocoder(application)
    private val routesRepository = RoutesWalkingRepository()
    private val _uiState = MutableStateFlow(NextClassLocationUiState())
    val uiState = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    fun refresh(courses: List<Course>, force: Boolean = false) {
        val nextClass = NextClassCalculator.findNextClass(courses)
        if (nextClass == null) {
            cancelRefresh()
            _uiState.value = NextClassLocationUiState(errorMessage = "目前沒有可計算的下一堂課，請先確認課表。")
            return
        }
        if (!locationRepository.hasLocationPermission()) {
            cancelRefresh()
            _uiState.value = NextClassLocationUiState(
                nextClass = nextClass, needsLocationPermission = true,
                errorMessage = "需要位置權限才能計算步行時間，請按重新定位。"
            )
            return
        }
        val previous = _uiState.value
        val sameClass = previous.nextClass?.course == nextClass.course &&
                previous.nextClass?.startDateTime == nextClass.startDateTime
        if (!force && sameClass && refreshJob?.isActive == true) return
        val now = System.currentTimeMillis()
        val age = previous.locationUpdatedAtMillis?.let { now - it }
        if (!force && sameClass && previous.canUseWalkingEstimate(now) &&
            age != null && age in 0L..30_000L && locationRepository.isLocationEnabled()) {
            val estimate = requireNotNull(previous.walkingEstimate)
            val departure = nextClass.minutesUntilClass - estimate.walkingMinutes
            _uiState.value = previous.copy(nextClass = nextClass, walkingEstimate = estimate.copy(
                suggestedDepartureMinutes = departure, canArriveOnTime = departure >= 0
            ))
            return
        }
        refreshJob?.cancel()
        _uiState.value = if (sameClass) previous.copy(
            nextClass = nextClass, isLoading = true, isStale = true,
            errorMessage = null, needsLocationPermission = false
        ) else NextClassLocationUiState(nextClass = nextClass, isLoading = true)

        refreshJob = viewModelScope.launch {
            try {
                // 先定位手機；教室無法辨識時，仍能顯示手機位置。
                val locationResult = locationRepository.getCurrentLocation(forceFresh = force)
                coroutineContext.ensureActive()
                val currentLocation = locationResult.getOrThrow()
                _uiState.value = _uiState.value.copy(
                    currentLocation = currentLocation,
                    locationUpdatedAtMillis = locationRepository.lastLocationTimeMillis,
                    locationAccuracyMeters = locationRepository.lastLocationAccuracyMeters,
                    walkingEstimate = null, walkingSource = null, routesError = null,
                    classroomLocation = null, destination = null
                )
                val destination = ClassroomLocationRepository.findDestination(nextClass.classroom)
                    ?: error("已取得手機位置，但無法辨識教室「${nextClass.classroom}」，暫時不能計算步行時間。")
                _uiState.value = _uiState.value.copy(destination = destination)
                val classroomResult = classroomGeocoder.findLocation(destination)
                coroutineContext.ensureActive()
                val classroomLocation = classroomResult.getOrThrow()
                _uiState.value = _uiState.value.copy(classroomLocation = classroomLocation)
                val routesResult = routesRepository.estimateWalkingTime(
                    currentLocation = currentLocation,
                    classroomLocation = classroomLocation,
                    minutesUntilClass = nextClass.minutesUntilClass
                )
                coroutineContext.ensureActive()
                val googleEstimate = routesResult.getOrNull()
                val estimate = googleEstimate ?: WalkingTimeCalculator.estimate(
                    currentLocation = currentLocation,
                    classroomLocation = classroomLocation,
                    minutesUntilClass = nextClass.minutesUntilClass
                )
                _uiState.value = _uiState.value.copy(
                    walkingEstimate = estimate,
                    walkingSource = if (googleEstimate != null) "GOOGLE_ROUTES" else "LOCAL_FALLBACK",
                    routesError = if (googleEstimate == null) routesResult.exceptionOrNull()?.message else null,
                    isLoading = false, isStale = false, errorMessage = null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                coroutineContext.ensureActive()
                // 保留已取得資料；舊步行結果標示為過期，不能當成現在的出發建議。
                _uiState.value = _uiState.value.copy(
                    isLoading = false, isStale = true,
                    errorMessage = error.message ?: "定位失敗，請重新定位。"
                )
            }
        }
    }

    fun onLocationPermissionResult(granted: Boolean, courses: List<Course>) {
        refresh(courses, force = granted)
    }

    fun cancelRefresh() {
        refreshJob?.cancel()
        refreshJob = null
        if (_uiState.value.isLoading) {
            _uiState.value = _uiState.value.copy(isLoading = false, isStale = true)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}