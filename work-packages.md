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
**Owns:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/PdfTextExtractor.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/ExtractionResult.kt`
**Reads:** `reference/python-scripts/extract_referral_fields.py`, `reference/python-scripts/dump_pdf.py`, `docs/spec/field-mapping.json`, `docs/spec/extraction-template.json`, `docs/spec/build-plan.md` (Section 6)
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
**Owns:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/FieldParser.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/ReferralFields.kt`
**Reads:** `reference/python-scripts/extract_referral_fields.py` (port this logic), `docs/spec/field-mapping.json`, `docs/spec/extraction-template.json`
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
**Owns:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/TableExtractor.kt`
**Reads:** `reference/python-scripts/dump_pdf.py`, `docs/spec/build-plan.md` (Section 6)
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
**Owns:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/SpreadsheetWriter.kt`
**Reads:** `docs/spec/build-plan.md` (Section 10, D2 and D3 specs)
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
**Owns:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/MainScreen.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ProcessingScreen.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/components/`
**Reads:** `docs/spec/build-plan.md` (Sections 10-11), `docs/brand/carbon-works-brand-guidelines.md`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/Main.kt` (replace placeholder content with screen navigation)
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
**Owns:** packaging config in `app/build.gradle.kts` (jpackage section), installer resources
**Reads:** `docs/spec/build-plan.md` (Section 10, D1 spec)
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

## WP-15: Fix Remaining Extraction Bugs (B9, B10, B11)

**Status:** done
**Owns:** `FieldParser.kt`, `FieldParserTest.kt`
**Depends on:** WP-12

**Scope:**
Fix three extraction bugs identified from real PDF testing:
1. B9: Date of Issue regex captures "Donotwrite..." form instruction instead of actual date
2. B10: Footer regex doesn't match real PDFBox-reconstructed footer text (spaces around slashes)
3. B11: Applicant name missing spaces between first/middle/last — apply `splitCamelCaseName()` or fix capture regex

**Acceptance:** New regression tests for each bug. All FieldParser tests pass.

---

## WP-16: PHI-Safe Debug Masking (E6)

**Status:** done
**Owns:** `PhiMask.kt` (NEW), `BuildConfig.kt` (NEW)
**Touches:** `ResultsScreen.kt`, `ProcessingScreen.kt`
**Depends on:** WP-14

**Scope:**
Add data masking for PHI-safe development:
1. `maskValue()` utility — first char of each word visible, rest asterisked
2. `BuildConfig.DEBUG` compile-time flag — debug=masked, release=unmasked
3. Apply masking in ResultsScreen card fields and ProcessingScreen display
4. Do NOT mask underlying ReferralFields data or XLSX output

**Acceptance:** All displayed field values masked in debug builds. XLSX output unaffected. Existing tests pass.

---

## WP-17: Hybrid Text Extraction — PDFBox getText() for Regex Matching (B9, B10)

**Status:** done
**Owns:** `PdfTextExtractor.kt`, `FieldParser.kt`, `FieldParserTest.kt`, `PdfTextExtractorTest.kt`
**Reads:** `reference/python-scripts/extract_referral_fields.py`, `CLAUDE.md`
**Touches:** `ProcessingScreen.kt`
**Depends on:** WP-15

**Scope:**
Fix the root cause of B9 and B10 — text reconstruction produces concatenated strings without proper spacing because our custom `PositionCollectingStripper` bypasses PDFBox's built-in text layout logic.

Implement a hybrid approach:
1. **Add `PDFTextStripper.getText()` output to `ExtractionResult.Success`** — add a `pageStrippedTexts: List<String>` field that contains PDFBox's built-in text output per page. This text has proper spacing, line breaks, and character positioning.
2. **Update `PdfTextExtractor.extractFromDocument()`** — after collecting TextBlocks via the custom stripper, also run a standard `PDFTextStripper` per page to get the properly-spaced text. Store both in the `PageInfo` data class (add a `strippedText: String` field to `PageInfo`).
3. **Update `FieldParser.reconstructPageTexts()`** — instead of reconstructing text from TextBlocks via naive `joinToString(" ")`, return the `strippedText` from each `PageInfo`. The existing regex patterns should work better against properly-spaced text.
4. **Keep `TextBlock` data in `PageInfo`** — the custom position-collecting extraction is still needed for region-based queries (`extractFromRegion()`), table extraction, and any future coordinate-based features.
5. **Update `dumpPageTextsDetailed()`** — it should use the new `strippedText` for its output, making the diagnostic dump match what regex patterns actually see.
6. **Update tests** — existing FieldParser tests construct `TextBlock` objects directly and won't be affected (they test regex logic, not text reconstruction). Add a test verifying that `reconstructPageTexts()` now returns `strippedText` from PageInfo. Update any PdfTextExtractor tests for the new `strippedText` field.

