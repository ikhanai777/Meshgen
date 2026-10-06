package com.meshgen.app.ui.samples

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meshgen.app.ui.SubScreen
import com.meshgen.app.ui.theme.MeshColors
import com.meshgen.core.samples.SampleMesh

@Composable
fun SamplesScreen(onOpen: (SampleMesh) -> Unit, onBack: () -> Unit) {
    SubScreen(title = "Sample meshes", onBack = onBack) {
        Text(
            "Built-in test shapes for the viewer, mesh checks and export. Engine-generated models will open in the same viewer.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SampleMesh.entries.forEach { s ->
            Surface(
                onClick = { onOpen(s) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MeshColors.Graphite900,
                border = BorderStroke(1.dp, MeshColors.Graphite800),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.title, style = MaterialTheme.typography.titleMedium)
                        Text(s.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = MeshColors.Graphite300)
                }
            }
        }
    }
}
