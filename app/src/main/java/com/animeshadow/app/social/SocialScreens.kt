package com.animeshadow.app.social

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.animeshadow.app.data.auth.GoogleAccount
import com.animeshadow.app.data.auth.GoogleAccountRepository
import com.animeshadow.app.ui.common.AppTopBar
import com.animeshadow.app.ui.common.LoadingBox
import com.animeshadow.app.ui.common.RoundImage
import com.animeshadow.app.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

// ================================================================== shared bits

/** Small gradient «PRO» badge next to a name. */
@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Text(
        "PRO", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp,
        modifier = modifier.clip(RoundedCornerShape(6.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFFFFB300), Color(0xFFFF4D8D), Palette.Accent)))
            .padding(horizontal = 6.dp, vertical = 1.dp).testTag("pro_badge"),
    )
}

@Composable
private fun Avatar(url: String?, name: String, size: Int = 44) {
    if (url != null) RoundImage(url, size.dp)
    else Box(
        Modifier.size(size.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Palette.AccentDeep, Palette.Blue))),
        contentAlignment = Alignment.Center,
    ) { Text(name.trim().take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size / 2.4).sp) }
}

@Composable
private fun Rtl(text: String, modifier: Modifier = Modifier, color: Color = Palette.Text, size: Int = 14, weight: FontWeight = FontWeight.Normal, align: TextAlign = TextAlign.Start, maxLines: Int = Int.MAX_VALUE) =
    Text(text, modifier, color = color, fontSize = size.sp, fontWeight = weight, textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Rtl, lineHeight = (size + 7).sp))

/** Calm empty state with an icon in a soft circle (v1.5.0 polish). */
@Composable
fun NiceEmpty(icon: ImageVector, title: String, sub: String? = null, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(Palette.AccentSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Palette.Accent, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(16.dp))
        Rtl(title, size = 17, weight = FontWeight.Bold, align = TextAlign.Center)
        if (sub != null) { Spacer(Modifier.height(6.dp)); Rtl(sub, color = Palette.TextSecondary, size = 13, align = TextAlign.Center) }
        if (action != null) { Spacer(Modifier.height(18.dp)); action() }
    }
}

@Composable
fun PillButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, filled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(24.dp))
            .then(if (filled) Modifier.background(if (enabled) Palette.Accent else Palette.CardHigh) else Modifier.border(1.dp, Palette.Divider, RoundedCornerShape(24.dp)))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = if (enabled) Color.White else Palette.TextMuted, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

private fun copy(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("AnimeShadow", text))
    android.widget.Toast.makeText(ctx, "تم النسخ", android.widget.Toast.LENGTH_SHORT).show()
}

// ================================================================== friends + conversations

data class FriendsUi(
    val account: GoogleAccount = GoogleAccount(),
    val me: Me? = null,
    val friends: Friends = Friends(),
    val conversations: Conversations = Conversations(),
    val loading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val busy: Boolean = false,
)

@HiltViewModel
class FriendsViewModel @Inject constructor(private val repo: SocialRepository, private val account: GoogleAccountRepository) : ViewModel() {
    private val _ui = MutableStateFlow(FriendsUi(account = account.account.value, me = account.me.value))
    val ui: StateFlow<FriendsUi> = _ui

    fun refresh(quiet: Boolean = false) = viewModelScope.launch {
        _ui.update { it.copy(account = account.account.value, me = account.me.value, loading = !quiet && it.friends.friends.isEmpty() && it.conversations.items.isEmpty()) }
        if (!repo.linked) { _ui.update { it.copy(loading = false) }; return@launch }
        try {
            val me = repo.refreshMe()
            val f = repo.api.friends(); val c = repo.api.conversations()
            _ui.update { it.copy(me = me ?: it.me, friends = f, conversations = c, loading = false, error = null) }
        } catch (e: SocialException) {
            _ui.update { it.copy(loading = false, error = e.message, me = account.me.value) }
        }
    }

