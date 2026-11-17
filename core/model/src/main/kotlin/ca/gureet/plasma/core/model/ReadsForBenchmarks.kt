// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

/**
 * For the benchmarks (#127): how what the screens read is worked out, and when Home's own reads
 * start, so that twin tests measure a start and the way back to Home each way in one run. Read as
 * Plasma starts, before anything is made.
 */
object ReadsForBenchmarks {
    enum class Way {
        /**
         * As built: off the thread that collects, and what Home reads besides its world isn't
         * read until its first world is in, so that it doesn't take the processors and the
         * database's threads from what the launch screen waits for.
         */
        HeldUntilTheWorld,

        /**
         * Where it's collected, and the parental words' patterns made as the words are, as both
         * were before they were moved.
         */
        AsBefore,

        /** Off the thread that collects from the start, with nothing held: as it was first moved. */
        OffMainFromTheStart,

        /**
         * Where it's collected for whatever starts to be read before the first world is in, and
         * off the collector's thread for whatever starts after: a start as before, and the way
         * back as moved.
         */
        WhereCollectedUntilTheWorld,
    }

    @Volatile var way = Way.HeldUntilTheWorld

    /** Whether what's read is worked out where it's collected, with the words' patterns made as the words are. */
    val asBefore: Boolean get() = way == Way.AsBefore

    /** Whether the first world is in: [Way.WhereCollectedUntilTheWorld] goes by it. The graph says so. */
    @Volatile var worldIn = false

    /**
     * Whether the world's whole guide waits for its first look to be out before it's read, where
     * as built the two are read side by side: a twin of the cold start measures what the first
     * world gains by it.
     */
    @Volatile var wholeGuideAfterFirstLook = false
}
