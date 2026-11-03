// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.component.CenteredKeyFooter
import ca.gureet.plasma.core.design.component.FooterKey
import ca.gureet.plasma.core.design.component.HintTone
import ca.gureet.plasma.core.design.component.SheetPanel
import ca.gureet.plasma.core.design.component.SheetRow
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.fixtures.Sample as Fixtures
import ca.gureet.plasma.core.model.CategoryItem
import ca.gureet.plasma.core.model.CategoryKind
import ca.gureet.plasma.core.model.CategoryOrder
import ca.gureet.plasma.core.model.CategoryOrdering
import ca.gureet.plasma.core.model.CategoryPlace
import ca.gureet.plasma.core.model.Formats
import ca.gureet.plasma.core.model.SourceCategories
import ca.gureet.plasma.core.model.SourceCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import ca.gureet.plasma.core.design.R as DesignR

/**
 * What a source's Categories pages ask of the app (the owner's ask, 1 November): its Live TV,
 * movie or series categories, and changes to them. The app keeps them with the source, and Live
 * TV, the Guide, Movies, Series, Search and Home follow; previews and tests keep the boards' world
 * in memory.
 */
interface CategoryEditor {
    /** [sourceId]'s categories of [kind], in the order they show in, hidden ones too. */
    fun categories(sourceId: String, kind: CategoryKind): Flow<SourceCategories>

    fun setHidden(kind: CategoryKind, categoryId: String, hidden: Boolean)

    /** Show All or Hide All. */
    fun setAllHidden(sourceId: String, kind: CategoryKind, hidden: Boolean)

    fun setOrder(sourceId: String, kind: CategoryKind, order: CategoryOrder)

    /** Move: the categories are in the user's order now, [ids] as they are. */
    fun move(sourceId: String, kind: CategoryKind, ids: List<String>)

    /** Rename; nothing typed is the provider's name again. */
    fun rename(kind: CategoryKind, categoryId: String, name: String)

    /** New Category: one of the user's own. */
    fun create(sourceId: String, kind: CategoryKind, name: String)

    /** Delete Category: one of the user's own goes; what's in it stays where it was. */
    fun delete(kind: CategoryKind, categoryId: String)

    /** What the category [categoryId] has: a provider's channels, movies or series, or what the user put in one of the user's own. */
    fun items(kind: CategoryKind, categoryId: String): Flow<List<CategoryItem>>

    /** [itemId] goes into the user's own [categoryId], or comes out of it. */
    fun setMember(categoryId: String, itemId: String, member: Boolean)

    /** The boards' world, kept in memory: every source has its groups and categories. */
    class Sample : CategoryEditor {
        private class Kept(val category: SourceCategory, val position: Int, var userPosition: Int? = null, val items: List<CategoryItem> = emptyList())

        private val orders = MutableStateFlow(emptyMap<String, CategoryOrder>())
        private val kept = MutableStateFlow(start())
        private val members = MutableStateFlow(mapOf(OWN to listOf("105", "107", "108", "106")))

        private fun start(): Map<CategoryKind, List<Kept>> {
            val channels = Fixtures.groups.filter { !it.smart }.mapIndexed { i, group ->
                Kept(
                    SourceCategory(group.id, group.name, null, hidden = group.id == "kids", custom = false, count = group.count),
                    i,
                    items = group.channels.map { CategoryItem(it.number.toString(), it.name, it.number.toString()) },
                )
            }
            val own = Kept(SourceCategory(OWN, "Game Day", null, hidden = false, custom = true, count = 4), 0)
            val movies = Fixtures.movieCategories.filter { !it.smart }.mapIndexed { i, category ->
                Kept(SourceCategory(category.id, category.name, null, false, false, category.count), i, items = Fixtures.movies.map { CategoryItem(it.id, it.title) })
            }
            val series = Fixtures.seriesCategories.filter { !it.smart }.mapIndexed { i, category ->
                Kept(SourceCategory(category.id, category.name, null, false, false, category.count), i, items = Fixtures.series.map { CategoryItem(it.id, it.title) })
            }
            return mapOf(CategoryKind.Live to listOf(own) + channels, CategoryKind.Movies to movies, CategoryKind.Series to series)
        }