    private fun act(f: suspend () -> String?) = viewModelScope.launch {
        _ui.update { it.copy(busy = true) }
        val note = try { f() } catch (e: SocialException) { e.message }
        _ui.update { it.copy(busy = false, notice = note) }
        refresh(quiet = true)
    }
    fun add(q: String) = act { val r = repo.api.request(q.trim()); if (r.status == "friends") "أصبحتما صديقين 🎉" else "أُرسل طلب الصداقة إلى ${r.user?.handle ?: q}" }
    fun accept(id: String) = act { repo.api.accept(id); "تمت إضافة الصديق" }
    fun decline(id: String) = act { repo.api.remove(id); null }
    fun unblock(id: String) = act { repo.api.unblock(id); "أُلغي الحظر" }
    fun setUsername(u: String) = act { account.saveMe(repo.api.setUsername(u)); "تم حفظ اسم المستخدم" }
    fun noticeShown() = _ui.update { it.copy(notice = null) }
}

class FriendsActions(
    val onMenu: () -> Unit = {}, val onLogin: () -> Unit = {}, val onOpen: (PublicUser) -> Unit = {}, val onAdd: (String) -> Unit = {},
    val onAccept: (String) -> Unit = {}, val onDecline: (String) -> Unit = {}, val onUnblock: (String) -> Unit = {},
    val onUsername: (String) -> Unit = {}, val onLists: () -> Unit = {}, val onRetry: () -> Unit = {},
)

@Composable
fun FriendsContent(ui: FriendsUi, a: FriendsActions, linked: Boolean, snackbar: SnackbarHostState = remember { SnackbarHostState() }) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(0) }
    var addText by rememberSaveable { mutableStateOf("") }
    var nameDialog by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Palette.Black,
        topBar = { AppTopBar("الأصدقاء", onMenu = a.onMenu) { IconButton(onClick = a.onLists) { Icon(Icons.Filled.ViewList, "مشاركة القوائم", tint = Palette.RefIcon) } } },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().testTag("friends_screen")) {
            when {
                !ui.account.signedIn -> NiceEmpty(Icons.Filled.Forum, "الدردشة مع الأصدقاء", "سجّل الدخول بحساب Google لتضيف أصدقاءك وتتراسلوا وتتبادلوا قوائمكم.") {
                    PillButton("تسجيل الدخول بـ Google", onClick = a.onLogin)
                }
                !linked -> NiceEmpty(Icons.Filled.Forum, "الدردشة غير متاحة الآن", ui.error ?: "أعد تسجيل الدخول لربط حسابك بالخادم.") {
                    PillButton("إعادة الربط", onClick = a.onLogin)
                }
                ui.loading -> LoadingBox()
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item(key = "me") { MeCard(ui.me, ui.account, onCopy = { copy(ctx, it) }, onName = { nameDialog = true }) }
                    item(key = "add") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                addText, { addText = it.take(30) }, Modifier.weight(1f).testTag("add_friend_field"), singleLine = true,
                                placeholder = { Rtl("اسم المستخدم أو كود الصديق", color = Palette.TextMuted, size = 13) },
                                shape = RoundedCornerShape(14.dp), colors = fieldColors(),
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = { if (addText.isNotBlank()) { a.onAdd(addText); addText = "" } }, enabled = !ui.busy, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Accent).testTag("add_friend")) {
                                if (ui.busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                else Icon(Icons.Filled.PersonAdd, "إضافة", tint = Color.White)
                            }
                        }
                    }
                    if (ui.error != null) item(key = "err") { Rtl(ui.error, Modifier.padding(16.dp), color = Palette.OnHold, size = 13) }
                    if (ui.friends.incoming.isNotEmpty()) {
                        item(key = "inc_h") { Header("طلبات صداقة (${ui.friends.incoming.size})") }
                        items(ui.friends.incoming, key = { "in_" + it.id }) { u ->
                            UserRow(u, sub = "يريد إضافتك") {
                                IconButton(onClick = { a.onAccept(u.id) }, modifier = Modifier.testTag("accept_${u.id}")) { Icon(Icons.Filled.Check, "قبول", tint = Palette.Completed) }
                                IconButton(onClick = { a.onDecline(u.id) }) { Icon(Icons.Filled.Close, "رفض", tint = Palette.TextMuted) }
                            }
                        }
                    }
                    item(key = "tabs") { Tabs(listOf("المحادثات" to ui.conversations.unreadTotal, "الأصدقاء" to ui.friends.friends.size), tab) { tab = it } }
                    if (tab == 0) {
                        if (ui.conversations.items.isEmpty()) item(key = "c_empty") { NiceEmpty(Icons.Filled.Forum, "لا توجد محادثات بعد", "افتح صديقاً من تبويب «الأصدقاء» وابدأ الكلام.") }
                        items(ui.conversations.items, key = { "c_" + it.user.id }) { c ->
                            UserRow(c.user, sub = c.last.preview(), onClick = { a.onOpen(c.user) }, bold = c.unread > 0) {
                                if (c.unread > 0) Text("${c.unread}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 12.dp).clip(CircleShape).background(Palette.Accent).padding(horizontal = 7.dp, vertical = 2.dp))
                            }
                        }
                    } else {
                        if (ui.friends.friends.isEmpty()) item(key = "f_empty") { NiceEmpty(Icons.Filled.PersonAdd, "أضف أول صديق", "شارك كودك أو اسم المستخدم مع صديقك، أو اكتب كوده في الخانة بالأعلى.") }
                        items(ui.friends.friends, key = { "f_" + it.id }) { u -> UserRow(u, sub = u.username?.let { "@$it" }, onClick = { a.onOpen(u) }) }
                        if (ui.friends.outgoing.isNotEmpty()) {
                            item(key = "out_h") { Header("طلبات أرسلتها") }
                            items(ui.friends.outgoing, key = { "o_" + it.id }) { u -> UserRow(u, sub = "بانتظار القبول") { TextButton(onClick = { a.onDecline(u.id) }) { Text("إلغاء", color = Palette.TextMuted) } } }
                        }
                        if (ui.friends.blocked.isNotEmpty()) {
                            item(key = "bl_h") { Header("المحظورون") }
                            items(ui.friends.blocked, key = { "b_" + it.id }) { u -> UserRow(u, sub = "محظور") { TextButton(onClick = { a.onUnblock(u.id) }) { Text("إلغاء الحظر", color = Palette.TextMuted) } } }
                        }
                    }
                }
            }
        }
    }
    if (nameDialog) UsernameDialog(ui.me?.username.orEmpty(), { nameDialog = false }) { a.onUsername(it); nameDialog = false }
}

