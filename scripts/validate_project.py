#!/usr/bin/env python3
from pathlib import Path
import json
import sqlite3
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
DATABASE_PATH = ASSETS / "bsb.sqlite"
READINGS_PATH = ASSETS / "curated_daily_verses.json"

required = [
    "settings.gradle.kts",
    "build.gradle.kts",
    "gradle.properties",
    "gradle/libs.versions.toml",
    "gradlew",
    "gradlew.bat",
    "app/build.gradle.kts",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/assets/bsb.sqlite",
    "app/src/main/assets/curated_daily_verses.json",
    "app/src/main/java/com/joel/minimallauncher/MainActivity.kt",
    "app/src/main/java/com/joel/minimallauncher/DeviceAdminReceiver.kt",
    "app/src/main/java/com/joel/minimallauncher/ui/LauncherApp.kt",
    "app/src/main/java/com/joel/minimallauncher/ui/LauncherViewModel.kt",
    "app/src/main/java/com/joel/minimallauncher/verse/BibleRepository.kt",
    "app/src/main/java/com/joel/minimallauncher/verse/DailyVerseRepository.kt",
]

missing = [path for path in required if not (ROOT / path).is_file()]
if missing:
    raise SystemExit("Missing required files:\n" + "\n".join(missing))

for xml_path in ROOT.glob("app/src/main/**/*.xml"):
    ET.parse(xml_path)

manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
for token in (
    "android.intent.category.HOME",
    "android.intent.category.LAUNCHER",
    "android.app.device_admin",
):
    if token not in manifest:
        raise SystemExit(f"Manifest is missing {token}")

connection = sqlite3.connect(f"file:{DATABASE_PATH}?mode=ro", uri=True)
try:
    integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]
    if integrity != "ok":
        raise SystemExit(f"SQLite integrity check failed: {integrity}")

    table_names = {
        row[0]
        for row in connection.execute(
            "SELECT name FROM sqlite_master WHERE type = 'table'"
        )
    }
    required_tables = {"BSB_books", "BSB_verses"}
    missing_tables = required_tables - table_names
    if missing_tables:
        raise SystemExit(
            "Missing required BSB database tables: "
            + ", ".join(sorted(missing_tables))
        )

    expected_columns = {
        "BSB_books": {"id", "name"},
        "BSB_verses": {"id", "book_id", "chapter", "verse", "text"},
    }
    for table, expected in expected_columns.items():
        columns = {
            row[1] for row in connection.execute(f'PRAGMA table_info("{table}")')
        }
        missing_columns = expected - columns
        if missing_columns:
            raise SystemExit(
                f"{table} is missing columns: "
                + ", ".join(sorted(missing_columns))
            )

    book_count = connection.execute(
        "SELECT COUNT(*) FROM BSB_books"
    ).fetchone()[0]
    verse_count = connection.execute(
        "SELECT COUNT(*) FROM BSB_verses"
    ).fetchone()[0]

database_path = ROOT / "app/src/main/assets/bsb.sqlite"

connection = sqlite3.connect(database_path)

try:
    tables = {
        row[0]
        for row in connection.execute(
            "SELECT name FROM sqlite_master WHERE type = 'table'"
        )
    }

    required_tables = {"BSB_books", "BSB_verses"}
    missing_tables = required_tables - tables

    if missing_tables:
        raise SystemExit(
            "Missing required BSB database tables: "
            + ", ".join(sorted(missing_tables))
        )

    verse_count = connection.execute(
        "SELECT COUNT(*) FROM BSB_verses"
    ).fetchone()[0]

    referenced_book_count = connection.execute(
        "SELECT COUNT(DISTINCT book_id) FROM BSB_verses"
    ).fetchone()[0]

    distinct_book_name_count = connection.execute(
        """
        SELECT COUNT(DISTINCT b.name)
        FROM BSB_books b
        INNER JOIN BSB_verses v
            ON v.book_id = b.id
        """
    ).fetchone()[0]

    orphaned_verse_count = connection.execute(
        """
        SELECT COUNT(*)
        FROM BSB_verses v
        LEFT JOIN BSB_books b
            ON b.id = v.book_id
        WHERE b.id IS NULL
        """
    ).fetchone()[0]

    if verse_count < 31_000:
        raise SystemExit(
            f"Expected a complete Bible, found only {verse_count} verses"
        )

    if referenced_book_count != 66:
        raise SystemExit(
            "Expected verses from 66 Bible books, "
            f"found {referenced_book_count}"
        )

    if distinct_book_name_count != 66:
        raise SystemExit(
            "Expected 66 distinct referenced book names, "
            f"found {distinct_book_name_count}"
        )

    if orphaned_verse_count != 0:
        raise SystemExit(
            f"Found {orphaned_verse_count} verses with no matching book"
        )

    print(f"BSB database verses: {verse_count}")
    print(f"Referenced BSB books: {referenced_book_count}")
    print(f"Distinct referenced book names: {distinct_book_name_count}")
    print("Orphaned verses: 0")

