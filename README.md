# CDCsvParser

Personal finance / crypto-portfolio app for Android (Material 3,
English + German). Bank and crypto CSV exports (Crypto.com, BlockPit,
Kraken, …) are parsed by a native C++ core, which computes wallets,
EUR-valued assets, money spent, P/L and rewards — the charts value
historical buckets with the price valid at that time (point-in-time
pricing, deep price-history chain).

This is a personal project; no public release yet.

## Build

```bash
./gradlew :app:assembleDebug          # debug APK (compiles the C++ core via NDK)
./gradlew :app:testDebugUnitTest      # JVM unit tests
./gradlew :app:lintDebug              # lint (must be clean)
```

## How to test

Install the debug APK (`app/build/outputs/apk/debug/app-debug.apk`) on an
emulator (or device) and set it to the German locale if you want to check
the de strings. On the device, parse a CSV through the SAF picker — e.g. a
real BlockPit export placed in the emulator's Downloads as `blockpit.csv`
(its distinctive header overrides the app-type setting; a stale saved
app-type will not break the parse). A previous export can be re-parsed from
the main screen via the history entry instead of re-picking the file.
Watch the parse screen: with a cold price cache the cards show a short
"prices are loading" state and fill in within ~10 s (bulk fetch runs off
the core thread). The `files/log/core.log` + `files/log/CDCsvParser.log`
files on device hold the C++/Kotlin logs.

# License

Shield: [![CC BY-NC-ND 4.0][cc-by-nc-nd-shield]][cc-by-nc-nd]

This work is licensed under a
[Creative Commons Attribution Non Commercial No Derivatives 4.0 International License][cc-by-nc-nd].

[![CC BY-NC-ND 4.0][cc-by-nc-nd-image]][cc-by-nc-nd]

[cc-by-nc-nd]: http://creativecommons.org/licenses/by-nc-nd/4.0/
[cc-by-nc-nd-image]: https://licensebuttons.net/l/by-nc-nd/4.0/88x31.png
[cc-by-nc-nd-shield]: https://img.shields.io/badge/License-CC%20BY--NC--ND%204.0-lightgrey.svg
