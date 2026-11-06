// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Back Up Settings' file (brief 5.17, #108): one JSON object, the [format] and its [version]
 * first, then everything a restore puts back. Channels, groups and categories go by Plasma's ids,
 * which are the source's id and the provider's own (a stream id, a category id), so they find
 * their channels again once the sources load on another TV. The guide, channels, movies and
 * series aren't in it: the sources load them again. Nor are recordings already made, which are
 * files, the TV's own phones and keys, or the logs. Logins are in it only when the user asks, in
 * [logins], sealed with a passphrase.
 */
@Serializable
data class PlasmaBackup(
    val format: String = FORMAT,
    val version: Int = VERSION,
    /** The Plasma that made it, as About shows it. */
    val plasma: String = "",
    /** When it was made, Unix ms. */
    val made: Long = 0,
    /** Made by Plasma Update before it installed an update (brief 5.18), not by the user. */
    val beforeUpdate: Boolean = false,
    val settings: BackupSettings = BackupSettings(),
    val profiles: List<BackupProfile> = emptyList(),
    val sources: List<BackupSource> = emptyList(),
    /** Groups the user hid, locked, made kid-safe, renamed or moved, and the user's own. */
    val groups: List<BackupGroup> = emptyList(),
    /** The same for movie and series categories. */
    val vodCategories: List<BackupVodCategory> = emptyList(),
    /** What's in the user's own groups and categories. */
    val categoryMembers: List<BackupMember> = emptyList(),
    /** Channels hidden or locked on their own. */
    val channels: List<BackupChannel> = emptyList(),
    /** Channel numbers the user chose (Change Number, Duplicate Numbers). */
    val numbers: List<BackupNumber> = emptyList(),
    val favorites: List<BackupFavorite> = emptyList(),
    val recents: List<BackupRecent> = emptyList(),
    val reminders: List<BackupReminder> = emptyList(),
    /** Each channel's own sound, player, tracks and picture size. */
    val playback: List<BackupPlayback> = emptyList(),
    /** Sources picked with Switch Source. */
    val chosenSources: List<BackupChosenSource> = emptyList(),
    /** Continue Watching. */
    val resumePoints: List<BackupResume> = emptyList(),
    val myList: List<BackupListItem> = emptyList(),
    val recordingRules: List<BackupRule> = emptyList(),
    /** Recordings still to come. */
    val recordings: List<BackupRecording> = emptyList(),
    val jellyfin: List<BackupJellyfin> = emptyList(),
    val networkFolder: BackupNetworkFolder? = null,
    /** The logins, sealed with the passphrase the user chose; null for a backup made without them. */
    val logins: SealedLogins? = null,
) {
    companion object {
        const val FORMAT = "plasma-backup"
        const val VERSION = 1
    }
}

/** The settings, as [SettingsStore.backup] reads them. */
@Serializable
data class BackupSettings(
    /** Each setting's option, by its key ([ca.gureet.plasma.core.model.SettingKeys]), a profile's own with "@" and its id. */
    val choices: Map<String, Int> = emptyMap(),
    /** When each was chosen, Unix ms. */
    val chosenAt: Map<String, Long> = emptyMap(),
    val duplicateRule: String? = null,
    val unmerged: List<String> = emptyList(),
    val lastChannel: String? = null,
    val lastList: String? = null,
    val learnedKeys: List<String> = emptyList(),
    /** Home's Apps row, by package, one a line. */
    val homeApps: String? = null,
    /** Home's rows, by id, separated by commas. */
    val homeRowOrder: String? = null,
    val hiddenApps: List<String> = emptyList(),
    val localCatchUp: List<String> = emptyList(),
    val hiddenPlayNext: List<String> = emptyList(),
    val hiddenPlayNextApps: List<String> = emptyList(),
    val hiddenJellyfin: List<String> = emptyList(),
    val adultWords: String? = null,
    val kidWords: String? = null,
    val currentProfile: String? = null,
    val defaultProfile: String? = null,
)

@Serializable
data class BackupProfile(
    val id: String,
    val name: String,
    val avatar: String? = null,
    val kind: String,
    val maxAge: Int? = null,
    val position: Int = 0,
    val createdAt: Long = 0,
)

/** A source, without its logins: they're in [BackupLogins.sources]. */
@Serializable
data class BackupSource(
    val id: String,
    val type: String,
    val name: String,
    /** The server's scheme, host and port, with no login in it. */
    val server: String,
    val priority: Int,
    val enabled: Boolean = true,
    val addedAt: Long = 0,
    val stbModel: String? = null,
    val portalTimeZone: String? = null,
    val refreshHours: Int? = null,
    val streamFormat: String = "ts",
    val userAgent: String? = null,
    val epgOffsetMinutes: Int = 0,
    val catchupOffsetMinutes: Int = 0,
    val bufferChoice: Int? = null,
    val audioOutputChoice: Int? = null,
    val engineChoice: Int? = null,
    val useHub: Boolean? = null,
    val referrer: String? = null,
    val udpxy: String? = null,
    val liveOrder: String? = null,
    val movieOrder: String? = null,
    val seriesOrder: String? = null,
)

