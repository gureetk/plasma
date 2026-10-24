// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.runtime.Immutable
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.model.AdultWords
import ca.gureet.plasma.core.model.QrMatrix
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

    /** Turns the override on over Plasma's pairing with the TV, restricted or hidden as its switch may be. */
    TurnOnOverDebugging,

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
data class PairedPhone(val name: String, val connected: Boolean, val lastUsed: ZonedDateTime, val id: String = name)

/** The world of Settings on the boards (appendix A): three sources, a USB drive, a NAS and a Jellyfin server. */
@Immutable
data class SettingsFacts(
    val sources: List<SourceInfo>,
    val xmltvFeeds: Int,
    val guideDays: Int,
    val driveName: String,
    val driveFree: Long,
    val driveTotal: Long,
    /** Whether the rewind buffer and local catch-up are on the TV's own storage, rather than a USB drive. */
    val storageOnTv: Boolean = false,
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
    /** Whether a parental PIN is set (5.12): parental controls are on once there's one. */
    val pinSet: Boolean = true,
    /** The channels that always need the PIN (5.12). */
    val lockedChannels: List<LockedChannelInfo> = emptyList(),
    /** Every group, and whether its channels need the PIN (5.12). */
    val lockedGroups: List<LockedGroupInfo> = emptyList(),
    /** Lock Adult Groups' words, as Settings shows them (5.12). */
    val adultWords: String = AdultWords.DEFAULT,
    /** The profiles (5.11), the one watching now, and the one Plasma starts as when the TV wakes (null: whoever watched last). */
    val profiles: List<ProfileInfo> = emptyList(),
    val currentProfile: String = "",
    val defaultProfile: String? = null,
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
    /** The phone remote's QR code (5.21); null shows the board's. */
    val phoneQr: QrMatrix? = null,
    /** Whether phones can reach the TV: it's on a network, and the page answers. */
    val phoneAvailable: Boolean = true,
    /** The phone remote is starting, so there's no code to show yet. */
    val phoneStarting: Boolean = false,
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
    /**
     * Whether that sign-in is the server's administrator, who may ask it to look for new
     * recordings (5.6); null while it's not known.
     */
    val jellyfinAdmin: Boolean? = true,
    /** The Jellyfin app is on this TV, for Play Jellyfin Items In (5.19). */
    val jellyfinAppInstalled: Boolean = true,
    /** The TV has picture-in-picture, for Keep Watching in a Small Window (5.20). */
    val pictureInPicture: Boolean = true,
    /** Reminders can show over other apps: Android lets Plasma "display over other apps" (5.7). */
    val remindersOverApps: ReminderOverApps = ReminderOverApps(),
    /** About › Plasma Update: what the updater is doing (5.18). */
    val update: UpdateFacts = UpdateFacts(),
    /** Local catch-up's channels on the primary source, which hold its connections all the time (5.5). */
    val localOnPrimary: Int = localChannels.size,
    /** The channels Local Catch-up Channels lists: those without catch-up of their own (4.31). */
    val localChoices: List<LocalChannelChoice> = emptyList(),
)

/** About › Plasma Update (brief 5.18): what the updater is doing, for its row. */
@Immutable
data class UpdateFacts(
    val status: UpdateStatus = UpdateStatus.UpToDate,
    /** The update's version, "0.2.0 (512)", while there's one. */
    val version: String = "",
    /** What's new in it, from the update file. */
    val notes: String = "",
    /** How much of the download has come, 0 to 100, or −1 when the server doesn't say. */
    val percent: Int = 0,
    /** When Plasma last looked. */
    val checkedAt: ZonedDateTime? = null,
    /** Why the last check or install didn't work. */
    val problem: UpdateTrouble? = null,
    /** Android lets Plasma install apps ("Install unknown apps"). */
    val allowed: Boolean = true,
    /** The TV has its own page for allowing that. */
    val tvSettings: Boolean = true,
)

/** Where Plasma's updater is (brief 5.18). */
enum class UpdateStatus {
    /** This build doesn't look for updates. */
    Off,
    NotChecked,
    UpToDate,
    Checking,

    /** A newer version is ready to download and install. */
    Ready,
    Downloading,

    /** With Android's installer, which asks the user to confirm. */
    Installing,
}

/** Why an update couldn't be checked for or installed (brief 5.18). */
enum class UpdateTrouble { CantReach, NotThere, BadFile, Damaged, NotPlasma, NotAllowed, Refused, NoRoom, Recording }

/** What About › Plasma Update does (brief 5.18). */
enum class UpdateAction {
    CheckNow,
    Install,

    /** "Install unknown apps" for Plasma, allowed over Plasma's pairing with the TV. */
    AllowOverWirelessDebugging,

    /** The TV's own page for it, where it has one. */
    OpenTvSettings,
}

/** Whether reminders can show over other apps (brief 5.7), and whether the TV has a page to allow it. */
@Immutable
data class ReminderOverApps(val allowed: Boolean = false, val tvSettings: Boolean = true)

/** What TV Guide › Show over Other Apps does to let reminders show over other apps (brief 5.7). */
enum class OverAppsAction {
    /** Allows it over Plasma's pairing with the TV, as many TVs hide the setting. */
    AllowOverWirelessDebugging,

    /** The TV's own page for it, where it has one. */
    OpenTvSettings,
}

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
        lockedChannels = listOf(LockedChannelInfo("110", 110, "Combat Arena")),
        // The sample's groups, and one of its own that's adult, locked.
        lockedGroups = Sample.groups.filter { !it.smart }.map { LockedGroupInfo(it.id, it.name, locked = false) } +
            LockedGroupInfo("after-dark", "After Dark 18+", locked = true, adult = true),
        // Board 05's profiles: Maya watching, Daniel with a PIN, Kids and a guest.
        profiles = Sample.profiles.mapIndexed { index, profile ->
            ProfileInfo(
                id = profile.id,
                name = profile.name,
                type = when {
                    profile.maxAge != null -> ProfileType.Kids
                    profile.guest -> ProfileType.Guest
                    else -> ProfileType.Standard
                },
                avatar = ProfileSettings.AVATARS[index].takeIf { !profile.guest },
                hasPin = profile.pin,
                maxAge = profile.maxAge,
                first = index == 0,
            )
        },
        currentProfile = Sample.maya.id,
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
