// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.annotation.ArrayRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.model.Formats
import ca.gureet.plasma.core.model.SettingKeys
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import ca.gureet.plasma.core.design.R as DesignR

/** A setting with a list of options: what they are, which is chosen, and the action that offers them. */
internal class Choice(val options: List<String>, val selected: Int, val action: SettingAction.Choose) {
    val value: String get() = options[selected]
}

/** A switch: whether it's on, "On" or "Off", and the action that flips it. */
internal class Switch(val on: Boolean, val value: String, val action: SettingAction.Toggle)

@Composable
internal fun SettingsValues.choice(key: String, @ArrayRes options: Int, default: Int, note: String? = null): Choice {
    val list = stringArrayResource(options).toList()
    val selected = index(key, default).coerceIn(list.indices)
    return Choice(list, selected, SettingAction.Choose(key, list, selected, note))
}

@Composable
internal fun SettingsValues.switch(key: String, default: Boolean): Switch {
    val on = isOn(key, default)
    return Switch(on, stringResource(if (on) R.string.on else R.string.off), SettingAction.Toggle(key, on))
}

/** A row whose options open on the right; the detail lists them under its description. */
@Composable
internal fun choiceRow(
    id: String,
    label: String,
    choice: Choice,
    icon: ca.gureet.plasma.core.design.icon.PlasmaIcons,
    text: String,
    subtitle: String? = null,
) = SettingItem(
    id = id,
    label = label,
    value = choice.value,
    subtitle = subtitle,
    action = choice.action,
    detail = SettingDetail(icon, label, text = text, extra = { ChoicesPreview(choice.options, choice.selected) }),
)

/** A switch row; its detail says what the switch does. */
@Composable
internal fun switchRow(
    id: String,
    label: String,
    switch: Switch,
    icon: ca.gureet.plasma.core.design.icon.PlasmaIcons,
    text: String,
    subtitle: String? = null,
) = SettingItem(
    id = id,
    label = label,
    value = switch.value,
    subtitle = subtitle,
    action = switch.action,
    detail = SettingDetail(icon, label, text = text),
)

/** A row that leads somewhere, or isn't ready to; its detail explains it. */
internal fun infoRow(
    id: String,
    label: String,
    value: String?,
    icon: ca.gureet.plasma.core.design.icon.PlasmaIcons,
    text: String,
    action: SettingAction = SettingAction.None,
    subtitle: String? = null,
) = SettingItem(id = id, label = label, value = value, subtitle = subtitle, action = action, detail = SettingDetail(icon, label, text = text))

// ---- Settings (board 19) ---------------------------------------------------------------------------

@Composable
internal fun SourceHealth.status(withPing: Boolean): Status = when (this) {
    is SourceHealth.Connected -> {
        val ping = pingMs
        Status(if (withPing && ping != null) stringResource(R.string.status_connected_ping, ping) else stringResource(R.string.status_connected), Tone.Ok)
    }
    SourceHealth.Standby -> Status(stringResource(R.string.status_standby), Tone.Idle)
    is SourceHealth.GuideOld -> Status(pluralStringResource(R.plurals.status_guide_old, days, days), Tone.Warn)
    SourceHealth.RefreshFailed -> Status(stringResource(R.string.status_refresh_failed), Tone.Warn)
}

@Composable
internal fun mainPage(values: SettingsValues, facts: SettingsFacts): PageSpec {
    val sourcesLabel = stringResource(R.string.sources)
    val statuses = facts.sources.map { it.name to it.health.status(withPing = false) }
    val engine = values.choice(KEY_ENGINE, R.array.engine_options, 0)
    val frameRate = values.choice(KEY_FRAME_RATE, R.array.frame_rate_options, 1)
    val favorites = values.choice(KEY_FAVORITE_NUMBERS, R.array.numbering_options, 0)
    val recordTo = values.choice(KEY_RECORD_TO, R.array.record_to_options, 0)
    val clock = values.choice(KEY_CLOCK, R.array.clock_options, 0)

    fun page(id: String, label: String, value: String, icon: PlasmaIcons, text: String, page: SettingsPage) = SettingItem(
        id = id,
        label = label,
        value = value,
        icon = icon,
        action = SettingAction.Open(page),
        detail = SettingDetail(icon, label, text = text),
    )

    return PageSpec(
        title = stringResource(DesignR.string.settings),
        back = stringResource(DesignR.string.tab_home),
        style = PageStyle.Large,
        metrics = RowMetrics.Main,
        detailTop = 190.px,
        listBottom = 1040.px,
        sections = listOf(
            SettingSection(
                label = null,
                items = listOf(
                    SettingItem(
                        id = "sources",
                        label = sourcesLabel,
                        value = pluralStringResource(R.plurals.source_count, facts.sources.size, facts.sources.size),
                        icon = PlasmaIcons.Server,
                        action = SettingAction.Open(SettingsPage.Sources),
                        detail = SettingDetail(
                            PlasmaIcons.ServerLines,
                            sourcesLabel,
                            text = stringResource(R.string.sources_text),
                            extra = { SourceStatusLines(statuses) },
                        ),
                    ),
                    page(
                        "channels", stringResource(R.string.channels),
                        stringResource(if (favorites.selected == 0) R.string.channels_value_from_one else R.string.channels_value_keep),
                        PlasmaIcons.Hash, stringResource(R.string.channels_text), SettingsPage.Channels,
                    ),
                    page(
                        "guide", stringResource(R.string.guide),
                        stringResource(
                            R.string.guide_value,
                            pluralStringResource(R.plurals.xmltv_feeds, facts.xmltvFeeds, facts.xmltvFeeds),
                            pluralStringResource(R.plurals.day_count, facts.guideDays, facts.guideDays),
                        ),
                        PlasmaIcons.GuideTable, stringResource(R.string.guide_text), SettingsPage.Guide,
                    ),
                    page(
                        "playback", stringResource(R.string.playback),
                        stringResource(R.string.playback_value, engine.value, stringResource(if (frameRate.selected == 0) R.string.state_off else R.string.state_on)),
                        PlasmaIcons.PlayCircle, stringResource(R.string.playback_text), SettingsPage.Playback,
                    ),
                    page(
                        "recording", stringResource(R.string.recording),
                        stringResource(R.string.recording_value, recordTo.value, Formats.size(facts.driveFree)),
                        PlasmaIcons.RecordRing, stringResource(R.string.recording_text), SettingsPage.Recording,
                    ),
                    page(
                        "parental", stringResource(R.string.parental),
                        stringResource(R.string.parental_value, facts.ageLimit),
                        PlasmaIcons.Shield, stringResource(R.string.parental_text), SettingsPage.Parental,
                    ),
                    page(
                        "profiles", stringResource(R.string.profiles),
                        pluralStringResource(R.plurals.profile_count, facts.profiles, facts.profiles),
                        PlasmaIcons.People, stringResource(R.string.profiles_text), SettingsPage.Profiles,
                    ),
                ),
            ),
            SettingSection(
                label = null,
                divider = true,
                items = listOf(
                    page(
                        "launcher", stringResource(R.string.launcher), stringResource(R.string.launcher_value),
                        PlasmaIcons.House, stringResource(R.string.launcher_text), SettingsPage.Launcher,
                    ),
                    page(
                        "remote", stringResource(R.string.remote), stringResource(R.string.remote_value),
                        PlasmaIcons.Remote, stringResource(R.string.remote_text), SettingsPage.Remote,
                    ),
                    page(
                        "appearance", stringResource(R.string.appearance),
                        stringResource(R.string.appearance_value, stringResource(if (clock.selected == 0) R.string.clock_24 else R.string.clock_12)),
                        PlasmaIcons.Contrast, stringResource(R.string.appearance_text), SettingsPage.Appearance,
                    ),
                    page(
                        "network", stringResource(R.string.network), stringResource(R.string.network_value, facts.connection, facts.speed),
                        PlasmaIcons.Wifi, stringResource(R.string.network_text), SettingsPage.Network,
                    ),
                    page(
                        "about", stringResource(R.string.about), stringResource(R.string.about_value, facts.version),
                        PlasmaIcons.Info, stringResource(R.string.about_text), SettingsPage.About,
                    ),
                ),
            ),
        ),
    )
}

