package com.meshgen.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meshgen.app.device.DeviceTier
import com.meshgen.app.ui.StatusDot
import com.meshgen.app.ui.Tag
import com.meshgen.app.ui.engine.Engine
import com.meshgen.app.ui.theme.MeshColors
import java.util.Locale

@Composable
fun HomeScreen(
    onOpenEngine: (Engine) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenDevice: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val device = state.device

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(MeshColors.Graphite900, MeshColors.Graphite950, MeshColors.Graphite950)),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        Text("ON-DEVICE 3D", style = MaterialTheme.typography.labelMedium, color = MeshColors.Accent)
        Spacer(Modifier.height(6.dp))
        Text("MeshGen", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "Generate printable meshes. No cloud — everything runs on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        Surface(
            onClick = onOpenDevice,
            shape = RoundedCornerShape(50),
            color = MeshColors.Graphite850,
            border = BorderStroke(1.dp, MeshColors.Graphite800),
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusDot(if (device.tier == DeviceTier.ENTRY) MeshColors.Warning else MeshColors.Accent)
                Text(
                    "${device.tier.label} · ${String.format(Locale.US, "%.1f", device.totalRamGb)} GB · ${device.socLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MeshColors.Graphite300,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Engine.entries.forEachIndexed { i, engine ->
            AnimatedEntry(delayMs = 80 * i) {
                EngineCard(engine = engine, onClick = { onOpenEngine(engine) })
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(8.dp))
        AnimatedEntry(delayMs = 280) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickTile("Library", "Past generations", Icons.Outlined.ViewInAr, onOpenLibrary, Modifier.weight(1f))
                QuickTile("Models", "Downloads & storage", Icons.Outlined.CloudDownload, onOpenModels, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** Fade + rise entrance, staggered by [delayMs]. */
@Composable
private fun AnimatedEntry(delayMs: Int, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 520, delayMillis = delayMs, easing = FastOutSlowInEasing))
    }
    Box(
        Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 40f
        },
    ) { content() }
}

@Composable
private fun EngineCard(engine: Engine, onClick: () -> Unit) {
    val core = !engine.experimental
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MeshColors.Graphite900,
        border = BorderStroke(1.dp, if (core) MeshColors.AccentDim else MeshColors.Graphite800),
    ) {
        Column(
            Modifier
                .background(
                    if (core) {
                        Brush.linearGradient(listOf(MeshColors.AccentDim.copy(alpha = 0.12f), MeshColors.Graphite900))
                    } else {
                        Brush.linearGradient(listOf(MeshColors.Graphite900, MeshColors.Graphite900))
                    },
                )
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (core) MeshColors.AccentDim else MeshColors.Graphite800),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(engine.icon, contentDescription = null, tint = if (core) MeshColors.Accent else MeshColors.Graphite100)
                }
                Spacer(Modifier.width(14.dp))
                Text(engine.index, style = MaterialTheme.typography.labelMedium, color = MeshColors.Graphite500)
                Spacer(Modifier.weight(1f))
                Tag(engine.badge, accent = core)
            }
            Spacer(Modifier.height(18.dp))
            Text(engine.title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    engine.tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = if (core) MeshColors.Accent else MeshColors.Graphite300,
                )
            }
        }
    }
}

@Composable
private fun QuickTile(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MeshColors.Graphite900,
        border = BorderStroke(1.dp, MeshColors.Graphite800),
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = MeshColors.Graphite100)
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

