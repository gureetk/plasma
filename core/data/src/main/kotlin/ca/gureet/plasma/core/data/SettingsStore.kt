// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import ca.gureet.plasma.channels.NumberKeys
import ca.gureet.plasma.channels.NumberSource
import ca.gureet.plasma.core.model.DuplicateRule
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

/** Settings kept with DataStore (brief 6.3); what screens show of them comes from here. */
class SettingsStore(private val store: DataStore<Preferences>) {
    val numbering: Flow<NumberingSettings> = store.data.map { prefs ->
        NumberingSettings(
            source = prefs[NUMBER_SOURCE]?.let { runCatching { NumberSource.valueOf(it) }.getOrNull() } ?: NumberSource.Playlists,
            duplicateRule = prefs[DUPLICATE_RULE]?.let { runCatching { DuplicateRule.valueOf(it) }.getOrNull() } ?: DuplicateRule.AddThousands,
            favoritesCountFromOne = prefs[FAVORITES_FROM_ONE] ?: true,
            groupsCountFromOne = prefs[GROUPS_FROM_ONE] ?: false,
            numberKeys = prefs[NUMBER_KEYS]?.let { runCatching { NumberKeys.valueOf(it) }.getOrNull() } ?: NumberKeys.CurrentListFirst,
        )
    }.distinctUntilChanged()

    suspend fun numberingNow(): NumberingSettings = numbering.first()

    suspend fun setNumbering(settings: NumberingSettings) {
        store.edit {
            it[NUMBER_SOURCE] = settings.source.name
            it[DUPLICATE_RULE] = settings.duplicateRule.name
            it[FAVORITES_FROM_ONE] = settings.favoritesCountFromOne
            it[GROUPS_FROM_ONE] = settings.groupsCountFromOne
            it[NUMBER_KEYS] = settings.numberKeys.name
        }
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

    private companion object {
        val NUMBER_SOURCE = stringPreferencesKey("channels.numberSource")
        val DUPLICATE_RULE = stringPreferencesKey("channels.duplicateRule")
        val FAVORITES_FROM_ONE = booleanPreferencesKey("channels.favoritesCountFromOne")
        val GROUPS_FROM_ONE = booleanPreferencesKey("channels.groupsCountFromOne")
        val NUMBER_KEYS = stringPreferencesKey("channels.numberKeys")
        val LAST_CHANNEL = stringPreferencesKey("watching.lastChannel")
        val LAST_LIST = stringPreferencesKey("watching.lastList")
    }
}
