package com.animeshadow.app.manga.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.animeshadow.app.R
import com.animeshadow.app.manga.data.DownloadState
import com.animeshadow.app.manga.data.DownloadWithManga
import com.animeshadow.app.ui.common.AppTopBar
import com.animeshadow.app.ui.common.EmptyState
import com.animeshadow.app.ui.theme.Palette

class QueueActions(
    val onBack: () -> Unit = {},
    val onPauseAll: () -> Unit = {},
    val onResumeAll: () -> Unit = {},
    val onCancelAll: () -> Unit = {},
    val onPause: (String) -> Unit = {},
    val onResume: (String) -> Unit = {},
    val onCancel: (String) -> Unit = {},
    val onTop: (String) -> Unit = {},
    val onReorder: (List<String>) -> Unit = {},
)

private val RowHeight = 78.dp

/**
 * «قائمة التنزيل» (ref 4): pause-all and cancel-all in the top bar; each row = drag handle, title + «3/17», chapter
 * number, progress bar, ⋮ (pause / resume, first in queue, cancel). Drag the handle to change the order.
 */
@Composable
fun DownloadQueueContent(rows: List<DownloadWithManga>, actions: QueueActions) {
    val anyActive = rows.any { it.download.state == DownloadState.QUEUED || it.download.state == DownloadState.RUNNING }
    var confirmCancel by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Palette.Black,
        topBar = {
            AppTopBar(stringResource(R.string.manga_queue), onBack = actions.onBack) {
                if (rows.isNotEmpty()) {
                    IconButton(onClick = if (anyActive) actions.onPauseAll else actions.onResumeAll, modifier = Modifier.testTag("queue_pause_all")) {
                        Icon(if (anyActive) Icons.Filled.Pause else Icons.Filled.PlayArrow, stringResource(if (anyActive) R.string.manga_pause_all else R.string.manga_resume_all), tint = Palette.RefIcon)
                    }
                    IconButton(onClick = { confirmCancel = true }, modifier = Modifier.testTag("queue_cancel_all")) {
                        Icon(Icons.Filled.Cancel, stringResource(R.string.manga_cancel_all), tint = Palette.RefIcon)
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (rows.isEmpty()) EmptyState(stringResource(R.string.manga_queue_empty))
            else ReorderableQueue(rows, actions)
        }
    }
    if (confirmCancel) AlertDialog(
        onDismissRequest = { confirmCancel = false }, containerColor = Palette.Card,
        text = { Text(stringResource(R.string.manga_cancel_all_confirm)) },
        confirmButton = { TextButton(onClick = { confirmCancel = false; actions.onCancelAll() }) { Text(stringResource(R.string.manga_cancel_all), color = Palette.Dropped) } },
        dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ReorderableQueue(rows: List<DownloadWithManga>, actions: QueueActions) {
    val order = remember { mutableStateListOf<String>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val byKey = rows.associateBy { it.download.chapterKey }
    LaunchedEffect(rows.map { it.download.chapterKey }) {
        if (dragging == null) { order.clear(); order.addAll(rows.map { it.download.chapterKey }) }
    }
    val rowPx = with(LocalDensity.current) { RowHeight.toPx() }
    LazyColumn(Modifier.fillMaxSize().testTag("queue_list")) {
        itemsIndexed(order.filter { it in byKey }, key = { _, k -> k }) { _, key ->
            val r = byKey[key] ?: return@itemsIndexed
            val isDragged = dragging == key
            QueueRow(
                r, actions,
                modifier = Modifier.zIndex(if (isDragged) 1f else 0f).graphicsLayer { translationY = if (isDragged) offset else 0f },
                handle = Modifier.pointerInput(key) {
                    detectDragGestures(
                        onDragStart = { dragging = key; offset = 0f },
                        onDragEnd = { dragging = null; offset = 0f; actions.onReorder(order.toList()) },
                        onDragCancel = { dragging = null; offset = 0f },
                    ) { change, amount ->
                        change.consume()
                        offset += amount.y
                        val i = order.indexOf(key)
                        if (offset > rowPx / 2 && i < order.lastIndex) { order.removeAt(i); order.add(i + 1, key); offset -= rowPx }
                        else if (offset < -rowPx / 2 && i > 0) { order.removeAt(i); order.add(i - 1, key); offset += rowPx }
                    }
                },
            )
        }
    }
}

@Composable
private fun QueueRow(r: DownloadWithManga, actions: QueueActions, modifier: Modifier, handle: Modifier) {
    val d = r.download
    var menu by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().height(RowHeight).background(Palette.Black).padding(start = 4.dp, end = 0.dp).testTag("queue_row_${d.chapterNumber}"), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.DragHandle, stringResource(R.string.cd_reorder), tint = Palette.RefIcon, modifier = handle.padding(10.dp).size(24.dp).testTag("drag_${d.chapterNumber}"))
        Column(Modifier.weight(1f).padding(end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    r.manga?.displayTitle.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content), modifier = Modifier.weight(1f),
                )
                Text("\u2066${d.donePages}/${d.totalPages.takeIf { it > 0 } ?: "?"}\u2069", style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d.chapterNumber, style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
                val stateText = when (d.state) {
                    DownloadState.PAUSED -> stringResource(R.string.manga_paused)
                    DownloadState.FAILED -> stringResource(R.string.manga_waiting_source)
                    DownloadState.QUEUED -> if (d.error == "network") stringResource(R.string.manga_waiting_network) else null
                    else -> null
                }
                if (stateText != null) Text("  •  $stateText", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            }
            Spacer(Modifier.height(5.dp))
            LinearProgressIndicator(
                progress = { d.progress }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = if (d.state == DownloadState.PAUSED) Palette.TextMuted else Palette.Accent, trackColor = Palette.CardHigh,
            )
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.testTag("queue_more_${d.chapterNumber}")) { Icon(Icons.Filled.MoreVert, stringResource(R.string.cd_more), tint = Palette.RefIcon) }
            DropdownMenu(menu, { menu = false }, containerColor = Palette.Card) {
                if (d.state == DownloadState.PAUSED || d.state == DownloadState.FAILED) DropdownMenuItem(text = { Text(stringResource(R.string.manga_resume)) }, onClick = { menu = false; actions.onResume(d.chapterKey) })
                else DropdownMenuItem(text = { Text(stringResource(R.string.manga_pause)) }, onClick = { menu = false; actions.onPause(d.chapterKey) })
                DropdownMenuItem(text = { Text(stringResource(R.string.manga_move_top)) }, onClick = { menu = false; actions.onTop(d.chapterKey) })
                DropdownMenuItem(text = { Text(stringResource(R.string.cancel), color = Palette.Dropped) }, onClick = { menu = false; actions.onCancel(d.chapterKey) })
            }
        }
    }
}

@Composable
fun DownloadQueueScreen(onBack: () -> Unit, vm: DownloadQueueViewModel = hiltViewModel()) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    DownloadQueueContent(
        rows,
        QueueActions(
            onBack = onBack, onPauseAll = { vm.pauseAll() }, onResumeAll = { vm.resumeAll() }, onCancelAll = { vm.cancelAll() },
            onPause = { vm.pause(it) }, onResume = { vm.resume(it) }, onCancel = { vm.cancel(it) }, onTop = { vm.toTop(it) }, onReorder = { vm.reorder(it) },
        ),
    )
}

@Suppress("unused")
private val unusedArrangement = Arrangement.Start
