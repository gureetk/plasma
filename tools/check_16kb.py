#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Checks that APKs are ready for Android's 16 KB memory pages (brief 5.18).

For each APK given: every native library stored uncompressed starts on a 16 KB boundary in the
file (what `zipalign -c -P 16` checks), and every 64-bit library's loadable segments are aligned to
16 KB or more. 32-bit libraries are left alone: only 64-bit Android uses 16 KB pages.

Exits 1 and names what isn't aligned if anything isn't. Needs only Python 3.

    python3 tools/check_16kb.py app/build/outputs/apk/release/*.apk
"""
import struct
import sys
import zipfile

PAGE = 16 * 1024


def data_offset(apk, info):
    """Where an entry's data starts in the file: after its local header, name and extra field."""
    apk.seek(info.header_offset)
    header = apk.read(30)
    name_length, extra_length = struct.unpack_from("<HH", header, 26)
    return info.header_offset + 30 + name_length + extra_length


def load_alignments(elf):
    """The p_align of each PT_LOAD segment of a 64-bit little-endian ELF, or None for any other."""
    if elf[:4] != b"\x7fELF" or elf[4] != 2 or elf[5] != 1:
        return None
    phoff, = struct.unpack_from("<Q", elf, 0x20)
    phentsize, phnum = struct.unpack_from("<HH", elf, 0x36)
    alignments = []
    for i in range(phnum):
        entry = phoff + i * phentsize
        if struct.unpack_from("<I", elf, entry)[0] == 1:
            alignments.append(struct.unpack_from("<Q", elf, entry + 0x30)[0])
    return alignments


def problems(path):
    found = []
    with zipfile.ZipFile(path) as zip_file, open(path, "rb") as raw:
        for info in zip_file.infolist():
            if not (info.filename.startswith("lib/") and info.filename.endswith(".so")):
                continue
            if info.compress_type == zipfile.ZIP_STORED and data_offset(raw, info) % PAGE:
                found.append(f"{info.filename}: not on a 16 KB boundary in the APK")
            alignments = load_alignments(zip_file.read(info))
            if alignments and min(alignments) < PAGE:
                found.append(f"{info.filename}: segments aligned to {min(alignments)} bytes")
    return found


def main(paths):
    if not paths:
        print(__doc__.strip().splitlines()[-1].strip())
        return 2
    failed = False
    for path in paths:
        found = problems(path)
        print(f"{path}: {'ready for 16 KB pages' if not found else 'NOT ready for 16 KB pages'}")
        for line in found:
            print(f"  {line}")
        failed = failed or bool(found)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
