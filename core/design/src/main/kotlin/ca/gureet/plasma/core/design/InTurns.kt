// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.channels.Channel

/**
 * Turns for the pieces of a screen that are out of sight (#127): a row below the screen's edge,
 * and each tile in it. Made all at once, in the frame their contents come, they hold the screen
 * for as long as they all take, with nothing of them to see. Taking turns, a few are made at each
 * frame, so no frame is long and what's on screen goes on answering the remote meanwhile.
 *
 * A piece asks with [InItsTurn], and is made when its turn comes, in the order the pieces asked.
 * A piece in sight is given no turns to wait for (`null`) and is made at once, and one that comes
 * into sight while it waits is made right then: nothing in sight is ever waited for, so what's
 * seen is as it would be without turns. Once made, a piece stays made.
 *
 * [give] hands the turns out for as long as it runs: it's run while the screen is in front.
 */
@Stable
class Turns {
    private val waiting = ArrayDeque<Turn>()
    private val asked = Channel<Unit>(Channel.CONFLATED)

    /** How many pieces wait for their turn. */
    val waitingCount: Int get() = waiting.size

    internal fun wait(turn: Turn) {
        waiting.addLast(turn)
        asked.trySend(Unit)
    }

    internal fun leave(turn: Turn) {
        waiting.remove(turn)
    }

    /**
     * At each frame, the turns of the [aFrame] pieces that have waited longest; it sleeps while
     * none waits. [onNoneLeft] runs in the frame that makes the last one waiting.
     */
    suspend fun give(aFrame: Int = 1, onNoneLeft: () -> Unit = {}): Nothing {
        while (true) {
            if (waiting.isEmpty()) {
                asked.receive()
                continue
            }
            // Within the frame's own call, so that the frame that's starting composes the pieces:
            // set once it has returned, they'd be a frame late.
            withFrameNanos {
                if (waiting.isNotEmpty()) {
                    repeat(aFrame) { waiting.removeFirstOrNull()?.come = true }
                    if (waiting.isEmpty()) onNoneLeft()
                }
            }
        }
    }
}

/** A piece's turn: [come] once it has, and [kept] once the piece has been made, in its turn or without one. */
internal class Turn {
    var come by mutableStateOf(false)
    var kept = false
}

/**
 * Whether the piece that asks is to be made now: at once without [turns], and otherwise once its
 * turn has come. A piece made once is made for good, whatever [turns] it's given afterwards.
 * While it waits, only what reads this composes again when its turn comes.
 */
@Composable
fun madeInItsTurn(turns: Turns?): Boolean {
    val turn = remember { Turn() }
    // The turn itself is read only by a piece that waits: one that's made has nothing to hear of.
    if (turns == null || turn.kept || turn.come) {
        turn.kept = true
        return true
    }
    DisposableEffect(turns) {
        turns.wait(turn)
        onDispose { turns.leave(turn) }
    }
    return false
}

/**
 * [content] in its turn ([madeInItsTurn]), with [waiting] in its place until then: something of
 * its size where its room has to be kept, or nothing.
 */
@Composable
fun InItsTurn(turns: Turns?, waiting: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    if (madeInItsTurn(turns)) content() else waiting()
}
