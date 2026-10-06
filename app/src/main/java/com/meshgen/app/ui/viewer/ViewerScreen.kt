package com.meshgen.app.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** Share of the screen (below the status bar) given to the 3D viewport. */
private const val VIEWPORT_FRACTION = 0.5f

class ViewerActions(
    val onBack: () -> Unit,
    val onWireframe: (Boolean) -> Unit,
    val onLighting: (LightingPreset) -> Unit,
    val onResetView: () -> Unit,
    val onFormat: (ExportFormat) -> Unit,
    val onFraction: (Float) -> Unit,
    val onDecimate: () -> Unit,
    val onRestore: () -> Unit,
    val onShare: () -> Unit,
    val onSave: () -> Unit,
)

@Composable
fun ViewerScreen(onBack: () -> Unit, vm: ViewerViewModel = viewModel()) {
    val wb = vm.workbench
    val state by wb.state.collectAsStateWithLifecycle()
    val snackbar = rememberViewerEvents(wb)
    val actions = remember(wb) { wb.actions(onBack) }
    ViewerContent(state, actions, snackbar) { MeshViewport(state, onError = wb::onRenderError) }
}

/** Collects share/message events from [wb] and returns the snackbar host state that shows messages. */
@Composable
fun rememberViewerEvents(wb: MeshWorkbench): SnackbarHostState {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(wb) {
        wb.events.collect { e ->
            when (e) {
                is ViewerEvent.Share -> context.startActivity(e.intent)
                is ViewerEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }
    return snackbar
}

/** Stateless layout, so it can be previewed and screenshot-tested without OpenGL. */
@Composable
fun ViewerContent(
    state: MeshPanelState,
    actions: ViewerActions,
    snackbar: SnackbarHostState,
    viewport: @Composable () -> Unit,
) {
    ViewerFrame(state, actions, snackbar, viewport) {
        state.report?.let { report ->
            StatsStrip(report)
            HealthRow(report, state.fixes)
            SimplifyCard(state, actions)
        }
    }
}

/**
 * Shared screen frame: 3D viewport on top (half the screen), a scrolling panel with [panel] below it,
 * and the export bar pinned at the bottom.
 */
@Composable
fun ViewerFrame(
    state: MeshPanelState,
    actions: ViewerActions,
    snackbar: SnackbarHostState,
    viewport: @Composable () -> Unit,
    viewportOverlay: @Composable BoxScope.() -> Unit = {},
    panel: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MeshColors.Graphite950)) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val viewportHeight = maxHeight * VIEWPORT_FRACTION
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(viewportHeight)) {
                    viewport()
                    TopOverlay(state.title, actions.onBack, Modifier.align(Alignment.TopStart))
                    ViewportHint(Modifier.align(Alignment.TopCenter).padding(top = 52.dp))
                    ViewportControls(state, actions, Modifier.align(Alignment.BottomCenter).padding(10.dp))
                    if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.Center), color = MeshColors.Accent)
                    state.error?.let {
                        Surface(Modifier.align(Alignment.Center).padding(24.dp), shape = RoundedCornerShape(16.dp), color = MeshColors.Graphite850) {
                            Text(it, Modifier.padding(16.dp), color = MeshColors.Warning, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    viewportOverlay()
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = panel,
                )

                ExportBar(state, actions)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp))
    }
}

@Composable
fun MeshViewport(state: MeshPanelState, onError: (String) -> Unit) {
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
            v.resetViewIfRequested(state.resetViewCount)
        },
    )
}

private val overlay = Color(0xE6121418)

