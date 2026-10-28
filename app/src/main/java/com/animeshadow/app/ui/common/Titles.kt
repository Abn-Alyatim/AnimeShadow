package com.animeshadow.app.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding

/**
 * v1.3.1 (Madyan): titles show the ENGLISH title on top and the Japanese (romaji, else native) one under it.
 * When both are the same title (e.g. "One Piece" / "ONE PIECE") it is shown once. Falls back to whichever exists.
 */
fun titlePair(english: String?, romaji: String?, native: String?): Pair<String, String?> {
    val top = english?.takeIf { it.isNotBlank() } ?: romaji?.takeIf { it.isNotBlank() } ?: native.orEmpty()
    val japanese = romaji?.takeIf { it.isNotBlank() } ?: native?.takeIf { it.isNotBlank() }
    val below = japanese?.takeIf { sameTitleKey(it) != sameTitleKey(top) }
    return top to below
}

private fun sameTitleKey(t: String) = t.lowercase().filter { it.isLetterOrDigit() }

@Composable
fun SecondTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
        color = Color(0xFFBDBDBD), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Left,
        modifier = modifier.padding(bottom = 2.dp).testTag("second_title"),
    )
}
