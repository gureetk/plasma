// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What Settings' choices come to (brief 5.16). */
class SettingsChoicesTest {
    private val gb = 1_000_000_000L

    @Test fun storageLimitIsTheDrivesOrTheTvsOwn() {
        // 50 GB on a USB drive, 8 GB of the TV's own, unless chosen otherwise (4.31).
        assertEquals(50 * gb, SettingsChoices().storageLimitBytes(onTv = false))
        assertEquals(8 * gb, SettingsChoices().storageLimitBytes(onTv = true))

        val chosen = SettingsChoices(mapOf(SettingKeys.STORAGE_LIMIT to 5, SettingKeys.STORAGE_LIMIT_TV to 0))
        assertEquals(500 * gb, chosen.storageLimitBytes(onTv = false))
        assertEquals(2 * gb, chosen.storageLimitBytes(onTv = true))
    }

    @Test fun theRemoteWorksAsChosen() {
        // Settings › Remote & Buttons, and Playback's Up/Down in Player, in their sheets' order.
        val defaults = SettingsChoices()
        assertEquals(1_500L, defaults.numberEntryDelay.millis)
        assertTrue(defaults.longPressOk)
        assertEquals(ChannelUpGoesTo.NextNumber, defaults.channelUpGoesTo)
        assertEquals(UpDownInPlayer.ChangeChannel, defaults.upDownInPlayer)

        val chosen = SettingsChoices(mapOf(SettingKeys.NUMBER_DELAY to 3, SettingKeys.LONG_PRESS_OK to 1, SettingKeys.CHANNEL_UP to 1, SettingKeys.UP_DOWN to 1))
        assertEquals(3_000L, chosen.numberEntryDelay.millis)
        assertFalse(chosen.longPressOk)
        assertEquals(ChannelUpGoesTo.PreviousNumber, chosen.channelUpGoesTo)
        assertEquals(UpDownInPlayer.ShowChannelList, chosen.upDownInPlayer)
    }

    @Test fun theSoundIsAsChosen() {
        // Settings › Audio's Stereo Downmix, Night Mode and Audio Delay: −100, −50, 0, +50, +100 ms.
        val defaults = SettingsChoices()
        assertFalse(defaults.stereoDownmix)
        assertFalse(defaults.nightMode)
        assertEquals(0, defaults.audioDelay.millis)

        val chosen = SettingsChoices(mapOf(SettingKeys.DOWNMIX to 0, SettingKeys.NIGHT_MODE to 0, SettingKeys.AUDIO_DELAY to 0))
        assertTrue(chosen.stereoDownmix)
        assertTrue(chosen.nightMode)
        assertEquals(-100, chosen.audioDelay.millis)
        assertEquals(listOf(-100, -50, 0, 50, 100), AudioDelay.entries.map { it.millis })
    }

    @Test fun frozenStreamsRestartAfterFiveSecondsUnlessChosenOtherwise() {
        // The owner's #91: After 5 Seconds, After 10 Seconds or Off, in Settings' order.
        assertEquals(5_000L, SettingsChoices().restartFrozen.millis)
        assertEquals(10_000L, SettingsChoices(mapOf(SettingKeys.RESTART_FROZEN to 1)).restartFrozen.millis)
        assertNull(SettingsChoices(mapOf(SettingKeys.RESTART_FROZEN to 2)).restartFrozen.millis)
    }

    @Test fun fastZapIsOffUntilChosenAndTheOldSwitchCountsForNothing() {
        assertEquals(FastZap.Off, SettingsChoices().fastZap)
        assertEquals(FastZap.Off, SettingsChoices(mapOf("fastZap" to 0)).fastZap)
        assertEquals(FastZap.Ahead, SettingsChoices(mapOf(SettingKeys.FAST_ZAP to 1)).fastZap)
        assertEquals(FastZap.BothWays, SettingsChoices(mapOf(SettingKeys.FAST_ZAP to 2)).fastZap)
    }

    @Test fun liveTvsListShowsWhatsOnUntilChannelNamesAreChosen() {
        // The owner's #137: What's On or Channel Names, in Settings' order.
        assertEquals(LiveListShows.WhatsOn, SettingsChoices().liveListShows)
        assertEquals(LiveListShows.WhatsOn, SettingsChoices(mapOf(SettingKeys.LIVE_LIST_SHOWS to 0)).liveListShows)
        assertEquals(LiveListShows.ChannelNames, SettingsChoices(mapOf(SettingKeys.LIVE_LIST_SHOWS to 1)).liveListShows)
    }

    @Test fun savePlayerLogsIsOffUntilTurnedOn() {
        assertEquals(false, SettingsChoices().savePlayerLogs)
        assertEquals(true, SettingsChoices(mapOf(SettingKeys.PLAYER_LOGS to 0)).savePlayerLogs)
        assertEquals(mapOf(SettingKeys.PLAYER_LOGS to 0), SettingsChoices(mapOf(SettingKeys.PLAYER_LOGS to 0)).chosenByKey)
    }
}
