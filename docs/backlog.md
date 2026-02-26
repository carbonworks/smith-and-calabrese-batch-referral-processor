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

### B2. Full name concatenated into firstName without spaces

When the header regex matches, `parseNameParts()` correctly splits into first/middle/last. But when name parts arrive from PDFBox as separate text blocks at different X/Y coordinates, `reconstructPageText()` may join them without proper spacing, producing `"FirstMiddleLast"` in the `firstName` field with `middleName` and `lastName` null.

Root cause: the `RE:` individual fallback regex `RE:\s*(.+?)(?:\s*DOB:|\s*Applicant:|\s*Authorization\s*#:|\s*$)` may capture a run of text blocks that were joined without spaces during line reconstruction (Y-tolerance too tight for the actual PDF, or the blocks span multiple reconstructed lines).

Additionally, some PDFBox extractions may produce the header block as one continuous string (confirmed by docling line 3046) where the combined regex works, but others produce word-level blocks at slightly different Y offsets that don't merge into one line.

**To investigate**: Run `FieldParser.dumpPageTexts()` against real PDFs to see how names appear in the reconstructed text. Fix may involve widening `lineYTolerance`, fixing space insertion in `reconstructPageText()`, or post-processing the name field to insert spaces before uppercase letters.

**Severity**: High — affects claimant identification
**File**: `FieldParser.kt` (line reconstruction + `parseNameParts()`)

---

### B3. Case ID not extracted

The individual fallback regex `Case ID:\s*(\S+)` only captures one non-whitespace token. If the case number contains spaces or is on a different line from the "Case ID:" label, it won't match or will truncate.

Docling reference shows the case number as `{{{CASE-NUMBER}}}` (a single hyphenated token), which the `\S+` pattern should match. If it's still not extracting, the issue is likely that PDFBox text reconstruction places "Case" and "ID:" on different lines, so the label itself isn't matched.

**To investigate**: Check `dumpPageTexts()` output for whether "Case ID:" appears as a single string or is split across lines.

**Severity**: High — core case identification
**File**: `FieldParser.kt` (`extractHeaderFieldsIndividually`)

---

### B4. Request ID not extracted

The RQID regex `RQID\s*:\s*(\S+)` should match `RQID:{{{REQUEST-ID}}}` from docling line 2613. If PDFBox splits "RQID:" and the value onto different lines, the `\S+` capture fails because it doesn't cross line boundaries.

**To investigate**: Check if PDFBox reconstructs "RQID:" and the ID value on the same line.

**Severity**: Medium — needed for case tracking
**File**: `FieldParser.kt` (`extractInvoiceFields`)

---

### B5. Date of Issue extracted as text, not as a date

`dateOfIssue` is stored as `String?` and rendered as-is (e.g., "August 13, 2024"). The XLSX output writes it as a text cell, not an Excel date cell.

The fix should parse the date string into a `LocalDate` using common formats (`"MMMM d, yyyy"`, `"MM/dd/yyyy"`, etc.) and write it as an Excel date-formatted cell in `SpreadsheetWriter`. The preview table can continue displaying the text representation.

**Severity**: Medium — affects downstream spreadsheet usability
**Files**: `SpreadsheetWriter.kt` (date cell formatting), possibly `ReferralFields.kt` (add parsed date field)

---

### B6. Street address not extracted

The claimant table cell parsing relies on a heuristic that finds a street address by looking for a word starting with a digit after the name. Docling shows the claimant cell as: `"Claimant Information FIRST MIDDLE LAST STREET-ADDRESS CITY, STATE ZIP PHONE"`.

If PDFBox doesn't extract the table cell or if the address doesn't start with a digit (e.g., "PO BOX" or suite-only addresses), the heuristic fails. Also, docling line 3398 shows `{{{STATE}}}{{{ZIP-CODE}}}` with no space between state and zip, which may break the `([A-Z]{2})\s*(\d{5})` pattern.

**To investigate**: Verify Tabula-java is producing the claimant table cell. If the cell text exists, debug the `parseClaimantCell()` address-splitting heuristic against the actual text. May need to handle missing space before zip code.