// ---- Portals & Playlists (board 20) ---------------------------------------------------------------


/** "Today, 18:02", "Mon 19 Oct, 04:00". */
@Composable
internal fun updatedText(time: ZonedDateTime, now: ZonedDateTime): String {
    val local = time.withZoneSameInstant(now.zone)
    return when (ChronoUnit.DAYS.between(local.toLocalDate(), now.toLocalDate())) {
        0L -> stringResource(DesignR.string.today_at, Formats.time(local))
        1L -> stringResource(DesignR.string.yesterday_at, Formats.time(local))
        else -> stringResource(DesignR.string.date_at, Formats.date(local), Formats.time(local))
    }
}

@Composable
private fun sourceDetail(source: SourceInfo, now: ZonedDateTime): SettingDetail {
    val rows = buildList {
        add(stringResource(if (source.type == SourceType.M3U) R.string.detail_playlist else R.string.detail_server) to source.server)
        source.mac?.let { add(stringResource(R.string.detail_mac) to it) }
        source.username?.let { add(stringResource(R.string.detail_username) to it) }
        source.guideUrl?.let { add(stringResource(R.string.detail_guide) to it) }
        source.activeUntil?.let { add(stringResource(R.string.detail_subscription) to stringResource(R.string.active_until, Formats.fullDate(it))) }
        if (source.connectionsInUse != null && source.connectionLimit != null) {
            add(stringResource(R.string.detail_connections) to stringResource(R.string.connections_in_use, source.connectionsInUse, source.connectionLimit))
        }
        add(stringResource(R.string.detail_updated) to updatedText(source.updated, now))
        if (source.movies != null && source.series != null && source.catchUpDays != null) {
            add(
                stringResource(R.string.detail_library) to stringResource(
                    R.string.library_summary,
                    Formats.count(source.channels),
                    Formats.count(source.movies),
                    Formats.count(source.series),
                    source.catchUpDays,
                ),
            )
        } else {
            add(stringResource(R.string.detail_library) to pluralStringResource(R.plurals.channel_count, source.channels, Formats.count(source.channels)))
        }
    }
    val mac = stringResource(R.string.detail_mac)
    return SettingDetail(
        icon = PlasmaIcons.ServerLines,
        title = source.name,
        subline = stringResource(
            when (source.type) {
                SourceType.Stalker -> R.string.type_line_stalker
                SourceType.Xtream -> R.string.type_line_xtream
                SourceType.M3U -> R.string.type_line_m3u
            },
        ),
        status = source.health.status(withPing = true),
        extra = { DetailGrid(rows, monospaceDigits = setOf(mac)) },
    )
}

