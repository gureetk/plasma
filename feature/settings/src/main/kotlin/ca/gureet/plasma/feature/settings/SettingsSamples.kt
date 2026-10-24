// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.runtime.Immutable
import ca.gureet.plasma.core.fixtures.Sample
import java.time.LocalDate
import java.time.ZonedDateTime

/** A source's kind (brief 5.1). */
enum class SourceType { Stalker, Xtream, M3U }

/** How a source is doing: connected (with its response time), on standby, or with a stale guide. */
@Immutable
sealed interface SourceHealth {
    /** [pingMs] is shown when it was measured. */
    data class Connected(val pingMs: Int?) : SourceHealth
    data object Standby : SourceHealth

    /** Turned off in its options: its channels are out of every list (brief 4.20). */
    data object Off : SourceHealth

    /** Test Connection is under way (4.20). */
    data object Testing : SourceHealth

    /** Test Connection didn't get through, for [problem]. */
    data class TestFailed(val problem: ConnectProblem) : SourceHealth
    data class GuideOld(val days: Int) : SourceHealth

    /** The last refresh failed; the old channels and guide stay (brief 5.1). */
    data object RefreshFailed : SourceHealth
}

@Immutable
data class SourceInfo(
    val id: String,
    val name: String,
    val type: SourceType,
    /** The host on the row ("portal.example.net"); an M3U playlist shows its channel count instead. */
    val host: String,
    val health: SourceHealth,
    val server: String,
    val mac: String? = null,
    val username: String? = null,
    val activeUntil: LocalDate? = null,
    val connectionsInUse: Int? = null,
    val connectionLimit: Int? = null,
    val updated: ZonedDateTime,
    val channels: Int,
    val movies: Int? = null,
    val series: Int? = null,
    val catchUpDays: Int? = null,
    val guideUrl: String? = null,
    val primary: Boolean = false,
    val enabled: Boolean = true,
    /** What the account says: "Active", "Expired"… */
    val accountStatus: String? = null,
    /** The stream formats the account offers ("m3u8", "ts"). */
    val formats: List<String> = emptyList(),
    /** The server's own time zone, as it reports it. */
    val timeZone: String? = null,
    val playback: SourcePlaybackInfo = SourcePlaybackInfo(),
)

/** Playback for This Source (brief 4.20): a null choice is "Same as Playback settings". */
@Immutable
data class SourcePlaybackInfo(
    /** "ts" or "m3u8", for Xtream. */
    val streamFormat: String = "ts",
    /** Buffer Size, as its index in Playback's options. */
    val bufferChoice: Int? = null,
    /** Audio Output, as its index in Audio's options. */
    val audioOutputChoice: Int? = null,
    val epgOffsetMinutes: Int = 0,
    val catchupOffsetMinutes: Int = 0,
    /** The user agent the source asks for, or null for Plasma's own. */
    val userAgent: String? = null,
    /** Player Engine, as its index in Playback's options; null is "Same as Playback settings". */
    val engineChoice: Int? = null,
    /** Use Stream Hub; null is "Same as Playback settings". */
    val useHub: Boolean? = null,
    val referrer: String? = null,
    /** Extra headers, one "Name: value" after another, separated by semicolons or lines. */
    val extraHeaders: String? = null,
    /** A udpxy proxy for multicast, "192.168.1.1:4022" (brief 5.4.10). */
    val udpxy: String? = null,
)

/** The ways Plasma can be the Home screen (brief 4.23). */
enum class HomeScreenMethod { Override, Replace, DefaultApp }

/** How this TV lets an app become its Home app (brief 5.13, method 3). */
enum class DefaultHomeOffer {
    /** Android asks when Plasma requests the Home role. */
    Role,

    /** The TV's settings have a Home app page. */
    Settings,

    /** Home shows a chooser: Plasma, then Always. */
    Chooser,

    /** It doesn't: Not available on this TV. */
    None,
}

/** What Home Screen setup knows about this TV (brief 4.23). */
@Immutable
data class HomeScreenFacts(
    /** The TV, as it names itself: "Google TV Streamer". */
    val device: String,
    /** Its Android version: "14". */
    val androidVersion: String,
    /** What makes Plasma Home now; null while the TV's own Home answers. */
    val method: HomeScreenMethod?,
    /** Plasma's Home Button Override is turned on. */
    val overrideOn: Boolean,
    /** The TV's own Home, by name. */
    val launcherName: String,
    val defaultHome: DefaultHomeOffer,
    /** Other apps whose Home overrides are on too, by name. */
    val conflicts: List<String> = emptyList(),
    /** Android holds back Plasma's Accessibility switch (a restricted setting, 5.13). */
    val restricted: Boolean = false,
    /** Holding Home reaches Plasma, so Long-Press Home is Plasma's to set (5.13). */
    val longPressHome: Boolean = true,
    /** Plasma's own package, for the commands. */
    val packageName: String = "ca.gureet.plasma",
)

