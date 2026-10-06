package com.meshgen.app.ui.engine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.meshgen.app.ui.Panel
import com.meshgen.app.ui.StatusDot
import com.meshgen.app.ui.SubScreen
import com.meshgen.app.ui.Tag
import com.meshgen.app.ui.theme.MeshColors

/** Placeholder until the engine is built. States plainly that nothing runs yet. */
@Composable
fun EngineScreen(engine: Engine, onBack: () -> Unit) {
    SubScreen(title = engine.title, onBack = onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Tag(engine.badge, accent = !engine.experimental) }
        Text(engine.description, style = MaterialTheme.typography.bodyLarge)
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusDot(MeshColors.Warning)
                Text("Not built yet", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "This engine arrives in Phase ${engine.plannedPhase}. Nothing is generated on this screen yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (engine.experimental) {
            Text(
                "Flagship phone recommended. When it ships, you'll see an honest time estimate before starting and can cancel at any time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
