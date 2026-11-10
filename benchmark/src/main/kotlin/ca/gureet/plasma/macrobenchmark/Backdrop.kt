// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.macrobenchmark

import android.app.Activity

/**
 * A black screen of the benchmarks' own, behind Plasma Benchmark while they measure, so stopping
 * Plasma Benchmark doesn't bring the TV's own Home back (`putBackdropBehind`).
 */
class Backdrop : Activity()