private fun Msg.preview(): String = if (kind == "list") "📋 " + (list?.title ?: "قائمة") + " (${list?.items?.size ?: 0})" else text

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.Divider, focusedTextColor = Palette.Text, unfocusedTextColor = Palette.Text, cursorColor = Palette.Accent,
)

@Composable
private fun Header(t: String) = Rtl(t, Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp), color = Palette.TextSecondary, size = 13, weight = FontWeight.Bold)

@Composable
private fun Tabs(items: List<Pair<String, Int>>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Card).padding(4.dp)) {
        items.forEachIndexed { i, (t, n) ->
            val on = i == selected
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (on) Palette.CardHigh else Color.Transparent).clickable { onSelect(i) }.padding(vertical = 9.dp).testTag("ftab_$i"),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t, color = if (on) Color.White else Palette.TextMuted, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                if (n > 0) { Spacer(Modifier.width(6.dp)); Text("$n", color = if (on) Palette.Accent else Palette.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun MeCard(me: Me?, account: GoogleAccount, onCopy: (String) -> Unit, onName: () -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1B1530), Palette.Card))).border(1.dp, Palette.Divider, RoundedCornerShape(18.dp)).padding(16.dp).testTag("me_card"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(me?.picture ?: account.photoUrl, me?.name ?: account.name, 52)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(me?.name ?: account.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (me?.pro?.active == true) { Spacer(Modifier.width(6.dp)); ProBadge() }
                }
                Text(me?.username?.let { "@$it" } ?: "بدون اسم مستخدم", color = if (me?.username != null) Palette.Accent else Palette.TextMuted, fontSize = 13.sp,
                    modifier = Modifier.clickable(onClick = onName).testTag("username"))
            }
            TextButton(onClick = onName) { Text(if (me?.username == null) "اختيار" else "تعديل", color = Palette.Accent) }
        }
        if (me != null) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Black.copy(alpha = 0.35f)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Rtl("كود الصديق", color = Palette.TextSecondary, size = 12)
                Spacer(Modifier.width(10.dp))
                Text(me.code.chunked(4).joinToString(" "), color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 16.sp, modifier = Modifier.weight(1f).testTag("friend_code"))
                IconButton(onClick = { onCopy(me.code) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.ContentCopy, "نسخ", tint = Palette.RefIcon, modifier = Modifier.size(18.dp)) }
                IconButton(onClick = {
                    val t = "أضفني في أنمي شادو 👋\nكود الصديق: ${me.code}" + (me.username?.let { "\nأو اسم المستخدم: @$it" } ?: "")
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), null))
                }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Share, "مشاركة", tint = Palette.RefIcon, modifier = Modifier.size(18.dp)) }
            }
            if (me.pro.active) {
                Spacer(Modifier.height(8.dp))
                Rtl("اشتراك ${me.pro.plan ?: "Pro"} فعّال" + (me.pro.until?.take(10)?.let { " حتى \u2066$it\u2069" } ?: ""), color = Palette.Star, size = 12)
            }
        }
    }
}

