// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import ca.gureet.plasma.channels.NumberKeys
import ca.gureet.plasma.channels.NumberSource
import ca.gureet.plasma.core.model.AdultWords
import ca.gureet.plasma.core.model.DuplicateRule
import ca.gureet.plasma.core.model.SettingKeys
import ca.gureet.plasma.core.model.SettingsChoices
import ca.gureet.plasma.source.stalker.StalkerDevice
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Settings › Channels (brief 4.29 and 5.2), with the brief's defaults. */
data class NumberingSettings(
    val source: NumberSource = NumberSource.Playlists,
    val duplicateRule: DuplicateRule = DuplicateRule.AddThousands,
    val favoritesCountFromOne: Boolean = true,
    val groupsCountFromOne: Boolean = false,
    val numberKeys: NumberKeys = NumberKeys.CurrentListFirst,
)

/** The channel last watched, and the list it was tuned from, so zapping follows it (brief 5.2). */
data class LastWatched(val channelId: String, val listId: String?)

/**
 * Settings kept with DataStore (brief 6.3). What's chosen in Settings is kept as it chooses it,
 * each setting's option index by its key ([SettingKeys]); what Plasma acts on is read from there.
 */
class SettingsStore(private val store: DataStore<Preferences>) {
    /** Everything chosen in Settings so far; settings never changed keep their defaults. */
    val choices: Flow<Map<String, Int>> = store.data.map { prefs ->
        buildMap {
            for ((key, value) in prefs.asMap()) {
                if (key.name.startsWith(CHOICE) && value is Int) put(key.name.removePrefix(CHOICE), value)
            }
        }
    }.distinctUntilChanged()

    suspend fun choose(key: String, index: Int, at: Instant = Instant.now()) {
        store.edit {
            it[intPreferencesKey(CHOICE + key)] = index
            it[longPreferencesKey(CHOSEN_AT + key)] = at.toEpochMilli()
        }
    }

    /** When [key] was last chosen in Settings; null if it never was. */
    fun chosenAt(key: String): Flow<Instant?> = store.data.map { prefs -> prefs[longPreferencesKey(CHOSEN_AT + key)]?.let(Instant::ofEpochMilli) }.distinctUntilChanged()

    val numbering: Flow<NumberingSettings> = store.data.map { prefs ->
        val chosen = SettingsChoices(
            buildMap {
                for (key in NUMBERING_KEYS) prefs[intPreferencesKey(CHOICE + key)]?.let { put(key, it) }
            },
        )
        NumberingSettings(
            source = if (chosen.numbersInPlaylistOrder) NumberSource.PlaylistOrder else NumberSource.Playlists,
            duplicateRule = prefs[DUPLICATE_RULE]?.let { runCatching { DuplicateRule.valueOf(it) }.getOrNull() } ?: DuplicateRule.AddThousands,
            favoritesCountFromOne = chosen.favoritesCountFromOne,
            groupsCountFromOne = chosen.groupsCountFromOne,
            numberKeys = if (chosen.numberKeysFullListOnly) NumberKeys.FullListOnly else NumberKeys.CurrentListFirst,
        )
    }.distinctUntilChanged()

    suspend fun numberingNow(): NumberingSettings = numbering.first()

    suspend fun setDuplicateRule(rule: DuplicateRule) {
        store.edit { it[DUPLICATE_RULE] = rule.name }
    }

    val lastWatched: Flow<LastWatched?> = store.data.map { prefs ->
        prefs[LAST_CHANNEL]?.let { LastWatched(it, prefs[LAST_LIST]) }
    }.distinctUntilChanged()

    suspend fun setLastWatched(channelId: String, listId: String?) {
        store.edit {
            it[LAST_CHANNEL] = channelId
            if (listId != null) it[LAST_LIST] = listId else it.remove(LAST_LIST)
        }
    }

