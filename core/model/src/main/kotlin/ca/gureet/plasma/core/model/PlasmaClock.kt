// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Where Plasma gets the time from, so previews and tests can stand still at 20:41 (appendix A). */
interface PlasmaClock {
    val zone: ZoneId
    fun now(): Instant
    fun nowZoned(): ZonedDateTime = now().atZone(zone)

    companion object {
        val System: PlasmaClock = object : PlasmaClock {
            override val zone: ZoneId get() = ZoneId.systemDefault()
            override fun now(): Instant = Instant.now()
        }
    }
}

/** A clock that never moves. */
class FixedClock(private val instant: Instant, override val zone: ZoneId) : PlasmaClock {
    override fun now(): Instant = instant
}

/** A clock that starts at [start] when created and then runs in real time, for the sample-data app. */
class ShiftedClock(start: Instant, override val zone: ZoneId) : PlasmaClock {
    private val offset = Duration.between(Instant.now(), start)
    override fun now(): Instant = Instant.now().plus(offset)
}
