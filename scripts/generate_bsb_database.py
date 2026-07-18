#!/usr/bin/env python3
"""Generate a clean offline Berean Standard Bible SQLite asset.

Input: the official public-domain engbsb_usfm.zip archive from eBible.org.
Output: app/src/main/assets/bsb.sqlite with one row per BSB verse record.
"""

from __future__ import annotations

import argparse
import re
import sqlite3
import tempfile
import zipfile
from pathlib import Path
from typing import Iterator

DATABASE_VERSION = 2
EXPECTED_VERSE_COUNT = 31_086

BOOKS: dict[str, tuple[int, str]] = {
    "GEN": (1, "Genesis"),
    "EXO": (2, "Exodus"),
    "LEV": (3, "Leviticus"),
    "NUM": (4, "Numbers"),
    "DEU": (5, "Deuteronomy"),
    "JOS": (6, "Joshua"),
    "JDG": (7, "Judges"),
    "RUT": (8, "Ruth"),
    "1SA": (9, "1 Samuel"),
    "2SA": (10, "2 Samuel"),
    "1KI": (11, "1 Kings"),
    "2KI": (12, "2 Kings"),
    "1CH": (13, "1 Chronicles"),
    "2CH": (14, "2 Chronicles"),
    "EZR": (15, "Ezra"),
    "NEH": (16, "Nehemiah"),
    "EST": (17, "Esther"),
    "JOB": (18, "Job"),
    "PSA": (19, "Psalms"),
    "PRO": (20, "Proverbs"),
    "ECC": (21, "Ecclesiastes"),
    "SNG": (22, "Song of Solomon"),
    "ISA": (23, "Isaiah"),
    "JER": (24, "Jeremiah"),
    "LAM": (25, "Lamentations"),
    "EZK": (26, "Ezekiel"),
    "DAN": (27, "Daniel"),
    "HOS": (28, "Hosea"),
    "JOL": (29, "Joel"),
    "AMO": (30, "Amos"),
    "OBA": (31, "Obadiah"),
    "JON": (32, "Jonah"),
    "MIC": (33, "Micah"),
    "NAM": (34, "Nahum"),
    "HAB": (35, "Habakkuk"),
    "ZEP": (36, "Zephaniah"),
    "HAG": (37, "Haggai"),
    "ZEC": (38, "Zechariah"),
    "MAL": (39, "Malachi"),
    "MAT": (40, "Matthew"),
    "MRK": (41, "Mark"),
    "LUK": (42, "Luke"),
    "JHN": (43, "John"),
    "ACT": (44, "Acts"),
    "ROM": (45, "Romans"),
    "1CO": (46, "1 Corinthians"),
    "2CO": (47, "2 Corinthians"),
    "GAL": (48, "Galatians"),
    "EPH": (49, "Ephesians"),
    "PHP": (50, "Philippians"),
    "COL": (51, "Colossians"),
    "1TH": (52, "1 Thessalonians"),
    "2TH": (53, "2 Thessalonians"),
    "1TI": (54, "1 Timothy"),
    "2TI": (55, "2 Timothy"),
    "TIT": (56, "Titus"),
    "PHM": (57, "Philemon"),
    "HEB": (58, "Hebrews"),
    "JAS": (59, "James"),
    "1PE": (60, "1 Peter"),
    "2PE": (61, "2 Peter"),
    "1JN": (62, "1 John"),
    "2JN": (63, "2 John"),
    "3JN": (64, "3 John"),
    "JUD": (65, "Jude"),
    "REV": (66, "Revelation"),
}

NOTE_BLOCK = re.compile(r"\\(?:f|fe|x)\s.*?\\(?:f|fe|x)\*", re.DOTALL)
WORD_MARKER = re.compile(r"\\\+?w\s+([^|\\]+?)(?:\|[^\\]*?)?\\\+?w\*")
MILESTONE = re.compile(r"\\[a-z0-9+]+-[se]\s+[^\\]*?\\\*", re.IGNORECASE)
ANY_MARKER = re.compile(r"\\[a-z0-9+]+\*?(?:\s+)?", re.IGNORECASE)
SPACE = re.compile(r"\s+")


