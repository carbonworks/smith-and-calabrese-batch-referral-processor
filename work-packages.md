# S&C Batch Referral Processor — Parallel Work Packages

This file defines independent work packages for parallel agent development. Each package owns specific files to minimize merge conflicts. See `CLAUDE.md` "Parallel Agent Workflow" section for how to run them.

---

## How to Read This File

- **Status**: `ready` (can start now), `blocked` (dependency not met), `done` (merged to main)
- **Owns**: Files this package creates or heavily modifies. Only one package should own a given file.
- **Reads**: Files the agent needs to reference but should NOT modify.
- **Touches**: Files where this package adds a small, well-scoped change (e.g., a few lines). Merge conflicts here are expected and acceptable.
- **Depends on**: Other packages that must be merged first.

---

## WP-0: PDF Text Extraction Core — FOUNDATION

**Status:** done
**Owns:** `src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/PdfTextExtractor.kt`, `src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/ExtractionResult.kt`
**Reads:** `reference/python-scripts/extract_referral_fields.py`, `reference/python-scripts/dump_pdf.py`, `docs/field-mapping.json`, `docs/extraction-template.json`, `docs/build-plan.md` (Section 6)
**Touches:** none
**Depends on:** nothing

**Scope:**
Build the core PDF text extraction layer using Apache PDFBox:
1. Create `PdfTextExtractor` — wraps PDFBox `PDFTextStripper` to extract raw text from a PDF file
2. Support coordinate-based text filtering (extract text from specific page regions)
3. Handle multi-page PDFs — extract from all pages, track which page each text block came from
4. Detect pages with no extractable text (flag for OCR fallback later)
5. Create `ExtractionResult` data classes — structured container for extracted text blocks with page number, coordinates, and content
6. Handle common PDF issues: encrypted files (report error), corrupt files (graceful failure), empty pages

**Why this is first:** Every other extraction package needs raw text extraction as its input. Field parsing, table extraction, and the UI all depend on being able to pull text from PDFs.

**Acceptance:** Can open a PDF file, extract text content with positional information, handle errors gracefully. Unit tests cover: normal PDF, multi-page PDF, empty page detection, corrupt file handling.

---

## WP-1: Field Parsing Engine — Regex & Pattern Matching

**Status:** done
**Owns:** `src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/FieldParser.kt`, `src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/ReferralFields.kt`
**Reads:** `reference/python-scripts/extract_referral_fields.py` (port this logic), `docs/field-mapping.json`, `docs/extraction-template.json`
**Touches:** none
**Depends on:** WP-0 (needs ExtractionResult as input)

**Scope:**
Port the Python field extraction logic to Kotlin:
1. Create `ReferralFields` data class — all target fields from an SSA/DDS referral (claimant name, SSN, DOB, case number, exam type, referring agency, dates, etc.)
2. Create `FieldParser` that takes `ExtractionResult` and produces `ReferralFields`
3. Regex patterns for: SSN (XXX-XX-XXXX), dates (multiple formats), case/claim numbers, names
4. Coordinate-based field identification — use field mapping template to locate fields by position on page
5. Confidence scoring per field — high/medium/low based on pattern match quality
6. Handle missing fields gracefully — extract what's available, flag what's absent

**Acceptance:** Given extracted text from WP-0, produces a populated `ReferralFields` with confidence scores. Handles missing/ambiguous fields without crashing. Regex patterns match the formats in the Python reference script.

---

## WP-2: Table Extraction — Tabula-java Integration

**Status:** done
**Owns:** `src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/TableExtractor.kt`
**Reads:** `reference/python-scripts/dump_pdf.py`, `docs/build-plan.md` (Section 6)
**Touches:** none
**Depends on:** nothing

**Scope:**
Integrate Tabula-java for structured table data extraction:
1. Create `TableExtractor` — wraps Tabula-java to detect and extract tables from PDF pages
2. Auto-detect table regions on each page
3. Extract cell contents with row/column indices
4. Return structured table data that can feed into field parsing
5. Handle PDFs with no tables (common — not all referrals have tabular data)
6. Handle malformed/partial tables gracefully

**Acceptance:** Can detect tables in PDFs that contain them. Returns structured row/column data. Does not crash on PDFs with no tables. Unit tests cover: PDF with tables, PDF without tables, partial table handling.

---

## WP-3: XLSX Output — Apache POI Spreadsheet Generation

**Status:** done
**Owns:** `src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/SpreadsheetWriter.kt`
**Reads:** `docs/build-plan.md` (Section 10, D2 and D3 specs)
**Touches:** none
**Depends on:** WP-1 (needs ReferralFields data class as input)

**Scope:**
Generate XLSX spreadsheets from extracted referral data:
1. Create `SpreadsheetWriter` that takes a list of `ReferralFields` and writes an XLSX file
2. Column headings matching the field list (one column per field)
3. One row per PDF/referral processed
4. Proper data types — dates as Excel date cells, numbers as numeric cells, strings as text
5. Filename format: `patient-referrals-[date]-[time].xlsx`
6. Output to a caller-specified directory (UI will default to same directory as source PDFs)
7. Google Sheets compatible — no Excel-only features (macros, named ranges, data validation)
8. Include a confidence flag column — mark rows with any low-confidence extractions

