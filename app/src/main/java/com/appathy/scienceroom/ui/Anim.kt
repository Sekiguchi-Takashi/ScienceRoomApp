package com.appathy.scienceroom.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appathy.scienceroom.engine.Cast
import com.appathy.scienceroom.engine.Character
import com.appathy.scienceroom.engine.Scene

/** 火のゆらぎ。大きさと傾きを少しずつずらして揺れて見せる */
@Composable
fun Flicker(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "flicker")
    val scale by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(620, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flickerScale"
    )
    val tilt by transition.animateFloat(
        initialValue = -1.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(870, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flickerTilt"
    )
    Box(modifier = modifier.scale(scale).rotate(tilt)) { content() }
}

/** 発見や完成のときに、ぽんと現れて落ち着く */
@Composable
fun Pop(key: Any?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scale = remember { Animatable(0.4f) }
    LaunchedEffect(key) {
        scale.snapTo(0.4f)
        scale.animateTo(
            targetValue = 1f,
            animationSpec = keyframes {
                durationMillis = 520
                0.4f at 0
                1.18f at 260
                0.94f at 400
                1f at 520
            }
        )
    }
    Box(modifier = modifier.scale(scale.value)) { content() }
}

/** 失敗や衝撃のときに左右へ小刻みに振れる */
@Composable
fun Shake(key: Any?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(key) {
        offset.snapTo(0f)
        offset.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 460
                0f at 0
                -14f at 60
                12f at 130
                -8f at 200
                6f at 270
                -3f at 340
                0f at 460
            }
        )
    }
    Box(modifier = modifier.graphicsLayer { translationX = offset.value }) { content() }
}

/** 反応が起きた瞬間の膨らみ。大きく開いてから戻る */
@Composable
fun Burst(key: Any?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scale = remember { Animatable(1f) }
    val spin = remember { Animatable(0f) }
    LaunchedEffect(key) {
        scale.snapTo(0.7f)
        spin.snapTo(-8f)
        scale.animateTo(
            targetValue = 1f,
            animationSpec = keyframes {
                durationMillis = 700
                0.7f at 0
                1.35f at 200
                0.88f at 380
                1.06f at 540
                1f at 700
            }
        )
    }
    LaunchedEffect(key) {
        spin.animateTo(0f, animationSpec = tween(700, easing = FastOutSlowInEasing))
    }
    Box(modifier = modifier.scale(scale.value).rotate(spin.value)) { content() }
}

/** ゆっくり上下に浮く。待機中の人物に使う */
@Composable
fun Hover(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "float")
    val dy by transition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatDy"
    )
    Box(modifier = modifier.graphicsLayer { translationY = dy }) { content() }
}

/** 人物とセリフ。場面に応じて話す人が変わる */
@Composable
fun SpeechRow(
    character: Character,
    text: String,
    size: Int = 64,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Hover { Thumb(character.imageId, size) }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                character.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(text, fontSize = 13.sp)
        }
    }
}

@Composable
fun SceneSpeech(scene: Scene, seed: Int, size: Int = 64, modifier: Modifier = Modifier) {
    val c = Cast.speaker(scene)
    SpeechRow(c, Cast.line(scene, seed), size, modifier)
}

/** 人物の並び。図鑑などで一覧に使う */
@Composable
fun CastRow(
    members: List<Character> = Cast.all,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        members.forEach { c ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Thumb(c.imageId, 44)
                Text(c.name, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
fun Spacer8() {
    Spacer(Modifier.height(8.dp))
}