        private fun change(kind: CategoryKind, id: String? = null, change: (Kept) -> Kept) {
            kept.value = kept.value + (kind to kept.value[kind].orEmpty().map { if (id == null || it.category.id == id) change(it) else it })
        }

        override fun categories(sourceId: String, kind: CategoryKind): Flow<SourceCategories> = combine(kept, orders, members) { all, chosen, inside ->
            val order = chosen[kind.id] ?: CategoryOrder.Provider
            val list = all[kind].orEmpty().map { k -> k.category.copy(count = inside[k.category.id]?.size ?: k.category.count).let { Kept(it, k.position, k.userPosition, k.items) } }
            SourceCategories(order, CategoryOrdering.sorted(list, order) { CategoryPlace(it.category.name, it.category.custom, it.position, it.userPosition) }.map { it.category })
        }

        override fun setHidden(kind: CategoryKind, categoryId: String, hidden: Boolean) =
            change(kind, categoryId) { Kept(it.category.copy(hidden = hidden), it.position, it.userPosition, it.items) }

        override fun setAllHidden(sourceId: String, kind: CategoryKind, hidden: Boolean) =
            change(kind) { Kept(it.category.copy(hidden = hidden), it.position, it.userPosition, it.items) }

        override fun setOrder(sourceId: String, kind: CategoryKind, order: CategoryOrder) {
            orders.value = orders.value + (kind.id to order)
        }

        override fun move(sourceId: String, kind: CategoryKind, ids: List<String>) {
            change(kind) { Kept(it.category, it.position, ids.indexOf(it.category.id).takeIf { i -> i >= 0 }, it.items) }
            setOrder(sourceId, kind, CategoryOrder.Mine)
        }

        override fun rename(kind: CategoryKind, categoryId: String, name: String) = change(kind, categoryId) { k ->
            val provider = k.category.providerName ?: k.category.name
            val wanted = name.trim()
            val category = when {
                k.category.custom -> if (wanted.isEmpty()) k.category else k.category.copy(name = wanted)
                wanted.isEmpty() || wanted == provider -> k.category.copy(name = provider, providerName = null)
                else -> k.category.copy(name = wanted, providerName = provider)
            }
            Kept(category, k.position, k.userPosition, k.items)
        }

        override fun create(sourceId: String, kind: CategoryKind, name: String) {
            val list = kept.value[kind].orEmpty()
            val id = "own-${list.size}"
            kept.value = kept.value + (kind to listOf(Kept(SourceCategory(id, name.trim(), null, false, true, 0), list.count { it.category.custom })) + list)
        }

        override fun delete(kind: CategoryKind, categoryId: String) {
            kept.value = kept.value + (kind to kept.value[kind].orEmpty().filter { it.category.id != categoryId || !it.category.custom })
        }

        override fun items(kind: CategoryKind, categoryId: String): Flow<List<CategoryItem>> = combine(kept, members) { all, inside ->
            val everything = all[kind].orEmpty().flatMap { it.items }.distinctBy { it.id }
            inside[categoryId]?.mapNotNull { id -> everything.firstOrNull { it.id == id } } ?: all[kind].orEmpty().firstOrNull { it.category.id == categoryId }?.items.orEmpty()
        }

        override fun setMember(categoryId: String, itemId: String, member: Boolean) {
            val now = members.value[categoryId].orEmpty()
            members.value = members.value + (categoryId to if (member) (now - itemId) + itemId else now - itemId)
        }

        private companion object {
            const val OWN = "own-game-day"
        }
    }
}

