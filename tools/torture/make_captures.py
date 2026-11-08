#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Makes the stream torture set's captures (brief 6.9, #130) with FFmpeg.

Every picture and sound is FFmpeg's own (testsrc2, test bars and tones), so nothing comes from a
provider, and the files stay out of the repository: they go in tools/torture/captures, which git
ignores. Each tone says which speaker it's in, by its pitch:

    left 440 Hz, right 554 Hz, centre 660 Hz, LFE 55 Hz, rear left 330 Hz, rear right 392 Hz

Needs FFmpeg 6 or later with libx264, libx265 and libsvtav1 (Homebrew's, or a static build).

    python3 tools/torture/make_captures.py            # the whole set, about 15 minutes
    python3 tools/torture/make_captures.py --only ts_scrambled.ts audio_ac3_51.ts
    python3 tools/torture/make_captures.py --seconds 6   # a quick check of every recipe
"""
from __future__ import annotations

import argparse
import json
import random
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
CAPTURES = HERE / "captures"

TONES = {"FL": 440, "FR": 554, "FC": 660, "LFE": 55, "BL": 330, "BR": 392}
STEREO = "aevalsrc=exprs='0.25*sin(2*PI*440*t)|0.25*sin(2*PI*554*t)':channel_layout=stereo:sample_rate=48000"
SURROUND = (
    "aevalsrc=exprs='" + "|".join(f"0.2*sin(2*PI*{TONES[c]}*t)" for c in ["FL", "FR", "FC", "LFE", "BL", "BR"])
    + "':channel_layout=5.1:sample_rate=48000"
)
H264 = ["-c:v", "libx264", "-preset", "veryfast", "-pix_fmt", "yuv420p"]
TS = ["-f", "mpegts"]

PACKET = 188


class Maker:
    def __init__(self, out: Path, seconds: float | None):
        self.out = out
        self.short = seconds
        self.work = Path(tempfile.mkdtemp(prefix="torture-"))
        #: The longest a recipe asked for: the capture's length, which its timestamps may not say.
        self.length = 0.0

    def seconds(self, full: float) -> float:
        value = min(full, self.short) if self.short else full
        self.length = max(self.length, value)
        return value

    def ff(self, *args: str) -> None:
        command = ["ffmpeg", "-y", "-hide_banner", "-loglevel", "error", *args]
        result = subprocess.run(command)
        if result.returncode != 0:
            sys.exit("FFmpeg failed: " + " ".join(command))

    def video(self, size: str, rate: int, seconds: float, source: str = "testsrc2") -> list:
        return ["-f", "lavfi", "-i", f"{source}=size={size}:rate={rate}:duration={seconds}"]

    def audio(self, layout: str, seconds: float) -> list:
        return ["-f", "lavfi", "-i", (STEREO if layout == "stereo" else SURROUND) + f":duration={seconds}"]

    def temp(self, name: str) -> Path:
        return self.work / name


# ---- MPEG-TS packets -------------------------------------------------------------------------


def packets(data: bytes) -> list:
    if len(data) % PACKET or data[:1] != b"\x47":
        sys.exit("Not an MPEG-TS file with 188-byte packets")
    return [bytearray(data[i:i + PACKET]) for i in range(0, len(data), PACKET)]


def pid(packet: bytes) -> int:
    return ((packet[1] & 0x1F) << 8) | packet[2]


def has_payload(packet: bytes) -> bool:
    return bool(packet[3] & 0x10)


def join_ts(first: Path, second: Path, out: Path) -> None:
    """The two files one after the other, the second's continuity counters carrying on from the first's."""
    a = packets(first.read_bytes())
    b = packets(second.read_bytes())
    last = {}
    for p in a:
        if has_payload(p):
            last[pid(p)] = p[3] & 0x0F
    for p in b:
        if has_payload(p) and pid(p) != 0x1FFF:
            n = (last.get(pid(p), 15) + 1) & 0x0F
            p[3] = (p[3] & 0xF0) | n
            last[pid(p)] = n
    out.write_bytes(b"".join(a + b))


def media_pids(ps: list) -> set:
    """The PIDs that carry pictures and sound: not PAT, PMT, SDT or padding."""
    counts = {}
    for p in ps:
        counts[pid(p)] = counts.get(pid(p), 0) + 1
    tables = {0x0000, 0x0011, 0x1FFF} | {k for k in counts if 0x1000 <= k < 0x1100}
    return {k for k, n in counts.items() if k not in tables and n > 50}


# ---- The recipes -----------------------------------------------------------------------------


def h264_720p50(m: Maker, out: Path, seconds: float = 60, audio: list | None = None, layout: str = "stereo", extra: list = ()) -> None:
    s = m.seconds(seconds)
    m.ff(*m.video("1280x720", 50, s), *m.audio(layout, s), *H264, "-b:v", "4M", "-g", "50",
         *(audio or ["-c:a", "aac", "-b:a", "128k"]), *extra, *TS, str(out))


def two_parts(m: Maker, out: Path, second: list, audio_first: list | None = None, audio_second: list | None = None,
              layout_first: str = "stereo", layout_second: str = "stereo") -> None:
    half = m.seconds(60) / 2
    a, b = m.temp("a.ts"), m.temp("b.ts")
    h264_720p50(m, a, half, audio=audio_first, layout=layout_first)
    h264_720p50(m, b, half, audio=audio_second, layout=layout_second, extra=second)
    join_ts(a, b, out)


def ts_jump_back(m, out):
    two_parts(m, out, second=[])


def ts_clock_jump(m, out):
    two_parts(m, out, second=["-output_ts_offset", "3600"])


def ts_pid_change(m, out):
    two_parts(m, out, second=["-mpegts_service_id", "2", "-mpegts_pmt_start_pid", "0x1100", "-mpegts_start_pid", "0x300"])


def ts_no_pat(m, out):
    whole = m.temp("whole.ts")
    h264_720p50(m, whole, extra=["-pat_period", "3", "-sdt_period", "3"])
    ps = packets(whole.read_bytes())
    pats = [i for i, p in enumerate(ps) if pid(p) == 0]
    # From just after the first PAT and its PMT: nothing says what's in the stream for 3 s.
    start = pats[0] + 2
    out.write_bytes(b"".join(ps[start:]))


def ts_continuity(m, out):
    whole = m.temp("whole.ts")
    h264_720p50(m, whole)
    ps = packets(whole.read_bytes())
    video = min(media_pids(ps))
    kept = [p for i, p in enumerate(ps) if not (pid(p) == video and i % 97 == 0)]
    out.write_bytes(b"".join(kept))


def ts_null_padding(m, out):
    s = m.seconds(60)
    m.ff(*m.video("1280x720", 50, s), *m.audio("stereo", s), *H264, "-b:v", "3M", "-maxrate", "3M", "-bufsize", "3M", "-g", "50",
         "-c:a", "aac", "-b:a", "128k", "-muxrate", "6M", *TS, str(out))


def ts_two_programmes(m, out):
    s = m.seconds(60)
    tone = lambda hz: ["-f", "lavfi", "-i", f"sine=frequency={hz}:sample_rate=48000:duration={s}"]
    m.ff(*m.video("1280x720", 50, s), *m.video("1280x720", 50, s, "smptehdbars"), *tone(440), *tone(880),
         "-map", "0:v", "-map", "2:a", "-map", "1:v", "-map", "3:a", *H264, "-b:v", "3M", "-g", "50", "-c:a", "aac", "-b:a", "96k",
         "-program", "program_num=1:title=One:st=0:st=1", "-program", "program_num=2:title=Two:st=2:st=3", *TS, str(out))


def ts_scrambled(m, out):
    whole = m.temp("whole.ts")
    h264_720p50(m, whole)
    ps = packets(whole.read_bytes())
    media = media_pids(ps)
    noise = random.Random(130)
    for p in ps:
        if pid(p) in media and has_payload(p):
            # Scrambled with the even key, as a conditional-access system leaves it: the flags set,
            # and the payload past the header and any adaptation field unreadable.
            p[3] = (p[3] & 0x3F) | 0x80
            start = 4 + (1 + p[4] if p[3] & 0x20 else 0)
            for i in range(start, PACKET):
                p[i] ^= noise.randrange(256)
    out.write_bytes(b"".join(ps))


def h264_1080i50_mbaff(m, out):
    s = m.seconds(60)
    m.ff(*m.video("1920x1080", 50, s), *m.audio("stereo", s), "-vf", "interlace=scan=tff", *H264, "-b:v", "8M", "-g", "25",
         "-flags", "+ilme+ildct", "-x264-params", "interlaced=1:tff=1", "-c:a", "aac", "-b:a", "128k", *TS, str(out))


def h264_720p50_file(m, out):
    h264_720p50(m, out)


def mpeg2_576i(m, out):
    s = m.seconds(60)
    m.ff(*m.video("720x576", 50, s), *m.audio("stereo", s), "-vf", "interlace=scan=tff", "-c:v", "mpeg2video", "-b:v", "4M",
         "-maxrate", "6M", "-bufsize", "1835k", "-g", "12", "-flags", "+ilme+ildct", "-top", "1", "-aspect", "16:9",
         "-c:a", "mp2", "-b:a", "192k", *TS, str(out))


def hevc_2160p50(m, out, transfer):
    s = m.seconds(30)
    params = "keyint=50:colorprim=bt2020:colormatrix=bt2020nc:repeat-headers=1:transfer=" + transfer
    if transfer == "smpte2084":
        params += ":hdr10=1:master-display=G(13250,34500)B(7500,3000)R(34000,16000)WP(15635,16450)L(10000000,50):max-cll=1000,400"
    m.ff(*m.video("3840x2160", 50, s), *m.audio("stereo", s), "-pix_fmt", "yuv420p10le", "-c:v", "libx265", "-preset", "ultrafast",
         "-b:v", "15M", "-x265-params", params, "-color_primaries", "bt2020", "-color_trc", transfer, "-colorspace", "bt2020nc",
         "-c:a", "aac", "-b:a", "128k", *TS, str(out))


def av1_1080p(m, out):
    s = m.seconds(30)
    m.ff(*m.video("1920x1080", 25, s), *m.audio("stereo", s), "-c:v", "libsvtav1", "-preset", "10", "-crf", "35", "-g", "50",
         "-c:a", "aac", "-b:a", "128k", str(out))


def audio_case(codec: list, layout: str, container: str = "ts"):
    def make(m, out):
        s = m.seconds(60)
        m.ff(*m.video("1280x720", 50, s), *m.audio(layout, s), *H264, "-b:v", "4M", "-g", "50", *codec,
             *(TS if container == "ts" else []), str(out))
    return make


def audio_stereo_to_51(m, out):
    two_parts(m, out, second=["-output_ts_offset", str(m.seconds(60) / 2)],
              audio_first=["-c:a", "ac3", "-b:a", "192k"], audio_second=["-c:a", "ac3", "-b:a", "448k"], layout_second="5.1")


def subtitles(kind: str):
    def make(m, out):
        s = m.seconds(60)
        text = m.temp("subs." + kind)
        text.write_text(srt(s) if kind == "srt" else ass(s), encoding="utf-8")
        m.ff(*m.video("1280x720", 25, s), *m.audio("stereo", s), "-i", str(text), *H264, "-b:v", "3M", "-g", "50",
             "-c:a", "aac", "-b:a", "128k", "-c:s", "copy" if kind == "ass" else "srt", "-metadata:s:s:0", "language=eng", str(out))
    return make


def hls(segment_type: str):
    def make(m, out):
        s = m.seconds(60)
        if out.exists():
            shutil.rmtree(out)
        out.mkdir(parents=True)
        fmp4 = segment_type == "fmp4"
        m.ff(*m.video("1280x720", 50, s), *m.audio("stereo", s), *H264, "-b:v", "3M", "-g", "50", "-keyint_min", "50",
             "-sc_threshold", "0", "-c:a", "aac", "-b:a", "128k", "-f", "hls", "-hls_time", "4", "-hls_playlist_type", "vod",
             *(["-hls_segment_type", "fmp4", "-hls_fmp4_init_filename", "init.mp4"] if fmp4 else []),
             "-hls_segment_filename", str(out / ("seg_%03d.m4s" if fmp4 else "seg_%03d.ts")), str(out / "index.m3u8"))
    return make


def hls_webvtt(m, out):
    s = m.seconds(60)
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    text = m.temp("subs.srt")
    text.write_text(srt(s), encoding="utf-8")
    m.ff(*m.video("1280x720", 25, s), *m.audio("stereo", s), "-i", str(text), "-map", "0:v", "-map", "1:a", "-map", "2:s",
         *H264, "-b:v", "3M", "-g", "50", "-c:a", "aac", "-b:a", "128k", "-c:s", "webvtt", "-metadata:s:s:0", "language=eng",
         "-f", "hls", "-hls_time", "6", "-hls_playlist_type", "vod", "-master_pl_name", "master.m3u8",
         "-var_stream_map", "v:0,a:0,s:0,sgroup:subs", "-hls_segment_filename", str(out / "seg_%v_%03d.ts"),
         str(out / "stream_%v.m3u8"))


def srt(seconds: float) -> str:
    lines = []
    for n, at in enumerate(range(1, int(seconds) - 1, 3), start=1):
        lines.append(f"{n}\n{clock(at)},000 --> {clock(at + 2)},000\nSubtitle {n}, at {at} s\n")
    return "\n".join(lines)


def ass(seconds: float) -> str:
    events = "\n".join(
        f"Dialogue: 0,{clock(at, ass=True)}.00,{clock(at + 2, ass=True)}.00,Torture,,0,0,0,,{{\\i1}}Styled{{\\i0}} subtitle {n}, at {at} s"
        for n, at in enumerate(range(1, int(seconds) - 1, 3), start=1)
    )
    return (
        "[Script Info]\nScriptType: v4.00+\nPlayResX: 1280\nPlayResY: 720\n\n"
        "[V4+ Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour, OutlineColour, BackColour, Bold, Italic, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV\n"
        "Style: Torture,Arial,48,&H0000FFFF,&H00000000,&H80000000,1,0,1,3,0,2,40,40,60\n\n"
        "[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" + events + "\n"
    )


def clock(seconds: int, ass: bool = False) -> str:
    h, rest = divmod(seconds, 3600)
    m, s = divmod(rest, 60)
    return f"{h}:{m:02d}:{s:02d}" if ass else f"{h:02d}:{m:02d}:{s:02d}"


RECIPES = {
    "h264_720p50.ts": h264_720p50_file,
    "ts_jump_back.ts": ts_jump_back,
    "ts_clock_jump.ts": ts_clock_jump,
    "ts_pid_change.ts": ts_pid_change,
    "ts_no_pat.ts": ts_no_pat,
    "ts_continuity.ts": ts_continuity,
    "ts_null_padding.ts": ts_null_padding,
    "ts_two_programmes.ts": ts_two_programmes,
    "ts_scrambled.ts": ts_scrambled,
    "h264_1080i50_mbaff.ts": h264_1080i50_mbaff,
    "mpeg2_576i.ts": mpeg2_576i,
    "hevc_2160p50_hdr10.ts": lambda m, out: hevc_2160p50(m, out, "smpte2084"),
    "hevc_2160p50_hlg.ts": lambda m, out: hevc_2160p50(m, out, "arib-std-b67"),
    "av1_1080p.mkv": av1_1080p,
    "audio_mp2.ts": audio_case(["-c:a", "mp2", "-b:a", "192k"], "stereo"),
    "audio_ac3_20.ts": audio_case(["-c:a", "ac3", "-b:a", "192k"], "stereo"),
    "audio_ac3_51.ts": audio_case(["-c:a", "ac3", "-b:a", "448k"], "5.1"),
    "audio_eac3_51.ts": audio_case(["-c:a", "eac3", "-b:a", "384k"], "5.1"),
    "audio_dts.mkv": audio_case(["-c:a", "dca", "-strict", "-2", "-b:a", "768k"], "5.1", "mkv"),
    "audio_truehd.mkv": audio_case(["-c:a", "truehd", "-strict", "-2"], "5.1", "mkv"),
    "audio_stereo_to_51.ts": audio_stereo_to_51,
    "subs_srt.mkv": subtitles("srt"),
    "subs_ass.mkv": subtitles("ass"),
    "hls_webvtt": hls_webvtt,
    "hls_ts": hls("ts"),
    "hls_fmp4": hls("fmp4"),
}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", type=Path, default=CAPTURES, help="where the captures go (default: %(default)s)")
    parser.add_argument("--only", nargs="*", help="just these captures")
    parser.add_argument("--seconds", type=float, help="make each at most this long, for a quick check")
    args = parser.parse_args()
    if not shutil.which("ffmpeg") or not shutil.which("ffprobe"):
        sys.exit("FFmpeg isn't installed: https://ffmpeg.org/download.html")

    args.out.mkdir(parents=True, exist_ok=True)
    maker = Maker(args.out, args.seconds)
    manifest_path = args.out / "manifest.json"
    manifest = json.loads(manifest_path.read_text()) if manifest_path.exists() else {}
    names = args.only or list(RECIPES)
    for name in names:
        if name not in RECIPES:
            sys.exit(f"No recipe for {name}; there are: {', '.join(RECIPES)}")
        print(f"Making {name}", flush=True)
        out = args.out / name
        maker.length = 0.0
        RECIPES[name](maker, out)
        # Its length as made, for the server to send it as fast as it plays: its timestamps can't
        # say, when they jump an hour or are scrambled.
        manifest[name] = {"duration": maker.length}
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n")
    shutil.rmtree(maker.work, ignore_errors=True)
    print(f"Done: {len(names)} captures in {args.out}")


if __name__ == "__main__":
    main()
