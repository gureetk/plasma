// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** Settings › TV Guide › Update Every Day At (brief 5.3). */
class GuideScheduleTest {
    private val zone = ZoneId.of("America/Toronto")

    private fun at(local: String) = LocalDateTime.parse(local).atZone(zone).toInstant()

    @Test fun theGuideIsDueOnceItsHourHasComeRoundSinceItLoaded() {
        // Loaded at 20:41 yesterday: 04:00 has come round since.
        assertTrue(GuideSchedule.due(at("2026-10-20T20:41"), at("2026-10-21T09:00"), zone, 4))
        // Loaded at 05:00: not again until 04:00 the next day.
        assertFalse(GuideSchedule.due(at("2026-10-21T05:00"), at("2026-10-21T23:00"), zone, 4))
        assertFalse(GuideSchedule.due(at("2026-10-21T05:00"), at("2026-10-22T03:59"), zone, 4))
        assertTrue(GuideSchedule.due(at("2026-10-21T05:00"), at("2026-10-22T04:00"), zone, 4))
        // Never loaded: at once.
        assertTrue(GuideSchedule.due(null, at("2026-10-21T05:00"), zone, 4))
    }

    @Test fun theNextUpdateIsTheNextTimeItsHourComesRound() {
        assertEquals(at("2026-10-22T04:00"), GuideSchedule.next(at("2026-10-21T09:00"), zone, 4))
        assertEquals(at("2026-10-21T06:00"), GuideSchedule.next(at("2026-10-21T05:59"), zone, 6))
        assertEquals(at("2026-10-22T06:00"), GuideSchedule.next(at("2026-10-21T06:00"), zone, 6))
        // On the TV's clock across the end of daylight time (1 November 2026 there).
        assertEquals(at("2026-11-01T04:00"), GuideSchedule.next(at("2026-10-31T20:00"), zone, 4))
        assertEquals(at("2026-11-02T04:00"), GuideSchedule.next(at("2026-11-01T04:30"), zone, 4))
    }

    @Test fun theTvGuideSettingsReadAsChosen() {
        val defaults = SettingsChoices()
        assertEquals(7, defaults.keepPastDays.days)
        assertEquals(7, defaults.loadAhead.days)
        assertEquals(4, defaults.guideUpdateAt.hour)
        assertEquals(ChannelMatching.Automatic, defaults.channelMatching)

        val chosen = SettingsChoices(
            mapOf(SettingKeys.GUIDE_PAST to 0, SettingKeys.GUIDE_AHEAD to 2, SettingKeys.GUIDE_UPDATE to 2, SettingKeys.GUIDE_MATCHING to 2),
        )
        assertEquals(1, chosen.keepPastDays.days)
        assertEquals(14, chosen.loadAhead.days)
        assertEquals(6, chosen.guideUpdateAt.hour)
        assertFalse(chosen.channelMatching.byGuideId)
        assertTrue(chosen.channelMatching.byName)
        assertEquals(LivePreview.Muted, defaults.livePreview)
        assertEquals(LivePreview.Off, SettingsChoices(mapOf(SettingKeys.LIVE_PREVIEW to 2)).livePreview)
    }
}
