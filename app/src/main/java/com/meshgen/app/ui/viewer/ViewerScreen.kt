package com.meshgen.app.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meshgen.app.ui.theme.MeshColors
import com.meshgen.app.viewer.LightingPreset
import com.meshgen.app.viewer.MeshView
import com.meshgen.core.export.ExportFormat
import com.meshgen.core.mesh.MeshReport
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ViewerScreen(onBack: () -> Unit, vm: ViewerViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is ViewerEvent.Share -> context.startActivity(e.intent)
                is ViewerEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MeshColors.Graphite950)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                Text(state.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Box(Modifier.fillMaxWidth().weight(1f)) {
                Viewport(state, onError = vm::onRenderError)
                ViewportHint()
                ViewportControls(
                    state = state,
                    onWireframe = vm::setWireframe,
                    onLighting = vm::setLighting,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                )
                if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.Center), color = MeshColors.Accent)
                state.error?.let {
                    Surface(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MeshColors.Graphite850,
                    ) { Text(it, Modifier.padding(16.dp), color = MeshColors.Warning) }
                }
            }

            val maxPanel = (LocalConfiguration.current.screenHeightDp * 0.42f).dp
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxPanel)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.report?.let { report ->
                    StatsRow(report)
                    HealthCard(report, state.fixes)
                    SimplifyCard(state, vm::setDecimateFraction, vm::decimate, vm::restoreOriginal)
                }
            }

            ExportBar(state, onFormat = vm::setFormat, onShare = vm::share, onSave = vm::saveToDownloads)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
}

@Composable
private fun Viewport(state: ViewerUiState, onError: (String) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var view by remember { mutableStateOf<MeshView?>(null) }
    DisposableEffect(lifecycle, view) {
        val v = view
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> v?.onResume()
                Lifecycle.Event.ON_PAUSE -> v?.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx -> MeshView(ctx, onError).also { view = it } },
        update = { v ->
            v.setMesh(state.viewerMesh, reframe = state.reframe)
            v.setWireframe(state.wireframe)
            v.setLighting(state.lighting)
        },
    )
}

@Composable
private fun ViewportHint() {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(4000); visible = false }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "Drag to rotate · pinch to zoom · two fingers to pan · double-tap to reset",
                style = MaterialTheme.typography.labelSmall,
                color = MeshColors.Graphite300,
                modifier = Modifier.background(MeshColors.Graphite900.copy(alpha = 0.8f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ViewportControls(
    state: ViewerUiState,
    onWireframe: (Boolean) -> Unit,
    onLighting: (LightingPreset) -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier
            .background(MeshColors.Graphite900.copy(alpha = 0.9f), RoundedCornerShape(50))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Chip("Wireframe", selected = state.wireframe, icon = { Icon(Icons.Outlined.GridOn, null, Modifier.size(16.dp)) }) {
            onWireframe(!state.wireframe)
        }
        Box(Modifier.width(1.dp).height(20.dp).background(MeshColors.Graphite700))
        LightingPreset.entries.forEach { p -> Chip(p.label, selected = state.lighting == p) { onLighting(p) } }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, icon: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MeshColors.AccentDim else Color.Transparent,
        contentColor = if (selected) MeshColors.Accent else MeshColors.Graphite300,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { it(); Spacer(Modifier.width(4.dp)) }
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private val numbers: NumberFormat = NumberFormat.getIntegerInstance(Locale.US)
private fun mm(v: Float) = String.format(Locale.US, "%.1f", v)

@Composable
private fun StatsRow(r: MeshReport) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stat("SIZE (MM)", "${mm(r.bounds.sizeX)} × ${mm(r.bounds.sizeY)} × ${mm(r.bounds.sizeZ)}", Modifier.weight(1.6f))
        Stat("TRIANGLES", numbers.format(r.triangleCount), Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stat("VOLUME", String.format(Locale.US, "%.2f cm³", r.volumeMm3 / 1000.0), Modifier.weight(1f))
        Stat(
            "WATERTIGHT",
            if (r.watertight) "Yes" else "No",
            Modifier.weight(1f),
            valueColor = if (r.watertight) MeshColors.Accent else MeshColors.Warning,
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, valueColor: Color = MeshColors.Graphite100) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = MeshColors.Graphite900, border = BorderStroke(1.dp, MeshColors.Graphite800)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MeshColors.Graphite500)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor, maxLines = 1)
        }
    }
}

@Composable
private fun HealthCard(r: MeshReport, fixes: List<String>) {
    Surface(shape = RoundedCornerShape(16.dp), color = MeshColors.Graphite900, border = BorderStroke(1.dp, MeshColors.Graphite800)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val problems = r.problems
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (problems.isEmpty()) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = if (problems.isEmpty()) MeshColors.Accent else MeshColors.Warning,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(if (problems.isEmpty()) "Ready to print" else "Needs attention", style = MaterialTheme.typography.titleMedium)
            }
            problems.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            (r.notes + fixes.map { "Auto-fixed: $it" }).forEach {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MeshColors.Graphite300)
            }
            if (problems.isNotEmpty()) {
                Text(
                    "Orange surfaces in the viewer are inside faces seen through a gap.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeshColors.Graphite500,
                )
            }
        }
    }
}

@Composable
private fun SimplifyCard(state: ViewerUiState, onFraction: (Float) -> Unit, onApply: () -> Unit, onRestore: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MeshColors.Graphite900, border = BorderStroke(1.dp, MeshColors.Graphite800)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Simplify", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                val target = (state.originalTriangles * state.decimateFraction).toInt()
                Text(
                    "${(state.decimateFraction * 100).toInt()}% · ~${numbers.format(target)} tris",
                    style = MaterialTheme.typography.labelMedium,
                    color = MeshColors.Graphite300,
                )
            }
            Slider(
                value = state.decimateFraction,
                onValueChange = onFraction,
                valueRange = 0.05f..0.95f,
                enabled = state.busyLabel == null,
                colors = SliderDefaults.colors(thumbColor = MeshColors.Accent, activeTrackColor = MeshColors.Accent, inactiveTrackColor = MeshColors.Graphite700),
            )
            if (state.busyLabel != null && state.busyProgress != null) {
                LinearProgressIndicator(
                    progress = { state.busyProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MeshColors.Accent,
                    trackColor = MeshColors.Graphite700,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onApply, enabled = state.busyLabel == null) { Text("Apply") }
                if (state.decimated) TextButton(onClick = onRestore, enabled = state.busyLabel == null) { Text("Restore original") }
            }
        }
    }
}

@Composable
private fun ExportBar(state: ViewerUiState, onFormat: (ExportFormat) -> Unit, onShare: () -> Unit, onSave: () -> Unit) {
    Surface(color = MeshColors.Graphite900, border = BorderStroke(1.dp, MeshColors.Graphite800), shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("EXPORT", style = MaterialTheme.typography.labelSmall, color = MeshColors.Graphite500)
                Spacer(Modifier.width(4.dp))
                ExportFormat.entries.forEach { f -> Chip(f.label, selected = state.format == f) { onFormat(f) } }
            }
            val enabled = state.report != null && state.busyLabel == null
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onSave, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Save")
                }
                Button(
                    onClick = onShare,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MeshColors.Accent, contentColor = MeshColors.Graphite950),
                ) {
                    Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Share")
                }
            }
            if (state.busyLabel != null && state.busyProgress == null) {
                Text(state.busyLabel, style = MaterialTheme.typography.labelSmall, color = MeshColors.Graphite300)
            }
        }
    }
}

