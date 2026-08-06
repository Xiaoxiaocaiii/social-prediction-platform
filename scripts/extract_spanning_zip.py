#!/usr/bin/env python3
"""Extract a single entry from a spanning (multi-disk) ZIP archive.

Python's zipfile and .NET ZipArchive both refuse spanning archives, so this
script parses the End Of Central Directory and the central directory manually.
In spanning archives all offsets are relative to the disk on which the record
starts, so we add the cumulative sizes of the preceding split parts.
"""
import struct
import sys
import zipfile
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "data"
SRC = DATA / "img_yt_divide_combined.zip"
DST = DATA / "img_yt.zip"
PARTS = [
    DATA / "img_yt_divide.z01",
    DATA / "img_yt_divide.z02",
    DATA / "img_yt_divide.zip",
]
TARGET_NAME = "img_yt.zip"

EOCD_SIG = b"PK\x05\x06"
CEN_SIG = b"PK\x01\x02"
LOC_SIG = b"PK\x03\x04"
CHUNK = 64 * 1024 * 1024


def main():
    if not SRC.exists():
        sys.exit(f"combined archive not found: {SRC}")
    total = SRC.stat().st_size
    print(f"combined archive: {total / 1e9:.1f} GB")

    bases = []
    acc = 0
    for part in PARTS:
        if not part.exists():
            sys.exit(f"missing part: {part}")
        bases.append(acc)
        acc += part.stat().st_size

    with open(SRC, "rb") as f:
        window = 70 * 1024
        f.seek(max(0, total - window))
        tail = f.read()
        eocd_off_in_tail = tail.rfind(EOCD_SIG)
        if eocd_off_in_tail < 0:
            sys.exit("EOCD not found")
        fields = struct.unpack_from("<IHHHHIIH", tail, eocd_off_in_tail)
        cd_disk = fields[2]
        n_total = fields[4]
        cd_size = fields[5]
        cd_off = fields[6]
        if cd_disk >= len(bases) or cd_off == 0xFFFFFFFF:
            sys.exit("unsupported EOCD layout (unexpected ZIP64 sentinels)")
        cd_abs = bases[cd_disk] + cd_off

        print(f"central directory: disk={cd_disk}, abs_offset={cd_abs}, size={cd_size}, entries={n_total}")
        f.seek(cd_abs)
        cd = f.read(cd_size)

    pos = 0
    entry = None
    while pos + 46 <= len(cd):
        if cd[pos:pos + 4] != CEN_SIG:
            sys.exit(f"bad central directory signature at {pos}")
        method = struct.unpack_from("<H", cd, pos + 10)[0]
        csize = struct.unpack_from("<I", cd, pos + 20)[0]
        usize = struct.unpack_from("<I", cd, pos + 24)[0]
        fname_len = struct.unpack_from("<H", cd, pos + 28)[0]
        extra_len = struct.unpack_from("<H", cd, pos + 30)[0]
        cment_len = struct.unpack_from("<H", cd, pos + 32)[0]
        disk_start = struct.unpack_from("<H", cd, pos + 34)[0]
        local_off = struct.unpack_from("<I", cd, pos + 42)[0]
        name = cd[pos + 46:pos + 46 + fname_len].decode("utf-8", errors="replace")
        extra = cd[pos + 46 + fname_len:pos + 46 + fname_len + extra_len]

        if name == TARGET_NAME:
            ex = 0
            while ex + 4 <= len(extra):
                eid, esize = struct.unpack_from("<HH", extra, ex)
                if eid == 0x0001:
                    vals = []
                    ex2 = ex + 4
                    end = ex + 4 + esize
                    while ex2 < end:
                        vals.append(struct.unpack_from("<Q", extra, ex2)[0])
                        ex2 += 8
                    vi = 0
                    if usize == 0xFFFFFFFF and vi < len(vals):
                        usize = vals[vi]
                        vi += 1
                    if csize == 0xFFFFFFFF and vi < len(vals):
                        csize = vals[vi]
                        vi += 1
                    if local_off == 0xFFFFFFFF and vi < len(vals):
                        local_off = vals[vi]
                ex += 4 + esize
            entry = {
                "name": name, "method": method,
                "csize": csize, "usize": usize, "disk_start": disk_start,
                "local_off": local_off,
            }
            break
        pos += 46 + fname_len + extra_len + cment_len

    if entry is None:
        sys.exit(f"entry {TARGET_NAME} not found in central directory")
    print(f"entry: {entry}")
    local_abs = bases[entry["disk_start"]] + entry["local_off"]
    print(f"local header abs_offset: {local_abs}")

    with open(SRC, "rb") as f:
        f.seek(local_abs)
        loc = f.read(30)
        if loc[:4] != LOC_SIG:
            sys.exit(f"bad local header at {local_abs}: {loc[:4].hex()}")
        fname_len = struct.unpack_from("<H", loc, 26)[0]
        extra_len = struct.unpack_from("<H", loc, 28)[0]
        data_off = local_abs + 30 + fname_len + extra_len
        f.seek(data_off)

        remaining = entry["csize"]
        written = 0
        with open(DST, "wb") as out:
            if entry["method"] == 0:
                while remaining > 0:
                    chunk = f.read(min(CHUNK, remaining))
                    if not chunk:
                        break
                    out.write(chunk)
                    written += len(chunk)
                    remaining -= len(chunk)
            elif entry["method"] == 8:
                d = zlib.decompressobj(-15)
                while remaining > 0:
                    chunk = f.read(min(CHUNK, remaining))
                    if not chunk:
                        break
                    out.write(d.decompress(chunk))
                    written += len(chunk)
                    remaining -= len(chunk)
                out.write(d.flush())
            else:
                sys.exit(f"unsupported compression method: {entry['method']}")

    print(f"extracted {DST} ({DST.stat().st_size / 1e9:.1f} GB)")
    with open(DST, "rb") as f:
        print("inner zip signature:", f.read(4).hex())
    with zipfile.ZipFile(DST) as zf:
        names = zf.namelist()
        print(f"inner zip entries: {len(names):,} (sample: {names[:5]})")


if __name__ == "__main__":
    main()