**Severity**: High — address is key patient metadata
**File**: `FieldParser.kt` (`parseClaimantCell`)

---

### B7. Federal Tax ID, Vendor Number not extracted

Docling shows invoice fields are on **separate lines** from their values:
- Line 2748: `"Authorization Number:"` (label only)
- Line 2775: `"{{{AUTHORIZATION-NUMBER}}}"` (value only)
- Line 2802: `"Vendor Number:"` (label only)
- Line 2829: `"923618220"` (value only)

The current regexes like `Vendor\s+Number\s*:\s*(\S+)` require the value on the **same line** as the label. When PDFBox produces these as separate text blocks on different Y coordinates, the label and value end up on different reconstructed lines.

Exception: `Federal Tax ID Number: 923618220` appears on a single line (docling line 1748), so that one may extract when it's on the same page. But the separate "Vendor Number:" / value pattern fails.

**Fix**: Make invoice regexes cross-line-aware by allowing `\s+` (which matches newlines) between label and value, or by searching for the label and then looking at the next non-empty line for the value.

**Severity**: High — needed for invoice processing
**File**: `FieldParser.kt` (`extractInvoiceFields`)

---

### B8. Footer case number, assigned code, DCC number not extracted

Docling line 3350 shows the footer as: `"{{{CASE-NUMBER}}}/ Assigned 9106 null/ DCPS / {{{REQUEST-ID}}} / OMB No. 0960-0555 / 98022179"`

The regex `(\S+)/\s*Assigned\s+(\d+)\s+(?:null/\s*)?(?:\S+\s*/\s*)?(\S+)` has two problems:
1. The third capture group `(\S+)` greedily matches the first non-whitespace token after the optional agency, which would be `{{{REQUEST-ID}}}`. But additional `/`-separated components follow (`OMB No.`, `98022179`), and the regex may match a wrong trailing token depending on greediness.
2. If PDFBox splits this across lines, the pattern won't span the line break.

Additionally, the Python reference extracted `dcc_number` field maps to `{{{REQUEST-ID}}}` in the footer position, but the current regex group naming calls it `dccNumber`, which may be confusing since the RQID field also contains the request ID.

**To investigate**: Verify the footer text appears as a single line in PDFBox output. Adjust regex to handle the extra trailing `/ OMB No. ...` components.

**Severity**: High — affects case tracking and routing
**File**: `FieldParser.kt` (`extractCaseNumberComponents`)

---

### E4. Replace data table with per-PDF card layout

Replace the current tabular data preview on ResultsScreen with a per-PDF card layout:
- One card per processed PDF
- **Left column**: Patient metadata stacked vertically as a text block (name, DOB, case ID, address, phone, etc.)
- **Right column**: Individual service authorizations stacked vertically with visual separation between items (CPT code, description, fee per service)
- Each card should include a link/button to open the source PDF in the OS default PDF viewer (`Desktop.getDesktop().open(file)`)
- Scrollable list of cards for multi-file batches

This replaces the horizontal-scroll spreadsheet-style table that requires scrolling to see populated columns.

**Severity**: Medium — significantly improves usability
**Files**: `ResultsScreen.kt` (major rewrite of data preview section)

---

### E5. Add "Open PDF" link per referral in results

Each referral result card should have a clickable link or button that opens the source PDF file in the OS default PDF reader application using `Desktop.getDesktop().open(file)`.

**Severity**: Low — convenience feature, dependent on E4 card layout
**Files**: `ResultsScreen.kt`

---

## Cross-cutting: Tyler Feedback

Tyler's input on field priorities and spreadsheet layout affects items #2, #3, and #4. This isn't a discrete work item — it's an ongoing dependency:

- **Checkpoint #1** (Thu 2/20): Send Tyler PDF analysis results, prototype output, field priority questions
- **Checkpoint #2** (Mon 2/24): Working prototype for Tyler to test, spreadsheet format review
- Incorporate feedback into extraction rules, column layout, and UI as received

---

*Last updated: 2026-02-26*
