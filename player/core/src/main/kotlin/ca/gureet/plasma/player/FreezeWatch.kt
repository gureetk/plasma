// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

/**
 * Restart Frozen Streams as it goes (the owner's #91), for Show Freeze Watch (Settings › Playback ›
 * Advanced, the owner's ask of 2 November): what it's doing about the stream on screen, and what
 * it has seen and done since Plasma started. The player keeps it whether or not the overlay shows,
 * so the counts are there when it's turned on; it's a few numbers at each look.
 */
data class FreezeWatch(
    /** What it's doing about the stream on screen. */
    val now: FreezeLook = FreezeLook(),
    /** Channels opened again because they froze, by the engine they froze in. */
    val restarts: Map<EngineId, Int> = emptyMap(),
    /** Streams handed to the next engine for freezing a third time within ten minutes, by the engine they froze in. */
    val handedOn: Map<EngineId, Int> = emptyMap(),
    /** The latest of what it did and saw, newest first: at most [EVENTS]. */
    val events: List<FreezeEvent> = emptyList(),
    /** When it started counting, in ms since the epoch: as Plasma started. */
    val since: Long = 0,
) {
    /** Notes [event]: the newest first, and the counts. */
    internal fun with(event: FreezeEvent): FreezeWatch = copy(
        events = (listOf(event) + events).take(EVENTS),
        restarts = if (event.kind == FreezeEvent.Kind.Restarted) restarts.plusOne(event.engine) else restarts,
        handedOn = if (event.kind == FreezeEvent.Kind.NextEngine) handedOn.plusOne(event.engine) else handedOn,
    )

    companion object {
        /** How many events it keeps. */
        const val EVENTS = 8
    }
}

private fun Map<EngineId, Int>.plusOne(engine: EngineId): Map<EngineId, Int> = this + (engine to (this[engine] ?: 0) + 1)

/** What Restart Frozen Streams is doing about the stream on screen (#91). */
data class FreezeLook(
    /** The key on screen (a channel, a programme from the archive, a movie or an episode); null while nothing plays. */
    val channelId: String? = null,
    /** The engine playing it. */
    val engine: EngineId? = null,
    val state: State = State.Idle,
    /** The setting's time, in ms; null while it's Off. */
    val afterMs: Long? = null,
    /** While [State.Watching]: how long what it goes by has stood still, in ms; 0 while it moves. */
    val stillMs: Long = 0,
    /** While it's looked at: it goes by the engine's place in the stream, as the engine can't count. */
    val byPlace: Boolean = false,
    /** While [State.Watching]: what came through the stream hub since it last moved, in bytes. */
    val arrivedBytes: Long = 0,
) {
    enum class State {
        /** Nothing plays. */
        Idle,

        /** Restart Frozen Streams is Off. */
        Off,

        /** Not live TV: a programme from the archive, a movie, an episode or a recording. It's not looked at. */
        NotLive,

        /** A live channel that goes straight to the engine (HLS, Use Stream Hub off, multicast): not looked at. */
        SkipsHub,

        /** Opening, or opened again after a seek or Go Live. */
        Opening,

        /** Paused. */
        Paused,

        /** The engine says it's buffering. */
        Buffering,

        /** The stream hub lost the stream and is connecting again. */
        Reconnecting,

        /** Played from the rewind buffer, behind live. */
        BehindLive,

        /** Playing, before its first picture. */
        NoPicture,

        /** Playing, but what it goes by hasn't moved since the stream opened: it can't stand still yet. */
        NotMoved,

        /** Playing, and looked at once a second. */
        Watching,
    }
}

/** Something Restart Frozen Streams did or saw (#91), at [at] ms since the epoch, about [channelId] in [engine]. */
data class FreezeEvent(
    val at: Long,
    val kind: Kind,
    val channelId: String,
    val engine: EngineId,
    /** [Kind.Restarted] and [Kind.NextEngine]: how long it had stood still. [Kind.Buffered]: how long the engine buffered. */
    val ms: Long = 0,
    /** [Kind.Restarted] and [Kind.NextEngine]: what came through the stream hub while it stood still, in bytes. */
    val bytes: Long = 0,
    /** [Kind.Reconnecting]: the hub's try, from 1. */
    val attempt: Int = 0,
    /** [Kind.EngineSwitched]: the engine that took over. */
    val to: EngineId? = null,
) {
    enum class Kind {
        /** Frozen: opened again on a new connection, in the same engine. */
        Restarted,

        /** Frozen a third time within ten minutes: handed to the next engine. */
        NextEngine,

        /** The engine buffered, after it had played, for [ms]. */
        Buffered,

        /** The stream hub lost the stream and is connecting again. */
        Reconnecting,

        /** Another engine took the stream over: [engine] couldn't play it, kept stalling, or froze too often. */
        EngineSwitched,
    }
}
