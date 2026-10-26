package com.animeshadow.app.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Recommend
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.animeshadow.app.R
import com.animeshadow.app.data.settings.Profile
import com.animeshadow.app.data.settings.SettingsRepository
import com.animeshadow.app.sync.SyncScheduler
import com.animeshadow.app.ui.browse.BrowseScreen
import com.animeshadow.app.ui.collections.FavoriteAnimeScreen
import com.animeshadow.app.ui.collections.FavoriteCharactersScreen
import com.animeshadow.app.ui.collections.HistoryScreen
import com.animeshadow.app.ui.collections.PopularCharactersScreen
import com.animeshadow.app.ui.details.DetailsScreen
import com.animeshadow.app.ui.discover.RecommendationsScreen
import com.animeshadow.app.ui.discover.ScheduleScreen
import com.animeshadow.app.ui.feed.HomeScreen
import com.animeshadow.app.ui.feed.GenreScreen
import com.animeshadow.app.ui.feed.StudioScreen
import com.animeshadow.app.ui.notifications.NotificationsScreen
import com.animeshadow.app.data.NotificationRepository
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.ui.platform.testTag
import com.animeshadow.app.ui.feed.SeasonsScreen
import com.animeshadow.app.ui.feed.TopScreen
import com.animeshadow.app.ui.library.MyListScreen
import com.animeshadow.app.ui.person.CharacterScreen
import com.animeshadow.app.ui.person.StaffScreen
import com.animeshadow.app.ui.profile.ProfileScreen
import com.animeshadow.app.ui.settings.SettingsScreen
import com.animeshadow.app.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

object Routes {
    const val LATEST = "latest"
    const val BROWSE = "browse"
    const val SEARCH = "search"
    const val SEASONS = "seasons"
    const val TOP = "top"
    const val MYLIST = "mylist"
    const val FAV_ANIME = "fav_anime"
    const val FAV_CHARACTERS = "fav_characters"
    const val HISTORY = "history"
    const val POPULAR_CHARACTERS = "popular_characters"
    const val RECOMMENDATIONS = "recommendations"
    const val SCHEDULE = "schedule"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val NOTIFICATIONS = "notifications"
    const val GENRE = "genre/{name}"
    fun genre(name: String) = "genre/" + android.net.Uri.encode(name)
    const val STUDIO = "studio/{name}"
    fun studio(name: String) = "studio/" + android.net.Uri.encode(name)
    const val ANIME = "anime/{id}"
    const val CHARACTER = "character/{id}"
    const val STAFF = "staff/{id}"
    fun anime(id: Int) = "anime/$id"
    fun character(id: Int) = "character/$id"
    fun staff(id: Int) = "staff/$id"
}

data class DrawerEntry(val route: String, @StringRes val label: Int, val icon: ImageVector, val dividerBefore: Boolean = false)

val DrawerEntries = listOf(
    DrawerEntry(Routes.LATEST, R.string.nav_latest, Icons.Filled.LocalFireDepartment),
    DrawerEntry(Routes.BROWSE, R.string.nav_browse, Icons.AutoMirrored.Filled.List),
    DrawerEntry(Routes.SEASONS, R.string.nav_seasons, Icons.Filled.ViewModule),
    DrawerEntry(Routes.TOP, R.string.nav_top, Icons.Filled.Star, dividerBefore = true),
    DrawerEntry(Routes.MYLIST, R.string.nav_mylist, Icons.Filled.Bookmarks, dividerBefore = true),
    DrawerEntry(Routes.FAV_ANIME, R.string.nav_fav_anime, Icons.Filled.Favorite),
    DrawerEntry(Routes.FAV_CHARACTERS, R.string.nav_fav_characters, Icons.Filled.People),
    DrawerEntry(Routes.HISTORY, R.string.nav_history, Icons.Filled.History),
    DrawerEntry(Routes.STATS, R.string.nav_stats, Icons.Filled.BarChart),
    DrawerEntry(Routes.POPULAR_CHARACTERS, R.string.nav_popular_characters, Icons.Filled.People, dividerBefore = true),
    DrawerEntry(Routes.RECOMMENDATIONS, R.string.nav_recommendations, Icons.Filled.Recommend),
    DrawerEntry(Routes.SCHEDULE, R.string.nav_schedule, Icons.Filled.CalendarMonth),
    DrawerEntry(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings, dividerBefore = true),
)

@HiltViewModel
class RootViewModel @Inject constructor(
    settings: SettingsRepository,
    notifications: NotificationRepository,
    @ApplicationContext context: Context,
) : ViewModel() {
    val profile: StateFlow<Profile> = settings.settings.map { it.profile }.stateIn(viewModelScope, SharingStarted.Eagerly, Profile())
    val unread: StateFlow<Int> = notifications.observeUnreadCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        viewModelScope.launch {
            val s = settings.settings.first()
            LocaleController.apply()
            runCatching { SyncScheduler.schedule(context, s.notifications) }
        }
    }
}

