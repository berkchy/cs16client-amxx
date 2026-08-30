package com.pickle.patcher.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pickle.patcher.ui.theme.Mint20
import com.pickle.patcher.ui.theme.Mint80
import com.pickle.patcher.ui.theme.OnDarkMuted
import com.pickle.patcher.ui.theme.Violet30
import kotlin.math.roundToInt

/** Hero glow behind a big icon — a slowly breathing radial gradient. */
@Composable
fun GlowHalo(size: Dp) {
    val transition = rememberInfiniteTransition(label = "glow")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )
    val scale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowScale",
    )
    Box(
        modifier = Modifier.size(size * scale),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Mint80.copy(alpha = 0.5f * alpha), Color.Transparent),
                ),
            )
        }
    }
}

@Composable
fun HeroCard(title: String, subtitle: String, icon: @Composable () -> Unit, onClick: () -> Unit = {}) {
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        HeroCardBody(title, subtitle, icon)
    }
}

@Composable
private fun HeroCardBody(title: String, subtitle: String, icon: @Composable () -> Unit) {
    Column(Modifier.padding(22.dp)) {
        Box(Modifier.align(Alignment.CenterHorizontally)) {
            Box(
                Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Mint20, Violet30))),
                contentAlignment = Alignment.Center,
            ) { icon() }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = OnDarkMuted,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

/** Animated step marker: done → filled check, active → pulsing ring, pending → dim dot. */
@Composable
fun StepMarker(state: StepState, modifier: Modifier = Modifier) {
    when (state) {
        StepState.DONE -> {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "done",
                tint = Mint80,
                modifier = modifier.size(30.dp),
            )
        }
        StepState.ACTIVE -> {
            val transition = rememberInfiniteTransition(label = "pulse")
            val scale by transition.animateFloat(
                initialValue = 0.7f, targetValue = 1.35f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "pulseScale",
            )
            Box(modifier.size(30.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(30.dp * scale)
                        .clip(CircleShape)
                        .background(Mint80.copy(alpha = 0.35f)),
                )
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Mint80),
                )
            }
        }
        StepState.PENDING -> {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(OnDarkMuted.copy(alpha = 0.35f))
                    .then(modifier),
            )
        }
    }
}

enum class StepState { PENDING, ACTIVE, DONE }

@Composable
fun StepListItem(
    title: String,
    detail: String,
    state: StepState,
    animate: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(
        targetValue = when (state) {
            StepState.DONE -> Mint20.copy(alpha = 0.55f)
            StepState.ACTIVE -> MaterialTheme.colorScheme.surfaceContainerHigh
            StepState.PENDING -> MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(if (animate) 320 else 0),
        label = "stepBg",
    )
    val animatedState by animateFloatAsState(
        targetValue = when (state) { StepState.ACTIVE -> 1f; else -> 0f },
        animationSpec = tween(if (animate) 260 else 0),
        label = "stepActive",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = container,
    ) {
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 13.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepMarker(state, Modifier.padding(end = 14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = when (state) {
                        StepState.PENDING -> OnDarkMuted.copy(alpha = 0.7f)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
                AnimatedVisibility(
                    visible = state != StepState.PENDING || animatedState > 0f,
                    enter = androidx.compose.animation.expandVertically(),
                    exit = androidx.compose.animation.shrinkVertically(),
                ) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state == StepState.ACTIVE) Mint80 else OnDarkMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun StatChip(label: String, value: String, modifier: Modifier = Modifier, accent: Color = Mint80) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(min = 92.dp),
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = OnDarkMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = OnDarkMuted,
        modifier = modifier,
    )
}

/** Big interactive loader for downloads/patches with an emergent percentage. */
@Composable
fun AnimatedProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "progress",
    )
    LinearProgressIndicator(
        progress = { animated },
        modifier = modifier.height(8.dp).clip(CircleShape),
        color = Mint80,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    Text(
        "${(animated * 100).roundToInt()}%",
        style = MaterialTheme.typography.labelMedium,
        color = Mint80,
    )
}