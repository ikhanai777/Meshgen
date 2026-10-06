package com.meshgen.app.ui.shape

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meshgen.app.ui.theme.MeshColors
import androidx.compose.material3.SnackbarHostState
import com.meshgen.app.ui.viewer.HealthRow
import com.meshgen.app.ui.viewer.MeshPanelState
import com.meshgen.app.ui.viewer.ViewerActions
import com.meshgen.app.ui.viewer.MeshViewport
import com.meshgen.app.ui.viewer.SimplifyCard
import com.meshgen.app.ui.viewer.StatsStrip
import com.meshgen.app.ui.viewer.ViewerFrame
import com.meshgen.app.ui.viewer.rememberViewerEvents
import com.meshgen.core.dsl.ParamDef
import com.meshgen.core.dsl.Quality
import com.meshgen.core.dsl.ShapeDoc
import java.util.Locale

@Composable
fun ShapeEditorScreen(onBack: () -> Unit, vm: ShapeEditorViewModel = viewModel()) {
    val wb = vm.workbench
    val panel by wb.state.collectAsStateWithLifecycle()
    val ed by vm.state.collectAsStateWithLifecycle()
    val snackbar = rememberViewerEvents(wb)
    val actions = remember(wb) { wb.actions(onBack) }
    ShapeEditorContent(
        panel, ed, actions, snackbar,
        viewport = { MeshViewport(panel, onError = wb::onRenderError) },
        onParam = vm::setParam, onReset = vm::resetParams, onQuality = vm::setQuality, onToggleRecipe = vm::toggleRecipe,
    )
}

/** Stateless editor layout (previewable without OpenGL or a ViewModel). */
@Composable
fun ShapeEditorContent(
    panel: MeshPanelState,
    ed: EditorState,
    actions: ViewerActions,
    snackbar: SnackbarHostState,
    viewport: @Composable () -> Unit,
    onParam: (String, Double) -> Unit,
    onReset: () -> Unit,
    onQuality: (Quality) -> Unit,
    onToggleRecipe: () -> Unit,
) {
    ViewerFrame(
        state = panel,
        actions = actions,
        snackbar = snackbar,
        viewport = viewport,
        viewportOverlay = { GeneratingBadge(ed) },
    ) {
        if (ed.issues.isNotEmpty()) IssuesCard(ed.issues)
        ed.doc?.let { ParamsCard(it, onParam, onReset) }
        QualityRow(ed, onQuality)
        panel.report?.let { report ->
            StatsStrip(report)
            HealthRow(report, panel.fixes)
        }
        SimplifyCard(panel, actions)
        ed.doc?.let { RecipeCard(it, ed.showRecipe, onToggleRecipe) }
    }
}

@Composable
private fun BoxScope.GeneratingBadge(ed: EditorState) {
    val shown = ed.shownQuality ?: return
    Row(
        Modifier
            .align(Alignment.TopEnd)
            .padding(10.dp)
            .background(Color(0xE6121418), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ed.generating) {
            CircularProgressIndicator(
                progress = { ed.progress },
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MeshColors.Accent,
                trackColor = MeshColors.Graphite700,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            if (shown != ed.quality) "${shown.label} preview" else shown.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (shown != ed.quality) MeshColors.Graphite300 else MeshColors.Accent,
        )
    }
}

@Composable
private fun IssuesCard(issues: List<String>) {
    Surface(shape = RoundedCornerShape(14.dp), color = MeshColors.Graphite900, border = BorderStroke(1.dp, MeshColors.Warning.copy(alpha = 0.5f))) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WarningAmber, null, tint = MeshColors.Warning, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("These settings don't make a valid shape", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            }
            Spacer(Modifier.height(4.dp))
            issues.take(4).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MeshColors.Graphite300) }
            Text("Still showing the last valid shape.", style = MaterialTheme.typography.bodySmall, color = MeshColors.Graphite500)
        }
    }
}

@Composable
private fun ParamsCard(doc: ShapeDoc, onChange: (String, Double) -> Unit, onReset: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = MeshColors.Graphite900, border = BorderStroke(1.dp, MeshColors.Graphite800)) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Parameters", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                TextButton(onClick = onReset) { Text("Reset", fontSize = 13.sp) }
            }
            doc.params.forEach { p -> ParamSlider(p) { onChange(p.name, it) } }
        }
    }
}

@Composable
private fun ParamSlider(p: ParamDef, onChange: (Double) -> Unit) {
    Column(Modifier.padding(end = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.label, style = MaterialTheme.typography.bodySmall, color = MeshColors.Graphite300, modifier = Modifier.weight(1f), maxLines = 1)
            Text(
                formatValue(p) + if (p.unit.isNotEmpty()) " ${p.unit}" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MeshColors.Graphite100,
            )
        }
        val step = if (p.integer) 1.0 else p.step
        val n = step?.let { Math.round((p.max - p.min) / it).toInt() } ?: 0
        Slider(
            value = p.value.toFloat(),
            onValueChange = { onChange(it.toDouble()) },
            valueRange = p.min.toFloat()..p.max.toFloat(),
            steps = if (n in 2..400) n - 1 else 0,
            modifier = Modifier.height(30.dp),
            colors = SliderDefaults.colors(
                thumbColor = MeshColors.Accent,
                activeTrackColor = MeshColors.Accent,
                inactiveTrackColor = MeshColors.Graphite700,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

private fun formatValue(p: ParamDef): String {
    val step = p.step ?: 0.01
    val decimals = when {
        p.integer || step >= 1 -> 0
        step >= 0.1 -> 1
        else -> 2
    }
    return String.format(Locale.US, "%.${decimals}f", p.value)
}

@Composable
private fun QualityRow(ed: EditorState, onQuality: (Quality) -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = MeshColors.Graphite900, border = BorderStroke(1.dp, MeshColors.Graphite800)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Quality", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = 4.dp, end = 8.dp))
            Quality.entries.forEach { q ->
                val selected = q == ed.quality
                Surface(
                    onClick = { onQuality(q) },
                    shape = RoundedCornerShape(50),
                    color = if (selected) MeshColors.AccentDim else Color.Transparent,
                    contentColor = if (selected) MeshColors.Accent else MeshColors.Graphite300,
                ) { Text(q.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) }
            }
            Spacer(Modifier.weight(1f))
            ed.lastMillis?.let {
                Text(String.format(Locale.US, "%.1f s", it / 1000.0), style = MaterialTheme.typography.labelSmall, color = MeshColors.Graphite500)
            }
        }
    }
}

@Composable
private fun RecipeCard(doc: ShapeDoc, expanded: Boolean, onToggle: () -> Unit) {
    Surface(onClick = onToggle, shape = RoundedCornerShape(14.dp), color = MeshColors.Graphite900, border = BorderStroke(1.dp, MeshColors.Graphite800)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Code, null, tint = MeshColors.Graphite300, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Shape recipe (JSON)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = MeshColors.Graphite300)
            }
            if (expanded) {
                val json = remember(doc) { doc.toJson() }
                Spacer(Modifier.height(8.dp))
                Text(
                    json,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = MeshColors.Graphite300,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    softWrap = false,
                )
            }
        }
    }
}
