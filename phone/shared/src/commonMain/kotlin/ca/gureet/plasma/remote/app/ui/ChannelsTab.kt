// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.plasma.remote.app.resources.Res
import ca.gureet.plasma.remote.app.resources.counts_from_one
import ca.gureet.plasma.remote.app.resources.list_count
import ca.gureet.plasma.remote.app.resources.search_channels
import ca.gureet.plasma.remote.app.tv.TvLink
import ca.gureet.plasma.remote.protocol.RemoteChannel
import ca.gureet.plasma.remote.protocol.RemoteList
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The Channels tab (board 33, brief 4.33): a search over the list that's shown, the TV's lists as
 * chips, "Favorites · 24" and whether its numbers count from 1, then the channels with their
 * logos and what's on now. The channel that plays is lit, with a speaker. A tap tunes, in that
 * list. Channels come 200 at a time, the next ones as the list nears its end.
 */
@Composable
fun ChannelsTab(tv: TvLink, playingId: String?) {
    var lists by remember { mutableStateOf<List<RemoteList>>(emptyList()) }
    var shownList by remember { mutableStateOf<String?>(null) }
    var channels by remember { mutableStateOf<List<RemoteChannel>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val rows = rememberLazyListState()

    suspend fun load(listId: String?, from: Int) {
        val answer = tv.lists(listId, from) ?: return
        lists = answer.lists
        channels = if (from == 0 || answer.list != shownList) answer.channels else channels + answer.channels
        shownList = answer.list
    }

    LaunchedEffect(tv) { load(null, 0) }

    val current = lists.firstOrNull { it.id == shownList }
    // The next page, as the list nears the end of what's come.
    val nearEnd by remember { derivedStateOf { rows.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 } }
    LaunchedEffect(nearEnd, channels.size, current) {
        val list = current ?: return@LaunchedEffect
        if (channels.size < list.count && channels.size % TvLink.PAGE == 0 && channels.isNotEmpty() && nearEnd >= channels.size - 20) {
            load(list.id, channels.size)
        }
    }

    val words = query.trim().lowercase()
    val shown = if (words.isEmpty()) channels else channels.filter { it.name.lowercase().contains(words) || it.number == words }
    // The numbers line up, however many digits the list's longest has.
    val digits = maxOf(2, channels.maxOfOrNull { it.number.length } ?: 2)

    LazyColumn(Modifier.fillMaxSize(), state = rows, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 20.dp)) {
        // The rows reach 4 dp past the search field on each side, as on the web page.
        item(key = "search") { Box(Modifier.padding(horizontal = 4.dp)) { SearchField(query, stringResource(Res.string.search_channels)) { query = it } } }
        item(key = "chips") {
            Chips(lists.map { Chip(it.id, it.name) }, shownList, Modifier.padding(start = 4.dp)) { id ->
                scope.launch {
                    load(id, 0)
                    rows.scrollToItem(0)
                }
            }
        }
        item(key = "meta") {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(current?.let { stringResource(Res.string.list_count, it.name, it.count) }.orEmpty(), style = textStyle(13.sp, color = RemoteColors.Text3))
                if (current?.countsFromOne == true) BasicText(stringResource(Res.string.counts_from_one), style = textStyle(13.sp, color = RemoteColors.Text3))
            }
        }
        items(shown, key = { it.id }) { channel ->
            ChannelRow(channel, digits, playing = channel.id == playingId || (playingId == null && channel.playing)) { tv.tune(channel.id, shownList) }
        }
    }
}

/** A search over the rows that have come, as the web page's: [hint] until something's typed. */
@Composable
internal fun SearchField(query: String, hint: String, onQuery: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(RemoteColors.Surface1).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(RemoteIcons.Search, 18.dp, RemoteColors.Text3)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) BasicText(hint, style = textStyle(16.sp, color = RemoteColors.Text3))
            BasicTextField(
                query,
                onQuery,
                Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = textStyle(16.sp),
                cursorBrush = SolidColor(RemoteColors.Text),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
            )
        }
    }
}

/** A chip across the top of a tab: a list of channels, or a category of titles, which may be [locked]. */
internal data class Chip(val id: String, val name: String, val locked: Boolean = false)

@Composable
internal fun Chips(chips: List<Chip>, shown: String?, modifier: Modifier = Modifier, onChip: (String) -> Unit) {
    Row(modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { chip ->
            val on = chip.id == shown
            val color = if (on) RemoteColors.Bg else RemoteColors.Soft
            Row(
                Modifier.height(34.dp).clip(RoundedCornerShape(10.dp)).background(if (on) RemoteColors.Text else RemoteColors.Surface1).tap(chip.name) { onChip(chip.id) }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (chip.locked) Icon(RemoteIcons.Lock, 14.dp, color)
                BasicText(chip.name, style = textStyle(14.sp, if (on) FontWeight.SemiBold else FontWeight.Medium, color))
            }
        }
    }
}

/** "1 · [logo] · Arena Sports 1 / Harbour City v Redmoor ▬": 60 dp, lit while it plays. */
@Composable
private fun ChannelRow(channel: RemoteChannel, digits: Int, playing: Boolean, onTune: () -> Unit) {
    val pressed = rememberPressed()
    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp).height(60.dp).clip(RoundedCornerShape(12.dp))
            .background(if (playing) RemoteColors.Line else RemoteColors.Bg).pressedLook(pressed)
            .tap("${channel.number} ${channel.name}", pressed = pressed, action = onTune)
            .padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(channel.number, Modifier.width(maxOf(22, digits * 10).dp), style = textStyle(16.sp, FontWeight.SemiBold, RemoteColors.Text2).copy(textAlign = TextAlign.End))
        Box(Modifier.size(72.dp, 36.dp).clip(RoundedCornerShape(7.dp)).background(RemoteColors.Plate), contentAlignment = Alignment.Center) {
            ChannelLogo(channel.id, maxWidth = 58.dp, maxHeight = 20.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BasicText(channel.name, style = textStyle(15.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (channel.now != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(channel.now.orEmpty(), Modifier.weight(1f, fill = false), style = textStyle(12.5.sp, color = RemoteColors.Text3), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    channel.progress?.let { MiniProgress(it) }
                }
            }
        }
        if (playing) Icon(RemoteIcons.Speaker, 18.dp, RemoteColors.Text)
    }
}

/** How far into the program on now: 34 × 3 dp. */
@Composable
private fun MiniProgress(progress: Float) {
    Box(Modifier.size(34.dp, 3.dp).clip(RoundedCornerShape(2.dp)).background(RemoteColors.MiniTrack)) {
        Box(Modifier.width(34.dp * progress.coerceIn(0f, 1f)).height(3.dp).clip(RoundedCornerShape(2.dp)).background(RemoteColors.Text3))
    }
}