@Serializable
data class BackupGroup(
    val id: String,
    val sourceId: String,
    val remoteId: String,
    val name: String,
    val position: Int = 0,
    val hidden: Boolean = false,
    val locked: Boolean? = null,
    val kidSafe: Boolean? = null,
    val customName: String? = null,
    val userPosition: Int? = null,
    val custom: Boolean = false,
)

@Serializable
data class BackupVodCategory(
    val id: String,
    val sourceId: String,
    val kind: String,
    val remoteId: String,
    val name: String,
    val position: Int = 0,
    val hidden: Boolean = false,
    val customName: String? = null,
    val userPosition: Int? = null,
    val custom: Boolean = false,
)

@Serializable
data class BackupMember(val categoryId: String, val itemId: String, val sourceId: String, val addedAt: Long = 0)

@Serializable
data class BackupChannel(val id: String, val hidden: Boolean = false, val locked: Boolean = false)

@Serializable
data class BackupNumber(val sourceId: String, val channelKey: String, val number: Int, val setAt: Long = 0, val wantedNumber: Int? = null)

@Serializable
data class BackupFavorite(val profileId: String, val channelId: String, val position: Int)

@Serializable
data class BackupRecent(val profileId: String, val channelId: String, val watchedAt: Long)

@Serializable
data class BackupReminder(
    val id: String,
    val profileId: String,
    val channelId: String,
    val title: String,
    val startAt: Long,
    val endAt: Long,
    val createdAt: Long = 0,
)

@Serializable
data class BackupPlayback(
    val channelId: String,
    val engine: String? = null,
    val engineReason: String? = null,
    val engineSetAt: Long? = null,
    val volumeOffsetDb: Int = 0,
    val audioOutput: String? = null,
    val audioTrack: String? = null,
    val subtitleTrack: String? = null,
    val pictureSize: String? = null,
)

@Serializable
data class BackupChosenSource(val mergedChannelId: String, val preferredChannelId: String? = null, val learnedAt: Long? = null)

@Serializable
data class BackupResume(
    val profileId: String,
    val kind: String,
    val contentId: String,
    val sourceId: String,
    val seriesId: String? = null,
    val positionMs: Long,
    val durationMs: Long,
    val watched: Boolean = false,
    val updatedAt: Long = 0,
    val hidden: Boolean = false,
)

@Serializable
data class BackupListItem(val profileId: String, val kind: String, val contentId: String, val sourceId: String, val addedAt: Long = 0)

@Serializable
data class BackupRule(
    val id: String,
    val title: String,
    val channelId: String? = null,
    val channelName: String? = null,
    val channelLogo: String? = null,
    val keepLast: Int? = null,
    val keepLastSince: Long = 0,
    val createdAt: Long = 0,
)

@Serializable
data class BackupRecording(
    val id: String,
    val channelId: String,
    val channelName: String,
    val channelLogo: String? = null,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val category: String? = null,
    val image: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val kind: String,
    val startAt: Long,
    val endAt: Long,
    val padBeforeMs: Long = 0,
    val padAfterMs: Long = 0,
    val keep: Boolean = false,
    val createdAt: Long = 0,
    val ruleId: String? = null,
    val rating: String? = null,
)

/** A profile's Jellyfin sign-in, without its token: that's in [BackupLogins.jellyfinTokens]. */
@Serializable
data class BackupJellyfin(
    val profileId: String,
    val serverUrl: String,
    val serverId: String,
    val serverName: String,
    val userId: String,
    val userName: String,
    val deviceId: String,
    val signedInAt: Long = 0,
)

/** Record To's network folder, without its password: that's in [BackupLogins.networkFolderPassword]. */
@Serializable
data class BackupNetworkFolder(val host: String, val share: String, val path: String, val domain: String? = null, val username: String)

/** The logins of a backup made with them, before they're sealed. */
@Serializable
data class BackupLogins(
    /** By source id. */
    val sources: Map<String, SourceLogin> = emptyMap(),
    /** The parental PIN as kept, a salted hash; null when there's none. */
    val parentalPin: String? = null,
    /** Profiles' own PINs as kept, by profile id. */
    val profilePins: Map<String, String> = emptyMap(),
    /** Jellyfin's sign-ins, by profile id. */
    val jellyfinTokens: Map<String, String> = emptyMap(),
    val networkFolderPassword: String? = null,
)

/** A source's login: an Xtream server's, a playlist's whole address, a portal's MAC and box. */
@Serializable
data class SourceLogin(
    val username: String? = null,
    val password: String? = null,
    val playlistUrl: String? = null,
    val guideUrls: String? = null,
    val mac: String? = null,
    val extraHeaders: String? = null,
    val serialNumber: String? = null,
    val deviceId: String? = null,
    val deviceId2: String? = null,
    val signature: String? = null,
)

