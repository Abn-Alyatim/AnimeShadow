package com.animeshadow.app.manga.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.animeshadow.app.manga.reader.ReaderScreen
import com.animeshadow.app.ui.Routes
import com.animeshadow.app.ui.sharedScreens

/** v1.3 MANGA mode's own navigation (its own back stack: switching to anime and back finds it where you left it). */
@Composable
fun MangaNavGraph(nav: NavHostController, openMenu: () -> Unit, toggle: @Composable () -> Unit) {
    val openTitle: (Int) -> Unit = { nav.navigate(Routes.mTitle(it)) }
    val openGenre: (String) -> Unit = { nav.navigate(Routes.mGenre(it)) }
    NavHost(nav, startDestination = Routes.M_HOME) {
        composable(Routes.M_HOME) { MangaHomeScreen(openMenu, { nav.navigate(Routes.M_SEARCH) }, openTitle, openGenre, toggle) }
        composable(Routes.M_SEARCH) { MangaSearchScreen(onBack = null, onMenu = openMenu, onOpen = openTitle) }
        composable(Routes.M_GENRE, arguments = listOf(navArgument("name") { type = NavType.StringType })) {
            MangaSearchScreen(onBack = { nav.popBackStack() }, onMenu = openMenu, onOpen = openTitle)
        }
        composable(Routes.M_LIBRARY) {
            val vm: MangaLibraryViewModel = hiltViewModel()
            val ui by vm.ui.collectAsStateWithLifecycle()
            MangaLibraryContent(ui, openMenu, { vm.toggleGrid() }, { openTitle(it.id) })
        }
        composable(Routes.M_FAVORITES) {
            val vm: MangaLibraryViewModel = hiltViewModel()
            val ui by vm.ui.collectAsStateWithLifecycle()
            MangaFavoritesContent(ui, openMenu, { vm.toggleGrid() }, { openTitle(it.id) })
        }
        composable(Routes.M_HISTORY) {
            val vm: MangaLibraryViewModel = hiltViewModel()
            val ui by vm.ui.collectAsStateWithLifecycle()
            MangaHistoryContent(ui.history, openMenu, openTitle)
        }
        composable(Routes.M_DOWNLOADED) {
            val vm: DownloadedViewModel = hiltViewModel()
            val ui by vm.ui.collectAsStateWithLifecycle()
            DownloadedContent(ui, openMenu, vm::setQuery, { vm.toggleGrid() }) { nav.navigate(Routes.mTitle(it, downloaded = true)) }
        }
        composable(Routes.M_QUEUE) { DownloadQueueScreen({ if (!nav.popBackStack()) nav.navigate(Routes.M_HOME) }) }
        composable(
            Routes.M_TITLE,
            arguments = listOf(navArgument("id") { type = NavType.IntType }, navArgument("downloaded") { type = NavType.BoolType; defaultValue = false }),
        ) {
            MangaTitleScreen({ nav.popBackStack() }, openGenre, onRead = { id, key -> nav.navigate(Routes.mReader(id, key)) }, onOpenTitle = openTitle)
        }
        composable(
            Routes.M_READER,
            arguments = listOf(navArgument("id") { type = NavType.IntType }, navArgument("key") { type = NavType.StringType }),
        ) { ReaderScreen({ nav.popBackStack() }) }
        sharedScreens(nav, openMenu, manga = true) { }
    }
}
