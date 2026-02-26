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

## Architecture Sanity Check

**Status**: Pass (reviewed 2026-02-26)

Reviewed the full application structure for architectural soundness. The application is well-formed:

**Pipeline architecture** — clean, linear data flow with no circular dependencies:
```
PdfTextExtractor → TableExtractor → FieldParser → SpreadsheetWriter
     (PDFBox)        (Tabula-java)     (regex)      (Apache POI)
```

**Data model** — proper use of sealed classes (`ExtractionResult.Success`/`.Error`), immutable data classes (`TextBlock`, `PageInfo`, `BoundingBox`, `ReferralFields`, `ParseResult`, `ParsingWarning`), and nullable types for optional fields.

**UI architecture** — standard Compose pattern: state hoisted to `App()`, enum-based screen navigation (`FILE_SELECTION → PROCESSING → RESULTS → HELP`), per-file processing in background coroutine, reusable component library (`CwCard`, `CwButton`, `SectionHeader`, etc.).

**Separation of concerns** — extraction knows nothing about UI or output; output knows nothing about extraction internals; UI orchestrates but doesn't own business logic.

**Things to watch** (not blockers):
- `PdfTextExtractor.lineYTolerance` (2.0f, character-level) vs `FieldParser.lineYTolerance` (5.0f, block-level) are independent tolerances at different levels — correct but could confuse future maintainers. A comment explaining the relationship would help.
- Pipeline processes files sequentially in a single coroutine — fine for 1-50 files but would need parallelism for larger batches.

**Verdict**: Architecture is sound and appropriate for the project scope. No structural rework needed. Proceed with bug fixes and enhancements.

---

## Testing Policy

All bug fixes (B-items) **must** include new test cases that:
1. **Reproduce the bug** — a test using realistic input that fails before the fix
2. **Verify the fix** — the same test passes after the fix
3. **Use realistic text structures** — multi-line `TextBlock` inputs at varied Y coordinates, labels on separate lines from values, cross-page patterns — not single-line synthetic strings

All enhancement work (E-items) should include tests where applicable (extraction logic, output formatting). UI-only changes don't require unit tests.

Test fixtures should be derived from the docling JSON reference at `reference/sample-output/` to ensure patterns match real PDF output. Sanitize all PHI — use placeholder tokens like `{{{FIRST-NAME}}}`.

Agents implementing work packages must run `./gradlew test` before committing and include the test results in their output.

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

### ~~E3. Improve PDF parsing feedback~~ ✓ RESOLVED (WP-10)

Replaced println diagnostics with structured `ParsingWarning` and `ParseResult` types. FieldParser.parse() returns warnings when labels are detected but patterns fail. ProcessingScreen shows per-file warning count; ResultsScreen has expandable warnings section grouped by file.

### ~~B2. Full name concatenated into firstName without spaces~~ ✓ RESOLVED (WP-12)

Fixed by adding `splitCamelCaseName()` post-processing that inserts spaces before uppercase letters in concatenated name strings, plus improved name parsing in `parseNameParts()`.

---

### ~~B3. Case ID not extracted~~ ✓ RESOLVED (WP-12)

Fixed with cross-line extraction via `extractCrossLineValue()` — searches for the "Case ID:" label and looks at the next non-empty line for the value when the label and value are on separate lines.

---

### ~~B4. Request ID not extracted~~ ✓ RESOLVED (WP-12)

Fixed with cross-line RQID extraction. The regex now handles both same-line `RQID:value` and separate-line patterns where the value follows the label on the next line.

---

### ~~B5. Date of Issue extracted as text, not as a date~~ ✓ RESOLVED (WP-13)

Fixed in `SpreadsheetWriter` with `tryParseDate()` that parses multiple date formats (`"MMMM d, yyyy"`, `"M/d/yyyy"`, weekday-prefixed dates with ordinal suffixes) and writes as Excel date cells. Unparseable dates fall back to text cells.

---

### ~~B6. Street address not extracted~~ ✓ RESOLVED (WP-12)

