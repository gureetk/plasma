// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.tv

import ca.gureet.plasma.remote.protocol.RemoteKey
import ca.gureet.plasma.remote.protocol.RemoteLists
import ca.gureet.plasma.remote.protocol.RemoteState
import ca.gureet.plasma.remote.protocol.RemoteTitles
import kotlinx.coroutines.flow.StateFlow

/** Whether the app can reach the TV it's paired with now. */
enum class LinkStatus { Connecting, Connected, Unreachable }

/**
 * A TV as the app's screens see it (brief 5.21 and 5.22): what it shows, and what a phone can
 * send it. A paired TV over the network is one; the demo TV, which needs no network, is another.
 */
interface TvLink {
    /** The TV's name, as it calls itself ("Living Room TV"). */
    val name: String

    val status: StateFlow<LinkStatus>

    /** What the TV shows now: whether Plasma's in front, what plays, the colour keys, a text field. */
    val state: StateFlow<RemoteState>

    /** A key pressed ([down]) or let go: the TV repeats a held one, as its own remote's. */
    fun key(key: RemoteKey, down: Boolean)

    /** Number entry, a digit at a time: the TV collects them as from its own remote's keys. */
    fun digits(digits: String)

    /** The whole text of the TV's focused field, as it is on the phone. */
    fun text(text: String)

    /** Done, on the phone's keyboard. */
    fun submit()

    /** A channel tapped on the Channels tab, in [listId], the list it was tapped in. */
    fun tune(channelId: String, listId: String?)

    /** Go Live: back to the live edge from the rewind buffer. */
    fun goLive()

    /** The lists, and [listId]'s channels from [from]; null for the TV's first list, and null back when the TV doesn't answer. */
    suspend fun lists(listId: String?, from: Int): RemoteLists?

    /** A channel's logo as the TV has it (PNG, JPEG or WebP), or null for none. */
    suspend fun logo(channelId: String): ByteArray?

    /**
     * Movies' or Series' categories ([kind] is [RemoteTitles.MOVIES] or [RemoteTitles.SERIES]),
     * and [categoryId]'s titles from [from]; null for the first category, and null back when the
     * TV doesn't answer (#230).
     */
    suspend fun titles(kind: String, categoryId: String?, from: Int): RemoteTitles? = null

    /** A title tapped: its page opens on the TV; long-pressed ([play]), it plays from where it was left. */
    fun open(kind: String, id: String, play: Boolean) {}

    /** A title's poster, as the TV made it for a row (JPEG), or null for none. */
    suspend fun poster(kind: String, id: String): ByteArray? = null

    companion object {
        /** How many channels a list sends at a time (the web page's own). */
        const val PAGE = 200
    }
}
