package com.elendheim.notes.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.elendheim.notes.ui.editor.EditorScreen
import com.elendheim.notes.ui.home.FolderScreen
import com.elendheim.notes.ui.home.HomeScreen

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val viewModel: NotesViewModel = viewModel(factory = NotesViewModel.Factory)

    NavHost(
        navController = navController,
        startDestination = "home",
        enterTransition = {
            slideInHorizontally(animationSpec = tween(260)) { it / 4 } + fadeIn(tween(260))
        },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = {
            slideOutHorizontally(animationSpec = tween(260)) { it / 4 } + fadeOut(tween(260))
        }
    ) {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onOpenNote = { id -> navController.navigate("note/$id") },
                onOpenFolder = { id -> navController.navigate("folder/$id") }
            )
        }
        composable(
            route = "folder/{folderId}",
            arguments = listOf(navArgument("folderId") { type = NavType.LongType })
        ) { entry ->
            FolderScreen(
                folderId = entry.arguments?.getLong("folderId") ?: return@composable,
                viewModel = viewModel,
                onOpenNote = { id -> navController.navigate("note/$id") },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "note/{noteId}",
            arguments = listOf(navArgument("noteId") { type = NavType.LongType })
        ) { entry ->
            EditorScreen(
                noteId = entry.arguments?.getLong("noteId") ?: return@composable,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