def fail(message: str) -> None:
    raise SystemExit(message)


def clean_usfm(value: str) -> str:
    value = NOTE_BLOCK.sub(" ", value)
    value = WORD_MARKER.sub(lambda match: match.group(1), value)
    value = MILESTONE.sub(" ", value)
    value = value.replace("~", " ")
    value = ANY_MARKER.sub(" ", value)
    value = value.replace("|", " ")
    return SPACE.sub(" ", value).strip()


def identify_book(raw: str, path: Path) -> str:
    match = re.search(r"(?m)^\\id\s+([1-3A-Z]{3})\b", raw)
    if match:
        code = match.group(1).upper()
        if code in BOOKS:
            return code

    stem = path.stem.upper()
    for code in BOOKS:
        if re.search(rf"(^|[^A-Z0-9]){re.escape(code)}([^A-Z0-9]|$)", stem):
            return code

    raise ValueError(f"Could not identify canonical book for {path}")


def parse_usfm_file(path: Path) -> Iterator[tuple[int, str, int, int, str]]:
    raw = path.read_text(encoding="utf-8-sig", errors="replace")
    code = identify_book(raw, path)
    book_number, book_name = BOOKS[code]

    chapter: int | None = None
    current_verse: int | None = None
    current_parts: list[str] = []

    def flush() -> tuple[int, str, int, int, str] | None:
        nonlocal current_verse, current_parts
        result = None
        if chapter is not None and current_verse is not None:
            text = clean_usfm(" ".join(current_parts))
            if text:
                result = (
                    book_number,
                    book_name,
                    chapter,
                    current_verse,
                    text,
                )
        current_verse = None
        current_parts = []
        return result

    ignored_prefixes = (
        "\\s",
        "\\r",
        "\\d",
        "\\sp",
        "\\cl",
        "\\cp",
        "\\id",
        "\\ide",
        "\\h",
        "\\toc",
        "\\mt",
        "\\is",
        "\\ip",
        "\\rem",
    )

    for raw_line in raw.splitlines():
        line = raw_line.strip()
        if not line:
            continue

        chapter_match = re.match(r"^\\c\s+(\d+)\b", line)
        if chapter_match:
            previous = flush()
            if previous is not None:
                yield previous
            chapter = int(chapter_match.group(1))
            continue

        # BSB contains a small number of bridged verse labels such as 8-9.
        # The BSB source counts the bridge as one verse record. Store it under
        # the first verse number while stripping the range from the text.
        verse_match = re.match(
            r"^\\v\s+(\d+)(?:[a-z])?(?:-\d+(?:[a-z])?)?\s*(.*)$",
            line,
        )
        if verse_match:
            previous = flush()
            if previous is not None:
                yield previous
            if chapter is None:
                raise ValueError(f"Verse appeared before chapter in {path}: {line}")
            current_verse = int(verse_match.group(1))
            current_parts = [verse_match.group(2)]
            continue

        if current_verse is not None and not line.startswith(ignored_prefixes):
            current_parts.append(line)

    previous = flush()
    if previous is not None:
        yield previous