Fixed by improving `parseClaimantCell()` to handle missing space between state and zip code (`{{{STATE}}}{{{ZIP-CODE}}}` pattern) and better address-splitting heuristics.

---

### ~~B7. Federal Tax ID, Vendor Number not extracted~~ ✓ RESOLVED (WP-12)

Fixed with `extractCrossLineValue()` for invoice fields. Labels and values on separate lines are now matched by searching for the label and extracting the value from the next non-empty line.

---

### ~~B8. Footer case number, assigned code, DCC number not extracted~~ ✓ RESOLVED (WP-12)

Fixed with improved footer regex `(\S+)/\s*Assigned\s+(\d+)\s+(?:null/\s*)?(?:[A-Z]+\s*/\s*)?(\S+?)(?:\s*/|\s*$)` that handles trailing `/ OMB No. ...` components.

---

### ~~E4. Replace data table with per-PDF card layout~~ ✓ RESOLVED (WP-14)

Complete rewrite of ResultsScreen data preview. Replaced horizontal-scroll table with per-PDF card layout: patient metadata stacked vertically on the left (60%), service authorizations on the right (40%), with visual separation between items. Scrollable list for multi-file batches.

---

### ~~E5. Add "Open PDF" link per referral in results~~ ✓ RESOLVED (WP-14)

Each referral card includes an "Open PDF" link that opens the source file in the OS default PDF reader via `Desktop.getDesktop().open(file)` with graceful fallback.

### ~~B9. Date of Issue parses to wrong value ("Donotwrite...")~~ ✓ RESOLVED (WP-15)

Fixed by replacing greedy `\S+` date capture with a date-specific regex requiring `MM/DD/YYYY` or `Month DD, YYYY` format, preventing capture of "Do not write in the blocks below" form instruction text.

---

### ~~B10. Footer pattern does not match real PDF footer text~~ ✓ RESOLVED (WP-15)

Fixed by making footer regex flexible with `\s*/\s*` around slash separators instead of `/ `, matching real PDFBox whitespace variations in footer text.

---

### ~~B11. Applicant name not separated into first/middle/last with spaces~~ ✓ RESOLVED (WP-15)

Fixed by applying `splitCamelCaseName()` to applicant name in both combined and individual header extraction paths, inserting spaces at lowercase-to-uppercase transitions.

---

### B12. Parsing performance is slower than expected

PDF processing takes noticeably longer than it should. Potential causes:
- Tabula-java table detection scanning all pages (expensive even when no tables found)
- `dumpPageTexts()` running in non-debug builds
- Redundant full-text searches across all pages for each field
- `reconstructPageText()` rebuilding text on every extraction call

**To investigate**: Profile a batch run to identify the bottleneck. Tabula-java is the most likely culprit — it does heavy analysis even on pages without tables.

**Severity**: Medium — noticeable UX impact during batch processing
**File**: Pipeline orchestration (`ProcessingScreen.kt`, `FieldParser.kt`, `TableExtractor.kt`)

---

### ~~E6. PHI-safe debug mode with data masking~~ ✓ RESOLVED (WP-16)

Added `PhiMask` utility with `maskValue()`/`maskDisplay()` and `BuildConfig.DEBUG` flag. All field values displayed in ResultsScreen (metadata rows, service items, footer fields) and ProcessingScreen error messages are masked when `DEBUG = true`. XLSX output and underlying data are unmasked.
- `"05/15/1990"` → `"0*********"`

---

## Cross-cutting: Tyler Feedback

Tyler's input on field priorities and spreadsheet layout affects items #2, #3, and #4. This isn't a discrete work item — it's an ongoing dependency:

- **Checkpoint #1** (Thu 2/20): Send Tyler PDF analysis results, prototype output, field priority questions
- **Checkpoint #2** (Mon 2/24): Working prototype for Tyler to test, spreadsheet format review
- Incorporate feedback into extraction rules, column layout, and UI as received

---

*Last updated: 2026-02-27*
