package com.animeshadow.app.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.animeshadow.app.R
import com.animeshadow.app.data.settings.AppMode
import com.animeshadow.app.data.settings.ChatPrefs
import com.animeshadow.app.data.settings.ExtraPrefs
import com.animeshadow.app.data.settings.SettingsRepository
import com.animeshadow.app.ui.common.AppTopBar
import com.animeshadow.app.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private val Bubble = Color(0xFF15131D)
private val BubbleUser = Color(0xFF2A2340)
private val Field = Color(0xFF111116)

// ======================= view models =======================

data class ChatHomeUi(val conversations: List<ChatConversation> = emptyList(), val status: WireStatus? = null)

@HiltViewModel
class ChatHomeViewModel @Inject constructor(private val repo: ChatRepository) : ViewModel() {
    private val status = MutableStateFlow<WireStatus?>(null)
    val conversations = repo.store.conversations
    val statusFlow: StateFlow<WireStatus?> = status.asStateFlow()
    init {
        viewModelScope.launch { repo.store.load() }
        refresh()
    }
    fun refresh() { viewModelScope.launch { status.value = repo.status() } }
    fun delete(id: String) { viewModelScope.launch { repo.store.delete(id) } }
}

data class ChatUi(
    val conversation: ChatConversation = ChatConversation(),
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val image: Pair<File, String>? = null,
    val sending: Boolean = false,
    val ready: Boolean? = null,
    val remaining: Int? = null,
    val limit: Int? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: ChatRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val convId: String? = savedState.get<String>("id")?.takeIf { it.isNotBlank() && it != "new" }
    private val _ui = MutableStateFlow(ChatUi())
    val ui: StateFlow<ChatUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            repo.store.load()
            val existing = convId?.let { id -> repo.store.conversations.value.firstOrNull { it.id == id } }
            if (existing != null) _ui.update { it.copy(conversation = existing, messages = repo.store.messages(existing.id)) }
        }
        viewModelScope.launch { repo.status()?.let { s -> _ui.update { it.copy(ready = s.ready, remaining = s.remaining, limit = s.limit) } } }
    }

    fun setInput(t: String) = _ui.update { it.copy(input = t.take(2000)) }
    fun setImage(img: Pair<File, String>?) { _ui.value.image?.first?.takeIf { img == null }?.delete(); _ui.update { it.copy(image = img) } }

    fun send(textOverride: String? = null) {
        val s = _ui.value
        val text = (textOverride ?: s.input).trim()
        if (s.sending || (text.isEmpty() && s.image == null)) return
        _ui.update { it.copy(sending = true, input = "", messages = it.messages + ChatMessage(role = "user", text = text, imagePath = it.image?.first?.absolutePath), image = null) }
        viewModelScope.launch {
            val mode = settings.mode.first()
            val (conv, msgs, result) = repo.send(s.conversation, s.messages, text, s.image, mode)
            _ui.update {
                it.copy(
                    conversation = conv, messages = msgs, sending = false,
                    ready = if (result is ChatResult.NotReady) false else if (result is ChatResult.Ok) true else it.ready,
                    remaining = (result as? ChatResult.Ok)?.reply?.remaining ?: if (result is ChatResult.Limit) 0 else it.remaining,
                )
            }
        }
    }
}

@HiltViewModel
class ChatSettingsViewModel @Inject constructor(private val prefs: ExtraPrefs) : ViewModel() {
    val prefsFlow: StateFlow<ChatPrefs?> = prefs.chat.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    fun save(p: ChatPrefs, done: () -> Unit) { viewModelScope.launch { prefs.setChat(p); done() } }
}

// ======================= shared bits =======================

