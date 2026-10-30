package com.animeshadow.app.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Recommend
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Category
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import com.animeshadow.app.data.auth.GoogleAccountRepository
import com.animeshadow.app.data.settings.AppMode
import com.animeshadow.app.manga.ui.MangaNavGraph
import com.animeshadow.app.ui.auth.LoginScreen
import com.animeshadow.app.ui.profile.EditProfileScreen
import kotlinx.coroutines.flow.MutableStateFlow

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
    const val EDIT_PROFILE = "edit_profile"
    const val LOGIN = "login"
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

    // v1.3 MANGA mode (its own NavHost; profile / settings / login / notifications exist in both).
    const val M_HOME = "m_home"
    const val M_SEARCH = "m_search"
    const val M_LIBRARY = "m_library"
    const val M_FAVORITES = "m_favorites"
    const val M_HISTORY = "m_history"
    const val M_DOWNLOADED = "m_downloaded"
    const val M_QUEUE = "m_queue"
    const val M_TITLE = "m_title/{id}?downloaded={downloaded}"
    fun mTitle(id: Int, downloaded: Boolean = false) = "m_title/$id?downloaded=$downloaded"
    const val M_GENRE = "m_genre/{name}"
    fun mGenre(name: String) = "m_genre/" + android.net.Uri.encode(name)
    const val M_READER = "m_reader/{id}/{key}"
    fun mReader(id: Int, key: String) = "m_reader/$id/" + android.net.Uri.encode(key)

    // v1.4.0
    const val CATEGORIES = "categories"
    const val M_CATEGORIES = "m_categories"
    /** «شادو سينباي»: history list, one conversation, customisation (in both modes). */
    const val CHAT = "chat"
    const val CHAT_CONV = "chat/c/{id}"
    fun chatConv(id: String) = "chat/c/" + android.net.Uri.encode(id)
    const val CHAT_SETTINGS = "chat/settings"
    /** v1.5.0: friends + user-to-user chat, and list export/import/sharing (in both modes). */
    const val FRIENDS = "friends"
    const val THREAD = "friends/t/{id}"
    fun thread(id: String) = "friends/t/" + android.net.Uri.encode(id)
    const val LISTS = "lists"
    const val LISTS_BACK = "lists/back"
    fun tag(name: String) = "genre/" + android.net.Uri.encode(com.animeshadow.app.domain.BrowseQuery.TAG_PREFIX + name)
    fun mTag(name: String) = "m_genre/" + android.net.Uri.encode(com.animeshadow.app.domain.BrowseQuery.TAG_PREFIX + name)
}

/** [avatar]: v1.4.0 «شادو سينباي» row shows the assistant's round picture instead of an icon. */
data class DrawerEntry(val route: String, @StringRes val label: Int, val icon: ImageVector, val dividerBefore: Boolean = false, val avatar: Boolean = false)

val DrawerEntries = listOf(
    DrawerEntry(Routes.LATEST, R.string.nav_latest, Icons.Filled.LocalFireDepartment),
    DrawerEntry(Routes.BROWSE, R.string.nav_browse, Icons.AutoMirrored.Filled.List),
    DrawerEntry(Routes.CATEGORIES, R.string.nav_categories, Icons.Filled.Category),
    DrawerEntry(Routes.SEASONS, R.string.nav_seasons, Icons.Filled.ViewModule),
    DrawerEntry(Routes.CHAT, R.string.nav_senpai, Icons.Filled.Person, dividerBefore = true, avatar = true),
    DrawerEntry(Routes.FRIENDS, R.string.nav_friends, Icons.Filled.Forum),
    DrawerEntry(Routes.TOP, R.string.nav_top, Icons.Filled.Star, dividerBefore = true),
    DrawerEntry(Routes.MYLIST, R.string.nav_mylist, Icons.Filled.Bookmarks, dividerBefore = true),
    DrawerEntry(Routes.FAV_ANIME, R.string.nav_fav_anime, Icons.Filled.Favorite),
    DrawerEntry(Routes.FAV_CHARACTERS, R.string.nav_fav_characters, Icons.Filled.People),
    DrawerEntry(Routes.HISTORY, R.string.nav_history, Icons.Filled.History),
    DrawerEntry(Routes.STATS, R.string.nav_stats, Icons.Filled.BarChart),
    DrawerEntry(Routes.LISTS, R.string.nav_lists, Icons.Filled.IosShare),
    DrawerEntry(Routes.POPULAR_CHARACTERS, R.string.nav_popular_characters, Icons.Filled.People, dividerBefore = true),
    DrawerEntry(Routes.RECOMMENDATIONS, R.string.nav_recommendations, Icons.Filled.Recommend),
    DrawerEntry(Routes.SCHEDULE, R.string.nav_schedule, Icons.Filled.CalendarMonth),
    DrawerEntry(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings, dividerBefore = true),
)

