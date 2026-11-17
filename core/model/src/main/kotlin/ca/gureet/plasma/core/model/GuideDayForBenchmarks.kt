// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

/**
 * For the benchmarks (#127): with [inOnePiece], a guide window is read as one query, as before it
 * was read a few guide channels at a time. A twin test measures the Guide's day both ways in one
 * run. Read as Plasma starts, before anything is made.
 */
object GuideDayForBenchmarks {
    @Volatile var inOnePiece = false
}
