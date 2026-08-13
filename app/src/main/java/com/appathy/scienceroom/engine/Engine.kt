package com.appathy.scienceroom.engine

import com.appathy.scienceroom.data.Content
import com.appathy.scienceroom.data.Element
import com.appathy.scienceroom.data.ExperimentInput
import com.appathy.scienceroom.data.ExperimentResult
import com.appathy.scienceroom.data.FailureCause
import com.appathy.scienceroom.data.GameLocation
import com.appathy.scienceroom.data.PlayerState
import com.appathy.scienceroom.data.Rank
import com.appathy.scienceroom.data.Reaction
import com.appathy.scienceroom.data.Technology
import kotlin.math.abs
import kotlin.random.Random

/** 実験で使える器具 */
object Equipment {
    const val NONE = "none"
    const val POT = "pot"
    const val KILN = "kiln"
    const val CRUCIBLE = "crucible"
    const val BELLOWS_KILN = "bellows_kiln"

    fun label(id: String?): String = when (id) {
        POT -> "土器"
        KILN -> "炉"
        CRUCIBLE -> "るつぼ"
        BELLOWS_KILN -> "ふいご付き炉"
        else -> "なし"
    }

    fun available(state: PlayerState): List<String> {
        val list = mutableListOf(NONE)
        if (state.completedTech.contains("pottery")) list.add(POT)
        if (state.completedTech.contains("kiln")) list.add(KILN)
        if (state.completedTech.contains("crucible")) list.add(CRUCIBLE)
        if (state.completedTech.contains("bellows")) list.add(BELLOWS_KILN)
        return list
    }

    /** 選んだ器具が必要条件を満たすか */
    fun satisfies(selected: String?, required: String?): Boolean {
        if (required == null) return true
        val s = selected ?: NONE
        if (s == required) return true
        if (required == KILN && (s == BELLOWS_KILN || s == CRUCIBLE)) return true
        if (required == CRUCIBLE && s == BELLOWS_KILN) return false
        return false
    }

    /** 器具で到達できる上限温度 */
    fun maxTemp(id: String?): Int = when (id) {
        POT -> 400
        KILN -> 1300
        CRUCIBLE -> 1400
        BELLOWS_KILN -> 1600
        else -> 800
    }
}

/** 週替わりのテーマ。サーバーを持たず、週番号から決まった内容を組み立てる */
data class GameEvent(
    val week: Long,
    val kind: Kind,
    val title: String,
    val description: String,
    val targetId: String,
    val goal: Int,
    val rewardExp: Int
) {
    enum class Kind { EXPLORE, STUDY, EXPERIMENT }
}

object EventEngine {

    /** 1970-01-01 は木曜。月曜始まりにするため3日ずらす */
    fun weekIndex(epochDay: Long): Long = (epochDay + 3) / 7

    /** その週の残り日数。月曜なら7、日曜なら1 */
    fun daysLeft(epochDay: Long): Int = (7 - ((epochDay + 3) % 7)).toInt()

    private val studyTargets = listOf(
        "非金属" to "気体と炎の週",
        "遷移金属" to "金属をきわめる週",
        "アルカリ金属" to "水に触れる金属の週",
        "アルカリ土類金属" to "石と骨をつくる週",
        "半金属" to "石英とガラスの週",
        "ハロゲン" to "塩をつくる週"
    )

    fun current(content: Content, state: PlayerState, epochDay: Long): GameEvent {
        val week = weekIndex(epochDay)
        val rnd = Random(week)
        // 行けない地域を指定しても参加できないので、解禁済みから選ぶ
        val open = content.locations.filter { state.unlockedLocations.contains(it.id) }
            .ifEmpty { content.locations }

        return when (rnd.nextInt(3)) {
            0 -> {
                val loc = open[rnd.nextInt(open.size)]
                GameEvent(
                    week, GameEvent.Kind.EXPLORE,
                    loc.name + "の当たり週",
                    "今週は" + loc.name + "で素材が見つかりやすく、一度に多く採れます",
                    loc.id, 10, 120
                )
            }
            1 -> {
                val pair = studyTargets[rnd.nextInt(studyTargets.size)]
                GameEvent(
                    week, GameEvent.Kind.STUDY,
                    pair.second,
                    "今週は" + pair.first + "が優先して出題され、正解の経験値が2倍になります",
                    pair.first, 20, 120
                )
            }
            else -> GameEvent(
                week, GameEvent.Kind.EXPERIMENT,
                "実験づくしの週",
                "今週は実験で得られる経験値が5割増しになります。失敗しても増えます",
                "", 12, 120
            )
        }
    }

    /** 週が変わっていれば進捗をリセットする */
    fun rollover(state: PlayerState, week: Long): PlayerState {
        if (state.eventWeek == week) return state
        return state.copy(eventWeek = week, eventCount = 0, eventClaimed = false)
    }

    /** 目標達成した瞬間に一度だけ報酬を渡す */
    fun advance(state: PlayerState, event: GameEvent, step: Int): PlayerState {
        if (step <= 0) return state
        val count = state.eventCount + step
        var next = state.copy(eventCount = count)
        if (!state.eventClaimed && count >= event.goal) {
            next = next.copy(
                eventClaimed = true,
                clearedEvents = state.clearedEvents + 1,
                exp = next.exp + event.rewardExp
            )
        }
        return next
    }
}

data class ExploreOutcome(
    val locationId: String,
    val foundMaterialId: String?,
    val amount: Int,
    val isNew: Boolean,
    val message: String
)

object ExplorationEngine {

