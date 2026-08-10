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

data class ExploreOutcome(
    val locationId: String,
    val foundMaterialId: String?,
    val amount: Int,
    val isNew: Boolean,
    val message: String
)

object ExplorationEngine {

    fun explore(content: Content, state: PlayerState, location: GameLocation): ExploreOutcome {
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
        val missChance = (location.difficulty - 1) * 8 + (picked.rarity - 1) * 6
        if (rnd.nextInt(100) < missChance) {
            return ExploreOutcome(
                location.id, null, 0, false,
                "めぼしいものは見つからなかった。もう一度探してみよう"
            )
        }

        val amount = 1 + rnd.nextInt(3 - (picked.rarity / 3).coerceAtMost(2))
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

object ExperimentEngine {

    fun run(content: Content, state: PlayerState, input: ExperimentInput): ExperimentResult {
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
        if (!reachable) causes.add(FailureCause("器具の到達温度", "高"))

        if (causes.isEmpty()) {
            val center = (exact.minTemp + exact.maxTemp) / 2
            val span = (exact.maxTemp - exact.minTemp).coerceAtLeast(1)
            val precise = abs(input.temperature - center) <= span / 4 &&
                input.duration >= exact.minDuration + 1
            val rank = if (precise) Rank.S else Rank.A
            val gained = exact.exp + if (rank == Rank.S) 10 else 0
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
            if (rank == Rank.B) 8 else 4, emptyList(), null
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
                else -> "使っている器具では条件を満たせていない可能性がある"
            }
            4 -> when (top) {
                "温度条件" -> "温度を変えて再実験してみよう"
                "加熱時間" -> "時間を延ばして再実験してみよう"
                else -> "別の器具を使って再実験してみよう"
            }
            else -> "「${r.name}」は${r.minTemp}〜${r.maxTemp}℃、${r.minDuration}以上の時間、" +
                "器具は${Equipment.label(r.equipment)}が必要だ"
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
                val left = (inv[id] ?: 0) - 1
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

    fun nextQuestion(content: Content, state: PlayerState, mode: QuizMode?, now: Long): Question {
        val chosenMode = mode ?: QuizMode.entries.random()
        val pool = content.elements
        val target = pool.maxByOrNull { e ->
            val l = state.learning[e.id]
            val risk = l?.forgettingRisk(now) ?: 1.0
            risk + Random.nextDouble() * 0.25
        } ?: pool.first()

        val distractors = pool.filter { it.id != target.id }.shuffled().take(3)
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

    fun answer(state: PlayerState, q: Question, correct: Boolean, now: Long): PlayerState {
        val prev = state.learning[q.element.id] ?: com.appathy.scienceroom.data.ElementLearning()
        val mode = q.mode.id
        val correctMap = prev.correct.toMutableMap()
        val totalMap = prev.total.toMutableMap()
        totalMap[mode] = (totalMap[mode] ?: 0) + 1
        if (correct) correctMap[mode] = (correctMap[mode] ?: 0) + 1

        val streak = if (correct) (prev.streak + 1).coerceAtMost(intervals.size - 1) else 0
        val updated = prev.copy(
            correct = correctMap,
            total = totalMap,
            lastReviewed = now,
            nextReview = now + intervals[streak],
            streak = streak
        )
        val learning = state.learning.toMutableMap()
        learning[q.element.id] = updated

        return state.copy(
            learning = learning,
            quizCount = state.quizCount + 1,
            quizCorrect = state.quizCorrect + if (correct) 1 else 0,
            knownElements = if (correct) state.knownElements + q.element.id else state.knownElements,
            exp = state.exp + if (correct) 5 else 1
        )
    }

    fun weakElements(content: Content, state: PlayerState, now: Long, limit: Int = 3): List<Element> =
        content.elements
            .sortedByDescending { state.learning[it.id]?.forgettingRisk(now) ?: 1.0 }
            .take(limit)
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

data class Mission(val text: String, val done: Boolean)

object MissionEngine {
    fun today(state: PlayerState): List<Mission> = listOf(
        Mission("元素クイズに5問正解する", state.quizCorrect % 5 == 0 && state.quizCorrect > 0),
        Mission("素材を1個発見する", state.discoveredMaterials.isNotEmpty()),
        Mission("実験を1回行う", state.experimentCount > 0)
    )
}
