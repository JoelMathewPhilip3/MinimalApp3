#!/usr/bin/env python3
from pathlib import Path
import json
import sqlite3
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
DATABASE_PATH = ASSETS / "bsb.sqlite"
READINGS_PATH = ASSETS / "curated_daily_verses.json"

REQUIRED_FILES = [
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


def fail(message: str) -> None:
    raise SystemExit(message)


missing_files = [
    relative_path
    for relative_path in REQUIRED_FILES
    if not (ROOT / relative_path).is_file()
]
if missing_files:
    fail("Missing required files:\n" + "\n".join(missing_files))


# Validate every Android XML file.
for xml_path in ROOT.glob("app/src/main/**/*.xml"):
    try:
        ET.parse(xml_path)
    except ET.ParseError as exc:
        fail(f"Malformed XML file {xml_path.relative_to(ROOT)}: {exc}")


# Validate required manifest declarations.
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(
    encoding="utf-8"
)
for token in (
    "android.intent.category.HOME",
    "android.intent.category.LAUNCHER",
    "android.app.device_admin",
):
    if token not in manifest:
        fail(f"Manifest is missing {token}")


# Validate the BSB SQLite database once, using a read-only connection.
connection = sqlite3.connect(
    f"file:{DATABASE_PATH.as_posix()}?mode=ro",
    uri=True,
)

try:
    integrity_result = connection.execute(
        "PRAGMA integrity_check"
    ).fetchone()

    if not integrity_result or integrity_result[0] != "ok":
        value = integrity_result[0] if integrity_result else "no result"
        fail(f"SQLite integrity check failed: {value}")

    table_names = {
        row[0]
        for row in connection.execute(
            "SELECT name FROM sqlite_master WHERE type = 'table'"
        )
    }

    required_tables = {"BSB_books", "BSB_verses"}
    missing_tables = required_tables - table_names
    if missing_tables:
        fail(
            "Missing required BSB database tables: "
            + ", ".join(sorted(missing_tables))
        )

    expected_columns = {
        "BSB_books": {"id", "name"},
        "BSB_verses": {
            "id",
            "book_id",
            "chapter",
            "verse",
            "text",
        },
    }

    for table_name, expected in expected_columns.items():
        actual = {
            row[1]
            for row in connection.execute(
                f'PRAGMA table_info("{table_name}")'
            )
        }
        missing_columns = expected - actual
        if missing_columns:
            fail(
                f"{table_name} is missing columns: "
                + ", ".join(sorted(missing_columns))
            )

    raw_book_row_count = connection.execute(
        "SELECT COUNT(*) FROM BSB_books"
    ).fetchone()[0]

    verse_count = connection.execute(
        "SELECT COUNT(*) FROM BSB_verses"
    ).fetchone()[0]

    referenced_book_count = connection.execute(
        "SELECT COUNT(DISTINCT book_id) FROM BSB_verses"
    ).fetchone()[0]

    distinct_referenced_book_names = connection.execute(
        """
        SELECT COUNT(DISTINCT b.name)
        FROM BSB_books AS b
        INNER JOIN BSB_verses AS v
            ON v.book_id = b.id
        """
    ).fetchone()[0]

    orphaned_verse_count = connection.execute(
        """
        SELECT COUNT(*)
        FROM BSB_verses AS v
        LEFT JOIN BSB_books AS b
            ON b.id = v.book_id
        WHERE b.id IS NULL
        """
    ).fetchone()[0]

    duplicate_reference_count = connection.execute(
        """
        SELECT COUNT(*)
        FROM (
            SELECT
                book_id,
                chapter,
                verse,
                COUNT(*) AS occurrence_count
            FROM BSB_verses
            GROUP BY book_id, chapter, verse
            HAVING COUNT(*) > 1
        )
        """
    ).fetchone()[0]

    if verse_count < 31_000:
        fail(
            "Expected a complete BSB Bible, "
            f"found only {verse_count:,} verses"
        )

    if referenced_book_count != 66:
        fail(
            "Expected verses from 66 Bible books, "
            f"found {referenced_book_count}"
        )

    if distinct_referenced_book_names != 66:
        fail(
            "Expected 66 distinct referenced book names, "
            f"found {distinct_referenced_book_names}"
        )

    if orphaned_verse_count:
        fail(
            f"Found {orphaned_verse_count} verses "
            "with no matching BSB_books row"
        )

    if duplicate_reference_count:
        fail(
            "BSB database has "
            f"{duplicate_reference_count} duplicate verse references"
        )

    available_references = {
        f"{book_name} {chapter}:{verse}"
        for book_name, chapter, verse in connection.execute(
            """
            SELECT
                b.name,
                v.chapter,
                v.verse
            FROM BSB_verses AS v
            INNER JOIN BSB_books AS b
                ON b.id = v.book_id
            """
        )
    }

finally:
    connection.close()


# Validate the curated Daily Verse file.
try:
    plans = json.loads(
        READINGS_PATH.read_text(encoding="utf-8")
    )
except json.JSONDecodeError as exc:
    fail(f"curated_daily_verses.json is invalid JSON: {exc}")

if plans.get("translation") != "BSB":
    fail(
        "curated_daily_verses.json must declare "
        '"translation": "BSB"'
    )

readings = plans.get("readings")
if not isinstance(readings, list):
    fail(
        "curated_daily_verses.json must contain "
        'a "readings" array'
    )

if len(readings) < 365:
    fail(
        "Expected at least 365 curated daily readings, "
        f"found {len(readings)}"
    )

main_references: list[str] = []

for index, reading in enumerate(readings):
    if not isinstance(reading, dict):
        fail(f"Reading {index} must be a JSON object")

    main = reading.get("main")
    related = reading.get("related")
    themes = reading.get("themes")

    if not isinstance(main, str) or not main.strip():
        fail(f"Reading {index} has an invalid main reference")

    if (
        not isinstance(related, list)
        or len(related) != 3
        or not all(
            isinstance(reference, str) and reference.strip()
            for reference in related
        )
    ):
        fail(
            f"Reading {index} must contain exactly "
            "three valid related verse references"
        )

    if (
        not isinstance(themes, list)
        or not themes
        or not all(
            isinstance(theme, str) and theme.strip()
            for theme in themes
        )
    ):
        fail(
            f"Reading {index} must contain at least "
            "one valid theme"
        )

    references = [main, *related]
    unresolved = [
        reference
        for reference in references
        if reference not in available_references
    ]

    if unresolved:
        fail(
            f"Reading {index} references missing BSB verses: "
            + ", ".join(unresolved)
        )

    main_references.append(main)

if len(set(main_references)) != len(main_references):
    fail("Curated daily main references are not unique")


# Validate critical Kotlin implementation markers.
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
        fail(f"Required source feature missing: {token}")

for stale_token in (
    "King James Version",
    '"kjv.sqlite"',
    "FROM verses",
):
    if stale_token in source:
        fail(
            "Stale Bible implementation remains: "
            f"{stale_token}"
        )


print(f"Validated {len(REQUIRED_FILES)} required files.")
print("All Android XML resources are well formed.")
print(
    "BSB SQLite database integrity: OK "
    f"({raw_book_row_count} raw book rows, "
    f"{referenced_book_count} referenced books, "
    f"{verse_count:,} verses)."
)
print(
    f"Curated daily readings: {len(readings)} "
    "unique main references; every reference resolves locally."
)
print(
    "Full-chapter viewing and double-tap lock "
    "source checks: present."
)
