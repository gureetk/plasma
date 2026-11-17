// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

/**
 * For the benchmarks (#127): with [asBefore], what the screens read is worked out where it's
 * collected, and the parental words' patterns are made as the words are, as both were before they
 * were moved. A twin test measures the way back to Home both ways in one run. Read as Plasma
 * starts, before anything is made.
 */
object ReadsForBenchmarks {
    @Volatile var asBefore = false
}