    /**
     * Keys taught in Settings › Remote & Buttons › Learn a Key: each key's id
     * ([ca.gureet.plasma.core.model.RemoteKeys.idOf]) and the key code it acts as.
     */
    val learnedKeys: Flow<Map<String, Int>> = store.data.map { prefs ->
        prefs[LEARNED_KEYS].orEmpty().mapNotNull { entry ->
            val id = entry.substringBefore('=')
            entry.substringAfter('=', "").toIntOrNull()?.takeIf { id.isNotEmpty() }?.let { id to it }
        }.toMap()
    }

    suspend fun learnKey(id: String, actsAs: Int) {
        store.edit { prefs -> prefs[LEARNED_KEYS] = prefs[LEARNED_KEYS].orEmpty().filterNot { it.substringBefore('=') == id }.toSet() + "$id=$actsAs" }
    }

    suspend fun forgetKey(id: String) {
        store.edit { prefs -> prefs[LEARNED_KEYS] = prefs[LEARNED_KEYS].orEmpty().filterNot { it.substringBefore('=') == id }.toSet() }
    }

    /**
     * Channels unmerged on Duplicate Numbers (brief 4.30), by what made them the same channel
     * ([ca.gureet.plasma.channels.ChannelMerger.identityOf]).
     */
    val unmerged: Flow<Set<String>> = store.data.map { it[UNMERGED].orEmpty() }.distinctUntilChanged()

    suspend fun unmerge(identity: String) {
        store.edit { it[UNMERGED] = it[UNMERGED].orEmpty() + identity }
    }

    /** Merges every unmerged channel again: Merge Duplicate Channels was turned back on. */
    suspend fun mergeAgain() {
        store.edit { it.remove(UNMERGED) }
    }

    /**
     * Home's Apps row (brief 4.4 and 5.13): its apps by package, in its order, once it has been
     * arranged; null until then.
     */
    val homeApps: Flow<List<String>?> = store.data.map { prefs ->
        prefs[HOME_APPS]?.split('\n')?.filter { it.isNotEmpty() }
    }.distinctUntilChanged()

    suspend fun setHomeApps(packages: List<String>) {
        store.edit { it[HOME_APPS] = packages.joinToString("\n") }
    }

    /** Local catch-up's channels (brief 5.5), by id: recorded all the time into a rolling window. */
    val localCatchUp: Flow<Set<String>> = store.data.map { it[LOCAL_CATCH_UP].orEmpty() }.distinctUntilChanged()

    suspend fun setLocalCatchUp(channelId: String, on: Boolean) {
        store.edit { prefs ->
            val now = prefs[LOCAL_CATCH_UP].orEmpty()
            prefs[LOCAL_CATCH_UP] = if (on) now + channelId else now - channelId
        }
    }

    /** Apps hidden on Apps (4.4), by package, until Settings › Launcher & Home shows them again. */
    val hiddenApps: Flow<Set<String>> = store.data.map { it[HIDDEN_APPS].orEmpty() }.distinctUntilChanged()

    suspend fun setAppHidden(packageName: String, hidden: Boolean) {
        store.edit { prefs ->
            val now = prefs[HIDDEN_APPS].orEmpty()
            prefs[HIDDEN_APPS] = if (hidden) now + packageName else now - packageName
        }
    }

    /**
     * Other apps' Play Next items hidden from Continue Watching (brief 5.19), by their keys
     * ("package|id"), and apps hidden whole, by package.
     */
    val hiddenPlayNext: Flow<Set<String>> = store.data.map { it[HIDDEN_PLAY_NEXT].orEmpty() }.distinctUntilChanged()
    val hiddenPlayNextApps: Flow<Set<String>> = store.data.map { it[HIDDEN_PLAY_NEXT_APPS].orEmpty() }.distinctUntilChanged()

    suspend fun hidePlayNext(keys: Set<String>) {
        store.edit { it[HIDDEN_PLAY_NEXT] = it[HIDDEN_PLAY_NEXT].orEmpty() + keys }
    }

