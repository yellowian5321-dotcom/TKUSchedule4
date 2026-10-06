package com.example.tkuschedule.assistant

import com.example.tkuschedule.data.Course
import com.example.tkuschedule.location.NextClassCalculator
import com.example.tkuschedule.location.NextClassLocationUiState
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CatClassMessage {
    private val zone = ZoneId.of("Asia/Taipei")
    private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormat = DateTimeFormatter.ofPattern("M/d HH:mm")

    fun build(
        courses: List<Course>,
        location: NextClassLocationUiState,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        if (courses.isEmpty()) {
            return "主人～先到課程管理匯入課表，\n我就能提醒你下一堂課喵！"
        }
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDateTime()
        val next = NextClassCalculator.findNextClass(courses, now)
            ?: return "目前沒有可辨識的上課時段，\n請確認課程的星期與節次喵。"

        val sameClass = location.nextClass?.course == next.course &&
                location.nextClass?.startDateTime == next.startDateTime
        val estimate = location.walkingEstimate?.takeIf {
            sameClass && location.canUseWalkingEstimate(nowMillis) &&
                    it.walkingMinutes >= 0
        }

        return buildString {
            append("下一堂：${next.courseName}\n")
            append("${formatTime(next.startDateTime, now)}｜${next.classroom}\n")
            if (estimate != null) {
                append("預估步行約 ${estimate.walkingMinutes} 分鐘\n")
                val departure = next.startDateTime
                    .minusMinutes(estimate.walkingMinutes.toLong())
                if (now.isBefore(departure)) {
                    append("建議 ${formatTime(departure, now)} 前出發喵！")
                } else {
                    append("建議現在出發喵！")
                }
                if (location.walkingSource == "LOCAL_FALLBACK") {
                    append("\n（依距離粗估）")
                }
            } else {
                append(when {
                    !sameClass -> "點我更新位置，就能估算步行時間喵。"
                    location.isLoading -> "正在更新位置與步行時間…"
                    location.needsLocationPermission -> "需要位置權限，才能估算步行時間喵。"
                    location.errorMessage?.contains("定位功能尚未開啟") == true ->
                        "請先開啟手機定位，再點我重試喵。"
                    location.errorMessage != null && location.currentLocation == null ->
                        "暫時無法取得位置，點我重新定位喵。"
                    location.errorMessage != null && location.destination == null ->
                        "暫時無法辨識這間教室的位置喵。"
                    location.errorMessage != null ->
                        "暫時無法計算步行時間，點我重試喵。"
                    else -> "定位資料已過期，點我更新步行時間喵。"
                })
            }
        }
    }

    private fun formatTime(time: LocalDateTime, now: LocalDateTime): String =
        when (time.toLocalDate()) {
            now.toLocalDate() -> "今天 ${time.format(clockFormat)}"
            now.toLocalDate().plusDays(1) -> "明天 ${time.format(clockFormat)}"
            else -> time.format(dateFormat)
        }
}