/** What a kind's Categories page says and shows. */
private class KindWords(
    @StringRes val title: Int,
    val icon: PlasmaIcons,
    @PluralsRes val count: Int,
    @StringRes val orderText: Int,
    @StringRes val allText: Int,
    @StringRes val newText: Int,
    @StringRes val providerText: Int,
    @StringRes val ownText: Int,
    /** What's in one of the user's own: Channels, Movies or Series. */
    @StringRes val items: Int,
    @StringRes val add: Int,
    @StringRes val deleteText: Int,
)

private fun CategoryKind.words(): KindWords = when (this) {
    CategoryKind.Live -> KindWords(
        R.string.live_categories, PlasmaIcons.LiveTv, R.plurals.channel_count, R.string.category_order_live_text, R.string.show_hide_all_live_text,
        R.string.new_category_live_text, R.string.category_live_text, R.string.own_category_live_text, R.string.category_channels, R.string.add_channels,
        R.string.delete_category_live_text,
    )
    CategoryKind.Movies -> KindWords(
        R.string.movie_categories, PlasmaIcons.Movies, R.plurals.category_movie_count, R.string.category_order_movies_text, R.string.show_hide_all_movies_text,
        R.string.new_category_movies_text, R.string.category_movies_text, R.string.own_category_movies_text, R.string.category_movies, R.string.add_movies,
        R.string.delete_category_movies_text,
    )
    CategoryKind.Series -> KindWords(
        R.string.series_categories, PlasmaIcons.Series, R.plurals.category_series_count, R.string.category_order_series_text, R.string.show_hide_all_series_text,
        R.string.new_category_series_text, R.string.category_series_text, R.string.own_category_series_text, R.string.category_series, R.string.add_series,
        R.string.delete_category_series_text,
    )
}

/** A category's row id, by the category's. */
internal fun categoryRowId(categoryId: String): String = "category-$categoryId"

/**
 * Portals & Playlists › a source › Categories › Live TV, Movies or Series (the owner's ask, 1
 * November): how they're ordered, Show or Hide All and New Category, then every category in its
 * order. OK shows one or hides it; holding OK or Menu offers Rename, Move and, for one of the
 * user's own, what's in it and Delete Category.
 */
