package com.animeshadow.app.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.animeshadow.app.data.settings.ExtraPrefs
import com.animeshadow.app.domain.AniTags
import com.animeshadow.app.ui.ads.ListAdBanner
import com.animeshadow.app.ui.common.AppTopBar
import com.animeshadow.app.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Arabic name for a tag category ("Theme-Arts-Music" → «فنون · موسيقى»). */
fun categoryLabel(c: String): String = when (c) {
    "Demographic" -> "الفئة المستهدفة"
    "Cast-Main Cast" -> "الشخصيات الرئيسية"
    "Cast-Traits" -> "صفات الشخصيات"
    "Setting-Scene" -> "مكان الأحداث"
    "Setting-Time" -> "زمن الأحداث"
    "Setting-Universe" -> "عالم القصة"
    "Technical" -> "تقنيات وأسلوب"
    "Sexual Content" -> "محتوى للكبار +18"
    else -> c.split('-').drop(if (c.startsWith("Theme")) 1 else 0).joinToString(" · ") { AniTags.ar(it).let { a -> if (a == it) themeAr(it) else a } }.let { if (c.startsWith("Theme")) "مواضيع: $it" else it }
}

private fun themeAr(w: String) = when (w) {
    "Action" -> "أكشن"; "Arts" -> "فنون"; "Music" -> "موسيقى"; "Comedy" -> "كوميديا"; "Drama" -> "دراما"; "Fantasy" -> "فانتازيا"; "Game" -> "ألعاب"
    "Sport" -> "رياضة"; "Card & Board Game" -> "ورق ولوحية"; "Other" -> "أخرى"; "Organisations" -> "منظمات"; "Vehicle" -> "مركبات"; "Romance" -> "رومانسية"
    "Sci" -> "خيال علمي"; "Fi" -> ""; "Mecha" -> "ميكا"; "Slice of Life" -> "شريحة من الحياة"; else -> w
}

@HiltViewModel
class CategoriesViewModel @Inject constructor(prefs: ExtraPrefs) : ViewModel() {
    val adult: StateFlow<Boolean> = prefs.adult.stateIn(viewModelScope, SharingStarted.Eagerly, false)
}

/** v1.4.0: every AniList genre + tag, grouped; tap one to list its titles (anime or manga, whichever mode is open). */
@Composable
fun CategoriesScreen(onMenu: (() -> Unit)?, onBack: (() -> Unit)?, manga: Boolean, onGenre: (String) -> Unit, onTag: (String) -> Unit, vm: CategoriesViewModel = hiltViewModel()) {
    val adult by vm.adult.collectAsStateWithLifecycle()
    CategoriesContent(adult, manga, onMenu, onBack, onGenre, onTag)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoriesContent(adult: Boolean, manga: Boolean, onMenu: (() -> Unit)?, onBack: (() -> Unit)?, onGenre: (String) -> Unit, onTag: (String) -> Unit) {
    var expanded by remember { mutableStateOf(setOf<String>()) }
    val tags = AniTags.tags(adult)
    val byCat = tags.groupBy { it.category }
    Scaffold(
        containerColor = Palette.Black,
        topBar = { AppTopBar(if (manga) "تصنيفات المانجا" else "تصنيفات الأنمي", onMenu = onMenu, onBack = onBack) },
        bottomBar = { ListAdBanner() },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("categories"), contentPadding = PaddingValues(14.dp)) {
            item { Header("الأنواع", AniTags.genres(adult).size) }
            item {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AniTags.genres(adult).forEach { g -> TagChip(AniTags.ar(g), g in AniTags.ADULT_GENRES, Modifier.testTag("genre_chip")) { onGenre(g) } }
                }
            }
            AniTags.CATEGORIES.filter { byCat.containsKey(it) }.forEach { cat ->
                val list = byCat.getValue(cat)
                item(key = cat) { Header(categoryLabel(cat), list.size) }
                item(key = "$cat-chips") {
                    val open = cat in expanded || list.size <= 14
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (if (open) list else list.take(12)).forEach { t -> TagChip(AniTags.ar(t.name), t.adult || t.category == AniTags.SEXUAL, Modifier.testTag("tag_chip")) { onTag(t.name) } }
                        if (!open) Text("المزيد (${list.size - 12}) ▾", color = Palette.Plan, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable { expanded = expanded + cat }.padding(horizontal = 10.dp, vertical = 7.dp).testTag("tags_more"))
                    }
                }
            }
            if (!adult) item {
                Text("تصنيفات +18 مخفية. تقدر تفعّلها من الإعدادات ← «محتوى +18».", color = Palette.TextMuted, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp))
            }
        }
    }
}

@Composable
private fun Header(title: String, n: Int) {
    Text("$title ($n)", style = MaterialTheme.typography.titleMedium, color = Palette.Text, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 10.dp))
}

@Composable
fun TagChip(text: String, adult: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text, color = if (adult) Color(0xFFFF8FB1) else Palette.Text, fontSize = 13.sp,
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(Palette.Chip).border(1.dp, if (adult) Color(0x55FF4D8D) else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