/** What can be done from Home Screen setup. */
enum class HomeAction {
    /** The TV's Accessibility settings, where the override is turned on and off. */
    OpenAccessibility,

    /** Turns the override off from Plasma. */
    TurnOverrideOff,

    /** Plasma's App info, where a restricted setting is allowed. */
    OpenAppInfo,

    /** Makes Plasma the Home app the way this TV offers. */
    SetDefaultHome,

    /** Turns Google TV Home back on over Plasma's pairing with the TV (method 2's undo). */
    Restore,
}

/** A phone paired as a remote (brief 4.33): connected now, or when it was last used. */
@Immutable
data class PairedPhone(val name: String, val connected: Boolean, val lastUsed: ZonedDateTime)

/** The world of Settings on the boards (appendix A): three sources, a USB drive, a NAS and a Jellyfin server. */
@Immutable
data class SettingsFacts(
    val sources: List<SourceInfo>,
    val xmltvFeeds: Int,
    val guideDays: Int,
    val driveName: String,
    val driveFree: Long,
    val driveTotal: Long,
    val recordingsBytes: Long,
    val localCatchUpBytes: Long,
    val rewindBytes: Long,
    val localChannels: List<String>,
    val primarySource: String,
    val networkFolder: String,
    val networkFolderFree: Long,
    val jellyfinServer: String,
    val jellyfinUser: String,
    val jellyfinItems: Int,
    /** Apps that share what's watched in them on Android's Play Next row (5.19). */
    val playNextApps: List<PlayNextApp>,
    val playNextItems: Int,
    val audioConnection: String,
    val supportedFormats: List<String>,
    val unsupportedFormats: List<String>,
    val duplicatesFixed: Int,
    val mergedChannels: Int,
    val hiddenChannels: Int,
    val hiddenGroups: Int,
    val profiles: Int,
    val ageLimit: String,
    val connection: String,
    /** This TV's address on the home network (appendix A). */
    val address: String,
    val speed: String,
    val version: String,
    /** This TV and its Home button, for Home Screen setup (board 23). */
    val home: HomeScreenFacts,
    /** The port of Plasma's page for phones (5.15), and the code it asks for now. */
    val phonePort: Int,
    val phoneCode: String,
    val pairedPhones: List<PairedPhone>,
    /** The first three favorites, for the numbering example on board 29: number, name and logo. */
    val favoriteExamples: List<Triple<Int, String, String?>>,
    /** Channels with a sound of their own (Channel Volume Levels, 4.28), by name. */
    val channelSounds: List<ChannelSoundInfo> = emptyList(),
    /** Apps hidden on Apps (4.4), A to Z, for Launcher & Home › Hidden Apps. */
    val hiddenApps: List<HiddenApp> = emptyList(),
    /** Whether Plasma's screensaver is the TV's own (5.14). */
    val tvScreensaver: TvScreensaverFacts = TvScreensaverFacts(),
    /** Android lets Plasma read other apps' Play Next items ("read TV listings", 5.19). */
    val playNextAllowed: Boolean = true,
    /** Signed in to a Jellyfin server (5.19). */
    val jellyfinConnected: Boolean = true,
)

/** An app that shares what's watched in it on Android's Play Next row (brief 5.19), and whether Plasma shows its items. */
@Immutable
data class PlayNextApp(val packageName: String, val name: String, val shown: Boolean = true)

/** What can be done about other apps' items in Continue Watching (brief 4.32 and 5.19). */
sealed interface OtherAppsAction {
    /** Include Other Apps was turned on: Android's permission is asked for, if it isn't given yet. */
    data object TurnOn : OtherAppsAction

    /** Android's question again, after it wasn't allowed. */
    data object AskAgain : OtherAppsAction

    /** Allows it over Plasma's pairing with the TV, for a TV that never asks. */
    data object AllowOverWirelessDebugging : OtherAppsAction

    /** One app's items shown or hidden. */
    data class ShowApp(val packageName: String, val shown: Boolean) : OtherAppsAction
}

/** Whether Plasma's screensaver is the TV's own (brief 5.14), as far as the TV says. */
enum class TvScreensaver { Plasma, Other, Unknown }

/**
 * What Launcher & Home knows of the TV's screensaver: whose it is, Plasma's by the name the
 * commands use ([component], "package/class"), and whether the TV has a page for choosing it.
 */
@Immutable
data class TvScreensaverFacts(
    val state: TvScreensaver = TvScreensaver.Other,
    val component: String = "ca.gureet.plasma/ca.gureet.plasma.PlasmaDream",
    val tvSettings: Boolean = true,
)