    fun explore(
        content: Content,
        state: PlayerState,
        location: GameLocation,
        event: GameEvent? = null
    ): ExploreOutcome {
        val boosted = event != null &&
            event.kind == GameEvent.Kind.EXPLORE && event.targetId == location.id
        val seed = (state.exploreCount * 31 + location.id.hashCode()).toLong()
        val rnd = Random(seed)

        val candidates = location.materials.mapNotNull { content.materialById[it] }
        if (candidates.isEmpty()) {
            return ExploreOutcome(location.id, null, 0, false, "この地域では何も見つからなかった")
        }

        // 希少度が低いものほど出やすい
        val weighted = candidates.flatMap { m -> List((7 - m.rarity).coerceAtLeast(1)) { m.id } }
        val pickedId = weighted[rnd.nextInt(weighted.size)]
        val picked = content.materialById[pickedId]!!

        // 難易度が高い地域ほど空振りしやすい
        var missChance = (location.difficulty - 1) * 8 + (picked.rarity - 1) * 6
        if (boosted) missChance = (missChance - 20).coerceAtLeast(0)
        if (rnd.nextInt(100) < missChance) {
            return ExploreOutcome(
                location.id, null, 0, false,
                "めぼしいものは見つからなかった。もう一度探してみよう"
            )
        }

        val amount = 1 + rnd.nextInt(3 - (picked.rarity / 3).coerceAtMost(2)) +
            if (boosted) 1 else 0
        val isNew = !state.discoveredMaterials.contains(picked.id)
        val message = if (isNew) "「${picked.name}」を初めて発見した" else "「${picked.name}」を採取した"
        return ExploreOutcome(location.id, picked.id, amount, isNew, message)
    }

    fun applyOutcome(state: PlayerState, outcome: ExploreOutcome): PlayerState {
        var s = state.copy(exploreCount = state.exploreCount + 1)
        val id = outcome.foundMaterialId ?: return s
        val inv = s.inventory.toMutableMap()
        inv[id] = (inv[id] ?: 0) + outcome.amount
        s = s.copy(
            inventory = inv,
            discoveredMaterials = s.discoveredMaterials + id,
            exp = s.exp + if (outcome.isNew) 15 else 3
        )
        return s
    }
}

object HintEngine {
    /** 成績に応じてヒントの詳しさを自動調整する。手動設定のときは設定値をそのまま使う */
    fun effectiveLevel(state: PlayerState): Int {
        if (!state.autoHint) return state.hintLevel.coerceIn(1, 5)
        val exp = state.experimentCount
        if (exp < 3) return 3
        val success = state.successRate()
        val quiz = if (state.quizCount == 0) 50 else state.quizCorrect * 100 / state.quizCount
        val score = (success + quiz) / 2
        return when {
            score >= 75 -> 1
            score >= 60 -> 2
            score >= 40 -> 3
            score >= 25 -> 4
            else -> 5
        }
    }
}

object SerendipityEngine {
    /** 定義された反応にならない組み合わせから、偶然に研究候補を見つけることがある */
    fun lead(content: Content, state: PlayerState, selected: List<String>): Reaction? {
        val candidates = content.reactions.filter { r ->
            !state.discoveredReactions.contains(r.id) &&
                r.inputs.any { selected.contains(it) }
        }
        if (candidates.isEmpty()) return null
        val seed = (state.experimentCount * 131L + selected.sorted().joinToString().hashCode())
        val rnd = Random(seed)
        if (rnd.nextInt(100) >= 35) return null
        return candidates[rnd.nextInt(candidates.size)]
    }
}

object RatioEngine {

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    private fun normalize(values: List<Int>): List<Int> {
        val g = values.fold(0) { acc, v -> gcd(acc, v) }
        if (g <= 1) return values
        return values.map { it / g }
    }

    /** 0 なら一致。数字が大きいほど比のずれが大きい */
    fun deviation(reaction: Reaction, input: ExperimentInput): Int {
        val want = reaction.ratios ?: return 0
        if (want.isEmpty()) return 0
        val given = normalize(reaction.inputs.map { input.amount(it) })
        val target = normalize(reaction.inputs.map { want[it] ?: 1 })
        if (given == target) return 0
        return given.indices.sumOf { i ->
            kotlin.math.abs(given[i] - target[i])
        }
    }

    fun describe(content: Content, reaction: Reaction): String {
        val want = reaction.ratios ?: return ""
        if (want.isEmpty()) return ""
        return reaction.inputs.joinToString(" : ") {
            content.materialName(it) + " " + (want[it] ?: 1)
        }
    }
}

object ExperimentEngine {

    /** 判定ルールの版。ノートに残しておくと、後でルールが変わっても結果を読み解ける */
    const val RULE_VERSION = "1"