**Key constraint**: The `PositionCollectingStripper` and `TextBlock` extraction must remain functional — only the text used for regex matching changes. This is an additive change to `ExtractionResult`/`PageInfo`, not a replacement.

**Acceptance:**
- `PageInfo` has a `strippedText: String` field populated by `PDFTextStripper.getText()`
- `FieldParser.reconstructPageTexts()` returns `strippedText` instead of naive block joining
- Existing FieldParser tests pass (regex patterns against TextBlock-based test data)
- New test verifies `strippedText` is used for page text reconstruction
- `./gradlew test` passes

---

## WP-18: Runtime PHI Visibility Toggle (E7)

**Status:** done
**Owns:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/SettingsScreen.kt` (NEW), `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/util/PhiPreferences.kt` (NEW)
**Touches:** `ResultsScreen.kt`, `PhiMask.kt`, `BuildConfig.kt`, `Main.kt`, `MainScreen.kt`, `HelpScreen.kt`
**Depends on:** WP-16 (existing masking infrastructure)

**Scope:**

Replace the compile-time `BuildConfig.DEBUG` PHI masking with a user-controlled runtime toggle:

1. **`PhiPreferences` utility** — wraps Java `Preferences` API (same node as existing directory pref: `tech/carbonworks/snc/batchreferralparser`). Stores a single boolean key `"showPhiByDefault"` (default `false`). Provides `getShowByDefault(): Boolean` and `setShowByDefault(Boolean)`.

2. **Runtime masking state in `PhiMask`** — replace `isMaskingEnabled()` delegation from `BuildConfig.DEBUG` to a mutable runtime property. Expose `var maskingEnabled: Boolean` (initialized from `PhiPreferences.getShowByDefault()` inverted — if "show by default" is true, masking starts disabled). Remove the `BuildConfig.DEBUG` dependency. `maskValue()` and `maskDisplay()` continue to check `maskingEnabled` — no changes to call sites in `ResultsScreen.kt` needed beyond recomposition.

3. **Eye toggle on Results screen** — add an `IconButton` with a visibility on/off icon (`Icons.Outlined.Visibility` / `Icons.Outlined.VisibilityOff`) in the Results screen header area. Clicking it flips `PhiMask.maskingEnabled` and triggers recomposition of all masked fields. Use a Compose `MutableState<Boolean>` hoisted to the Results screen level to drive recomposition.

4. **Discovery cue animation** — the eye toggle pulses (subtle scale or alpha animation) on a repeating 15-second cycle on every app launch. The cue runs indefinitely across launches until dismissed. Dismissal is permanent (persisted via `"phiToggleDismissed"` preference key set to `true`) and triggered by either: (a) the user clicks the eye toggle, or (b) the user changes the "Show extracted data by default" setting. Once dismissed, the cue never animates again on any future launch.

5. **Settings screen** — new `SettingsScreen.kt` composable. Add `SETTINGS` to the `Screen` enum in `Main.kt`. Accessible via a gear icon (`Icons.Outlined.Settings`) `IconButton` in the `MainScreen` header. Contains:
   - Section: "Privacy"
   - Toggle row: "Show extracted data by default" with a `Switch` composable
   - Descriptive text: explains that data is masked by default for privacy protection
   - Back button returns to `FILE_SELECTION`
   - Changing this toggle also persists the `"phiToggleDismissed"` key to `true` (stops the discovery cue)

6. **Upgrade MainScreen header buttons** — replace the existing `CwSecondaryButton(text = "? Help")` with a pair of `IconButton`s using Material icons:
   - Settings: `Icons.Outlined.Settings` (navigates to `SETTINGS`)
   - Help: `Icons.AutoMirrored.Outlined.HelpOutline` (navigates to `HELP`)
   - Both use `SoftGray` tint, arranged in a `Row` with 4.dp spacing, right-aligned in the header
   - Optional: tooltip text ("Settings" / "Help") on hover via `Modifier.pointerHoverIcon` or `TooltipBox`

7. **Help screen tip** — add a `HelpBullet` in the Tips section: `"Extracted data is masked by default for privacy. Use the eye toggle on the results screen to reveal values."`

8. **Remove `BuildConfig.kt`** — the `DEBUG` constant is no longer needed. Delete the file. All masking is now controlled by `PhiMask.maskingEnabled` at runtime.

9. **XLSX output unchanged** — `SpreadsheetWriter` never calls `PhiMask` and writes raw `ReferralFields` values. No changes needed.

**Acceptance:**
- Eye toggle on Results screen masks/unmasks all displayed field values
- PHI is masked by default on every app launch (unless "show by default" setting is on)
- Discovery cue pulses on a 15s cycle every launch until permanently dismissed by first toggle click or settings change
- Settings screen accessible from MainScreen with "Show extracted data by default" toggle
- MainScreen header has Settings (gear) and Help (question mark) as Material icon buttons, replacing the old text-based "? Help" button
- Setting persists across app restarts via Java Preferences
- New tip in HelpScreen about the toggle
- `BuildConfig.kt` removed
- XLSX output always contains unmasked data
- `./gradlew test` passes

---

## WP-19: Adjust Discovery Cue Animation Timing

**Status:** done
**Owns:** `ResultsScreen.kt` (keyframes block only)
**Depends on:** WP-18

**Scope:**
Replace the single-pulse 15s discovery cue animation cycle with a double-pulse 12s cycle: 5 seconds idle, two quick 200ms grow/shrink pulses, then idle until repeat.

**Acceptance:** Discovery cue animation uses the new double-pulse 12s timing. Compiles and existing tests pass.

---

## WP-20: Green Mask Icon and Secondary Buttons

**Status:** done
**Owns:** `ResultsScreen.kt` (icon tint only), `CwButton.kt`
**Depends on:** WP-18

**Scope:**
Change the eye mask icon tint from SoftGray to BrandGreen. Update CwSecondaryButton to use BrandGreen for content color and add a BrandGreen border.

**Acceptance:** Eye icon and all secondary buttons use BrandGreen. Compiles and existing tests pass.

---

## WP-21: Always-Visible Scrollbar on Data Preview

**Status:** done
**Owns:** `ResultsScreen.kt` (LazyColumn block + imports)
**Depends on:** WP-18

**Scope:**
Wrap the ResultsScreen data preview LazyColumn in a Box with a VerticalScrollbar companion for always-visible scroll indication.

**Acceptance:** Scrollbar visible at all times on the data preview. Compiles and existing tests pass.

---

## WP-22: PHI Masking Tests

**Status:** done
**Owns:** `app/src/test/kotlin/.../util/PhiMaskTest.kt` (NEW)
**Depends on:** WP-18

**Scope:**
Comprehensive test file for PhiMask (maskValue, maskDisplay), PhiPreferences (round-trips), and SpreadsheetWriter boundary (unmasked output despite masking enabled). 17 tests with state isolation helpers.

**Acceptance:** 17 new tests pass. Total test count: 98. All existing tests unaffected.

---

## WP-23: "Show Extracted Data by Default" Setting Not Honored (B12)

**Status:** done
**Owns:** `PhiMask.kt`, `ResultsScreen.kt`
**Reads:** `PhiPreferences.kt`, `SettingsScreen.kt`
**Touches:** none
**Depends on:** nothing (all dependencies already merged)

**Scope:**
The "Show extracted data by default" toggle in the Settings screen persists its value via `PhiPreferences`, but the app does not honor the setting on subsequent launches or when navigating to the Results screen:
1. Investigate how `PhiMask.maskingEnabled` is initialized from `PhiPreferences.getShowByDefault()` — the `object` singleton initializes once at class load time and is never re-read
2. Investigate how `ResultsScreen` initializes its `isMasked` state from `PhiMask.maskingEnabled` — verify this reads the correct persisted value
3. Fix the initialization path so that when "Show extracted data by default" is enabled, the Results screen starts with data unmasked
4. Verify the setting round-trips correctly: toggle on → restart app → Results screen shows unmasked data

**Acceptance:** Toggling "Show extracted data by default" ON in Settings causes the Results screen to display unmasked data on next navigation and on app restart. Toggling OFF restores default masked behavior. Existing PHI masking tests pass.

---

## WP-24: Copy/Paste Support for Extracted Data Values (E8)

**Status:** done (needs rework — see WP-26)
**Owns:** `ResultsScreen.kt`
**Reads:** none
**Touches:** none
**Depends on:** nothing

**Scope:**
Enable copy-to-clipboard behavior for individual data values on the Results screen:
1. Each extracted field value in the referral cards (metadata rows, service items, footer fields) should be selectable/copyable
2. Use Compose `SelectionContainer` or per-value click-to-copy with clipboard API
3. Respect PHI masking — only copy the displayed (possibly masked) value, not the underlying raw value
4. Visual feedback on copy (e.g., brief tooltip or color flash)

**Acceptance:** Users can copy individual field values from the Results screen referral cards. Copied text matches what is displayed (masked if masking is active). Visual feedback confirms the copy action.

---

## WP-26: Replace Click-to-Copy with Native Text Selection (E10)

**Status:** done
**Owns:** `ResultsScreen.kt`
**Reads:** none
**Touches:** none
**Depends on:** WP-24

**Scope:**
WP-24 implemented click-to-copy with a color flash — a web/mobile pattern that feels foreign on Windows desktop. Replace with standard native text selection:
1. Remove the `CopyableValue` composable and `copyToClipboard()` helper
2. Wrap referral card content in Compose `SelectionContainer` so users can click-drag to highlight text and Ctrl+C to copy — standard Windows behavior
3. Decide on masking behavior during selection (see notes below)
4. Remove unused imports added by WP-24 (`animateColorAsState`, `tween`, `MutableInteractionSource`, `LaunchedEffect`, `PointerIcon`, `pointerHoverIcon`, `TextUnit`, `delay`, `Toolkit`, `StringSelection`)

**Masking + selection behavior (TBD):**
The displayed text drives what can be selected — when masked, users select and copy masked values. This matches the visual contract (what you see is what you get). The eye toggle unmasks first if the user needs real values.

**Acceptance:** Users can click-drag to select text in referral cards and Ctrl+C to copy. No click-to-copy flash behavior. Standard Windows desktop UX. Masked text copies as masked.

---

## WP-25: CarbonWorks Origami Bird App Icon (E9)

**Status:** done
**Owns:** installer icon resources, `app/build.gradle.kts` (icon config)
**Reads:** `docs/brand/carbon-works-brand-guidelines.md`
**Touches:** none
**Depends on:** nothing

**Scope:**
Replace the default app icon with the CarbonWorks origami bird logo for both desktop platforms:
1. Source the origami bird asset from brand assets (`docs/brand/` or request from user)
2. Generate Windows .ico file (multi-resolution: 16x16, 32x32, 48x48, 256x256)
3. Generate macOS .icns file (standard resolutions)
4. Configure jpackage icon paths in `app/build.gradle.kts`
5. Set the Compose window icon in `Main.kt`
6. Verify icon appears in: window title bar, taskbar, installer, and desktop shortcut

**Acceptance:** App icon shows the CarbonWorks origami bird on Windows (title bar, taskbar, installer) and macOS (dock, title bar, DMG). Icon is crisp at all standard sizes.

---

## WP-27: Update Application Title (E11)

**Status:** done
**Owns:** `Main.kt`
**Reads:** none
**Touches:** `app/build.gradle.kts` (packageName if it should match)
**Depends on:** nothing

**Scope:**
Change the application window title to "PDF Referral Parser - Carbon Works":
1. Update the `Window` composable `title` parameter in `Main.kt`
2. Update `packageName` in `build.gradle.kts` `nativeDistributions` if appropriate (affects installer name, Start Menu entry)

**Acceptance:** Window title bar reads "PDF Referral Parser - Carbon Works". Installer/package name updated to match.

---

## WP-28: Fix Progress Bar Color (B13)

**Status:** done
**Owns:** `ProcessingScreen.kt`
**Reads:** `docs/brand/carbon-works-brand-guidelines.md`
**Touches:** none
**Depends on:** nothing

**Scope:**
The progress indicator on the ProcessingScreen has a purple/default Material tint instead of brand colors:
1. Find the `LinearProgressIndicator` or `CircularProgressIndicator` in `ProcessingScreen.kt`
2. Override its color to use `BrandGreen` and track color to use a muted green or `LightGray`
3. Ensure no purple/Material default colors remain anywhere in the processing flow

**Acceptance:** Progress indicator uses BrandGreen. No purple anywhere in the app. Compiles and existing tests pass.

---

## WP-29: Help Button on Results Screen (E12)

**Status:** done
**Owns:** `ResultsScreen.kt`
**Reads:** `MainScreen.kt` (for existing icon button pattern), `HelpScreen.kt`
**Touches:** none
**Depends on:** nothing

**Scope:**
Add a help icon button to the Results screen header so users can reach the Help screen without navigating back to the main screen first:
1. Add a help icon (`Icons.AutoMirrored.Outlined.HelpOutline`) button in the Results screen header row
2. Follow the same icon style used in the MainScreen header (SoftGray or BrandGreen tint, same sizing)
3. Wire navigation to the `HELP` screen via the existing `Screen` enum
4. ResultsScreen will need an `onNavigateToHelp` callback parameter (or similar)

**Acceptance:** Help icon visible on the Results screen header. Clicking it navigates to the Help screen. Back button on Help returns to Results. Consistent styling with MainScreen header icons.

---

## WP-30: Shorten Discovery Cue Animation Cycle (E13)

**Status:** done
**Owns:** `ResultsScreen.kt` (keyframes block only)
**Depends on:** nothing

**Scope:**
Reduce the discovery cue animation cycle on the mask eye toggle from 12 seconds to 8 seconds. Adjust the keyframe timings proportionally to maintain the same double-pulse feel in a tighter loop.

**Acceptance:** Animation cycle is 8 seconds. Double-pulse still reads clearly. Compiles and existing tests pass.

---

## WP-31: Add App Icon Resources and Clean Up Old Icon Artifacts (E15)

**Status:** done
**Owns:** `app/src/main/resources/icon.ico`, `app/src/main/resources/icon.png`, `app/src/main/resources/icon.svg`
**Reads:** `app/build.gradle.kts`
**Touches:** `app/build.gradle.kts`
**Depends on:** WP-25 (icon infrastructure), WP-27 (app title)

**Scope:**
The origami bird brand SVG has been exported to icon.png (256x256) and icon.ico (16/32/48/256) using Inkscape. These files already exist as untracked files in `app/src/main/resources/`. This WP commits them and cleans up the old icon generation artifacts:
1. Add `app/src/main/resources/icon.svg`, `icon.png`, and `icon.ico` to the repository
2. Delete `tools/generate-icons.py` (old Python polygon-rendering script, superseded by real brand SVG)
3. Delete `app/src/main/resources/cw-emblem.svg` (old extracted polygon emblem, replaced by icon.svg)
4. Remove the stale "Icon generation" comment block in `app/build.gradle.kts` (lines 54-57, referencing the deleted Python script)

**Acceptance:** icon.svg, icon.png, and icon.ico committed to resources. Old artifacts (generate-icons.py, cw-emblem.svg) deleted. Stale comment removed. Build compiles and all tests pass.

---

## WP-32: Reduce Branding — Remove Watermark and Window Title Suffix (E14)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/MainScreen.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/Main.kt`, `app/build.gradle.kts`
**Depends on:** WP-27 (app title), WP-25 (icon)