@Composable
internal fun sourcesPage(
    values: SettingsValues,
    facts: SettingsFacts,
    now: ZonedDateTime,
    onSource: (String, SourceAction) -> Unit,
    footer: @Composable () -> Unit,
): PageSpec {
    val refresh = values.choice(KEY_REFRESH, R.array.refresh_options, 1)
    val merge = values.switch(KEY_MERGE, true)
    val logos = values.choice(KEY_LOGOS, R.array.logo_options, 0)
    val failOver = values.switch(KEY_FAIL_OVER, true)
    val remember = values.switch(KEY_REMEMBER_WORKS, true)
    val refreshNow = stringResource(R.string.refresh_now)
    val delete = stringResource(R.string.delete_source)
    val confirm = stringResource(R.string.delete)
    val cancel = stringResource(R.string.cancel)
    val deleteText = stringResource(R.string.delete_source_text)
    val sources = facts.sources.map { source ->
        SettingItem(
            id = "source-${source.id}",
            action = SettingAction.Menu(
                title = source.name,
                options = listOf(
                    MenuOption(refreshNow) { onSource(source.id, SourceAction.RefreshNow) },
                    MenuOption(
                        delete,
                        next = SettingAction.Menu(
                            title = stringResource(R.string.delete_source_title, source.name),
                            subtitle = deleteText,
                            options = listOf(MenuOption(cancel), MenuOption(confirm) { onSource(source.id, SourceAction.Delete) }),
                        ),
                    ),
                ),
            ),
            label = source.name,
            value = source.health.status(withPing = false).text,
            subtitle = when (source.type) {
                SourceType.Stalker -> "${stringResource(R.string.type_stalker)} · ${source.host}"
                SourceType.Xtream -> "${stringResource(R.string.type_xtream)} · ${source.host}"
                SourceType.M3U -> "${stringResource(R.string.type_m3u)} · " +
                    pluralStringResource(R.plurals.channel_count, source.channels, Formats.count(source.channels))
            },
            status = when (source.health) {
                is SourceHealth.Connected -> Tone.Ok
                SourceHealth.Standby -> Tone.Idle
                is SourceHealth.GuideOld, SourceHealth.RefreshFailed -> Tone.Warn
            },
            labelPx = 26f,
            detail = sourceDetail(source, now),
        )
    }
    val add = stringResource(R.string.add_source)
    val guideFeeds = pluralStringResource(R.plurals.xmltv_feeds_title, facts.xmltvFeeds, facts.xmltvFeeds)
    return PageSpec(
        title = stringResource(R.string.sources),
        back = stringResource(DesignR.string.settings),
        style = PageStyle.Large,
        metrics = RowMetrics.Sources,
        detailTop = 178.px,
        footer = footer,
        listBottom = 976.px,
        sections = listOf(
            SettingSection(
                label = stringResource(R.string.section_sources),
                items = sources + SettingItem(
                    id = "add",
                    label = add,
                    value = null,
                    chevron = false,
                    leadingIcon = PlasmaIcons.Plus,
                    action = SettingAction.Go(SettingsDestination.AddSource),
                    detail = SettingDetail(PlasmaIcons.ServerLines, add, text = stringResource(R.string.add_source_text)),
                ),
            ),
            SettingSection(
                label = stringResource(R.string.section_options),
                items = listOf(
                    choiceRow("refresh", stringResource(R.string.refresh_sources), refresh, PlasmaIcons.ServerLines, stringResource(R.string.refresh_sources_text)),
                    infoRow(
                        "feeds", stringResource(R.string.guide_sources), guideFeeds, PlasmaIcons.GuideTable,
                        stringResource(R.string.guide_sources_text), SettingAction.Open(SettingsPage.Guide),
                    ),
                    switchRow("merge", stringResource(R.string.merge_duplicates), merge, PlasmaIcons.Hash, stringResource(R.string.merge_duplicates_text)),
                    choiceRow("logos", stringResource(R.string.channel_logos), logos, PlasmaIcons.ServerLines, stringResource(R.string.channel_logos_text)),
                    switchRow("failover", stringResource(R.string.fail_over), failOver, PlasmaIcons.ServerLines, stringResource(R.string.fail_over_text)),
                    switchRow("remember", stringResource(R.string.remember_works), remember, PlasmaIcons.ServerLines, stringResource(R.string.remember_works_text)),
                ),
            ),
        ),
    )
}

// ---- Launcher & Home (board 22) -------------------------------------------------------------------

@Composable
internal fun launcherPage(values: SettingsValues): PageSpec {
    val homeButton = values.choice(KEY_HOME_BUTTON, R.array.home_button_options, 0)
    val longPress = values.choice(KEY_LONG_PRESS_HOME, R.array.long_press_home_options, 0)
    val turnsOn = values.choice(KEY_TV_ON, R.array.tv_turns_on_options, 0)
    val turnsOff = values.choice(KEY_TV_OFF, R.array.tv_turns_off_options, 0)
    val smallWindow = values.choice(KEY_SMALL_WINDOW, R.array.small_window_options, 0)
    val preview = values.choice(KEY_LIVE_PREVIEW, R.array.live_preview_options, 0)
    val androidApps = values.switch(KEY_ANDROID_APPS, true)
    val screensaver = values.choice(KEY_SCREENSAVER, R.array.screensaver_options, 1)
    val clock = values.choice(KEY_CLOCK, R.array.clock_options, 0)
    return PageSpec(
        title = stringResource(R.string.launcher),
        back = stringResource(DesignR.string.settings),
        style = PageStyle.Relaxed,
        metrics = RowMetrics.Roomy,
        detailTop = 196.px,
        // Two rows sit after When the TV Turns On (brief 4.22), so Screensaver ends the first screen.
        listBottom = 1040.px,
        sections = listOf(
            SettingSection(
                label = null,
                items = listOf(
                    infoRow(
                        "use-as-home", stringResource(R.string.use_as_home), stringResource(R.string.use_as_home_value), PlasmaIcons.House,
                        stringResource(R.string.use_as_home_text), SettingAction.Open(SettingsPage.HomeScreen),
                    ),
                    choiceRow("home-button", stringResource(R.string.home_button), homeButton, PlasmaIcons.Home, stringResource(R.string.home_button_text)),
                    choiceRow("long-press", stringResource(R.string.long_press_home), longPress, PlasmaIcons.Home, stringResource(R.string.long_press_home_text)),
                    choiceRow("tv-on", stringResource(R.string.tv_turns_on), turnsOn, PlasmaIcons.Power, stringResource(R.string.tv_turns_on_text)),
                    choiceRow("tv-off", stringResource(R.string.tv_turns_off), turnsOff, PlasmaIcons.Power, stringResource(R.string.tv_turns_off_text)),
                    choiceRow("small-window", stringResource(R.string.small_window), smallWindow, PlasmaIcons.PictureInPicture, stringResource(R.string.small_window_text)),
                    choiceRow("preview", stringResource(R.string.live_preview), preview, PlasmaIcons.LiveTv, stringResource(R.string.live_preview_text)),
                    infoRow(
                        "home-rows", stringResource(R.string.home_rows), stringResource(R.string.home_rows_value), PlasmaIcons.House,
                        stringResource(R.string.home_rows_text), SettingAction.Open(SettingsPage.ContinueWatching),
                    ),
                    switchRow("android-apps", stringResource(R.string.android_apps), androidApps, PlasmaIcons.AllApps, stringResource(R.string.android_apps_text)),
                    choiceRow("screensaver", stringResource(R.string.screensaver), screensaver, PlasmaIcons.Moon, stringResource(R.string.screensaver_text)),
                    choiceRow("clock", stringResource(R.string.clock), clock, PlasmaIcons.History, stringResource(R.string.clock_text)),
                ),
            ),
        ),
    )
}

// ---- Playback (board 27) ----------------------------------------------------------------------------