@Composable
private fun UserRow(u: PublicUser, sub: String? = null, onClick: (() -> Unit)? = null, bold: Boolean = false, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 9.dp).testTag("user_${u.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(u.picture, u.name)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(u.name, color = Color.White, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (u.pro) { Spacer(Modifier.width(6.dp)); ProBadge() }
            }
            if (sub != null) Rtl(sub, color = if (bold) Palette.Text else Palette.TextMuted, size = 13, maxLines = 1)
        }
        trailing()
    }
}

@Composable
private fun UsernameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Palette.Card,
        title = { Rtl("اسم المستخدم", weight = FontWeight.Bold, size = 17) },
        text = {
            Column {
                Rtl("3 إلى 20 حرفاً إنجليزياً أو رقماً أو _ . يراه أصدقاؤك ويضيفونك به.", color = Palette.TextSecondary, size = 13)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(t, { v -> t = v.lowercase().filter { it.isLetterOrDigit() && it.code < 128 || it == '_' || it == '.' }.take(20) }, singleLine = true,
                    prefix = { Text("@", color = Palette.TextMuted) }, colors = fieldColors(), modifier = Modifier.testTag("username_field"))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(t) }, enabled = t.length >= 3) { Text("حفظ", color = Palette.Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = Palette.TextMuted) } },
    )
}

@Composable
fun FriendsScreen(onMenu: () -> Unit, onLogin: () -> Unit, onOpen: (String) -> Unit, onLists: () -> Unit, vm: FriendsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val account by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { while (true) { vm.refresh(quiet = true); delay(10_000) } }
    LaunchedEffect(ui.notice) { ui.notice?.let { snackbar.showSnackbar(it); vm.noticeShown() } }
    val linked = ui.me != null || (account.account.signedIn && ui.friends.friends.isNotEmpty())
    FriendsContent(
        ui, FriendsActions(onMenu, onLogin, { onOpen(it.id) }, vm::add, vm::accept, vm::decline, vm::unblock, vm::setUsername, onLists, { vm.refresh() }),
        linked = linked, snackbar = snackbar,
    )
}

// ================================================================== one conversation

data class ThreadUi(val user: PublicUser? = null, val me: String? = null, val items: List<Msg> = emptyList(), val blocked: Boolean = false, val friends: Boolean = true, val error: String? = null, val sending: Boolean = false, val notice: String? = null)

