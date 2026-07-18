#!/usr/bin/env python3
"""Build the offline Berean Standard Bible SQLite asset from official USFM files."""

from __future__ import annotations

import argparse
import re
import sqlite3
import tempfile
import zipfile
from pathlib import Path

BOOKS = {
    "GEN": (1, "Genesis"), "EXO": (2, "Exodus"), "LEV": (3, "Leviticus"),
    "NUM": (4, "Numbers"), "DEU": (5, "Deuteronomy"), "JOS": (6, "Joshua"),
    "JDG": (7, "Judges"), "RUT": (8, "Ruth"), "1SA": (9, "1 Samuel"),
    "2SA": (10, "2 Samuel"), "1KI": (11, "1 Kings"), "2KI": (12, "2 Kings"),
    "1CH": (13, "1 Chronicles"), "2CH": (14, "2 Chronicles"), "EZR": (15, "Ezra"),
    "NEH": (16, "Nehemiah"), "EST": (17, "Esther"), "JOB": (18, "Job"),
    "PSA": (19, "Psalms"), "PRO": (20, "Proverbs"), "ECC": (21, "Ecclesiastes"),
    "SNG": (22, "Song of Solomon"), "ISA": (23, "Isaiah"), "JER": (24, "Jeremiah"),
    "LAM": (25, "Lamentations"), "EZK": (26, "Ezekiel"), "DAN": (27, "Daniel"),
    "HOS": (28, "Hosea"), "JOL": (29, "Joel"), "AMO": (30, "Amos"),
    "OBA": (31, "Obadiah"), "JON": (32, "Jonah"), "MIC": (33, "Micah"),
    "NAM": (34, "Nahum"), "HAB": (35, "Habakkuk"), "ZEP": (36, "Zephaniah"),
    "HAG": (37, "Haggai"), "ZEC": (38, "Zechariah"), "MAL": (39, "Malachi"),
    "MAT": (40, "Matthew"), "MRK": (41, "Mark"), "LUK": (42, "Luke"),
    "JHN": (43, "John"), "ACT": (44, "Acts"), "ROM": (45, "Romans"),
    "1CO": (46, "1 Corinthians"), "2CO": (47, "2 Corinthians"),
    "GAL": (48, "Galatians"), "EPH": (49, "Ephesians"), "PHP": (50, "Philippians"),
    "COL": (51, "Colossians"), "1TH": (52, "1 Thessalonians"),
    "2TH": (53, "2 Thessalonians"), "1TI": (54, "1 Timothy"),
    "2TI": (55, "2 Timothy"), "TIT": (56, "Titus"), "PHM": (57, "Philemon"),
    "HEB": (58, "Hebrews"), "JAS": (59, "James"), "1PE": (60, "1 Peter"),
    "2PE": (61, "2 Peter"), "1JN": (62, "1 John"), "2JN": (63, "2 John"),
    "3JN": (64, "3 John"), "JUD": (65, "Jude"), "REV": (66, "Revelation"),
}

NOTE_BLOCK = re.compile(r"\\(?:f|fe|x)\s.*?\\(?:f|fe|x)\*", re.DOTALL)
WORD_MARKER = re.compile(r"\\\+?w\s+([^|\\]+?)(?:\|[^\\]*?)?\\\+?w\*")
MILESTONE = re.compile(r"\\[a-z0-9+]+-[se]\s+[^\\]*?\\\*", re.IGNORECASE)
ANY_MARKER = re.compile(r"\\[a-z0-9+]+\*?(?:\s+)?", re.IGNORECASE)
SPACE = re.compile(r"\s+")

def clean_usfm(value: str) -> str:
    value = NOTE_BLOCK.sub(" ", value)
    value = WORD_MARKER.sub(lambda m: m.group(1), value)
    value = MILESTONE.sub(" ", value)
    value = value.replace("~", " ")
    value = ANY_MARKER.sub(" ", value)
    value = value.replace("|", " ")
    return SPACE.sub(" ", value).strip()

def book_code(text: str, path: Path) -> str:
    match = re.search(r"(?m)^\\id\s+([1-3A-Z]{3})\b", text)
    if match and match.group(1).upper() in BOOKS:
        return match.group(1).upper()
    upper = path.stem.upper()
    for code in BOOKS:
        if re.search(rf"(^|[^A-Z0-9]){re.escape(code)}([^A-Z0-9]|$)", upper):
            return code
    raise ValueError(f"Could not identify canonical book for {path}")

