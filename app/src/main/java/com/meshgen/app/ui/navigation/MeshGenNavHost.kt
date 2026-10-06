package com.meshgen.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.meshgen.app.ui.engine.Engine
import com.meshgen.app.ui.engine.EngineScreen
import com.meshgen.app.ui.home.DeviceScreen
import com.meshgen.app.ui.home.HomeScreen
import com.meshgen.app.ui.library.LibraryScreen
import com.meshgen.app.ui.models.ModelsScreen
import com.meshgen.app.ui.samples.SamplesScreen
import com.meshgen.app.ui.shape.ShapeEditorScreen
import com.meshgen.app.ui.shape.ShapeGalleryScreen
import com.meshgen.app.ui.viewer.ViewerScreen
import com.meshgen.core.samples.SampleMesh

object Routes {
    const val HOME = "home"
    const val ENGINE = "engine/{id}"
    const val LIBRARY = "library"
    const val MODELS = "models"
    const val DEVICE = "device"
    const val SAMPLES = "samples"
    const val SHAPE_GALLERY = "shape"
    const val SHAPE_EDITOR = "shape/{template}"
    fun shapeEditor(template: String) = "shape/$template"
    const val VIEWER = "viewer/{sample}"
    fun viewer(sample: SampleMesh) = "viewer/${sample.name}"
    fun engine(engine: Engine) = "engine/${engine.id}"
}

@Composable
fun MeshGenNavHost() {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(320)) { it / 6 } },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(260)) },
        popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(280)) { it / 6 } },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenEngine = { nav.navigate(if (it == Engine.TEXT_TO_SHAPE) Routes.SHAPE_GALLERY else Routes.engine(it)) },
                onOpenLibrary = { nav.navigate(Routes.LIBRARY) },
                onOpenModels = { nav.navigate(Routes.MODELS) },
                onOpenDevice = { nav.navigate(Routes.DEVICE) },
                onOpenSamples = { nav.navigate(Routes.SAMPLES) },
            )
        }
        composable(Routes.ENGINE) { entry ->
            val engine = Engine.fromId(entry.arguments?.getString("id")) ?: Engine.TEXT_TO_SHAPE
            EngineScreen(engine = engine, onBack = back)
        }
        composable(Routes.LIBRARY) { LibraryScreen(onBack = back) }
        composable(Routes.MODELS) { ModelsScreen(onBack = back) }
        composable(Routes.DEVICE) { DeviceScreen(onBack = back) }
        composable(Routes.SAMPLES) { SamplesScreen(onOpen = { nav.navigate(Routes.viewer(it)) }, onBack = back) }
        composable(Routes.VIEWER) { ViewerScreen(onBack = back) }
        composable(Routes.SHAPE_GALLERY) { ShapeGalleryScreen(onOpen = { nav.navigate(Routes.shapeEditor(it)) }, onBack = back) }
        composable(Routes.SHAPE_EDITOR) { ShapeEditorScreen(onBack = back) }
    }
}
