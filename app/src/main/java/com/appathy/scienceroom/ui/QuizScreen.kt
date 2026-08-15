package com.appathy.scienceroom.ui

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appathy.scienceroom.Feedback
import com.appathy.scienceroom.Game
import com.appathy.scienceroom.engine.EventEngine
import com.appathy.scienceroom.engine.GameEvent
import com.appathy.scienceroom.engine.LearningEngine
import com.appathy.scienceroom.engine.Question
import com.appathy.scienceroom.engine.QuizMode
import com.appathy.scienceroom.engine.QuizScope
import com.appathy.scienceroom.engine.Scene
import com.appathy.scienceroom.engine.QuizSession
import com.appathy.scienceroom.engine.ReviewEngine

/** 1セッションの問題数。短く区切って終わりを作る */
private const val SESSION_LENGTH = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(game: Game, onClose: () -> Unit) {
    val content = game.content
    val now = System.currentTimeMillis()
    val dueAtStart = remember { ReviewEngine.dueCount(content, game.state, now) }

    var mode by remember { mutableStateOf<QuizMode?>(null) }
    var scope by remember { mutableStateOf(QuizScope.ALL) }
    var dueOnly by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf(QuizSession()) }
    var streak by remember { mutableStateOf(0) }
    var picked by remember { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf(false) }
    var question by remember {
        mutableStateOf(
            LearningEngine.nextQuestion(content, game.state, null, now, false, game.event)
        )
    }

    fun draw() {
        picked = null
        question = LearningEngine.nextQuestion(
            content, game.state, mode, System.currentTimeMillis(), dueOnly, game.event, scope
        )
    }

    fun restart(newDueOnly: Boolean) {
        dueOnly = newDueOnly
        session = QuizSession()
        streak = 0
        finished = false
        draw()
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
                Text(
                    if (dueOnly) "復習クイズ" else "元素クイズ",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onClose) { Text("閉じる") }
            }
            Text(
                "この回 " + session.answered + " / " + SESSION_LENGTH +
                    "　連続正解 " + streak,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { session.answered.toFloat() / SESSION_LENGTH },
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )
        }

        if (dueAtStart >= 4 && !dueOnly && session.answered == 0) {
            item {
                PanelCard {
                    Text(
                        "復習どきの元素が " + dueAtStart + " 個あります",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "忘れかけたころに解き直すと、いちばん記憶に残ります",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { restart(true) }) { Text("復習だけ解く") }
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(QuizScope.entries.toList()) { sc ->
                    FilterChip(
                        selected = scope == sc,
                        onClick = { scope = sc; draw() },
                        label = { Text(sc.label, fontSize = 11.sp) }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChip(
                        selected = mode == null,
                        onClick = { mode = null; draw() },
                        label = { Text("おまかせ", fontSize = 11.sp) }
                    )
                }
                items(QuizMode.entries.toList()) { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { mode = m; draw() },
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
                        session = session.record(question.element.id, correct, streak)
                        game.feedback(
                            if (correct) Feedback.Kind.CORRECT else Feedback.Kind.WRONG
                        )
                        game.update {
                            var s2 = LearningEngine.answer(
                                it, question, correct, System.currentTimeMillis(), game.event
                            )
                            if (correct && game.event.kind == GameEvent.Kind.STUDY) {
                                s2 = EventEngine.advance(s2, game.event, 1)
                            }
                            s2
                        }
                        if (session.answered >= SESSION_LENGTH) finished = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    choice,
                    fontSize = 16.sp,
                    color = if (picked == null) MaterialTheme.colorScheme.onSurface
                    else Color.White
                )
            }
        }

        if (picked != null) {
            item {
                PanelCard {
                    val e = question.element
                    Text(
                        if (picked == question.answer) "正解"
                        else "不正解　答えは " + question.answer,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (picked == question.answer)
                            MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(6.dp))
                    SceneSpeech(
                        if (picked == question.answer) Scene.QUIZ_CORRECT else Scene.QUIZ_WRONG,
                        game.state.quizCount,
                        48
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pop(key = question) { Thumb(e.imageId, 64) }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(e.symbol + "　" + e.name, fontWeight = FontWeight.Bold)
                            Text(
                                e.english,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(e.property, fontSize = 14.sp)
                    Text(
                        "使われ方：" + e.uses,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!finished) {
                item {
                    Button(onClick = { draw() }, modifier = Modifier.fillMaxWidth()) {
                        Text("次の問題")
                    }
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }

    if (finished) {
        AlertDialog(
            onDismissRequest = { finished = false },
            title = { Text("この回の結果") },
            text = {
                Column {
                    Text(
                        session.correct.toString() + " / " + session.answered + " 問正解",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    LabeledRow("正答率", session.accuracy().toString() + " %")
                    LabeledRow("最長の連続正解", session.bestStreak.toString())
                    Spacer(Modifier.height(8.dp))
                    Text(session.comment(), fontSize = 14.sp)
                    if (session.missed.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("間違えた元素", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            session.missed.joinToString("、") { content.elementName(it) },
                            fontSize = 14.sp
                        )
                        Text(
                            "この数個は数分後にもう一度出ます",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { restart(dueOnly) }) { Text("もう一度") }
            },
            dismissButton = {
                TextButton(onClick = { finished = false; onClose() }) { Text("終わる") }
            }
        )
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