@HiltViewModel
class ThreadViewModel @Inject constructor(state: SavedStateHandle, private val repo: SocialRepository, private val lists: ListShare) : ViewModel() {
    val peer: String = state.get<String>("id").orEmpty()
    private val _ui = MutableStateFlow(ThreadUi(me = repo.account.me.value?.id))
    val ui: StateFlow<ThreadUi> = _ui

    fun poll() = viewModelScope.launch {
        try {
            val after = _ui.value.items.lastOrNull()?.id ?: 0
            val t = repo.api.thread(peer, after)
            _ui.update { u -> u.copy(user = t.user, items = (u.items + t.items).distinctBy { it.id }, blocked = t.blocked, friends = t.friends, error = null) }
            val lastIn = t.items.lastOrNull { it.from == peer }
            if (lastIn != null) repo.api.markRead(peer, lastIn.id)
        } catch (e: SocialException) { _ui.update { it.copy(error = e.message) } }
    }

    fun send(text: String) = viewModelScope.launch {
        if (text.isBlank()) return@launch
        _ui.update { it.copy(sending = true) }
        try { val m = repo.api.sendText(peer, text.trim()); _ui.update { u -> u.copy(items = (u.items + m).distinctBy { it.id }, sending = false, error = null) } }
        catch (e: SocialException) { _ui.update { it.copy(sending = false, notice = e.message) } }
    }

    fun importList(m: Msg) = viewModelScope.launch {
        val l = m.list ?: return@launch
        val r = runCatching { lists.import(listOf(ExportList(lists.guessKey(l), l.title, l.items))) }.getOrNull()
        _ui.update { it.copy(notice = if (r == null) "تعذّر الاستيراد" else "أُضيف ${r.added} عنواناً إلى مكتبتك" + if (r.skipped > 0) " (${r.skipped} موجود مسبقاً)" else "") }
    }

    fun block() = viewModelScope.launch { runCatching { repo.api.block(peer) }; _ui.update { it.copy(blocked = true, notice = "تم الحظر") } }
    fun unfriend() = viewModelScope.launch { runCatching { repo.api.remove(peer) }; _ui.update { it.copy(friends = false, notice = "أُزيل من الأصدقاء") } }
    fun report(reason: String) = viewModelScope.launch {
        val r = runCatching { repo.api.report(peer, reason, _ui.value.items.lastOrNull { it.from == peer }?.id) }
        _ui.update { it.copy(notice = if (r.isSuccess) "وصل البلاغ، شكراً لك." else (r.exceptionOrNull()?.message ?: "تعذّر الإبلاغ")) }
    }
    fun noticeShown() = _ui.update { it.copy(notice = null) }
}

