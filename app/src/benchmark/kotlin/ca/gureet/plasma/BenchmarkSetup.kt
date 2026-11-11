// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import ca.gureet.plasma.core.model.HomeButtonChoice
import ca.gureet.plasma.core.model.LivePreview
import ca.gureet.plasma.core.model.ScreensaverAfter
import ca.gureet.plasma.core.model.SettingKeys
import ca.gureet.plasma.core.model.TvTurnsOn
import ca.gureet.plasma.feature.settings.AddSourceForm
import ca.gureet.plasma.feature.settings.SourceType
import ca.gureet.plasma.feature.settings.SyncStage
import ca.gureet.plasma.live.addSource
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * The benchmark build's setup (#127), which the benchmark module calls before it measures:
 * `adb shell content call --uri content://<package>.setup --method bigSource`.
 *
 * Plasma gets brief 6.7's big source ([MadeUpSource]), through Add a Source's own Connect, once,
 * and settings that keep the measures to what they're for: no first-time setup, no live picture
 * on Home (6.7 times that apart), no screensaver and no update checks. Home Button opens Live TV's
 * channel list and the TV turning on opens the Guide, so a benchmark can open either with an
 * intent, as Home and power-on do. It answers once the channels and the guide are in, with how
 * many there are, or why they aren't. Only the benchmark build has it.
 */
class BenchmarkSetup : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != BIG_SOURCE) return null
        val context = requireNotNull(context)
        return runBlocking {
            // A call can come while the app is still starting: the main thread has the graph once
            // the app's onCreate is done.
            val graph = withContext(Dispatchers.Main) { context.graph }
            withTimeout(SETUP_MS) { bigSource(graph) }
        }
    }

    private suspend fun bigSource(graph: AppGraph): Bundle {
        graph.choose(SettingKeys.LIVE_PREVIEW, LivePreview.Off.ordinal)
        graph.choose(SettingKeys.HOME_BUTTON, HomeButtonChoice.ChannelList.ordinal)
        graph.choose(SettingKeys.TV_ON, TvTurnsOn.Guide.ordinal)
        graph.choose(SettingKeys.SCREENSAVER, ScreensaverAfter.Never.ordinal)
        graph.choose(SettingKeys.CHECK_UPDATES, OFF)
        graph.finishSetup()
        if (graph.data.sources.sources.first().any { it.name == NAME }) return answer(READY)
        return MadeUpSource(Instant.now()).use { made ->
            val form = AddSourceForm(
                type = SourceType.M3U,
                name = NAME,
                server = made.playlist,
                username = "",
                password = "",
                primary = true,
                guide = made.guide,
            )
            val done = addSource(graph, form).progress.first { it.stage == SyncStage.Done || it.stage == SyncStage.Failed }
            when {
                done.stage == SyncStage.Failed -> answer(FAILED, problem = done.problem?.name)
                done.guideFailed -> {
                    // Without its guide the measures would be wrong: it goes, to be tried again.
                    graph.data.sources.sources.first().firstOrNull { it.name == NAME }?.let { graph.data.sources.remove(it.id) }
                    answer(FAILED, problem = "guide")
                }
                else -> answer(READY, channels = done.channels, programs = done.programs)
            }
        }
    }

    private fun answer(state: String, channels: Int? = null, programs: Int? = null, problem: String? = null) = Bundle().apply {
        putString("state", state)
        channels?.let { putInt("channels", it) }
        programs?.let { putInt("programs", it) }
        problem?.let { putString("problem", it) }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    private companion object {
        const val BIG_SOURCE = "bigSource"
        const val NAME = "Benchmark Source"
        const val READY = "ready"
        const val FAILED = "failed"

        /** A settings switch's off (switches are 0 for on). */
        const val OFF = 1

        /** Adding the source, on a slow emulator. */
        const val SETUP_MS = 15 * 60_000L
    }
}
