package com.example.tkuschedule.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tkuschedule.data.Course
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class AiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean
)

data class AiAssistantUiState(
    val inputText: String = "",
    val messages: List<AiChatMessage> = listOf(
        AiChatMessage(
            text = "你好，我是淡江課表 AI 助理。你可以問我空堂、衝堂、每天的課程或課表安排。",
            isUser = false
        )
    ),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class AiAssistantViewModel(
    private val repository: GeminiRepository =
        GeminiRepository()
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(AiAssistantUiState())

    val uiState: StateFlow<AiAssistantUiState> =
        _uiState.asStateFlow()

    fun updateInputText(
        value: String
    ) {
        _uiState.update {
            it.copy(
                inputText = value,
                errorMessage = null
            )
        }
    }

    fun sendMessage(
        courses: List<Course>
    ) {
        val question =
            _uiState.value.inputText.trim()

        if (
            question.isBlank() ||
            _uiState.value.isLoading
        ) {
            return
        }

        sendQuestion(
            question = question,
            courses = courses
        )
    }

    fun askSuggestion(
        question: String,
        courses: List<Course>
    ) {
        if (_uiState.value.isLoading) {
            return
        }

        sendQuestion(
            question = question,
            courses = courses
        )
    }

    fun clearConversation() {
        _uiState.value = AiAssistantUiState()
    }

    fun clearError() {
        _uiState.update {
            it.copy(
                errorMessage = null
            )
        }
    }

    private fun sendQuestion(
        question: String,
        courses: List<Course>
    ) {
        val userMessage = AiChatMessage(
            text = question,
            isUser = true
        )

        _uiState.update {
            it.copy(
                inputText = "",
                messages =
                    it.messages + userMessage,
                isLoading = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val prompt = buildPrompt(
                question = question,
                courses = courses
            )

            repository.sendMessage(prompt).fold(
                onSuccess = { answer ->

                    val aiMessage = AiChatMessage(
                        text = answer,
                        isUser = false
                    )

                    _uiState.update {
                        it.copy(
                            messages =
                                it.messages + aiMessage,
                            isLoading = false
                        )
                    }
                },

                onFailure = { error ->

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage =
                                error.message
                                    ?.takeIf {
                                            message ->
                                        message.isNotBlank()
                                    }
                                    ?: "Gemini 連線失敗，請檢查網路後再試一次"
                        )
                    }
                }
            )
        }
    }

    private fun buildPrompt(
        question: String,
        courses: List<Course>
    ): String {
        val scheduleText =
            buildScheduleText(courses)

        val recentConversation =
            _uiState.value.messages
                .takeLast(8)
                .joinToString("\n") {
                        message ->

                    if (message.isUser) {
                        "使用者：${message.text}"
                    } else {
                        "AI 助理：${message.text}"
                    }
                }

        return """
            你是淡江大學的課表 AI 助理。

            回答規則：
            1. 一律使用繁體中文。
            2. 只能根據下方提供的真實課表資料回答。
            3. 不得編造不存在的課程、教師、教室、時間或開課序號。
            4. 如果課表沒有資料，必須明確告訴使用者目前尚未載入課程。
            5. 如果使用者詢問衝堂，請比較星期與節次。
            6. 如果有兩門課在同一星期、相同節次上課，必須清楚指出衝堂。
            7. 如果使用者詢問空堂，請根據星期一到星期日、第1節到第14節分析。
            8. 回答請簡潔、清楚，不需要使用太複雜的術語。
            9. 不要宣稱你可以直接替使用者完成正式選課。
            10. 如果問題與課表無關，可以簡短回答，但不能假裝擁有未提供的資料。

            目前課表資料：
            $scheduleText

            最近對話：
            $recentConversation

            使用者目前問題：
            $question
        """.trimIndent()
    }

    private fun buildScheduleText(
        courses: List<Course>
    ): String {
        if (courses.isEmpty()) {
            return "目前尚未載入或匯入任何課程。"
        }

        val groupedCourses = courses
            .groupBy(::courseKey)
            .values
            .map {
                    sessions ->

                val primary =
                    sessions.first()

                val schedule = sessions
                    .sortedWith(
                        compareBy<Course> {
                            it.weekday
                        }.thenBy {
                            it.periods
                                .firstOrNull()
                                ?.toIntOrNull()
                                ?: Int.MAX_VALUE
                        }
                    )
                    .joinToString("、") {
                            session ->

                        "${session.weekdayText} " +
                                "${session.periodsText} " +
                                "教室${session.classroom.ifBlank { "未公告" }}"
                    }

                """
                    課程名稱：${primary.courseName}
                    開課序號：${primary.courseNo}
                    科目代碼：${primary.subjectCode}
                    系所：${primary.departmentName}
                    年級：${primary.grade}
                    班級：${primary.className}
                    教師：${primary.teacher}
                    必選修：${primary.requiredText}
                    學分：${primary.creditsText}
                    上課時間：$schedule
                """.trimIndent()
            }

        return groupedCourses
            .mapIndexed {
                    index, courseText ->

                "課程${index + 1}：\n$courseText"
            }
            .joinToString("\n\n")
    }

    private fun courseKey(
        course: Course
    ): String {
        return if (
            course.courseNo.isNotBlank()
        ) {
            "${course.departmentCode}|" +
                    course.courseNo
        } else {
            "${course.departmentCode}|" +
                    "${course.subjectCode}|" +
                    "${course.className}|" +
                    course.courseName
        }
    }
}