@Composable
fun ThreadContent(ui: ThreadUi, onBack: () -> Unit, onSend: (String) -> Unit, onImport: (Msg) -> Unit, onBlock: () -> Unit, onUnfriend: () -> Unit, onReport: (String) -> Unit, snackbar: SnackbarHostState = remember { SnackbarHostState() }) {
    var input by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var reportDialog by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    LaunchedEffect(ui.items.size) { if (ui.items.isNotEmpty()) list.animateScrollToItem(ui.items.size - 1) }
    Scaffold(
        containerColor = Palette.Black,
        topBar = {
            AppTopBar(ui.user?.name ?: "", onBack = onBack) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "المزيد", tint = Palette.RefIcon) }
                    DropdownMenu(menu, { menu = false }, containerColor = Palette.Card) {
                        DropdownMenuItem(text = { Text("إبلاغ") }, leadingIcon = { Icon(Icons.Filled.Flag, null) }, onClick = { menu = false; reportDialog = true })
                        DropdownMenuItem(text = { Text("إزالة من الأصدقاء") }, leadingIcon = { Icon(Icons.Filled.PersonRemove, null) }, onClick = { menu = false; onUnfriend() })
                        DropdownMenuItem(text = { Text("حظر", color = Palette.Dropped) }, leadingIcon = { Icon(Icons.Filled.Block, null, tint = Palette.Dropped) }, onClick = { menu = false; confirmBlock = true })
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding().testTag("thread_screen")) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (ui.items.isEmpty()) item { NiceEmpty(Icons.Filled.Forum, "قل مرحباً 👋", "الرسائل بينك وبين ${ui.user?.name ?: "صديقك"} فقط.") }
                items(ui.items, key = { it.id }) { m -> Bubble(m, mine = m.from != ui.user?.id, onImport = { onImport(m) }) }
            }
            if (ui.error != null) Rtl(ui.error, Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = Palette.OnHold, size = 12)
            if (ui.blocked || !ui.friends) {
                Rtl(if (ui.blocked) "المحادثة محظورة." else "المراسلة بين الأصدقاء فقط.", Modifier.fillMaxWidth().padding(16.dp), color = Palette.TextMuted, size = 13, align = TextAlign.Center)
            } else Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(input, { input = it.take(1000) }, Modifier.weight(1f).testTag("thread_input"), maxLines = 5,
                    placeholder = { Rtl("اكتب رسالة…", color = Palette.TextMuted, size = 13) }, shape = RoundedCornerShape(22.dp), colors = fieldColors())
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { onSend(input); input = "" }, enabled = input.isNotBlank() && !ui.sending,
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(if (input.isNotBlank()) Palette.Accent else Palette.CardHigh).testTag("thread_send")) {
                    Icon(Icons.AutoMirrored.Filled.Send, "إرسال", tint = Color.White)
                }
            }
        }
    }
    if (confirmBlock) AlertDialog(
        onDismissRequest = { confirmBlock = false }, containerColor = Palette.Card,
        title = { Rtl("حظر ${ui.user?.name ?: ""}؟", weight = FontWeight.Bold) },
        text = { Rtl("لن يستطيع مراسلتك أو إضافتك، ويُزال من أصدقائك.", color = Palette.TextSecondary, size = 13) },
        confirmButton = { TextButton(onClick = { confirmBlock = false; onBlock() }) { Text("حظر", color = Palette.Dropped) } },
        dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("إلغاء", color = Palette.TextMuted) } },
    )
    if (reportDialog) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reportDialog = false }, containerColor = Palette.Card,
            title = { Rtl("إبلاغ عن ${ui.user?.name ?: ""}", weight = FontWeight.Bold) },
            text = {
                Column {
                    listOf("إزعاج أو رسائل مزعجة", "إساءة أو تحرّش", "محتوى غير لائق").forEach { r ->
                        Rtl("• $r", Modifier.fillMaxWidth().clickable { reason = r }.padding(vertical = 8.dp), color = if (reason == r) Palette.Accent else Palette.Text)
                    }
                    OutlinedTextField(reason, { reason = it.take(300) }, placeholder = { Rtl("أو اكتب السبب", color = Palette.TextMuted, size = 13) }, colors = fieldColors())
                }
            },
            confirmButton = { TextButton(onClick = { reportDialog = false; onReport(reason) }, enabled = reason.isNotBlank()) { Text("إرسال البلاغ", color = Palette.Accent) } },
            dismissButton = { TextButton(onClick = { reportDialog = false }) { Text("إلغاء", color = Palette.TextMuted) } },
        )
    }
}

