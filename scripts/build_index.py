#!/usr/bin/env python3
"""Build a SQLite search/analysis index from SMTPD basic_view_pn.csv.

Usage:
    python scripts/build_index.py [--limit N]
"""
import argparse
import csv
import sqlite3
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CSV_PATH = ROOT / "data" / "basic_view_pn.csv"
DB_PATH = ROOT / "data" / "smtpd.db"

BASE_COLS = [
    "video_id", "video_url", "video_cover", "user_url", "user_image", "user_id",
    "category", "title", "tags", "description",
    "user_subscribers", "video_duration", "title_length", "tag_num", "desc_length",
]
DAY_COLS = [f"Day {i}" for i in range(1, 31)]
SQL_COLS = [c.lower().replace(" ", "") for c in BASE_COLS] + [
    f"day{i:02d}" for i in range(1, 31)
]
PLACEHOLDERS = ", ".join("?" * len(SQL_COLS))


def parse_float(text: str):
    try:
        return float(text)
    except (TypeError, ValueError):
        return None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit", type=int, default=0, help="limit rows for a smoke test")
    args = parser.parse_args()

    if not CSV_PATH.exists():
        sys.exit(f"CSV not found: {CSV_PATH}")
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)

    t0 = time.time()
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()

    text_cols = SQL_COLS[:10]
    num_cols = SQL_COLS[10:]
    cur.executescript(f"""
    DROP TABLE IF EXISTS videos;
    DROP TABLE IF EXISTS videos_fts;
    CREATE TABLE videos (
        {", ".join(f'"{c}" TEXT' for c in text_cols)},
        {", ".join(f'"{c}" REAL' for c in num_cols)}
    );
    CREATE INDEX idx_videos_category ON videos(category);
    CREATE INDEX idx_videos_day30 ON videos(day30);
    """)
    conn.commit()

    insert_sql = f"INSERT INTO videos VALUES ({PLACEHOLDERS})"
    batch, total, skipped = [], 0, 0
    with open(CSV_PATH, newline="", encoding="utf-8-sig") as f:
        reader = csv.reader(f)
        header = next(reader)
        col_index = {name: i for i, name in enumerate(header)}
        missing = [c for c in BASE_COLS + DAY_COLS if c not in col_index]
        if missing:
            sys.exit(f"missing columns: {missing}")
        base_idx = [col_index[c] for c in BASE_COLS]
        day_idx = [col_index[c] for c in DAY_COLS]

        for row in reader:
            if args.limit and total >= args.limit:
                break
            if len(row) != len(header):
                skipped += 1
                continue
            values = [row[i].strip() for i in base_idx] + [
                parse_float(row[i]) for i in day_idx
            ]
            batch.append(values)
            total += 1
            if len(batch) >= 20000:
                cur.executemany(insert_sql, batch)
                conn.commit()
                batch = []
                if total % 200000 == 0:
                    print(f"  {total:,} rows, {time.time() - t0:.0f}s", flush=True)
    if batch:
        cur.executemany(insert_sql, batch)
        conn.commit()

    print(f"index rows: {total:,} ({time.time() - t0:.0f}s), building FTS...", flush=True)
    cur.executescript("""
    CREATE VIRTUAL TABLE videos_fts USING fts5(
        video_id UNINDEXED, title, tags, description, tokenize='trigram'
    );
    INSERT INTO videos_fts(video_id, title, tags, description)
    SELECT video_id, title, tags, description FROM videos;
    """)
    conn.commit()

    count = cur.execute("SELECT COUNT(*) FROM videos").fetchone()[0]
    cats = cur.execute("SELECT COUNT(DISTINCT category) FROM videos").fetchone()[0]
    print(f"done: {count:,} rows, {cats} categories, {skipped:,} skipped, {time.time() - t0:.0f}s")
    conn.close()


if __name__ == "__main__":
    main()
