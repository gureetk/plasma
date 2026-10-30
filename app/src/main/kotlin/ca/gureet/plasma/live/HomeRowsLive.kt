// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.core.data.Channel
import ca.gureet.plasma.core.data.Recording
import ca.gureet.plasma.core.data.RecordingKind
import ca.gureet.plasma.core.data.RecordingStatus
import ca.gureet.plasma.core.data.Reminder
import ca.gureet.plasma.core.data.ReminderRepository
import ca.gureet.plasma.core.model.UpcomingCandidate
import ca.gureet.plasma.core.model.UpcomingEvents
import ca.gureet.plasma.core.model.UpcomingKind
import ca.gureet.plasma.core.model.UpcomingReason
import ca.gureet.plasma.feature.home.RecordedItem
import ca.gureet.plasma.feature.home.UpcomingItem
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Home's Upcoming Events (the owner's #80) from what's kept and the guide in memory: the reminders
 * and recordings set, starting within a day, and a recording being made; programs starting within
 * the hour on favorites; and what the guide lists as sport, on now or within two hours. Only the
 * [kinds] turned on bring programs in. Each shows on its channel as the lists have it: a hidden
 * channel, or one the profile doesn't see, brings nothing in, and the guide's own programs on a
 * locked channel, or rated past the Age Limit, stay out too (5.12).
 */
internal fun upcomingEvents(world: LiveWorld, reminders: List<Reminder>, recordings: List<Recording>, kinds: Set<UpcomingKind>, zone: ZoneId): List<UpcomingItem> {
    val now = world.now
    val candidates = ArrayList<UpcomingCandidate>()

    fun shown(channelId: String): Channel? = world.byId[channelId]?.takeIf { !it.hidden }

    fun add(id: String, channel: Channel, title: String, start: Instant, end: Instant, reason: UpcomingReason) {
        candidates += UpcomingCandidate(id, channel.id, title, start, end, reason, channel.number)
    }

    if (UpcomingKind.RemindersAndRecordings in kinds) {
        // Under their own ids, which cancelling them takes.
        for (reminder in reminders) {
            val channel = shown(reminder.channelId) ?: continue
            add(reminder.id, channel, reminder.title, reminder.start, reminder.end, UpcomingReason.Reminder)
        }
        for (recording in recordings) {
            if (recording.status != RecordingStatus.Scheduled && recording.status != RecordingStatus.Recording) continue
            val channel = shown(recording.channelId) ?: continue
            add(recording.id, channel, recording.title, recording.start, recording.end, UpcomingReason.Recording)
        }
    }
    if (UpcomingKind.StartingSoonOnFavorites in kinds) {
        val until = now.plus(UpcomingEvents.FAVORITES_AHEAD)
        for (id in world.favoriteChannelIds) {
            val channel = shown(id)?.takeIf { !it.locked } ?: continue
            for (programme in world.programmesOf(channel)) {
                if (programme.start.isAfter(until)) break
                if (!programme.start.isAfter(now) || world.overLimit(programme)) continue
                add(ReminderRepository.idOf(channel.id, programme.start), channel, programme.title, programme.start, programme.end, UpcomingReason.Favorite)
            }
        }
    }
    if (UpcomingKind.LiveSportsAndEvents in kinds) {
        val until = now.plus(UpcomingEvents.SPORTS_AHEAD)
        // A guide channel several channels share (one channel in several qualities) shows once,
        // on the first of them by number.
        val byGuide = HashMap<String, Channel>()
        for (channel in world.channels) {
            val key = channel.epgKey ?: continue
            if (channel.hidden || channel.locked) continue
            val had = byGuide[key]
            if (had == null || channel.number < had.number) byGuide[key] = channel
        }
        for ((key, programmes) in world.programmes) {
            val channel = byGuide[key] ?: continue
            for (programme in programmes) {
                if (programme.start.isAfter(until)) break
                if (!programme.end.isAfter(now) || !UpcomingEvents.isSports(programme.category) || world.overLimit(programme)) continue
                add(ReminderRepository.idOf(channel.id, programme.start), channel, programme.title, programme.start, programme.end, UpcomingReason.Sports)
            }
        }
    }
    return UpcomingEvents.pick(candidates, now, kinds).mapNotNull { event ->
        val channel = world.byId[event.channelId] ?: return@mapNotNull null
        UpcomingItem(
            id = event.id,
            channelId = channel.id,
            channelName = channel.name,
            logo = channel.logo,
            title = event.title,
            start = event.start.atZone(zone),
            end = event.end.atZone(zone),
            reminder = event.reminder,
            recording = event.recording,
            fromGuide = UpcomingReason.Favorite in event.reasons || UpcomingReason.Sports in event.reasons,
        )
    }
}

/**
 * Home's Recently Recorded (the owner's #80): the finished recordings that play, the latest to air
 * first, as Recordings sorts them, at most [limit]; news has its time on its line, as on Recordings.
 * A recording's channel's logo is the one it has now, where it still has the channel.
 */
internal fun recentlyRecorded(recordings: List<Recording>, world: LiveWorld?, zone: ZoneId, limit: Int = RECENTLY_RECORDED): List<RecordedItem> =
    recordings.asSequence()
        .filter { it.status == RecordingStatus.Done && (it.file != null || it.onShareOnly) }
        .sortedByDescending { it.start }
        .take(limit)
        .map { recording ->
            val length = recording.recordedFor.takeIf { !it.isZero } ?: Duration.between(recording.start, recording.end)
            val at = recording.positionMs.takeIf { it > 0 && !recording.watched }
            RecordedItem(
                id = recording.id,
                title = recording.title,
                channelName = recording.channelName,
                logo = world?.byId?.get(recording.channelId)?.logo ?: recording.channelLogo,
                image = recording.image,
                aired = recording.start.atZone(zone),
                length = length,
                progress = at?.let { (it.toFloat() / length.toMillis().coerceAtLeast(1)).coerceIn(0f, 1f) },
                unwatched = !recording.watched && recording.positionMs == 0L,
                news = recording.kind == RecordingKind.News,
            )
        }
        .toList()

/** Recently Recorded has this many, then See All. */
private const val RECENTLY_RECORDED = 20