@Composable
internal fun categoriesPage(kind: CategoryKind, source: SourceInfo, editor: CategoryEditor): PageSpec {
    val listed by remember(editor, source.id, kind) { editor.categories(source.id, kind) }.collectAsState(null)
    val list = listed ?: SourceCategories(CategoryOrder.Provider, emptyList())
    val categories = list.categories
    val words = kind.words()
    // Reorder (the owner's ask, 2 November): in My Order, OK picks a category up and puts it down,
    // one after another, until Done Reordering or Back.
    var reordering by remember(source.id, kind) { mutableStateOf(false) }
    val reorderOn = reordering && list.order == CategoryOrder.Mine
    BackHandler(enabled = reorderOn) { reordering = false }
    // Another order ends it.
    LaunchedEffect(list.order) { if (list.order != CategoryOrder.Mine) reordering = false }
    val orderNames = listOf(stringResource(R.string.order_provider), stringResource(R.string.order_a_to_z), stringResource(R.string.order_mine))

    val orderLabel = stringResource(R.string.category_order)
    val order = SettingItem(
        id = "order",
        label = orderLabel,
        value = orderNames[list.order.ordinal],
        icon = PlasmaIcons.Stacked,
        action = SettingAction.Menu(
            title = orderLabel,
            subtitle = stringResource(R.string.category_order_menu_text),
            options = CategoryOrder.entries.map { choice -> MenuOption(orderNames[choice.ordinal], checked = choice == list.order) { editor.setOrder(source.id, kind, choice) } },
        ),
        detail = SettingDetail(PlasmaIcons.Stacked, orderLabel, text = stringResource(words.orderText), extra = { ChoicesPreview(orderNames, list.order.ordinal) }),
    )

    val reorderLabel = stringResource(if (reorderOn) R.string.reorder_done else R.string.reorder)
    val reorder = SettingItem(
        id = "reorder",
        label = reorderLabel,
        value = null,
        icon = PlasmaIcons.Move,
        chevron = false,
        action = SettingAction.Run { reordering = !reorderOn },
        detail = SettingDetail(PlasmaIcons.Move, stringResource(R.string.reorder), text = stringResource(R.string.reorder_text)),
    )

    val allLabel = stringResource(R.string.show_hide_all)
    val all = SettingItem(
        id = "all",
        label = allLabel,
        value = stringResource(R.string.categories_shown, Formats.count(categories.count { !it.hidden }), Formats.count(categories.size)),
        icon = PlasmaIcons.Show,
        action = SettingAction.Menu(
            title = allLabel,
            options = listOf(
                MenuOption(stringResource(R.string.show_all)) { editor.setAllHidden(source.id, kind, false) },
                MenuOption(stringResource(R.string.hide_all)) { editor.setAllHidden(source.id, kind, true) },
            ),
        ),
        detail = SettingDetail(PlasmaIcons.Show, allLabel, text = stringResource(words.allText)),
    )

    val newLabel = stringResource(R.string.new_category)
    val create = SettingItem(
        id = "new-category",
        label = newLabel,
        value = null,
        icon = PlasmaIcons.Plus,
        action = SettingAction.Type(
            title = newLabel,
            fields = listOf(TypedField(stringResource(R.string.field_name), "")),
            text = stringResource(words.newText),
            save = stringResource(R.string.create_category),
            onSave = { texts -> texts.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { editor.create(source.id, kind, it) } },
        ),
        detail = SettingDetail(PlasmaIcons.Plus, newLabel, text = stringResource(words.newText)),
    )

    val providers = categories.filter { !it.custom }
    val on = stringResource(R.string.on)
    val off = stringResource(R.string.off)
    val providerNote = stringResource(R.string.category_note)
    val ownNote = stringResource(R.string.own_category_note)
    val rows = categories.map { category ->
        val count = pluralStringResource(words.count, category.count, Formats.count(category.count))
        val subtitle = when {
            category.custom -> stringResource(R.string.category_yours, count)
            category.providerName != null -> stringResource(R.string.category_renamed, category.providerName.orEmpty(), count)
            else -> count
        }
        val text = if (category.custom) {
            stringResource(words.ownText)
        } else {
            stringResource(words.providerText, category.providerName ?: category.name)
        }
        SettingItem(
            id = categoryRowId(category.id),
            label = category.name,
            value = if (category.hidden) off else on,
            subtitle = subtitle,
            // Reordering, each one can be picked up.
            leadingIcon = PlasmaIcons.Move.takeIf { reorderOn },
            dimmed = category.hidden,
            chevron = false,
            action = SettingAction.Run { editor.setHidden(kind, category.id, !category.hidden) },
            options = categoryOptions(kind, category, words, providers, editor),
            detail = SettingDetail(if (category.custom) PlasmaIcons.Folder else words.icon, category.name, subline = subtitle, text = text, extra = {
                DetailNote(if (category.custom) ownNote else providerNote)
            }),
        )
    }

    return PageSpec(
        title = stringResource(words.title),
        back = source.name,
        style = PageStyle.Compact,
        metrics = RowMetrics.Page,
        detailTop = 160.px,
        sections = listOf(
            // Reorder is there in My Order.
            SettingSection(null, listOfNotNull(order, reorder.takeIf { list.order == CategoryOrder.Mine }, all, create)),
            SettingSection(
                label = stringResource(if (reorderOn) R.string.section_reordering else R.string.section_categories),
                items = rows,
                onMove = { ids -> editor.move(source.id, kind, ids.map { it.removePrefix(categoryRowId("")) }) },
                reordering = reorderOn,
            ),
        ),
    )
}