def build_database(input_zip: Path, output: Path) -> None:
    if not input_zip.is_file():
        fail(f"BSB USFM ZIP was not found: {input_zip}")

    with tempfile.TemporaryDirectory(prefix="bsb_usfm_") as temporary_directory:
        extracted = Path(temporary_directory)
        try:
            with zipfile.ZipFile(input_zip) as archive:
                archive.extractall(extracted)
        except zipfile.BadZipFile as exc:
            fail(f"Downloaded BSB archive is not a valid ZIP: {exc}")

        candidates = sorted(
            path
            for path in extracted.rglob("*")
            if path.is_file()
            and path.suffix.lower() in {".usfm", ".sfm", ".txt"}
        )
        preferred = [
            path for path in candidates if path.suffix.lower() in {".usfm", ".sfm"}
        ]
        if preferred:
            candidates = preferred
        if not candidates:
            fail("No USFM/SFM Bible files were found in the downloaded archive")

        rows: list[tuple[int, str, int, int, str]] = []
        parsed_books: set[int] = set()

        for path in candidates:
            try:
                parsed = list(parse_usfm_file(path))
            except ValueError:
                # The archive can contain documentation text. Only canonical
                # Bible files successfully identified by their USFM ID are used.
                continue
            if parsed:
                rows.extend(parsed)
                parsed_books.add(parsed[0][0])

    rows.sort(key=lambda row: (row[0], row[2], row[3]))
    references = {(row[1], row[2], row[3]) for row in rows}

    if len(parsed_books) != 66:
        fail(f"Expected 66 canonical books, parsed {len(parsed_books)}")
    if len(rows) != EXPECTED_VERSE_COUNT:
        fail(
            f"Expected {EXPECTED_VERSE_COUNT:,} BSB verses, "
            f"parsed {len(rows):,}"
        )
    if len(references) != EXPECTED_VERSE_COUNT:
        fail("The generated BSB data contains duplicate verse references")
    if any(not row[4].strip() for row in rows):
        fail("The generated BSB data contains blank verse text")

    output.parent.mkdir(parents=True, exist_ok=True)
    output.unlink(missing_ok=True)

    connection = sqlite3.connect(output)
    try:
        connection.executescript(
            f"""
            PRAGMA journal_mode=OFF;
            PRAGMA synchronous=OFF;
            PRAGMA user_version={DATABASE_VERSION};

            CREATE TABLE verses (
                id INTEGER PRIMARY KEY,
                book_number INTEGER NOT NULL,
                book_name TEXT NOT NULL,
                chapter INTEGER NOT NULL,
                verse INTEGER NOT NULL,
                text TEXT NOT NULL,
                UNIQUE(book_name, chapter, verse)
            );

            CREATE TABLE metadata (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            );

            CREATE INDEX idx_verses_reference
                ON verses(book_name, chapter, verse);
            CREATE INDEX idx_verses_canonical
                ON verses(book_number, chapter, verse);
            """
        )

        connection.executemany(
            """
            INSERT INTO verses(
                book_number,
                book_name,
                chapter,
                verse,
                text
            ) VALUES (?, ?, ?, ?, ?)
            """,
            rows,
        )

        connection.executemany(
            "INSERT INTO metadata(key, value) VALUES (?, ?)",
            [
                ("translation", "BSB"),
                ("title", "Berean Standard Bible"),
                ("source", "eBible.org engbsb_usfm.zip"),
                ("license", "Public Domain / CC0"),
                ("verse_record_count", str(EXPECTED_VERSE_COUNT)),
                ("database_version", str(DATABASE_VERSION)),
            ],
        )

        connection.commit()

        integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]
        stored_count = connection.execute("SELECT COUNT(*) FROM verses").fetchone()[0]
        if integrity != "ok":
            fail(f"Generated SQLite database failed integrity check: {integrity}")
        if stored_count != EXPECTED_VERSE_COUNT:
            fail(
                f"Generated database stored {stored_count:,} verses; "
                f"expected {EXPECTED_VERSE_COUNT:,}"
            )
    finally:
        connection.close()

    print(
        f"Generated {output} with {EXPECTED_VERSE_COUNT:,} "
        "Berean Standard Bible verse records"
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--input",
        required=True,
        type=Path,
        help="Official engbsb_usfm.zip file",
    )
    parser.add_argument(
        "--output",
        required=True,
        type=Path,
        help="Output app/src/main/assets/bsb.sqlite path",
    )
    arguments = parser.parse_args()
    build_database(arguments.input, arguments.output)


if __name__ == "__main__":
    main()