@Composable
fun SenpaiAvatar(size: Int, modifier: Modifier = Modifier) {
    Image(
        painterResource(R.drawable.shadow_senpai), ASSISTANT_NAME,
        modifier.size(size.dp).clip(CircleShape).border(1.dp, Palette.Accent.copy(alpha = 0.55f), CircleShape).testTag("senpai_avatar"),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun RtlText(text: String, modifier: Modifier = Modifier, color: Color = Palette.Text, size: Int = 14, weight: FontWeight? = null, maxLines: Int = Int.MAX_VALUE, align: TextAlign = TextAlign.Start) {
    Text(
        text, modifier, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = size.sp, lineHeight = (size + 7).sp, textDirection = TextDirection.ContentOrRtl, fontWeight = weight),
    )
}

private fun dayLabel(t: Long): String = SimpleDateFormat("d/M · HH:mm", Locale.US).format(Date(t))

// ======================= history list =======================

@Composable
fun ChatHomeScreen(onMenu: () -> Unit, onOpen: (String) -> Unit, onNew: () -> Unit, onSettings: () -> Unit, vm: ChatHomeViewModel = hiltViewModel()) {
    val list by vm.conversations.collectAsStateWithLifecycle()
    val status by vm.statusFlow.collectAsStateWithLifecycle()
    ChatHomeContent(ChatHomeUi(list, status), onMenu, onOpen, onNew, onSettings, vm::delete)
}

@Composable
fun ChatHomeContent(ui: ChatHomeUi, onMenu: () -> Unit, onOpen: (String) -> Unit, onNew: () -> Unit, onSettings: () -> Unit, onDelete: (String) -> Unit) {
    var confirm by remember { mutableStateOf<ChatConversation?>(null) }
    Scaffold(
        containerColor = Palette.Black,
        topBar = { AppTopBar(ASSISTANT_NAME, onMenu = onMenu) { IconButton(onClick = onSettings, modifier = Modifier.testTag("chat_settings_btn")) { Icon(Icons.Filled.Tune, "تخصيص", tint = Palette.RefIcon) } } },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("chat_home"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Card).border(1.dp, Palette.Divider, RoundedCornerShape(18.dp)).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    SenpaiAvatar(84)
                    Spacer(Modifier.height(10.dp))
                    Text(ASSISTANT_NAME, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), color = Palette.Text)
                    Spacer(Modifier.height(4.dp))
                    RtlText("مساعدك في الأنمي والمانجا: يرشّح لك من قائمتك، يقيّم، ويعرف الأنمي من البوستر.", color = Palette.TextSecondary, size = 13, align = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    val s = ui.status
                    val line = when {
                        s == null -> "حد الرسائل: $DAILY_LIMIT رسالة في اليوم"
                        !s.ready -> ChatRepository.NOT_READY
                        else -> "باقي لك اليوم ${s.remaining} من ${s.limit} رسالة"
                    }
                    Text(line, color = if (s != null && !s.ready) Palette.OnHold else Palette.Plan, style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("chat_quota"))
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onNew, colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("chat_new")) {
                        Icon(Icons.Filled.Add, null, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("محادثة جديدة", color = Color.White)
                    }
                }
            }
            if (ui.conversations.isNotEmpty()) item { Text("المحادثات", style = MaterialTheme.typography.titleSmall, color = Palette.TextSecondary, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = TextAlign.End) }
            items(ui.conversations, key = { it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Surface).clickable { onOpen(c.id) }.padding(start = 4.dp, end = 14.dp, top = 10.dp, bottom = 10.dp).testTag("chat_item"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { confirm = c }, modifier = Modifier.testTag("chat_delete")) { Icon(Icons.Outlined.DeleteOutline, "حذف", tint = Palette.TextMuted) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        RtlText(c.title.ifBlank { c.preview.take(28).ifBlank { "محادثة" } }, weight = FontWeight.Bold, maxLines = 1, size = 15)
                        RtlText(c.preview, color = Palette.TextMuted, size = 12, maxLines = 1)
                        Text(dayLabel(c.updatedAt), color = Palette.TextMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Palette.Card,
            title = { RtlText("حذف المحادثة؟", weight = FontWeight.Bold, size = 17) },
            text = { RtlText("«${c.title.ifBlank { "محادثة" }}» بتنحذف من جهازك نهائياً.", color = Palette.TextSecondary) },
            confirmButton = { TextButton(onClick = { onDelete(c.id); confirm = null }, modifier = Modifier.testTag("chat_delete_confirm")) { Text("حذف", color = Palette.Dropped) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("إلغاء", color = Palette.TextSecondary) } },
        )
    }
}

// ======================= conversation =======================

@Composable
fun ChatScreen(onBack: () -> Unit, onOpenCard: (ChatCard) -> Unit, vm: ChatViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) vm.setImage(ChatImages.prepare(ctx, uri, File(ctx.filesDir, "chat/img")))
    }
    ChatContent(ui, onBack, onOpenCard, vm::setInput, { vm.send() }, { vm.send(it) },
        onPickImage = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onRemoveImage = { vm.setImage(null) })
}

