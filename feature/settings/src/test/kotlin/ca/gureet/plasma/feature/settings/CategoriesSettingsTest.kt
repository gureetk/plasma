// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import ca.gureet.plasma.core.model.CategoryKind
import ca.gureet.plasma.core.model.CategoryOrder
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/**
 * Settings › Portals & Playlists › a source › Categories (the owner's ask, 1 November): showing
 * and hiding, the order, Move, Rename, New Category, and what's in a category of the user's own.
 */
class CategoriesSettingsTest : ScreenshotTest() {
    /** The boards' categories, noting what the page asks for. */
    private class Recording(private val inner: CategoryEditor = CategoryEditor.Sample()) : CategoryEditor by inner {
        val calls = mutableListOf<String>()

        override fun setHidden(kind: CategoryKind, categoryId: String, hidden: Boolean) {
            calls += "hide $categoryId $hidden"
            inner.setHidden(kind, categoryId, hidden)
        }

        override fun setAllHidden(sourceId: String, kind: CategoryKind, hidden: Boolean) {
            calls += "all $hidden"
            inner.setAllHidden(sourceId, kind, hidden)
        }

        override fun setOrder(sourceId: String, kind: CategoryKind, order: CategoryOrder) {
            calls += "order $order"
            inner.setOrder(sourceId, kind, order)
        }

        override fun move(sourceId: String, kind: CategoryKind, ids: List<String>) {
            calls += "move ${ids.joinToString(" ")}"
            inner.move(sourceId, kind, ids)
        }

        override fun rename(kind: CategoryKind, categoryId: String, name: String) {
            calls += "rename $categoryId $name"
            inner.rename(kind, categoryId, name)
        }

        override fun create(sourceId: String, kind: CategoryKind, name: String) {
            calls += "create $name"
            inner.create(sourceId, kind, name)
        }

        override fun setMember(categoryId: String, itemId: String, member: Boolean) {
            calls += "member $categoryId $itemId $member"
            inner.setMember(categoryId, itemId, member)
        }
    }

    private val editor = Recording()

    private fun open(page: SettingsPage = SettingsPage.LiveCategories) =
        screen { SettingsScreen(page, SettingsValues(), SettingsActions(categories = editor), sourceId = "home") }

    /** The focused row reads as [description], whole. */
    private fun focusedIs(description: String) = focused().assert(hasContentDescription(description))

    private fun check(vararg calls: String) = check(editor.calls == calls.toList()) { "Asked ${editor.calls}" }

    @Test fun okShowsOrHidesACategory() {
        open()
        // The order, Show or Hide All, New Category, then the categories: the user's own first.
        focusedIs("Order, Provider’s Order")
        press(Key.DirectionDown, times = 3)
        focusedIs("Game Day, Yours · 4 channels, On")
        press(Key.DirectionDown)
        focusedIs("Sports, 186 channels, On")

        press(Key.DirectionCenter)
        focusedIs("Sports, 186 channels, Off")
        press(Key.DirectionCenter)
        focusedIs("Sports, 186 channels, On")
        check("hide sports true", "hide sports false")
    }

    @Test fun showOrHideAllDoesEveryCategory() {
        open()
        press(Key.DirectionDown)
        // Kids starts hidden.
        focusedIs("Show or Hide All, 8 of 9 shown")
        press(Key.DirectionCenter)
        focused().assert(hasText("Show All"))
        press(Key.DirectionDown)
        focused().assert(hasText("Hide All"))
        press(Key.DirectionCenter)

        focusedIs("Show or Hide All, 0 of 9 shown")
        check("all true")
    }

    @Test fun theOrderIsPickedFromTheThree() {
        open()
        press(Key.DirectionCenter)
        // The one in force has focus, ticked.
        focused().assert(hasText("Provider’s Order"))
        press(Key.DirectionDown)
        focused().assert(hasText("A to Z"))
        press(Key.DirectionCenter)

        focusedIs("Order, A to Z")
        check("order AToZ")
        // A to Z, numbers first, the user's own among the rest.
        press(Key.DirectionDown, times = 3)
        focusedIs("4K UHD, 36 channels, On")
        press(Key.DirectionDown, times = 3)
        focusedIs("Game Day, Yours · 4 channels, On")
    }

