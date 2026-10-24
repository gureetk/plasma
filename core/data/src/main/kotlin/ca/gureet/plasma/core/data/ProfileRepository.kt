// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.database.ProfileEntity
import ca.gureet.plasma.core.model.PlasmaClock
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Who's watching now (brief 5.11): whose favorites, history, reminders, resume points and My List
 * the screens show and keep.
 */
interface ProfileSource {
    /** The profile's id, as it changes. */
    val id: Flow<String>

    /** The profile's id now. */
    suspend fun now(): String

    /** Whether the profile watching keeps a history: a guest doesn't (5.11). */
    suspend fun keepsHistory(): Boolean = true

    companion object {
        /** Always [profileId]: before there were profiles, and in tests. */
        fun fixed(profileId: String = DEFAULT_PROFILE): ProfileSource = object : ProfileSource {
            override val id: Flow<String> = flowOf(profileId)

            override suspend fun now(): String = profileId
        }
    }
}

/** [query] for the profile watching now, and again for each one after. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> ProfileSource.watching(query: (profileId: String) -> Flow<T>): Flow<T> = id.flatMapLatest(query)

/** What a profile is (brief 5.11). */
enum class ProfileKind {
    Standard,

    /** A rating cap, kid-safe groups only, and no Settings. */
    Kids,

    /** Keeps no history, and can't manage recordings. */
    Guest,
    ;

    companion object {
        fun of(name: String): ProfileKind = entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: Standard
    }
}

/** A profile (brief 5.11). */
data class Profile(
    val id: String,
    /** Empty for the first profile until it's named: the app shows it as "Me". */
    val name: String,
    /** Its picture, one of Plasma's own; null for a figure on graphite. */
    val avatar: String?,
    val kind: ProfileKind = ProfileKind.Standard,
    /** Whether it has a PIN of its own. */
    val hasPin: Boolean = false,
    /** A kids profile's age rating: programs rated older need the parental PIN. */
    val maxAge: Int? = null,
    /** The channel it watched last, for Who's Watching. */
    val lastChannelId: String? = null,
)

/**
 * The profiles (brief 5.11): up to six, each with what it keeps of its own, and the one watching
 * now. Until one is made, there's the first, "Me", which has had everything since before
 * profiles; it can be renamed but not deleted.
 */
