package com.example.tkuschedule.location

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tkuschedule.data.Course
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NextClassLocationUiState(
    val isLoading: Boolean = false,
    val needsLocationPermission: Boolean = false,
    val nextClass: NextClassInfo? = null,
    val destination: ClassroomDestination? = null,
    val currentLocation: GeoPoint? = null,
    val classroomLocation: GeoPoint? = null,
    val walkingEstimate: WalkingEstimate? = null,
    val errorMessage: String? = null
) {
    val hasResult: Boolean
        get() {
            return nextClass != null &&
                    destination != null &&
                    walkingEstimate != null
        }
}

class NextClassLocationViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val locationRepository =
        LocationRepository(application)

    private val classroomGeocoder =
        ClassroomGeocoder(application)

    private val _uiState =
        MutableStateFlow(
            NextClassLocationUiState()
        )

    val uiState:
            StateFlow<NextClassLocationUiState> =
        _uiState.asStateFlow()

    fun refresh(
        courses: List<Course>
    ) {
        if (courses.isEmpty()) {
            _uiState.value =
                NextClassLocationUiState(
                    errorMessage =
                        "目前課表沒有任何課程，" +
                                "請先載入或匯入課程"
                )

            return
        }

        if (
            !locationRepository
                .hasLocationPermission()
        ) {
            _uiState.value =
                NextClassLocationUiState(
                    needsLocationPermission =
                        true,
                    errorMessage =
                        "需要位置權限才能計算" +
                                "走到下一堂課的時間"
                )

            return
        }

        loadNextClassInformation(
            courses = courses
        )
    }

    fun onLocationPermissionResult(
        granted: Boolean,
        courses: List<Course>
    ) {
        if (!granted) {
            _uiState.value =
                NextClassLocationUiState(
                    needsLocationPermission =
                        true,
                    errorMessage =
                        "位置權限未允許，" +
                                "目前無法計算步行時間"
                )

            return
        }

        loadNextClassInformation(
            courses = courses
        )
    }

    private fun loadNextClassInformation(
        courses: List<Course>
    ) {
        viewModelScope.launch {
            _uiState.value =
                NextClassLocationUiState(
                    isLoading = true
                )

            val nextClass =
                NextClassCalculator
                    .findNextClass(courses)

            if (nextClass == null) {
                showError(
                    "找不到下一堂課，" +
                            "請確認課程星期與節次資料"
                )

                return@launch
            }

            val destination =
                ClassroomLocationRepository
                    .findDestination(
                        nextClass.classroom
                    )

            if (destination == null) {
                _uiState.value =
                    NextClassLocationUiState(
                        nextClass = nextClass,
                        errorMessage =
                            "目前尚未收錄教室代碼「" +
                                    nextClass.classroom +
                                    "」對應的大樓位置"
                    )

                return@launch
            }

            val currentLocationResult =
                locationRepository
                    .getCurrentLocation()

            val currentLocation =
                currentLocationResult
                    .getOrElse { exception ->

                        showError(
                            exception.message
                                ?: "無法取得目前位置",
                            nextClass =
                                nextClass,
                            destination =
                                destination
                        )

                        return@launch
                    }

            val classroomLocationResult =
                classroomGeocoder
                    .findLocation(
                        destination
                    )

            val classroomLocation =
                classroomLocationResult
                    .getOrElse { exception ->

                        showError(
                            exception.message
                                ?: "找不到教室位置",
                            nextClass =
                                nextClass,
                            destination =
                                destination,
                            currentLocation =
                                currentLocation
                        )

                        return@launch
                    }

            val walkingEstimate =
                WalkingTimeCalculator
                    .estimate(
                        currentLocation =
                            currentLocation,
                        classroomLocation =
                            classroomLocation,
                        minutesUntilClass =
                            nextClass
                                .minutesUntilClass
                    )

            _uiState.value =
                NextClassLocationUiState(
                    isLoading = false,
                    needsLocationPermission =
                        false,
                    nextClass = nextClass,
                    destination = destination,
                    currentLocation =
                        currentLocation,
                    classroomLocation =
                        classroomLocation,
                    walkingEstimate =
                        walkingEstimate,
                    errorMessage = null
                )
        }
    }

    private fun showError(
        message: String,
        nextClass: NextClassInfo? = null,
        destination:
        ClassroomDestination? = null,
        currentLocation:
        GeoPoint? = null
    ) {
        _uiState.value =
            NextClassLocationUiState(
                isLoading = false,
                needsLocationPermission =
                    false,
                nextClass = nextClass,
                destination = destination,
                currentLocation =
                    currentLocation,
                errorMessage = message
            )
    }

    fun clearError() {
        _uiState.value =
            _uiState.value.copy(
                errorMessage = null
            )
    }
}