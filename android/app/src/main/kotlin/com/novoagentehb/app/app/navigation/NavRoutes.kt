package com.novoagentehb.app.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.novoagentehb.app.design.GabiTokens
import com.novoagentehb.app.ui.chat.ChatScreen
import com.novoagentehb.app.ui.computer.ComputerScreen
import com.novoagentehb.app.ui.feed.FeedScreen
import com.novoagentehb.app.ui.goals.GoalsScreen
import com.novoagentehb.app.ui.habitat.QuartoGabiScreen
import com.novoagentehb.app.ui.home.HomeScreen
import com.novoagentehb.app.ui.library.LibraryScreen
import com.novoagentehb.app.ui.memory.MemoryScreen
import com.novoagentehb.app.ui.permissions.PermissionsScreen
import com.novoagentehb.app.ui.permissions.PermissionsViewModel
import com.novoagentehb.app.ui.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Chat : Screen("chat")
    object Habitat : Screen("habitat")
    object Feed : Screen("feed")
    object Goals : Screen("goals")
    object Library : Screen("library")
    object Memory : Screen("memory")
    object Computer : Screen("computer")
    object Settings : Screen("settings")
    object Permissions : Screen("permissions")

    companion object {
        // Do not initialize nested screen objects during Screen class initialization.
        // The initial destination may itself be the first object being initialized.
        val allRoutes: List<String> get() = listOf(
            Home.route,
            Chat.route,
            Habitat.route,
            Feed.route,
            Goals.route,
            Library.route,
            Memory.route,
            Computer.route,
            Settings.route,
            Permissions.route
        )
    }
}

@Composable
fun NovoAgenteNavGraph(
    navController: NavHostController = rememberNavController(),
    autoStartListening: MutableState<Boolean>? = null,
    pendingRoute: MutableState<String?>? = null
) {
    val permissionsViewModel: PermissionsViewModel = hiltViewModel()
    val onboarded by permissionsViewModel.onboardingCompleted.collectAsState(initial = null)

    val resolvedOnboarded = onboarded
    if (resolvedOnboarded == null) {
        Box(modifier = Modifier.fillMaxSize().background(GabiTokens.NightBg))
        return
    }

    LaunchedEffect(pendingRoute?.value) {
        val target = pendingRoute?.value
        if (!target.isNullOrBlank()) {
            navController.navigate(target) { launchSingleTop = true }
            pendingRoute.value = null
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (resolvedOnboarded) Screen.Home.route else Screen.Permissions.route,
        enterTransition = { fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 10 } },
        exitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { -it / 10 } },
        popEnterTransition = { fadeIn(tween(200)) + slideInHorizontally(tween(200)) { -it / 10 } },
        popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { it / 10 } }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(navController = navController, autoStartListening = autoStartListening)
        }
        composable(Screen.Chat.route) {
            ChatScreen(navController = navController)
        }
        composable(Screen.Habitat.route) {
            QuartoGabiScreen(navController = navController)
        }
        composable(Screen.Feed.route) {
            FeedScreen(navController = navController)
        }
        composable(Screen.Goals.route) {
            GoalsScreen(navController = navController)
        }
        composable(Screen.Library.route) {
            LibraryScreen(navController = navController)
        }
        composable(Screen.Memory.route) {
            MemoryScreen(navController = navController)
        }
        composable(Screen.Computer.route) {
            ComputerScreen(navController = navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
        composable(Screen.Permissions.route) {
            PermissionsScreen(navController = navController, viewModel = permissionsViewModel)
        }
    }
}
