// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import ca.gureet.plasma.AppGraph
import ca.gureet.plasma.SleepState
import ca.gureet.plasma.core.data.Playable
import ca.gureet.plasma.feature.player.PlaybackInfo
import ca.gureet.plasma.feature.player.PlayerStatus
import ca.gureet.plasma.feature.player.SleepInfo
import ca.gureet.plasma.feature.player.UpNext
import ca.gureet.plasma.feature.player.VodEpisode
import ca.gureet.plasma.feature.player.VodPlayerActions
import ca.gureet.plasma.feature.player.VodPlayerScreen
import ca.gureet.plasma.feature.player.VodPlayerUiState
import ca.gureet.plasma.player.LiveStatus
import ca.gureet.plasma.player.PictureSize
import ca.gureet.plasma.player.StreamKeys
import java.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The player's key for a movie or an episode. */
internal fun vodKey(kind: Playable.Kind, id: String): String =
    if (kind == Playable.Kind.Movie) StreamKeys.movie(id) else StreamKeys.episode(id)

/**
 * Whether [status] is about [key], once the player has opened it: before that, where the player
 * is belongs to whatever played before.
 */
internal fun isPlaying(status: LiveStatus, key: String): Boolean =
    (status as? LiveStatus.Playing)?.channelId == key || (status as? LiveStatus.Ended)?.channelId == key

/**
 * Up Next (brief 4.9 and 5.9): the episode after this one, from 30 s before the end, unless Back
 * put it away; [countdown] once it has ended and Auto-Play Next Episode counts down.
 */
internal fun upNextOf(next: Playable?, positionMs: Long, lengthMs: Long, ended: Boolean, cancelled: Boolean, countdown: Int?): UpNext? {
    if (next == null || cancelled) return null
    val near = lengthMs > 0 && lengthMs - positionMs <= UP_NEXT_MS
    if (!ended && !near) return null
    return UpNext(VodEpisode(next.season ?: 1, next.number ?: 1, next.episodeTitle), next.art, countdown)
}

/**
 * A movie or an episode playing (brief 4.9's VOD variant and 5.9): from where it was left, or its
 * start; where it has got to is kept every 10 s and when it stops, and it's watched at 92 %. An
 * episode's end counts down to the next one when Auto-Play Next Episode is on; a movie's end goes
 * back to its page. [gone] shows when the title isn't in the catalog any more.
 */
