// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.Context
import ca.gureet.plasma.core.data.ChannelLineup
import ca.gureet.plasma.core.data.CategoryRepository
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.model.CategoryKind
import ca.gureet.plasma.core.model.SettingKeys
import ca.gureet.plasma.core.model.SettingsChoices
import ca.gureet.plasma.editor.CategoryChange
import ca.gureet.plasma.editor.EditorBackend
import ca.gureet.plasma.editor.EditorBrowser
import ca.gureet.plasma.editor.EditorCategories
import ca.gureet.plasma.editor.EditorCategory
import ca.gureet.plasma.editor.EditorChannel
import ca.gureet.plasma.editor.EditorGroup
import ca.gureet.plasma.editor.EditorLibrary
import ca.gureet.plasma.editor.EditorLogo
import ca.gureet.plasma.editor.EditorPages
import ca.gureet.plasma.editor.EditorProfile
import ca.gureet.plasma.editor.EditorServer
import ca.gureet.plasma.editor.EditorSource
import ca.gureet.plasma.editor.EditorTitle
import ca.gureet.plasma.editor.FavoritesChange
import ca.gureet.plasma.editor.PinCheck
import ca.gureet.plasma.editor.wire
import ca.gureet.plasma.feature.profiles.PinTries
import ca.gureet.plasma.feature.settings.BrowserEditor
import ca.gureet.plasma.feature.settings.PhonePairing
import ca.gureet.plasma.pairing.LanAddress
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Edit in a Browser, on Plasma's own data: the favorites of the profile watching, the channels
 * hidden and locked, the sources' categories, numbering and locks, organized from a computer's or
 * a phone's browser. Each change is kept as the TV's own screens keep it, and the TV's screens
 * follow it at once.
 *
 * The page answers while Settings › Portals & Playlists › Edit in a Browser shows its code, and
 * while a browser that came in with it is editing; then it stops (brief 5.15's rules for the
 * TV's pages: the home network only, a code for each browser, nothing secret sent).
 */
class BrowserEditorHost(private val graph: AppGraph, private val context: Context, private val http: () -> OkHttpClient) : EditorBackend, BrowserEditor {
    private val scope: CoroutineScope = graph.scope
    private val data get() = graph.data

    private sealed interface State {
        data object Off : State

        data object Starting : State

        /** The TV isn't on a network a browser can reach, or no port would open. */
        data object Unavailable : State

        class Running(val server: EditorServer) : State
    }

    private val state = MutableStateFlow<State>(State.Off)
    private val sheets = MutableStateFlow(0)

    /** The server's running or starting; read and changed on the main thread only. */
    private var running = false

    private val _browsers = MutableStateFlow<List<EditorBrowser>>(emptyList())

    /** The browsers editing now. */
    val editing: StateFlow<List<EditorBrowser>> = _browsers.asStateFlow()

    override val browsers: Flow<List<String>> = _browsers.map { list -> list.map { it.name } }.distinctUntilChanged()

    private val changes = AtomicLong(1)

    override val revision: Long get() = changes.get()

    /** Each channel's logo address, by the id it shows under, for the logos the page asks for. */
    @Volatile private var logoUrls: Map<String, String> = emptyMap()

    /** Logos fetched, by channel: a few megabytes at most. */
    private val logos = object : LinkedHashMap<String, EditorLogo>(LOGOS, LOAD_FACTOR, true) {
        var bytes = 0L

        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EditorLogo>): Boolean {
            if (bytes <= MAX_LOGO_BYTES) return false
            bytes -= eldest.value.bytes.size
            return true
        }
    }

    // ---- The TV's sheet ---------------------------------------------------------------------------

    override fun sheet(): Flow<PhonePairing> = channelFlow {
        sheets.update { it + 1 }
        start()
        try {
            state.collectLatest { now ->
                when (now) {
                    State.Off, State.Starting -> send(PhonePairing.Starting)
                    State.Unavailable -> send(PhonePairing.Unavailable)
                    is State.Running -> {
                        withContext(Dispatchers.IO) { now.server.openJoining() }
                        now.server.invite.collect { invite -> send(invite?.let { PhonePairing.Ready(it.address, it.code, it.qr) } ?: PhonePairing.Starting) }
                    }
                }
            }
        } finally {
            sheets.update { it - 1 }
            if (sheets.value == 0) (state.value as? State.Running)?.server?.closeJoining()
        }
    }

    override fun endAll() {
        val server = (state.value as? State.Running)?.server ?: return
        scope.launch(Dispatchers.IO) { server.endAll() }
    }

    /** Starts the page's server, unless it's running; it stops once no sheet shows and no browser edits. */
    private fun start() {
        if (running) return
        running = true
        scope.launch {
            try {
                serve()
            } finally {
                running = false
                state.value = State.Off
                _browsers.value = emptyList()
                // A sheet that opened while this stopped gets a server of its own.
                if (sheets.value > 0) start()
            }
        }
    }

    private suspend fun serve() = coroutineScope {
        state.value = State.Starting
        val pages = EditorPages(texts(context), context.resources.configuration.locales[0].toLanguageTag())
        val server = EditorServer(this@BrowserEditorHost, pages, onJoined = ::joined)
        try {
            val started = withContext(Dispatchers.IO) {
                val address = LanAddress.find() ?: return@withContext false
                try {
                    server.start(address, this@coroutineScope)
                    true
                } catch (failed: IOException) {
                    false
                }
            }
            if (!started) {
                state.value = State.Unavailable
                sheets.first { it == 0 }
                return@coroutineScope
            }
            state.value = State.Running(server)
            val watching = launch { watchChanges() }
            val listing = launch { server.browsers.collect { _browsers.value = it } }
            // While a sheet shows, or a browser edits.
            combine(sheets, server.browsers) { open, browsers -> open > 0 || browsers.isNotEmpty() }.first { !it }
            watching.cancel()
            listing.cancel()
        } finally {
            // Stops the server's clock too, so this scope can end.
            withContext(NonCancellable + Dispatchers.IO) { server.close() }
        }
    }

    /** "Chrome · Mac is editing in a browser". */
    private fun joined(browser: EditorBrowser) {
        scope.launch { graph.notice(Notice(context.getString(R.string.editor_joined, browser.name), PlasmaIcons.Screen)) }
    }

    /**
     * What the page shows changed, on the TV or from a browser: the revision grows, and the pages
     * ask again. Each flow is the one [library] reads, so a page asking after a change gets it.
     */
    private suspend fun watchChanges() = coroutineScope {
        fun changed() = changes.incrementAndGet()
        launch {
            graph.lineup.collect { lineup ->
                logoUrls = lineup.shown.mapNotNull { channel -> channel.logo?.takeIf(::fetchable)?.let { channel.id to it } }.toMap()
                changed()
            }
        }
        launch { graph.groupLocks.collect { changed() } }
        launch { graph.parental.pinSet.collect { changed() } }
        launch { graph.sourcesNow.collect { changed() } }
        launch { graph.libraryNow.collect { changed() } }
        launch { graph.storedChoices.map { it?.numbering() }.distinctUntilChanged().collect { changed() } }
        launch { data.channels.everyFavorite.collect { changed() } }
        launch { data.categories.allMembers.collect { changed() } }
    }

    private fun Map<String, Int>.numbering() = this[SettingKeys.FAVORITE_NUMBERS] to this[SettingKeys.GROUP_NUMBERS]

    // ---- What the page reads ----------------------------------------------------------------------

    override suspend fun profile(): EditorProfile {
        val profile = graph.currentProfile.value ?: data.profiles.current.first()
        return EditorProfile(profile.id, profile.name)
    }

    override suspend fun library(profileId: String): EditorLibrary = withContext(Dispatchers.Default) {
        val rev = changes.get()
        val lineup = graph.lineup.first()
        val locks = graph.groupLocks.first()
        val pinSet = graph.parental.pinSet.value ?: data.settings.pinSet.first()
        val choices = SettingsChoices(graph.storedChoices.value.orEmpty())
        val sources = graph.sourcesNow.value ?: data.sources.all()
        val counts = data.channels.countsBySource.first()
        val shelves = graph.libraryNow.value.orEmpty()
        val shownId = shownIds(lineup)
        val on = sources.filter { it.enabled }.mapTo(HashSet()) { it.id }
        val channels = locks.hideWithGroups(lineup.shown).map { channel ->
            EditorChannel(
                id = channel.id,
                number = channel.number,
                name = channel.name,
                group = channel.groupId,
                source = channel.sourceId,
                hidden = channel.hidden && !channel.hiddenWithGroup,
                groupHidden = channel.hiddenWithGroup,
                locked = channel.locked,
                groupLocked = channel.groupId in locks.lockedIds,
                logo = channel.logo?.let(::fetchable) == true,
            )
        }
        EditorLibrary(
            rev = rev,
            tv = PhoneRemoteHost.tvName(context),
            profile = graph.profilesNow.value?.firstOrNull { it.id == profileId }?.name.orEmpty(),
            pin = pinSet,
            favoritesFromOne = choices.favoritesCountFromOne,
            groupsFromOne = choices.groupsCountFromOne,
            sources = sources.map { source ->
                val shelf = shelves[source.id]
                EditorSource(source.id, source.name, source.type.id, source.enabled, counts[source.id] ?: 0, shelf?.movies ?: 0, shelf?.series ?: 0)
            },
            groups = locks.groups.filter { it.sourceId in on }.map { group ->
                EditorGroup(
                    id = group.id,
                    source = group.sourceId,
                    name = group.name,
                    provider = group.providerName.takeIf { !group.custom && it != group.name },
                    custom = group.custom,
                    hidden = group.hidden,
                    locked = locks.isLocked(group),
                    adult = locks.isAdult(group),
                    members = if (group.custom) group.members.mapNotNull { shownId[it] }.distinct() else emptyList(),
                )
            },
            channels = channels,
            favorites = data.channels.favoriteIdsOf(profileId).first().mapNotNull { shownId[it] }.distinct(),
        )
    }

    override suspend fun categories(sourceId: String, kind: CategoryKind): EditorCategories? = withContext(Dispatchers.Default) {
        val rev = changes.get()
        if ((graph.sourcesNow.value ?: data.sources.all()).none { it.id == sourceId }) return@withContext null
        val list = data.categories.categories(sourceId, kind).first()
        val locks = if (kind == CategoryKind.Live) graph.groupLocks.first() else null
        val groups = locks?.groups?.associateBy { it.id }.orEmpty()
        EditorCategories(
            rev = rev,
            order = list.order.wire(),
            categories = list.categories.map { category ->
                val group = groups[category.id]
                EditorCategory(
                    id = category.id,
                    name = category.name,
                    provider = category.providerName,
                    hidden = category.hidden,
                    custom = category.custom,
                    count = category.count,
                    locked = group != null && locks?.isLocked(group) == true,
                    adult = group != null && locks?.isAdult(group) == true,
                )
            },
        )
    }

    override suspend fun titles(kind: CategoryKind, categoryId: String): List<EditorTitle> = when (kind) {
        CategoryKind.Movies -> data.vod.moviesIn(categoryId).first().map { EditorTitle(it.id, it.name, it.year) }
        CategoryKind.Series -> data.vod.seriesIn(categoryId).first().map { EditorTitle(it.id, it.name, it.year) }
        CategoryKind.Live -> emptyList()
    }

    override suspend fun searchTitles(kind: CategoryKind, sourceId: String, query: String): List<EditorTitle> {
        if (query.isBlank()) return emptyList()
        val own = "$sourceId:"
        return when (kind) {
            CategoryKind.Movies -> data.vod.searchMovies(query, SEARCH_LIMIT).filter { it.id.startsWith(own) }.map { EditorTitle(it.id, it.name, it.year) }
            CategoryKind.Series -> data.vod.searchSeries(query, SEARCH_LIMIT).filter { it.id.startsWith(own) }.map { EditorTitle(it.id, it.name, it.year) }
            CategoryKind.Live -> emptyList()
        }
    }

    // ---- What the page changes --------------------------------------------------------------------

    override suspend fun favorites(profileId: String, change: FavoritesChange) {
        val same = sameChannels()
        fun idsOf(id: String): Set<String> = same[id].orEmpty().toSet() + id
        when (change) {
            is FavoritesChange.Add -> data.channels.addFavorites(profileId, change.ids.map { it to idsOf(it) })
            is FavoritesChange.Remove -> data.channels.removeFavorites(profileId, change.ids.flatMapTo(LinkedHashSet(), ::idsOf))
            is FavoritesChange.Order -> data.channels.arrangeFavorites(profileId, change.ids.map(::idsOf))
        }
    }

    override suspend fun hide(channelIds: List<String>, hidden: Boolean) = data.channels.setHiddenAll(channelIds, hidden)

    override suspend fun lock(channelIds: List<String>, locked: Boolean) = data.channels.setLockedAll(channelIds, locked)

    override suspend fun lockGroup(groupId: String, locked: Boolean) = data.channels.setGroupLocked(groupId, locked)

    override suspend fun category(change: CategoryChange): String? {
        val categories = data.categories
        val kind = change.kind
        var made: String? = null
        when (change) {
            is CategoryChange.Hide -> categories.setHidden(kind, change.id, change.hidden)
            is CategoryChange.HideAll -> categories.setAllHidden(change.sourceId, kind, change.hidden)
            is CategoryChange.Order -> categories.setOrder(change.sourceId, kind, change.order)
            // Only the source's own categories: each one's id starts with the source's.
            is CategoryChange.Move -> categories.move(change.sourceId, kind, change.ids.filter { it.startsWith("${change.sourceId}:") })
            is CategoryChange.Rename -> categories.rename(kind, change.id, change.name)
            is CategoryChange.Create -> made = categories.create(change.sourceId, kind, change.name)
            is CategoryChange.Delete -> categories.delete(kind, change.id)
            is CategoryChange.Members -> {
                // Only the user's own have what the user puts in them.
                if (!change.id.contains(":${CategoryRepository.CUSTOM}")) return null
                if (change.add.isNotEmpty()) categories.addAll(change.id, change.add.distinct())
                if (change.remove.isNotEmpty()) {
                    // A channel from several sources may be in it under any of their ids (5.1).
                    val same = if (kind == CategoryKind.Live) sameChannels() else emptyMap()
                    categories.remove(change.id, change.remove.flatMapTo(LinkedHashSet()) { same[it].orEmpty() + it })
                }
            }
        }
        // Movies' and Series' categories aren't watched: the pages hear of these from here.
        changes.incrementAndGet()
        return made
    }

    override suspend fun numbering(favoritesFromOne: Boolean?, groupsFromOne: Boolean?) {
        if (favoritesFromOne != null) graph.choose(SettingKeys.FAVORITE_NUMBERS, if (favoritesFromOne) FROM_ONE else KEEP)
        if (groupsFromOne != null) graph.choose(SettingKeys.GROUP_NUMBERS, if (groupsFromOne) FROM_ONE else KEEP)
    }

    /**
     * The parental PIN typed in a browser (5.12), counted with the TV's own wrong tries: five, and
     * entry stops for a minute, on the TV as here. The count is the TV screens' state, so it's
     * read and kept on the main thread.
     */
    override suspend fun checkPin(pin: String): PinCheck {
        val parental = graph.parental
        if (parental.pinSet.value != true) return PinCheck.Off
        val tries = parental.tries
        val blocked = withContext(Dispatchers.Main) {
            tries.reopenIfDue()
            tries.blockedUntil.takeIf { tries.blocked }
        }
        if (blocked != null) return PinCheck.Blocked(secondsUntil(blocked))
        val right = pin.length == PIN_LENGTH && parental.check(pin)
        return withContext(Dispatchers.Main) {
            if (right) {
                tries.succeeded()
                PinCheck.Right
            } else {
                tries.failed()
                val until = tries.blockedUntil.takeIf { tries.blocked }
                if (until != null) PinCheck.Blocked(secondsUntil(until)) else PinCheck.Wrong(PinTries.TRIES - tries.wrong)
            }
        }
    }

    private fun secondsUntil(at: Long): Int = ((at - System.currentTimeMillis() + MILLIS - 1) / MILLIS).toInt().coerceAtLeast(1)

    /** A channel's logo, fetched by the TV and kept a while: the browser never asks the provider. */
    override fun logo(channelId: String): EditorLogo? {
        synchronized(logos) { logos[channelId] }?.let { return it }
        val url = logoUrls[channelId] ?: return null
        val fetched = try {
            http().newCall(Request.Builder().url(url).build()).execute().use { response ->
                val body = response.body
                val type = body.contentType()?.toString()?.takeIf { it.startsWith("image/") }
                if (!response.isSuccessful || type == null || body.contentLength() > MAX_LOGO) {
                    null
                } else {
                    body.bytes().takeIf { it.size <= MAX_LOGO }?.let { EditorLogo(it, type) }
                }
            }
        } catch (failed: IOException) {
            null
        } catch (bad: IllegalArgumentException) {
            null
        } ?: return null
        synchronized(logos) {
            logos.put(channelId, fetched)?.let { logos.bytes -= it.bytes.size }
            logos.bytes += fetched.bytes.size
        }
        return fetched
    }

    /** Each channel the screens show, by the ids of the sources it comes from (5.1): its own, and a merged channel's on each. */
    private fun shownIds(lineup: ChannelLineup): Map<String, String> {
        val shown = HashMap<String, String>(lineup.shown.size * 2)
        for (channel in lineup.shown) {
            for (candidate in channel.candidates) shown.putIfAbsent(candidate, channel.id)
            shown[channel.id] = channel.id
        }
        return shown
    }

    /** The ids a channel the screens show may be kept under: a merged channel's on each of its sources. */
    private suspend fun sameChannels(): Map<String, List<String>> = graph.lineup.first().shown.associate { it.id to it.candidates }

    private fun fetchable(url: String) = url.startsWith("http://") || url.startsWith("https://")

    companion object {
        /** The page's words, in the TV's language, by the names it uses ([EditorPages.KEYS]); "tv" is the TV's name. */
        fun texts(context: Context): Map<String, String> {
            val res = context.resources
            return WORDS.mapValues { (_, id) -> context.getString(id) } +
                PLURALS.mapValues { (_, id) -> "${res.getQuantityString(id, 1)}|${res.getQuantityString(id, 2)}" } +
                ("tv" to PhoneRemoteHost.tvName(context))
        }

        internal val WORDS: Map<String, Int> = mapOf(
            "title" to R.string.editor_page_title,
            "heading" to R.string.editor_page_heading,
            "browser" to R.string.editor_page_browser,
            "end" to R.string.editor_page_end,
            "joinText" to R.string.editor_page_join_text,
            "codeLabel" to R.string.editor_page_code_label,
            "join" to R.string.editor_page_join,
            "wrongCode" to R.string.editor_page_wrong_code,
            "tooManyTries" to R.string.editor_page_too_many_tries,
            "notOpen" to R.string.editor_page_not_open,
            "full" to R.string.editor_page_full,
            "endedTitle" to R.string.editor_page_ended_title,
            "endedIdle" to R.string.editor_page_ended_idle,
            "endedTv" to R.string.editor_page_ended_tv,
            "endedYou" to R.string.editor_page_ended_you,
            "endedText" to R.string.editor_page_ended_text,
            "unreachable" to R.string.editor_page_unreachable,
            "saving" to R.string.editor_page_saving,
            "saveFailed" to R.string.editor_page_save_failed,
            "favorites" to R.string.editor_page_favorites,
            "channels" to R.string.editor_page_channels,
            "categories" to R.string.editor_page_categories,
            "liveTv" to R.string.editor_page_live_tv,
            "movies" to R.string.editor_page_movies,
            "series" to R.string.editor_page_series,
            "numbering" to R.string.editor_page_numbering,
            "locks" to R.string.editor_page_locks,
            "sourceOff" to R.string.editor_page_source_off,
            "editingAs" to R.string.editor_page_editing_as,
            "favoritesText" to R.string.editor_page_favorites_text,
            "addChannels" to R.string.editor_page_add_channels,
            "searchChannels" to R.string.editor_page_search_channels,
            "allGroups" to R.string.editor_page_all_groups,
            "noFavorites" to R.string.editor_page_no_favorites,
            "noMatches" to R.string.editor_page_no_matches,
            "moveUp" to R.string.editor_page_move_up,
            "moveDown" to R.string.editor_page_move_down,
            "moveTop" to R.string.editor_page_move_top,
            "moveBottom" to R.string.editor_page_move_bottom,
            "removeFavorite" to R.string.editor_page_remove_favorite,
            "addFavorite" to R.string.editor_page_add_favorite,
            "addAllShown" to R.string.editor_page_add_all_shown,
            "dragToMove" to R.string.editor_page_drag_to_move,
            "countsFromOne" to R.string.editor_page_counts_from_one,
            "keepsNumbers" to R.string.editor_page_keeps_numbers,
            "change" to R.string.editor_page_change,
            "close" to R.string.editor_page_close,
            "channelsText" to R.string.editor_page_channels_text,
            "filterAll" to R.string.editor_page_filter_all,
            "filterFavorites" to R.string.editor_page_filter_favorites,
            "filterHidden" to R.string.editor_page_filter_hidden,
            "filterLocked" to R.string.editor_page_filter_locked,
            "hide" to R.string.editor_page_hide,
            "show" to R.string.editor_page_show,
            "lock" to R.string.editor_page_lock,
            "unlock" to R.string.editor_page_unlock,
            "hiddenWithGroup" to R.string.editor_page_hidden_with_group,
            "lockedWithGroup" to R.string.editor_page_locked_with_group,
            "selected" to R.string.editor_page_selected,
            "selectAll" to R.string.editor_page_select_all,
            "clearSelection" to R.string.editor_page_clear_selection,
            "addToCategory" to R.string.editor_page_add_to_category,
            "noOwnCategories" to R.string.editor_page_no_own_categories,
            "categoriesText" to R.string.editor_page_categories_text,
            "order" to R.string.editor_page_order,
            "providerOrder" to R.string.editor_page_provider_order,
            "aToZ" to R.string.editor_page_a_to_z,
            "myOrder" to R.string.editor_page_my_order,
            "showAll" to R.string.editor_page_show_all,
            "hideAll" to R.string.editor_page_hide_all,
            "newCategory" to R.string.editor_page_new_category,
            "categoryName" to R.string.editor_page_category_name,
            "create" to R.string.editor_page_create,
            "cancel" to R.string.editor_page_cancel,
            "save" to R.string.editor_page_save,
            "rename" to R.string.editor_page_rename,
            "resetName" to R.string.editor_page_reset_name,
            "providerName" to R.string.editor_page_provider_name,
            "yours" to R.string.editor_page_yours,
            "adult" to R.string.editor_page_adult,
            "shown" to R.string.editor_page_shown,
            "hidden" to R.string.editor_page_hidden,
            "hiddenCount" to R.string.editor_page_hidden_count,
            "deleteCategory" to R.string.editor_page_delete_category,
            "deleteText" to R.string.editor_page_delete_text,
            "delete" to R.string.editor_page_delete,
            "editChannels" to R.string.editor_page_edit_channels,
            "editMovies" to R.string.editor_page_edit_movies,
            "editSeries" to R.string.editor_page_edit_series,
            "inThisCategory" to R.string.editor_page_in_this_category,
            "searchMovies" to R.string.editor_page_search_movies,
            "searchSeries" to R.string.editor_page_search_series,
            "nothingInCategory" to R.string.editor_page_nothing_in_category,
            "add" to R.string.editor_page_add,
            "remove" to R.string.editor_page_remove,
            "movedToMyOrder" to R.string.editor_page_moved_to_my_order,
            "numberingText" to R.string.editor_page_numbering_text,
            "numbersInFavorites" to R.string.editor_page_numbers_in_favorites,
            "numbersInFavoritesText" to R.string.editor_page_numbers_in_favorites_text,
            "numbersInGroups" to R.string.editor_page_numbers_in_groups,
            "numbersInGroupsText" to R.string.editor_page_numbers_in_groups_text,
            "countFromOne" to R.string.editor_page_count_from_one,
            "keepNumbers" to R.string.editor_page_keep_numbers,
            "locksText" to R.string.editor_page_locks_text,
            "locksOff" to R.string.editor_page_locks_off,
            "pinTitle" to R.string.editor_page_pin_title,
            "pinText" to R.string.editor_page_pin_text,
            "pinLabel" to R.string.editor_page_pin_label,
            "unlockLocks" to R.string.editor_page_unlock_locks,
            "pinBlocked" to R.string.editor_page_pin_blocked,
            "pinTooMany" to R.string.editor_page_pin_too_many,
            "locksUnlocked" to R.string.editor_page_locks_unlocked,
            "lockedGroups" to R.string.editor_page_locked_groups,
            "lockedGroupsText" to R.string.editor_page_locked_groups_text,
            "lockedChannels" to R.string.editor_page_locked_channels,
            "lockedChannelsText" to R.string.editor_page_locked_channels_text,
            "lockChannels" to R.string.editor_page_lock_channels,
            "noLockedChannels" to R.string.editor_page_no_locked_channels,
        )

        /** Counts: the page picks the one form or the other ("%s channel|%s channels"). */
        internal val PLURALS: Map<String, Int> = mapOf(
            "wrongPin" to R.plurals.editor_page_wrong_pin,
            "channelCount" to R.plurals.editor_page_channel_count,
            "categoryCount" to R.plurals.editor_page_category_count,
            "titleCount" to R.plurals.editor_page_title_count,
        )

        private const val FROM_ONE = 0
        private const val KEEP = 1
        private const val PIN_LENGTH = 4
        private const val MILLIS = 1_000L
        private const val SEARCH_LIMIT = 400
        private const val LOGOS = 300
        private const val LOAD_FACTOR = 0.75f
        private const val MAX_LOGO = 256 * 1024L
        private const val MAX_LOGO_BYTES = 8 * 1024 * 1024L
    }
}