@Composable
private fun TopOverlay(title: String, onBack: () -> Unit, modifier: Modifier) {
    Row(modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(onClick = onBack, shape = CircleShape, color = overlay, contentColor = MeshColors.Graphite100) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", modifier = Modifier.padding(8.dp).size(20.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MeshColors.Graphite100,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.background(overlay, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ViewportHint(modifier: Modifier) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(4000); visible = false }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Text(
            "Drag to rotate · pinch to zoom",
            style = MaterialTheme.typography.labelSmall,
            color = MeshColors.Graphite300,
            modifier = Modifier.background(overlay, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun ViewportControls(state: MeshPanelState, actions: ViewerActions, modifier: Modifier) {
    Row(
        modifier.background(overlay, RoundedCornerShape(50)).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillButton(Icons.Outlined.GridOn, "Wire", selected = state.wireframe) { actions.onWireframe(!state.wireframe) }
        // One button cycles the lighting presets to keep the overlay small.
        PillButton(Icons.Outlined.LightMode, state.lighting.label, selected = false) {
            val all = LightingPreset.entries
            actions.onLighting(all[(state.lighting.ordinal + 1) % all.size])
        }
        PillButton(Icons.Outlined.CenterFocusStrong, "Reset", selected = false, onClick = actions.onResetView)
    }
}

@Composable
private fun PillButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MeshColors.AccentDim else Color.Transparent,
        contentColor = if (selected) MeshColors.Accent else MeshColors.Graphite100,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

private val numbers: NumberFormat = NumberFormat.getIntegerInstance(Locale.US)

/** "20" for whole numbers, otherwise one decimal. */
private fun mm(v: Float): String {
    val r = Math.round(v * 10f) / 10f
    return if (r == Math.round(r).toFloat()) Math.round(r).toString() else String.format(Locale.US, "%.1f", r)
}

@Composable
internal fun StatsStrip(r: MeshReport) {
    Surface(shape = RoundedCornerShape(14.dp), color = MeshColors.Graphite900, border = BorderStroke(1.dp, MeshColors.Graphite800)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("SIZE MM", "${mm(r.bounds.sizeX)}×${mm(r.bounds.sizeY)}×${mm(r.bounds.sizeZ)}", Modifier.weight(1.5f))
            Stat("TRIS", numbers.format(r.triangleCount), Modifier.weight(1f))
            Stat("CM³", String.format(Locale.US, "%.1f", r.volumeMm3 / 1000.0), Modifier.weight(0.8f))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MeshColors.Graphite500, maxLines = 1)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MeshColors.Graphite100,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun HealthRow(r: MeshReport, fixes: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    val problems = r.problems
    val ok = problems.isEmpty()
    val details = problems.map { "• $it" } + r.notes + fixes.map { "Auto-fixed: $it" } +
        if (!ok) listOf("Orange surfaces in the viewer are inside faces seen through a gap.") else emptyList()
    Surface(
        onClick = { expanded = !expanded },
        enabled = details.isNotEmpty(),
        shape = RoundedCornerShape(14.dp),
        color = MeshColors.Graphite900,
        border = BorderStroke(1.dp, if (ok) MeshColors.Graphite800 else MeshColors.Warning.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.fillMaxWidth().animateContentSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = if (ok) MeshColors.Accent else MeshColors.Warning,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        ok && r.watertight -> "Watertight · ready to print"
                        ok -> "Ready to print"
                        problems.size == 1 -> "Needs attention · 1 problem"
                        else -> "Needs attention · ${problems.size} problems"
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MeshColors.Graphite100,
                    modifier = Modifier.weight(1f),
                )
                if (details.isNotEmpty()) {
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = "Details", tint = MeshColors.Graphite300)
                }
            }
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                details.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MeshColors.Graphite300) }
            }
        }
    }
}

@Composable
internal fun SimplifyCard(state: MeshPanelState, actions: ViewerActions) {
    Surface(shape = RoundedCornerShape(14.dp), color = MeshColors.Graphite900, border = BorderStroke(1.dp, MeshColors.Graphite800)) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val target = (state.originalTriangles * state.decimateFraction).toInt()
                Text("Simplify", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MeshColors.Graphite100)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${(state.decimateFraction * 100).toInt()}% · ${numbers.format(target)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MeshColors.Graphite300,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (state.decimated) {
                    TextButton(onClick = actions.onRestore, enabled = state.busyLabel == null) { Text("Undo", fontSize = 13.sp) }
                }
                TextButton(onClick = actions.onDecimate, enabled = state.busyLabel == null) { Text("Apply", fontSize = 13.sp) }
            }
            Slider(
                value = state.decimateFraction,
                onValueChange = actions.onFraction,
                valueRange = 0.05f..0.95f,
                enabled = state.busyLabel == null,
                modifier = Modifier.padding(end = 8.dp).height(28.dp),
                colors = SliderDefaults.colors(thumbColor = MeshColors.Accent, activeTrackColor = MeshColors.Accent, inactiveTrackColor = MeshColors.Graphite700),
            )
            if (state.busyLabel != null && state.busyProgress != null) {
                LinearProgressIndicator(
                    progress = { state.busyProgress },
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 6.dp),
                    color = MeshColors.Accent,
                    trackColor = MeshColors.Graphite700,
                )
            }
        }
    }
}

@Composable
internal fun ExportBar(state: MeshPanelState, actions: ViewerActions) {
    val enabled = state.report != null && state.busyLabel == null
    Surface(color = MeshColors.Graphite900, border = BorderStroke(1.dp, MeshColors.Graphite800)) {
        Column {
            if (state.busyLabel != null && state.busyProgress == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = MeshColors.Accent, trackColor = MeshColors.Graphite800)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var menu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { menu = true }, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                        Text(state.format.label, color = MeshColors.Graphite100)
                        Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Choose format", tint = MeshColors.Graphite300)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        ExportFormat.entries.forEach { f ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(f.label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                        Text(f.description, style = MaterialTheme.typography.bodySmall, color = MeshColors.Graphite300)
                                    }
                                },
                                onClick = { actions.onFormat(f); menu = false },
                            )
                        }
                    }
                }
                OutlinedButton(onClick = actions.onSave, enabled = enabled, modifier = Modifier.weight(1f), contentPadding = ButtonDefaults.TextButtonContentPadding) {
                    Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Save", maxLines = 1)
                }
                Button(
                    onClick = actions.onShare,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                    colors = ButtonDefaults.buttonColors(containerColor = MeshColors.Accent, contentColor = MeshColors.Graphite950),
                ) {
                    Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Share", maxLines = 1)
                }
            }
        }
    }
}