def parse_file(path: Path):
    raw = path.read_text(encoding="utf-8-sig", errors="replace")
    code = book_code(raw, path)
    book_number, book_name = BOOKS[code]
    chapter = None
    current_verse = None
    current_parts: list[str] = []

    def flush():
        nonlocal current_verse, current_parts
        if chapter is not None and current_verse is not None:
            cleaned = clean_usfm(" ".join(current_parts))
            if cleaned:
                yield (book_number, book_name, chapter, current_verse, cleaned)
        current_verse = None
        current_parts = []

    for line in raw.splitlines():
        line = line.strip()
        if not line:
            continue

        chapter_match = re.match(r"^\\c\s+(\d+)\b", line)
        if chapter_match:
            yield from flush()
            chapter = int(chapter_match.group(1))
            continue

        verse_match = re.match(r"^\\v\s+(\d+)(?:[a-z])?\s*(.*)$", line)
        if verse_match:
            yield from flush()
            if chapter is None:
                raise ValueError(f"Verse appeared before chapter in {path}: {line}")
            current_verse = int(verse_match.group(1))
            current_parts = [verse_match.group(2)]
            continue

        if current_verse is not None:
            # Continue verse text across poetry/paragraph lines, but ignore headings and metadata.
            if line.startswith(("\\s", "\\r", "\\d", "\\sp", "\\cl", "\\cp", "\\id", "\\ide", "\\h", "\\toc")):
                continue
            current_parts.append(line)

    yield from flush()

def build_database(input_zip: Path, output: Path) -> None:
    if not input_zip.is_file():
        raise SystemExit(f"USFM ZIP not found: {input_zip}")

    with tempfile.TemporaryDirectory(prefix="bsb_usfm_") as temp:
        temp_dir = Path(temp)
        with zipfile.ZipFile(input_zip) as archive:
            archive.extractall(temp_dir)

        files = sorted(
            p for p in temp_dir.rglob("*")
            if p.is_file() and p.suffix.lower() in {".usfm", ".sfm", ".usx", ".txt"}
        )
        # Prefer actual USFM/SFM files when the ZIP also contains readme text.
        usfm_files = [p for p in files if p.suffix.lower() in {".usfm", ".sfm"}]
        if usfm_files:
            files = usfm_files
        if not files:
            raise SystemExit("No USFM files were found in the downloaded archive.")

        rows = []
        seen_books = set()
        for file in files:
            try:
                parsed = list(parse_file(file))
            except ValueError:
                continue
            if parsed:
                rows.extend(parsed)
                seen_books.add(parsed[0][0])

        if len(seen_books) != 66:
            raise SystemExit(f"Expected 66 canonical books, parsed {len(seen_books)}.")
        if len(rows) != 31_102:
            raise SystemExit(f"Expected 31,102 BSB verses, parsed {len(rows)}.")

        references = {(r[1], r[2], r[3]) for r in rows}
        if len(references) != len(rows):
            raise SystemExit("Duplicate Bible references were produced by the USFM parser.")

        output.parent.mkdir(parents=True, exist_ok=True)
        output.unlink(missing_ok=True)
        connection = sqlite3.connect(output)
        try:
            connection.executescript("""
                PRAGMA journal_mode=OFF;
                PRAGMA synchronous=OFF;
                CREATE TABLE verses (
                    id INTEGER PRIMARY KEY,
                    book_number INTEGER NOT NULL,
                    book_name TEXT NOT NULL,
                    chapter INTEGER NOT NULL,
                    verse INTEGER NOT NULL,
                    text TEXT NOT NULL,
                    UNIQUE(book_name, chapter, verse)
                );
                CREATE INDEX idx_verses_reference
                    ON verses(book_name, chapter, verse);
                CREATE INDEX idx_verses_canonical
                    ON verses(book_number, chapter, verse);
            """)
            connection.executemany(
                "INSERT INTO verses(book_number, book_name, chapter, verse, text) VALUES (?, ?, ?, ?, ?)",
                rows,
            )
            connection.commit()
            integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]
            if integrity != "ok":
                raise SystemExit(f"Generated SQLite database failed integrity check: {integrity}")
        finally:
            connection.close()

    print(f"Generated {output} with {len(rows):,} Berean Standard Bible verses.")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path, help="Official engbsb_usfm.zip file")
    parser.add_argument("--output", required=True, type=Path, help="Output bsb.sqlite path")
    args = parser.parse_args()
    build_database(args.input, args.output)

if __name__ == "__main__":
    main()