    fun run(
        content: Content,
        state: PlayerState,
        input: ExperimentInput,
        event: GameEvent? = null
    ): ExperimentResult {
        val expBoost = event != null && event.kind == GameEvent.Kind.EXPERIMENT
        val selected = input.materials.sorted()

        if (selected.isEmpty()) {
            return ExperimentResult(
                Rank.D, null, null, "条件不成立",
                "素材が入っていない", "実験には少なくとも1つの素材が要る", "—",
                listOf(FailureCause("素材", "高")), "図鑑で素材を選んでから始めよう", 0, emptyList(), null
            )
        }

        val level = HintEngine.effectiveLevel(state)

        val exact = content.reactions.firstOrNull { it.inputs.sorted() == selected }
        if (exact == null) {
            val lead = SerendipityEngine.lead(content, state, selected)
            if (lead != null && !state.researchLeads.contains(lead.id)) {
                return ExperimentResult(
                    Rank.C, null, null, "予想外の結果",
                    "狙った変化は起きなかったが、器の底にわずかな手がかりが残った",
                    "偶然の組み合わせから、まだ確かめていない反応の気配が見つかった。" +
                        "科学の発見はこうした寄り道から生まれることがある",
                    "—",
                    emptyList(),
                    "研究候補「${lead.name}」が開いた。必要な素材は" +
                        lead.inputs.joinToString("、") { content.materialName(it) } + "だ",
                    6, listOf("新しい研究候補「${lead.name}」を書き留めた"), null, lead.id
                )
            }
            val near = content.reactions.filter { r -> r.inputs.any { selected.contains(it) } }
            val hint = when {
                near.isEmpty() -> "この組み合わせに手がかりはなさそうだ。図鑑で素材の関連を見てみよう"
                level <= 1 -> "組み合わせを見直してみよう"
                level == 2 -> "素材の数か種類が合っていないかもしれない"
                else -> "「${content.materialName(near.first().inputs.first())}」を含む組み合わせに何かありそうだ"
            }
            return ExperimentResult(
                Rank.C, null, null, "変化なし",
                "混ぜても温めても、見た目は変わらなかった",
                "この組み合わせでは反応が起こる条件がそろっていない。組み合わせそのものを変えてみよう",
                "—",
                listOf(FailureCause("材料の組み合わせ", "高")),
                hint, 2, emptyList(), null
            )
        }

        val short = selected.filter { (state.inventory[it] ?: 0) < input.amount(it) }
        if (short.isNotEmpty()) {
            return ExperimentResult(
                Rank.D, exact, null, "素材不足",
                "指定した分量に足りず、実験を始められなかった",
                "手持ちより多い量を入れようとしている。探索して集めるか、量を減らそう",
                "—",
                listOf(FailureCause("素材の所持数", "高")),
                "不足：" + short.joinToString("、") { content.materialName(it) },
                0, emptyList(), null
            )
        }

        val ratioDeviation = RatioEngine.deviation(exact, input)
        val tempOk = input.temperature in exact.minTemp..exact.maxTemp
        val durOk = input.duration >= exact.minDuration
        val equipOk = Equipment.satisfies(input.equipment, exact.equipment)
        val reachable = input.temperature <= Equipment.maxTemp(input.equipment)

        val causes = mutableListOf<FailureCause>()
        if (!tempOk) {
            val gap = if (input.temperature < exact.minTemp) exact.minTemp - input.temperature
            else input.temperature - exact.maxTemp
            causes.add(FailureCause("温度条件", if (gap > 200) "高" else if (gap > 50) "中" else "低"))
        }
        if (!durOk) {
            val gap = exact.minDuration - input.duration
            causes.add(FailureCause("加熱時間", if (gap > 2) "高" else "中"))
        }
        if (!equipOk) causes.add(FailureCause("器具", "高"))
        if (ratioDeviation > 0) {
            causes.add(FailureCause("材料の比", if (ratioDeviation >= 3) "高" else "中"))
        }
        if (!reachable) causes.add(FailureCause("器具の到達温度", "高"))

        if (causes.isEmpty()) {
            val center = (exact.minTemp + exact.maxTemp) / 2
            val span = (exact.maxTemp - exact.minTemp).coerceAtLeast(1)
            val precise = abs(input.temperature - center) <= span / 4 &&
                input.duration >= exact.minDuration + 1
            val rank = if (precise) Rank.S else Rank.A
            val baseGain = exact.exp + if (rank == Rank.S) 10 else 0
            val gained = if (expBoost) baseGain * 3 / 2 else baseGain
            val known = mutableListOf<String>()
            if (!state.discoveredReactions.contains(exact.id)) known.add("新しい反応「${exact.name}」を記録した")
            val product = content.materialById[exact.product]
            if (product != null && !state.discoveredMaterials.contains(product.id)) {
                known.add("新しい素材「${product.name}」を手に入れた")
            }
            return ExperimentResult(
                rank, exact, exact.product,
                if (rank == Rank.S) "実験成功（完全）" else "実験成功",
                exact.observation, exact.explanation, exact.principle,
                emptyList(),
                "", gained, known,
                if (exact.danger >= 4) "危険度が高い操作のため、ゲーム内シミュレーションとして扱っている" else null
            )
        }

        val rank = if (causes.size == 1) Rank.B else Rank.D
        val hint = buildHint(level, causes, exact, input, content)
        val title = if (rank == Rank.B) "部分成功" else "実験失敗"
        val observation = if (rank == Rank.B)
            "変化のきざしはあったが、途中で止まってしまった"
        else "見たかった変化は起こらなかった"

        return ExperimentResult(
            rank, exact, null, title, observation,
            "材料の組み合わせは合っている。足りないのは条件のほうだ",
            exact.principle, causes, hint,
            if (expBoost) (if (rank == Rank.B) 12 else 6) else (if (rank == Rank.B) 8 else 4),
            emptyList(), null
        )
    }