/** The drawer in MANGA mode: same look, its own pages (library, favorites, history, downloads are manga-only). */
val MangaDrawerEntries = listOf(
    DrawerEntry(Routes.M_HOME, R.string.manga_home, Icons.Filled.AutoStories),
    DrawerEntry(Routes.M_SEARCH, R.string.manga_browse, Icons.AutoMirrored.Filled.List),
    DrawerEntry(Routes.M_CATEGORIES, R.string.nav_categories, Icons.Filled.Category),
    DrawerEntry(Routes.CHAT, R.string.nav_senpai, Icons.Filled.Person, dividerBefore = true, avatar = true),
    DrawerEntry(Routes.FRIENDS, R.string.nav_friends, Icons.Filled.Forum),
    DrawerEntry(Routes.M_LIBRARY, R.string.nav_mylist, Icons.Filled.Bookmarks, dividerBefore = true),
    DrawerEntry(Routes.M_FAVORITES, R.string.manga_favorites, Icons.Filled.Favorite),
    DrawerEntry(Routes.M_HISTORY, R.string.manga_history, Icons.Filled.History),
    DrawerEntry(Routes.M_DOWNLOADED, R.string.manga_downloaded, Icons.Filled.DownloadDone, dividerBefore = true),
    DrawerEntry(Routes.M_QUEUE, R.string.manga_queue, Icons.Filled.Download),
    DrawerEntry(Routes.LISTS, R.string.nav_lists, Icons.Filled.IosShare, dividerBefore = true),
    DrawerEntry(Routes.STATS, R.string.nav_stats, Icons.Filled.Person, dividerBefore = true),
    DrawerEntry(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

/** Top-bar «أنمي | مانجا» switch, provided to the two home screens. */
val LocalModeToggle = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

@HiltViewModel
class RootViewModel @Inject constructor(
    private val settings: SettingsRepository,
    notifications: NotificationRepository,
    google: GoogleAccountRepository,
    @ApplicationContext context: Context,
) : ViewModel() {
    val profile: StateFlow<Profile> = settings.settings.map { it.profile }.stateIn(viewModelScope, SharingStarted.Eagerly, Profile())
    val unread: StateFlow<Int> = notifications.observeUnreadCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val accountName: StateFlow<String> = google.account.map { it.name }.stateIn(viewModelScope, SharingStarted.Eagerly, google.account.value.name)

    /** v1.3 mode. Read once synchronously-ish at start (DataStore), then followed. */
    private val _mode = MutableStateFlow<AppMode?>(null)
    val mode: StateFlow<AppMode?> = _mode

    fun setMode(m: AppMode) {
        _mode.value = m // instant: the UI switches on this frame; the key is written behind it
        viewModelScope.launch { settings.setMode(m) }
    }

    /**
     * v1.3 item 7: Madyan's video is the splash. It holds about [SPLASH_ONLINE_MS] (2 s), or [SPLASH_OFFLINE_MS]
     * (5 s) when the phone has no internet, then fades into the app. Lives in the ViewModel, so a rotation does not
     * replay it.
     */
    private val _splashDone = MutableStateFlow(false)
    val splashDone: StateFlow<Boolean> = _splashDone

    init {
        viewModelScope.launch { _mode.value = settings.mode.first() }
        if (!SplashConfig.enabled) _splashDone.value = true
        else viewModelScope.launch {
            kotlinx.coroutines.delay(splashHoldMs(isOnline(context)))
            _splashDone.value = true
        }
        viewModelScope.launch {
            val s = settings.settings.first()
            LocaleController.apply()
            runCatching { SyncScheduler.schedule(context, s.notifications) }
        }
    }

    companion object {
        const val SPLASH_ONLINE_MS = 2_000L
        const val SPLASH_OFFLINE_MS = 5_000L

        fun splashHoldMs(online: Boolean) = if (online) SPLASH_ONLINE_MS else SPLASH_OFFLINE_MS

        fun isOnline(context: Context): Boolean = runCatching {
            val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(true)
    }
}

/** «أنمي | مانجا»: a two-part pill; the active part is filled with the accent. */
@Composable
fun ModeToggle(mode: AppMode, onMode: (AppMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.height(30.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(15.dp))
            .border(1.dp, Palette.Accent, androidx.compose.foundation.shape.RoundedCornerShape(15.dp)).testTag("mode_toggle"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(AppMode.ANIME to R.string.mode_anime, AppMode.MANGA to R.string.mode_manga).forEach { (m, label) ->
            val on = m == mode
            Box(
                Modifier.fillMaxHeight().background(if (on) Palette.Accent else Palette.Black)
                    .clickable { if (!on) onMode(m) }.padding(horizontal = 10.dp).testTag("mode_" + m.name.lowercase()),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (on) androidx.compose.ui.graphics.Color.White else Palette.TextSecondary, maxLines = 1)
            }
        }
    }
}

/** v1.4.0 (Madyan): a slightly narrower drawer (1.3.1: 300 dp) with a smaller menu font (1.3.1: bodyLarge 16 sp). */
val DRAWER_WIDTH = 280.dp
val DRAWER_FONT = 14.5.sp

@Composable
fun DrawerContent(
    current: String?,
    profile: Profile,
    onSelect: (String) -> Unit,
    unread: Int = 0,
    entries: List<DrawerEntry> = DrawerEntries,
    accountName: String = "",
) {
    // v1.3 item 3: the drawer is pure black (#000) like the lists, not the grey surface.
    ModalDrawerSheet(drawerContainerColor = Palette.Black, modifier = Modifier.width(DRAWER_WIDTH).testTag("drawer_sheet")) {
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().clickable { onSelect(Routes.STATS) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val m = Modifier.size(56.dp).clip(CircleShape).border(2.dp, Palette.DrawerAccent, CircleShape)
                if (profile.avatarPath != null) AsyncImage(File(profile.avatarPath), null, m, contentScale = ContentScale.Crop)
                else Image(painterResource(R.drawable.as_logo), null, m.background(Palette.Black), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(12.dp))
                // v1.3 item 6: only the account name under the avatar (no «Your Anime. Your Journey.»).
                Text(
                    profile.name.ifBlank { accountName }.ifBlank { stringResource(R.string.profile_default_name) },
                    style = MaterialTheme.typography.titleMedium.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content),
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("drawer_name"),
                )
                // v1.0 bug: the bell opened the schedule. It opens the Notifications history now.
                IconButton(onClick = { onSelect(Routes.NOTIFICATIONS) }, modifier = Modifier.testTag("drawer_bell")) {
                    BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 99) "99+" else unread.toString()) } }) {
                        // v1.3.1 (Madyan): a thin WHITE outline bell — no fill, no colour, same 24 dp as the other icons.
                        Icon(Icons.Outlined.Notifications, stringResource(R.string.cd_notifications), tint = Color.White, modifier = Modifier.testTag("drawer_bell_icon"))
                    }
                }
            }
            entries.forEach { e ->
                if (e.dividerBefore) HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = Palette.Divider)
                NavigationDrawerItem(
                    // v1.4.0 (Madyan): smaller menu font
                    label = { Text(stringResource(e.label), style = MaterialTheme.typography.bodyLarge.copy(fontSize = DRAWER_FONT)) },
                    icon = { if (e.avatar) com.animeshadow.app.chat.SenpaiAvatar(26) else Icon(e.icon, null) },
                    selected = current == e.route,
                    onClick = { onSelect(e.route) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = Palette.DrawerAccentSoft,
                        selectedIconColor = Palette.DrawerAccent,
                        selectedTextColor = Palette.Text,
                        unselectedContainerColor = Palette.Black,
                        unselectedIconColor = Palette.RefIcon,
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
fun AppRoot(openAnimeId: Int? = null, onOpened: () -> Unit = {}, openDownloads: Boolean = false, onDownloadsOpened: () -> Unit = {}, vm: RootViewModel = hiltViewModel()) {
    val animeNav = rememberNavController()
    val mangaNav = rememberNavController()
    val holder = rememberSaveableStateHolder()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val unread by vm.unread.collectAsStateWithLifecycle()
    val accountName by vm.accountName.collectAsStateWithLifecycle()
    val storedMode by vm.mode.collectAsStateWithLifecycle()
    val mode = storedMode ?: AppMode.ANIME
    val nav = if (mode == AppMode.ANIME) animeNav else mangaNav
    val entries = if (mode == AppMode.ANIME) DrawerEntries else MangaDrawerEntries
    val back by nav.currentBackStackEntryAsState()
    val current = back?.destination?.route
    val topLevel = entries.any { it.route == current }

    LaunchedEffect(openAnimeId) {
        if (openAnimeId != null) {
            vm.setMode(AppMode.ANIME)
            animeNav.navigate(Routes.anime(openAnimeId))
            onOpened()
        }
    }
    LaunchedEffect(openDownloads, storedMode) {
        if (openDownloads && storedMode != null) {
            vm.setMode(AppMode.MANGA)
            mangaNav.navigate(Routes.M_QUEUE) { launchSingleTop = true }
            onDownloadsOpened()
        }
    }

    // v1.4.0: a chat card opens its page; a card of the other kind switches mode first, then opens there.
    var pendingCard by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.animeshadow.app.chat.ChatCard?>(null) }
    val openCard: (com.animeshadow.app.chat.ChatCard) -> Unit = { c ->
        val want = if (c.isManga) AppMode.MANGA else AppMode.ANIME
        if (want == mode) runCatching { nav.navigate(if (c.isManga) Routes.mTitle(c.id) else Routes.anime(c.id)) }
        else { pendingCard = c; vm.setMode(want) }
    }
    LaunchedEffect(mode, pendingCard) {
        val c = pendingCard ?: return@LaunchedEffect
        if ((if (c.isManga) AppMode.MANGA else AppMode.ANIME) != mode) return@LaunchedEffect
        androidx.compose.runtime.withFrameNanos { }
        runCatching { nav.navigate(if (c.isManga) Routes.mTitle(c.id) else Routes.anime(c.id)) }
        pendingCard = null
    }
    // v1.4.0 AdMob: at most one interstitial every few screens (never splash / login / reader / chat), see Interstitials.
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    LaunchedEffect(current) { if (current != null) com.animeshadow.app.ui.ads.Interstitials.onScreen(activity, current) }
    val openMenu: () -> Unit = { scope.launch { drawer.open() } }
    // v1.2 bug fix: Back with the drawer open closed the screen (or left the app) instead of closing the drawer.
    androidx.activity.compose.BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    val splashDone by vm.splashDone.collectAsStateWithLifecycle()
    val toggle: @Composable () -> Unit = { ModeToggle(mode, { m -> vm.setMode(m) }, Modifier.padding(end = 4.dp)) }
    Box(Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawer,
            gesturesEnabled = topLevel,
            drawerContent = {
                DrawerContent(current, profile, { route ->
                    scope.launch { drawer.close() }
                    val home = if (mode == AppMode.ANIME) Routes.LATEST else Routes.M_HOME
                    if (route != current) nav.navigate(route) {
                        popUpTo(home) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }, unread, entries, accountName)
            },
        ) {
            // Each mode keeps its own back stack, screens and scroll positions; switching only changes which one shows.
            // (Until the stored mode is read — a few ms, under the splash — the anime side shows.)
            holder.SaveableStateProvider(mode.name) {
                CompositionLocalProvider(LocalOpenChatCard provides openCard) {
                when (mode) {
                    AppMode.ANIME -> AppNavHost(animeNav, openMenu, drawer, toggle)
                    AppMode.MANGA -> MangaNavGraph(mangaNav, openMenu, toggle)
                }
                }
            }
        }
        // v1.5.0: Madyan Panel maintenance (full screen) + broadcast card, above the app and under nothing but the splash.
        com.animeshadow.app.ui.status.AppStatusLayer(ready = splashDone)
        ShadowSplash(visible = !splashDone)
    }
}

/** Robolectric app tests switch the start-up animation off (their clock does not run on its own). */
object SplashConfig {
    @Volatile var enabled: Boolean = true
}

/** v1.3: full-screen black with Madyan's video; fades out into the UI. */
@Composable
fun ShadowSplash(visible: Boolean) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.EnterTransition.None,
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(durationMillis = 450)),
    ) {
        Box(
            Modifier.fillMaxSize().background(Palette.Black)
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                .testTag("shadow_splash"),
            contentAlignment = Alignment.Center,
        ) {
            // v1.4.0: the small animated Shadow icon, centred (replaces the full-width video).
            Box(Modifier.testTag("shadow_icon")) { com.animeshadow.app.ui.common.ShadowLoop(Modifier.width(150.dp)) }
        }
    }
}

