# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project follows semantic versioning where practical.

## [Unreleased]

### Added

- GitHub Actions workflow for building debug APKs.
- Project changelog.

## [1.0.0] - 2026-06-21

### Added

- Initial public release.
- Graphical category management for ColorOS / COUI launcher drawer categories.
- Custom category creation, editing, and deletion.
- Installed app picker with icons, package names, current category labels, search, and category filtering.
- Manual package name input.
- Backup and restore for JSON configuration.
- Option to hide empty categories.
- Option to assign newly installed apps to the Other category.
- Debug logging switch.
- JSON preview.
- Save flow with launcher restart confirmation.
- Recommended LSPosed scope metadata for `com.android.launcher`.

### Notes

- The module targets ColorOS / COUI system launcher behavior and may not work on other launchers or OS versions.