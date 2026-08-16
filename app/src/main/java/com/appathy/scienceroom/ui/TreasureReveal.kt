package com.appathy.scienceroom.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appathy.scienceroom.data.GameMaterial
import kotlinx.coroutines.delay

/**
 * 希少な素材が出たときの宝箱演出。
 * 箱が揺れる → ひらく → 中身がせり出す、の3段。動画は使わず画像の変形だけで見せる。
 */
@Composable
fun TreasureReveal(
    material: GameMaterial,
    amount: Int,
    onDone: () -> Unit
) {
    var phase by remember { mutableStateOf(0) }
    val shake = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }

    LaunchedEffect(material) {
        phase = 0
        shake.snapTo(0f)
        glow.snapTo(0f)
        shake.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 1100
                0f at 0
                -9f at 90
                9f at 180
                -7f at 280
                7f at 380
                -5f at 500
                5f at 620
                -3f at 760
                0f at 1100
            }
        )
        phase = 1
        glow.animateTo(1f, animationSpec = tween(420, easing = FastOutSlowInEasing))
        delay(180)
        phase = 2
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.8f)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            AssetImage(
                name = "ui_treasure",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = shake.value },
                contentScale = ContentScale.Crop
            )

            if (phase >= 1) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(glow.value * 0.55f)
                        .background(Color(0xFFFFE9A8))
                )
            }

            if (phase >= 2) {
                Burst(key = material) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xF2FFFBF4))
                            .padding(10.dp)
                    ) {
                        Thumb(material.imageId, 96)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        when (phase) {
            0 -> Text(
                "何かが入っているようだ…",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            1 -> Text("ひらいた！", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    material.name + " ×" + amount,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "希少度 " + dangerStars(material.rarity),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    material.note,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = onDone,
                modifier = Modifier.scale(if (phase >= 2) 1f else 0.96f),
                enabled = phase >= 2
            ) {
                Text(if (phase >= 2) "受け取る" else "…")
            }
        }
    }
}