/** Screens that exist in both modes (account, settings, notifications). */
fun androidx.navigation.NavGraphBuilder.sharedScreens(nav: NavHostController, openMenu: () -> Unit, manga: Boolean = false, openAnime: (Int) -> Unit) {
    composable(Routes.STATS) { ProfileScreen(openMenu, onEdit = { nav.navigate(Routes.EDIT_PROFILE) }, onLogin = { nav.navigate(Routes.LOGIN) }, manga = manga) }
    composable(Routes.EDIT_PROFILE) { EditProfileScreen({ nav.popBackStack() }) }
    composable(Routes.LOGIN) { LoginScreen({ nav.popBackStack() }) }
    composable(Routes.SETTINGS) { SettingsScreen(openMenu) }
    composable(Routes.NOTIFICATIONS) { NotificationsScreen({ nav.popBackStack() }, openAnime) }
    // v1.4.0 «شادو سينباي»
    composable(Routes.CHAT) {
        com.animeshadow.app.chat.ChatHomeScreen(openMenu, onOpen = { nav.navigate(Routes.chatConv(it)) }, onNew = { nav.navigate(Routes.chatConv("new")) }, onSettings = { nav.navigate(Routes.CHAT_SETTINGS) })
    }
    composable(Routes.CHAT_CONV, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
        com.animeshadow.app.chat.ChatScreen({ nav.popBackStack() }, onOpenCard = LocalOpenChatCard.current)
    }
    composable(Routes.CHAT_SETTINGS) { com.animeshadow.app.chat.ChatSettingsScreen({ nav.popBackStack() }) }
    // v1.5.0 friends, chat between users, list sharing
    composable(Routes.FRIENDS) {
        com.animeshadow.app.social.FriendsScreen(openMenu, onLogin = { nav.navigate(Routes.LOGIN) }, onOpen = { nav.navigate(Routes.thread(it)) }, onLists = { nav.navigate(Routes.LISTS_BACK) })
    }
    composable(Routes.THREAD, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
        com.animeshadow.app.social.ThreadScreen({ nav.popBackStack() })
    }
    composable(Routes.LISTS) { com.animeshadow.app.social.ListsScreen(onBack = { nav.popBackStack() }, onMenu = openMenu) }
    composable(Routes.LISTS_BACK) { com.animeshadow.app.social.ListsScreen(onBack = { nav.popBackStack() }) }
}

