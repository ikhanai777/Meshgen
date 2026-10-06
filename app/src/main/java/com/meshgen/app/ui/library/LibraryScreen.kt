package com.meshgen.app.ui.library

import androidx.compose.runtime.Composable
import com.meshgen.app.ui.EmptyState
import com.meshgen.app.ui.SubScreen

@Composable
fun LibraryScreen(onBack: () -> Unit) {
    SubScreen(title = "Library", onBack = onBack) {
        EmptyState(
            title = "No generations yet",
            body = "Every mesh you create will be saved here with a thumbnail, its dimensions and its editable parameters.",
        )
    }
}
