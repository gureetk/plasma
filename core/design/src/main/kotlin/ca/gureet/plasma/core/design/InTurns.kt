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
import kotlinx.coroutines.delay

/**
 * Turns for the pieces of a screen that are out of sight (#127): a row below the screen's edge,
 * and each tile in it. Made all at once, in the frame their contents come, they hold the screen
 * for as long as they all take, with nothing of them to see. Taking turns, a few are made at each
 * frame, so no frame is long and what's on screen goes on answering the remote meanwhile.
 *
 * A piece asks with [InItsTurn], and is made when its turn comes: the nearest to what's in sight
 * first ([Turns.at]), and of two as near, the one that asked first. What's nearest is what the
 * next key brings into sight, and it may be the last to ask: a row whose contents come late.
 * A piece in sight is given no turns to wait for (`null`) and is made at once, and one that comes
 * into sight while it waits is made right then: nothing in sight is ever waited for, so what's
 * seen is as it would be without turns. Once made, a piece stays made.
 *
 * [give] hands the turns out for as long as it runs: it's run while the screen is in front. The
 * remote comes first: no turn is given in the frame that answers a key, nor for as long after it
 * as the screen says.
 *
 * A piece has a weight, in tiles' worth of work: a frame is given as many pieces from the head
 * of the line as it has room for, and one whatever it weighs. With room for one, that's a piece
 * a frame.
 */
@Stable
class Turns private constructor(private val line: Line, private val rank: Int) {
    constructor() : this(Line(), 0)

    private val waiting get() = line.waiting
    private val asked get() = line.asked

    /**
     * These turns for pieces [rank] places from what's in sight: a row's place down the screen.
     * The same object each time for the same place, so that a piece handed it isn't composed
     * again for it.
     */
    fun at(rank: Int): Turns = if (rank == this.rank) this else line.places.getOrPut(rank) { Turns(line, rank) }

    /** How many pieces wait for their turn. */
    val waitingCount: Int get() = waiting.size

    internal fun wait(turn: Turn, weight: Int) {
        turn.rank = rank
        turn.weight = weight
        // Behind the pieces as near or nearer, and before those further off.
        var at = waiting.size
        while (at > 0 && waiting[at - 1].rank > rank) at--
        waiting.add(at, turn)
        asked.trySend(Unit)
    }

    internal fun leave(turn: Turn) {
        waiting.remove(turn)
        if (waiting.isEmpty()) line.emptied.trySend(Unit)
    }

    /**
     * Returns once no piece waits for a turn: at once where none does. A piece may ask again
     * straight after; whoever needs to know that everything is made looks again a frame later.
     */
    suspend fun untilNoneWaits() {
        while (waiting.isNotEmpty()) line.emptied.receive()
    }

    /**
     * At each frame, the turns of the pieces at the head of the line, the nearest and of those
     * the ones that have waited longest: as many as weigh [aFrame] together, and the first of
     * them whatever it weighs; it sleeps while none waits. [quietInMs] says
     * how long the turns still have to keep out of the way, in milliseconds, 0 or less when they
     * needn't: a key and what it moves get their frames to themselves, and the turns go on
     * afterwards. It's asked before a frame is waited for, and again as that frame begins
     * ([atTheFrameToo]): a key comes between two frames, after the next one was asked for, and
     * the frame that answers it is the one that most needs to be short. [onNoneLeft] runs in the
     * frame that makes the last one waiting.
     */
    suspend fun give(
        aFrame: Int = 1,
        quietInMs: () -> Long = { 0L },
        atTheFrameToo: Boolean = true,
        onNoneLeft: () -> Unit = {},
    ): Nothing {
        while (true) {
            if (waiting.isEmpty()) {
                asked.receive()
                continue
            }
            val keepOut = quietInMs()
            if (keepOut > 0) {
                delay(keepOut)
                continue
            }
            // Within the frame's own call, so that the frame that's starting composes the pieces:
            // set once it has returned, they'd be a frame late.
            withFrameNanos {
                if (waiting.isNotEmpty() && !(atTheFrameToo && quietInMs() > 0)) {
                    var room = aFrame
                    do {
                        val next = waiting.removeAt(0)
                        next.come = true
                        room -= next.weight
                    } while (waiting.isNotEmpty() && waiting[0].weight <= room)
                    if (waiting.isEmpty()) {
                        onNoneLeft()
                        line.emptied.trySend(Unit)
                    }
                }
            }
        }
    }
}

/** The pieces that wait, the nearest first, shared by the turns for every place ([Turns.at]). */
private class Line {
    val waiting = ArrayList<Turn>()
    val asked = Channel<Unit>(Channel.CONFLATED)

    /** Told each time the last piece waiting has had its turn or has left ([Turns.untilNoneWaits]). */
    val emptied = Channel<Unit>(Channel.CONFLATED)
    val places = HashMap<Int, Turns>()
}

/**
 * A piece's turn: [come] once it has, [kept] once the piece has been made, in its turn or without
 * one, how near it is ([rank]), and what it weighs in a frame ([weight]).
 */
internal class Turn {
    var come by mutableStateOf(false)
    var kept = false
    var rank = 0
    var weight = 1
}

/**
 * Whether the piece that asks is to be made now: at once without [turns], and otherwise once its
 * turn has come. A piece made once is made for good, whatever [turns] it's given afterwards.
 * While it waits, only what reads this composes again when its turn comes. [weight] is how much
 * of a frame it takes, in tiles' worth: 1 for a plain tile.
 */
@Composable
fun madeInItsTurn(turns: Turns?, weight: Int = 1): Boolean {
    val turn = remember { Turn() }
    // The turn itself is read only by a piece that waits: one that's made has nothing to hear of.
    if (turns == null || turn.kept || turn.come) {
        turn.kept = true
        return true
    }
    DisposableEffect(turns) {
        turns.wait(turn, weight)
        onDispose { turns.leave(turn) }
    }
    return false
}

/**
 * [content] in its turn ([madeInItsTurn]), with [waiting] in its place until then: something of
 * its size where its room has to be kept, or nothing.
 */
@Composable
fun InItsTurn(turns: Turns?, weight: Int = 1, waiting: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    if (madeInItsTurn(turns, weight)) content() else waiting()
}
