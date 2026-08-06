#!/usr/bin/env python3
"""Restore and extract SMTPD cover images from the split archive.

Subcommands:
    restore  - join split volumes (img_yt_divide.z01/.z02/.zip) into data/img_yt.zip
    pool     - extract covers for the curated pool (top-N per category + random)
    ids      - extract covers for comma-separated video ids
    all      - extract every cover (full extraction)
"""
import argparse
import random
import shutil
import sqlite3
import sys
import time
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "data"
INNER_ZIP = DATA / "img_yt.zip"
COVERS = DATA / "covers"

SPLIT_PARTS = [
    DATA / "img_yt_divide.z01",
    DATA / "img_yt_divide.z02",
    DATA / "img_yt_divide.zip",
]
COMBINED_TMP = DATA / "img_yt_divide_combined.zip"


def shard_path(video_id: str) -> Path:
    return COVERS / video_id[:2] / f"{video_id}.jpg"


def restore() -> Path:
    if INNER_ZIP.exists() and INNER_ZIP.stat().st_size > 0:
        print(f"inner zip already present: {INNER_ZIP.name} ({INNER_ZIP.stat().st_size / 1e9:.1f} GB)")
        return INNER_ZIP
    missing = [p for p in SPLIT_PARTS if not p.exists()]
    if missing:
        sys.exit(f"missing split parts: {missing}")
    t0 = time.time()
    with open(COMBINED_TMP, "wb") as out:
        for part in SPLIT_PARTS:
            with open(part, "rb") as src:
                shutil.copyfileobj(src, out, length=64 * 1024 * 1024)
            print(f"  joined {part.name} ({time.time() - t0:.0f}s)", flush=True)
    print(f"combined archive: {COMBINED_TMP.stat().st_size / 1e9:.1f} GB", flush=True)
    with zipfile.ZipFile(COMBINED_TMP) as zf:
        names = zf.namelist()
        print(f"outer entries: {len(names)} -> {names[:5]}")
        if len(names) != 1:
            COMBINED_TMP.unlink()
            sys.exit(f"unexpected outer entries: {names}")
        with zf.open(names[0]) as src, open(INNER_ZIP, "wb") as out:
            shutil.copyfileobj(src, out, length=64 * 1024 * 1024)
    COMBINED_TMP.unlink()
    print(f"inner zip restored: {INNER_ZIP.name} ({INNER_ZIP.stat().st_size / 1e9:.1f} GB, {time.time() - t0:.0f}s)")
    return INNER_ZIP


def entry_name_to_id(name: str):
    stem = name.replace("\\", "/").rsplit("/", 1)[-1]
    if "." in stem:
        stem = stem.rsplit(".", 1)[0]
    return stem or None


def extract(ids, missing_only=True):
    if not INNER_ZIP.exists():
        sys.exit("inner zip missing; run: python scripts/extract_covers.py restore")
    want = set(ids)
    with zipfile.ZipFile(INNER_ZIP) as zf:
        names = zf.namelist()
        print(f"inner zip entries: {len(names):,} (sample: {names[:3]})")
        by_stem = {}
        for n in names:
            s = entry_name_to_id(n)
            if s:
                by_stem.setdefault(s, n)
        matched = [s for s in want if s in by_stem]
        print(f"want {len(want):,}, matched {len(matched):,}")
        t0 = time.time()
        done = 0
        for vid in matched:
            dest = shard_path(vid)
            if missing_only and dest.exists():
                continue
            dest.parent.mkdir(parents=True, exist_ok=True)
            with zf.open(by_stem[vid]) as src, open(dest, "wb") as out:
                shutil.copyfileobj(src, out, length=1024 * 1024)
            done += 1
            if done % 2000 == 0:
                print(f"  {done:,}/{len(matched):,} covers ({time.time() - t0:.0f}s)", flush=True)
        print(f"extracted {done:,} covers ({time.time() - t0:.0f}s)")


def pool_ids(db_path, per_category=1000, random_n=5000, seed=42):
    conn = sqlite3.connect(db_path)
    cur = conn.cursor()
    categories = [r[0] for r in cur.execute("SELECT DISTINCT category FROM videos")]
    ids = set()
    for cat in categories:
        rows = cur.execute(
            "SELECT video_id FROM videos WHERE category=? ORDER BY day30 DESC, day01 DESC LIMIT ?",
            (cat, per_category),
        ).fetchall()
        ids.update(r[0] for r in rows)
    all_ids = [r[0] for r in cur.execute("SELECT video_id FROM videos").fetchall()]
    rng = random.Random(seed)
    ids.update(rng.sample(all_ids, min(random_n, len(all_ids))))
    conn.close()
    return ids


def main():
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="cmd", required=True)
    sub.add_parser("restore")
    p_pool = sub.add_parser("pool")
    p_pool.add_argument("--db", default=str(DATA / "smtpd.db"))
    p_pool.add_argument("--per-category", type=int, default=1000)
    p_pool.add_argument("--random", type=int, default=5000)
    p_ids = sub.add_parser("ids")
    p_ids.add_argument("ids")
    sub.add_parser("all")
    args = parser.parse_args()

    if args.cmd == "restore":
        restore()
    elif args.cmd == "pool":
        restore()
        ids = pool_ids(args.db, args.per_category, args.random)
        print(f"pool size: {len(ids):,}")
        extract(ids)
    elif args.cmd == "ids":
        restore()
        ids = {s.strip() for s in args.ids.split(",") if s.strip()}
        extract(ids)
    elif args.cmd == "all":
        restore()
        with zipfile.ZipFile(INNER_ZIP) as zf:
            ids = {entry_name_to_id(n) for n in zf.namelist()}
        extract(ids)


if __name__ == "__main__":
    main()
