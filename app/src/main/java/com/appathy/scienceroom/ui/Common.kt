package com.appathy.scienceroom.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF2A2118)
private val Paper = Color(0xFFF6EFE3)
private val Ember = Color(0xFFC2611F)
private val Moss = Color(0xFF4E6B4A)
private val Sky = Color(0xFF2E5A72)

private val LightColors = lightColorScheme(
    primary = Ember,
    onPrimary = Color.White,
    secondary = Moss,
    onSecondary = Color.White,
    tertiary = Sky,
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFFFBF4),
    onSurface = Ink,
    surfaceVariant = Color(0xFFEADFCC),
    onSurfaceVariant = Color(0xFF574A38)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE08A46),
    secondary = Color(0xFF8FB08A),
    tertiary = Color(0xFF7FAFC9),
    background = Color(0xFF17202A),
    surface = Color(0xFF1F2A36)
)

@Composable
fun ScienceRoomTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}

@Composable
fun drawableId(name: String): Int {
    val ctx = LocalContext.current
    return remember(name) {
        ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
    }
}

@Composable
fun AssetImage(
    name: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val id = drawableId(name)
    if (id != 0) {
        Image(
            painter = painterResource(id),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(modifier.background(Color(0xFFE0DACE)))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp)
    )
}

@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}

@Composable
fun LabeledRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Thumb(imageId: String, size: Int = 44) {
    AssetImage(
        name = imageId,
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.Fit
    )
}

fun dangerStars(level: Int): String {
    val n = level.coerceIn(1, 5)
    return "★".repeat(n) + "☆".repeat(5 - n)
}
