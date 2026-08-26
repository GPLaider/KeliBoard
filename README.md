# KeliBoard

> **HeliBoard, refined.**

KeliBoard is a modern, actively maintained [HeliBoard](https://github.com/HeliBorg/HeliBoard) fork focused on stability, usability, and first-class language support.
It keeps the privacy-conscious, offline, and deeply customizable foundation of HeliBoard while fixing long-standing bugs and shipping practical improvements faster.

Korean support is a flagship feature, not the limit of the project. KeliBoard is built for everyone who wants a dependable, configurable Android keyboard.

[Download the latest APK](https://github.com/GPLaider/KeliBoard/releases/latest)

## Why KeliBoard?

- Active downstream maintenance of public HeliBoard issues
- Android 16 stability fixes and real-device regression testing
- UI and theme fixes that improve visibility and consistency
- Better punctuation, suggestion, popup-key, and emoji-search behavior
- First-class Korean support, including a Samsung-style Cheonjiin layout
- Fully offline operation with no internet permission
- The customization and privacy model inherited from HeliBoard

## First-class Korean support

KeliBoard treats Korean as a complete input experience rather than a layout checkbox:

- Samsung-style four-column Cheonjiin layout
- Complete modern Cheonjiin vowel composition
- Correct consonant cycling, separators, compound finals, and syllable splitting
- Bundled Korean dictionary with corrected Hangul lookup
- Reliable Hangul composition after moving the cursor
- Korean-friendly punctuation behavior
- Double consonants prioritized on Dubeolsik long press
- Normal English QWERTY with Shift and Caps Lock on the lock screen
- Key labels sized and arranged for familiar Korean phone use

## Core features

- Dictionaries for suggestions and spell checking
- Custom keyboard layouts, functional rows, symbols, and number layouts
- Theme, color, icon, and background-image customization
- Emoji search and inline emoji suggestions
- Multilingual typing
- Optional glide typing with a separately supplied compatible library
- Clipboard history
- One-handed, split, floating, numpad, and D-pad modes
- Settings and learned-data backup and restore

See [layouts.md](layouts.md) for custom layout documentation. The upstream [HeliBoard wiki](https://github.com/HeliBorg/HeliBoard/wiki) remains useful for inherited features.

## Installation

Download the current build from [GitHub Releases](https://github.com/GPLaider/KeliBoard/releases/latest).

KeliBoard 4.0-ko-preview5 and later use the independent `io.github.gplaider.keliboard` application ID. They can coexist with official HeliBoard and earlier KeliBoard previews.

Android does not migrate app data between application IDs. Before switching, back up settings in the earlier app and restore that backup in KeliBoard.

## Reporting issues

Report KeliBoard bugs and feature requests in [KeliBoard Issues](https://github.com/GPLaider/KeliBoard/issues).

Please include:

- Android version and device model
- KeliBoard version
- Active language and layout
- Exact reproduction steps
- Expected and actual behavior
- A screenshot or screen recording when the problem is visual

One issue per problem keeps fixes reviewable and easier to verify.

## Project relationship

KeliBoard is a downstream fork of HeliBoard, which is based on OpenBoard and the AOSP LatinIME keyboard. Upstream history, licenses, and attribution are preserved.

Fixes suitable for the wider ecosystem may be contributed upstream, but KeliBoard does not wait for upstream release cadence before shipping verified user-facing fixes.

## Contributing

Code, translations, layouts, testing, and reproducible bug reports are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for the inherited technical guide; KeliBoard-specific contribution guidance will replace it as the project matures.

## License

KeliBoard is licensed under the [GNU General Public License v3.0](LICENSE). The repository also preserves the applicable [Apache 2.0](LICENSE-Apache-2.0) and [CC BY-SA 4.0](LICENSE-CC-BY-SA-4.0) notices inherited from its sources and artwork.

## Credits

- [HeliBoard](https://github.com/HeliBorg/HeliBoard) and its contributors
- [OpenBoard](https://github.com/openboard-team/openboard)
- [AOSP LatinIME](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/)
- LineageOS, Simple Keyboard, Indic Keyboard, and FlorisBoard contributors
