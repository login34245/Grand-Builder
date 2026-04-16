# Changelog

## 1.1.0 - 2026-03-27

### Added
- Production config file: `config/grand_builder.json`.
- Permission modes and dimension restrictions.
- Replace rules for safer block placement.
- Action cooldown and rollback safety confirmation.
- Hotkeys:
  - `B` open console
  - `Enter` confirm preview
  - `X` cancel preview
- New client and server messages for onboarding and failure reasons.
- `/grandbuilder reload` and `/grandbuilder status` admin commands.

### Changed
- Package namespace migrated from `com.example` to `dev.grandbuilder`.
- Build pipeline now enforces preview-first flow on core usage.
- Build placement is chunk-aware and capped by per-tick limits.
- Rollback now uses targeted snapshots and safer application.
- Metadata, README, license, and localization updated for public release.

### Fixed
- Broken `ru_ru` encoding (now proper UTF-8).
- Legacy example-mod references in metadata and project structure.
- Client/server status sync now includes effective blocks-per-tick.
