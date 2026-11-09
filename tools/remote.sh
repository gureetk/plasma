#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# The keys a MAG-style remote has, for an emulator or a TV that adb reaches (brief 6.9, #129):
# the Google TV remote has no Menu, colour, number or channel keys, so this stands in for them.
#
#   tools/remote.sh red              the red key
#   tools/remote.sh 1 0 5 ok         type channel 105, then OK
#   tools/remote.sh -s emulator-5556 guide
#
# Keys: up down left right ok back home menu info guide red green yellow blue 0-9 ch+ ch- play
# pause playpause stop rewind forward record prev next mute vol+ vol- tv epg subtitles audio
set -euo pipefail

serial=()
if [[ ${1:-} == "-s" ]]; then
  serial=(-s "$2")
  shift 2
fi
if [[ $# -eq 0 ]]; then
  sed -n '3,11p' "$0" | sed 's/^# \{0,1\}//'
  exit 1
fi

code() {
  case "$1" in
    up) echo KEYCODE_DPAD_UP ;;
    down) echo KEYCODE_DPAD_DOWN ;;
    left) echo KEYCODE_DPAD_LEFT ;;
    right) echo KEYCODE_DPAD_RIGHT ;;
    ok) echo KEYCODE_DPAD_CENTER ;;
    back) echo KEYCODE_BACK ;;
    home) echo KEYCODE_HOME ;;
    menu) echo KEYCODE_MENU ;;
    info) echo KEYCODE_INFO ;;
    guide | epg) echo KEYCODE_GUIDE ;;
    red) echo KEYCODE_PROG_RED ;;
    green) echo KEYCODE_PROG_GREEN ;;
    yellow) echo KEYCODE_PROG_YELLOW ;;
    blue) echo KEYCODE_PROG_BLUE ;;
    [0-9]) echo "KEYCODE_$1" ;;
    ch+) echo KEYCODE_CHANNEL_UP ;;
    ch-) echo KEYCODE_CHANNEL_DOWN ;;
    play) echo KEYCODE_MEDIA_PLAY ;;
    pause) echo KEYCODE_MEDIA_PAUSE ;;
    playpause) echo KEYCODE_MEDIA_PLAY_PAUSE ;;
    stop) echo KEYCODE_MEDIA_STOP ;;
    rewind) echo KEYCODE_MEDIA_REWIND ;;
    forward) echo KEYCODE_MEDIA_FAST_FORWARD ;;
    record) echo KEYCODE_MEDIA_RECORD ;;
    prev) echo KEYCODE_MEDIA_PREVIOUS ;;
    next) echo KEYCODE_MEDIA_NEXT ;;
    mute) echo KEYCODE_VOLUME_MUTE ;;
    vol+) echo KEYCODE_VOLUME_UP ;;
    vol-) echo KEYCODE_VOLUME_DOWN ;;
    tv) echo KEYCODE_TV ;;
    subtitles) echo KEYCODE_CAPTIONS ;;
    audio) echo KEYCODE_MEDIA_AUDIO_TRACK ;;
    *) echo "Unknown key: $1" >&2; exit 1 ;;
  esac
}

for key in "$@"; do
  adb "${serial[@]}" shell input keyevent "$(code "$key")"
done