@Composable
internal fun playbackPage(values: SettingsValues, facts: SettingsFacts): PageSpec {
    val engine = values.choice(KEY_ENGINE, R.array.engine_options, 0)
    val ifFails = values.choice(KEY_IF_FAILS, R.array.if_fails_options, 0)
    val hdr = values.choice(KEY_HDR, R.array.hdr_options, 0)
    val decoding = values.choice(KEY_DECODING, R.array.decoding_options, 0)
    val deinterlacing = values.choice(KEY_DEINTERLACING, R.array.deinterlacing_options, 0)
    val frameRate = values.choice(KEY_FRAME_RATE, R.array.frame_rate_options, 1)
    val subtitles = values.choice(KEY_SUBTITLES, R.array.subtitles_options, 0)
    val buffer = values.choice(KEY_BUFFER, R.array.buffer_options, 2, note = stringResource(R.string.buffer_note))
    val rewind = values.choice(KEY_REWIND, R.array.rewind_options, 4)
    val output = values.choice(KEY_AUDIO_OUTPUT, R.array.audio_output_options, 1)
    val fromStart = values.switch(KEY_FROM_START, true)
    val timeout = values.choice(KEY_TIMEOUT, R.array.timeout_options, 2)
    val fastZap = values.switch(KEY_FAST_ZAP, false)
    val upDown = values.choice(KEY_UP_DOWN, R.array.up_down_options, 0)
    val nextEpisode = values.switch(KEY_NEXT_EPISODE, true)
    val tunneling = values.switch(KEY_TUNNELING, false)
    val hub = values.switch(KEY_STREAM_HUB, true)
    val stats = values.switch(KEY_STREAM_STATS, false)
    val logs = values.switch(KEY_PLAYER_LOGS, false)

    val engineLabel = stringResource(R.string.player_engine)
    val engineText = stringResource(
        when (engine.selected) {
            1 -> R.string.player_engine_text_mpv
            2 -> R.string.player_engine_text_media3
            else -> R.string.player_engine_text_vlc
        },
    )
    val engines = stringArrayResource(R.array.engine_options)
    val cards = listOf(
        Triple(engines[0], stringResource(R.string.engine_vlc_line), Status(stringResource(R.string.engine_default), Tone.Ok)),
        Triple(engines[1], stringResource(R.string.engine_mpv_line), Status(stringResource(R.string.engine_backup), Tone.Idle)),
        Triple(engines[2], stringResource(R.string.engine_media3_line), Status(stringResource(R.string.engine_hdr), Tone.Idle)),
    )
    val engineNote = stringResource(R.string.engine_note)
    val formats = facts.supportedFormats.size - 1 // Atmos travels inside Dolby Digital Plus.
    val localCount = facts.localChannels.size
    val rewindValue = if (rewind.selected == 0) {
        stringResource(R.string.rewind_value_off, pluralStringResource(R.plurals.local_channels, localCount, localCount))
    } else {
        stringResource(R.string.rewind_value, stringResource(R.string.n_hour, rewindHours(rewind.selected)), pluralStringResource(R.plurals.local_channels, localCount, localCount))
    }

    return PageSpec(
        title = stringResource(R.string.playback),
        back = stringResource(DesignR.string.settings),
        style = PageStyle.Compact,
        metrics = RowMetrics.Page,
        detailTop = 160.px,
        sections = listOf(
            SettingSection(
                label = stringResource(R.string.section_player),
                items = listOf(
                    SettingItem(
                        id = "engine",
                        label = engineLabel,
                        value = engine.value,
                        action = engine.action,
                        detail = SettingDetail(
                            PlasmaIcons.Screen,
                            engineLabel,
                            text = engineText,
                            extra = {
                                EngineCards(cards)
                                DetailNote(engineNote)
                            },
                        ),
                    ),
                    choiceRow("if-fails", stringResource(R.string.if_fails), ifFails, PlasmaIcons.SwitchPlayer, stringResource(R.string.if_fails_text)),
                    choiceRow("hdr", stringResource(R.string.hdr_movies), hdr, PlasmaIcons.Screen, stringResource(R.string.hdr_movies_text)),
                ),
            ),
            SettingSection(
                label = stringResource(R.string.section_picture),
                items = listOf(
                    choiceRow("decoding", stringResource(R.string.decoding), decoding, PlasmaIcons.Screen, stringResource(R.string.decoding_text)),
                    choiceRow("deinterlacing", stringResource(R.string.deinterlacing), deinterlacing, PlasmaIcons.Screen, stringResource(R.string.deinterlacing_text)),
                    choiceRow("frame-rate", stringResource(R.string.frame_rate), frameRate, PlasmaIcons.Screen, stringResource(R.string.frame_rate_text)),
                ),
            ),
            SettingSection(
                label = stringResource(R.string.section_sound),
                items = listOf(
                    infoRow(
                        "audio", stringResource(R.string.audio),
                        stringResource(R.string.audio_value, output.value, pluralStringResource(R.plurals.format_count, formats, formats)),
                        PlasmaIcons.Speaker, stringResource(R.string.audio_text), SettingAction.Open(SettingsPage.Audio),
                    ),
                    choiceRow("subtitles", stringResource(R.string.subtitles), subtitles, PlasmaIcons.Subtitles, stringResource(R.string.subtitles_text)),
                    choiceRow("buffer", stringResource(R.string.buffer), buffer, PlasmaIcons.History, stringResource(R.string.buffer_text)),
                    infoRow(
                        "rewind", stringResource(R.string.rewind), rewindValue, PlasmaIcons.History,
                        stringResource(R.string.rewind_text), SettingAction.Open(SettingsPage.Rewind),
                    ),
                ),
            ),
            SettingSection(
                label = stringResource(R.string.section_more),
                items = listOf(
                    switchRow("from-start", stringResource(R.string.watch_from_start), fromStart, PlasmaIcons.StartOver, stringResource(R.string.watch_from_start_text)),
                    choiceRow("timeout", stringResource(R.string.timeout), timeout, PlasmaIcons.History, stringResource(R.string.timeout_text)),
                    switchRow("fast-zap", stringResource(R.string.fast_zap), fastZap, PlasmaIcons.ChangeChannel, stringResource(R.string.fast_zap_text)),
                    choiceRow("up-down", stringResource(R.string.up_down), upDown, PlasmaIcons.ChannelList, stringResource(R.string.up_down_text)),
                    switchRow("next-episode", stringResource(R.string.next_episode), nextEpisode, PlasmaIcons.SkipForward, stringResource(R.string.next_episode_text)),
                ),
            ),
            SettingSection(
                label = stringResource(R.string.section_advanced),
                items = listOf(
                    switchRow("tunneling", stringResource(R.string.tunneling), tunneling, PlasmaIcons.Screen, stringResource(R.string.tunneling_text)),
                    switchRow("hub", stringResource(R.string.stream_hub), hub, PlasmaIcons.Server, stringResource(R.string.stream_hub_text)),
                    switchRow("stats", stringResource(R.string.stream_stats), stats, PlasmaIcons.Info, stringResource(R.string.stream_stats_text)),
                    infoRow("forget", stringResource(R.string.forget_players), null, PlasmaIcons.SwitchPlayer, stringResource(R.string.forget_players_text)),
                    switchRow("logs", stringResource(R.string.player_logs), logs, PlasmaIcons.Info, stringResource(R.string.player_logs_text)),
                ),
            ),
        ),
    )
}