@Composable
fun DrawerContent(current: String?, profile: Profile, onSelect: (String) -> Unit, unread: Int = 0) {
    ModalDrawerSheet(drawerContainerColor = Palette.Surface, modifier = Modifier.width(300.dp)) {
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().clickable { onSelect(Routes.STATS) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val m = Modifier.size(56.dp).clip(CircleShape).border(2.dp, Palette.Accent, CircleShape)
                if (profile.avatarPath != null) AsyncImage(File(profile.avatarPath), null, m, contentScale = ContentScale.Crop)
                else Image(painterResource(R.drawable.as_logo), null, m.background(Palette.Black), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.name.ifBlank { stringResource(R.string.profile_default_name) }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("\u2068" + (if (profile.handle.isNotBlank()) "@" + profile.handle else stringResource(R.string.tagline)) + "\u2069", style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
                }
                // v1.0 bug: the bell opened the schedule. It opens the Notifications history now.
                IconButton(onClick = { onSelect(Routes.NOTIFICATIONS) }, modifier = Modifier.testTag("drawer_bell")) {
                    BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 99) "99+" else unread.toString()) } }) {
                        Icon(Icons.Filled.Notifications, stringResource(R.string.cd_notifications), tint = Palette.Accent)
                    }
                }
            }
            DrawerEntries.forEach { e ->
                if (e.dividerBefore) HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = Palette.Divider)
                NavigationDrawerItem(
                    label = { Text(stringResource(e.label), style = MaterialTheme.typography.bodyLarge) },
                    icon = { Icon(e.icon, null) },
                    selected = current == e.route,
                    onClick = { onSelect(e.route) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = Palette.AccentSoft,
                        selectedIconColor = Palette.Accent,
                        selectedTextColor = Palette.Text,
                        unselectedContainerColor = Palette.Surface,
                        unselectedIconColor = Palette.TextSecondary,
                        unselectedTextColor = Palette.Text,
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
            }
            Spacer(Modifier.padding(8.dp))
        }
    }
}

@Composable
fun AppRoot(openAnimeId: Int? = null, onOpened: () -> Unit = {}, vm: RootViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val unread by vm.unread.collectAsStateWithLifecycle()
    val back by nav.currentBackStackEntryAsState()
    val current = back?.destination?.route
    val topLevel = DrawerEntries.any { it.route == current }

    LaunchedEffect(openAnimeId) {
        if (openAnimeId != null) {
            nav.navigate(Routes.anime(openAnimeId))
            onOpened()
        }
    }

    val openMenu: () -> Unit = { scope.launch { drawer.open() } }
    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = topLevel,
        drawerContent = {
            DrawerContent(current, profile, { route ->
                scope.launch { drawer.close() }
                if (route != current) nav.navigate(route) {
                    popUpTo(Routes.LATEST) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }, unread)
        },
    ) {
        AppNavHost(nav, openMenu, drawer)
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, openMenu: () -> Unit, @Suppress("UNUSED_PARAMETER") drawer: DrawerState) {
    val open: (Int) -> Unit = { nav.navigate(Routes.anime(it)) }
    val openCharacter: (Int) -> Unit = { nav.navigate(Routes.character(it)) }
    val openStaff: (Int) -> Unit = { nav.navigate(Routes.staff(it)) }
    val search: () -> Unit = { nav.navigate(Routes.SEARCH) }
    NavHost(nav, startDestination = Routes.LATEST) {
        composable(Routes.LATEST) { HomeScreen(openMenu, search, open) }
        composable(Routes.BROWSE) { BrowseScreen(autoFocus = false, onMenu = openMenu, onBack = null, onOpen = open) }
        composable(Routes.SEARCH) { BrowseScreen(autoFocus = true, onMenu = openMenu, onBack = { nav.popBackStack() }, onOpen = open) }
        composable(Routes.SEASONS) { SeasonsScreen(openMenu, search, open) }
        composable(Routes.TOP) { TopScreen(openMenu, search, open) }
        composable(Routes.MYLIST) { MyListScreen(openMenu, open) }
        composable(Routes.FAV_ANIME) { FavoriteAnimeScreen(openMenu, open) }
        composable(Routes.FAV_CHARACTERS) { FavoriteCharactersScreen(openMenu, openCharacter) }
        composable(Routes.HISTORY) { HistoryScreen(openMenu, open) }
        composable(Routes.POPULAR_CHARACTERS) { PopularCharactersScreen(openMenu, openCharacter) }
        composable(Routes.RECOMMENDATIONS) { RecommendationsScreen(openMenu, open) }
        composable(Routes.SCHEDULE) { ScheduleScreen(openMenu, open) }
        composable(Routes.STATS) { ProfileScreen(openMenu) }
        composable(Routes.SETTINGS) { SettingsScreen(openMenu) }
        composable(Routes.NOTIFICATIONS) { NotificationsScreen({ nav.popBackStack() }, open) }
        composable(Routes.GENRE, arguments = listOf(navArgument("name") { type = NavType.StringType })) {
            GenreScreen({ nav.popBackStack() }, open)
        }
        composable(Routes.STUDIO, arguments = listOf(navArgument("name") { type = NavType.StringType })) {
            StudioScreen({ nav.popBackStack() }, open)
        }
        composable(Routes.ANIME, arguments = listOf(navArgument("id") { type = NavType.IntType })) {
            DetailsScreen({ nav.popBackStack() }, open, openCharacter, openStaff, onGenre = { nav.navigate(Routes.genre(it)) }, onStudio = { nav.navigate(Routes.studio(it)) })
        }
        composable(Routes.CHARACTER, arguments = listOf(navArgument("id") { type = NavType.IntType })) {
            CharacterScreen({ nav.popBackStack() }, open, openStaff)
        }
        composable(Routes.STAFF, arguments = listOf(navArgument("id") { type = NavType.IntType })) {
            StaffScreen({ nav.popBackStack() }, open, openCharacter)
        }
    }
}
