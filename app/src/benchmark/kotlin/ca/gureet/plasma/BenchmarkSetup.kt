// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import ca.gureet.plasma.core.design.FocusMotionForBenchmarks
import ca.gureet.plasma.core.design.KeptScreenForBenchmarks
import ca.gureet.plasma.core.model.GuideDayForBenchmarks
import ca.gureet.plasma.core.model.HomeButtonChoice
import ca.gureet.plasma.core.model.LivePreview
import ca.gureet.plasma.core.model.ReadsForBenchmarks
import ca.gureet.plasma.core.model.ScreensaverAfter
import ca.gureet.plasma.core.model.SettingKeys
import ca.gureet.plasma.core.model.SettingsChoices
import ca.gureet.plasma.core.model.TvTurnsOn
import ca.gureet.plasma.core.design.component.CountedListForBenchmarks
import ca.gureet.plasma.feature.guide.GuideCellsForBenchmarks
import ca.gureet.plasma.live.LiveTvRowsForBenchmarks
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
        LiveTvRowsForBenchmarks.atOpening = atOpeningMark(context).exists()
        HomeForBenchmarks.madeAgain = homeMadeAgainMark(context).exists()
        HomeForBenchmarks.belowAFrame = homeBelowMarked(context)
        KeptScreenForBenchmarks.notPlacedUnder = homeNotPlacedMark(context).exists()
        KeptScreenForBenchmarks.layersHeldUnder = homeLayersHeldMark(context).exists()
        MainThreadSamples.set(sampledMark(context).exists())
        ReadsForBenchmarks.way = readsMarked(context)
        ReadsForBenchmarks.wholeGuideBesideFirstLook = wholeGuideBesideFirstLookMark(context).exists()
        ReadsForBenchmarks.worldAskedAhead = worldAskedAheadMark(context).exists()
        GuideDayForBenchmarks.inOnePiece = guideDayInOnePieceMark(context).exists()
        thread(name = "MadeUpSource start", isDaemon = true) { MadeUpSource.start(context) }
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != BIG_SOURCE && method != LIGHTER_EFFECTS && method != FOCUS_MOTION && method != GUIDE_CELLS && method != CATEGORY_ROWS && method != LIVE_TV_ROWS && method != HOME && method != HOME_BELOW && method != MAIN_THREAD && method != READS && method != WHOLE_GUIDE && method != WORLD && method != GUIDE_DAY) return null
        val context = requireNotNull(context)
        if (method == READS) return reads(context, arg)
        if (method == WHOLE_GUIDE) return wholeGuide(context, besideFirstLook = arg == BESIDE_FIRST_LOOK_ARG)
        if (method == WORLD) return world(context, askedAhead = arg == ASKED_AHEAD_ARG)
        if (method == GUIDE_DAY) return guideDay(context, inOnePiece = arg == IN_ONE_PIECE_ARG)
        if (method == MAIN_THREAD) return mainThread(context, sampled = arg == SAMPLED_ARG)
        if (method == FOCUS_MOTION) return focusMotion(context, asBefore = arg == AS_BEFORE_ARG)
        if (method == GUIDE_CELLS) return guideCells(context, arg)
        if (method == CATEGORY_ROWS) return categoryRows(context, every = arg == EVERY_ARG)
        if (method == LIVE_TV_ROWS) return liveTvRows(context, atOpening = arg == AT_OPENING_ARG)
        if (method == HOME) return home(context, arg)
        if (method == HOME_BELOW) return homeBelow(context, arg)
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
     * Live TV's rows made at once as it opens, with nothing kept made, as before #229, or back as
     * it is: `--method liveTvRows --arg atOpening`, or any other argument. Kept and read as
     * [focusMotion]'s choice is: a benchmark sets it after [bigSource], which puts it back, and
     * Plasma is started afresh before it's measured.
     */
    private fun liveTvRows(context: Context, atOpening: Boolean): Bundle {
        val mark = atOpeningMark(context)
        if (atOpening) mark.createNewFile() else mark.delete()
        LiveTvRowsForBenchmarks.atOpening = mark.exists()
        return Bundle().apply { putString("liveTvRows", if (mark.exists()) AT_OPENING_ARG else "keptMade") }
    }

    private fun atOpeningMark(context: Context) = File(context.filesDir, "live-tv-rows-at-opening")

    /**
     * Home as it was before it was kept made (#234), or as it was first kept, or back as it is:
     * `--method home --arg madeAgain` has it made again from nothing each time it comes back from
     * under another screen; `--arg notPlaced` keeps it made and not placed while it's under, so
     * that it's placed and recorded again as it comes back; `--arg layersHeld` keeps it laid out
     * with its drawing left recorded behind a layer at alpha 0, so that the layers in it keep
     * their textures; any other argument keeps it laid out and not drawn, as it is. Kept and read
     * as [focusMotion]'s choice is: a benchmark sets it after [bigSource], which puts it back, and
     * Plasma is started afresh before it's measured.
     */
    private fun home(context: Context, arg: String?): Bundle {
        val madeAgain = homeMadeAgainMark(context)
        val notPlaced = homeNotPlacedMark(context)
        val layersHeld = homeLayersHeldMark(context)
        if (arg == MADE_AGAIN_ARG) madeAgain.createNewFile() else madeAgain.delete()
        if (arg == NOT_PLACED_ARG) notPlaced.createNewFile() else notPlaced.delete()
        if (arg == LAYERS_HELD_ARG) layersHeld.createNewFile() else layersHeld.delete()
        HomeForBenchmarks.madeAgain = madeAgain.exists()
        KeptScreenForBenchmarks.notPlacedUnder = notPlaced.exists()
        KeptScreenForBenchmarks.layersHeldUnder = layersHeld.exists()
        return Bundle().apply {
            putString("home", if (madeAgain.exists()) MADE_AGAIN_ARG else if (notPlaced.exists()) NOT_PLACED_ARG else if (layersHeld.exists()) LAYERS_HELD_ARG else "kept")
        }
    }

    private fun homeLayersHeldMark(context: Context) = File(context.filesDir, "home-layers-held-under")

    /**
     * What's below Home's screen edge (its rows, and each row's tiles) made all at once in the
     * frame its contents come, as before it was made in turns, or two pieces at a frame, or back
     * as it is, one at a frame: `--method homeBelow --arg atOnce`, `--arg twoAFrame`, or any other
     * argument. Kept and read as [focusMotion]'s choice is: a benchmark sets it after [bigSource],
     * which puts it back, and Plasma is started afresh before it's measured.
     */
    private fun homeBelow(context: Context, arg: String?): Bundle {
        val mark = homeBelowMark(context)
        if (arg == AT_ONCE_ARG || arg == TWO_A_FRAME_ARG) mark.writeText(arg) else mark.delete()
        HomeForBenchmarks.belowAFrame = homeBelowMarked(context)
        return Bundle().apply {
            putString("homeBelow", when (HomeForBenchmarks.belowAFrame) { 0 -> AT_ONCE_ARG; 2 -> TWO_A_FRAME_ARG; else -> "oneAFrame" })
        }
    }

    private fun homeBelowMark(context: Context) = File(context.filesDir, "home-below")

    private fun homeBelowMarked(context: Context): Int = when (runCatching { homeBelowMark(context).readText().trim() }.getOrNull()) {
        AT_ONCE_ARG -> 0
        TWO_A_FRAME_ARG -> 2
        else -> 1
    }

    private fun homeMadeAgainMark(context: Context) = File(context.filesDir, "home-made-again")

    /**
     * The main thread's stack written into the trace every few milliseconds ([MainThreadSamples]),
     * or not: `--method mainThread --arg sampled`, or any other argument. For a run that looks at
     * what the main thread does between frames, not for one that measures: taking a stack stops
     * the thread for a moment. Kept and read as [focusMotion]'s choice is, and it also starts or
     * stops at once.
     */
    private fun mainThread(context: Context, sampled: Boolean): Bundle {
        val mark = sampledMark(context)
        if (sampled) mark.createNewFile() else mark.delete()
        MainThreadSamples.set(mark.exists())
        return Bundle().apply { putString("mainThread", if (mark.exists()) SAMPLED_ARG else "left") }
    }

    private fun sampledMark(context: Context) = File(context.filesDir, "main-thread-sampled")

    /**
     * What the screens read worked out another way than as built, or back as it is: `--method
     * reads --arg asBefore` (where it's collected, which for a screen is the main thread, and the
     * parental words' patterns made as the words are, as both were before they were moved),
     * `offMainFromTheStart` (moved, with nothing of Home's held until its world is in, as it was
     * first moved) or `heldUntilTheWorld` (moved, and what Home reads besides its world not read
     * until the world is in); any other argument is as built, `whereCollectedUntilTheWorld`
     * (where it's collected for what starts before the first world is in, moved for what starts
     * after). Kept and read as [focusMotion]'s choice is: the flows are made as
     * Plasma starts, so a benchmark sets it after [bigSource], which puts it back, and Plasma is
     * started afresh before it's measured.
     */
    private fun reads(context: Context, arg: String?): Bundle {
        val way = ReadsForBenchmarks.Way.entries.firstOrNull { it.arg == arg } ?: ReadsForBenchmarks.Way.WhereCollectedUntilTheWorld
        val mark = readsMark(context)
        if (way == ReadsForBenchmarks.Way.WhereCollectedUntilTheWorld) mark.delete() else mark.writeText(way.arg)
        // The mark of the benchmarks' builds before these, which a set installed over them finds.
        File(context.filesDir, "reads-as-before").delete()
        return Bundle().apply { putString("reads", readsMarked(context).arg) }
    }

    private fun readsMark(context: Context) = File(context.filesDir, "reads-way")

    private fun readsMarked(context: Context): ReadsForBenchmarks.Way {
        val marked = runCatching { readsMark(context).readText().trim() }.getOrNull()
        return ReadsForBenchmarks.Way.entries.firstOrNull { it.arg == marked } ?: ReadsForBenchmarks.Way.WhereCollectedUntilTheWorld
    }

    /** A way of reading as the setup's argument names it: `asBefore` for [ReadsForBenchmarks.Way.AsBefore]. */
    private val ReadsForBenchmarks.Way.arg get() = name.replaceFirstChar { it.lowercase() }

    /**
     * The world's guide window read beside its first look, as before, or back as it is, not
     * read until the first look is out: `--method wholeGuide --arg besideFirstLook`, or any other
     * argument. Kept and read as [focusMotion]'s choice is.
     */
    private fun wholeGuide(context: Context, besideFirstLook: Boolean): Bundle {
        val mark = wholeGuideBesideFirstLookMark(context)
        if (besideFirstLook) mark.createNewFile() else mark.delete()
        // The mark of the benchmarks' builds before these, which a set installed over them finds.
        File(context.filesDir, "whole-guide-after-first-look").delete()
        return Bundle().apply { putString("wholeGuide", if (mark.exists()) BESIDE_FIRST_LOOK_ARG else "afterFirstLook") }
    }

    private fun wholeGuideBesideFirstLookMark(context: Context) = File(context.filesDir, "whole-guide-beside-first-look")

    /**
     * The world asked for as Plasma's screen is made, ahead of the screens, or back as it is, not
     * asked for until the first screen's composition asks for it: `--method world --arg
     * askedAhead`, or any other argument. Kept and read as [focusMotion]'s choice is.
     */
    private fun world(context: Context, askedAhead: Boolean): Bundle {
        val mark = worldAskedAheadMark(context)
        if (askedAhead) mark.createNewFile() else mark.delete()
        // The mark of the benchmarks' builds before these, which a set installed over them finds.
        File(context.filesDir, "world-when-composed").delete()
        return Bundle().apply { putString("world", if (mark.exists()) ASKED_AHEAD_ARG else "whenComposed") }
    }

    private fun worldAskedAheadMark(context: Context) = File(context.filesDir, "world-asked-ahead")

    /**
     * A guide window read as one query, as before it was read a few guide channels at a time, or
     * back as it is: `--method guideDay --arg inOnePiece`, or any other argument. Kept and read
     * as [focusMotion]'s choice is: the guide's flows are made as Plasma starts, so a benchmark
     * sets it after [bigSource], which puts it back, and Plasma is started afresh before it's
     * measured.
     */
    private fun guideDay(context: Context, inOnePiece: Boolean): Bundle {
        val mark = guideDayInOnePieceMark(context)
        if (inOnePiece) mark.createNewFile() else mark.delete()
        return Bundle().apply { putString("guideDay", if (mark.exists()) IN_ONE_PIECE_ARG else "inPieces") }
    }

    private fun guideDayInOnePieceMark(context: Context) = File(context.filesDir, "guide-day-in-one-piece")

    private fun homeNotPlacedMark(context: Context) = File(context.filesDir, "home-not-placed-under")

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
        liveTvRows(requireNotNull(context), atOpening = false)
        home(requireNotNull(context), arg = null)
        homeBelow(requireNotNull(context), arg = null)
        mainThread(requireNotNull(context), sampled = false)
        reads(requireNotNull(context), arg = null)
        wholeGuide(requireNotNull(context), besideFirstLook = false)
        world(requireNotNull(context), askedAhead = false)
        guideDay(requireNotNull(context), inOnePiece = false)
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
        const val LIVE_TV_ROWS = "liveTvRows"
        const val AT_OPENING_ARG = "atOpening"
        const val HOME = "home"
        const val MADE_AGAIN_ARG = "madeAgain"
        const val NOT_PLACED_ARG = "notPlaced"
        const val LAYERS_HELD_ARG = "layersHeld"
        const val MAIN_THREAD = "mainThread"
        const val HOME_BELOW = "homeBelow"
        const val AT_ONCE_ARG = "atOnce"
        const val TWO_A_FRAME_ARG = "twoAFrame"
        const val READS = "reads"
        const val WHOLE_GUIDE = "wholeGuide"
        const val BESIDE_FIRST_LOOK_ARG = "besideFirstLook"
        const val WORLD = "world"
        const val ASKED_AHEAD_ARG = "askedAhead"
        const val GUIDE_DAY = "guideDay"
        const val IN_ONE_PIECE_ARG = "inOnePiece"
        const val SAMPLED_ARG = "sampled"
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
