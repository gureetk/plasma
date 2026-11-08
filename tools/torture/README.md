# The stream torture set

Brief 6.9's torture set, for #130: short captures of the stream cases live TV runs into, served
to Plasma as an M3U playlist, so each one can be played in VLC, mpv and the Android player, in
each way of decoding. The results go in `docs/testing.md` ("The stream torture set"), and the
defaults follow from them: live TV's engine, the fallback order, decoding and deinterlacing.

Everything is made by FFmpeg from its own test pictures and tones. Nothing comes from a
provider, and the captures stay out of the repository, in `captures/` here, which git ignores.

## Making the captures

FFmpeg 6 or later, with libx264, libx265 and libsvtav1, as Homebrew's (`brew install ffmpeg`) and
the static builds for Windows and Linux have. Then, from the repository:

```
python3 tools/torture/make_captures.py
```

That takes 10 to 20 minutes, most of it for the two 2160p HEVC captures, and makes under a
gigabyte. `--only name ...` remakes some, and `--seconds 6` makes short ones to check that
every recipe works.

The tones say which speaker they're in, by pitch: front left 440 Hz, front right 554 Hz, centre
660 Hz, LFE 55 Hz, surround left 330 Hz, surround right 392 Hz.

## Playing them on the TV

On a computer on the TV's network:

```
python3 tools/torture/serve.py
```

It prints the playlist's address, something like `http://192.168.1.20:8099/torture.m3u`. On
the TV, Settings → Portals & Playlists → Add Portal or Playlist → M3U Playlist, and type it, or
send it from a phone. Each case is a channel, numbered, in a group for its area
("Torture · MPEG-TS", "Torture · Audio"...).

- **Live cases** play as a live channel: at the speed they play, joined where the stream is now,
  and round again after 60 s, with a jump back in time where they go round.
- **Joined without PAT/PMT** always starts at its start, 3 s before its first PAT.
- **The MKV cases** (AV1, DTS, TrueHD, SRT and ASS) are channels too, served as files.

For each case, play it in each engine (Player Options → Switch Player) and with each of Settings
→ Playback → Video Decoding's choices: Hardware, Hardware, copy back, and Software. Each case's name
says what it is, and `cases.py` says what Plasma should do with it.

## Not in the set

Some of 6.9's cases can't be made with FFmpeg, so they come from real captures or the owner's
providers:

| Area | Case | Why |
|---|---|---|
| Video | H.264 1080i50 PAFF | x264 codes interlaced video as MBAFF only. |
| Video | Dolby Vision profiles 5 and 8.1 | FFmpeg doesn't write Dolby Vision metadata. |
| Audio | E-AC-3 Atmos, TrueHD Atmos, DTS-HD MA | FFmpeg has no encoder for them. |
| Audio | HE-AAC v1 and v2 in LATM | Needs FFmpeg built with libfdk_aac. |
| Subtitles | DVB bitmap, teletext page 888, CEA-608, PGS | FFmpeg can't make them from text. |
| Transport | UDP and RTP multicast, with and without udpxy | Needs a multicast network. |
