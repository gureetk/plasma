// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.database.CategoryMemberEntity
import ca.gureet.plasma.core.database.ChannelPlaybackEntity
import ca.gureet.plasma.core.database.ChannelSourceMemoryEntity
import ca.gureet.plasma.core.database.FavoriteEntity
import ca.gureet.plasma.core.database.GroupEntity
import ca.gureet.plasma.core.database.JellyfinAccountEntity
import ca.gureet.plasma.core.database.MyListEntity
import ca.gureet.plasma.core.database.NetworkFolderEntity
import ca.gureet.plasma.core.database.NumberOverrideEntity
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.database.ProfileEntity
import ca.gureet.plasma.core.database.RecentChannelEntity
import ca.gureet.plasma.core.database.RecordingEntity
import ca.gureet.plasma.core.database.RecordingRuleEntity
import ca.gureet.plasma.core.database.ReminderEntity
import ca.gureet.plasma.core.database.RestoredRows
import ca.gureet.plasma.core.database.ResumePointEntity
import ca.gureet.plasma.core.database.SourceEntity
import ca.gureet.plasma.core.database.VodCategoryEntity
import ca.gureet.plasma.core.model.PlasmaClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a backup holds, for Restore from Backup to show before it replaces anything (#108). */
data class BackupPreview(
    /** When it was made, Unix ms. */
    val made: Long,
    /** The Plasma that made it. */
    val plasma: String,
    val beforeUpdate: Boolean,
    /** Its sources' names, the primary first. */
    val sources: List<String>,
    val profiles: Int,
    val favorites: Int,
    /** Reminders, recording rules and recordings still to come. */
    val plans: Int,
    /** Its logins are sealed with a passphrase. */
    val withLogins: Boolean,
)

/** How Restore from Backup went (#108). */
sealed interface RestoreResult {
    /** The passphrase isn't the backup's: nothing changed. */
    data object WrongPassphrase : RestoreResult

    /** Everything's in place; [needLogins] names the sources that came back without their logins. */
    data class Restored(val sources: Int, val needLogins: List<String>) : RestoreResult
}

/**
 * Back Up Settings and Restore from Backup (brief 5.17 and 5.18, #108): the settings, sources,
 * favorites, profiles, rules and orders in one file ([PlasmaBackup]), and that file put back, on
 * this TV or another. Logins go in only with a passphrase, and a backup without them keeps this
 * TV's own for the sources it has too, as a backup made before an update does on the same TV.
 */
class Backups internal constructor(
    private val database: PlasmaDatabase,
    private val settings: SettingsStore,
    private val vault: Vault,
    private val clock: PlasmaClock,
    private val iterations: Int = BackupCrypto.ITERATIONS,
) {
    private val dao get() = database.backups()

    /**
     * A backup of everything (5.17), by the Plasma [plasma]; with the logins sealed with
     * [passphrase], or without them when it's null. [beforeUpdate] marks one Plasma Update made.
     */
    suspend fun make(plasma: String, passphrase: String?, beforeUpdate: Boolean = false): PlasmaBackup = withContext(Dispatchers.IO) {
        val now = clock.now().toEpochMilli()
        val sources = dao.sources()
        val profiles = dao.profiles()
        val jellyfin = dao.jellyfin()
        val folder = dao.networkFolders().firstOrNull()
        val logins = passphrase?.let {
            BackupLogins(
                sources = sources.associate { it.id to it.login() },
                parentalPin = settings.pinHash(),
                profilePins = profiles.mapNotNull { profile -> profile.pinHash?.let { profile.id to it } }.toMap(),
                jellyfinTokens = jellyfin.mapNotNull { account -> openOrNull(account.accessTokenEnc)?.let { account.profileId to it } }.toMap(),
                networkFolderPassword = folder?.passwordEnc?.let(::openOrNull),
            )
        }
        PlasmaBackup(
            plasma = plasma,
            made = now,
            beforeUpdate = beforeUpdate,
            settings = settings.backup(),
            profiles = profiles.map { BackupProfile(it.id, it.name, it.avatar, it.kind, it.maxAge, it.position, it.createdAt) },
            sources = sources.map { it.toBackup() },
            groups = dao.arrangedGroups().map {
                BackupGroup(it.id, it.sourceId, it.remoteId, it.name, it.position, it.hidden, it.locked, it.kidSafe, it.customName, it.userPosition, it.custom)
            },
            vodCategories = dao.arrangedVodCategories().map {
                BackupVodCategory(it.id, it.sourceId, it.kind, it.remoteId, it.name, it.position, it.hidden, it.customName, it.userPosition, it.custom)
            },
            categoryMembers = dao.categoryMembers().map { BackupMember(it.categoryId, it.itemId, it.sourceId, it.addedAt) },
            channels = dao.channelFlags().map { BackupChannel(it.id, it.hidden, it.locked) },
            numbers = dao.numberOverrides().map { BackupNumber(it.sourceId, it.channelKey, it.number, it.setAt, it.wantedNumber) },
            favorites = dao.favorites().map { BackupFavorite(it.profileId, it.channelId, it.position) },
            recents = dao.recents().map { BackupRecent(it.profileId, it.channelId, it.watchedAt) },
            reminders = dao.reminders(now).map { BackupReminder(it.id, it.profileId, it.channelId, it.title, it.startAt, it.endAt, it.createdAt) },
            playback = dao.playback().map {
                BackupPlayback(it.channelId, it.engine, it.engineReason, it.engineSetAt, it.volumeOffsetDb, it.audioOutput, it.audioTrack, it.subtitleTrack, it.pictureSize)
            },
            chosenSources = dao.chosenSources().map { BackupChosenSource(it.mergedChannelId, it.preferredChannelId, it.learnedAt) },
            resumePoints = dao.resumePoints().map {
                BackupResume(it.profileId, it.kind, it.contentId, it.sourceId, it.seriesId, it.positionMs, it.durationMs, it.watched, it.updatedAt, it.hidden)
            },
            myList = dao.myList().map { BackupListItem(it.profileId, it.kind, it.contentId, it.sourceId, it.addedAt) },
            recordingRules = dao.recordingRules().map {
                BackupRule(it.id, it.title, it.channelId, it.channelName, it.channelLogo, it.keepLast, it.keepLastSince, it.createdAt)
            },
            recordings = dao.scheduledRecordings(now).map {
                BackupRecording(
                    it.id, it.channelId, it.channelName, it.channelLogo, it.title, it.subtitle, it.description, it.category, it.image,
                    it.season, it.episode, it.kind, it.startAt, it.endAt, it.padBeforeMs, it.padAfterMs, it.keep, it.createdAt, it.ruleId, it.rating,
                )
            },
            jellyfin = jellyfin.map { BackupJellyfin(it.profileId, it.serverUrl, it.serverId, it.serverName, it.userId, it.userName, it.deviceId, it.signedInAt) },
            networkFolder = folder?.let { BackupNetworkFolder(it.host, it.share, it.path, it.domain, it.username) },
            logins = logins?.let { BackupCrypto.seal(BackupFile.encodeLogins(it), passphrase, iterations) },
        )
    }

    /** What [backup] holds, to show before restoring it. */
    fun preview(backup: PlasmaBackup): BackupPreview = BackupPreview(
        made = backup.made,
        plasma = backup.plasma,
        beforeUpdate = backup.beforeUpdate,
        sources = backup.sources.sortedBy { it.priority }.map { it.name },
        profiles = backup.profiles.size,
        favorites = backup.favorites.size,
        plans = backup.reminders.size + backup.recordingRules.size + backup.recordings.size,
        withLogins = backup.logins != null,
    )

    /**
     * Puts [backup] in place of what's here (5.17): its sources, profiles, favorites, categories,
     * channels' own choices, plans and settings. With [passphrase], its logins too; a wrong one
     * changes nothing. Without, this TV's own logins stay for the sources it has too. The sources
     * then load as usual: their hidden and locked channels follow them ([SourceImporter]).
     */
    suspend fun restore(backup: PlasmaBackup, passphrase: String?): RestoreResult = withContext(Dispatchers.IO) {
        val logins = backup.logins?.takeIf { passphrase != null }?.let { sealed ->
            val opened = BackupCrypto.open(sealed, passphrase!!) ?: return@withContext RestoreResult.WrongPassphrase
            BackupFile.decodeLogins(opened)
        }
        val now = clock.now().toEpochMilli()
        val here = dao.sources().associateBy { it.id }
        val hereProfiles = dao.profiles().associateBy { it.id }
        val hereJellyfin = dao.jellyfin().associateBy { it.profileId }
        val hereFolder = dao.networkFolders().firstOrNull()

        val sources = backup.sources.map { source ->
            val login = logins?.sources?.get(source.id)?.let { sealed(it) } ?: here[source.id]?.loginColumns() ?: LoginColumns()
            source.toEntity(login)
        }
        val kept = sources.map { it.id }.toSet()
        val profiles = backup.profiles.map { profile ->
            val pin = if (logins != null) logins.profilePins[profile.id] else hereProfiles[profile.id]?.pinHash
            ProfileEntity(profile.id, profile.name, profile.avatar, profile.kind, pin, profile.maxAge, profile.position, profile.createdAt)
        }
        val jellyfin = backup.jellyfin.mapNotNull { account ->
            val token = logins?.jellyfinTokens?.get(account.profileId)?.let(vault::seal)
                ?: hereJellyfin[account.profileId]?.takeIf { it.serverId == account.serverId && it.userId == account.userId }?.accessTokenEnc
                ?: return@mapNotNull null
            JellyfinAccountEntity(account.profileId, account.serverUrl, account.serverId, account.serverName, account.userId, account.userName, token, account.deviceId, account.signedInAt)
        }
        val folder = backup.networkFolder?.let { wanted ->
            val password = logins?.networkFolderPassword?.let(vault::seal)
                ?: hereFolder?.takeIf { it.host == wanted.host && it.share == wanted.share && it.username == wanted.username }?.passwordEnc
                ?: return@let null
            NetworkFolderEntity(NETWORK_FOLDER, wanted.host, wanted.share, wanted.path, wanted.domain, wanted.username, password, freeBytes = null, checkedAt = 0)
        }
        val rows = RestoredRows(
            sources = sources,
            profiles = profiles,
            groups = backup.groups.filter { it.sourceId in kept }.map {
                GroupEntity(it.id, it.sourceId, it.remoteId, it.name, it.position, it.hidden, generation = 0, it.locked, it.kidSafe, it.customName, it.userPosition, it.custom)
            },
            vodCategories = backup.vodCategories.filter { it.sourceId in kept }.map {
                VodCategoryEntity(it.id, it.sourceId, it.kind, it.remoteId, it.name, it.position, generation = 0, it.hidden, it.customName, it.userPosition, it.custom)
            },
            categoryMembers = backup.categoryMembers.filter { it.sourceId in kept }.map { CategoryMemberEntity(it.categoryId, it.itemId, it.sourceId, it.addedAt) },
            numberOverrides = backup.numbers.filter { it.sourceId in kept }.map { NumberOverrideEntity(it.sourceId, it.channelKey, it.number, it.setAt, it.wantedNumber) },
            favorites = backup.favorites.map { FavoriteEntity(it.profileId, it.channelId, it.position) },
            recents = backup.recents.map { RecentChannelEntity(it.profileId, it.channelId, it.watchedAt) },
            reminders = backup.reminders.filter { it.startAt > now }.map { ReminderEntity(it.id, it.profileId, it.channelId, it.title, it.startAt, it.endAt, it.createdAt) },
            playback = backup.playback.map {
                ChannelPlaybackEntity(it.channelId, it.engine, it.engineReason, it.engineSetAt, it.volumeOffsetDb, it.audioOutput, it.audioTrack, it.subtitleTrack, it.pictureSize)
            },
            sourceMemory = backup.chosenSources.map {
                ChannelSourceMemoryEntity(it.mergedChannelId, it.preferredChannelId, reason = USER, learnedAt = it.learnedAt, recheckAfter = null, recentResults = "")
            },
            resumePoints = backup.resumePoints.filter { it.sourceId in kept }.map {
                ResumePointEntity(it.profileId, it.kind, it.contentId, it.sourceId, it.seriesId, it.positionMs, it.durationMs, it.watched, it.updatedAt, it.hidden)
            },
            myList = backup.myList.filter { it.sourceId in kept }.map { MyListEntity(it.profileId, it.kind, it.contentId, it.sourceId, it.addedAt) },
            recordingRules = backup.recordingRules.map {
                RecordingRuleEntity(it.id, it.title, it.channelId, it.channelName, it.channelLogo, it.keepLast, it.keepLastSince, it.createdAt)
            },
            scheduledRecordings = backup.recordings.filter { it.startAt > now }.map { it.toEntity() },
            jellyfin = jellyfin,
            networkFolders = listOfNotNull(folder),
        )
        dao.replace(rows)
        settings.restore(
            backup.settings,
            pinHash = logins?.parentalPin,
            replacePin = logins != null,
            hidden = backup.channels.filter { it.hidden }.map { it.id },
            locked = backup.channels.filter { it.locked }.map { it.id },
        )
        RestoreResult.Restored(sources.size, needLogins = sources.filterNot { it.hasLogin() }.map { it.name })
    }

    private fun SourceEntity.login(): SourceLogin = SourceLogin(
        username = username,
        password = passwordEnc?.let(::openOrNull),
        playlistUrl = playlistUrlEnc?.let(::openOrNull),
        guideUrls = guideUrlsEnc?.let(::openOrNull),
        mac = macEnc?.let(::openOrNull),
        extraHeaders = extraHeadersEnc?.let(::openOrNull),
        serialNumber = serialNumber,
        deviceId = deviceId,
        deviceId2 = deviceId2,
        signature = signature,
    )

    /** A source's sealed columns, sealed by this TV: from a backup's login, or kept from the source here. */
    private data class LoginColumns(
        val username: String? = null,
        val passwordEnc: String? = null,
        val playlistUrlEnc: String? = null,
        val guideUrlsEnc: String? = null,
        val macEnc: String? = null,
        val extraHeadersEnc: String? = null,
        val serialNumber: String? = null,
        val deviceId: String? = null,
        val deviceId2: String? = null,
        val signature: String? = null,
    )

    private fun sealed(login: SourceLogin) = LoginColumns(
        username = login.username,
        passwordEnc = login.password?.let(vault::seal),
        playlistUrlEnc = login.playlistUrl?.let(vault::seal),
        guideUrlsEnc = login.guideUrls?.let(vault::seal),
        macEnc = login.mac?.let(vault::seal),
        extraHeadersEnc = login.extraHeaders?.let(vault::seal),
        serialNumber = login.serialNumber,
        deviceId = login.deviceId,
        deviceId2 = login.deviceId2,
        signature = login.signature,
    )

    private fun SourceEntity.loginColumns() = LoginColumns(
        username, passwordEnc, playlistUrlEnc, guideUrlsEnc, macEnc, extraHeadersEnc, serialNumber, deviceId, deviceId2, signature,
    )

    private fun SourceEntity.toBackup() = BackupSource(
        id = id, type = type, name = name, server = server, priority = priority, enabled = enabled, addedAt = addedAt,
        stbModel = stbModel, portalTimeZone = portalTimeZone, refreshHours = refreshHours, streamFormat = streamFormat,
        userAgent = userAgent, epgOffsetMinutes = epgOffsetMinutes, catchupOffsetMinutes = catchupOffsetMinutes,
        bufferChoice = bufferChoice, audioOutputChoice = audioOutputChoice, engineChoice = engineChoice, useHub = useHub,
        referrer = referrer, udpxy = udpxy, liveOrder = liveOrder, movieOrder = movieOrder, seriesOrder = seriesOrder,
    )

    private fun BackupSource.toEntity(login: LoginColumns) = SourceEntity(
        id = id, type = type, name = name, server = server,
        username = login.username, passwordEnc = login.passwordEnc, playlistUrlEnc = login.playlistUrlEnc,
        guideUrlsEnc = login.guideUrlsEnc, macEnc = login.macEnc, stbModel = stbModel,
        serialNumber = login.serialNumber, deviceId = login.deviceId, deviceId2 = login.deviceId2, signature = login.signature,
        portalTimeZone = portalTimeZone, refreshHours = refreshHours, priority = priority, enabled = enabled, addedAt = addedAt,
        streamFormat = streamFormat, userAgent = userAgent, epgOffsetMinutes = epgOffsetMinutes, bufferChoice = bufferChoice,
        audioOutputChoice = audioOutputChoice, catchupOffsetMinutes = catchupOffsetMinutes, engineChoice = engineChoice,
        useHub = useHub, referrer = referrer, extraHeadersEnc = login.extraHeadersEnc, udpxy = udpxy,
        liveOrder = liveOrder, movieOrder = movieOrder, seriesOrder = seriesOrder,
    )

    private fun BackupRecording.toEntity() = RecordingEntity(
        id = id, channelId = channelId, channelName = channelName, channelLogo = channelLogo, title = title, subtitle = subtitle,
        description = description, category = category, image = image, season = season, episode = episode, kind = kind,
        startAt = startAt, endAt = endAt, padBeforeMs = padBeforeMs, padAfterMs = padAfterMs, status = SCHEDULED, filePath = null,
        sizeBytes = 0, recordedFrom = null, recordedTo = null, interruptions = 0, problem = null, keep = keep, positionMs = 0,
        watched = false, watchedAt = 0, createdAt = createdAt, ruleId = ruleId, rating = rating,
    )

    /** Whether a source has what it signs in with: an Xtream password, a playlist's address, a portal's MAC. */
    private fun SourceEntity.hasLogin(): Boolean = when (SourceType.of(type)) {
        SourceType.Xtream -> passwordEnc != null && username != null
        SourceType.M3u -> playlistUrlEnc != null
        SourceType.Stalker -> macEnc != null
    }

    private fun openOrNull(sealed: String): String? = runCatching { vault.open(sealed) }.getOrNull()

    private companion object {
        const val NETWORK_FOLDER = "main"
        const val USER = "USER"
        const val SCHEDULED = "scheduled"
    }
}
