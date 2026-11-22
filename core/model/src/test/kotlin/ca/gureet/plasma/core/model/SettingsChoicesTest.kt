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
        // Switch Automatically (#115): off unless it's turned on.
        assertEquals(false, SettingsChoices(emptyMap()).switchAutomatically)
        assertEquals(true, SettingsChoices(mapOf(SettingKeys.REMINDER_SWITCH to 0)).switchAutomatically)
        // Over other apps only when chosen as well (the owner on #115).
        assertEquals(false, SettingsChoices(mapOf(SettingKeys.REMINDER_SWITCH to 0)).switchOverApps)
        assertEquals(false, SettingsChoices(mapOf(SettingKeys.REMINDER_SWITCH_OVER_APPS to 0)).switchOverApps)
        assertEquals(true, SettingsChoices(mapOf(SettingKeys.REMINDER_SWITCH to 0, SettingKeys.REMINDER_SWITCH_OVER_APPS to 0)).switchOverApps)
        // Long-Press OK's Number Pad (#105), stored after Nothing: holding OK still does more than OK.
        assertEquals(LongPressOk.Options, SettingsChoices(emptyMap()).longPressOkDoes)
        val numberPad = SettingsChoices(mapOf(SettingKeys.LONG_PRESS_OK to 2))
        assertEquals(LongPressOk.NumberPad, numberPad.longPressOkDoes)
        assertTrue(numberPad.longPressOk)
        assertEquals(LongPressOk.Nothing, SettingsChoices(mapOf(SettingKeys.LONG_PRESS_OK to 1)).longPressOkDoes)
    }

    @Test fun mpvPlaysUnlessAnotherEngineIsChosen() {
        // The owner's #119: mpv is the default, and takes over to VLC if a stream won't play.
        assertEquals(PlayerEngineChoice.Mpv, SettingsChoices().playerEngine)
        assertEquals(PlayerEngineChoice.Vlc, SettingsChoices(mapOf(SettingKeys.PLAYER_ENGINE to 0)).playerEngine)
        assertEquals(IfStreamFails.TryOtherThenAndroidPlayer, SettingsChoices().ifStreamFails)
    }

    @Test fun moviesAreCheckedFirstWithTwoConnectionsUnlessChosenOtherwise() {
        assertEquals(ProbeMovies.WithTwoConnections, SettingsChoices().probeMovies)
        assertEquals(ProbeMovies.Always, SettingsChoices(mapOf(SettingKeys.PROBE_MOVIES to 1)).probeMovies)
        assertEquals(ProbeMovies.Never, SettingsChoices(mapOf(SettingKeys.PROBE_MOVIES to 2)).probeMovies)
    }

    @Test fun audioDelayIsTheSlidersOrElseTheChoiceFromBefore() {
        // #116: none to start with; the five choices from before still count until the slider moves.
        assertEquals(0, SettingsChoices().audioDelayMs)
        assertEquals(-50, SettingsChoices(mapOf(SettingKeys.AUDIO_DELAY to 1)).audioDelayMs)
        assertEquals(240, SettingsChoices(mapOf(SettingKeys.AUDIO_DELAY to 1, SettingKeys.AUDIO_DELAY_MS to 240)).audioDelayMs)
        // Within its ends, on a 10 ms step.
        assertEquals(500, SettingsChoices(mapOf(SettingKeys.AUDIO_DELAY_MS to 900)).audioDelayMs)
        assertEquals(-120, SettingsChoices(mapOf(SettingKeys.AUDIO_DELAY_MS to -125)).audioDelayMs)
        // A channel's own.
        assertEquals(0, SettingsChoices().channelAudioDelayMs("home:105"))
        assertEquals(-80, SettingsChoices(mapOf(SettingKeys.channelAudioDelay("home:105") to -80)).channelAudioDelayMs("home:105"))
    }

    @Test fun upcomingEventsLooksAtFavoritesUntilFromSaysOtherwise() {
        // The owner's #110: From, Favorites, Groups or Channels in Settings' order, with the picks
        // kept under keys of their own, 0 picked and 1 not.
        assertEquals(UpcomingPicks(), SettingsChoices().upcomingPicks)
        val chosen = SettingsChoices(
            mapOf(
                SettingKeys.UPCOMING_FROM to 1,
                SettingKeys.upcomingGroup("home:sports") to 0,
                SettingKeys.upcomingGroup("home:news") to 1,
                SettingKeys.upcomingChannel("home:105") to 0,
            ),
        )
        assertEquals(UpcomingPicks(UpcomingFrom.Groups, groups = setOf("home:sports"), channels = setOf("home:105")), chosen.upcomingPicks)
        assertEquals(UpcomingFrom.Channels, SettingsChoices(mapOf(SettingKeys.UPCOMING_FROM to 2)).upcomingPicks.from)
    }

    @Test fun savePlayerLogsIsOffUntilTurnedOn() {
        assertEquals(false, SettingsChoices().savePlayerLogs)
        assertEquals(true, SettingsChoices(mapOf(SettingKeys.PLAYER_LOGS to 0)).savePlayerLogs)
        assertEquals(mapOf(SettingKeys.PLAYER_LOGS to 0), SettingsChoices(mapOf(SettingKeys.PLAYER_LOGS to 0)).chosenByKey)
    }
}
