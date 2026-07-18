#!/usr/bin/env python3
from pathlib import Path
import json
import sqlite3
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
DATABASE_PATH = ASSETS / "bsb.sqlite"
READINGS_PATH = ASSETS / "curated_daily_verses.json"
EXPECTED_DATABASE_VERSION = 2
EXPECTED_VERSE_COUNT = 31_086

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
    "scripts/generate_bsb_database.py",
    ".github/workflows/build-apk.yml",
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

for xml_path in ROOT.glob("app/src/main/**/*.xml"):
    try:
        ET.parse(xml_path)
    except ET.ParseError as exc:
        fail(f"Malformed XML file {xml_path.relative_to(ROOT)}: {exc}")

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

connection = sqlite3.connect(
    f"file:{DATABASE_PATH.as_posix()}?mode=ro",
    uri=True,
)

try:
    integrity_result = connection.execute("PRAGMA integrity_check").fetchone()
    if not integrity_result or integrity_result[0] != "ok":
        value = integrity_result[0] if integrity_result else "no result"
        fail(f"SQLite integrity check failed: {value}")

    database_version = connection.execute("PRAGMA user_version").fetchone()[0]
    if database_version != EXPECTED_DATABASE_VERSION:
        fail(
            f"Expected BSB database version {EXPECTED_DATABASE_VERSION}, "
            f"found {database_version}. The clean generator did not run."
        )

    table_names = {
        row[0]
        for row in connection.execute(
            "SELECT name FROM sqlite_master WHERE type = 'table'"
        )
    }
    required_tables = {"verses", "metadata"}
    missing_tables = required_tables - table_names
    if missing_tables:
        fail(
            "Generated BSB database is missing tables: "
            + ", ".join(sorted(missing_tables))
        )

    # These are from the incompatible Scrollmapper database that caused the
    # repeated validation failures. They must not be used by this project.
    stale_tables = {"BSB_books", "BSB_verses"} & table_names
    if stale_tables:
        fail(
            "The incompatible Scrollmapper BSB database is still present: "
            + ", ".join(sorted(stale_tables))
        )

    expected_columns = {
        "id",
        "book_number",
        "book_name",
        "chapter",
        "verse",
        "text",
    }
    actual_columns = {
        row[1] for row in connection.execute('PRAGMA table_info("verses")')
    }
    missing_columns = expected_columns - actual_columns
    if missing_columns:
        fail(
            "Generated verses table is missing columns: "
            + ", ".join(sorted(missing_columns))
        )

    verse_count = connection.execute("SELECT COUNT(*) FROM verses").fetchone()[0]
    book_count = connection.execute(
        "SELECT COUNT(DISTINCT book_name) FROM verses"
    ).fetchone()[0]
    duplicate_count = connection.execute(
        """
        SELECT COUNT(*)
        FROM (
            SELECT book_name, chapter, verse
            FROM verses
            GROUP BY book_name, chapter, verse
            HAVING COUNT(*) > 1
        )
        """
    ).fetchone()[0]
    blank_text_count = connection.execute(
        "SELECT COUNT(*) FROM verses WHERE TRIM(text) = ''"
    ).fetchone()[0]

    if verse_count != EXPECTED_VERSE_COUNT:
        fail(
            f"Expected {EXPECTED_VERSE_COUNT:,} BSB verses, "
            f"found {verse_count:,}"
        )
    if book_count != 66:
        fail(f"Expected 66 BSB books, found {book_count}")
    if duplicate_count:
        fail(f"Generated BSB database has {duplicate_count} duplicate references")
    if blank_text_count:
        fail(f"Generated BSB database has {blank_text_count} blank verses")

    available_references = {
        f"{book_name} {chapter}:{verse}"
        for book_name, chapter, verse in connection.execute(
            "SELECT book_name, chapter, verse FROM verses"
        )
    }

finally:
    connection.close()

try:
    plans = json.loads(READINGS_PATH.read_text(encoding="utf-8"))
except json.JSONDecodeError as exc:
    fail(f"curated_daily_verses.json is invalid JSON: {exc}")

if plans.get("translation") != "BSB":
    fail('curated_daily_verses.json must declare "translation": "BSB"')

readings = plans.get("readings")
if not isinstance(readings, list):
    fail('curated_daily_verses.json must contain a "readings" array')
if len(readings) < 365:
    fail(
        f"Expected at least 365 curated daily readings, found {len(readings)}"
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
            f"Reading {index} must contain exactly three valid "
            "related references"
        )
    if (
        not isinstance(themes, list)
        or not themes
        or not all(
            isinstance(theme, str) and theme.strip()
            for theme in themes
        )
    ):
        fail(f"Reading {index} must contain at least one valid theme")

    unresolved = [
        reference
        for reference in [main, *related]
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
    '"verses"',
):
    if token not in source:
        fail(f"Required source feature missing: {token}")

for stale_token in (
    "King James Version",
    '"kjv.sqlite"',
    '"BSB_books"',
    '"BSB_verses"',
):
    if stale_token in source:
        fail(f"Stale or incompatible Bible implementation remains: {stale_token}")

print(f"Validated {len(REQUIRED_FILES)} required files")
print("All Android XML resources are well formed")
print(
    "Clean BSB SQLite database: OK "
    f"({book_count} books, {verse_count:,} unique verses, "
    f"version {database_version})"
)
print(
    f"Curated daily readings: {len(readings)} unique main references; "
    "every main and related reference resolves locally"
)
print("Full-chapter viewing and double-tap lock source checks: present")
