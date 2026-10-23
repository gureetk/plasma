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
)

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
    val playNextApps: List<String>,
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
    /** This TV and its system, for Home Screen setup (board 23). */
    val tvModel: String,
    val tvSystem: String,
    /** The port of Plasma's page for phones (5.15), and the code it asks for now. */
    val phonePort: Int,
    val phoneCode: String,
    val pairedPhones: List<PairedPhone>,
    /** The first three favorites, for the numbering example on board 29: number, name and logo. */
    val favoriteExamples: List<Triple<Int, String, String?>>,
)

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
        playNextApps = listOf("Reelbox", "Driftwood Podcasts"),
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
        tvModel = "Google TV Streamer",
        tvSystem = "Android TV 14",
        phonePort = 8484,
        phoneCode = "7KQ 4MZ",
        pairedPhones = listOf(
            PairedPhone("Maya’s iPhone", connected = true, lastUsed = Sample.at(20, 41)),
            PairedPhone("Daniel’s Pixel", connected = false, lastUsed = Sample.at(21, 12, -1)),
        ),
        favoriteExamples = Sample.favorites.take(3).map { Triple(it.number, it.name, it.logo) },
    )
}