    private fun buildHint(
        level: Int,
        causes: List<FailureCause>,
        r: Reaction,
        input: ExperimentInput,
        content: Content
    ): String {
        val top = causes.first().label
        return when (level.coerceIn(1, 5)) {
            1 -> "条件のどこかを見直してみよう"
            2 -> "${top}が関係していそうだ"
            3 -> when (top) {
                "温度条件" -> if (input.temperature < r.minTemp) "今の温度では足りていない可能性がある"
                else "温度が高すぎる可能性がある"
                "加熱時間" -> "もう少し長く加熱する必要がありそうだ"
                "材料の比" -> "材料はそろっているが、入れる量のつり合いが取れていないようだ"
                else -> "使っている器具では条件を満たせていない可能性がある"
            }
            4 -> when (top) {
                "温度条件" -> "温度を変えて再実験してみよう"
                "加熱時間" -> "時間を延ばして再実験してみよう"
                "材料の比" -> "どれかの量を増やすか減らすかして、比を変えてみよう"
                else -> "別の器具を使って再実験してみよう"
            }
            else -> "「${r.name}」は${r.minTemp}〜${r.maxTemp}℃、${r.minDuration}以上の時間、" +
                "器具は${Equipment.label(r.equipment)}が必要だ" +
                if (r.ratios.isNullOrEmpty()) "" else "。材料の比は " + RatioEngine.describe(content, r)
        }
    }

    fun applyResult(content: Content, state: PlayerState, input: ExperimentInput, result: ExperimentResult): PlayerState {
        var s = state.copy(
            experimentCount = state.experimentCount + 1,
            exp = state.exp + result.gainedExp
        )
        if (result.rank == Rank.S || result.rank == Rank.A) {
            val inv = s.inventory.toMutableMap()
            input.materials.forEach { id ->
                val left = (inv[id] ?: 0) - input.amount(id)
                if (left <= 0) inv.remove(id) else inv[id] = left
            }
            val productId = result.productId
            if (productId != null) inv[productId] = (inv[productId] ?: 0) + 1
            s = s.copy(
                inventory = inv,
                successCount = s.successCount + 1,
                discoveredReactions = s.discoveredReactions + (result.reaction?.id ?: ""),
                discoveredMaterials = if (productId != null) s.discoveredMaterials + productId
                else s.discoveredMaterials
            )
        } else {
            s = s.copy(
                failCount = s.failCount + 1,
                lastFailureIds = (listOf(result.reaction?.id ?: "unknown") + s.lastFailureIds).take(5)
            )
        }
        val lead = result.leadReactionId
        if (lead != null) s = s.copy(researchLeads = s.researchLeads + lead)

        val log = com.appathy.scienceroom.data.ExperimentLog(
            time = System.currentTimeMillis(),
            materials = input.materials,
            quantities = input.quantities,
            temperature = input.temperature,
            duration = input.duration,
            equipment = input.equipment,
            rank = result.rank.name,
            title = result.title,
            reactionId = result.reaction?.id,
            productId = result.productId,
            causes = result.causes.map { it.label + "：" + it.weight },
            ruleVersion = RULE_VERSION
        )
        s = s.copy(notebook = (listOf(log) + s.notebook).take(50))
        return s
    }
}

data class TechStatus(
    val tech: Technology,
    val completed: Boolean,
    val unlocked: Boolean,
    val missingTech: List<String>,
    val missingMaterials: List<String>,
    val missingElements: List<String>,
    val missingReactions: List<String>
) {
    val ready: Boolean
        get() = !completed && unlocked && missingMaterials.isEmpty() &&
            missingElements.isEmpty() && missingReactions.isEmpty()
}

object TechnologyEngine {

    fun status(content: Content, state: PlayerState, tech: Technology): TechStatus {
        val missingTech = tech.requiredTech.filter { !state.completedTech.contains(it) }
        val missingMaterials = tech.requiredMaterials.filter { !state.has(it) }
        val missingElements = tech.requiredElements.filter { !state.knownElements.contains(it) }
        val missingReactions = tech.requiredReactions.filter { !state.discoveredReactions.contains(it) }
        return TechStatus(
            tech = tech,
            completed = state.completedTech.contains(tech.id),
            unlocked = missingTech.isEmpty(),
            missingTech = missingTech,
            missingMaterials = missingMaterials,
            missingElements = missingElements,
            missingReactions = missingReactions
        )
    }

    fun all(content: Content, state: PlayerState): List<TechStatus> =
        content.technologies.map { status(content, state, it) }

    fun complete(content: Content, state: PlayerState, tech: Technology): PlayerState {
        val st = status(content, state, tech)
        if (!st.ready) return state
        val inv = state.inventory.toMutableMap()
        tech.requiredMaterials.forEach { id ->
            val left = (inv[id] ?: 0) - 1
            if (left <= 0) inv.remove(id) else inv[id] = left
        }
        tech.unlocksMaterials.forEach { id -> inv[id] = (inv[id] ?: 0) + 1 }
        return state.copy(
            inventory = inv,
            completedTech = state.completedTech + tech.id,
            unlockedLocations = state.unlockedLocations + tech.unlocksLocations,
            discoveredMaterials = state.discoveredMaterials + tech.unlocksMaterials,
            exp = state.exp + tech.difficulty * 30
        )
    }
}

enum class QuizMode(val id: String, val label: String) {
    SYMBOL_TO_NAME("sym2name", "記号 → 名前"),
    NAME_TO_SYMBOL("name2sym", "名前 → 記号"),
    IMAGE_TO_ELEMENT("image", "イメージ → 元素"),
    PROPERTY_TO_ELEMENT("property", "性質 → 元素")
}

data class Question(
    val mode: QuizMode,
    val element: Element,
    val prompt: String,
    val showImage: Boolean,
    val choices: List<String>,
    val answer: String
)

object LearningEngine {

    private val intervals = longArrayOf(
        10L * 60 * 1000,
        24L * 60 * 60 * 1000,
        3L * 24 * 60 * 60 * 1000,
        7L * 24 * 60 * 60 * 1000,
        30L * 24 * 60 * 60 * 1000
    )