class ProfileRepository(
    private val db: PlasmaDatabase,
    private val settings: SettingsStore,
    private val clock: PlasmaClock,
) : ProfileSource {
    override val id: Flow<String> = settings.profile

    override suspend fun now(): String = settings.profileNow()

    override suspend fun keepsHistory(): Boolean = !isGuest(now())

    /** The profiles in their order, the first one there even before it's kept. */
    val all: Flow<List<Profile>> = combine(db.profiles().observeAll(), db.profiles().observeLastWatched()) { profiles, last ->
        val lastBy = last.associate { it.profileId to it.channelId }
        withFirst(profiles).map { it.toProfile(lastBy[it.id]) }
    }

    /** The profile watching now. */
    val current: Flow<Profile> = combine(all, id) { all, id -> all.firstOrNull { it.id == id } ?: all.first() }

    suspend fun get(id: String): Profile? = withFirst(db.profiles().all()).firstOrNull { it.id == id }?.toProfile(null)

    /** [id] watches from now on; a profile that's gone leaves the first one watching. */
    suspend fun choose(id: String) {
        settings.setProfile(id.takeIf { get(it) != null } ?: DEFAULT_PROFILE)
    }

    /** A new profile, last on Who's Watching; null when there are six already. */
    suspend fun add(name: String, avatar: String?, kind: ProfileKind, maxAge: Int?): String? {
        val profiles = withFirst(db.profiles().all())
        if (profiles.size >= MAX) return null
        // The first one is kept from now on, so its place stays first.
        profiles.firstOrNull { it.id == DEFAULT_PROFILE }?.let { db.profiles().upsert(it) }
        val id = UUID.randomUUID().toString()
        db.profiles().upsert(
            ProfileEntity(
                id = id,
                name = name.trim(),
                avatar = avatar,
                kind = kind.name.lowercase(),
                pinHash = null,
                maxAge = maxAge.takeIf { kind == ProfileKind.Kids },
                position = (profiles.maxOfOrNull { it.position } ?: 0) + 1,
                createdAt = clock.now().toEpochMilli(),
            ),
        )
        return id
    }

    /** Edit Profile: its name, picture, kind and age rating. */
    suspend fun update(id: String, name: String, avatar: String?, kind: ProfileKind, maxAge: Int?) {
        val old = withFirst(db.profiles().all()).firstOrNull { it.id == id } ?: return
        db.profiles().upsert(
            old.copy(
                name = name.trim(),
                avatar = avatar,
                // The first profile is always a standard one: it's where Plasma goes back to.
                kind = (if (id == DEFAULT_PROFILE) ProfileKind.Standard else kind).name.lowercase(),
                maxAge = maxAge.takeIf { kind == ProfileKind.Kids && id != DEFAULT_PROFILE },
            ),
        )
        // A guest from now on keeps nothing from before.
        if (kind == ProfileKind.Guest && ProfileKind.of(old.kind) != ProfileKind.Guest) forgetHistory(id)
    }

    /** Sets the profile's own PIN, kept as a salted hash (5.12); null takes it away. */
    suspend fun setPin(id: String, pin: String?) {
        val old = withFirst(db.profiles().all()).firstOrNull { it.id == id } ?: return
        db.profiles().upsert(old.copy(pinHash = pin?.let(PinHash::of)))
    }

    /** Whether [pin] is the profile's own PIN; always when it has none. */
    suspend fun isPin(id: String, pin: String): Boolean {
        val hash = db.profiles().get(id)?.pinHash ?: return true
        return PinHash.matches(hash, pin)
    }

    /**
     * Deletes the profile, with its favorites, history, reminders, resume points, My List, Jellyfin
     * sign-in, and audio and subtitle choices. The first profile stays; if the one watching goes,
     * the first watches.
     */
    suspend fun delete(id: String) {
        if (id == DEFAULT_PROFILE) return
        db.profiles().deleteWithAll(id)
        settings.forget(ProfileChoices.keysOf(id))
        if (settings.profileNow() == id) settings.setProfile(DEFAULT_PROFILE)
        if (settings.defaultProfile() == id) settings.setDefaultProfile(null)
    }

    /** A guest keeps no history: what it watched, searched for and where it left things. */
    suspend fun forgetHistory(id: String) {
        db.profiles().deleteRecents(id)
        db.profiles().deleteSearches(id)
        db.profiles().deleteResumePoints(id)
    }

    /** The profile [id] is a guest, which keeps no history (5.11). */
    suspend fun isGuest(id: String): Boolean = db.profiles().get(id)?.kind?.let(ProfileKind::of) == ProfileKind.Guest

    /** The profiles kept, with the first one in front of them when it isn't kept yet. */
    private fun withFirst(profiles: List<ProfileEntity>): List<ProfileEntity> =
        if (profiles.any { it.id == DEFAULT_PROFILE }) profiles else listOf(FIRST) + profiles

    private fun ProfileEntity.toProfile(lastChannelId: String?) = Profile(
        id = id,
        name = name,
        avatar = avatar,
        kind = ProfileKind.of(kind),
        hasPin = pinHash != null,
        maxAge = maxAge,
        lastChannelId = lastChannelId,
    )

    companion object {
        /** Who's Watching has room for six. */
        const val MAX = 6

        private val FIRST = ProfileEntity(DEFAULT_PROFILE, name = "", avatar = null, kind = "standard", pinHash = null, maxAge = null, position = 0, createdAt = 0)
    }
}