**Scope:**
The origami bird icon and brand colors already provide sufficient brand presence. Reduce branding clutter:
1. Remove the CW emblem watermark from the MainScreen background (the `Image` composable loading `icon.svg` with 0.25 alpha)
2. Shorten the window title from "PDF Referral Parser - Carbon Works" to just "PDF Referral Parser" (in `Main.kt` Window title and `build.gradle.kts` packageName)

**Acceptance:** No watermark visible on the main screen. Window title bar reads "PDF Referral Parser". Icon still appears in title bar/taskbar.

---

## WP-33: Rebrand to PDF Authorization Processor (E16)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `Main.kt`, `MainScreen.kt`, `HelpScreen.kt`, `app/build.gradle.kts`
**Depends on:** WP-32

**Scope:**
Rebrand the application from "referral" terminology to "authorization" terminology. The tool gathers authorization information from Maryland Disability Determination Services (MD DDS) PDF service authorization forms.

Update all user-visible text:
1. **`app/build.gradle.kts`**: `packageName` → `"PDF Authorization Processor"`, `description` → `"Batch PDF data extraction tool for MD DDS service authorization processing"`
2. **`Main.kt`**: Window title → `"PDF Authorization Processor"`
3. **`MainScreen.kt`**: Header text `"S&C Batch Referral Processor"` → `"S&C Batch Authorization Processor"`, subtitle `"Select PDF referral files to extract structured data"` → `"Gather authorization information from MD DDS service authorization forms"`, file picker dialog title `"Select PDF Referral Files"` → `"Select PDF Authorization Files"`
4. **`HelpScreen.kt`**: Update subtitle `"Learn how to use the batch referral processor"` → `"Learn how to use the batch authorization processor"`, step 1 `"Select PDF referral files..."` → `"Select PDF authorization files..."`, supported formats `"The tool processes SSA/DDS consultative examination referral PDFs."` → `"The tool processes Maryland DDS service authorization PDFs."`