**Acceptance:** Produces a valid .xlsx file that opens in Excel and imports cleanly into Google Sheets. Column headings present. Data types correct. Low-confidence rows flagged.

---

## WP-4: Desktop UI — File Selection & Batch Processing

**Status:** done
**Owns:** `src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/MainScreen.kt`, `src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ProcessingScreen.kt`, `src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/components/`
**Reads:** `docs/build-plan.md` (Sections 10-11), `docs/brand/carbon-works-brand-guidelines.md`
**Touches:** `src/main/kotlin/tech/carbonworks/snc/batchreferralparser/Main.kt` (replace placeholder content with screen navigation)
**Depends on:** WP-0, WP-1, WP-3 (needs extraction pipeline and XLSX output to wire up)

**Scope:**
Build the Compose Desktop UI for the complete batch processing workflow:
1. **MainScreen** — file picker (single or multiple PDFs), drag-and-drop support, "Process" button
2. **ProcessingScreen** — progress display during batch extraction, per-file status
3. **Data preview** — review extracted data in a table before saving to XLSX
4. **Save action** — trigger SpreadsheetWriter, show success/error
5. Batch processing — handle 1-50 PDFs in sequence, one bad PDF doesn't stop the batch
6. CW branding throughout — colors, typography, layout per build plan Section 11
7. Error display — clear messages for unreadable PDFs, missing fields, partial extractions

**Acceptance:** Complete workflow: pick files → extract → preview data → save XLSX. Progress visible during processing. Errors displayed clearly. CW branding applied consistently.

---

## WP-6: Packaging — jpackage Installer

**Status:** done
**Owns:** packaging config in `build.gradle.kts` (jpackage section), installer resources
**Reads:** `docs/build-plan.md` (Section 10, D1 spec)
**Touches:** none
**Depends on:** WP-4 (needs complete, working application to package)

**Scope:**
Create distributable installers:
1. Configure Gradle jpackage task for Windows .msi (bundled JRE, ~60-100MB)
2. Configure Gradle jpackage task for macOS .dmg (secondary target)
3. Configure Gradle jpackage task for Linux .deb (tertiary target)
4. App icon and installer metadata (CW branding)
5. Verify installer produces a working application on clean machine
6. No "install Java first" requirement — JRE is bundled

**Acceptance:** .msi installs on Windows and runs the application. Bundled JRE — no external Java needed. App icon and window title show CW branding.

---

## WP-7: Remove Confidence Scoring (E2)

**Status:** done
**Owns:** `ReferralFields.kt`, `SpreadsheetWriter.kt`, `SpreadsheetWriterTest.kt`
**Touches:** `FieldParser.kt`, `ResultsScreen.kt`, `ProcessingScreen.kt`
**Depends on:** WP-1

**Scope:**
Remove the per-field confidence system — fields become plain `String?` instead of `ParsedField<String>`:
1. Remove `Confidence` enum and `ParsedField` wrapper from `ReferralFields.kt`
2. Delete `ConfidenceBadge.kt` composable
3. Remove "Low Confidence Flag" column from XLSX output
4. Remove confidence-colored UI highlighting from `ResultsScreen.kt`
5. Simplify `FieldParser` merge logic

**Acceptance:** All fields are `String?`. No confidence UI or XLSX column. Tests pass.

---

## WP-8: Remember Last File Picker Directory (E1)

**Status:** done
**Owns:** `MainScreen.kt`
**Depends on:** WP-4

**Scope:**
Persist the last-used file picker directory across sessions:
1. Use Java Preferences API to store/restore directory path
2. Apply saved directory to JFileChooser on open
3. Save directory after both file picker selection and drag-and-drop

**Acceptance:** File picker opens to the last-used directory on subsequent launches.

---

## WP-9: Harden Field Extraction Regex Patterns (B1)

**Status:** done
**Owns:** `FieldParser.kt`, `FieldParserTest.kt`
**Depends on:** WP-7

**Scope:**
Fix missing patient metadata extraction by hardening all regex patterns:
1. Multi-line header matching with `DOT_MATCHES_ALL` and individual field fallbacks
2. Case-insensitive invoice labels with alternate label variations
3. Flexible footer regex with optional `null/` and agency code
4. Cross-page search for all patterns
5. Configurable `lineYTolerance` constructor parameter
6. `dumpPageTexts()` PHI-safe diagnostic utility
7. `reconstructPageTexts()` for proper line-grouped text reconstruction

**Acceptance:** Header, invoice, and footer fields extract from test PDFs. 23 unit tests pass covering all patterns.

---

## WP-10: Improve Parsing Feedback (E3)

**Status:** done
**Owns:** `ParseResult.kt`, `ParsingWarning.kt`, `ProcessingScreen.kt`, `ResultsScreen.kt`
**Touches:** `FieldParser.kt`, `FieldParserTest.kt`
**Depends on:** WP-9