@Composable
private fun Bubble(m: Msg, mine: Boolean, onImport: () -> Unit) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.Start else Arrangement.End) {
        AnimatedVisibility(appeared, enter = fadeIn() + slideInVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) { it / 3 }) {
            Column(
                Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (mine) 4.dp else 18.dp, bottomEnd = if (mine) 18.dp else 4.dp))
                    .background(if (mine) Palette.AccentDeep else Palette.CardHigh).padding(horizontal = 12.dp, vertical = 8.dp).animateContentSize(),
            ) {
                if (m.kind == "list" && m.list != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ViewList, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Rtl(m.list.title + " (${m.list.items.size})", weight = FontWeight.Bold, size = 14)
                    }
                    m.list.items.take(5).forEach { Rtl("• " + it.t, color = Color.White.copy(alpha = 0.85f), size = 13, maxLines = 1) }
                    if (m.list.items.size > 5) Rtl("و ${m.list.items.size - 5} أخرى…", color = Color.White.copy(alpha = 0.6f), size = 12)
                    if (!mine) Text("إضافة إلى مكتبتي", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.14f)).clickable(onClick = onImport).padding(horizontal = 10.dp, vertical = 6.dp).testTag("import_${m.id}"))
                    if (m.text.isNotBlank()) Rtl(m.text, size = 14, modifier = Modifier.padding(top = 4.dp))
                } else Rtl(m.text, size = 15)
                Text(m.at.drop(11).take(5), color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

@Composable
fun ThreadScreen(onBack: () -> Unit, vm: ThreadViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { while (true) { vm.poll(); delay(4_000) } }
    LaunchedEffect(ui.notice) { ui.notice?.let { snackbar.showSnackbar(it); vm.noticeShown() } }
    ThreadContent(ui, onBack, vm::send, vm::importList, vm::block, vm::unfriend, vm::report, snackbar)
}

// ================================================================== export / share / import lists

data class ListsUi(val selected: Set<String> = setOf("anime:COMPLETED", "anime:DROPPED", "anime:NOT_INTERESTED"), val counts: Map<String, Int> = emptyMap(), val friends: List<PublicUser> = emptyList(), val linked: Boolean = false, val notice: String? = null, val busy: Boolean = false)

@HiltViewModel
class ListsViewModel @Inject constructor(private val share: ListShare, private val repo: SocialRepository) : ViewModel() {
    private val _ui = MutableStateFlow(ListsUi(linked = repo.linked))
    val ui: StateFlow<ListsUi> = _ui

    init { load() }
    fun load() = viewModelScope.launch {
        val all = share.build(ListKeys.ANIME + ListKeys.MANGA)
        _ui.update { it.copy(counts = all.associate { l -> l.key to l.items.size }, linked = repo.linked) }
        if (repo.linked) runCatching { repo.api.friends() }.getOrNull()?.let { f -> _ui.update { it.copy(friends = f.friends) } }
    }
    fun toggle(key: String) = _ui.update { it.copy(selected = if (key in it.selected) it.selected - key else it.selected + key) }

    /** Writes «AnimeShadow-lists.json» + «.csv» + «.txt» to cache/exports and returns them for the share sheet. */
    fun export(ctx: Context, onFiles: (List<File>) -> Unit) = viewModelScope.launch {
        val lists = share.build(_ui.value.selected)
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val files = listOf(
            File(dir, "AnimeShadow-lists-$stamp.json").apply { writeText(share.toJson(lists)) },
            File(dir, "AnimeShadow-lists-$stamp.csv").apply { writeText(share.toCsv(lists)) },
            File(dir, "AnimeShadow-lists-$stamp.txt").apply { writeText(share.toText(lists)) },
        )
        onFiles(files)
    }

    fun sendTo(friend: PublicUser) = viewModelScope.launch {
        _ui.update { it.copy(busy = true) }
        var sent = 0
        val note = try {
            for (l in share.build(_ui.value.selected).filter { it.items.isNotEmpty() }) { repo.api.sendList(friend.id, SharedList(l.title, l.items.take(500))); sent++ }
            if (sent == 0) "القوائم المختارة فارغة" else "أُرسلت $sent قائمة إلى ${friend.name}"
        } catch (e: SocialException) { e.message }
        _ui.update { it.copy(busy = false, notice = note) }
    }

    fun importText(text: String) = viewModelScope.launch {
        val note = try { val r = share.import(share.parse(text)); "أُضيف ${r.added} عنواناً" + if (r.skipped > 0) " (${r.skipped} موجود مسبقاً)" else "" }
        catch (e: Exception) { "الملف ليس قائمة من أنمي شادو (JSON)." }
        _ui.update { it.copy(notice = note) }
        load()
    }
    fun noticeShown() = _ui.update { it.copy(notice = null) }
}

@Composable
fun ListsContent(ui: ListsUi, onBack: () -> Unit, onToggle: (String) -> Unit, onExport: () -> Unit, onSend: (PublicUser) -> Unit, onImport: () -> Unit, snackbar: SnackbarHostState = remember { SnackbarHostState() }, onMenu: (() -> Unit)? = null) {
    var pick by remember { mutableStateOf(false) }
    Scaffold(containerColor = Palette.Black, topBar = { AppTopBar("مشاركة القوائم", onMenu = onMenu, onBack = if (onMenu == null) onBack else null) }, snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("lists_screen"), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { Rtl("اختر القوائم ثم صدّرها إلى ملف أو أرسلها إلى صديق داخل التطبيق. يمكنك استيراد الملف لاحقاً على أي جهاز.", Modifier.padding(16.dp), color = Palette.TextSecondary, size = 13) }
            item { Header("أنمي") }
            items(ListKeys.ANIME, key = { it }) { k -> CheckRow(ListKeys.title(k), ui.counts[k] ?: 0, k in ui.selected) { onToggle(k) } }
            item { Header("مانجا") }
            items(ListKeys.MANGA, key = { it }) { k -> CheckRow(ListKeys.title(k), ui.counts[k] ?: 0, k in ui.selected) { onToggle(k) } }
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("تصدير ومشاركة ملف", Modifier.fillMaxWidth().testTag("export_btn"), icon = Icons.Filled.FileDownload, enabled = ui.selected.isNotEmpty(), onClick = onExport)
                    PillButton(if (ui.linked) "إرسال إلى صديق" else "إرسال إلى صديق (سجّل الدخول أولاً)", Modifier.fillMaxWidth().testTag("send_friend_btn"), icon = Icons.Filled.Forum,
                        enabled = ui.linked && ui.selected.isNotEmpty() && !ui.busy, filled = false) { pick = true }
                    PillButton("استيراد من ملف", Modifier.fillMaxWidth().testTag("import_btn"), icon = Icons.Filled.FileUpload, filled = false, onClick = onImport)
                }
            }
        }
    }
    if (pick) AlertDialog(
        onDismissRequest = { pick = false }, containerColor = Palette.Card,
        title = { Rtl("إرسال إلى…", weight = FontWeight.Bold) },
        text = {
            if (ui.friends.isEmpty()) Rtl("لا يوجد أصدقاء بعد. أضفهم من صفحة «الأصدقاء».", color = Palette.TextSecondary, size = 13)
            else Column { ui.friends.forEach { f -> UserRow(f, sub = f.username?.let { "@$it" }, onClick = { pick = false; onSend(f) }) } }
        },
        confirmButton = { TextButton(onClick = { pick = false }) { Text("إغلاق", color = Palette.TextMuted) } },
    )
}