/** What can be done from Launcher & Home › Use as the TV's Screensaver. */
enum class TvScreensaverAction {
    /** Makes Plasma's the TV's screensaver, over Plasma's pairing with the TV. */
    UsePlasma,

    /** Puts the TV's own back. */
    UseTvsOwn,

    /** The TV's own page for choosing its screensaver, where it has one. */
    OpenTvSettings,
}

/** An app hidden on Apps (brief 4.4): Launcher & Home lists it, to show it again. */
@Immutable
data class HiddenApp(val packageName: String, val label: String)

object SettingsSamples {
    val facts: SettingsFacts = SettingsFacts(
        sources = listOf(
            SourceInfo(
                id = "home",
                name = "Home Portal",
                type = SourceType.Stalker,
                host = "portal.example.net",
                health = SourceHealth.Connected(pingMs = 42),
                server = "portal.example.net/c/",
                mac = "00:1A:79:4C:2E:91",
                activeUntil = LocalDate.of(2027, 3, 12),
                connectionsInUse = 1,
                connectionLimit = 2,
                updated = Sample.at(18, 2),
                channels = 1_284,
                movies = 8_412,
                series = 2_106,
                catchUpDays = 7,
                primary = true,
                accountStatus = "Active",
            ),
            SourceInfo(
                id = "backup",
                name = "Backup Line",
                type = SourceType.Xtream,
                host = "line.example.org",
                health = SourceHealth.Standby,
                server = "line.example.org:8080",
                username = "living-room",
                activeUntil = LocalDate.of(2027, 1, 31),
                connectionsInUse = 0,
                connectionLimit = 1,
                updated = Sample.at(6, 0),
                channels = 1_190,
                movies = 5_204,
                series = 1_310,
                accountStatus = "Active",
                formats = listOf("m3u8", "ts"),
                timeZone = "Europe/London",
            ),
            SourceInfo(
                id = "extras",
                name = "Sports Extras",
                type = SourceType.M3U,
                host = "lists.example.com",
                health = SourceHealth.GuideOld(days = 2),
                server = "lists.example.com/sports-extras.m3u",
                updated = Sample.at(4, 0, -2),
                channels = 212,
                guideUrl = "lists.example.com/sports-extras.xml",
            ),
        ),
        xmltvFeeds = 2,
        guideDays = 7,
        driveName = "USB drive",
        driveFree = 212_000_000_000,
        driveTotal = 500_000_000_000,
        recordingsBytes = 246_000_000_000,
        localCatchUpBytes = 38_000_000_000,
        rewindBytes = 4_000_000_000,
        localChannels = listOf(Sample.courtside.name),
        primarySource = "Home Portal",
        networkFolder = "nas.home.lan › Media › Recordings",
        networkFolderFree = 3_100_000_000_000,
        jellyfinServer = "media.home.lan",
        jellyfinUser = Sample.maya.name,
        jellyfinItems = 3,
        playNextApps = listOf(PlayNextApp("tv.example.reelbox", "Reelbox"), PlayNextApp("tv.example.driftwood", "Driftwood Podcasts")),
        playNextItems = 4,
        audioConnection = "HDMI eARC · AV receiver",
        supportedFormats = listOf("Dolby Digital", "Dolby Digital Plus", "Dolby Atmos", "DTS"),
        unsupportedFormats = listOf("DTS-HD MA", "Dolby TrueHD"),
        duplicatesFixed = 4,
        mergedChannels = 38,
        hiddenChannels = 12,
        hiddenGroups = 3,
        profiles = Sample.profiles.size,
        ageLimit = "18+",
        connection = "Wi-Fi",
        address = "192.168.1.42",
        speed = "94 Mb/s",
        version = "1.0.0",
        home = HomeScreenFacts(
            device = "Google TV Streamer",
            androidVersion = "14",
            method = HomeScreenMethod.Override,
            overrideOn = true,
            launcherName = "Google TV Home",
            defaultHome = DefaultHomeOffer.None,
        ),
        phonePort = 8484,
        phoneCode = "7KQ 4MZ",
        pairedPhones = listOf(
            PairedPhone("Maya’s iPhone", connected = true, lastUsed = Sample.at(20, 41)),
            PairedPhone("Daniel’s Pixel", connected = false, lastUsed = Sample.at(21, 12, -1)),
        ),
        favoriteExamples = Sample.favorites.take(3).map { Triple(it.number, it.name, it.logo) },
        // Appendix A's three: stereo channels, decoded, so the levels apply with passthrough on.
        channelSounds = listOf(
            ChannelSoundInfo("home:101", "Northline News", 3),
            ChannelSoundInfo("home:118", "Meridian 24", -2),
            ChannelSoundInfo("home:120", "Atlas One", -3),
        ),
    )
}