    /**
     * Jellyfin items hidden from Continue Watching with Hide from Plasma (brief 5.19): each item's
     * id, with when it had last been watched as it was hidden. Watching it again brings it back.
     */
    val hiddenJellyfin: Flow<Map<String, Long>> = store.data.map { prefs ->
        prefs[HIDDEN_JELLYFIN].orEmpty().associate { entry -> entry.substringBeforeLast('@') to (entry.substringAfterLast('@').toLongOrNull() ?: 0L) }
    }.distinctUntilChanged()

    suspend fun hideJellyfin(id: String, lastWatched: Instant?) {
        store.edit { prefs ->
            val others = prefs[HIDDEN_JELLYFIN].orEmpty().filterNot { it.substringBeforeLast('@') == id }
            prefs[HIDDEN_JELLYFIN] = (others + "$id@${lastWatched?.toEpochMilli() ?: 0L}").toSet()
        }
    }

    suspend fun setPlayNextAppHidden(packageName: String, hidden: Boolean) {
        store.edit { prefs ->
            val now = prefs[HIDDEN_PLAY_NEXT_APPS].orEmpty()
            prefs[HIDDEN_PLAY_NEXT_APPS] = if (hidden) now + packageName else now - packageName
        }
    }

    /**
     * The TV's own screensaver from before Plasma took its place (brief 5.14), to put back; null
     * when there's none to put back (the TV's default shows then).
     */
    suspend fun tvScreensaverBefore(): String? = store.data.first()[SCREENSAVER_BEFORE]

    suspend fun setTvScreensaverBefore(component: String?) {
        store.edit { if (component != null) it[SCREENSAVER_BEFORE] = component else it.remove(SCREENSAVER_BEFORE) }
    }

    /**
     * Plasma replaced the TV's own Home (brief 5.13, method 2), so a TV update that turns it back
     * on is worth a word; false once it's restored, or the user would rather not be asked.
     */
    suspend fun homeReplaced(): Boolean = store.data.first()[HOME_REPLACED] ?: false

    suspend fun setHomeReplaced(replaced: Boolean) {
        store.edit { it[HOME_REPLACED] = replaced }
    }

    /** The boot Plasma last asked about the TV's Home coming back, so it asks once a boot at most. */
    suspend fun homeBackAskedBoot(): Int? = store.data.first()[HOME_BACK_ASKED]

    suspend fun setHomeBackAskedBoot(boot: Int) {
        store.edit { it[HOME_BACK_ASKED] = boot }
    }

    /**
     * The MAC address this TV offers a Stalker portal (brief 7.1): made once, in MAG's range, and
     * kept, so it's the same one the provider registers.
     */
    suspend fun deviceMac(): String {
        store.data.first()[DEVICE_MAC]?.let { return it }
        var mac = ""
        store.edit { prefs -> mac = prefs[DEVICE_MAC] ?: StalkerDevice.newMac().also { prefs[DEVICE_MAC] = it } }
        return mac
    }

    /** Whether a parental PIN is set (brief 5.12): parental controls are on once there's one. */
    val pinSet: Flow<Boolean> = store.data.map { it[PARENTAL_PIN] != null }.distinctUntilChanged()

    /** Sets the parental PIN, kept as a salted hash (brief 5.12); null takes it away. */
    suspend fun setPin(pin: String?) {
        val stored = pin?.let { PinHash.of(it) }
        store.edit { prefs -> if (stored == null) prefs.remove(PARENTAL_PIN) else prefs[PARENTAL_PIN] = stored }
    }

    /** Whether [pin] is the parental PIN; never when none is set. */
    suspend fun isPin(pin: String): Boolean = store.data.first()[PARENTAL_PIN]?.let { PinHash.matches(it, pin) } ?: false

    /** The profile watching now (brief 5.11); the first one until another is chosen. */
    val profile: Flow<String> = store.data.map { it[CURRENT_PROFILE] ?: DEFAULT_PROFILE }.distinctUntilChanged()

    suspend fun profileNow(): String = store.data.first()[CURRENT_PROFILE] ?: DEFAULT_PROFILE