/** [BackupLogins], sealed by [BackupCrypto]: AES-GCM, with a key made from the passphrase by PBKDF2. */
@Serializable
data class SealedLogins(
    val kdf: String = BackupCrypto.KDF,
    val iterations: Int,
    /** Base64. */
    val salt: String,
    val cipher: String = BackupCrypto.CIPHER,
    /** Base64. */
    val iv: String,
    /** Base64: the sealed logins and GCM's tag. */
    val data: String,
)

/** What a file can't be as a backup: not one at all, or one from a newer Plasma. */
class BackupUnreadable(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason { NotABackup, Newer }
}

/** Reading and writing [PlasmaBackup] (#108). */
object BackupFile {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
    }

    fun encode(backup: PlasmaBackup): ByteArray = json.encodeToString(PlasmaBackup.serializer(), backup).toByteArray(Charsets.UTF_8)

    /** [bytes] as a backup; [BackupUnreadable] when they're not one this Plasma can read. */
    fun decode(bytes: ByteArray): PlasmaBackup {
        val backup = try {
            json.decodeFromString(PlasmaBackup.serializer(), String(bytes, Charsets.UTF_8))
        } catch (failed: SerializationException) {
            throw BackupUnreadable(BackupUnreadable.Reason.NotABackup, failed)
        } catch (failed: IllegalArgumentException) {
            throw BackupUnreadable(BackupUnreadable.Reason.NotABackup, failed)
        }
        if (backup.format != PlasmaBackup.FORMAT) throw BackupUnreadable(BackupUnreadable.Reason.NotABackup)
        if (backup.version > PlasmaBackup.VERSION) throw BackupUnreadable(BackupUnreadable.Reason.Newer)
        return backup
    }

    fun encodeLogins(logins: BackupLogins): String = json.encodeToString(BackupLogins.serializer(), logins)

    fun decodeLogins(text: String): BackupLogins = try {
        json.decodeFromString(BackupLogins.serializer(), text)
    } catch (failed: SerializationException) {
        throw BackupUnreadable(BackupUnreadable.Reason.NotABackup, failed)
    }
}

/**
 * Seals a backup's logins with the user's passphrase (brief 6.10): AES-256-GCM, with a key PBKDF2
 * makes from the passphrase and a random salt, slow enough on purpose that guessing it takes long.
 */
object BackupCrypto {
    const val KDF = "PBKDF2WithHmacSHA256"
    const val CIPHER = "AES/GCM/NoPadding"

    /** OWASP's count for PBKDF2 with SHA-256: about a second on a TV. */
    const val ITERATIONS = 600_000

    /** A file asking for more than this is refused, rather than keeping the TV busy for minutes. */
    private const val MAX_ITERATIONS = 10_000_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256

    fun seal(plain: String, passphrase: String, iterations: Int = ITERATIONS, random: SecureRandom = SecureRandom()): SealedLogins {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(CIPHER).apply { init(Cipher.ENCRYPT_MODE, key(passphrase, salt, iterations), GCMParameterSpec(TAG_BITS, iv)) }
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val base64 = Base64.getEncoder()
        return SealedLogins(iterations = iterations, salt = base64.encodeToString(salt), iv = base64.encodeToString(iv), data = base64.encodeToString(sealed))
    }

    /**
     * The logins [sealed] holds, or null when [passphrase] isn't the one they were sealed with.
     * [BackupUnreadable] when they're sealed in a way this Plasma doesn't know.
     */
    fun open(sealed: SealedLogins, passphrase: String): String? {
        if (sealed.kdf != KDF || sealed.cipher != CIPHER || sealed.iterations !in 1..MAX_ITERATIONS) {
            throw BackupUnreadable(BackupUnreadable.Reason.NotABackup)
        }
        val (salt, iv, data) = try {
            val base64 = Base64.getDecoder()
            Triple(base64.decode(sealed.salt), base64.decode(sealed.iv), base64.decode(sealed.data))
        } catch (failed: IllegalArgumentException) {
            throw BackupUnreadable(BackupUnreadable.Reason.NotABackup, failed)
        }
        return try {
            val cipher = Cipher.getInstance(CIPHER).apply { init(Cipher.DECRYPT_MODE, key(passphrase, salt, sealed.iterations), GCMParameterSpec(TAG_BITS, iv)) }
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (wrong: AEADBadTagException) {
            null
        } catch (failed: GeneralSecurityException) {
            throw BackupUnreadable(BackupUnreadable.Reason.NotABackup, failed)
        }
    }

    private fun key(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        // The same passphrase, however the keyboard composed its letters.
        val chars = Normalizer.normalize(passphrase, Normalizer.Form.NFC).toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, KEY_BITS)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
            chars.fill(' ')
        }
    }
}