/** The buffer's length in hours for each Rewind Buffer option. */
internal fun rewindHours(option: Int): Int = when (option) {
    1, 3 -> 1
    5 -> 4
    else -> 2
}

// ---- Audio (board 28) -------------------------------------------------------------------------------

@Composable
internal fun audioPage(values: SettingsValues, facts: SettingsFacts): PageSpec {
    val output = values.choice(KEY_AUDIO_OUTPUT, R.array.audio_output_options, 1)
    val encode = values.switch(KEY_ENCODE_DD, false)
    val downmix = values.switch(KEY_DOWNMIX, false)
    val night = values.switch(KEY_NIGHT_MODE, false)
    val delay = values.choice(KEY_AUDIO_DELAY, R.array.audio_delay_options, 2)
    val language = values.choice(KEY_LANGUAGE, R.array.language_options, 0)
    val skip = values.switch(KEY_SKIP_DESCRIPTION, true)

    val outputLabel = stringResource(R.string.audio_output)
    val outputLine = stringResource(
        when (output.selected) {
            0 -> R.string.output_auto_line
            2 -> R.string.output_pcm_line
            else -> R.string.output_passthrough_line
        },
    )
    val connected = stringResource(R.string.connected)
    val supports = stringResource(R.string.system_supports)
    val note = stringResource(R.string.audio_note)
    // "Dolby Digital, DD+, DTS": Atmos rides inside Dolby Digital Plus, so it isn't a separate tick.
    val shortNames = facts.supportedFormats.filterNot { it == "Dolby Atmos" }.map { if (it == "Dolby Digital Plus") "DD+" else it }

    return PageSpec(
        title = stringResource(R.string.audio),
        back = stringResource(R.string.playback),
        style = PageStyle.Compact,
        metrics = RowMetrics.Page,
        detailTop = 160.px,
        sections = listOf(
            SettingSection(
                label = stringResource(R.string.section_output),
                items = listOf(
                    SettingItem(
                        id = "output",
                        label = outputLabel,
                        value = output.value,
                        action = output.action,
                        detail = SettingDetail(
                            PlasmaIcons.Speaker,
                            output.value,
                            subline = outputLine,
                            extra = {
                                AudioCard(connected, facts.audioConnection, supports, facts.supportedFormats, facts.unsupportedFormats)
                                DetailNote(note)
                            },
                        ),
                    ),
                    infoRow(
                        "formats", stringResource(R.string.passthrough_formats), shortNames.joinToString(", "),
                        PlasmaIcons.Speaker, stringResource(R.string.passthrough_formats_text),
                    ),
                    switchRow(
                        "encode", stringResource(R.string.encode_surround), encode, PlasmaIcons.Speaker, stringResource(R.string.encode_surround_text),
                        subtitle = stringResource(R.string.encode_surround_subtitle),
                    ).withChevron(),
                    switchRow("downmix", stringResource(R.string.downmix), downmix, PlasmaIcons.Speaker, stringResource(R.string.downmix_text)).withChevron(),
                ),
            ),
            SettingSection(
                label = stringResource(R.string.section_sound_only),
                items = listOf(
                    switchRow(
                        "night", stringResource(R.string.night_mode), night, PlasmaIcons.Moon, stringResource(R.string.night_mode_text),
                        subtitle = stringResource(R.string.night_mode_subtitle),
                    ).withChevron(),
                    choiceRow(
                        "delay", stringResource(R.string.audio_delay), delay, PlasmaIcons.History, stringResource(R.string.audio_delay_text),
                        subtitle = stringResource(R.string.audio_delay_subtitle),
                    ),
                    infoRow(
                        "levels", stringResource(R.string.volume_levels), pluralStringResource(R.plurals.local_channels, 3, 3),
                        PlasmaIcons.Volume, stringResource(R.string.volume_levels_text), subtitle = stringResource(R.string.volume_levels_subtitle),
                    ),
                ),
            ),
            SettingSection(
                label = stringResource(R.string.section_languages),
                items = listOf(
                    choiceRow("language", stringResource(R.string.preferred_language), language, PlasmaIcons.Subtitles, stringResource(R.string.preferred_language_text)),
                    switchRow("skip", stringResource(R.string.skip_description), skip, PlasmaIcons.Subtitles, stringResource(R.string.skip_description_text)).withChevron(),
                ),
            ),
        ),
    )
}

/** Board 28 draws a chevron on every row, switches included. */
private fun SettingItem.withChevron() = SettingItem(
    id, label, value, detail, action, subtitle, icon, leadingIcon, status, chevron = true, dimmed = dimmed, labelPx = labelPx,
)

// ---- Channels (board 29) ----------------------------------------------------------------------------

