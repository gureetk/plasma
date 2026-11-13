// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import ca.gureet.plasma.core.design.FocusMotionForBenchmarks
import ca.gureet.plasma.core.model.HomeButtonChoice
import ca.gureet.plasma.core.model.LivePreview
import ca.gureet.plasma.core.model.ScreensaverAfter
import ca.gureet.plasma.core.model.SettingKeys
import ca.gureet.plasma.core.model.SettingsChoices
import ca.gureet.plasma.core.model.TvTurnsOn
import ca.gureet.plasma.core.design.component.CountedListForBenchmarks
import ca.gureet.plasma.feature.guide.GuideCellsForBenchmarks
import ca.gureet.plasma.feature.settings.AddSourceForm
import ca.gureet.plasma.feature.settings.SourceSync
import ca.gureet.plasma.feature.settings.SourceType
import ca.gureet.plasma.feature.settings.SyncStage
import ca.gureet.plasma.live.addSource
import java.io.File
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * The benchmark build's setup (#127), which the benchmark module calls before it measures:
 * `adb shell content call --uri content://<package>.setup --method bigSource`.
 *
 * Plasma gets brief 6.7's big source ([MadeUpSource]), with a catalog of movies and series,
 * through Add a Source's own Connect, once, and settings that keep the measures to what they're
 * for: no first-time setup, no live picture on Home (6.7 times that apart), no screensaver, no
 * update checks, and Lighter Effects off, as it is on a new install. Home Button opens Live TV's channel list and the TV turning on opens the Guide,
 * so a benchmark can open either with an intent, as Home and power-on do. It answers once the
 * channels, the guide, the movies and the series are in, with how many there are, or why they
 * aren't. Only the benchmark build has it, and it starts the made-up source as Plasma starts,
 * since Plasma loads the posters from it as they come on screen.
 */
