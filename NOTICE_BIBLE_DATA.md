# Berean Standard Bible Data Notice

This project bundles the complete Berean Standard Bible for offline use in the generated APK.

## Source

The GitHub Actions build downloads the official BSB USFM archive from:

`https://ebible.org/Scriptures/engbsb_usfm.zip`

The build converts the canonical 66-book text into:

`app/src/main/assets/bsb.sqlite`

The resulting database contains 31,102 canonical verses and is used read-only at runtime.

## Public-domain status

The Berean Bible and Majority Bible texts were dedicated to the public domain on April 30, 2023. All uses are freely permitted.

Attribution is appreciated but not required:

> The Holy Bible, Berean Standard Bible, BSB is produced in cooperation with Bible Hub, Discovery Bible, OpenBible.com, and the Berean Bible Translation Committee. This text of God's Word has been dedicated to the public domain.

The app preserves the official Scripture wording and does not require runtime internet access.