/** What holding OK or Menu on a category offers: Rename and Move, and for one of the user's own, what's in it and Delete Category. */
@Composable
private fun categoryOptions(
    kind: CategoryKind,
    category: SourceCategory,
    words: KindWords,
    providers: List<SourceCategory>,
    editor: CategoryEditor,
): SettingAction.Menu {
    val provider = category.providerName ?: category.name.takeIf { !category.custom }
    val rename = SettingAction.Type(
        title = stringResource(R.string.rename_category),
        fields = listOf(TypedField(stringResource(R.string.field_name), category.name, hint = provider)),
        text = provider?.let { stringResource(R.string.rename_category_text, it) },
        onSave = { texts -> editor.rename(kind, category.id, texts.firstOrNull().orEmpty()) },
    )
    val delete = SettingAction.Menu(
        title = stringResource(R.string.delete_category_title, category.name),
        subtitle = stringResource(words.deleteText),
        options = listOf(
            MenuOption(stringResource(R.string.cancel)),
            MenuOption(stringResource(R.string.delete)) { editor.delete(kind, category.id) },
        ),
    )
    return SettingAction.Menu(
        title = category.name,
        options = listOfNotNull(
            if (category.custom) {
                MenuOption(stringResource(words.items), sheet = SettingAction.Sheet { close -> OwnCategorySheet(kind, category, providers, editor, close) })
            } else {
                null
            },
            MenuOption(stringResource(R.string.rename), type = rename),
            MenuOption(stringResource(R.string.move), move = true),
            if (category.custom) MenuOption(stringResource(R.string.delete_category), next = delete) else null,
        ),
    )
}

/** A row of [OwnCategorySheet]: a channel, movie or series ticked when it's in, or a category to pick from. */
@Immutable
private data class PickRow(val id: String, val name: String, val lead: String? = null, val value: String? = null, val tickable: Boolean = true)

/**
 * A category of the user's own's Channels, Movies or Series (the owner's ask, 1 November): what's
 * in it, ticked, and OK takes one out or puts it back; first, Add Channels lists the source's own
 * categories, and OK on one lists what it has, ticked when it's in, to put in or take out. Back
 * goes back a step, and closes it from the first.
 */