Do NOT rename packages, classes, variable names, or file names — only user-visible strings.

**Acceptance:** All user-visible text references "authorization" instead of "referral". Window title reads "PDF Authorization Processor". Build compiles and all tests pass.

---

## WP-34: Feature Flag Infrastructure (F0)

**Status:** done
**Owns:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/FeatureFlags.kt`
**Reads:** none
**Touches:** none
**Depends on:** nothing

**Scope:**
Create a build-time feature flag system. Flags are `const val` booleans in a singleton object — the compiler dead-code-eliminates disabled paths. No runtime configuration, no build flavors, no variant support. To toggle a feature, change the value and commit.

1. **`FeatureFlags.kt`**: Create `object FeatureFlags` in the root package with:
   - `const val EXPORT_COLUMN_CONFIG = false` — gates the configurable export columns feature (WP-35 through WP-37)
   - KDoc on the object explaining the convention: flags are `const val Boolean`, `false` = disabled, flip to `true` and commit to enable

No tests needed — it's a static constants file.

**Acceptance:** `FeatureFlags.kt` compiles. Existing tests still pass. Flag is `false` by default.

---

## WP-35: Export Column Configuration — Data Model and Persistence (F1)

**Status:** done
**Owns:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/ExportColumn.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/ExportPreferences.kt`
**Reads:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/ReferralFields.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/SpreadsheetWriter.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/util/PhiPreferences.kt`
**Touches:** `app/build.gradle.kts` (add kotlinx-serialization-json dependency if needed)
**Depends on:** WP-34

**Scope:**
Create the data model and persistence layer for configurable export columns:

1. **`ExportColumn.kt`**: Define the column configuration types:
   - `ExportColumn` sealed class with two subclasses:
     - `ExportColumn.Field(fieldId: String, displayName: String, enabled: Boolean = true)` — a data field from extraction
     - `ExportColumn.Spacer(id: String, label: String = "Empty Column")` — a blank spacer column
   - `ExportColumnConfig(columns: List<ExportColumn>)` with a `companion object` providing `default()` that returns all 22 fields in the canonical order from `SpreadsheetWriter.COLUMN_HEADINGS`
   - `DEFAULT_FIELD_ORDER: List<Pair<String, String>>` — canonical mapping of fieldId to display name (e.g., `"firstName" to "First Name"`)
   - `DATE_FIELD_IDS = setOf("dateOfIssue", "dob", "appointmentDate")` — replaces the fragile index-based `DATE_COLUMN_INDICES`
   - Extension function `ReferralFields.getFieldValue(fieldId: String): String` — returns the value for a given field ID via a `when` expression
   - All serializable via `kotlinx.serialization` annotations (`@Serializable`, `@SerialName` discriminator for the sealed class)

2. **`ExportPreferences.kt`**: Persistence singleton following the `PhiPreferences` pattern:
   - Uses `java.util.prefs.Preferences` at the same node (`"tech/carbonworks/snc/batchreferralparser"`)
   - `load(): ExportColumnConfig` — reads JSON string from Preferences key `"exportColumnConfig"`, deserializes, returns `default()` on missing/corrupt data
   - `save(config: ExportColumnConfig)` — serializes to JSON, writes to Preferences
   - `reset()` — removes the key (next load returns default)

3. **Tests**: Unit tests verifying:
   - `ExportColumnConfig.default()` produces 22 fields in the expected order
   - `getFieldValue()` returns correct values for all 22 field IDs
   - `ExportPreferences` round-trips a config through save/load (including spacers and disabled fields)
   - Corrupt/empty JSON in preferences falls back to default gracefully

4. If `kotlinx-serialization-json` is not on the compile classpath, add it to `app/build.gradle.kts` dependencies and add the serialization plugin.

**Acceptance:** Data model compiles, serialization round-trips correctly, preferences load/save works, all tests pass (existing + new). No UI changes.

---

## WP-36: Refactor SpreadsheetWriter to Use ExportColumnConfig (F2)

**Status:** done
**Owns:** none
**Reads:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/ExportColumn.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/FeatureFlags.kt`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/SpreadsheetWriter.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`
**Depends on:** WP-35

**Scope:**
Refactor SpreadsheetWriter to accept and use column configuration:

1. **`SpreadsheetWriter.write()`**: Add optional `columnConfig: ExportColumnConfig = ExportColumnConfig.default()` parameter.
2. **Replace hardcoded parallel arrays**: Instead of `COLUMN_HEADINGS` + `extractRowValues()`, filter `columnConfig.columns` to enabled fields and spacers, then:
   - Write header row from column display names (blank for spacers)
   - Write data rows using `ReferralFields.getFieldValue(fieldId)` (blank for spacers)
3. **Decouple date detection**: Replace `DATE_COLUMN_INDICES` (index set) with `DATE_FIELD_IDS` (field ID set). Check `column.fieldId in DATE_FIELD_IDS` instead of `colIndex in DATE_COLUMN_INDICES`.
4. **Keep `COLUMN_HEADINGS` as a reference constant** (or remove if unused after refactor).
5. **Wire in ResultsScreen behind feature flag**: In `saveToXlsx()`, check `FeatureFlags.EXPORT_COLUMN_CONFIG`:
   - If `true`: load `ExportPreferences.load()` and pass to `SpreadsheetWriter.write()`
   - If `false`: call `SpreadsheetWriter.write()` with no config (uses default — identical to current behavior)
6. **Update existing tests**: Ensure `SpreadsheetWriter` tests still pass — default config should produce byte-identical output to the old hardcoded behavior.

**Acceptance:** Default config produces identical XLSX output. Custom configs (reordered, with disabled fields, with spacers) produce correct output. When `EXPORT_COLUMN_CONFIG = false`, behavior is identical to pre-refactor. All tests pass.

---

## WP-37: Column Configuration UI on Settings Screen (F3)

**Status:** done
**Owns:** none
**Reads:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/ExportColumn.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/MainScreen.kt` (for existing component patterns), `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/FeatureFlags.kt`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/SettingsScreen.kt`
**Depends on:** WP-35

**Scope:**
Add an "Export Columns" configuration section to the Settings screen, gated behind `FeatureFlags.EXPORT_COLUMN_CONFIG`:

1. **Feature flag gate**: The entire "Export Columns" CwCard section is only rendered when `FeatureFlags.EXPORT_COLUMN_CONFIG` is `true`. When `false`, the Settings screen looks exactly as it does today.
2. **New CwCard section** below the existing Privacy section, titled "Export Columns" via `SectionHeader`.
3. **Column list**: Each row displays:
   - `Checkbox` — toggles `enabled` for Field columns
   - Field name label (or "Empty Column" in italic for spacers)
   - Up/down `IconButton`s (`Icons.Default.KeyboardArrowUp` / `KeyboardArrowDown`) — swap with neighbor, disabled at top/bottom
   - Remove `IconButton` (`Icons.Default.Close`) — only shown for Spacer rows
4. **"+ Add Empty Column" button** (CwSecondaryButton) below the list — appends a new Spacer
5. **Preset buttons row** above the column list: "All Fields" and "Essential Only" (CwSecondaryButton style)
   - "All Fields": resets to `ExportColumnConfig.default()` (all 22 fields enabled, default order, no spacers)
   - "Essential Only": curated subset — First Name, Last Name, DOB, Case ID, Authorization #, Appointment Date, Appointment Time, Services — others unchecked
6. **"Reset to Defaults" link or button** — calls `ExportPreferences.reset()`
7. **State management**: `var columnConfig by remember { mutableStateOf(ExportPreferences.load()) }` — each UI action updates state and immediately calls `ExportPreferences.save()`
8. **Scrollable content**: Wrap Settings screen content in `verticalScroll` since the column list may be tall

**Acceptance:** When `EXPORT_COLUMN_CONFIG = false`, Settings screen is unchanged. When `true`, column config UI renders. Checkboxes toggle field inclusion. Up/down buttons reorder. Spacers can be added/removed. Presets work. Config persists across app restarts. All tests pass.

---

## WP-38: Fix Main Screen Title and Subtitle Text (E17)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/MainScreen.kt`
**Depends on:** WP-33