@Composable
internal fun channelsPage(values: SettingsValues, facts: SettingsFacts): PageSpec {
    val numbers = values.choice(KEY_CHANNEL_NUMBERS, R.array.channel_numbers_options, 0)
    val favorites = values.choice(KEY_FAVORITE_NUMBERS, R.array.numbering_options, 0)
    val groups = values.choice(KEY_GROUP_NUMBERS, R.array.numbering_options, 1)
    val keys = values.choice(KEY_NUMBER_KEYS, R.array.number_keys_options, 0)
    val merge = values.switch(KEY_MERGE, true)
    val quality = values.choice(KEY_QUALITY, R.array.quality_options, 0)

    val numbering = stringArrayResource(R.array.numbering_options)
    val favoritesLabel = stringResource(R.string.favorite_numbers)
    val favoritesText = stringResource(R.string.favorite_numbers_text)
    val note = stringResource(R.string.favorite_numbers_note, facts.favoriteExamples[1].second, facts.favoriteExamples[0].first.toString())
    val groupsLabel = stringResource(R.string.group_numbers)
    val groupsText = stringResource(R.string.group_numbers_text)

    return PageSpec(
        title = stringResource(R.string.channels),
        back = stringResource(DesignR.string.settings),
        style = PageStyle.Compact,
        metrics = RowMetrics.Page,
        detailTop = 160.px,
        sections = listOf(
            SettingSection(
                label = stringResource(R.string.section_numbers),
                items = listOf(
                    choiceRow("numbers", stringResource(R.string.channel_numbers), numbers, PlasmaIcons.Hash, stringResource(R.string.channel_numbers_text)),
                    infoRow(
                        "duplicates", stringResource(R.string.duplicate_numbers), stringResource(R.string.duplicate_numbers_value, facts.duplicatesFixed),
                        PlasmaIcons.Hash, stringResource(R.string.duplicate_numbers_text), SettingAction.Go(SettingsDestination.DuplicateNumbers),
                        subtitle = pluralStringResource(R.plurals.duplicate_numbers_subtitle, facts.duplicatesFixed, facts.duplicatesFixed),
                    ),
                    SettingItem(
                        id = "favorites",
                        label = favoritesLabel,
                        value = favorites.value,
                        action = favorites.action,
                        detail = SettingDetail(
                            PlasmaIcons.Hash,
                            favoritesLabel,
                            text = favoritesText,
                            extra = {
                                NumberingExamples(facts.favoriteExamples, numbering[1], numbering[0], countFromOne = favorites.selected == 0)
                                DetailNote(note, top = 20.px)
                            },
                        ),
                    ),
                    SettingItem(
                        id = "groups",
                        label = groupsLabel,
                        value = groups.value,
                        action = groups.action,
                        detail = SettingDetail(
                            PlasmaIcons.Hash,
                            groupsLabel,
                            text = groupsText,
                            extra = { NumberingExamples(facts.favoriteExamples, numbering[1], numbering[0], countFromOne = groups.selected == 0) },
                        ),
                    ),
                    choiceRow(
                        "keys", stringResource(R.string.number_keys), keys, PlasmaIcons.Hash, stringResource(R.string.number_keys_text),
                        subtitle = stringResource(R.string.number_keys_subtitle),
                    ),
                ),
            ),
            SettingSection(
                label = stringResource(R.string.section_duplicates),
                items = listOf(
                    SettingItem(
                        id = "merge",
                        label = stringResource(R.string.merge_duplicates),
                        value = if (merge.on) stringResource(R.string.merge_value, facts.mergedChannels) else merge.value,
                        action = merge.action,
                        chevron = true,
                        detail = SettingDetail(PlasmaIcons.Hash, stringResource(R.string.merge_duplicates), text = stringResource(R.string.merge_duplicates_text)),
                    ),
                    choiceRow("quality", stringResource(R.string.preferred_quality), quality, PlasmaIcons.Screen, stringResource(R.string.preferred_quality_text)),
                ),
            ),
            SettingSection(
                label = stringResource(R.string.section_hidden),
                items = listOf(
                    infoRow("hidden-channels", stringResource(R.string.hidden_channels), facts.hiddenChannels.toString(), PlasmaIcons.Hash, stringResource(R.string.hidden_channels_text)),
                    infoRow("hidden-groups", stringResource(R.string.hidden_groups), facts.hiddenGroups.toString(), PlasmaIcons.Hash, stringResource(R.string.hidden_groups_text)),
                ),
            ),
        ),
    )
}

// ---- Rewind and Local Catch-up (board 31) -----------------------------------------------------------

@Composable
internal fun rewindPage(values: SettingsValues, facts: SettingsFacts): PageSpec {
    val rewind = values.choice(KEY_REWIND, R.array.rewind_options, 4, note = stringResource(R.string.rewind_sheet_note))
    val keep = values.choice(KEY_KEEP, R.array.keep_options, 2)
    val tvOff = values.choice(KEY_TV_OFF_RECORDING, R.array.tv_off_options, 0)
    val saveTo = values.choice(KEY_SAVE_TO, R.array.save_to_options, 0)
    val limit = values.choice(KEY_STORAGE_LIMIT, R.array.storage_limit_options, 2)

    val bufferLabel = stringResource(R.string.rewind_buffer)
    val length = rewindHours(rewind.selected)
    val hours = pluralStringResource(R.plurals.hour_count, length, length)
    val bufferText = if (rewind.selected == 0) stringResource(R.string.rewind_buffer_text_off) else stringResource(R.string.rewind_buffer_text, hours)
    val total = facts.driveTotal.toFloat()
    val parts = listOf(
        stringResource(R.string.storage_recordings, Formats.size(facts.recordingsBytes)) to facts.recordingsBytes / total,
        stringResource(R.string.storage_local, Formats.size(facts.localCatchUpBytes)) to facts.localCatchUpBytes / total,
        stringResource(R.string.storage_rewind, Formats.size(facts.rewindBytes)) to facts.rewindBytes / total,
    )
    val home = facts.sources.first { it.name == facts.primarySource }
    val warning = facts.localChannels.firstOrNull()?.let {
        stringResource(R.string.local_connection, it, facts.localChannels.size, home.connectionLimit ?: 1, home.name)
    }
    val free = stringResource(R.string.storage_free, Formats.size(facts.driveFree), Formats.size(facts.driveTotal))
    val note = stringResource(R.string.rewind_note)

    return PageSpec(
        title = stringResource(R.string.rewind),
        back = stringResource(R.string.playback),
        style = PageStyle.Compact,
        metrics = RowMetrics.Page,
        detailTop = 160.px,
        sections = listOf(
            SettingSection(
                label = stringResource(R.string.section_watching),
                items = listOf(
                    SettingItem(
                        id = "buffer",
                        label = bufferLabel,
                        value = rewind.value,
                        action = rewind.action,
                        detail = SettingDetail(
                            PlasmaIcons.History,
                            bufferLabel,
                            text = bufferText,
                            extra = {
                                StorageBreakdown(facts.driveName, free, parts, warning)
                                DetailNote(note)
                            },
                        ),
                    ),
                ),
            ),
            SettingSection(
                label = stringResource(R.string.section_local),
                items = listOf(
                    infoRow(
                        "local-channels", stringResource(R.string.local_channels_row), facts.localChannels.joinToString(", "),
                        PlasmaIcons.History, stringResource(R.string.local_channels_text), subtitle = stringResource(R.string.local_channels_subtitle),
                    ),
                    choiceRow("keep", stringResource(R.string.keep), keep, PlasmaIcons.History, stringResource(R.string.keep_text)),
                    choiceRow(
                        "tv-off", stringResource(R.string.tv_off), tvOff, PlasmaIcons.Power, stringResource(R.string.tv_off_text),
                        subtitle = stringResource(R.string.tv_off_subtitle),
                    ),
                ),
            ),
            SettingSection(
                label = stringResource(R.string.section_storage),
                items = listOf(
                    choiceRow("save-to", stringResource(R.string.save_to), saveTo, PlasmaIcons.Drive, stringResource(R.string.save_to_text)),
                    choiceRow(
                        "limit", stringResource(R.string.storage_limit), limit, PlasmaIcons.Drive, stringResource(R.string.storage_limit_text),
                        subtitle = stringResource(R.string.storage_limit_subtitle),
                    ),
                    infoRow("clear", stringResource(R.string.clear_local), Formats.size(facts.localCatchUpBytes), PlasmaIcons.Drive, stringResource(R.string.clear_local_text)),
                ),
            ),
        ),
    )
}