    @Test fun moveFromACategorysOptionsMakesItTheUsersOrder() {
        open()
        press(Key.DirectionDown, times = 4)
        focusedIs("Sports, 186 channels, On")
        // Menu offers Rename and Move rather than lifting it.
        press(Key.Menu)
        focused().assert(hasText("Rename"))
        press(Key.DirectionDown)
        focused().assert(hasText("Move"))
        press(Key.DirectionCenter)

        focusedIs("Sports, 186 channels, On, moving")
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        check("move own-game-day news sports movies entertainment documentary kids music uhd")
        focusedIs("Sports, 186 channels, On")
        compose.onNodeWithContentDescription("Order, My Order").assertExists()
        press(Key.DirectionUp)
        focusedIs("News, 92 channels, On")
    }

    @Test fun renameGivesACategoryANameOfItsOwn() {
        open()
        press(Key.DirectionDown, times = 5)
        focusedIs("News, 92 channels, On")
        press(Key.Menu)
        focused().assert(hasText("Rename"))
        press(Key.DirectionCenter)

        // The field has the name in it, and focus.
        compose.onNodeWithContentDescription("Name, News").assertExists()
        press(Key.Backspace, times = 4)
        press(Key.T)
        press(Key.O)
        press(Key.P)
        // OK moves to the keyboard, Next from the only field to Save.
        press(Key.DirectionCenter)
        press(Key.DirectionDown, times = 4)
        press(Key.DirectionRight, times = 8)
        focused().assert(hasText("Next"))
        press(Key.DirectionCenter)
        focused().assert(hasText("Save"))
        press(Key.DirectionCenter)

        check("rename news top")
        focusedIs("top, Provider’s name: News · 92 channels, On")
    }

    @Test fun newCategoryIsNamedThenMade() {
        open()
        press(Key.DirectionDown, times = 2)
        focusedIs("New Category")
        press(Key.DirectionCenter)
        press(Key.G)
        press(Key.O)
        press(Key.DirectionCenter)
        press(Key.DirectionDown, times = 4)
        press(Key.DirectionRight, times = 8)
        focused().assert(hasText("Next"))
        press(Key.DirectionCenter)
        focused().assert(hasText("Create"))
        press(Key.DirectionCenter)

        check("create go")
        compose.onNodeWithContentDescription("go, Yours · 0 channels, On").assertExists()
    }

    @Test fun aCategoryOfYourOwnHasItsChannelsAndAddsMore() {
        open()
        press(Key.DirectionDown, times = 3)
        focusedIs("Game Day, Yours · 4 channels, On")
        press(Key.Menu)
        focused().assert(hasText("Channels"))
        press(Key.DirectionCenter)

        // What's in it, after Add Channels; OK takes one out, and it stays to put back.
        focused().assert(hasText("Add Channels"))
        press(Key.DirectionDown)
        focused().assert(hasText("Arena Sports 1"))
        press(Key.DirectionCenter)
        check("member own-game-day 105 false")
        focused().assert(hasText("Arena Sports 1"))

        // Add Channels: the source's categories, then one's channels.
        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        focused().assert(hasText("Sports"))
        press(Key.DirectionCenter)
        focused().assert(hasText("Arena Sports News"))
        press(Key.DirectionCenter)
        check("member own-game-day 105 false", "member own-game-day 103 true")

        // Back goes back a step at a time.
        press(Key.Back)
        focused().assert(hasText("Sports"))
        press(Key.Back)
        focused().assert(hasText("Add Channels"))
        press(Key.Back)
        focusedIs("Game Day, Yours · 4 channels, On")
    }

    @Test fun movieCategoriesHaveTheirOwnPage() {
        open(SettingsPage.MovieCategories)
        press(Key.DirectionDown, times = 3)
        focusedIs("Action, 1,204 movies, On")
    }

    @Test fun aSourcesCategoriesOpenByKind() {
        val opened = mutableListOf<Pair<String, SourceAction>>()
        screen { SettingsScreen(SettingsPage.Sources, SettingsValues(), SettingsActions(onSource = { id, action -> opened += id to action })) }
        // Home Portal's actions: Edit, Refresh Now, Test Connection, Account Info, then Categories.
        press(Key.DirectionCenter)
        press(Key.DirectionDown, times = 4)
        focused().assert(hasText("Categories"))
        press(Key.DirectionCenter)
        focused().assert(hasText("Live TV"))
        press(Key.DirectionDown)
        focused().assert(hasText("Movies"))
        press(Key.DirectionCenter)

        check(opened == listOf("home" to SourceAction.MovieCategories)) { "Opened $opened" }
    }
}