    fun nextQuestion(
        content: Content,
        state: PlayerState,
        mode: QuizMode?,
        now: Long,
        dueOnly: Boolean = false,
        event: GameEvent? = null
    ): Question {
        val focus = if (event != null && event.kind == GameEvent.Kind.STUDY) event.targetId else null
        val chosenMode = mode ?: QuizMode.entries.random()
        val due = content.elements.filter { e ->
            val l = state.learning[e.id]
            l != null && l.nextReview in 1..now
        }
        val pool = if (dueOnly && due.size >= 4) due else content.elements
        val target = pool.maxByOrNull { e ->
            val l = state.learning[e.id]
            val risk = l?.forgettingRisk(now) ?: 1.0
            val bonus = if (focus != null && e.category == focus) 0.5 else 0.0
            risk + bonus + Random.nextDouble() * 0.25
        } ?: pool.first()

        val distractors = content.elements.filter { it.id != target.id }.shuffled().take(3)
        return when (chosenMode) {
            QuizMode.SYMBOL_TO_NAME -> Question(
                chosenMode, target, target.symbol, false,
                (distractors.map { it.name } + target.name).shuffled(), target.name
            )
            QuizMode.NAME_TO_SYMBOL -> Question(
                chosenMode, target, target.name, false,
                (distractors.map { it.symbol } + target.symbol).shuffled(), target.symbol
            )
            QuizMode.IMAGE_TO_ELEMENT -> Question(
                chosenMode, target, "この絵が表す元素は？", true,
                (distractors.map { it.name } + target.name).shuffled(), target.name
            )
            QuizMode.PROPERTY_TO_ELEMENT -> Question(
                chosenMode, target, target.property, false,
                (distractors.map { it.name } + target.name).shuffled(), target.name
            )
        }
    }

    fun answer(
        state: PlayerState,
        q: Question,
        correct: Boolean,
        now: Long,
        event: GameEvent? = null
    ): PlayerState {
        val doubled = event != null && event.kind == GameEvent.Kind.STUDY &&
            q.element.category == event.targetId
        val prev = state.learning[q.element.id] ?: com.appathy.scienceroom.data.ElementLearning()
        val mode = q.mode.id
        val correctMap = prev.correct.toMutableMap()
        val totalMap = prev.total.toMutableMap()
        totalMap[mode] = (totalMap[mode] ?: 0) + 1
        if (correct) correctMap[mode] = (correctMap[mode] ?: 0) + 1

        val streak = if (correct) (prev.streak + 1).coerceAtMost(intervals.size - 1) else 0
        // 間違えたものは数分後にもう一度。正解なら段階に応じて先へ送る
        val wait = if (correct) intervals[streak] else 3L * 60 * 1000
        val updated = prev.copy(
            correct = correctMap,
            total = totalMap,
            lastReviewed = now,
            nextReview = now + wait,
            streak = streak
        )
        val learning = state.learning.toMutableMap()
        learning[q.element.id] = updated

        return state.copy(
            learning = learning,
            quizCount = state.quizCount + 1,
            quizCorrect = state.quizCorrect + if (correct) 1 else 0,
            knownElements = if (correct) state.knownElements + q.element.id else state.knownElements,
            exp = state.exp + if (correct) (if (doubled) 10 else 5) else 1
        )
    }

    fun weakElements(content: Content, state: PlayerState, now: Long, limit: Int = 3): List<Element> =
        content.elements
            .sortedByDescending { state.learning[it.id]?.forgettingRisk(now) ?: 1.0 }
            .take(limit)
}

enum class PlanStyle(val id: String, val label: String) {
    SHORTEST("shortest", "最短"),
    LEARNING("learning", "学習重視"),
    EXPLORE("explore", "探索重視"),
    EXPERIMENT("experiment", "実験重視")
}

data class PlanStep(val kind: String, val label: String, val detail: String, val route: String)

object PlanEngine {

    /** 目標に必要な未完成の技術を依存順に並べる（仕様書22節の逆算） */
    fun chain(content: Content, state: PlayerState, goalId: String): List<Technology> {
        val out = LinkedHashSet<String>()

        fun visit(id: String, seen: Set<String>) {
            if (seen.contains(id) || out.contains(id)) return
            val tech = content.techById[id] ?: return
            tech.requiredTech.forEach { visit(it, seen + id) }
            if (!state.completedTech.contains(id)) out.add(id)
        }

        visit(goalId, emptySet())
        return out.mapNotNull { content.techById[it] }
    }

