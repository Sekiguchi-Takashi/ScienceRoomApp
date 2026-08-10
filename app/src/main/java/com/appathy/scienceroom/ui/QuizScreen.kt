package com.appathy.scienceroom.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appathy.scienceroom.Game
import com.appathy.scienceroom.engine.LearningEngine
import com.appathy.scienceroom.engine.Question
import com.appathy.scienceroom.engine.QuizMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(game: Game, onClose: () -> Unit) {
    val content = game.content
    var mode by remember { mutableStateOf<QuizMode?>(null) }
    var question by remember {
        mutableStateOf(
            LearningEngine.nextQuestion(content, game.state, null, System.currentTimeMillis())
        )
    }
    var picked by remember { mutableStateOf<String?>(null) }
    var streak by remember { mutableStateOf(0) }

    fun next() {
        picked = null
        question = LearningEngine.nextQuestion(content, game.state, mode, System.currentTimeMillis())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("元素クイズ", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onClose) { Text("閉じる") }
            }
            Text(
                "連続正解 $streak ／ 通算 ${game.state.quizCorrect} / ${game.state.quizCount}",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChip(
                        selected = mode == null,
                        onClick = { mode = null; next() },
                        label = { Text("おまかせ", fontSize = 11.sp) }
                    )
                }
                items(QuizMode.entries.toList()) { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { mode = m; next() },
                        label = { Text(m.label, fontSize = 11.sp) }
                    )
                }
            }
        }

        item { QuestionCard(question) }

        items(question.choices) { choice ->
            val isPicked = picked == choice
            val isAnswer = choice == question.answer
            val color = when {
                picked == null -> MaterialTheme.colorScheme.surface
                isAnswer -> MaterialTheme.colorScheme.secondary
                isPicked -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.surface
            }
            Button(
                onClick = {
                    if (picked == null) {
                        picked = choice
                        val correct = choice == question.answer
                        streak = if (correct) streak + 1 else 0
                        game.update {
                            LearningEngine.answer(it, question, correct, System.currentTimeMillis())
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    choice,
                    fontSize = 16.sp,
                    color = if (picked == null) MaterialTheme.colorScheme.onSurface
                    else androidx.compose.ui.graphics.Color.White
                )
            }
        }

        if (picked != null) {
            item {
                PanelCard {
                    val e = question.element
                    Text(
                        if (picked == question.answer) "正解" else "不正解　答えは ${question.answer}",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = if (picked == question.answer) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Thumb(e.imageId, 64)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("${e.symbol}　${e.name}", fontWeight = FontWeight.Bold)
                            Text(e.english, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(e.property, fontSize = 14.sp)
                    Text("使われ方：${e.uses}", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Button(onClick = { next() }, modifier = Modifier.fillMaxWidth()) {
                    Text("次の問題")
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun QuestionCard(q: Question) {
    PanelCard {
        Text(
            q.mode.label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        if (q.showImage) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Thumb(q.element.imageId, 160)
            }
            Spacer(Modifier.height(8.dp))
        }
        Text(
            q.prompt,
            fontSize = if (q.mode == QuizMode.SYMBOL_TO_NAME) 40.sp else 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