private val SUGGESTIONS = listOf("رشّح لي أنمي يشبه ذوقي", "قيّم لي قائمتي بصراحة", "أفضل مانجا مكتملة؟", "وش أشاهد بعد Attack on Titan؟")

@Composable
fun ChatContent(
    ui: ChatUi, onBack: () -> Unit, onOpenCard: (ChatCard) -> Unit, onInput: (String) -> Unit, onSend: () -> Unit, onSuggestion: (String) -> Unit,
    onPickImage: () -> Unit, onRemoveImage: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(ui.messages.size, ui.sending) { if (ui.messages.isNotEmpty()) listState.animateScrollToItem(ui.messages.size + (if (ui.sending) 1 else 0)) }
    Scaffold(
        containerColor = Palette.Black,
        topBar = { AppTopBar(ui.conversation.title.ifBlank { ASSISTANT_NAME }, onBack = onBack) { SenpaiAvatar(32, Modifier.padding(end = 12.dp)) } },
        bottomBar = {
            ChatInputBar(ui, onInput, onSend, onPickImage, onRemoveImage)
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("chat_list"), state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                if (ui.ready == false) NotReadyCard() else if (ui.messages.isEmpty()) Welcome(onSuggestion)
            }
            items(ui.messages, key = { it.id }) { m -> MessageRow(m, onOpenCard) }
            if (ui.sending) item { Typing() }
        }
    }
}

@Composable
private fun NotReadyCard() {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp).testTag("chat_not_ready"), horizontalAlignment = Alignment.CenterHorizontally) {
        SenpaiAvatar(96)
        Spacer(Modifier.height(14.dp))
        Text(ChatRepository.NOT_READY, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), color = Palette.Text)
        Spacer(Modifier.height(6.dp))
        RtlText("سيعمل شادو سينباي هنا فور تفعيله من الخادم، دون تحديث للتطبيق.", color = Palette.TextSecondary, size = 13, align = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
    }
}

@Composable
private fun Welcome(onSuggestion: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SenpaiAvatar(88)
        Spacer(Modifier.height(10.dp))
        RtlText("هلا! أنا $ASSISTANT_NAME 👋\nاسألني عن أي أنمي أو مانجا، أو أرسل لي بوستر وأقولك وش هو.", align = TextAlign.Center, color = Palette.TextSecondary)
        Spacer(Modifier.height(14.dp))
        SUGGESTIONS.forEach { s ->
            Box(
                Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, Palette.Divider, RoundedCornerShape(16.dp)).clickable { onSuggestion(s) }.padding(horizontal = 14.dp, vertical = 8.dp).testTag("chat_suggestion"),
            ) { RtlText(s, size = 13, color = Palette.Text) }
        }
    }
}

@Composable
private fun Typing() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("chat_typing")) {
        SenpaiAvatar(30)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Bubble).padding(horizontal = 14.dp, vertical = 10.dp)) { Text("يكتب…", color = Palette.TextMuted, fontSize = 13.sp) }
    }
}