    suspend fun setProfile(id: String) {
        store.edit { it[CURRENT_PROFILE] = id }
    }

    /** Default Profile (5.11): who's watching when the TV wakes without asking; null stays with the last one. */
    val defaultProfileId: Flow<String?> = store.data.map { it[DEFAULT_PROFILE_ID] }.distinctUntilChanged()

    suspend fun defaultProfile(): String? = store.data.first()[DEFAULT_PROFILE_ID]

    suspend fun setDefaultProfile(id: String?) {
        store.edit { prefs -> if (id == null) prefs.remove(DEFAULT_PROFILE_ID) else prefs[DEFAULT_PROFILE_ID] = id }
    }

    /** Lock Adult Groups' words (brief 5.12): a group with one in its name is adult. */
    val adultWords: Flow<AdultWords> = store.data.map { it[ADULT_WORDS] ?: AdultWords.DEFAULT }.distinctUntilChanged().map(AdultWords::parse)

    /** The adult words as typed, separated by commas; none at all leaves no group adult. */
    suspend fun setAdultWords(text: String) {
        store.edit { it[ADULT_WORDS] = AdultWords.parse(text).toString() }
    }

    /** The key that encrypts channel addresses, sealed by the Keystore (brief 6.10). */
    internal suspend fun sealedDataKey(): String? = store.data.first()[DATA_KEY]

    internal suspend fun setSealedDataKey(sealed: String) {
        store.edit { it[DATA_KEY] = sealed }
    }

    /** Plasma's ADB key, sealed by the Keystore (brief 5.13). */
    internal suspend fun sealedAdbKey(): String? = store.data.first()[ADB_KEY]

    internal suspend fun setSealedAdbKey(sealed: String) {
        store.edit { it[ADB_KEY] = sealed }
    }

    private companion object {
        const val CHOICE = "choice."
        const val CHOSEN_AT = "chosenAt."
        val NUMBERING_KEYS = listOf(SettingKeys.CHANNEL_NUMBERS, SettingKeys.FAVORITE_NUMBERS, SettingKeys.GROUP_NUMBERS, SettingKeys.NUMBER_KEYS)
        val DUPLICATE_RULE = stringPreferencesKey("channels.duplicateRule")
        val UNMERGED = stringSetPreferencesKey("channels.unmerged")
        val LAST_CHANNEL = stringPreferencesKey("watching.lastChannel")
        val LAST_LIST = stringPreferencesKey("watching.lastList")
        val DATA_KEY = stringPreferencesKey("secrets.dataKey")
        val LEARNED_KEYS = stringSetPreferencesKey("remote.learnedKeys")
        val DEVICE_MAC = stringPreferencesKey("stalker.deviceMac")
        val ADB_KEY = stringPreferencesKey("adb.key")
        val HOME_APPS = stringPreferencesKey("apps.homeRow")
        val HIDDEN_APPS = stringSetPreferencesKey("apps.hidden")
        val LOCAL_CATCH_UP = stringSetPreferencesKey("localCatchUp.channels")
        val SCREENSAVER_BEFORE = stringPreferencesKey("screensaver.before")
        val HIDDEN_PLAY_NEXT = stringSetPreferencesKey("playNext.hidden")
        val HIDDEN_PLAY_NEXT_APPS = stringSetPreferencesKey("playNext.hiddenApps")
        val HIDDEN_JELLYFIN = stringSetPreferencesKey("jellyfin.hidden")
        val HOME_REPLACED = booleanPreferencesKey("home.replaced")
        val HOME_BACK_ASKED = intPreferencesKey("home.backAskedBoot")
        val PARENTAL_PIN = stringPreferencesKey("parental.pin")
        val ADULT_WORDS = stringPreferencesKey("parental.adultWords")
        val CURRENT_PROFILE = stringPreferencesKey("profile.current")
        val DEFAULT_PROFILE_ID = stringPreferencesKey("profile.default")
    }
}