@Composable
private fun CheckRow(title: String, count: Int, checked: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, { onClick() }, colors = CheckboxDefaults.colors(checkedColor = Palette.Accent, uncheckedColor = Palette.TextMuted))
        Text(
            "$count", color = if (count > 0) Palette.TextSecondary else Palette.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Palette.CardHigh).padding(horizontal = 9.dp, vertical = 2.dp),
        )
        Rtl(title, Modifier.weight(1f).padding(start = 10.dp, end = 12.dp), size = 15)
    }
}

@Composable
fun ListsScreen(onBack: () -> Unit, onMenu: (() -> Unit)? = null, vm: ListsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(ui.notice) { ui.notice?.let { snackbar.showSnackbar(it); vm.noticeShown() } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } }.getOrNull()
            if (text != null) vm.importText(text)
        }
    }
    ListsContent(ui, onBack, vm::toggle, onExport = {
        vm.export(ctx) { files ->
            val uris = ArrayList(files.map { FileProvider.getUriForFile(ctx, ctx.packageName + ".files", it) })
            val send = Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                .putExtra(Intent.EXTRA_TEXT, files.last().readText().take(3000)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(Intent.createChooser(send, "مشاركة القوائم"))
        }
    }, onSend = vm::sendTo, onImport = { picker.launch(arrayOf("application/json", "text/plain", "*/*")) }, snackbar = snackbar, onMenu = onMenu)
}
