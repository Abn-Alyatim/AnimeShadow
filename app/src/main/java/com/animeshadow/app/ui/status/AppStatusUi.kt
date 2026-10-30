package com.animeshadow.app.ui.status

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.animeshadow.app.R
import com.animeshadow.app.social.AppStatus
import com.animeshadow.app.social.AppStatusRepository
import com.animeshadow.app.social.Broadcast
import com.animeshadow.app.social.MaintenanceBus
import com.animeshadow.app.ui.common.ShadowLoop
import com.animeshadow.app.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * v1.5.0: Madyan Panel «إيقاف مؤقت» → full-screen maintenance (checked on launch, on resume, when any server call
 * answers MAINTENANCE, and every 30 s while it lasts; it lifts by itself). «رسالة جماعية» → one card per message.
 */
@HiltViewModel
class StatusViewModel @Inject constructor(private val repo: AppStatusRepository, private val social: com.animeshadow.app.social.SocialRepository) : ViewModel() {
    val status: StateFlow<AppStatus?> = repo.status
    val checking: StateFlow<Boolean> = repo.checking
    private val _broadcast = MutableStateFlow<Broadcast?>(null)
    val broadcast: StateFlow<Broadcast?> = _broadcast
    private var loop: Job? = null

    init {
        check()
        viewModelScope.launch { MaintenanceBus.signals.collect { check() } }
    }

    fun check() = viewModelScope.launch {
        val s = repo.refresh()
        _broadcast.value = repo.unseenBroadcast(s)
        if (s?.maintenance?.on != true) runCatching { social.refreshMe() } // keeps Pro / username fresh
        if (s?.maintenance?.on == true) startLoop()
    }

    private fun startLoop() {
        if (loop?.isActive == true) return
        loop = viewModelScope.launch {
            while (repo.status.value?.maintenance?.on == true) {
                delay(RECHECK_MS)
                repo.refresh()
            }
            _broadcast.value = repo.unseenBroadcast(repo.status.value)
        }
    }

    fun dismissBroadcast() {
        _broadcast.value?.let { repo.markSeen(it) }
        _broadcast.value = null
    }

    companion object { const val RECHECK_MS = 30_000L }
}

/** Formats the panel's ISO «until» as «الجمعة 18:30» in the phone's zone; null when absent/unreadable. */
object MaintenanceText {
    private val days = mapOf(1 to "الاثنين", 2 to "الثلاثاء", 3 to "الأربعاء", 4 to "الخميس", 5 to "الجمعة", 6 to "السبت", 7 to "الأحد")
    fun until(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String? = iso?.let {
        runCatching {
            val t = OffsetDateTime.parse(it).atZoneSameInstant(zone)
            days[t.dayOfWeek.value] + " " + t.format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
        }.getOrNull()
    }
}

/** Overlay for the whole app: maintenance screen above everything, broadcast card when the app is usable. */
@Composable
fun AppStatusLayer(ready: Boolean, vm: StatusViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val status by vm.status.collectAsStateWithLifecycle()
    val checking by vm.checking.collectAsStateWithLifecycle()
    val broadcast by vm.broadcast.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.check() }
    val m = status?.maintenance
    AnimatedVisibility(visible = m?.on == true, enter = fadeIn(tween(350)), exit = fadeOut(tween(450))) {
        MaintenanceScreen(m?.message, MaintenanceText.until(m?.until), checking, onRetry = { vm.check() })
    }
    val b = broadcast
    if (ready && m?.on != true && b != null) BroadcastCard(b, onDismiss = vm::dismissBroadcast)
}

@Composable
fun MaintenanceScreen(message: String?, until: String?, checking: Boolean, onRetry: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "maint")
    val glow by pulse.animateFloat(0.35f, 0.9f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    val ring by pulse.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ring")
    val dots by pulse.animateFloat(0f, 3f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "dots")
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Palette.Black, Color(0xFF0B0B14), Palette.Black)))
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .testTag("maintenance_screen"),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.widthIn(max = 420.dp).padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(176.dp).scale(ring).alpha(glow).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Palette.Accent.copy(alpha = 0.45f), Color.Transparent))),
                )
                Box(Modifier.testTag("shadow_icon")) { ShadowLoop(Modifier.width(128.dp)) }
            }
            Spacer(Modifier.height(26.dp))
            Text(
                stringResource(R.string.maintenance_title), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp,
                textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Rtl),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                message?.takeIf { it.isNotBlank() } ?: stringResource(R.string.maintenance_default),
                color = Palette.TextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl, lineHeight = 24.sp),
                modifier = Modifier.testTag("maintenance_message"),
            )
            if (until != null) {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.maintenance_until, until), color = Palette.Blue, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(30.dp))
            Row(
                Modifier.clip(RoundedCornerShape(26.dp)).background(Palette.Accent).clickable(enabled = !checking, onClick = onRetry)
                    .padding(horizontal = 26.dp, vertical = 12.dp).testTag("maintenance_retry"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (checking) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(Icons.Filled.Refresh, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.maintenance_retry), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.maintenance_auto) + ".".repeat(dots.toInt() + 1).padEnd(3, ' '),
                color = Palette.TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun BroadcastCard(b: Broadcast, onDismiss: () -> Unit) {
    var shown by remember(b.id) { mutableStateOf(false) }
    LaunchedEffect(b.id) { shown = true }
    Dialog(onDismissRequest = onDismiss) {
        AnimatedVisibility(visible = shown, enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.92f) + slideInVertically { it / 12 }) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.Card)
                    .border(1.dp, Brush.linearGradient(listOf(Palette.Accent, Palette.Blue)), RoundedCornerShape(22.dp))
                    .padding(22.dp).testTag("broadcast_card"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(58.dp).clip(CircleShape).background(Palette.AccentSoft), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Campaign, null, tint = Palette.Accent, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.broadcast_from), color = Palette.TextMuted, fontSize = 12.sp)
                if (b.title.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(b.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Rtl))
                }
                if (b.body.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(b.body, color = Palette.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Rtl, lineHeight = 22.sp),
                        modifier = Modifier.testTag("broadcast_body"))
                }
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.Accent).clickable(onClick = onDismiss)
                        .padding(vertical = 11.dp).testTag("broadcast_ok"),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.broadcast_ok), color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
