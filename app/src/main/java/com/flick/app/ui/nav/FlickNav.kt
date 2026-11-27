package com.flick.app.ui.nav

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.flick.app.AppContainer
import com.flick.app.ui.editor.EditorScreen
import com.flick.app.ui.editor.EditorViewModel
import com.flick.app.ui.home.HomeScreen
import com.flick.app.ui.home.HomeViewModel

/**
 * Two routes only: home and editor/{noteId}.
 * The card -> editor expansion is a shared-bounds transition across the two.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FlickNav(container: AppContainer) {
    val nav = rememberNavController()
    SharedTransitionLayout {
        NavHost(
            navController = nav,
            startDestination = "home",
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { fadeOut(tween(200)) },
        ) {
            composable("home") {
                val vm = viewModel<HomeViewModel>(
                    factory = factoryOf { HomeViewModel(container.repo, container.settings) },
                )
                HomeScreen(
                    vm = vm,
                    sharedScope = this@SharedTransitionLayout,
                    animScope = this,
                    onOpenNote = { id -> nav.navigate("editor/$id") },
                )
            }
            composable(
                route = "editor/{noteId}",
                arguments = listOf(navArgument("noteId") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("noteId") ?: return@composable
                val vm = viewModel<EditorViewModel>(
                    key = "editor-$id",
                    factory = factoryOf { EditorViewModel(id, container.repo) },
                )
                EditorScreen(
                    vm = vm,
                    noteId = id,
                    sharedScope = this@SharedTransitionLayout,
                    animScope = this,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