@Composable
fun LiveVodPlayer(
    graph: AppGraph,
    kind: Playable.Kind,
    id: String,
    fromStart: Boolean,
    onBack: () -> Unit,
    onEpisode: (episodeId: String) -> Unit,
    gone: @Composable () -> Unit,
) {
    val vod = graph.data.vod
    val player = graph.player
    val key = vodKey(kind, id)
    val read by produceState<Read<Playable?>?>(null, kind, id) { value = Read(vod.playable(kind, id)) }
    val next by produceState<Playable?>(null, kind, id) { value = if (kind == Playable.Kind.Episode) vod.nextEpisode(id) else null }
    val item = read?.page
    val status by player.status.collectAsState()
    val position by player.position.collectAsState()
    val length by player.length.collectAsState()
    val tracks by player.tracks.collectAsState()
    val video by player.video.collectAsState()
    val sound by player.audio.collectAsState()
    val aspect by player.aspectRatio.collectAsState()
    val engine by player.engineId.collectAsState()
    val afterFallback by player.afterFallback.collectAsState()
    val sleep by graph.sleep.state.collectAsState()
    val passing by remember(graph) { graph.passingThrough() }.collectAsState(null)
    var paused by remember(key) { mutableStateOf(false) }
    var size by remember(key) { mutableStateOf(PictureSize.Fit) }
    var cancelled by remember(key) { mutableStateOf(false) }
    var countdown by remember(key) { mutableStateOf<Int?>(null) }
    val ended = (status as? LiveStatus.Ended)?.channelId == key
    val playing by rememberUpdatedState(item)

    /** Keeps where it has got to (brief 5.9), once the player has it open. */
    fun save() {
        val title = playing ?: return
        val now = player.status.value
        if (!isPlaying(now, key)) return
        val total = player.length.value
        val at = if (now is LiveStatus.Ended) total else player.position.value
        graph.scope.launch { runCatching { vod.savePlace(title, Duration.ofMillis(at), Duration.ofMillis(total)) } }
    }

    // It plays once it's read: from where it was left, or its start.
    LaunchedEffect(item) {
        val title = item ?: return@LaunchedEffect
        val start = if (fromStart) Duration.ZERO else vod.resumeFrom(title)
        player.playVod(key, start.toMillis())
    }
    // Full screen, the TV may switch to the movie's frame rate (Match Frame Rate, brief 5.4.4).
    DisposableEffect(graph) {
        player.setFullScreen(true)
        onDispose { player.setFullScreen(false) }
    }
    LaunchedEffect(key) {
        while (true) {
            delay(SAVE_EVERY_MS)
            save()
        }
    }
    DisposableEffect(key) { onDispose { save() } }
    // The end: an episode counts down to the next, a movie goes back to its page.
    LaunchedEffect(ended, next, cancelled) {
        if (!ended) return@LaunchedEffect
        save()
        val following = next
        if (following == null) {
            onBack()
            return@LaunchedEffect
        }
        if (cancelled || !graph.choices.autoPlayNext) return@LaunchedEffect
        for (left in COUNTDOWN_SECONDS downTo 1) {
            countdown = left
            delay(SECOND_MS)
        }
        onEpisode(following.id)
    }

    val current = read ?: return
    val title = current.page ?: return gone()
    val words = rememberVodWords()
    val ours = isPlaying(status, key) || (status as? LiveStatus.Opening)?.channelId == key || (status as? LiveStatus.Failed)?.channelId == key
    val playerStatus = if (ended) PlayerStatus.Playing else statusFor(status, key)
    val state = VodPlayerUiState(
        title = title.title,
        episode = if (title.kind == Playable.Kind.Episode) VodEpisode(title.season ?: 1, title.number ?: 1, title.episodeTitle) else null,
        meta = listOfNotNull(title.year?.toString()) + title.genres.take(GENRES),
        badges = badges(null, title.video, title.audio, words),
        art = title.art,
        key = key,
        positionMs = if (ours) position else 0,
        lengthMs = (if (ours) length else 0).takeIf { it > 0 } ?: title.duration?.toMillis() ?: 0,
        paused = paused,
        status = playerStatus,
        playback = PlaybackInfo(
            behindLiveMs = 0,
            paused = paused,
            tracks = tracks,
            engine = engineName(engine),
            nextEngine = player.nextEngine(engine)?.let { engineName(it) }.orEmpty(),
            format = streamFormat(video, tracks.playingAudio, sound, passthrough = passing == key),
            bitrate = "",
            buffer = "",
            source = "",
            nextSource = null,
            pictureSize = size,
            pictureAspect = aspect,
            sleep = (sleep as? SleepState.Set)?.let { SleepInfo(it.choice, it.at) },
            health = streamHealth(playerStatus, afterFallback),
        ),
        upNext = upNextOf(next, position, length, ended, cancelled, countdown),
    )
    val following = next
    VodPlayerScreen(
        state = state,
        actions = remember(key, following) {
            VodPlayerActions(
                onBack = onBack,
                onPause = { pause ->
                    paused = pause
                    player.setPaused(pause)
                },
                onSeek = player::seekTo,
                onFromStart = {
                    paused = false
                    // Playing: a jump; ended or failed: it opens again from the start.
                    if ((player.status.value as? LiveStatus.Playing)?.channelId == key) {
                        player.setPaused(false)
                        player.seekTo(0)
                    } else {
                        player.playVod(key, 0)
                    }
                },
                onNext = following?.let { episode ->
                    {
                        save()
                        onEpisode(episode.id)
                    }
                },
                onCancelNext = {
                    cancelled = true
                    countdown = null
                },
                onRetry = player::retry,
                onSwitchPlayer = player::switchEngine,
                onAudio = player::selectAudio,
                onSubtitles = player::selectSubtitles,
                onPictureSize = { size = it },
                onSleepTimer = graph.sleep::set,
            )
        },
    )
}

/** Where a movie or episode has got to is kept this often while it plays (brief 5.9). */
private const val SAVE_EVERY_MS = 10_000L

/** Up Next shows for an episode's last 30 s. */
private const val UP_NEXT_MS = 30_000L

/** The next episode plays 10 s after one ends (brief 5.9). */
private const val COUNTDOWN_SECONDS = 10
private const val SECOND_MS = 1_000L

/** A movie's genres in the player, after its year. */
private const val GENRES = 2