**Scope:**
Two text fixes on the main screen:
1. Remove the "S&C" prefix from the header title. Change `"S&C Batch Authorization Processor"` to `"Batch Authorization Processor"`.
2. Remove abbreviations from the subtitle. Change `"Gather authorization information from MD DDS service authorization forms"` to spell out the abbreviation — `"Gather authorization information from Maryland Disability Determination Services authorization forms"`.

**Acceptance:** Main screen header reads "Batch Authorization Processor". Subtitle has no abbreviations. Build compiles and all tests pass.

---

## WP-39: Drag-and-Drop Column Reordering (F4)

**Status:** done
**Owns:** none
**Reads:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/ExportColumn.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/ExportPreferences.kt`
**Touches:** `app/build.gradle.kts`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/SettingsScreen.kt`
**Depends on:** WP-37

**Scope:**
Add drag-and-drop reordering to the Export Columns configuration list on the Settings screen:

1. **Add dependency**: `sh.calvin.reorderable:reorderable:3.0.0` in `app/build.gradle.kts`
2. **Migrate to LazyColumn**: Convert the Export Columns section from `Column` + `forEachIndexed` to a `ReorderableLazyColumn` inside a fixed-height container (e.g., `400.dp`). Each row keyed by field ID or spacer ID.
3. **Nested scroll handling**: The Export Columns list lives inside the outer Settings `verticalScroll`. Use `Modifier.nestedScroll` or a fixed-height `Box` to prevent gesture conflicts between the inner `LazyColumn` and the outer scroll.
4. **Drag handles**: Add a `DragIndicator` icon (6-dot grip) as the leftmost element in each row, using `Modifier.draggableHandle()` from the reorderable library.
5. **Drag feedback**: Elevated shadow on the dragged item, semi-transparent ghost at the original position, and a `BrandGreen` 2dp drop indicator line between rows showing the insertion point.
6. **Smooth animations**: Use `animateItem()` on `LazyColumn` items for reorder transitions.
7. **Auto-scroll**: List auto-scrolls when dragging near the top or bottom edges.

