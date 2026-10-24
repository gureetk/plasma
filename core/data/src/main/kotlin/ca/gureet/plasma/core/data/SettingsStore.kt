// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import ca.gureet.plasma.channels.NumberKeys
import ca.gureet.plasma.channels.NumberSource
import ca.gureet.plasma.core.model.DuplicateRule
import ca.gureet.plasma.core.model.SettingKeys
import ca.gureet.plasma.core.model.SettingsChoices
import ca.gureet.plasma.source.stalker.StalkerDevice
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

    suspend fun choose(key: String, index: Int) {
        store.edit { it[intPreferencesKey(CHOICE + key)] = index }
    }

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
     * The MAC address this TV offers a Stalker portal (brief 7.1): made once, in MAG's range, and
     * kept, so it's the same one the provider registers.
     */
    suspend fun deviceMac(): String {
        store.data.first()[DEVICE_MAC]?.let { return it }
        var mac = ""
        store.edit { prefs -> mac = prefs[DEVICE_MAC] ?: StalkerDevice.newMac().also { prefs[DEVICE_MAC] = it } }
        return mac
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
        val SCREENSAVER_BEFORE = stringPreferencesKey("screensaver.before")
        val HIDDEN_PLAY_NEXT = stringSetPreferencesKey("playNext.hidden")
        val HIDDEN_PLAY_NEXT_APPS = stringSetPreferencesKey("playNext.hiddenApps")
    }
}
