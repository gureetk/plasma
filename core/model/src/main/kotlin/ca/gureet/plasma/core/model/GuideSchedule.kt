// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

import java.time.Instant
import java.time.ZoneId

/**
 * When the guide loads (Settings › TV Guide › Update Every Day At, brief 5.3): once a day, at an
 * hour on the TV's clock.
 */
object GuideSchedule {
    /**
     * A guide loaded at [refreshedAt] is due again once [hour] o'clock has come round since, on the
     * TV's clock in [zone]: at that hour, or at the first look after it, as when Plasma wasn't in
     * front then. One never loaded is due at once.
     */
    fun due(refreshedAt: Instant?, now: Instant, zone: ZoneId, hour: Int): Boolean {
        if (refreshedAt == null) return true
        val today = now.atZone(zone).toLocalDate().atTime(hour, 0).atZone(zone)
        val last = if (today.toInstant().isAfter(now)) today.minusDays(1) else today
        return refreshedAt.isBefore(last.toInstant())
    }

    /** The next time [hour] o'clock comes round after [now] on the TV's clock: the guide's next update. */
    fun next(now: Instant, zone: ZoneId, hour: Int): Instant {
        val today = now.atZone(zone).toLocalDate().atTime(hour, 0).atZone(zone)
        return (if (today.toInstant().isAfter(now)) today else today.plusDays(1)).toInstant()
    }
}