**Scope:**
Replace println diagnostics with structured warnings:
1. `ParsingWarning` data class with field, stage, and message
2. `ParseResult` wrapper returned by `FieldParser.parse()` instead of bare `ReferralFields`
3. Collect warnings for each extraction stage when labels detected but patterns fail
4. ProcessingScreen shows per-file warning count
5. ResultsScreen has expandable warnings section grouped by file

**Acceptance:** Structured warnings replace all FieldParser println diagnostics. ProcessingScreen shows warning counts. ResultsScreen has expandable warnings. 27 unit tests pass.

---

## WP-11: Help Screen

**Status:** done
**Owns:** `HelpScreen.kt`
**Touches:** `Main.kt`, `MainScreen.kt`
**Depends on:** WP-4

**Scope:**
Dedicated Help screen with usage instructions and support contact:
1. Getting Started — 4-step workflow guide
2. Supported Formats — SSA/DDS PDFs, batch limit, XLSX output
3. Tips — warnings panel, directory persistence, drag-and-drop
4. Support — clickable mailto link to support@carbonworks.tech
5. Help button in MainScreen header, navigation via Screen enum

**Acceptance:** Help button on main screen opens Help screen. All sections display. Email link opens system mail client. Back button returns to file selection.

---

## WP-12: Fix Extraction Regex Bugs (B2–B8)

**Status:** done
**Owns:** `FieldParser.kt`, `FieldParserTest.kt`
**Depends on:** WP-10

**Scope:**
Fix 7 extraction bugs identified from real PDF testing:
1. B2: CamelCase name splitting via `splitCamelCaseName()` post-processing
2. B3: Cross-line Case ID extraction via `extractCrossLineValue()`
3. B4: Cross-line RQID extraction for same-line and separate-line patterns
4. B5: (Handled by WP-13 — date formatting in SpreadsheetWriter)
5. B6: Improved `parseClaimantCell()` for no-space state/zip patterns
6. B7: Cross-line invoice field extraction (Federal Tax ID, Vendor Number)
7. B8: Footer regex handling trailing `/ OMB No. ...` components
8. `extractFallbackFields()` for fallback extraction of remaining fields

**Acceptance:** 16 new regression tests using realistic multi-line TextBlock inputs. 70 total FieldParser tests pass.

---

## WP-13: Date Formatting in XLSX Output (B5)

**Status:** done
**Owns:** `SpreadsheetWriter.kt`, `SpreadsheetWriterTest.kt`
**Depends on:** WP-3

**Scope:**
Write date fields as Excel date cells instead of text:
1. `tryParseDate()` parses multiple formats: `"MMMM d, yyyy"`, `"M/d/yyyy"`, weekday-prefixed dates
2. Strip weekday prefixes and ordinal suffixes before parsing
3. Date columns (Date of Issue, DOB, Appointment Date) written as numeric date cells
4. Unparseable dates fall back to text cells

**Acceptance:** 4 new date tests (date of issue, DOB, unparseable fallback, weekday prefix). 12 total SpreadsheetWriter tests pass.

---

## WP-14: Results Card Layout & Open PDF (E4, E5)

**Status:** done
**Owns:** `ResultsScreen.kt`
**Depends on:** WP-10

**Scope:**
Replace horizontal-scroll data table with per-PDF card layout:
1. `ReferralCard` composable — one card per processed PDF
2. Patient metadata stacked vertically on left (60% width)
3. Service authorizations stacked vertically on right (40% width)
4. `OpenPdfLink` — opens source PDF in OS default viewer via `Desktop.getDesktop().open(file)`
5. Scrollable card list for multi-file batches
6. Removed: `TableHeaderCell`, `TableDataCell`, `extractRowValues()`, `SelectionContainer`

**Acceptance:** Per-PDF cards display all extracted fields. Open PDF link functional. Scrollable for batch results.

---

## Dependency Graph

```
WP-0 (PDF Text Extraction) ──> WP-1 (Field Parsing) ──┬──> WP-3 (XLSX Output) ──┐
                                                        │                          │
WP-2 (Table Extraction) ───────────────────────────────┘                          ├──> WP-4 (Desktop UI) ──> WP-6 (Packaging)
                                                                                   │
WP-7 (Remove Confidence) ──> WP-9 (Harden Regex) ──> WP-10 (Parsing Feedback) ──┬──> WP-12 (Fix Regex Bugs)
                                                                                   └──> WP-14 (Results Cards)
WP-3 (XLSX Output) ──> WP-13 (Date Formatting)
WP-8 (Remember Directory)
WP-11 (Help Screen)
```

## Recommended Execution Order

**Wave 1** (no dependencies): WP-0, WP-2
**Wave 2** (after WP-0): WP-1
**Wave 3** (after WP-1, WP-2): WP-3
**Wave 4** (after WP-0, WP-1, WP-3): WP-4
**Wave 5** (after WP-4, parallel): WP-6, WP-7, WP-8
**Wave 6** (after WP-7): WP-9
**Wave 7** (after WP-9, parallel): WP-10, WP-11
**Wave 8** (after WP-10, parallel): WP-12, WP-13, WP-14
