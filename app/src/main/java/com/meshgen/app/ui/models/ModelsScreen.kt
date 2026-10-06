package com.meshgen.app.ui.models

import androidx.compose.runtime.Composable
import com.meshgen.app.ui.EmptyState
import com.meshgen.app.ui.SubScreen

@Composable
fun ModelsScreen(onBack: () -> Unit) {
    SubScreen(title = "Models", onBack = onBack) {
        EmptyState(
            title = "No AI models installed",
            body = "Models are not bundled in the app. When an engine needs one, it is downloaded here on first use " +
                "— with its size, progress, resume and delete. Nothing needs downloading yet.",
        )
    }
}
