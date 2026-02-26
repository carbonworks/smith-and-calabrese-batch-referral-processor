---
type: backlog
status: active
project: smith-calabrese-consulting
created: 2026-02-23
updated: 2026-02-23
---

# S&C Phase 1 — Backlog

Work items for the PDF Referral Parser, ordered by dependency. Items are high-level — AI handles implementation details within each item.

See [build-plan.md](build-plan.md) for delivery schedule, contract details, and deliverable specs.

---

## Work Items

### 1. Scaffold Kotlin/Gradle project

Set up the Compose Multiplatform Desktop project structure.

- Gradle Kotlin DSL with Compose Desktop plugin
- Dependencies: PDFBox, Tabula-java, Apache POI, Tess4J
- Basic Compose window with CW branding (title bar, warm white background, Inter font)
- Verify clean build and `./gradlew run` on dev machine

**Depends on**: Nothing
**Checkpoint**: App window opens with branding applied

---

### 2. Build PDF extraction engine

Port extraction logic from Python reference scripts to Kotlin. This is the core of the tool.

- PDFBox `PDFTextStripper` with coordinate filtering for text extraction
- Tabula-java for structured table data
- Field mapping using coordinate-based identification (from `field-mapping.json` template)
- Regex parsing for SSN, dates, case numbers, names (port from `reference/python-scripts/extract_referral_fields.py`)
- Confidence scoring — flag low-confidence extractions for manual review
- Validate against all sample PDFs (Randall-driven, PHI files)

**Depends on**: #1 (project exists to add code to)
**Depends on**: Tyler feedback on field priorities (can start without, refine after)
**Checkpoint**: Extract all target fields from sample PDFs with acceptable accuracy

---

### 3. Build XLSX output

Generate spreadsheets from extracted data using Apache POI.

- Column headings matching agreed field list
- One row per PDF/referral
- Proper data types (dates as dates, numbers as numbers)
- Filename format: `patient-referrals-[date]-[time].xlsx`
- Output to same directory as source PDFs
- Google Sheets compatible (no Excel-only features)

**Depends on**: #2 (needs structured data to write)
**Depends on**: Tyler feedback on column layout and order
**Checkpoint**: End-to-end PDF → XLSX works correctly

---

### 4. Build desktop UI

Compose Multiplatform interface for batch processing.

- File picker for selecting one or multiple PDFs
- Drag-and-drop support
- Batch processing (1-50 PDFs in sequence)
- Progress display during extraction
- Data preview — review extracted data before saving to XLSX
- CW branding: colors, typography, layout per build plan Section 11

**Depends on**: #2 and #3 (needs working extraction + output to wire up)
**Checkpoint**: Complete workflow — pick files → extract → preview → save XLSX

---

### 5. Add OCR fallback

Tess4J integration for scanned PDF pages where PDFBox returns no text.

- Detect pages with no extractable text
- Run Tesseract OCR via Tess4J on those pages
- Feed OCR text back into the extraction pipeline
- Bundle Tesseract data files with the application

**Depends on**: #2 (extends extraction engine)
**Checkpoint**: Scanned PDF pages produce extracted text

---

### 6. Add error handling & edge cases

Harden the tool for real-world usage.

- Corrupt or unreadable PDFs — graceful error with clear message
- Missing fields — extract what's available, flag what's missing
- Unexpected layouts — confidence scoring catches anomalies
- Multi-page referrals — handle forms that span multiple pages
- Batch errors — one bad PDF doesn't stop the entire batch

**Depends on**: #2, #3, #4 (needs working tool to harden)
**Checkpoint**: Tool handles all known edge cases without crashing

---

### 7. Package & test

Create distributable installer and verify on clean machines.

- jpackage .msi installer for Windows (bundled JRE, ~60-100MB)
- jpackage .dmg for macOS (secondary target)
- Clean-machine test — install and run on fresh Windows machine
- Verify all dependencies are bundled (no "install Java first")

**Depends on**: #4, #6 (needs complete, hardened tool)
**Checkpoint**: Install from .msi on clean Windows machine, process PDFs successfully

---

### 8. Write documentation

Deliverable documents for Tyler and future maintainers.

- Technical specification: architecture, field mapping, dependencies, known limitations, maintenance notes
- User guide: installation, daily workflow, troubleshooting, screenshots
- CW header/footer branding on documents

**Depends on**: #7 (needs final tool for accurate screenshots and specs)
**Checkpoint**: Tech spec and user guide complete, reviewed

---

## Bugs

### ~~B1. Most patient metadata fields not extracted~~ ✓ RESOLVED (WP-9)

Fixed by hardening all regex patterns: multi-line header matching with DOT_MATCHES_ALL, individual field fallbacks, case-insensitive invoice labels, flexible footer regex with optional `null/` and agency code, cross-page search, configurable lineYTolerance, and `dumpPageTexts()` diagnostic utility.

---

## Enhancements

### ~~E1. Remember last file picker directory~~ ✓ RESOLVED (WP-8)

Implemented via Java Preferences API. File picker and drag-and-drop both save/restore the last-used directory.

---

### ~~E2. Remove confidence scoring~~ ✓ RESOLVED (WP-7)

Removed `Confidence` enum, `ParsedField` wrapper, `ConfidenceBadge` composable, "Low Confidence Flag" XLSX column, and confidence-colored UI highlighting. All fields are now plain `String?`.

---

### E3. Improve PDF parsing feedback

Log warnings for fields that fail to extract. Currently, missing fields are silently returned as null/empty with no indication of what went wrong.

- Log a warning per field when a regex pattern doesn't match (e.g., "WARN: header block not matched — 'Case ID:' label found but full pattern failed")
- Include which extraction stage was attempted (header, table, invoice, footer)
- Surface a per-file summary in the UI (e.g., "12/23 fields extracted — 11 missing") alongside the error panel
- Optionally list missing field names in the results screen so the user knows what to check manually

**Depends on**: #2, #4
**Priority**: Medium — critical for debugging extraction issues in the field

---

## Cross-cutting: Tyler Feedback

Tyler's input on field priorities and spreadsheet layout affects items #2, #3, and #4. This isn't a discrete work item — it's an ongoing dependency:

- **Checkpoint #1** (Thu 2/20): Send Tyler PDF analysis results, prototype output, field priority questions
- **Checkpoint #2** (Mon 2/24): Working prototype for Tyler to test, spreadsheet format review
- Incorporate feedback into extraction rules, column layout, and UI as received

---

*Last updated: 2026-02-24*