The `onMove` callback should update `columnConfig` state and persist via `ExportPreferences.save()`.

**Acceptance:** Drag handles visible on each row. Grabbing the handle and dragging vertically repositions the item with smooth animation. Drop indicator line shows insertion point. Auto-scroll works near edges. Existing button-based reorder (up/down arrows) still works. Config persists after drag reorder. All tests pass.

---

## WP-40: Overflow Menu and Spacer Insertion UX (F5)

**Status:** done
**Owns:** none
**Reads:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/ExportColumn.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/ExportPreferences.kt`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/SettingsScreen.kt`
**Depends on:** WP-39

**Scope:**
Add per-row overflow menus and targeted spacer insertion to the Export Columns configuration:

1. **Overflow menu**: Add a `MoreVert` (three-dot) `IconButton` as the rightmost element on each row. Clicking opens a `DropdownMenu` with:
   - **Move to Top** (`VerticalAlignTop` icon) — moves item to position 0. Disabled when already first.
   - **Move to Bottom** (`VerticalAlignBottom` icon) — moves item to last position. Disabled when already last.
   - **Divider**
   - **Insert Spacer Above** (`Add` icon) — creates a new spacer and inserts it immediately above the current row.
   - **Insert Spacer Below** (`Add` icon) — creates a new spacer and inserts it immediately below the current row.
   - **Divider + Remove** (spacer rows only) — removes the spacer from the list.
