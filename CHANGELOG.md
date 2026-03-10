# Changelog

All notable changes to the S&C Batch Referral Processor are documented in this file.

---

## [Unreleased]

---

## [1.1.2] — 2026-03-10

### Fixed
- Export column settings now automatically pick up new fields added in app updates (previously, users who had saved custom export settings would not see fields introduced in later versions)

---

## [1.1.1] — 2026-03-10

### Fixed
- Provider/Doctor Name now extracts from the correct field (Mailing address) instead of the billing address (Pay to)

---

## [1.1.0] — 2026-03-10

### Added
- Extract Provider/Doctor Name from referral PDFs
- Extract Special Instructions from referral PDFs
- Extract Examiner Name & Contact from referral PDFs
- Export format dropdown — choose XLSX, CSV, or TSV on the results screen
- In-app changelog ("What's New" on Help screen)

---

## [1.0.1] — 2026-03-07

### Fixed
- Crash on packaged installs due to missing `java.sql` module in jlink runtime image

---

## [1.0.0] — 2026-03-07

Initial release.