// ---- Continue Watching (board 32) -------------------------------------------------------------------

@Composable
internal fun continueWatchingPage(values: SettingsValues, facts: SettingsFacts): PageSpec {
    val include = values.switch(KEY_INCLUDE_APPS, true)
    val playIn = values.choice(KEY_PLAY_JELLYFIN_IN, R.array.play_in_options, 0)
    val nextEpisodes = values.switch(KEY_NEXT_EPISODES, true)
    val hideOlder = values.choice(KEY_HIDE_OLDER, R.array.hide_older_options, 2)
    val kids = values.choice(KEY_KIDS_SEE, R.array.kids_see_options, 0)

    val otherApps = stringResource(R.string.other_apps)
    val appCount = facts.playNextApps.size
    val rows = listOf(
        Triple(
            stringResource(R.string.from_play_next),
            Status(stringResource(if (include.on) R.string.allowed else R.string.not_allowed), if (include.on) Tone.Ok else Tone.Idle),
            stringResource(
                R.string.apps_items,
                pluralStringResource(R.plurals.app_count, appCount, appCount),
                pluralStringResource(R.plurals.item_count, facts.playNextItems, facts.playNextItems),
            ),
        ),
        Triple(
            stringResource(R.string.from_jellyfin),
            Status(stringResource(R.string.server_user, facts.jellyfinServer, facts.jellyfinUser), Tone.Ok),
            pluralStringResource(R.plurals.item_count, facts.jellyfinItems, facts.jellyfinItems),
        ),
    )
    val note = stringResource(R.string.continue_note)

    return PageSpec(
        title = stringResource(R.string.continue_watching),
        back = stringResource(R.string.launcher),
        style = PageStyle.Compact,
        metrics = RowMetrics.Page,
        detailTop = 160.px,
        sections = listOf(
            SettingSection(
                label = stringResource(R.string.section_other_apps),
                items = listOf(
                    SettingItem(
                        id = "include",
                        label = stringResource(R.string.include_apps),
                        value = include.value,
                        action = include.action,
                        chevron = true,
                        detail = SettingDetail(
                            PlasmaIcons.Stacked,
                            otherApps,
                            text = stringResource(R.string.include_apps_text),
                            extra = {
                                ContinueSourcesCard(rows)
                                DetailNote(note)
                            },
                        ),
                    ),
                    infoRow(
                        "apps", stringResource(R.string.apps), pluralStringResource(R.plurals.app_count, appCount, appCount),
                        PlasmaIcons.Stacked, stringResource(R.string.apps_text),
                        subtitle = if (appCount == 2) stringResource(R.string.apps_and, facts.playNextApps[0], facts.playNextApps[1]) else facts.playNextApps.joinToString(", "),
                    ),
                    infoRow(
                        "jellyfin", stringResource(R.string.jellyfin_server), stringResource(R.string.connected), PlasmaIcons.Server,
                        stringResource(R.string.jellyfin_text), subtitle = stringResource(R.string.jellyfin_subtitle, facts.jellyfinServer, facts.jellyfinUser),
                    ),
                    choiceRow("play-in", stringResource(R.string.play_jellyfin_in), playIn, PlasmaIcons.Play, stringResource(R.string.play_jellyfin_in_text)),
                ),
            ),
            SettingSection(
                label = stringResource(R.string.section_row),
                items = listOf(
                    switchRow("next", stringResource(R.string.next_episodes), nextEpisodes, PlasmaIcons.Series, stringResource(R.string.next_episodes_text)).withChevron(),
                    choiceRow("older", stringResource(R.string.hide_older), hideOlder, PlasmaIcons.History, stringResource(R.string.hide_older_text)),
                    choiceRow("kids", stringResource(R.string.kids_see), kids, PlasmaIcons.People, stringResource(R.string.kids_see_text)),
                ),
            ),
        ),
    )
}

// ---- Recording (board 34) ---------------------------------------------------------------------------