@Composable
private fun MessageRow(m: ChatMessage, onOpenCard: (ChatCard) -> Unit) {
    if (m.isUser) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            m.imagePath?.let { p ->
                AsyncImage(File(p), null, Modifier.widthIn(max = 200.dp).heightIn(max = 260.dp).clip(RoundedCornerShape(14.dp)).testTag("chat_user_image"), contentScale = ContentScale.Fit)
                if (m.text.isNotBlank()) Spacer(Modifier.height(4.dp))
            }
            if (m.text.isNotBlank()) Box(Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)).background(BubbleUser).padding(horizontal = 13.dp, vertical = 9.dp)) { RtlText(m.text) }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            SenpaiAvatar(30)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                if (m.error != null) {
                    Row(Modifier.clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)).background(Bubble).border(BorderStroke(1.dp, Palette.Divider), RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)).padding(horizontal = 12.dp, vertical = 9.dp).testTag("chat_error"), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Info, null, tint = Palette.OnHold, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        RtlText(m.error, color = Palette.TextSecondary, size = 13)
                    }
                } else {
                    if (m.text.isNotBlank()) Box(Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)).background(Bubble).padding(horizontal = 13.dp, vertical = 9.dp)) { RtlText(m.text) }
                    if (m.cards.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(m.cards, key = { it.type + it.id }) { c -> CardTile(c) { onOpenCard(c) } } }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardTile(c: ChatCard, onClick: () -> Unit) {
    Column(Modifier.width(112.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Card).clickable(onClick = onClick).testTag("chat_card")) {
        AsyncImage(c.cover, c.title, Modifier.fillMaxWidth().height(156.dp).background(Palette.CardHigh), contentScale = ContentScale.Crop)
        Column(Modifier.padding(horizontal = 7.dp, vertical = 6.dp)) {
            Text(c.title, color = Palette.Text, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
            val meta = listOfNotNull(if (c.isManga) "مانجا" else "أنمي", c.year?.toString(), c.score?.let { "★ ${it / 10.0}" }).joinToString(" · ")
            Text(meta, color = Palette.TextMuted, fontSize = 10.5.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ChatInputBar(ui: ChatUi, onInput: (String) -> Unit, onSend: () -> Unit, onPickImage: () -> Unit, onRemoveImage: () -> Unit) {
    // 0 left today: the server would refuse anyway, so the send button rests until midnight
    val enabled = ui.ready != false && !ui.sending && ui.remaining != 0
    Column(Modifier.fillMaxWidth().background(Palette.Black).navigationBarsPadding().imePadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
        if (ui.remaining != null && ui.ready != false) Text("باقي اليوم: ${ui.remaining}/${ui.limit ?: DAILY_LIMIT}", color = Palette.TextMuted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp).testTag("chat_remaining"), textAlign = TextAlign.Center)
        ui.image?.let { (file, _) ->
            Box(Modifier.padding(bottom = 6.dp)) {
                AsyncImage(file, null, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                Box(Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(Color(0xCC000000)).clickable(onClick = onRemoveImage), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, "إزالة الصورة", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPickImage, enabled = enabled && ui.image == null, modifier = Modifier.testTag("chat_pick_image")) {
                Icon(Icons.Filled.Image, "إرفاق صورة", tint = if (enabled && ui.image == null) Palette.RefIcon else Palette.TextMuted)
            }
            OutlinedTextField(
                ui.input, onInput, Modifier.weight(1f).testTag("chat_input"), enabled = ui.ready != false,
                placeholder = { RtlText(if (ui.ready == false) ChatRepository.NOT_READY else "اكتب اسم أنمي أو سؤالك…", color = Palette.TextMuted, size = 13) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.ContentOrRtl, color = Palette.Text),
                maxLines = 4, shape = RoundedCornerShape(22.dp),
                keyboardOptions = KeyboardOptions.Default,
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Field, unfocusedContainerColor = Field, disabledContainerColor = Field, focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.Divider, disabledBorderColor = Palette.Divider, cursorColor = Palette.Accent),
            )
            Spacer(Modifier.width(6.dp))
            val canSend = enabled && (ui.input.isNotBlank() || ui.image != null)
            Box(Modifier.size(46.dp).clip(CircleShape).background(if (canSend) Palette.Accent else Palette.Chip).clickable(enabled = canSend, onClick = onSend).testTag("chat_send"), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.Send, "إرسال", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ======================= customisation =======================

@Composable
fun ChatSettingsScreen(onBack: () -> Unit, vm: ChatSettingsViewModel = hiltViewModel()) {
    val p by vm.prefsFlow.collectAsStateWithLifecycle()
    p?.let { ChatSettingsContent(it, onBack) { np -> vm.save(np, onBack) } }
}

@Composable
fun ChatSettingsContent(initial: ChatPrefs, onBack: () -> Unit, onSave: (ChatPrefs) -> Unit) {
    var callMe by remember(initial) { mutableStateOf(initial.callMe) }
    var persona by remember(initial) { mutableStateOf(initial.persona) }
    val fieldColors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Field, unfocusedContainerColor = Field, disabledContainerColor = Field, focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.Divider, disabledBorderColor = Palette.Divider, cursorColor = Palette.Accent, disabledTextColor = Palette.TextSecondary)
    val rtl = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.ContentOrRtl, color = Palette.Text)
    Scaffold(containerColor = Palette.Black, topBar = { AppTopBar("تخصيص $ASSISTANT_NAME", onBack = onBack) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("chat_settings"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SenpaiAvatar(80) } }
            item {
                Label("اسم المساعد")
                OutlinedTextField(ASSISTANT_NAME, {}, Modifier.fillMaxWidth().testTag("chat_name_locked"), enabled = false, textStyle = rtl.copy(color = Palette.TextSecondary), colors = fieldColors, shape = RoundedCornerShape(14.dp),
                    leadingIcon = { Icon(Icons.Filled.Lock, null, tint = Palette.TextMuted) })
                Hint("الاسم ثابت ما يتغير، حتى لو طلبت منه في البرومبت أو المحادثة.")
            }
            item {
                Label("وش يناديك؟")
                OutlinedTextField(callMe, { callMe = it.take(ExtraPrefs.MAX_CALL_ME) }, Modifier.fillMaxWidth().testTag("chat_call_me"), singleLine = true, textStyle = rtl, colors = fieldColors, shape = RoundedCornerShape(14.dp),
                    placeholder = { RtlText("مثلاً: مَدْيَن، كابتن، سينسي…", color = Palette.TextMuted, size = 13) })
            }
            item {
                Label("أسلوب الكلام (البرومبت)")
                OutlinedTextField(persona, { persona = it.take(ExtraPrefs.MAX_PERSONA) }, Modifier.fillMaxWidth().heightIn(min = 130.dp).testTag("chat_persona"), textStyle = rtl, colors = fieldColors, shape = RoundedCornerShape(14.dp),
                    placeholder = { RtlText("مثلاً: تكلم بلهجة يمنية، خلك مختصر ومرح، وركز على أنميات الأكشن.", color = Palette.TextMuted, size = 13) })
                Text("${persona.length}/${ExtraPrefs.MAX_PERSONA}", color = Palette.TextMuted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                Hint("يتبع أسلوبك، بس يظل متخصص في الأنمي والمانجا واسمه ما يتغير.")
            }
            item {
                Button(onClick = { onSave(ChatPrefs(callMe, persona)) }, colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(48.dp).testTag("chat_settings_save")) {
                    Text("حفظ", color = Color.White, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable private fun Label(t: String) = Text(t, style = MaterialTheme.typography.titleSmall, color = Palette.Text, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), textAlign = TextAlign.End)
@Composable private fun Hint(t: String) = Text(t, color = Palette.TextMuted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.ContentOrRtl))
