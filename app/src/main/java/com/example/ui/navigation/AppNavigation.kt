package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.di.AppContainer
import com.example.ui.languages.LanguageManagerScreen
import com.example.ui.languages.LanguageManagerViewModel
import com.example.ui.library.LibraryScreen
import com.example.ui.library.LibraryViewModel
import com.example.ui.reader.ReaderScreen
import com.example.ui.reader.ReaderViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

@Serializable
data object LanguagesRoute

@Serializable
data object SettingsRoute

@Serializable
data class ReaderRoute(val bookId: Long)

private data class TopLevelDestination(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TopLevelDestinations = listOf(
    TopLevelDestination(
        route = LibraryRoute,
        label = "Biblioteca",
        selectedIcon = Icons.AutoMirrored.Filled.MenuBook,
        unselectedIcon = Icons.AutoMirrored.Outlined.MenuBook,
        testTag = "nav_library"
    ),
    TopLevelDestination(
        route = LanguagesRoute,
        label = "Idiomas",
        selectedIcon = Icons.Filled.Translate,
        unselectedIcon = Icons.Outlined.Translate,
        testTag = "nav_languages"
    ),
    TopLevelDestination(
        route = SettingsRoute,
        label = "Ajustes",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings"
    )
)

@Composable
fun KindleTranslateApp(appContainer: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isInReader = currentDestination?.hasRoute<ReaderRoute>() == true

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        if (isInReader) {
            AppNavHost(
                navController = navController,
                appContainer = appContainer,
                onNavigateToLanguages = {
                    navController.popBackStack()
                    navigateTopLevel(LanguagesRoute)
                }
            )
        } else if (useNavigationRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail {
                    TopLevelDestinations.forEach { dest ->
                        val selected = when (dest.route) {
                            LibraryRoute -> currentDestination?.hasRoute<LibraryRoute>() == true
                            LanguagesRoute -> currentDestination?.hasRoute<LanguagesRoute>() == true
                            SettingsRoute -> currentDestination?.hasRoute<SettingsRoute>() == true
                            else -> false
                        }
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTopLevel(dest.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = dest.label
                                )
                            },
                            label = { Text(dest.label) },
                            modifier = Modifier.testTag(dest.testTag)
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    AppNavHost(
                        navController = navController,
                        appContainer = appContainer,
                        onNavigateToLanguages = { navigateTopLevel(LanguagesRoute) }
                    )
                }
            }
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        TopLevelDestinations.forEach { dest ->
                            val selected = when (dest.route) {
                                LibraryRoute -> currentDestination?.hasRoute<LibraryRoute>() == true
                                LanguagesRoute -> currentDestination?.hasRoute<LanguagesRoute>() == true
                                SettingsRoute -> currentDestination?.hasRoute<SettingsRoute>() == true
                                else -> false
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                    AppNavHost(
                        navController = navController,
                        appContainer = appContainer,
                        onNavigateToLanguages = { navigateTopLevel(LanguagesRoute) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: androidx.navigation.NavHostController,
    appContainer: AppContainer,
    onNavigateToLanguages: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = LibraryRoute
    ) {
        composable<LibraryRoute> {
            val vm: LibraryViewModel = viewModel(
                factory = LibraryViewModel.factory(
                    bookRepository = appContainer.bookRepository,
                    preferencesRepository = appContainer.preferencesRepository
                )
            )
            LibraryScreen(
                viewModel = vm,
                onOpenBook = { bookId ->
                    navController.navigate(ReaderRoute(bookId = bookId))
                }
            )
        }

        composable<LanguagesRoute> {
            val vm: LanguageManagerViewModel = viewModel(
                factory = LanguageManagerViewModel.factory(
                    translationManager = appContainer.translationManager,
                    preferencesRepository = appContainer.preferencesRepository
                )
            )
            LanguageManagerScreen(viewModel = vm)
        }

        composable<SettingsRoute> {
            val vm: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(
                    bookRepository = appContainer.bookRepository,
                    preferencesRepository = appContainer.preferencesRepository
                )
            )
            SettingsScreen(viewModel = vm)
        }

        composable<ReaderRoute> { entry ->
            val route = entry.toRoute<ReaderRoute>()
            val vm: ReaderViewModel = viewModel(
                key = "reader_${route.bookId}",
                factory = ReaderViewModel.factory(
                    bookId = route.bookId,
                    bookRepository = appContainer.bookRepository,
                    preferencesRepository = appContainer.preferencesRepository,
                    translationManager = appContainer.translationManager
                )
            )
            ReaderScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenLanguageManager = onNavigateToLanguages
            )
        }
    }
}
