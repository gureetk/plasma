#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Serves the stream torture set (brief 6.9, #130) to Plasma, as an M3U playlist of channels.

Make the captures first (make_captures.py), then, on a computer on the TV's network:

    python3 tools/torture/serve.py

and add the address it prints as an M3U playlist in Plasma (Settings › Portals & Playlists › Add
Portal or Playlist, typed or from a phone). Each case is a channel, numbered, in a group for its
area. Live captures play as live TV: at the speed they play, joined where the stream is now, and
round again at the end (where there's a jump back in time). Nothing here needs more than Python 3.
"""
from __future__ import annotations

import argparse
import json
import re
import secrets
import socket
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

sys.path.insert(0, str(Path(__file__).resolve().parent))
from cases import CASES, Case  # noqa: E402

HERE = Path(__file__).resolve().parent
PACKET = 188
#: Sent this often, in seconds, so a stream arrives about as fast as it plays.
TICK = 0.1
#: The live HLS window: this many segments, moving on one segment at a time.
WINDOW = 3
USER_AGENT = "PlasmaTorture/1.0"
REFERRER = "http://torture.example/"
CONTENT_TYPES = {".ts": "video/mp2t", ".mkv": "video/x-matroska", ".m3u8": "application/vnd.apple.mpegurl",
                 ".m4s": "video/iso.segment", ".mp4": "video/mp4", ".vtt": "text/vtt"}


class Torture:
    def __init__(self, captures: Path):
        self.captures = captures
        manifest = captures / "manifest.json"
        self.durations = {k: v.get("duration") for k, v in json.loads(manifest.read_text()).items()} if manifest.exists() else {}
        self.started = time.monotonic()
        self.cases = {case.id: case for case in CASES}

    def missing(self) -> list:
        return [case for case in CASES if not (self.captures / case.capture).exists()]

    def rate(self, capture: str) -> float:
        """Bytes a second, so it's sent as fast as it plays."""
        size = (self.captures / capture).stat().st_size
        duration = self.durations.get(capture) or 60.0
        return size / duration

    def live_offset(self, capture: str) -> int:
        """Where the stream is now, as a live channel is joined: in packets from the start, round again at the end."""
        size = (self.captures / capture).stat().st_size
        duration = self.durations.get(capture) or 60.0
        at = (time.monotonic() - self.started) % duration
        return int(at / duration * size) // PACKET * PACKET

    def playlist(self, host: str) -> str:
        lines = ["#EXTM3U"]
        for number, case in enumerate(CASES, start=1):
            lines.append(f'#EXTINF:-1 tvg-id="torture-{case.id}" tvg-chno="{number}" group-title="Torture · {case.area}",{number} {case.name}')
            lines.extend(case.options)
            lines.append(f"http://{host}/c/{case.id}/{'index.m3u8' if case.transport.startswith('hls') else 'stream'}")
        return "\n".join(lines) + "\n"

    def hls_segments(self, capture: str) -> list:
        """A VOD playlist's segments, as (duration, uri), and its EXT-X-MAP if it has one."""
        text = (self.captures / capture / "index.m3u8").read_text()
        segments, duration = [], None
        for line in text.splitlines():
            if line.startswith("#EXTINF:"):
                duration = float(line[8:].split(",")[0])
            elif line and not line.startswith("#") and duration is not None:
                segments.append((duration, line))
                duration = None
        init = re.search(r'#EXT-X-MAP:URI="([^"]+)"', text)
        return segments, init.group(1) if init else None

    def live_playlist(self, case: Case, prefix: str, loop: int | None = None) -> str:
        """A live HLS playlist over the capture's segments: a window that moves on in real time, with a
        discontinuity each time it goes round."""
        segments, init = self.hls_segments(case.capture)
        if loop:
            segments = segments[:loop]
        target = max(d for d, _ in segments)
        length = sum(d for d, _ in segments)
        elapsed = time.monotonic() - self.started
        # Media sequence numbers count every segment since the server started.
        laps, into = divmod(elapsed, length)
        index = 0
        while into >= segments[index][0]:
            into -= segments[index][0]
            index += 1
        first = int(laps) * len(segments) + index
        lines = ["#EXTM3U", "#EXT-X-VERSION:7" if init else "#EXT-X-VERSION:3", f"#EXT-X-TARGETDURATION:{int(target + 0.999)}",
                 f"#EXT-X-MEDIA-SEQUENCE:{first}", f"#EXT-X-DISCONTINUITY-SEQUENCE:{int(laps)}"]
        if init:
            lines.append(f'#EXT-X-MAP:URI="{prefix}{init}"')
        for n in range(first, first + WINDOW):
            duration, uri = segments[n % len(segments)]
            if n % len(segments) == 0 and n != first:
                lines.append("#EXT-X-DISCONTINUITY")
                if init:
                    lines.append(f'#EXT-X-MAP:URI="{prefix}{init}"')
            lines.append(f"#EXTINF:{duration:.3f},")
            lines.append(prefix + uri)
        return "\n".join(lines) + "\n"


def handler_for(torture: Torture):
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"
        server_version = "PlasmaTorture/1.0"

        def log_message(self, format, *args):
            sys.stderr.write(f"{time.strftime('%H:%M:%S')} {self.client_address[0]} {format % args}\n")

        def do_GET(self):
            url = urlparse(self.path)
            parts = [p for p in url.path.split("/") if p]
            if url.path in ("/", "/torture.m3u"):
                return self.text(torture.playlist(self.headers.get("Host", "localhost")), "audio/x-mpegurl")
            if len(parts) >= 3 and parts[0] == "t":
                # Where the redirects end: /t/<token>/<case>/stream.ts.
                case = torture.cases.get(parts[2])
                return self.stream(case) if case else self.status(404, "No such case")
            if len(parts) >= 2 and parts[0] == "c":
                case = torture.cases.get(parts[1])
                if case is None:
                    return self.status(404, "No such case")
                return self.case(case, parts[2:], parse_qs(url.query))
            self.status(404, "Not found")

        def case(self, case: Case, rest: list, query: dict):
            kind = case.transport
            if kind.startswith("hls"):
                return self.hls(case, rest)
            if kind == "redirect":
                # Two redirects, the second to an address with a token in it, as providers' load balancers do.
                if "token" not in query:
                    return self.redirect(f"/c/{case.id}/stream?token={secrets.token_hex(8)}")
                return self.redirect(f"/t/{query['token'][0]}/{case.id}/stream.ts")
            if kind == "user-agent" and self.headers.get("User-Agent") != USER_AGENT:
                return self.status(403, f"This stream is only for the user agent {USER_AGENT}")
            if kind == "referrer" and self.headers.get("Referer") != REFERRER:
                return self.status(403, f"This stream is only for the referrer {REFERRER}")
            if kind == "limit":
                return self.status(403, "Max connections reached for this account")
            if kind == "error-page":
                return self.text("<!DOCTYPE html><html><head><title>Error</title></head><body><h1>Your subscription has expired</h1>"
                                 "<p>Please contact your provider.</p></body></html>\n", "text/html")
            if kind == "file":
                return self.file(torture.captures / case.capture)
            if kind == "slow":
                time.sleep(5)
            self.stream(case, http10=kind == "http10", chunked=kind == "chunked", drop_after=20 if kind == "drop" else None,
                        from_start=kind == "from-start")

        def stream(self, case: Case, http10=False, chunked=False, drop_after=None, from_start=False):
            path = torture.captures / case.capture
            size = path.stat().st_size
            rate = torture.rate(case.capture)
            per_tick = max(PACKET, int(rate * TICK) // PACKET * PACKET)
            offset = 0 if from_start else torture.live_offset(case.capture)
            if http10:
                self.wfile.write(b"HTTP/1.0 200 OK\r\nContent-Type: video/mp2t\r\n\r\n")
                self.close_connection = True
            else:
                self.send_response(200)
                self.send_header("Content-Type", "video/mp2t")
                if chunked:
                    self.send_header("Transfer-Encoding", "chunked")
                else:
                    self.send_header("Connection", "close")
                    self.close_connection = True
                self.end_headers()
            began = time.monotonic()
            sent = 0
            with path.open("rb") as file:
                file.seek(offset)
                try:
                    while drop_after is None or time.monotonic() - began < drop_after:
                        data = file.read(per_tick)
                        if len(data) < per_tick:
                            # Round again: live TV doesn't end.
                            file.seek(0)
                            data += file.read(per_tick - len(data))
                        if chunked:
                            self.wfile.write(f"{len(data):x}\r\n".encode() + data + b"\r\n")
                        else:
                            self.wfile.write(data)
                        sent += len(data)
                        # Ahead of time by no more than a tick.
                        wait = began + sent / rate - time.monotonic() - TICK
                        if wait > 0:
                            time.sleep(wait)
                except (BrokenPipeError, ConnectionResetError):
                    return
            # Dropped mid-stream: no end to the response, the connection just goes.
            self.close_connection = True
            try:
                self.connection.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass

        def hls(self, case: Case, rest: list):
            folder = torture.captures / case.capture
            name = rest[0] if rest else "index.m3u8"
            prefix = f"/c/{case.id}/"
            if case.transport == "hls-redundant" and name == "index.m3u8":
                # Two streams of the same bandwidth: the first has gone, so the player takes the second.
                return self.text("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=3500000,RESOLUTION=1280x720\n"
                                 f"/c/{case.id}/gone.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=3500000,RESOLUTION=1280x720\n"
                                 f"/c/{case.id}/live.m3u8\n", CONTENT_TYPES[".m3u8"])
            if name == "gone.m3u8":
                return self.status(404, "This stream has gone")
            if case.transport == "hls-vod":
                if name == "index.m3u8":
                    name = "master.m3u8"
                return self.file(folder / name)
            if name in ("index.m3u8", "live.m3u8"):
                loop = 4 if case.transport == "hls-discontinuity" else None
                return self.text(torture.live_playlist(case, prefix, loop), CONTENT_TYPES[".m3u8"])
            self.file(folder / name)

        def file(self, path: Path):
            if not path.is_file():
                return self.status(404, "No such file")
            size = path.stat().st_size
            start, end = 0, size - 1
            ranged = re.match(r"bytes=(\d*)-(\d*)", self.headers.get("Range", ""))
            if ranged and (ranged.group(1) or ranged.group(2)):
                if ranged.group(1):
                    start = int(ranged.group(1))
                    end = int(ranged.group(2)) if ranged.group(2) else size - 1
                else:
                    start = size - int(ranged.group(2))
                self.send_response(206)
                self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
            else:
                self.send_response(200)
            self.send_header("Content-Type", CONTENT_TYPES.get(path.suffix, "application/octet-stream"))
            self.send_header("Content-Length", str(end - start + 1))
            self.send_header("Accept-Ranges", "bytes")
            self.end_headers()
            with path.open("rb") as file:
                file.seek(start)
                left = end - start + 1
                try:
                    while left > 0:
                        data = file.read(min(left, 256 * 1024))
                        if not data:
                            break
                        self.wfile.write(data)
                        left -= len(data)
                except (BrokenPipeError, ConnectionResetError):
                    pass

        def text(self, body: str, content_type: str):
            data = body.encode()
            self.send_response(200)
            self.send_header("Content-Type", content_type + "; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(data)

        def status(self, code: int, message: str):
            data = (message + "\n").encode()
            self.send_response(code)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def redirect(self, location: str):
            self.send_response(302)
            self.send_header("Location", location)
            self.send_header("Content-Length", "0")
            self.end_headers()

    return Handler


def lan_address() -> str:
    """This computer's address on its network, as the TV sees it."""
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
        try:
            probe.connect(("192.0.2.1", 9))  # Nothing is sent: it only picks the interface.
            return probe.getsockname()[0]
        except OSError:
            return "127.0.0.1"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--captures", type=Path, default=HERE / "captures", help="where make_captures.py put them")
    parser.add_argument("--port", type=int, default=8099)
    args = parser.parse_args()
    torture = Torture(args.captures)
    missing = torture.missing()
    if missing:
        print("Not made yet, so not in the playlist's working set: " + ", ".join(c.capture for c in missing), file=sys.stderr)
    server = ThreadingHTTPServer(("0.0.0.0", args.port), handler_for(torture))
    server.daemon_threads = True
    print(f"Add this as an M3U playlist in Plasma: http://{lan_address()}:{args.port}/torture.m3u")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
