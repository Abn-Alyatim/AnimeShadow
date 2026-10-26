package com.animeshadow.app.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.animeshadow.app.R
import com.animeshadow.app.data.NotificationRepository
import com.animeshadow.app.data.local.NotificationEntity
import com.animeshadow.app.ui.common.AppTopBar
import com.animeshadow.app.ui.common.EmptyState
import com.animeshadow.app.ui.common.Poster
import com.animeshadow.app.ui.common.latinDateTimeFormat
import com.animeshadow.app.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

/** v1.1 item 8: the drawer bell's screen — history of new-episode / reminder alerts, read/unread, clear all. */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repo: NotificationRepository,
) : ViewModel() {
    val items: StateFlow<List<NotificationEntity>?> = repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun open(item: NotificationEntity) = viewModelScope.launch { repo.markRead(item.id) }
    fun markAllRead() = viewModelScope.launch { repo.markAllRead() }
    fun clearAll() = viewModelScope.launch { repo.clear() }
}

@Composable
fun NotificationsContent(
    items: List<NotificationEntity>?,
    onBack: () -> Unit,
    onOpen: (NotificationEntity) -> Unit,
    onMarkAllRead: () -> Unit,
    onClearAll: () -> Unit,
) {
    Scaffold(
        containerColor = Palette.Black,
        topBar = {
            AppTopBar(stringResource(R.string.notifications_title), onBack = onBack) {
                if (!items.isNullOrEmpty()) {
                    IconButton(onClick = onMarkAllRead) { Icon(Icons.Filled.DoneAll, stringResource(R.string.mark_all_read), tint = Palette.RefIcon) }
                    IconButton(onClick = onClearAll, modifier = Modifier.testTag("clear_all")) {
                        Icon(Icons.Filled.DeleteSweep, stringResource(R.string.clear_all), tint = Palette.RefIcon)
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().testTag("notifications_screen")) {
            when {
                items == null -> Unit
                items.isEmpty() -> EmptyState(stringResource(R.string.notifications_empty))
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(items, key = { it.id }) { n ->
                        NotificationRow(n) { onOpen(n) }
                        HorizontalDivider(color = Palette.Divider, modifier = Modifier.padding(start = 84.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: NotificationEntity, onClick: () -> Unit) {
    val fmt = latinDateTimeFormat()
    Row(
        Modifier.fillMaxWidth().background(if (n.read) Palette.Black else Palette.Card).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp).testTag("notif_${n.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(n.image, Modifier.width(52.dp).aspectRatio(0.7f).clip(RoundedCornerShape(6.dp)), n.title)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                n.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (n.read) FontWeight.Normal else FontWeight.Bold, color = if (n.read) Palette.TextSecondary else Palette.RefText,
            )
            Text(
                stringResource(if (n.kind == NotificationRepository.KIND_REMINDER) R.string.notif_item_reminder else R.string.notif_item_new, n.episode.toString()),
                style = MaterialTheme.typography.bodyMedium, color = if (n.read) Palette.TextMuted else Palette.Blue, textAlign = TextAlign.Left,
            )
            Text(fmt.format(Date(n.createdAt)), style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        }
        if (!n.read) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(10.dp).clip(CircleShape).background(Palette.Blue).testTag("unread_dot"))
        }
    }
}

@Composable
fun NotificationsScreen(onBack: () -> Unit, onOpenAnime: (Int) -> Unit, vm: NotificationsViewModel = hiltViewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    NotificationsContent(
        items, onBack,
        onOpen = { vm.open(it); onOpenAnime(it.animeId) },
        onMarkAllRead = vm::markAllRead,
        onClearAll = vm::clearAll,
    )
}
