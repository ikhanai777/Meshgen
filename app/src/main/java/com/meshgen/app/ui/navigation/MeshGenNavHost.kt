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

object Routes {
    const val HOME = "home"
    const val ENGINE = "engine/{id}"
    const val LIBRARY = "library"
    const val MODELS = "models"
    const val DEVICE = "device"
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
                onOpenEngine = { nav.navigate(Routes.engine(it)) },
                onOpenLibrary = { nav.navigate(Routes.LIBRARY) },
                onOpenModels = { nav.navigate(Routes.MODELS) },
                onOpenDevice = { nav.navigate(Routes.DEVICE) },
            )
        }
        composable(Routes.ENGINE) { entry ->
            val engine = Engine.fromId(entry.arguments?.getString("id")) ?: Engine.TEXT_TO_SHAPE
            EngineScreen(engine = engine, onBack = back)
        }
        composable(Routes.LIBRARY) { LibraryScreen(onBack = back) }
        composable(Routes.MODELS) { ModelsScreen(onBack = back) }
        composable(Routes.DEVICE) { DeviceScreen(onBack = back) }
    }
}