/** v1.4.0: opens a chat card's anime/manga page, switching mode first when the card is of the other kind. */
val LocalOpenChatCard = staticCompositionLocalOf<(com.animeshadow.app.chat.ChatCard) -> Unit> { {} }

@Composable
private fun AppNavHost(nav: NavHostController, openMenu: () -> Unit, @Suppress("UNUSED_PARAMETER") drawer: DrawerState, toggle: @Composable () -> Unit) {
    val open: (Int) -> Unit = { nav.navigate(Routes.anime(it)) }
    val openCharacter: (Int) -> Unit = { nav.navigate(Routes.character(it)) }
    val openStaff: (Int) -> Unit = { nav.navigate(Routes.staff(it)) }
    val search: () -> Unit = { nav.navigate(Routes.SEARCH) }
    NavHost(nav, startDestination = Routes.LATEST) {
        composable(Routes.LATEST) { CompositionLocalProvider(LocalModeToggle provides toggle) { HomeScreen(openMenu, search, open) } }
        composable(Routes.BROWSE) { BrowseScreen(autoFocus = false, onMenu = openMenu, onBack = null, onOpen = open) }
        composable(Routes.CATEGORIES) {
            com.animeshadow.app.ui.browse.CategoriesScreen(openMenu, null, manga = false, onGenre = { nav.navigate(Routes.genre(it)) }, onTag = { nav.navigate(Routes.tag(it)) })
        }
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
        sharedScreens(nav, openMenu, openAnime = open)
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