@Composable
internal fun recordingPage(values: SettingsValues, facts: SettingsFacts): PageSpec {
    val recordTo = values.choice(KEY_RECORD_TO, R.array.record_to_options, 0)
    val afterCopying = values.choice(KEY_AFTER_COPYING, R.array.after_copying_options, 0)
    val names = values.switch(KEY_JELLYFIN_NAMES, true)
    val early = values.choice(KEY_START_EARLY, R.array.start_early_options, 1)
    val late = values.choice(KEY_END_LATE, R.array.end_late_options, 1)
    val sports = values.choice(KEY_SPORTS_EXTRA, R.array.sports_extra_options, 1)
    val deleteWatched = values.choice(KEY_DELETE_WATCHED, R.array.delete_watched_options, 2)
    val spaceLow = values.choice(KEY_SPACE_LOW, R.array.space_low_options, 0)

    val folderLabel = stringResource(R.string.network_folder)
    val free = stringResource(R.string.free, Formats.size(facts.networkFolderFree))
    val note = stringResource(R.string.network_folder_note, facts.jellyfinServer)
    val layout = buildAnnotatedString {
        fun folder(text: String) = withStyle(SpanStyle(color = PlasmaColors.Text)) { append(text) }
        fun extra(text: String) = withStyle(SpanStyle(color = PlasmaColors.Text4)) { append(text) }
        folder("TV Recordings/")
        append("\n  Kingdoms of Ice/Season 01/\n    Kingdoms of Ice S01E03.ts  ")
        extra("+ .nfo, -thumb.jpg")
        append("\n  Late Night Sessions/Season 2026/\n    Late Night Sessions S2026E001.ts\n")
        folder("Movie Recordings/")
        append("\n  The Last Lighthouse (2025)/\n    The Last Lighthouse (2025).ts  ")
        extra("+ movie.nfo")
        append("\n")
        folder("Other Recordings/")
        append("Arena Sports 1/\n  2026-10-21 1945 Harbour City v Redmoor.ts")
    }

    return PageSpec(
        title = stringResource(R.string.recording),
        back = stringResource(DesignR.string.settings),
        style = PageStyle.Compact,
        metrics = RowMetrics.Page,
        detailTop = 160.px,
        sections = listOf(
            SettingSection(
                label = stringResource(R.string.section_where),
                items = listOf(
                    choiceRow("record-to", stringResource(R.string.record_to), recordTo, PlasmaIcons.Drive, stringResource(R.string.record_to_text)),
                    SettingItem(
                        id = "copy",
                        label = stringResource(R.string.copy_to_folder),
                        value = stringResource(R.string.copy_to_folder_value, facts.networkFolder.substringBefore(" ›")),
                        detail = SettingDetail(
                            PlasmaIcons.NetworkFolder,
                            folderLabel,
                            text = stringResource(R.string.network_folder_text),
                            extra = {
                                FolderCard(facts.networkFolder, free, layout)
                                DetailNote(note, top = 16.px)
                            },
                        ),
                    ),
                    choiceRow("after-copying", stringResource(R.string.after_copying), afterCopying, PlasmaIcons.NetworkFolder, stringResource(R.string.after_copying_text)),
                    switchRow(
                        "names", stringResource(R.string.jellyfin_names), names, PlasmaIcons.NetworkFolder, stringResource(R.string.jellyfin_names_text),
                        subtitle = stringResource(R.string.jellyfin_names_subtitle),
                    ).withChevron(),
                ),
            ),
            SettingSection(
                label = stringResource(R.string.section_timing),
                items = listOf(
                    choiceRow("early", stringResource(R.string.start_early), early, PlasmaIcons.RecordRing, stringResource(R.string.start_early_text)),
                    choiceRow("late", stringResource(R.string.end_late), late, PlasmaIcons.RecordRing, stringResource(R.string.end_late_text)),
                    choiceRow("sports", stringResource(R.string.sports_extra), sports, PlasmaIcons.RecordRing, stringResource(R.string.sports_extra_text)),
                ),
            ),
            SettingSection(
                label = stringResource(R.string.section_cleanup),
                items = listOf(
                    choiceRow("delete-watched", stringResource(R.string.delete_watched), deleteWatched, PlasmaIcons.Drive, stringResource(R.string.delete_watched_text)),
                    choiceRow("space-low", stringResource(R.string.space_low), spaceLow, PlasmaIcons.Drive, stringResource(R.string.space_low_text)),
                ),
            ),
        ),
    )
}

// ---- Keys -------------------------------------------------------------------------------------------
// The settings Plasma acts on share their keys with the rest of the app (SettingKeys).

internal const val KEY_ENGINE = "engine"
internal const val KEY_IF_FAILS = "ifFails"
internal const val KEY_HDR = "hdr"
internal const val KEY_DECODING = SettingKeys.DECODING
internal const val KEY_DEINTERLACING = SettingKeys.DEINTERLACING
internal const val KEY_FRAME_RATE = SettingKeys.FRAME_RATE
internal const val KEY_SUBTITLES = "subtitles"
internal const val KEY_BUFFER = SettingKeys.BUFFER
internal const val KEY_REWIND = "rewind"
internal const val KEY_FROM_START = SettingKeys.FROM_START
internal const val KEY_TIMEOUT = "timeout"
internal const val KEY_FAST_ZAP = "fastZap"
internal const val KEY_UP_DOWN = "upDown"
internal const val KEY_NEXT_EPISODE = "nextEpisode"
internal const val KEY_TUNNELING = "tunneling"
internal const val KEY_STREAM_HUB = SettingKeys.STREAM_HUB
internal const val KEY_STREAM_STATS = "streamStats"
internal const val KEY_PLAYER_LOGS = "playerLogs"
internal const val KEY_AUDIO_OUTPUT = SettingKeys.AUDIO_OUTPUT
internal const val KEY_ENCODE_DD = "encodeDd"
internal const val KEY_DOWNMIX = "downmix"
internal const val KEY_NIGHT_MODE = "nightMode"
internal const val KEY_AUDIO_DELAY = "audioDelay"
internal const val KEY_LANGUAGE = "language"
internal const val KEY_SKIP_DESCRIPTION = "skipDescription"
internal const val KEY_CHANNEL_NUMBERS = SettingKeys.CHANNEL_NUMBERS
internal const val KEY_FAVORITE_NUMBERS = SettingKeys.FAVORITE_NUMBERS
internal const val KEY_GROUP_NUMBERS = SettingKeys.GROUP_NUMBERS
internal const val KEY_NUMBER_KEYS = SettingKeys.NUMBER_KEYS
internal const val KEY_MERGE = "merge"
internal const val KEY_QUALITY = "quality"
internal const val KEY_KEEP = "keep"
internal const val KEY_TV_OFF_RECORDING = "tvOffRecording"
internal const val KEY_SAVE_TO = "saveTo"
internal const val KEY_STORAGE_LIMIT = "storageLimit"
internal const val KEY_INCLUDE_APPS = "includeApps"
internal const val KEY_PLAY_JELLYFIN_IN = "playJellyfinIn"
internal const val KEY_NEXT_EPISODES = "nextEpisodes"
internal const val KEY_HIDE_OLDER = "hideOlder"
internal const val KEY_KIDS_SEE = "kidsSee"
internal const val KEY_RECORD_TO = "recordTo"
internal const val KEY_AFTER_COPYING = "afterCopying"
internal const val KEY_JELLYFIN_NAMES = "jellyfinNames"
internal const val KEY_START_EARLY = "startEarly"
internal const val KEY_END_LATE = "endLate"
internal const val KEY_SPORTS_EXTRA = "sportsExtra"
internal const val KEY_DELETE_WATCHED = "deleteWatched"
internal const val KEY_SPACE_LOW = "spaceLow"
internal const val KEY_REFRESH = "refresh"
internal const val KEY_LOGOS = "logos"
internal const val KEY_FAIL_OVER = "failOver"
internal const val KEY_REMEMBER_WORKS = "rememberWorks"
internal const val KEY_HOME_BUTTON = "homeButton"
internal const val KEY_LONG_PRESS_HOME = "longPressHome"
internal const val KEY_TV_ON = SettingKeys.TV_ON
internal const val KEY_TV_OFF = SettingKeys.TV_OFF
internal const val KEY_SMALL_WINDOW = "smallWindow"
internal const val KEY_LIVE_PREVIEW = "livePreview"
internal const val KEY_ANDROID_APPS = "androidApps"
internal const val KEY_SCREENSAVER = "screensaver"
internal const val KEY_CLOCK = "clock"
