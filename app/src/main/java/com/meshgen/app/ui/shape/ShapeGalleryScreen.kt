package com.meshgen.app.ui.shape

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meshgen.app.ui.Tag
import com.meshgen.app.ui.theme.MeshColors
import com.meshgen.core.dsl.ShapeDoc
import com.meshgen.core.dsl.Templates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Engine 1 entry: template gallery. Text prompts are added in Phase 3. */
@Composable
fun ShapeGalleryScreen(
    onOpen: (String) -> Unit,
    onOpenGenerated: () -> Unit,
    onOpenModels: () -> Unit,
    onBack: () -> Unit,
    vm: GalleryViewModel = viewModel(),
) {
    val templates by produceState<List<Pair<String, ShapeDoc>>>(emptyList()) {
        value = withContext(Dispatchers.Default) { Templates.IDS.map { it to Templates.load(it) } }
    }
    Column(Modifier.fillMaxSize().background(MeshColors.Graphite950).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
            Text("Text → Shape", style = MaterialTheme.typography.titleLarge)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(2) }) { PromptCard(vm, onOpenGenerated, onOpenModels) }
            item(span = { GridItemSpan(2) }) {
                Text(
                    "START FROM A TEMPLATE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MeshColors.Graphite500,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            items(templates, key = { it.first }) { (id, doc) -> TemplateCard(id, doc) { onOpen(id) } }
        }
    }
}

@Composable
private fun PromptCard(vm: GalleryViewModel, onReady: () -> Unit, onOpenModels: () -> Unit) {
    val states by vm.models.states.collectAsStateWithLifecycle()
    val active by vm.models.active.collectAsStateWithLifecycle()
    val progress by vm.task.progress.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val ready = remember(states, active) { vm.models.readyModel() }
    var text by rememberSaveable { mutableStateOf("") }

    Surface(shape = RoundedCornerShape(18.dp), color = MeshColors.Graphite900, border = BorderStroke(1.dp, MeshColors.Graphite800)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MeshColors.Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Describe a shape", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (ready != null) Tag(ready.name.uppercase())
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(300) },
                placeholder = { Text("e.g. a 10cm hexagonal pen holder with 3mm walls", color = MeshColors.Graphite500) },
                enabled = progress == null,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeshColors.Accent, unfocusedBorderColor = MeshColors.Graphite700),
            )
            val p = progress
            when {
                p != null -> AiProgressRow(p, onCancel = vm::cancel)
                ready == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Needs an AI model on this phone (about 1.1 GB, one-time download).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MeshColors.Graphite300,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = onOpenModels, colors = ButtonDefaults.buttonColors(containerColor = MeshColors.Accent, contentColor = MeshColors.Graphite950)) {
                        Text("Get model")
                    }
                }
                else -> Button(
                    onClick = { vm.generate(text, onReady) },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MeshColors.Accent, contentColor = MeshColors.Graphite950),
                ) { Text("Generate") }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MeshColors.Warning) }
        }
    }
}

/** Stage, detail, elapsed time and a cancel button while the AI works. */
@Composable
fun AiProgressRow(p: AiProgress, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (p.fraction != null) {
            LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth(), color = MeshColors.Accent, trackColor = MeshColors.Graphite700)
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MeshColors.Accent, trackColor = MeshColors.Graphite700)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.stage, style = MaterialTheme.typography.bodySmall, color = MeshColors.Graphite100, maxLines = 2)
                Text(
                    listOfNotNull(p.detail, "${p.elapsedMs / 1000} s").joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MeshColors.Graphite500,
                    maxLines = 2,
                )
            }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun TemplateCard(id: String, doc: ShapeDoc, onClick: () -> Unit) {
    val context = LocalContext.current
    var thumb by remember(id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(id) {
        thumb = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("thumbnails/$id.webp").use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        }
    }
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = MeshColors.Graphite900, border = BorderStroke(1.dp, MeshColors.Graphite800)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))) {
                thumb?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(doc.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    doc.category?.uppercase() ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MeshColors.Graphite500,
                    maxLines = 1,
                )
            }
        }
    }
}
