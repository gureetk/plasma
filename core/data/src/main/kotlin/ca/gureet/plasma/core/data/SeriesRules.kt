// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.model.PlasmaClock
import java.time.Duration

/**
 * Record Series (brief 5.6): each rule's airings in the guide ahead, set as recordings. A rule on
 * one channel records from it alone; one on any channel, from whichever channel shows the title.
 * An episode already recorded, or set to be, isn't set again: the same season and episode, or
 * else the same episode title; without either, the same start on another channel. An airing
 * cancelled isn't set again either, though a repeat of it can be.
 */
class SeriesRules(
    private val recordings: RecordingRepository,
    private val guide: GuideRepository,
    private val clock: PlasmaClock,
) {
    /**
     * Sets what the rules find from now until [ahead], on [channels] (the lineup), with
     * [padding] (before, after) for each kind. The number of recordings set.
     */
    suspend fun schedule(channels: List<Channel>, padding: (RecordingKind) -> Pair<Duration, Duration>, ahead: Duration = AHEAD): Int {
        val now = clock.now()
        // A guide channel shows on the first channel that has it, hidden ones aside.
        val byKey = channels.filter { !it.hidden && it.epgKey != null }.groupBy { it.epgKey!! }
        var added = 0
        for (rule in recordings.rulesNow()) {
            val own = rule.channelId?.let { id -> channels.firstOrNull { it.id == id } }
            if (rule.channelId != null && own == null) continue
            val set = recordings.ofRule(rule.id).toMutableList()
            val cancelled = recordings.cancelledOfRule(rule.id)
            for (programme in guide.titled(rule.title, now, now.plus(ahead))) {
                val channel = if (own != null) own.takeIf { it.epgKey == programme.epgKey } else byKey[programme.epgKey]?.firstOrNull()
                if (channel == null || RecordingRepository.idOf(channel.id, programme.start) in cancelled) continue
                if (set.any { it.isSameAiring(programme) }) continue
                val kind = RecordingKind.guess(programme.category, programme.season, programme.episode).let { if (it == RecordingKind.Other) RecordingKind.Series else it }
                val (before, after) = padding(kind)
                set += recordings.schedule(
                    ProgrammeToRecord(
                        channelId = channel.id,
                        channelName = channel.name,
                        channelLogo = channel.logo,
                        title = programme.title,
                        subtitle = programme.subtitle,
                        description = programme.description,
                        category = programme.category,
                        image = programme.icon,
                        season = programme.season,
                        episode = programme.episode,
                        start = programme.start,
                        end = programme.end,
                    ),
                    before,
                    after,
                    ruleId = rule.id,
                    kind = kind,
                )
                added++
            }
        }
        return added
    }

    /** Whether this recording is [programme] already, or the same episode of it. */
    private fun Recording.isSameAiring(programme: Programme): Boolean {
        if (start == programme.start) return true
        if (season != null && episode != null && programme.season != null && programme.episode != null) {
            return season == programme.season && episode == programme.episode
        }
        val mine = subtitle?.trim()?.takeIf { it.isNotEmpty() }
        val theirs = programme.subtitle?.trim()?.takeIf { it.isNotEmpty() }
        return mine != null && theirs != null && mine.equals(theirs, ignoreCase = true)
    }

    private companion object {
        /** How far ahead rules look: as far as most guides go. */
        val AHEAD: Duration = Duration.ofDays(8)
    }
}