2. **Relocate "Add Empty Column"**: Move from below the list to the toolbar row (next to the preset buttons), rename to "Insert Empty Column". This button appends a spacer at the bottom (existing behavior, new location).
3. **Spacer row inline removal**: Replace the checkbox area on spacer rows with a `Close` icon button for direct single-click removal (in addition to the overflow menu "Remove" option).
4. **Visual differentiation**:
   - Disabled field labels rendered in `SoftGray` to signal exclusion
   - Spacer labels rendered in `SoftGray` italic to differentiate from field rows
   - Drag handle icons in `SoftGray`

**Acceptance:** Three-dot menu visible on each row. "Move to Top" and "Move to Bottom" work correctly. "Insert Spacer Above/Below" creates spacers at the correct position. Spacers removable via inline close button or overflow menu. "Insert Empty Column" button appears in toolbar row next to presets. Disabled fields and spacers visually differentiated. Config persists. All tests pass.

---

## Dependency Graph

```
WP-0 (PDF Text Extraction) ──> WP-1 (Field Parsing) ──┬──> WP-3 (XLSX Output) ──┐
                                                        │                          │
WP-2 (Table Extraction) ───────────────────────────────┘                          ├──> WP-4 (Desktop UI) ──> WP-6 (Packaging)
                                                                                   │
WP-7 (Remove Confidence) ──> WP-9 (Harden Regex) ──> WP-10 (Parsing Feedback) ──┬──> WP-12 (Fix Regex Bugs) ──> WP-15 (Fix B9/B10/B11) ──> WP-17 (Hybrid Text)
                                                                                   └──> WP-14 (Results Cards) ──> WP-16 (Debug Masking) ──> WP-18 (PHI Toggle)
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
**Wave 9** (after WP-12/WP-14, parallel): WP-15, WP-16
**Wave 10** (after WP-15): WP-17
**Wave 11** (after WP-16): WP-18
**Wave 12** (after WP-18, parallel): WP-19, WP-20, WP-21, WP-22