    fun plan(
        content: Content,
        state: PlayerState,
        goalId: String,
        style: PlanStyle
    ): List<PlanStep> {
        val steps = LinkedHashMap<String, PlanStep>()

        chain(content, state, goalId).forEach { tech ->
            val st = TechnologyEngine.status(content, state, tech)

            st.missingElements.forEach { id ->
                steps["e:" + id] = PlanStep(
                    "学習",
                    content.elementName(id) + "を覚える",
                    "「" + tech.name + "」に必要な知識",
                    "quiz"
                )
            }

            st.missingMaterials.forEach { id ->
                val where = content.locations.firstOrNull {
                    it.materials.contains(id) && state.unlockedLocations.contains(it.id)
                }
                val maker = content.reactions.firstOrNull { it.product == id }
                if (where != null) {
                    steps["m:" + id] = PlanStep(
                        "探索",
                        where.name + "で" + content.materialName(id) + "を集める",
                        "「" + tech.name + "」の材料",
                        "world"
                    )
                } else if (maker != null) {
                    steps["m:" + id] = PlanStep(
                        "実験",
                        content.materialName(id) + "を「" + maker.name + "」でつくる",
                        "「" + tech.name + "」の材料",
                        "lab"
                    )
                } else {
                    steps["m:" + id] = PlanStep(
                        "探索",
                        content.materialName(id) + "を手に入れる",
                        "入手できる地域がまだ解禁されていない",
                        "world"
                    )
                }
            }

            st.missingReactions.forEach { id ->
                val r = content.reactionById[id]
                steps["r:" + id] = PlanStep(
                    "実験",
                    "「" + (r?.name ?: id) + "」を実験で確かめる",
                    if (r == null) "" else r.minTemp.toString() + "〜" + r.maxTemp +
                        "℃／器具は" + Equipment.label(r.equipment),
                    "lab"
                )
            }

            steps["t:" + tech.id] = PlanStep(
                "研究",
                tech.name + "を完成させる",
                "難易度 " + tech.difficulty,
                "tech"
            )
        }

        val list = steps.values.toList()
        val priority: (PlanStep) -> Int = { step ->
            when (style) {
                PlanStyle.SHORTEST -> 0
                PlanStyle.LEARNING -> if (step.kind == "学習") 0 else 1
                PlanStyle.EXPLORE -> if (step.kind == "探索") 0 else 1
                PlanStyle.EXPERIMENT -> if (step.kind == "実験") 0 else 1
            }
        }
        return list.sortedBy(priority)
    }
}

object CivilizationEngine {

    private val branches: List<Pair<String, List<String>>> = listOf(
        "窯業" to listOf("kiln", "pottery", "lime", "plaster", "masonry", "glaze", "porcelain"),
        "金属" to listOf("crucible", "bellows", "bronze", "iron", "casting", "forging", "steel"),
        "ガラス・光学" to listOf("glass", "glassware", "lens", "microscope", "telescope"),
        "合金・分離" to listOf("zinc", "lead", "brass", "solder", "cupellation"),
        "化学" to listOf("fire", "charcoal", "salt", "lye", "distillation")
    )

    fun scores(state: PlayerState): List<Pair<String, Int>> =
        branches.map { pair ->
            pair.first to pair.second.count { state.completedTech.contains(it) }
        }

    fun label(state: PlayerState): String {
        val s = scores(state)
        val top = s.maxByOrNull { it.second }
        if (top == null || top.second == 0) return "まだ方向が定まっていない"
        val tied = s.filter { it.second == top.second }
        if (tied.size > 1) return tied.joinToString("と") { it.first } + "を並行して進める文明"
        return top.first + "に強い文明"
    }
}

data class Suggestion(val title: String, val reason: String, val route: String)

object RecommendEngine {

    fun top(content: Content, state: PlayerState, now: Long): List<Suggestion> {
        val out = mutableListOf<Suggestion>()
        val statuses = TechnologyEngine.all(content, state)

        val ready = statuses.firstOrNull { it.ready }
        if (ready != null) {
            out.add(Suggestion("「${ready.tech.name}」を完成させる", "必要な条件がすべてそろっている", "tech"))
        }

        val nextTech = statuses.firstOrNull { !it.completed && it.unlocked }
        if (nextTech != null && out.size < 3) {
            when {
                nextTech.missingElements.isNotEmpty() -> out.add(
                    Suggestion(
                        "${content.elementName(nextTech.missingElements.first())}を覚える",
                        "「${nextTech.tech.name}」に必要な知識が足りていない",
                        "quiz"
                    )
                )
                nextTech.missingMaterials.isNotEmpty() -> {
                    val need = nextTech.missingMaterials.first()
                    val where = content.locations.firstOrNull {
                        it.materials.contains(need) && state.unlockedLocations.contains(it.id)
                    }
                    out.add(
                        Suggestion(
                            if (where != null) "${where.name}を探索して${content.materialName(need)}を集める"
                            else "${content.materialName(need)}をつくる実験をする",
                            "「${nextTech.tech.name}」に必要な素材が足りていない",
                            if (where != null) "world" else "lab"
                        )
                    )
                }
                nextTech.missingReactions.isNotEmpty() -> {
                    val r = content.reactionById[nextTech.missingReactions.first()]
                    out.add(
                        Suggestion(
                            "「${r?.name ?: "新しい反応"}」を実験で確かめる",
                            "「${nextTech.tech.name}」にはこの反応の記録が要る",
                            "lab"
                        )
                    )
                }
            }
        }

        if (out.size < 3) {
            val weak = LearningEngine.weakElements(content, state, now, 1).firstOrNull()
            if (weak != null) {
                out.add(Suggestion("${weak.name}（${weak.symbol}）を復習する", "いま最も忘れやすい元素", "quiz"))
            }
        }

        if (out.size < 3) {
            val loc = content.locations.firstOrNull { state.unlockedLocations.contains(it.id) }
            if (loc != null) out.add(Suggestion("${loc.name}を探索する", "素材を増やすと実験の幅が広がる", "world"))
        }

        return out.take(3)
    }

    fun currentResearch(content: Content, state: PlayerState): TechStatus? =
        TechnologyEngine.all(content, state).firstOrNull { !it.completed && it.unlocked }
}

data class Title(
    val name: String,
    val condition: String,
    val category: String,
    val current: Int,
    val goal: Int
) {
    val achieved: Boolean get() = current >= goal
    fun ratio(): Float = (current.toFloat() / goal).coerceIn(0f, 1f)
}

object TitleEngine {

    val categories = listOf("学習", "探索", "実験", "技術", "続ける")

