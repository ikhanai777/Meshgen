package com.meshgen.app.ui.models

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshgen.app.MeshGenApp
import com.meshgen.app.llm.ModelManager
import com.meshgen.app.llm.ModelSpec
import com.meshgen.app.llm.ModelState
import com.meshgen.app.llm.ModelCatalog
import com.meshgen.app.ui.SubScreen
import com.meshgen.app.ui.Tag
import com.meshgen.app.ui.theme.MeshColors

@Composable
fun ModelsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MeshGenApp
    val states by app.models.states.collectAsStateWithLifecycle()
    val active by app.models.active.collectAsStateWithLifecycle()
    val ramGb = remember {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        ActivityManager.MemoryInfo().also(am::getMemoryInfo).totalMem / (1024.0 * 1024 * 1024)
    }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<ModelSpec?>(null) }

    SubScreen(title = "AI models", onBack = onBack) {
        Text(
            "Models run entirely on this phone. Download one to describe shapes in words. " +
                "Files are checked after download; downloads continue in the background and resume after connection loss.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Free storage: ${ModelManager.gb(app.models.freeSpaceBytes())}", style = MaterialTheme.typography.labelMedium, color = MeshColors.Graphite500)
        for (spec in ModelCatalog.ALL) {
            val state = states[spec.id] ?: ModelState.NotDownloaded
            ModelCard(
                spec = spec,
                state = state,
                active = state == ModelState.Ready && (active?.id == spec.id || (active == null && app.models.readyModel()?.id == spec.id)),
                fitsDevice = ramGb >= spec.minRamGb,
                onDownload = { error = app.models.download(spec) },
                onCancel = { app.models.cancel(spec) },
                onDelete = { confirmDelete = spec },
                onUse = { app.models.setActive(spec) },
            )
        }
        error?.let { Text(it, color = MeshColors.Warning, style = MaterialTheme.typography.bodyMedium) }
    }

    confirmDelete?.let { spec ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${spec.name}?") },
            text = { Text("Frees ${ModelManager.gb(spec.sizeBytes)}. You can download it again later.") },
            confirmButton = {
                TextButton(onClick = {
                    if (app.llm.loadedModel?.id == spec.id) app.llm.release()
                    app.models.delete(spec)
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun ModelCard(
    spec: ModelSpec,
    state: ModelState,
    active: Boolean,
    fitsDevice: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onUse: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MeshColors.Graphite900,
        border = BorderStroke(1.dp, if (active) MeshColors.AccentDim else MeshColors.Graphite800),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state == ModelState.Ready) {
                    Surface(onClick = onUse, shape = RoundedCornerShape(50), color = androidx.compose.ui.graphics.Color.Transparent) {
                        Icon(
                            if (active) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = if (active) "In use" else "Use this model",
                            tint = if (active) MeshColors.Accent else MeshColors.Graphite300,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(spec.name, style = MaterialTheme.typography.titleMedium)
                    Text(spec.tagline, style = MaterialTheme.typography.bodySmall, color = MeshColors.Graphite300)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(ModelManager.gb(spec.sizeBytes).uppercase())
                Tag(spec.license.uppercase())
                if (!fitsDevice) Tag("MAY BE TOO BIG FOR THIS PHONE")
            }
            when (state) {
                ModelState.NotDownloaded, is ModelState.Failed -> {
                    if (state is ModelState.Failed) Text(state.message, color = MeshColors.Warning, style = MaterialTheme.typography.bodySmall)
                    if (!fitsDevice) {
                        Text(
                            "This phone has less memory than this model needs (${spec.minRamGb.toInt()}+ GB recommended). It may fail to start.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MeshColors.Graphite300,
                        )
                    }
                    Button(
                        onClick = onDownload,
                        colors = ButtonDefaults.buttonColors(containerColor = MeshColors.Accent, contentColor = MeshColors.Graphite950),
                    ) { Text("Download ${ModelManager.gb(spec.sizeBytes)}") }
                }
                is ModelState.Downloading -> {
                    val f = if (state.total > 0) state.bytes.toFloat() / state.total else 0f
                    LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth(), color = MeshColors.Accent, trackColor = MeshColors.Graphite700)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${ModelManager.gb(state.bytes)} of ${ModelManager.gb(state.total)}" + (state.waiting?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MeshColors.Graphite300,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onCancel) { Text("Cancel") }
                    }
                }
                is ModelState.Verifying -> {
                    LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth(), color = MeshColors.Accent, trackColor = MeshColors.Graphite700)
                    Text("Checking the file…", style = MaterialTheme.typography.bodySmall, color = MeshColors.Graphite300)
                }
                ModelState.Ready -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (active) "Ready · in use" else "Ready",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (active) MeshColors.Accent else MeshColors.Graphite100,
                        modifier = Modifier.weight(1f),
                    )
                    if (!active) OutlinedButton(onClick = onUse) { Text("Use") }
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
