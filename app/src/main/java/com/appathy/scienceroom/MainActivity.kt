package com.appathy.scienceroom

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.appathy.scienceroom.data.Content
import com.appathy.scienceroom.data.PlayerRepo
import com.appathy.scienceroom.data.PlayerState
import com.appathy.scienceroom.engine.EventEngine
import com.appathy.scienceroom.engine.GameEvent
import com.appathy.scienceroom.engine.MissionEngine
import com.appathy.scienceroom.ui.EncyclopediaScreen
import com.appathy.scienceroom.ui.HomeScreen
import com.appathy.scienceroom.ui.LabScreen
import com.appathy.scienceroom.ui.ProfileScreen
import com.appathy.scienceroom.ui.QuizScreen
import com.appathy.scienceroom.ui.ScienceRoomTheme
import com.appathy.scienceroom.ui.TutorialScreen
import com.appathy.scienceroom.ui.TechScreen
import com.appathy.scienceroom.ui.WorldScreen

/** ゲーム全体の状態を持つ。UIはここを読み、ここ経由で更新する */
class Game(private val ctx: Context) {
    val content: Content = Content.load(ctx)

    var state by mutableStateOf(PlayerRepo.load(ctx))
        private set

    /** 今週のイベント。週が変わればアプリ起動時に入れ替わる */
    val event: GameEvent

    init {
        val date = java.time.LocalDate.now()
        val epochDay = date.toEpochDay()
        event = EventEngine.current(content, state, epochDay)

        var next = MissionEngine.rollover(state, date.toString())
        next = EventEngine.rollover(next, event.week)
        if (next !== state) {
            state = next
            PlayerRepo.save(ctx, next)
        }
        ReviewReminder.sync(ctx)
    }

    val eventDaysLeft: Int
        get() = EventEngine.daysLeft(java.time.LocalDate.now().toEpochDay())

    fun update(transform: (PlayerState) -> PlayerState) {
        val next = transform(state)
        state = next
        PlayerRepo.save(ctx, next)
    }

    fun feedback(kind: Feedback.Kind) {
        Feedback.play(ctx, kind, state.soundOn, state.hapticOn)
    }

    fun replace(next: PlayerState) {
        state = next
        PlayerRepo.save(ctx, next)
    }

    fun reset() {
        PlayerRepo.clear(ctx)
        state = PlayerState()
    }
}

class MainActivity : ComponentActivity() {

    override fun onDestroy() {
        super.onDestroy()
        Feedback.release()
        Bgm.stop()
    }

    override fun onPause() {
        super.onPause()
        Bgm.stop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScienceRoomTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot()
                }
            }
        }
    }
}

private data class TabDef(val route: String, val icon: String, val label: String)

private val tabs = listOf(
    TabDef("home", "🏠", "ホーム"),
    TabDef("world", "🗺", "世界"),
    TabDef("lab", "🧪", "実験"),
    TabDef("tech", "🔬", "技術"),
    TabDef("book", "📖", "図鑑"),
    TabDef("quiz", "✏️", "クイズ")
)

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val game = remember { Game(ctx) }
    var route by remember { mutableStateOf("home") }
    var overlay by remember { mutableStateOf<String?>(null) }
    var showTutorial by remember { mutableStateOf(!game.state.tutorialDone) }

    LaunchedEffect(game.state.bgmOn) {
        if (game.state.bgmOn) Bgm.start() else Bgm.stop()
    }

    if (showTutorial) {
        TutorialScreen(game) { showTutorial = false }
        return
    }

    val navigate: (String) -> Unit = { target ->
        if (target == "profile") overlay = target
        else {
            overlay = null
            route = target
        }
    }

    if (overlay != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (overlay) {
                "profile" -> ProfileScreen(game) { overlay = null }
            }
        }
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = { route = t.route },
                        icon = { Text(t.icon, fontSize = 18.sp) },
                        label = { Text(t.label, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (route) {
                "home" -> HomeScreen(game, navigate)
                "world" -> WorldScreen(game, navigate)
                "lab" -> LabScreen(game, navigate)
                "tech" -> TechScreen(game, navigate)
                "book" -> EncyclopediaScreen(game, navigate)
                "quiz" -> QuizScreen(game) { route = "home" }
            }
        }
    }
}