    fun all(content: Content, state: PlayerState): List<Title> {
        val sRank = state.notebook.count { it.rank == "S" }
        val activeDays = state.history.count { !it.isEmpty() }
        val principles = state.discoveredReactions
            .mapNotNull { content.reactionById[it]?.principle }.distinct().size
        val physical = state.discoveredReactions
            .count { content.reactionById[it]?.changeType == "物理変化" }

        return listOf(
            Title("元素をひとつ覚えた", "元素を1つ覚える", "学習", state.knownElements.size, 1),
            Title("十を数える", "元素を10覚える", "学習", state.knownElements.size, 10),
            Title("元素をそらんじる人", "全元素を覚える", "学習",
                state.knownElements.size, content.elements.size),
            Title("問いに慣れた人", "クイズに100問答える", "学習", state.quizCount, 100),

            Title("外へ出た人", "探索を1回する", "探索", state.exploreCount, 1),
            Title("採集の達人", "素材を20種見つける", "探索", state.discoveredMaterials.size, 20),
            Title("すべてを見た人", "全素材を見つける", "探索",
                state.discoveredMaterials.size, content.materials.size),
            Title("地の果てまで", "地域を8つ解禁する", "探索", state.unlockedLocations.size, 8),

            Title("手を動かす人", "実験を10回行う", "実験", state.experimentCount, 10),
            Title("失敗を数えた人", "失敗を5回記録する", "実験", state.failCount, 5),
            Title("精度の人", "S判定を1回出す", "実験", sRank, 1),
            Title("反応の記録者", "反応を10件記録する", "実験",
                state.discoveredReactions.size, 10),
            Title("寄り道の名手", "研究候補を3件見つける", "実験", state.researchLeads.size, 3),
            Title("原理を並べる人", "5種類の科学原理に触れる", "実験", principles, 5),
            Title("変化を見分ける人", "物理変化の反応を3件記録する", "実験", physical, 3),

            Title("火をつかう者", "火起こしを完成させる", "技術",
                if (state.completedTech.contains("fire")) 1 else 0, 1),
            Title("焼き物の民", "土器を完成させる", "技術",
                if (state.completedTech.contains("pottery")) 1 else 0, 1),
            Title("金属の民", "製鉄を完成させる", "技術",
                if (state.completedTech.contains("iron")) 1 else 0, 1),
            Title("光を曲げる人", "レンズを完成させる", "技術",
                if (state.completedTech.contains("lens")) 1 else 0, 1),
            Title("文明の設計者", "技術をすべて完成させる", "技術",
                state.completedTech.size, content.technologies.size),

            Title("週の常連", "週替わりの目標を3回達成する", "続ける", state.clearedEvents, 3),
            Title("三日坊主をこえた", "4日以上活動する", "続ける", activeDays, 4),
            Title("ひと月の人", "20日以上活動する", "続ける", activeDays, 20)
        )
    }

    fun current(content: Content, state: PlayerState): String =
        all(content, state).lastOrNull { it.achieved }?.name ?: "見習い"

    fun achievedCount(content: Content, state: PlayerState): Int =
        all(content, state).count { it.achieved }
}

data class Skill(val name: String, val value: Int, val note: String)

object SkillEngine {
    /** 仕様書28節の科学的思考力。すべて0〜100 */
    fun compute(content: Content, state: PlayerState): List<Skill> {
        fun pct(a: Int, b: Int): Int = if (b == 0) 0 else (a * 100 / b).coerceIn(0, 100)

        val knowledge = pct(state.knownElements.size, content.elements.size)
        val observation = pct(state.discoveredMaterials.size, content.materials.size)
        val hypothesis = pct(state.discoveredReactions.size, content.reactions.size)
        val design = state.successRate()
        val interpretation =
            if (state.quizCount == 0) 0 else state.quizCorrect * 100 / state.quizCount
        val causal =
            if (state.failCount == 0) 0
            else pct(state.successCount, state.successCount + state.failCount)
        val application = pct(state.completedTech.size, content.technologies.size)

        return listOf(
            Skill("知識量", knowledge, "覚えた元素の割合"),
            Skill("観察力", observation, "見つけた素材の割合"),
            Skill("仮説形成", hypothesis, "記録した反応の割合"),
            Skill("実験設計", design, "実験の成功率"),
            Skill("結果解釈", interpretation, "クイズの正答率"),
            Skill("原因分析", causal, "失敗を経て成功に至った割合"),
            Skill("応用力", application, "完成させた技術の割合")
        )
    }

    fun total(skills: List<Skill>): Int =
        if (skills.isEmpty()) 0 else skills.sumOf { it.value } / skills.size
}

object RoadmapEngine {
    /** 前提技術の深さから段（tier）を求める。分岐したツリーを段ごとに並べるために使う */
    fun tiers(content: Content): Map<String, Int> {
        val result = mutableMapOf<String, Int>()

        fun depth(id: String, seen: Set<String>): Int {
            result[id]?.let { return it }
            if (seen.contains(id)) return 0
            val tech = content.techById[id] ?: return 0
            val d = if (tech.requiredTech.isEmpty()) 0
            else (tech.requiredTech.maxOfOrNull { depth(it, seen + id) } ?: -1) + 1
            result[id] = d
            return d
        }

        content.technologies.forEach { depth(it.id, emptySet()) }
        return result
    }

    /** この技術から派生する技術 */
    fun children(content: Content, id: String): List<Technology> =
        content.technologies.filter { it.requiredTech.contains(id) }
}

data class Mission(val text: String, val current: Int, val goal: Int) {
    val done: Boolean get() = current >= goal
}

object MissionEngine {

