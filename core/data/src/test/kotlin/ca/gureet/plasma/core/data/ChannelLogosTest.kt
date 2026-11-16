// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.database.ChannelEntity
import ca.gureet.plasma.core.model.ChannelLogos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A channel's logo (#228): Channel Logos' order, a source's placeholder giving way to the guide's
 * with Replace Placeholder Logos, and never a logo that doesn't load.
 */
class ChannelLogosTest {
    private val placeholder = "https://logos.home.lan/tv.png"

    @Test fun aLogoTenChannelsShareIsAPlaceholder() {
        val channels = (1..10).map { channel("$it", logo = placeholder) } + channel("11", logo = "https://logos.home.lan/arena.png") +
            (12..20).map { channel("$it", logo = "https://logos.home.lan/local-network.png") }
        assertEquals(setOf(placeholder), placeholderLogos(channels))
    }

    @Test fun aPlaceholderGivesWayToTheGuidesLogoWhenThereIsOne() {
        val replacing = LogoChoice(ChannelLogos.FromPortal, replacing = true, placeholders = setOf(placeholder))
        assertEquals("https://guide.home.lan/arena.png", channel(logo = placeholder, guideLogo = "https://guide.home.lan/arena.png").shownLogo(replacing))
        // Without one from the guide, the source's stays: it's better than nothing.
        assertEquals(placeholder, channel(logo = placeholder).shownLogo(replacing))
        // A real logo is kept over the guide's.
        assertEquals("https://logos.home.lan/arena.png", channel(logo = "https://logos.home.lan/arena.png", guideLogo = "https://guide.home.lan/arena.png").shownLogo(replacing))

        // Replace Placeholder Logos off: the source's, as it gives it.
        val asGiven = LogoChoice(ChannelLogos.FromPortal, replacing = false, placeholders = setOf(placeholder))
        assertEquals(placeholder, channel(logo = placeholder, guideLogo = "https://guide.home.lan/arena.png").shownLogo(asGiven))
    }

    @Test fun aLogoThatDoesntLoadGivesWayToTheOtherOrTheName() {
        val notLoading = setOf("https://gone.home.lan/arena.png", "https://guide.home.lan/gone.png")
        val fromPortal = LogoChoice(ChannelLogos.FromPortal, notLoading = notLoading)
        assertEquals("https://guide.home.lan/arena.png", channel(logo = "https://gone.home.lan/arena.png", guideLogo = "https://guide.home.lan/arena.png").shownLogo(fromPortal))
        assertNull(channel(logo = "https://gone.home.lan/arena.png").shownLogo(fromPortal))

        val fromGuide = LogoChoice(ChannelLogos.FromGuide, notLoading = notLoading)
        assertEquals("https://logos.home.lan/arena.png", channel(logo = "https://logos.home.lan/arena.png", guideLogo = "https://guide.home.lan/gone.png").shownLogo(fromGuide))
        assertEquals("https://guide.home.lan/arena.png", channel(logo = "https://logos.home.lan/arena.png", guideLogo = "https://guide.home.lan/arena.png").shownLogo(fromGuide))
    }

    private fun channel(id: String = "1", logo: String? = null, guideLogo: String? = null) = ChannelEntity(
        id = "home:$id",
        sourceId = "home",
        key = id,
        groupId = null,
        name = "Channel $id",
        position = id.toInt(),
        playlistNumber = null,
        number = id.toInt(),
        numberReason = "Playlist",
        wantedNumber = null,
        logo = logo,
        tvgId = null,
        epgKey = null,
        catchupDays = null,
        isRadio = false,
        generation = 1,
        guideLogo = guideLogo,
    )
}
