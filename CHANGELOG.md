# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.0.1] - 2026-03-25

### Added
- **Duplicate key warning** — adding, renaming or auto-translating a key that already exists now shows a warning; the dialog stays open so the user can correct the key without re-entering everything

### Fixed
- **Error filter state preserved** — the "Show errors" checkbox no longer resets after clicking Refresh or Save; the active state is carried over to the reloaded view

## [2.0.0] - 2026-03-25

### Added
- **Hierarchical key display** — nested translation keys (e.g. `auth.user.name`) now show collapsible group headers with indentation at every level
- **Group rename** — right-click a group header to rename the entire group; all keys underneath are renamed automatically
- **Plugin UI translations (i18n)** — all buttons, labels and dialogs are now translatable via `/lang/en.json` and `/lang/nl.json`; new languages can be added by dropping in a JSON file
- **Auto-reload on file change** — translation tables refresh automatically when `.php` files in the translation folder change on disk (e.g. after switching git branches); uses a 500 ms debounce to handle bulk changes
- **Language dropdown in Translate dialog** — source language is now a searchable dropdown instead of a fixed label, pre-selected to the default language from Settings
- **Recent languages** — the Translate dialog shows the last 5 used languages at the top of the language dropdown for quick access
- **Spelling correction** — the source language word is now also sent to OpenAI so it is returned spelling-corrected in the response
- **Row highlighting** — any modified row (value edit, key rename, or newly added) is highlighted green so unsaved changes are always visible

### Fixed
- **First load** — all translation files (auth, messages, validation, …) now load immediately on startup; previously only the first file per folder was shown until Refresh was clicked
- **Wrong source language in translation** — the Translate dialog now uses the language selected in the dropdown instead of always defaulting to the language configured in Settings
- **Group rename shows full path** — renaming a group now shows only the last segment (e.g. `user` instead of `auth.user`) in the input field

## [1.9.0] - 2024-11-01

### Added
- Custom translation folder paths can now be configured in Settings

### Fixed
- Max tokens setting was not being saved correctly

## [1.8.0] - 2024-09-01

### Added
- OpenAI GPT-4o-mini integration for automatic translations
- API key and max tokens configuration in Settings
- Default language setting
- Capitalisation is preserved: if the source word starts with a capital, all translations follow suit

## [1.7.0] - 2024-07-01

### Added
- Search field to filter by key or translation value
- Error filter checkbox to highlight rows with missing translations

## [1.6.0] - 2024-05-01

### Added
- Right-click context menu with Rename Key and Delete Key actions
- Delete key shortcut (Delete key on keyboard)
- Unsaved changes are highlighted in green

## [1.0.0] - 2024-01-01

### Added
- Initial release
- Multi-language PHP Laravel translation file editor
- Add, edit and save translation keys across all languages in a single table view
