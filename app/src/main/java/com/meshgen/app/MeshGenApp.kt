package com.meshgen.app

import android.app.Application
import android.content.ComponentCallbacks2
import com.meshgen.app.llm.LlamaEngine
import com.meshgen.app.llm.ModelManager
import com.meshgen.core.dsl.ShapeDoc
import com.meshgen.core.llm.ShapeAgent

/** App-wide singletons. The language model stays loaded across screens and is freed when memory runs low. */
class MeshGenApp : Application() {
    lateinit var models: ModelManager
        private set
    lateinit var llm: LlamaEngine
        private set
    val agent: ShapeAgent by lazy { ShapeAgent(llm) }

    /** Hand-off of a freshly generated design from the prompt screen to the editor. */
    var pendingDesign: PendingDesign? = null

    override fun onCreate() {
        super.onCreate()
        models = ModelManager(this)
        llm = LlamaEngine(this, models)
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // In the background or under memory pressure: give the model's memory back rather than risk being killed.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            Thread { llm.release() }.start()
        }
    }
}

class PendingDesign(val doc: ShapeDoc, val notes: List<String>, val sourceLabel: String)