finally:
    connection.close()
    
    if verse_count < 31_000:
        raise SystemExit(
            f"Expected a complete BSB Bible, found only {verse_count} verses"
        )

    duplicate_count = connection.execute(
        """
        SELECT COUNT(*)
        FROM (
            SELECT book_id, chapter, verse, COUNT(*) AS count
            FROM BSB_verses
            GROUP BY book_id, chapter, verse
            HAVING count > 1
        )
        """
    ).fetchone()[0]
    if duplicate_count:
        raise SystemExit(
            f"BSB database has {duplicate_count} duplicate verse references"
        )

    available = {
        f"{book} {chapter}:{verse}"
        for book, chapter, verse in connection.execute(
            """
            SELECT b.name, v.chapter, v.verse
            FROM BSB_verses AS v
            INNER JOIN BSB_books AS b ON b.id = v.book_id
            """
        )
    }
finally:
    connection.close()

plans = json.loads(READINGS_PATH.read_text(encoding="utf-8"))
if plans.get("translation") != "BSB":
    raise SystemExit("curated_daily_verses.json must declare translation BSB")

readings = plans.get("readings", [])
if len(readings) < 365:
    raise SystemExit(
        f"Expected at least 365 curated daily readings, found {len(readings)}"
    )

main_references = []
for index, reading in enumerate(readings):
    main = reading.get("main")
    related = reading.get("related")
    themes = reading.get("themes")

    if not isinstance(main, str):
        raise SystemExit(f"Reading {index} has an invalid main reference")
    if not isinstance(related, list) or len(related) != 3:
        raise SystemExit(f"Reading {index} must contain exactly three related verses")
    if not isinstance(themes, list) or not themes:
        raise SystemExit(f"Reading {index} must contain at least one theme")

    missing_references = [
        reference
        for reference in [main, *related]
        if reference not in available
    ]
    if missing_references:
        raise SystemExit(
            f"Reading {index} references missing BSB verses: "
            f"{missing_references}"
        )

    main_references.append(main)

if len(set(main_references)) != len(main_references):
    raise SystemExit("Curated daily main references are not unique")

source = "\n".join(
    path.read_text(encoding="utf-8")
    for path in ROOT.glob("app/src/main/java/**/*.kt")
)
for token in (
    "Screen.CHAPTER",
    "BibleRepository.chapter",
    "dpm.lockNow()",
    "Screen.DAILY_VERSE",
    '"bsb.sqlite"',
    '"BSB_books"',
    '"BSB_verses"',
):
    if token not in source:
        raise SystemExit(f"Required source feature missing: {token}")

for stale_token in ("King James Version", '"kjv.sqlite"', 'FROM verses'):
    if stale_token in source:
        raise SystemExit(f"Stale Bible implementation remains: {stale_token}")

print(f"Validated {len(required)} required files.")
print("All Android XML resources are well formed.")
print(
    f"BSB SQLite database integrity: OK "
    f"({book_count} books, {verse_count:,} verses)."
)
print(
    f"Curated daily readings: {len(readings)} unique main references; "
    "every reference resolves locally."
)
print("Full-chapter viewing and double-tap lock source checks: present.")
