package com.example.tkuschedule.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tkuschedule.data.Course
import com.example.tkuschedule.location.NextClassLocationUiState
import com.example.tkuschedule.location.NextClassLocationStatusCard

private val suggestionQuestions = listOf(
    "下一堂課要幾點出發？",
    "幫我分析哪一天最有空",
    "我的課表有衝堂嗎？",
    "哪一天的第一堂課最早？",
    "幫我整理這週的課程",
    "哪一天最適合安排打工？"
)

@Composable
fun AiAssistantScreen(
    courses: List<Course>,
    viewModel: AiAssistantViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(),
    locationState: NextClassLocationUiState = NextClassLocationUiState(),
    onRefreshLocation: () -> Unit = {}
) {
    val state by viewModel.uiState
        .collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(
        state.messages.size,
        state.isLoading
    ) {
        val additionalItem =
            if (state.isLoading) 1 else 0

        val lastIndex =
            state.messages.size +
                    additionalItem - 1

        if (lastIndex >= 0) {
            listState.animateScrollToItem(
                lastIndex
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        AiStatusCard(
            courseCount = courses
                .groupBy(::courseKey)
                .size,
            onClear =
                viewModel::clearConversation
        )

        NextClassLocationStatusCard(locationState, onRefreshLocation, compact = true)

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 10.dp,
                    vertical = 6.dp
                ),
            horizontalArrangement =
                Arrangement.spacedBy(7.dp)
        ) {
            items(
                items = suggestionQuestions,
                key = {
                    it
                }
            ) { question ->

                OutlinedButton(
                    onClick = {
                        viewModel.askSuggestion(
                            question = question,
                            courses = courses,
                            locationState = locationState
                        )
                    },
                    enabled = !state.isLoading
                ) {
                    Text(
                        text = question
                    )
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = state.messages,
                key = {
                    it.id
                }
            ) { message ->

                ChatMessageBubble(
                    message = message
                )
            }

            if (state.isLoading) {
                item(
                    key = "ai_loading"
                ) {
                    AiLoadingBubble()
                }
            }
        }

        state.errorMessage?.let { error ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 10.dp,
                        vertical = 4.dp
                    ),
                color =
                    MaterialTheme
                        .colorScheme
                        .errorContainer,
                shape =
                    RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier =
                        Modifier.padding(10.dp),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Text(
                        text = error,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onErrorContainer,
                        modifier =
                            Modifier.weight(1f)
                    )

                    TextButton(
                        onClick =
                            viewModel::clearError
                    ) {
                        Text("關閉")
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme
                        .colorScheme
                        .surface
                )
                .padding(10.dp),
            verticalAlignment =
                Alignment.Bottom,
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.inputText,
                onValueChange =
                    viewModel::updateInputText,
                placeholder = {
                    Text(
                        "詢問課表、空堂或衝堂…"
                    )
                },
                enabled = !state.isLoading,
                maxLines = 4,
                keyboardOptions =
                    androidx.compose.foundation.text
                        .KeyboardOptions(
                            imeAction =
                                ImeAction.Send
                        ),
                keyboardActions =
                    KeyboardActions(
                        onSend = {
                            if (
                                state.inputText
                                    .isNotBlank()
                            ) {
                                focusManager
                                    .clearFocus()

                                viewModel
                                    .sendMessage(
                                        courses, locationState
                                    )
                            }
                        }
                    ),
                modifier =
                    Modifier.weight(1f)
            )

            Button(
                onClick = {
                    focusManager.clearFocus()

                    viewModel.sendMessage(
                        courses, locationState
                    )
                },
                enabled =
                    state.inputText
                        .isNotBlank() &&
                            !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("送出")
                }
            }
        }
    }
}

@Composable
private fun AiStatusCard(
    courseCount: Int,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 10.dp,
                vertical = 8.dp
            ),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme
                    .colorScheme
                    .primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color =
                            MaterialTheme
                                .colorScheme
                                .primary,
                        shape =
                            RoundedCornerShape(
                                14.dp
                            )
                    ),
                contentAlignment =
                    Alignment.Center
            ) {
                Text(
                    text = "AI",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(
                modifier = Modifier.width(10.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "淡江課表 AI 助理",
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = if (courseCount == 0) {
                        "目前尚未載入課程"
                    } else {
                        "已讀取 $courseCount 門課程"
                    },
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall
                )
            }

            TextButton(
                onClick = onClear
            ) {
                Text("清除對話")
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: AiChatMessage
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            if (message.isUser) {
                Arrangement.End
            } else {
                Arrangement.Start
            }
    ) {
        Surface(
            color = if (message.isUser) {
                MaterialTheme
                    .colorScheme
                    .primary
            } else {
                MaterialTheme
                    .colorScheme
                    .surfaceVariant
            },
            contentColor =
                if (message.isUser) {
                    MaterialTheme
                        .colorScheme
                        .onPrimary
                } else {
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
                },
            shape =
                if (message.isUser) {
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 4.dp,
                        bottomStart = 18.dp,
                        bottomEnd = 18.dp
                    )
                } else {
                    RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 18.dp,
                        bottomStart = 18.dp,
                        bottomEnd = 18.dp
                    )
                },
            modifier = Modifier
                .fillMaxWidth(0.86f)
        ) {
            Text(
                text = message.text,
                modifier =
                    Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun AiLoadingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.Start
    ) {
        Surface(
            color =
                MaterialTheme
                    .colorScheme
                    .surfaceVariant,
            shape =
                RoundedCornerShape(
                    topStart = 4.dp,
                    topEnd = 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 18.dp
                )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )

                Spacer(
                    modifier = Modifier.width(8.dp)
                )

                Text("Gemma 正在分析課表…")
            }
        }
    }
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
