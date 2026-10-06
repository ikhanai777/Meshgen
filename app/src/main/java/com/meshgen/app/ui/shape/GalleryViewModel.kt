package com.meshgen.app.ui.shape

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meshgen.app.MeshGenApp
import com.meshgen.app.PendingDesign
import com.meshgen.core.llm.ShapeAgent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Text → shape from the gallery screen. */
class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val meshApp = app as MeshGenApp
    val task = AiTask(meshApp, viewModelScope)
    val models = meshApp.models

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun generate(request: String, onReady: () -> Unit) {
        if (request.isBlank()) return
        _message.value = null
        task.run(
            work = { agent, onEvent, cancelled -> agent.create(request, onEvent, cancelled) },
            onDone = { r ->
                val doc = r.doc
                if (doc == null) {
                    _message.value = "Couldn't make a printable shape from that description after ${r.attempts} tries. " +
                        "Try simpler wording, or start from a template below." +
                        (r.problems.firstOrNull()?.let { "\nLast problem: $it" } ?: "")
                } else {
                    val label = when (r.source) {
                        ShapeAgent.Source.TEMPLATE -> "Based on the “${com.meshgen.core.dsl.Templates.load(r.templateId!!).name}” template"
                        else -> "Custom recipe written by the AI (best effort)"
                    }
                    meshApp.pendingDesign = PendingDesign(doc, r.notes, label)
                    onReady()
                }
            },
            onError = { _message.value = it },
        )
    }

    fun cancel() = task.cancel()
}
