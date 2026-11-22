# SPDX-License-Identifier: GPL-3.0-or-later
"""The stream torture set (brief 6.9, #130): each case, the capture it plays, and how it's served.

`make_captures.py` makes the captures with FFmpeg, from FFmpeg's own test pictures and tones, so
nothing comes from a provider. `serve.py` serves them, and the playlist that lists them, to Plasma.
"""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Case:
    """One thing to play in each engine and decoding mode."""

    id: str
    area: str
    name: str
    #: The capture it plays, in the captures folder (a file, or for HLS a folder with its playlist).
    capture: str
    #: How it's served: see serve.py's TRANSPORTS.
    transport: str = "live"
    #: What should happen in Plasma.
    expect: str = "Plays, with sound."
    #: For an M3U entry: #EXTVLCOPT lines Plasma passes to the engine and the hub.
    options: tuple = ()


CASES = [
    # MPEG-TS
    Case("ts-jump-back", "MPEG-TS", "PCR and PTS jump back", "ts_jump_back.ts",
         expect="Plays on through the jump, 30 s in, without freezing."),
    Case("ts-clock-jump", "MPEG-TS", "Clock jumps an hour ahead", "ts_clock_jump.ts",
         expect="Plays on through the jump, 30 s in (Freeze Watch lists it)."),
    Case("ts-pid-change", "MPEG-TS", "Programme and PIDs change", "ts_pid_change.ts",
         expect="Plays on after the change, 30 s in, picture and sound."),
    Case("ts-no-pat", "MPEG-TS", "Joined without PAT/PMT", "ts_no_pat.ts", transport="from-start",
         expect="Starts once the first PAT/PMT arrives, 3 s in."),
    Case("ts-continuity", "MPEG-TS", "Continuity errors", "ts_continuity.ts",
         expect="Plays, with the odd glitch, without stopping."),
    Case("ts-null-padding", "MPEG-TS", "Null-packet padding (CBR)", "ts_null_padding.ts"),
    Case("ts-two-programmes", "MPEG-TS", "Two programmes in one TS", "ts_two_programmes.ts",
         expect="Plays the first programme (the moving picture, 440 Hz)."),
    Case("ts-scrambled", "MPEG-TS", "Scrambled PIDs", "ts_scrambled.ts",
         expect='Says "This channel is encrypted" (brief 6.9), and doesn\'t try every engine.'),
    # Video
    Case("video-1080i50-mbaff", "Video", "H.264 1080i50 (MBAFF)", "h264_1080i50_mbaff.ts",
         expect="Plays without combing when deinterlaced."),
    Case("video-720p50", "Video", "H.264 720p50", "h264_720p50.ts"),
    Case("video-576i-mpeg2", "Video", "MPEG-2 576i, MP2", "mpeg2_576i.ts",
         expect="Plays without combing when deinterlaced."),
    Case("video-2160p50-hdr10", "Video", "HEVC 2160p50 10-bit HDR10", "hevc_2160p50_hdr10.ts",
         expect="Plays in HDR where the TV has it."),
    Case("video-2160p50-hlg", "Video", "HEVC 2160p50 10-bit HLG", "hevc_2160p50_hlg.ts",
         expect="Plays in HLG where the TV has it."),
    Case("video-av1", "Video", "AV1 1080p (VOD)", "av1_1080p.mkv", transport="file",
         expect="Plays where the TV decodes AV1; says it can't where it doesn't."),
    # Audio
    Case("audio-mp2", "Audio", "MP2 stereo", "audio_mp2.ts"),
    Case("audio-ac3-20", "Audio", "AC-3 2.0", "audio_ac3_20.ts"),
    Case("audio-ac3-51", "Audio", "AC-3 5.1", "audio_ac3_51.ts",
         expect="Passes through, or decodes, as Audio Output says."),
    Case("audio-eac3-51", "Audio", "E-AC-3 5.1", "audio_eac3_51.ts",
         expect="Passes through, or decodes, as Audio Output says."),
    Case("audio-aac", "Audio", "AAC-LC stereo", "h264_720p50.ts"),
    Case("audio-dts", "Audio", "DTS core 5.1 (VOD)", "audio_dts.mkv", transport="file",
         expect="Passes through, or decodes, as Audio Output says."),
    Case("audio-truehd", "Audio", "TrueHD 5.1 (VOD)", "audio_truehd.mkv", transport="file",
         expect="Passes through, or decodes, as Audio Output says."),
    Case("audio-stereo-to-51", "Audio", "Stereo to 5.1 at an ad break", "audio_stereo_to_51.ts",
         expect="The sound carries on through the switch, 30 s in."),
    # Subtitles
    Case("subs-srt", "Subtitles", "SRT in MKV (VOD)", "subs_srt.mkv", transport="file",
         expect="Subtitles show when turned on."),
    Case("subs-ass", "Subtitles", "ASS in MKV (VOD)", "subs_ass.mkv", transport="file",
         expect="Subtitles show when turned on, styled."),
    Case("subs-webvtt-hls", "Subtitles", "WebVTT in HLS", "hls_webvtt", transport="hls-vod",
         expect="Subtitles show when turned on."),
    # Transport
    Case("net-redirects", "Transport", "Two 302 redirects, tokenised", "h264_720p50.ts", transport="redirect"),
    Case("net-chunked", "Transport", "Chunked response", "h264_720p50.ts", transport="chunked"),
    Case("net-http10", "Transport", "HTTP/1.0 server", "h264_720p50.ts", transport="http10"),
    Case("net-hls-ts", "Transport", "HLS live, TS segments", "hls_ts", transport="hls-live"),
    Case("net-hls-fmp4", "Transport", "HLS live, fMP4 segments", "hls_fmp4", transport="hls-live"),
    Case("net-hls-discontinuity", "Transport", "HLS with discontinuity tags", "hls_ts", transport="hls-discontinuity",
         expect="Plays on through each discontinuity."),
    Case("net-hls-redundant", "Transport", "HLS with a redundant stream", "hls_ts", transport="hls-redundant",
         expect="Plays from the second stream: the first is gone (404)."),
    Case("net-user-agent", "Transport", "Only for one user agent", "h264_720p50.ts", transport="user-agent",
         options=("#EXTVLCOPT:http-user-agent=PlasmaTorture/1.0",),
         expect="Plays: the playlist gives the user agent the server wants."),
    Case("net-referrer", "Transport", "Only from one referrer", "h264_720p50.ts", transport="referrer",
         options=("#EXTVLCOPT:http-referrer=http://torture.example/",),
         expect="Plays: the playlist gives the referrer the server wants."),
    Case("net-error-page", "Transport", "An error page with status 200", "h264_720p50.ts", transport="error-page",
         expect="Says the provider sent a web page, and doesn't try every engine."),
    Case("net-slow-start", "Transport", "5 s to the first byte", "h264_720p50.ts", transport="slow",
         expect="Plays after about 5 s."),
    Case("net-drops", "Transport", "Drops every 20 s", "h264_720p50.ts", transport="drop",
         expect="The hub reconnects; the picture carries on after a moment."),
    Case("net-connection-limit", "Transport", "Too many connections (403)", "h264_720p50.ts", transport="limit",
         expect="Says the provider turned it down, without retrying."),
]

#: Cases brief 6.9 lists that FFmpeg can't make, so they come from real captures or the owner's providers.
NOT_MADE = [
    ("Video", "H.264 1080i50 PAFF", "x264 codes interlaced video as MBAFF only."),
    ("Video", "Dolby Vision profiles 5 and 8.1 (VOD)", "FFmpeg doesn't write Dolby Vision metadata."),
    ("Audio", "E-AC-3 Atmos, TrueHD Atmos, DTS-HD MA", "FFmpeg has no encoder for them."),
    ("Audio", "HE-AAC v1 and v2 in LATM", "Needs FFmpeg built with libfdk_aac."),
    ("Subtitles", "DVB bitmap, teletext page 888, CEA-608, PGS", "FFmpeg can't make them from text."),
    ("Transport", "UDP and RTP multicast, with and without udpxy", "Needs a multicast network."),
]
