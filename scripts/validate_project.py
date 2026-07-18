#!/usr/bin/env python3
from pathlib import Path
import json
import sqlite3
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
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

for xml in ROOT.glob("app/src/main/**/*.xml"):
    ET.parse(xml)

manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
for token in [
    "android.intent.category.HOME",
    "android.intent.category.LAUNCHER",
    "android.app.device_admin",
]:
    if token not in manifest:
        raise SystemExit(f"Manifest is missing {token}")

db_path = ASSETS / "bsb.sqlite"
connection = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
try:
    integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]
    if integrity != "ok":
        raise SystemExit(f"SQLite integrity check failed: {integrity}")
    verse_count = connection.execute("SELECT COUNT(*) FROM verses").fetchone()[0]
    if verse_count != 31_102:
        raise SystemExit(f"Expected 31,102 BSB verses, found {verse_count}")
    duplicate_count = connection.execute(
        "SELECT COUNT(*) FROM (SELECT book_name, chapter, verse, COUNT(*) c "
        "FROM verses GROUP BY book_name, chapter, verse HAVING c > 1)"
    ).fetchone()[0]
    if duplicate_count:
        raise SystemExit(f"Bible database has {duplicate_count} duplicate references")
    available = {
        f"{book} {chapter}:{verse}"
        for book, chapter, verse in connection.execute(
            "SELECT book_name, chapter, verse FROM verses"
        )
    }
finally:
    connection.close()

plans = json.loads((ASSETS / "curated_daily_verses.json").read_text(encoding="utf-8"))
if plans.get("translation") != "BSB":
    raise SystemExit("curated_daily_verses.json must declare translation BSB")
readings = plans.get("readings", [])
if len(readings) < 365:
    raise SystemExit(f"Expected at least 365 curated daily readings, found {len(readings)}")

main_refs = []
for index, reading in enumerate(readings):
    main = reading.get("main")
    related = reading.get("related")
    themes = reading.get("themes")
    if (
        not isinstance(main, str)
        or not isinstance(related, list)
        or len(related) != 3
        or not isinstance(themes, list)
        or not themes
    ):
        raise SystemExit(f"Invalid curated daily reading at index {index}")
    missing_refs = [ref for ref in [main, *related] if ref not in available]
    if missing_refs:
        raise SystemExit(
            f"Curated reading {index} references missing BSB verses: {missing_refs}"
        )
    main_refs.append(main)

if len(set(main_refs)) != len(main_refs):
    raise SystemExit("Curated daily main references are not unique")

source = "\n".join(
    path.read_text(encoding="utf-8")
    for path in ROOT.glob("app/src/main/java/**/*.kt")
)
for token in [
    "Screen.CHAPTER",
    "BibleRepository.chapter",
    "dpm.lockNow()",
    "Screen.DAILY_VERSE",
    '"bsb.sqlite"',
]:
    if token not in source:
        raise SystemExit(f"Required source feature missing: {token}")

if "King James Version" in source or '"kjv.sqlite"' in source:
    raise SystemExit("Stale KJV source reference remains")

print(f"Validated {len(required)} required files.")
print("All Android XML resources are well formed.")
print(f"BSB SQLite database integrity: OK ({verse_count:,} verses).")
print(
    f"Curated daily readings: {len(readings)} unique main references; "
    "every reference resolves locally."
)
print("Full-chapter viewing, idle lock, and double-tap lock source checks: present.")
