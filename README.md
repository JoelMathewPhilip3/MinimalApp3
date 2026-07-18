# Joel Minimal Launcher V8

A private, battery-conscious Android home launcher with a Daily Verse home screen and a complete offline King James Bible.

## Daily Bible home screen

- Daily Verse is the true launcher Home page.
- Pressing Android Home returns to Daily Verse.
- Swipe left or tap **Open favourites** to reach the favourites page.
- One main reading plan is selected per calendar day.
- Three related morning verses can be shown or hidden.
- The previous seven days remain available.
- Tap the main verse or any related verse to read its complete chapter.
- The full KJV is bundled locally in a read-only SQLite database.
- All 31,102 KJV verses are available offline.
- The three-year plan contains 1,095 unique main references.
- The app uses every plan once before the 1,095-day cycle repeats.

The Daily Verse page does not use internet access, a midnight alarm, notifications, WorkManager, or a persistent background service. The next reading is calculated when the launcher is opened on a new calendar day.

## Launcher features

- Ordered favourites
- Searchable installed-app list
- Minimal Mode with deliberate hold-to-open access
- Long-press app actions: open, add/remove favourite, app information
- Double-tap empty space to lock the screen using optional Device Administrator access
- Larger text, high contrast, reduced gesture dependence, and optional haptics
- Cached app discovery
- Normal app icon plus selectable Android Home activity
- No internet permission
- No ads, analytics, account, or cloud backend

## Bible assets

- `app/src/main/assets/kjv.sqlite` — complete KJV, 31,102 verses
- `app/src/main/assets/daily_reading_refs.json` — 1,095 reference-only daily reading plans

Verse text is stored once in SQLite. The daily plans contain references only, avoiding duplicate verse text and allowing full-chapter reading.

## Build using GitHub Actions

1. Upload the contents of this folder to a GitHub repository.
2. Open **Actions**.
3. Run **Build Android APK**.
4. Download the `Joel-Minimal-Launcher-V8-debug` artifact.
5. Extract and install `app-debug.apk`.

The build is self-contained. It does not download Bible text during compilation.

## Local build

```bash
./gradlew assembleDebug
```

On Windows:

```bat
gradlew.bat assembleDebug
```

## Validation

```bash
python3 scripts/validate_project.py
```

The validator checks:

- required project files
- Android XML parsing
- SQLite integrity
- exactly 31,102 verses
- exactly 1,095 unique daily main references
- every reading reference resolves in the bundled Bible
- the no-repeat selector covers all 1,095 entries
- Daily Verse, chapter reading, and double-tap lock source paths

## Bible text notice

The bundled King James Version data comes from the public-domain `kjv` npm package, which mirrors the `farskipper/kjv` dataset and is released under the Unlicense. See `NOTICE_BIBLE_DATA.md`.

## License

Launcher source code: MIT.