class BenchmarkSetup : ContentProvider() {
    override fun onCreate(): Boolean {
        val context = requireNotNull(context)
        // Before anything is composed: one look for a file, which a start doesn't notice.
        FocusMotionForBenchmarks.asBefore = asBeforeMark(context).exists()
        GuideCellsForBenchmarks.step = guideCellsMarked(context)
        CountedListForBenchmarks.everyRow = everyRowMark(context).exists()
        thread(name = "MadeUpSource start", isDaemon = true) { MadeUpSource.start(context) }
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != BIG_SOURCE && method != LIGHTER_EFFECTS && method != FOCUS_MOTION && method != GUIDE_CELLS && method != CATEGORY_ROWS) return null
        val context = requireNotNull(context)
        if (method == FOCUS_MOTION) return focusMotion(context, asBefore = arg == AS_BEFORE_ARG)
        if (method == GUIDE_CELLS) return guideCells(context, arg)
        if (method == CATEGORY_ROWS) return categoryRows(context, every = arg == EVERY_ARG)
        return runBlocking {
            // A call can come while the app is still starting: the main thread has the graph once
            // the app's onCreate is done.
            val graph = withContext(Dispatchers.Main) { context.graph }
            if (method == LIGHTER_EFFECTS) withTimeout(STORED_MS) { lighterEffects(graph, on = arg == ON_ARG) } else withTimeout(SETUP_MS) { bigSource(graph) }
        }
    }

    /**
     * Settings › Appearance › Lighter Effects, [on] or off: `--method lighterEffects --arg on`. A
     * benchmark that measures with it on turns it on after [bigSource], which turns it off. It
     * answers once the choice is stored, since the benchmark stops Plasma straight after.
     */
    private suspend fun lighterEffects(graph: AppGraph, on: Boolean): Bundle {
        graph.choose(SettingKeys.LIGHTER_EFFECTS, if (on) ON else OFF)
        graph.storedChoices.first { SettingsChoices(it.orEmpty()).lighterEffects == on }
        return Bundle().apply { putString("lighterEffects", if (on) ON_ARG else "off") }
    }

    /**
     * The focus motion every surface has, as it was before it was set up on demand, or back as it
     * is: `--method focusMotion --arg asBefore`, or any other argument. A benchmark that measures a
     * screen as before turns it on after [bigSource], which turns it off, and Plasma is started
     * afresh before it's measured: the choice is a file that [onCreate] looks for.
     */
    private fun focusMotion(context: Context, asBefore: Boolean): Bundle {
        val mark = asBeforeMark(context)
        if (asBefore) mark.createNewFile() else mark.delete()
        FocusMotionForBenchmarks.asBefore = mark.exists()
        return Bundle().apply { putString("focusMotion", if (mark.exists()) AS_BEFORE_ARG else "onDemand") }
    }

    private fun asBeforeMark(context: Context) = File(context.filesDir, "focus-motion-as-before")

    /**
     * A column of groups or categories with every row made, as before #227, or back as it is:
     * `--method categoryRows --arg every`, or any other argument. Kept and read as [focusMotion]'s
     * choice is: a benchmark sets it after [bigSource], which puts it back, and Plasma is started
     * afresh before it's measured.
     */
    private fun categoryRows(context: Context, every: Boolean): Bundle {
        val mark = everyRowMark(context)
        if (every) mark.createNewFile() else mark.delete()
        CountedListForBenchmarks.everyRow = mark.exists()
        return Bundle().apply { putString("categoryRows", if (mark.exists()) EVERY_ARG else "nearItsWindow") }
    }

    private fun everyRowMark(context: Context) = File(context.filesDir, "category-rows-every")

    /**
     * The Guide as it was at a step before the last, or back as it is: `--method guideCells --arg
     * byPlace` (as on `main`), `placingThemselves` (#222 alone), `placedByTheRow` (#223 without a
     * cell handed on) or `handedOn` (#223); any other argument is as built, `asBuilt`. As
     * [focusMotion]: a benchmark sets it after [bigSource], which puts it back, and Plasma is
     * started afresh before it's measured.
     */
    private fun guideCells(context: Context, arg: String?): Bundle {
        val step = GuideCellsForBenchmarks.Step.entries.firstOrNull { it.arg == arg } ?: GuideCellsForBenchmarks.Step.AsBuilt
        val mark = guideCellsMark(context)
        if (step == GuideCellsForBenchmarks.Step.AsBuilt) mark.delete() else mark.writeText(step.arg)
        // The mark of the benchmarks' builds before these, which a set installed over them finds.
        File(context.filesDir, "guide-cells-by-place").delete()
        GuideCellsForBenchmarks.step = guideCellsMarked(context)
        return Bundle().apply { putString("guideCells", GuideCellsForBenchmarks.step.arg) }
    }

    private fun guideCellsMark(context: Context) = File(context.filesDir, "guide-cells")

    private fun guideCellsMarked(context: Context): GuideCellsForBenchmarks.Step {
        val marked = runCatching { guideCellsMark(context).readText().trim() }.getOrNull()
        return GuideCellsForBenchmarks.Step.entries.firstOrNull { it.arg == marked } ?: GuideCellsForBenchmarks.Step.AsBuilt
    }

    /** A step of the Guide as the setup's argument names it: `byPlace` for [GuideCellsForBenchmarks.Step.ByPlace]. */
    private val GuideCellsForBenchmarks.Step.arg get() = name.replaceFirstChar { it.lowercase() }

    private suspend fun bigSource(graph: AppGraph): Bundle {
        focusMotion(requireNotNull(context), asBefore = false)
        guideCells(requireNotNull(context), arg = null)
        categoryRows(requireNotNull(context), every = false)
        graph.choose(SettingKeys.LIVE_PREVIEW, LivePreview.Off.ordinal)
        graph.choose(SettingKeys.LIGHTER_EFFECTS, OFF)
        graph.choose(SettingKeys.HOME_BUTTON, HomeButtonChoice.ChannelList.ordinal)
        graph.choose(SettingKeys.TV_ON, TvTurnsOn.Guide.ordinal)
        graph.choose(SettingKeys.SCREENSAVER, ScreensaverAfter.Never.ordinal)
        graph.choose(SettingKeys.CHECK_UPDATES, OFF)
        graph.finishSetup()
        MadeUpSource.start(requireNotNull(context))
        val sources = graph.data.sources.sources.first()
        // The playlist the big source was before it had movies and series goes.
        sources.filter { it.name == PLAYLIST_NAME }.forEach { graph.data.sources.remove(it.id) }
        if (sources.any { it.name == NAME }) return answer(READY)
        val form = AddSourceForm(
            type = SourceType.Xtream,
            name = NAME,
            server = MadeUpSource.BASE,
            username = MadeUpSource.USERNAME,
            password = MadeUpSource.PASSWORD,
            primary = true,
        )
        val done = addSource(graph, form).progress.first { it.stage == SyncStage.Done || it.stage == SyncStage.Failed }
        return when {
            done.stage == SyncStage.Failed -> answer(FAILED, problem = done.problem?.name)
            done.guideFailed || done.libraryFailed -> {
                // Without its guide, or its movies and series, the measures would be wrong: it goes,
                // to be tried again.
                graph.data.sources.sources.first().firstOrNull { it.name == NAME }?.let { graph.data.sources.remove(it.id) }
                answer(FAILED, problem = if (done.guideFailed) "guide" else "library")
            }
            else -> answer(READY, done)
        }
    }

    private fun answer(state: String, done: SourceSync? = null, problem: String? = null) = Bundle().apply {
        putString("state", state)
        done?.channels?.let { putInt("channels", it) }
        done?.programs?.let { putInt("programs", it) }
        done?.movies?.let { putInt("movies", it) }
        done?.series?.let { putInt("series", it) }
        problem?.let { putString("problem", it) }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    private companion object {
        const val BIG_SOURCE = "bigSource"
        const val LIGHTER_EFFECTS = "lighterEffects"
        const val FOCUS_MOTION = "focusMotion"
        const val AS_BEFORE_ARG = "asBefore"
        const val GUIDE_CELLS = "guideCells"
        const val CATEGORY_ROWS = "categoryRows"
        const val EVERY_ARG = "every"
        const val ON_ARG = "on"
        const val NAME = "Benchmark Server"

        /** The big source's name while it was a playlist, without movies and series. */
        const val PLAYLIST_NAME = "Benchmark Source"
        const val READY = "ready"
        const val FAILED = "failed"

        /** A settings switch's on and off (switches are 0 for on). */
        const val ON = 0
        const val OFF = 1

        /** Adding the source, on a slow emulator. */
        const val SETUP_MS = 15 * 60_000L

        /** For a choice to be stored. */
        const val STORED_MS = 10_000L
    }
}