@Composable
internal fun OwnCategorySheet(kind: CategoryKind, category: SourceCategory, providers: List<SourceCategory>, editor: CategoryEditor, onClose: () -> Unit) {
    val words = kind.words()
    // The step showing: what's in it; the source's categories, to add from; or one of them.
    var choosing by remember { mutableStateOf(false) }
    var from by remember { mutableStateOf<SourceCategory?>(null) }
    var lastFrom by remember { mutableStateOf<String?>(null) }
    val inside by remember(editor, category.id) { editor.items(kind, category.id) }.collectAsState(null)
    // What was in it when the sheet opened and what went in since, so one taken out stays to put back.
    var seen by remember { mutableStateOf<List<CategoryItem>>(emptyList()) }
    var flipped by remember { mutableStateOf(emptyMap<String, Boolean>()) }
    LaunchedEffect(inside) {
        val now = inside ?: return@LaunchedEffect
        seen = seen + now.filter { item -> seen.none { it.id == item.id } }
    }
    val memberIds = inside.orEmpty().mapTo(HashSet()) { it.id }
    fun isIn(id: String): Boolean = flipped[id] ?: (id in memberIds)
    fun flip(id: String) {
        val now = !isIn(id)
        flipped = flipped + (id to now)
        editor.setMember(category.id, id, now)
    }

    val picking = from
    val fromItems by remember(editor, picking?.id) { picking?.let { editor.items(kind, it.id) } ?: flowOf(null) }.collectAsState(null)
    val addLabel = stringResource(words.add)
    val countWord = words.count
    val providerCounts = providers.map { pluralStringResource(countWord, it.count, Formats.count(it.count)) }
    val step = when {
        picking != null -> Step(
            key = "from-${picking.id}",
            title = picking.name,
            text = stringResource(R.string.own_category_pick_text, category.name),
            rows = fromItems.orEmpty().map { PickRow(it.id, it.name, it.lead) },
        )
        choosing -> Step(
            key = "choosing",
            title = addLabel,
            text = stringResource(R.string.own_category_from_text, category.name),
            rows = providers.mapIndexed { i, provider -> PickRow(provider.id, provider.name, value = providerCounts[i], tickable = false) },
            focusId = lastFrom,
        )
        else -> Step(
            key = "inside",
            title = category.name,
            text = stringResource(R.string.own_category_sheet_text),
            rows = listOf(PickRow(ADD_ROW, addLabel, tickable = false)) + seen.map { PickRow(it.id, it.name, it.lead) },
        )
    }

    fun back() {
        when {
            from != null -> from = null
            choosing -> choosing = false
            else -> onClose()
        }
    }

    val first = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val focusIndex = step.focusId?.let { id -> step.rows.indexOfFirst { it.id == id } }?.takeIf { it >= 0 } ?: 0
    LaunchedEffect(step.key, step.rows.isNotEmpty()) {
        if (step.rows.isEmpty()) return@LaunchedEffect
        listState.scrollToItem(focusIndex)
        withFrameNanos { }
        first.requestFocus()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(PlasmaColors.black(0.5f))
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Back || event.key == Key.Escape) {
                    if (event.type == KeyEventType.KeyUp) back()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        SheetPanel(Modifier.padding(horizontal = 96.px).width(720.px).heightIn(max = 820.px)) {
            Column(Modifier.padding(horizontal = 10.px), verticalArrangement = Arrangement.spacedBy(4.px)) {
                PlasmaText(step.title, plasmaStyle(30f, 600, trackingPx = -0.4f), maxLines = 1)
                PlasmaText(step.text, plasmaStyle(21f, 500, lineHeight = 1.35f), color = PlasmaColors.Text2, maxLines = 3)
            }
            Box(Modifier.padding(top = 22.px, bottom = 14.px).fillMaxWidth().height(1.px).background(PlasmaColors.white(0.1f)))
            LazyColumn(
                Modifier
                    .weight(1f, fill = false)
                    // Focus stays in the sheet until it closes.
                    .focusProperties { onExit = { cancelFocusChange() } }
                    .focusGroup(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.px),
            ) {
                itemsIndexed(step.rows, key = { _, row -> step.key + "/" + row.id }) { index, row ->
                    SheetRow(
                        label = row.name,
                        value = row.value,
                        onClick = {
                            when {
                                row.id == ADD_ROW && picking == null && !choosing -> choosing = true
                                choosing && picking == null -> {
                                    lastFrom = row.id
                                    from = providers.firstOrNull { it.id == row.id }
                                }
                                else -> flip(row.id)
                            }
                        },
                        modifier = if (index == focusIndex) Modifier.focusRequester(first) else Modifier,
                        icon = row.lead?.let { lead ->
                            @Composable {
                                val leadColor = if (LocalFocused.current) PlasmaColors.OnFocus3 else PlasmaColors.Text2
                                PlasmaText(lead, plasmaStyle(21f, 600, tabular = true), Modifier.width(64.px), color = leadColor)
                            }
                        },
                        checked = row.tickable && isIn(row.id),
                    )
                }
            }
        }
        CenteredKeyFooter(
            keys = listOf(
                FooterKey("OK", stringResource(DesignR.string.key_select)),
                FooterKey(PlasmaIcons.Back, if (picking != null || choosing) stringResource(R.string.key_back) else stringResource(DesignR.string.key_close)),
            ),
            modifier = Modifier.align(Alignment.TopStart),
            tone = HintTone.Screen,
        )
    }
}

/** One step of [OwnCategorySheet]: its rows, and the row focus starts on, by id, when it isn't the first. */
private class Step(val key: String, val title: String, val text: String, val rows: List<PickRow>, val focusId: String? = null)

/** Add Channels', Add Movies' or Add Series' row, first in a category of the user's own. */
private const val ADD_ROW = "\u0000add"