    fun today(state: PlayerState): List<Mission> {
        val d = state.daily
        return listOf(
            Mission(
                "元素クイズに5問正解する",
                (state.quizCorrect - d.quizCorrectBase).coerceAtLeast(0), 5
            ),
            Mission(
                "探索を3回する",
                (state.exploreCount - d.exploreBase).coerceAtLeast(0), 3
            ),
            Mission(
                "実験を2回行う",
                (state.experimentCount - d.experimentBase).coerceAtLeast(0), 2
            )
        )
    }

    /** 日付が変わっていれば前日分を履歴に確定し、基準値を今の値で置き直す */
    fun rollover(state: PlayerState, today: String): PlayerState {
        val prev = state.daily
        if (prev.date == today) return state

        val history = state.history.toMutableList()
        if (prev.date.isNotEmpty()) {
            val stat = com.appathy.scienceroom.data.DailyStat(
                date = prev.date,
                quizAnswered = (state.quizCount - prev.quizCountBase).coerceAtLeast(0),
                quizCorrect = (state.quizCorrect - prev.quizCorrectBase).coerceAtLeast(0),
                experiments = (state.experimentCount - prev.experimentBase).coerceAtLeast(0),
                successes = (state.successCount - prev.successBase).coerceAtLeast(0),
                explores = (state.exploreCount - prev.exploreBase).coerceAtLeast(0),
                newElements = (state.knownElements.size - prev.knownElementsBase)
                    .coerceAtLeast(0),
                newMaterials = (state.discoveredMaterials.size - prev.materialsBase)
                    .coerceAtLeast(0)
            )
            if (!stat.isEmpty()) history.add(stat)
        }

        return state.copy(
            history = history.takeLast(60),
            daily = com.appathy.scienceroom.data.DailyProgress(
                date = today,
                quizCorrectBase = state.quizCorrect,
                quizCountBase = state.quizCount,
                exploreBase = state.exploreCount,
                experimentBase = state.experimentCount,
                successBase = state.successCount,
                knownElementsBase = state.knownElements.size,
                materialsBase = state.discoveredMaterials.size
            )
        )
    }

    /** 今日の途中経過も含めた並び。グラフの右端を今日にする */
    fun series(state: PlayerState): List<com.appathy.scienceroom.data.DailyStat> {
        val today = com.appathy.scienceroom.data.DailyStat(
            date = state.daily.date,
            quizAnswered = (state.quizCount - state.daily.quizCountBase).coerceAtLeast(0),
            quizCorrect = (state.quizCorrect - state.daily.quizCorrectBase).coerceAtLeast(0),
            experiments = (state.experimentCount - state.daily.experimentBase).coerceAtLeast(0),
            successes = (state.successCount - state.daily.successBase).coerceAtLeast(0),
            explores = (state.exploreCount - state.daily.exploreBase).coerceAtLeast(0),
            newElements = (state.knownElements.size - state.daily.knownElementsBase)
                .coerceAtLeast(0),
            newMaterials = (state.discoveredMaterials.size - state.daily.materialsBase)
                .coerceAtLeast(0)
        )
        return state.history + today
    }

    /** 直近7日のふりかえり文 */
    fun weeklyComment(state: PlayerState): String {
        val week = series(state).takeLast(7)
        val active = week.count { !it.isEmpty() }
        val answered = week.sumOf { it.quizAnswered }
        val correct = week.sumOf { it.quizCorrect }
        val experiments = week.sumOf { it.experiments }
        val acc = if (answered == 0) 0 else correct * 100 / answered

        if (active == 0) return "この1週間は記録がありません。1日5問でも続けると定着します"
        val head = "この1週間は " + active + " 日活動し、クイズ " + answered +
            "問・実験 " + experiments + "回でした。"
        val tail = when {
            answered == 0 -> "実験は進んでいます。元素も少しずつ覚えると技術が早く開きます"
            acc >= 80 -> "正答率 " + acc + "% は十分です。新しい出題形式に広げてみましょう"
            acc >= 50 -> "正答率 " + acc + "% です。間違えたものだけ復習すると伸びます"
            else -> "正答率 " + acc + "% です。一度に多くより、少ない数を繰り返すほうが早く覚えられます"
        }
        return head + tail
    }
}

/** クイズ1回分の成績。終わったときにまとめて振り返る */
data class QuizSession(
    val answered: Int = 0,
    val correct: Int = 0,
    val bestStreak: Int = 0,
    val missed: List<String> = emptyList()
) {
    fun record(elementId: String, isCorrect: Boolean, streak: Int): QuizSession = copy(
        answered = answered + 1,
        correct = correct + if (isCorrect) 1 else 0,
        bestStreak = maxOf(bestStreak, streak),
        missed = if (isCorrect) missed else (missed + elementId).distinct()
    )

    fun accuracy(): Int = if (answered == 0) 0 else correct * 100 / answered

    fun comment(): String = when {
        answered == 0 -> ""
        accuracy() >= 90 -> "ほとんど迷わず答えられている。次は別の出題形式にも広げてみよう"
        accuracy() >= 70 -> "だいぶ身についてきた。間違えたものだけ復習すると効率がよい"
        accuracy() >= 40 -> "半分ほど。図鑑で性質と使われ方を読んでから戻ると覚えやすい"
        else -> "まだ出会って間もない元素が多い。少ない数を繰り返すほうが早く覚えられる"
    }
}

object ReviewEngine {
    /** 復習の期限が来ている元素の数 */
    fun dueCount(content: Content, state: PlayerState, now: Long): Int =
        content.elements.count { e ->
            val l = state.learning[e.id]
            l != null && l.nextReview in 1..now
        }
}
