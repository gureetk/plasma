// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.plasma.remote.app.resources.Res
import ca.gureet.plasma.remote.app.resources.category_locked
import ca.gureet.plasma.remote.app.resources.list_count
import ca.gureet.plasma.remote.app.resources.nothing_here
import ca.gureet.plasma.remote.app.resources.play
import ca.gureet.plasma.remote.app.resources.search_movies
import ca.gureet.plasma.remote.app.resources.search_series
import ca.gureet.plasma.remote.app.tv.TvLink
import ca.gureet.plasma.remote.protocol.RemoteCategory
import ca.gureet.plasma.remote.protocol.RemoteTitle
import ca.gureet.plasma.remote.protocol.RemoteTitles
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The Movies or Series tab (#230), made the way Channels is: a search over the category shown,
 * the TV's categories as chips (a locked one with a lock, which opens only on the TV), "Thrillers ·
 * 250", then the titles with their posters, year and rating, and how far in on Continue Watching.
 * A tap opens the title's page on the TV; a hold plays it. Titles come 200 at a time.
 */
@Composable
fun TitlesTab(tv: TvLink, kind: String) {
    var categories by remember(kind) { mutableStateOf<List<RemoteCategory>>(emptyList()) }
    var shown by remember(kind) { mutableStateOf<String?>(null) }
    var titles by remember(kind) { mutableStateOf<List<RemoteTitle>>(emptyList()) }
    var query by remember(kind) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val rows = rememberLazyListState()

    suspend fun load(categoryId: String?, from: Int) {
        val answer = tv.titles(kind, categoryId, from) ?: return
        categories = answer.categories
        titles = if (from == 0 || answer.category != shown) answer.titles else titles + answer.titles
        shown = answer.category
    }

    LaunchedEffect(tv, kind) { load(null, 0) }

    val current = categories.firstOrNull { it.id == shown }
    val nearEnd by remember { derivedStateOf { rows.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 } }
    LaunchedEffect(nearEnd, titles.size, current) {
        val category = current ?: return@LaunchedEffect
        if (!category.locked && titles.size < category.count && titles.size % TvLink.PAGE == 0 && titles.isNotEmpty() && nearEnd >= titles.size - 20) {
            load(category.id, titles.size)
        }
    }

    val words = query.trim().lowercase()
    val found = if (words.isEmpty()) titles else titles.filter { it.name.lowercase().contains(words) }
    val hint = stringResource(if (kind == RemoteTitles.SERIES) Res.string.search_series else Res.string.search_movies)

    LazyColumn(Modifier.fillMaxSize(), state = rows, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 20.dp)) {
        item(key = "search") { Box(Modifier.padding(horizontal = 4.dp)) { SearchField(query, hint) { query = it } } }
        item(key = "chips") {
            Chips(categories.map { Chip(it.id, it.name, it.locked) }, shown, Modifier.padding(start = 4.dp)) { id ->
                scope.launch {
                    load(id, 0)
                    rows.scrollToItem(0)
                }
            }
        }
        item(key = "meta") {
            BasicText(
                current?.let { stringResource(Res.string.list_count, it.name, it.count) }.orEmpty(),
                Modifier.padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
                style = textStyle(13.sp, color = RemoteColors.Text3),
            )
        }
        when {
            current?.locked == true -> item(key = "locked") { Line(stringResource(Res.string.category_locked, current.name)) }
            current != null && titles.isEmpty() -> item(key = "nothing") { Line(stringResource(Res.string.nothing_here)) }
        }
        items(found, key = { it.id }) { title ->
            TitleRow(kind, title, onOpen = { tv.open(kind, title.id, play = false) }, onPlay = { tv.open(kind, title.id, play = true) })
        }
    }
}

@Composable
private fun Line(text: String) =
    BasicText(text, Modifier.padding(horizontal = 8.dp, vertical = 6.dp), style = textStyle(14.sp, color = RemoteColors.Text2).copy(lineHeight = 19.sp))

/** "[poster] The Last Lighthouse / 2025 · ★ 7.8 ▬": 84 dp, the poster 48 × 72. */
@Composable
private fun TitleRow(kind: String, title: RemoteTitle, onOpen: () -> Unit, onPlay: () -> Unit) {
    val pressed = rememberPressed()
    val details = listOfNotNull(title.year?.toString(), title.rating?.let { "★ " + oneDecimal(it) }).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp).height(84.dp).clip(RoundedCornerShape(12.dp)).pressedLook(pressed)
            .tapOrHold(title.name, stringResource(Res.string.play), pressed, onTap = onOpen, onHold = onPlay)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TitlePoster(kind, title.id, title.name, title.poster)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(title.name, style = textStyle(15.sp, FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (details.isNotEmpty()) BasicText(details, style = textStyle(12.5.sp, color = RemoteColors.Text3), maxLines = 1)
            title.progress?.let { HowFar(it) }
        }
    }
}

/** How far into a title, on Continue Watching: 60 × 3 dp. */
@Composable
private fun HowFar(progress: Float) {
    Box(Modifier.padding(top = 2.dp).size(60.dp, 3.dp).clip(RoundedCornerShape(2.dp)).background(RemoteColors.MiniTrack)) {
        Box(Modifier.width(60.dp * progress.coerceIn(0f, 1f)).height(3.dp).clip(RoundedCornerShape(2.dp)).background(RemoteColors.Text2))
    }
}

/** 7.4, as the TV shows a rating. */
private fun oneDecimal(value: Float): String {
    val tenths = (value * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}
