// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

/**
 * For the benchmarks (#127): how what the screens read is worked out, and when Home's own reads
 * and the world's guide window start, so that twin tests measure a start and the way back to Home
 * each way in one run. Read as Plasma starts, before anything is made.
 */
object ReadsForBenchmarks {
    enum class Way {
        /**
         * As built: where it's collected for whatever starts to be read before the first world is
         * in, and off the collector's thread for whatever starts after: a start as before, and the
         * way back as moved.
         */
        WhereCollectedUntilTheWorld,

        /**
         * Where it's collected, and the parental words' patterns made as the words are, as both
         * were before they were moved.
         */
        AsBefore,

        /** Off the thread that collects from the start, with nothing held: as it was first moved. */
        OffMainFromTheStart,

        /**
         * Off the thread that collects, and what Home reads besides its world not read until its
         * first world is in: tried, and not taken.
         */
        HeldUntilTheWorld,
    }

    @Volatile var way = Way.WhereCollectedUntilTheWorld

    /** Whether what's read is worked out where it's collected, with the words' patterns made as the words are. */
    val asBefore: Boolean get() = way == Way.AsBefore

    /** Whether the first world is in: [Way.WhereCollectedUntilTheWorld] goes by it. The graph says so. */
    @Volatile var worldIn = false

    /**
     * Whether the world's guide window is read beside its first look, as before, where as built it
     * waits for the first look to be out: a twin of the cold start measures what the first world
     * gains by the wait.
     */
    @Volatile var wholeGuideBesideFirstLook = false

    /**
     * How long the world's guide window waits, once the first look is out, before it's read, in
     * milliseconds: 0 as built, where its read begins as the first screen is about to show and
     * runs beside that screen's first frames. A twin of the cold start has it wait, to measure
     * what those frames gain by having the processors and the database's threads to themselves.
     */
    @Volatile var wholeGuideLaterMs = 0L

    /**
     * Whether the world is asked for as Plasma's screen is made, ahead of the screens, where as
     * built nothing asks for it until the first screen's composition does. Tried, and not taken:
     * the world is made sooner and Home shows no sooner for it. It's left for a twin of the cold
     * start, should it be measured again with what would have to go with it.
     */
    @Volatile var worldAskedAhead = false

    /**
     * Whether the four values Plasma's screen waits for as it starts (whether there's a source,
     * the settings, Home's rows' order, first-time setup) are worked out where they're collected,
     * on the main thread, with the app composed whenever the last of them is in: as built, and
     * as on `main`. Otherwise they're worked out off the main thread, and the app is composed in
     * the frame after the launch screen's. Tried, and not taken: the first screen shows no
     * sooner for it. It's left for a twin of the cold start.
     */
    @Volatile var startValuesWhereCollected = true
}
