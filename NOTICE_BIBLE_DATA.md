# Bible Data Notice

This project bundles the King James Version of the Bible for offline use.

Source used to create `app/src/main/assets/kjv.sqlite`:

- npm package: `kjv@1.0.0`
- upstream repository: `farskipper/kjv`
- source file: `json/verses-1769.json`
- upstream license: Unlicense / Public Domain

The database contains 31,102 canonical KJV verses. Formatting markers from the source were normalized for plain-text display: paragraph markers and square brackets around supplied italic words were removed while preserving the words.

The `daily_reading_refs.json` file contains references only. It does not duplicate the Bible text.
