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

## WP-41: Clean Up Spacer Row Remove Button Duplication (E18)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/SettingsScreen.kt`
**Depends on:** WP-40

**Scope:**
Remove the redundant `Close` icon button that appears next to the up/down arrows on spacer rows. Spacer rows already have an inline `Close` button in the checkbox area (left side) and a "Remove" option in the overflow menu — the third remove button next to the arrows is unnecessary clutter.

In `ExportColumnRow`, remove the `if (onRemove != null)` block that renders the remove `IconButton` between the down arrow and the overflow menu. Keep the `Spacer(modifier = Modifier.width(36.dp))` fallback so field rows stay aligned with the overflow menu, but spacer rows should also just get the same spacer (no extra close button). The inline close button on the left and the overflow menu "Remove" remain as the two removal paths.

**Acceptance:** Spacer rows show only the inline close button (left side) and the overflow menu "Remove" — no close button next to the arrows. Field rows are unchanged. Row alignment is preserved. Build compiles and all tests pass.

---

## WP-42: Add Reset Button to Export Columns Settings (E19)

**Status:** done
**Owns:** none
**Reads:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/ExportPreferences.kt`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/SettingsScreen.kt`
**Depends on:** WP-40

**Scope:**
Add a "Reset" button to the Export Columns toolbar row (alongside "All Fields", "Essential Only"). Clicking it calls `ExportPreferences.reset()` and resets `columnConfig` state to `ExportColumnConfig.default()`. This provides a clear "start over" affordance.

**Acceptance:** "Reset" button appears in the preset toolbar row. Clicking it restores the default 22-field configuration with all fields enabled, default order, and no spacers. Config persists after reset. Build compiles and all tests pass.

---

## WP-43: Add Scrollbar to Export Columns List (E20)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/SettingsScreen.kt`
**Depends on:** WP-39

**Scope:**
Add a visible vertical scrollbar to the Export Columns `LazyColumn` list when its content exceeds the fixed-height container (400.dp). Use the same `VerticalScrollbar` + `rememberScrollbarAdapter` pattern already used for the outer Settings scroll.

In `ExportColumnReorderableList`, wrap the `LazyColumn` in a `Box` (already exists) and add a `VerticalScrollbar` aligned to `Alignment.CenterEnd` using `rememberLazyListState()` (already available as `lazyListState`). Use `rememberScrollbarAdapter(lazyListState)` for the adapter.

**Acceptance:** When the column list exceeds the visible area, a scrollbar appears on the right side of the list. Scrollbar follows the existing app styling. Drag-and-drop still works. Build compiles and all tests pass.

---

## WP-44: Remove Insert Empty Column Button (E21)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/SettingsScreen.kt`
**Depends on:** WP-40

**Scope:**
Remove the "Insert Empty Column" `CwSecondaryButton` from the Export Columns toolbar row. Spacer insertion is now handled via the per-row overflow menu ("Insert Spacer Above" / "Insert Spacer Below"), making the top-level button redundant.

**Acceptance:** No "Insert Empty Column" button in the toolbar row. Spacer insertion still works via overflow menus. Build compiles and all tests pass.

---

## WP-45: Improve Save Results UX on Results Screen (E22)

**Status:** done
**Owns:** none
**Reads:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`
**Depends on:** none

**Scope:**
Improve the save-to-XLSX feedback on the Results screen:

1. **Right-align the save result text** so it appears near the save button rather than at the left edge.
2. **Make the text orange** using the app's existing `BrandOrange` (or define one if needed — check `ui/theme/` for the current palette).
3. **Make the filename clickable** — clicking it opens the saved `.xlsx` file with the system's default application using `java.awt.Desktop.getDesktop().open(file)`.
4. **Add a directory link in parentheses** immediately after the filename — e.g., `patient-referrals-2026-03-03-143022.xlsx (Open folder)`. Clicking "Open folder" opens the containing directory using `java.awt.Desktop.getDesktop().open(file.parentFile)`.

Both links should use underlined text or a visual cue indicating clickability (`Modifier.clickable`, pointer cursor if available).

**Acceptance:** After saving, the result text is right-aligned near the save button and displayed in orange. The filename is clickable and opens the file. The parenthesized folder link opens the directory. Build compiles and all tests pass.

---

## WP-46: Move Export Columns Configuration to Dedicated Screen (E23)

**Status:** done
**Owns:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ExportSettingsScreen.kt`
**Reads:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/SettingsScreen.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/HelpScreen.kt` (layout pattern reference)
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/SettingsScreen.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/Main.kt`
**Depends on:** WP-44

**Scope:**
Extract the Export Columns configuration into its own dedicated screen:

1. **Create `ExportSettingsScreen.kt`**: A new screen composable that contains all the export column configuration UI currently in the "Export Columns" `CwCard` on SettingsScreen — the description text, preset buttons row (All Fields, Essential Only, Reset), the reorderable column list (`ExportColumnReorderableList`), `ExportColumnRow`, and all supporting state/callbacks. Follow the same layout pattern as HelpScreen (header, subtitle, scrollable content, back button).
2. **Add a `Screen.EXPORT_SETTINGS` enum value** (or equivalent) in Main.kt and wire navigation to/from the new screen.
3. **Replace the Export Columns card on SettingsScreen** with a navigation row: a "Export Columns" label with a chevron or button that navigates to the new ExportSettingsScreen. Keep the feature flag gate — when `FeatureFlags.EXPORT_COLUMN_CONFIG` is `false`, neither the navigation row nor the screen is available.
4. **Move all related private functions and constants** (`ESSENTIAL_FIELD_IDS`, `stableKey()`, `ExportColumnReorderableList`, `ExportColumnRow`) from SettingsScreen.kt to ExportSettingsScreen.kt.

**Acceptance:** Export column configuration lives on its own screen accessible from Settings. SettingsScreen shows a navigation row instead of the full column config inline. The ExportSettingsScreen has a Back button returning to Settings. All existing functionality preserved (drag-and-drop, overflow menus, presets, checkboxes). Build compiles and all tests pass.

---

## WP-47: Add Reset Confirmation Dialog with Text Input (E24)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ExportSettingsScreen.kt`
**Depends on:** WP-46

**Scope:**
Add a confirmation dialog to the Reset button on the Export Settings screen:

1. Clicking "Reset" opens an `AlertDialog` instead of immediately resetting.
2. The dialog explains that this will restore all columns to their default configuration.
3. The dialog contains a `TextField` where the user must type the word "reset" (case-insensitive) to enable the confirm button.
4. The confirm button is disabled until the text field contains "reset".
5. On confirm: call `ExportPreferences.reset()`, set `columnConfig = ExportColumnConfig.default()`, dismiss the dialog.
6. A cancel button dismisses the dialog without changes.

**Acceptance:** Clicking Reset shows a confirmation dialog. The confirm button is disabled until "reset" is typed. Confirming resets the config. Cancelling dismisses without changes. Build compiles and all tests pass.

---

## WP-48: Add "Expand Services to Individual Rows" Option (F6)

**Status:** done
**Owns:** none
**Reads:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/ExportColumn.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/SpreadsheetWriter.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/ReferralFields.kt`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ExportSettingsScreen.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/ExportColumnConfig.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/ExportPreferences.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/SpreadsheetWriter.kt`
**Depends on:** WP-46

**Scope:**
Add a checkbox to the Export Settings screen (above the column list) that controls whether services are expanded to individual rows:

1. **UI**: Add a labeled `Checkbox` above the column list: "Place each service on its own row (duplicate other fields)". When checked, each service CPT code for a referral gets its own row in the XLSX output, with all other field values duplicated across those rows.
2. **Data model**: Add an `expandServices: Boolean = false` property to `ExportColumnConfig`. Update serialization and persistence (`ExportPreferences`) to include this field.
3. **SpreadsheetWriter**: When `expandServices` is `true` and a referral has multiple services, write one row per service. The "services" column for each row contains just the single CPT code. All other field values are duplicated verbatim.
4. **Default behavior**: `expandServices = false` (current behavior — all services joined as comma-separated in one row).

**Acceptance:** Checkbox visible on Export Settings screen above the column list. When unchecked, behavior is unchanged (services comma-separated in one cell). When checked, each service gets its own row with duplicated field values. Setting persists. Existing tests still pass. Add at least one new test verifying the expansion behavior.

---

## WP-49: Add Export Settings Button on Results Screen (E25)

**Status:** done
**Owns:** none
**Reads:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/Main.kt`
**Depends on:** WP-46

**Scope:**
Add a settings button to the Results screen action bar:

1. Add a `CwSecondaryButton` with text "Export Settings" (or an `IconButton` with a settings gear icon) to the left of the "Start Over" button in the action buttons row.
2. Clicking it navigates to the Export Settings screen (`Screen.EXPORT_SETTINGS`).
3. Add an `onNavigateToExportSettings` callback parameter to `ResultsScreen` and wire it in Main.kt.
4. Gate behind `FeatureFlags.EXPORT_COLUMN_CONFIG` — only show the button when the feature flag is enabled.

**Acceptance:** Settings/gear button visible to the left of "Start Over" on the Results screen. Clicking navigates to Export Settings. Back from Export Settings returns to Results. Button hidden when feature flag is off. Build compiles and all tests pass.

---

## WP-50: Fix Help Screen Back Navigation from Results Screen (B14)

**Status:** done
**Owns:** none
**Reads:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/Main.kt`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/Main.kt`
**Depends on:** none

**Scope:**
When the user navigates to Help & Support from the Results screen (via the help icon button added in WP-29), pressing Back on the Help screen returns to the Main screen instead of the Results screen.

The likely cause is that the Help screen's `onBack` callback is hardcoded to navigate to `Screen.MAIN` rather than returning to the previous screen. Fix this so that Back returns to whatever screen the user came from. Approaches:
- Track the previous screen in a variable (e.g., `var previousScreen`) and set it before navigating to Help. The Help screen's `onBack` restores `previousScreen`.
- Or pass a dynamic `onBack` lambda from Main.kt that captures the correct return screen at navigation time.

**Acceptance:** Navigating Results → Help → Back returns to Results. Navigating Main → Help → Back returns to Main (existing behavior preserved). Build compiles and all tests pass.

---

## WP-51: Migrate to Compose Multiplatform Navigation (R3)

**Status:** done
**Owns:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/Main.kt`
**Reads:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/*.kt`, `app/build.gradle.kts`
**Touches:** `app/build.gradle.kts`, all screen composables (callback signature changes)
**Depends on:** WP-46, WP-49, WP-50

**Scope:**
Replace the hand-rolled `enum class Screen` / `mutableStateOf` / `when` navigation in Main.kt with the official Compose Multiplatform navigation library (`org.jetbrains.androidx.navigation:navigation-compose`):

1. **Add the navigation-compose dependency** to `app/build.gradle.kts`.
2. **Define a navigation graph** using `NavHost` and `composable()` routes, replacing the current `Crossfade` + `when (screen)` block in `App()`.
3. **Replace the `Screen` enum** with string route constants (or a sealed class of route objects).
4. **Wire `NavController`** for all screen transitions — forward navigation, back navigation, and "start over" (pop to root).
5. **Preserve the `Crossfade` animation** (or migrate to `AnimatedNavHost` for equivalent transition animations).
6. **Hoist shared state** (selectedFiles, fileStates, processingResults) appropriately — either via a shared ViewModel, `rememberSaveable`, or by passing them through the nav graph's back stack entry arguments.
7. **Remove `previousScreen` tracking** — the NavController's built-in back stack handles return-to-previous-screen automatically.
8. **Update all screen composables** to accept navigation callbacks from the NavController rather than raw lambdas where appropriate.

This is a refactor — no user-visible behavior changes. All existing navigation flows must continue to work identically.

**Acceptance:** App uses `NavHost`/`NavController` for all screen navigation. Back button behavior is correct for all flows (Main→Help→Back, Results→Help→Back, Settings→ExportSettings→Back, etc.). Crossfade or equivalent transition animation preserved. No `Screen` enum or manual `previousScreen` tracking. Build compiles and all tests pass.

---

## WP-52: Define Manual End-to-End Test Script (T1)

**Status:** done
**Owns:** `docs/testing/manual-e2e-tests.md`
**Reads:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/*.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/Main.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/SpreadsheetWriter.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/ExportColumn.kt`, `docs/spec/field-mapping.json`
**Touches:** none
**Depends on:** WP-51

**Scope:**
Create a concise manual end-to-end test script (`docs/testing/manual-e2e-tests.md`) designed to verify the app's core functionality within 3 minutes or less. The script should:

1. **Define prerequisites**: what test PDFs are needed (count, characteristics — e.g., multi-service, single-service, malformed), how to launch the app.
2. **Cover these functional areas** with numbered, step-by-step test cases:
   - **File selection**: drag-and-drop, file picker, file removal, duplicate rejection
   - **Processing**: progress bar, completion transition
   - **Results screen**: referral cards display, field values present, warning/error indicators, PHI masking toggle (eye icon), text selection (triple-click to copy)
   - **XLSX export**: save to file, verify file opens in Excel/Sheets, correct column order, date formatting, expand-services row expansion
   - **Export Settings**: navigate from Settings and from Results, preset buttons (All Fields, Essential Only), drag reorder, checkbox toggle, spacer insertion via overflow menu, reset confirmation dialog (type "reset"), expand services checkbox, back navigation returns to correct screen
   - **Settings**: privacy toggle (show/unmask by default), navigation to Export Settings via chevron row
   - **Help screen**: accessible from Main and Results, back returns to originating screen
   - **Navigation**: all forward/back flows, Start Over clears state, screen transitions animate
3. **Optimize for speed**: group related checks into single test flows (e.g., process files → check results → export → verify file in one pass). Avoid redundant navigation. Aim for a tester to complete all cases in under 3 minutes with familiarity.
4. **Mark critical vs. nice-to-have**: flag which tests are P0 (must pass for release) vs. P1 (important but not blocking).
5. **Include a quick-pass checklist** at the top — a condensed version (10-15 checkboxes) for rapid smoke testing in under 1 minute.

**Acceptance:** A `docs/testing/manual-e2e-tests.md` file exists with a complete, time-budgeted test script. Each test case has clear steps, expected results, and pass/fail criteria. The full script is executable in ≤3 minutes. A quick-pass smoke checklist is included at the top.

---

## WP-53: Research Navigation Animation Strategy (R4)

**Status:** done
**Owns:** `docs/research/navigation-animation-strategy.md`
**Reads:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/Main.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/*.kt`, `docs/brand/carbon-works-brand-guidelines.md`
**Touches:** none
**Depends on:** WP-51

**Scope:**
Research and define a navigation animation strategy that communicates spatial relationships and user intent through motion. Produce `docs/research/navigation-animation-strategy.md` covering:

1. **Audit the current navigation graph** and categorize each transition by semantic type:
   - **Forward workflow** (Main → Processing → Results): linear progression
   - **Lateral/overlay** (Main → Help, Main → Settings, Results → Help): contextual detour, user expects to return
   - **Drill-down** (Settings → Export Settings): hierarchical depth
   - **Reset** (Start Over → Main): state clearing, return to origin
   - **Back** (any screen → previous): reversal of the above

2. **Research Compose animation APIs** relevant to communicating these semantics:
   - `AnimatedNavHost` enter/exit/popEnter/popExit transitions
   - `slideInHorizontally` / `slideOutHorizontally` for lateral and hierarchical motion
   - `slideInVertically` / `slideOutVertically` for overlay/modal-feel screens
   - `fadeIn` / `fadeOut` with `tween` / `spring` easing
   - `scaleIn` / `scaleOut` for emphasis
   - `SharedTransitionLayout` / `AnimatedContent` for shared element transitions (e.g., file list → processing cards)
   - `EnterTransition` / `ExitTransition` combinators (`+` operator for layered effects)
   - Material Motion patterns (container transform, shared axis, fade through)

3. **Survey industry conventions** for desktop app navigation animation:
   - macOS system preferences drill-down (horizontal slide)
   - Windows Settings app (fade + slide)
   - IntelliJ/Android Studio panel transitions
   - Material Design motion guidelines (shared axis for related content, fade through for unrelated)

4. **Propose a concrete animation map**: for each transition pair (e.g., Main→Help, Help→Main, Settings→ExportSettings, ExportSettings→Settings), specify the exact enter/exit transition combination with duration and easing. Justify each choice in terms of what it communicates to the user.

5. **Identify implementation complexity**: which transitions are straightforward (`slideIn`/`fadeIn` on `AnimatedNavHost`), which require `SharedTransitionLayout` or custom work, and recommended implementation order.

**Acceptance:** A research document exists at `docs/research/navigation-animation-strategy.md` with categorized transitions, API survey, industry conventions, a concrete animation map for every navigation pair, and an implementation plan with complexity ratings.

---

## WP-54: Fix Expand Services Checkbox Alignment on Export Settings Screen (B15)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ExportSettingsScreen.kt`
**Depends on:** WP-48

**Scope:**
The "Place each service on its own row" checkbox on the Export Settings screen is indented in a way that doesn't communicate any meaningful information hierarchy. It should align with the surrounding content (preset buttons row, column list) rather than being visually nested under something it doesn't belong to.

Review the current layout and adjust the checkbox row's padding/alignment so it sits at the same indentation level as the preset buttons and the column list header area. The checkbox is a top-level export option, not a sub-item of any particular column or preset.

**Acceptance:** The expand services checkbox row is visually aligned with the preset buttons and column list — no misleading indentation. Build compiles and all tests pass.

---

## WP-55: Add Gutter Between Privacy Toggle Description and Switch (B16)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/SettingsScreen.kt`
**Depends on:** none

**Scope:**
On the Settings screen, the "Show extracted data by default" description text and the Switch toggle sit in a Row with no horizontal spacing between them. The description text can run right up against the toggle, lacking a visual gutter to separate the two elements.

Add appropriate horizontal spacing (e.g., `Spacer(modifier = Modifier.width(16.dp))` or padding on the Column/Switch) so the description text and the toggle have a clear shared gutter between them.

**Acceptance:** Visible horizontal gap between the privacy description text and the Switch toggle. The text does not crowd the toggle. Build compiles and all tests pass.

---

## WP-56: Replace Auto-Save with System Save Dialog (E26)

**Status:** done
**Owns:** none
**Reads:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/SpreadsheetWriter.kt`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`
**Depends on:** none

**Scope:**
Replace the current "Save to XLSX" button and auto-save behavior with a standard system save dialog:

1. **Rename the button** from "Save to XLSX" to "Save".
2. **Open a native file save dialog** (e.g., `java.awt.FileDialog` in save mode or `javax.swing.JFileChooser` with `showSaveDialog`) when the user clicks Save. The dialog should:
   - Default the file type filter to `.xlsx`
   - Suggest a default filename (e.g., `referral-export-YYYY-MM-DD.xlsx` or similar based on current behavior)
   - Remember the last-used save directory across sessions using `java.util.prefs.Preferences` (the same pattern the app already uses for remembering the file picker directory). On first launch, default to the user's Documents folder or desktop.
3. **Write the XLSX to the user-chosen path** only after they confirm in the dialog.
4. **Preserve the existing save feedback UX**: inline orange clickable filename + "(Open folder)" link after a successful save, error message on failure.
5. **Remove any auto-generated filename/path logic** that bypasses user choice.

Use `java.util.prefs.Preferences` for directory memory — this is the standard JVM approach and matches the existing file picker directory persistence pattern in the app.

**Acceptance:** Button reads "Save". Clicking it opens a native OS save dialog defaulting to `.xlsx`. The dialog remembers the last-used directory. File is written to the user-chosen location. Save feedback (filename link, open folder) still works. Build compiles and all tests pass.

---

## WP-57: Reposition Save Results Links and Polish (E27)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`
**Depends on:** WP-56

**Scope:**
Move the save results feedback (filename link and open-folder link) from inline with the action buttons to just above the Save button, and polish the presentation:

1. **Relocate the save feedback** (orange clickable filename + open-folder link) from its current position inline/right-aligned in the action buttons Row to a line immediately above the action buttons row.
2. **Animate the layout change** when the feedback appears after saving — use `AnimatedVisibility` (or equivalent) so the feedback row slides/fades in rather than popping into existence and pushing content around.
3. **Fix the open-folder link**: remove the parentheses from the clickable text. Instead of "(Open folder)" with the parens as part of the link, render it as "Open folder" without parentheses (or use a non-clickable separator between the filename and the folder link).

**Acceptance:** Save feedback appears on its own line above the action buttons, animated into view. The filename is clickable (opens file). "Open folder" text has no parentheses and is clickable (opens directory). Layout shift is smooth, not jarring. Build compiles and all tests pass.

---

## WP-58: Review and Correct Default Export Filename Terminology (R5)

**Status:** done
**Owns:** none
**Reads:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/SpreadsheetWriter.kt`, `docs/spec/field-mapping.json`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt` (or wherever the default filename is generated)
**Depends on:** none

**Scope:**
The default save filename currently uses the term "patient referrals" which may not accurately describe the document contents. The PDFs being processed are service authorizations issued by Maryland DDS (Disability Determination Services), not patient referrals.

1. **Research**: Review the extraction code, field mapping, and any sample output to confirm what the documents actually are — service authorizations, consultative examination authorizations, referral letters, or something else.
2. **Determine the correct terminology** that matches the document type. The app title already uses "Authorization Processor" and the UI uses "authorization" language throughout (per WP-33 rebrand).
3. **Update the default filename** to use consistent, accurate terminology (e.g., `service-authorizations-YYYY-MM-DD.xlsx` or `authorizations-YYYY-MM-DD.xlsx` instead of `patient-referrals-...`).

**Acceptance:** Default export filename uses terminology consistent with the actual document type and the rest of the application's language. Build compiles and all tests pass.

---

## WP-59: Fix File Drag-and-Drop on Main Screen (B17)

**Status:** done
**Owns:** none
**Reads:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/MainScreen.kt`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/MainScreen.kt`
**Depends on:** none

**Scope:**
File drag-and-drop on the main file selection screen is not working. Investigate and fix the drop handling so users can drag PDF files from the OS file explorer onto the app to add them to the batch.

1. **Diagnose**: Read MainScreen.kt and identify how drag-and-drop is currently implemented. Check whether it uses Compose `Modifier.onExternalDrag` APIs, AWT `DropTarget`, or `java.awt.dnd` — and whether the handler is actually wired up and receiving events.
2. **Fix**: Ensure the drop zone correctly receives file drop events, filters for `.pdf` files, and adds them to the file list. The drop zone should provide visual feedback (hover state) when files are dragged over it.
3. **Test on Desktop**: Drag-and-drop on Compose Desktop typically requires AWT interop (`java.awt.dnd.DropTarget` on the ComposeWindow) or the Compose `DragAndDropTarget` modifier. Ensure the approach works on Windows (primary target).

**Acceptance:** Users can drag PDF files from Windows Explorer onto the app's file selection area and have them added to the batch. Visual hover feedback is shown during drag. Non-PDF files are ignored or filtered. Build compiles and all tests pass.

---

## WP-60: Move Expand Services Checkbox Above Preset Buttons (E28)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ExportSettingsScreen.kt`
**Depends on:** WP-54

**Scope:**
Move the "Place each service on its own row" checkbox from its current position (between the preset buttons and the column list) to above the preset buttons row. The checkbox is a global export option and should appear before the column-specific controls.

**Acceptance:** The expand services checkbox appears above the All Fields / Essential Only / Reset buttons row. Build compiles and all tests pass.

---

## WP-61: Add Spacing Below Subtitle Text on Main and Results Screens (B18)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/MainScreen.kt`
**Depends on:** none

**Scope:**
Add more vertical whitespace below subtitle text on two screens:

1. **Results screen**: Add spacing below the "[#] successful extraction[s]" summary text. It currently sits too close to the content below it.
2. **Main screen**: Add spacing below the "Gather authorization..." subtitle text. It currently sits too close to the content below it.

**Acceptance:** Visible additional spacing below the subtitle text on both screens. Build compiles and all tests pass.

---

## WP-62: Move Start Over Button to Far Left on Results Screen (E29)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`
**Depends on:** WP-57

**Scope:**
Move the "Start Over" button from its current position (grouped with Save and Export Settings) to the far left of the action buttons row, where the Back button typically sits on other screens. This provides consistent placement for the "go back / leave this screen" action.

The Save button and Export Settings button should remain right-aligned. The layout should be: Start Over on the left, spacer in the middle, Export Settings and Save on the right.

**Acceptance:** Start Over button sits at the far left of the action bar. Save and Export Settings remain right-aligned. Build compiles and all tests pass.

---

## WP-63: Fix Claimant Cell Parsing to Handle Multi-Line Address (B19)

**Status:** done
**Owns:** none
**Reads:** `docs/spec/field-mapping.json`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/FieldParser.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`
**Depends on:** none

**Scope:**
The `parseClaimantCell()` method in FieldParser.kt assumes the claimant information cell is a single run of text (name + street + city/state/zip + phone all space-separated). In reality, the PDF cell contains multiple lines:

```
Claimant Information
FIRST MIDDLE LAST
123 STREET ADDRESS
CITY, ST 12345
555-123-4567
```

The current heuristic (line 597) tries to split name from street address by finding the first digit-starting word, but only when there are 4+ words before the city. This fails when newlines are collapsed or the word count doesn't match expectations, causing `streetAddress` to come back null even when the data is present in the document.

Two fixes needed:

1. **Extraction fix**: Rewrite `parseClaimantCell()` to be line-aware. Split on newlines first (after removing the "Claimant Information" prefix). If the cell has distinct lines, parse them structurally: line 1 = name, line 2 = street address, line 3 = city/state/zip, line 4 = phone. Fall back to the current single-line heuristic only if the cell is truly one line. Update or add unit tests for both multi-line and single-line cell formats.

2. **Display fix**: In ResultsScreen.kt, when `streetAddress` is missing but city/state/zip exists, show the city/state/zip with the "Address" label instead of as a label-less continuation row. This provides a safety net for any remaining edge cases where street address extraction still fails.

**Acceptance:** Street address is correctly extracted from multi-line claimant cells. City/state/zip displays with "Address" label when street address is missing. Existing single-line parsing still works. Unit tests cover both formats. Build compiles and all tests pass.

---

## WP-64: Add Processing Warnings for Missing Expected Fields (B20)

**Status:** done
**Owns:** none
**Reads:** `docs/spec/field-mapping.json`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/FieldParser.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`
**Depends on:** none

**Scope:**
When the parser successfully extracts a referral but certain expected fields are null/empty, there is no warning surfaced to the user. The app silently skips missing fields in the results preview. Add processing warnings for missing-but-expected fields so the user knows data may be incomplete.

1. **Define expected fields**: Identify which fields should normally be present in a valid referral (e.g., claimant name, DOB, case ID, street address, city, state, zip, appointment date). Reference `field-mapping.json` for the full field list.
2. **Generate warnings**: After parsing, check the extracted `ReferralFields` for any expected fields that are null/empty. For each missing field, add a warning message (e.g., "Street address not found").
3. **Display warnings**: Ensure warnings are visible in the results card for that referral. The app already has a warnings display mechanism — use or extend it.

**Acceptance:** When a referral is parsed but expected fields are missing, the results card shows a warning listing the missing fields. Warnings do not appear for optional/rare fields. Build compiles and all tests pass.

---

## WP-65: Fix Warning Tag Text Wrapping in Results Card (B21)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`
**Depends on:** WP-64

**Scope:**
The per-card completeness warning banner displays text like `[completeness] Street address: Street address not found`. The bracketed tag `[completeness]` word-wraps because it doesn't fit in the space allotted. Fix the layout so the warning text doesn't break awkwardly. Options include:
- Removing or abbreviating the stage tag (e.g., drop `[completeness]` entirely since the banner context already implies it)
- Using `maxLines = 1` with ellipsize
- Giving the text more horizontal space
- Using a smaller font size for the tag

Choose the simplest approach that eliminates the word-wrap issue.

**Acceptance:** Warning text in the per-card banner does not word-wrap on the stage tag. Build compiles and all tests pass.

---

## WP-66: Preserve Line Breaks in TableExtractor Cell Text (B22)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/TableExtractor.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/FieldParser.kt`, `app/src/test/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/FieldParserTest.kt`
**Depends on:** none

**Scope:**
Root cause fix for the street address extraction failure. Tabula's `RectangularTextContainer.text` property concatenates all text elements with spaces, destroying the line structure present in multi-line PDF cells. The claimant information cell in real PDFs has name, street address, city/state/zip, and phone on separate lines, but by the time text reaches `parseClaimantCell()` it's a single space-separated string. The multi-line parser added in WP-63 never fires because `lines.size >= 2` is always false.

**Fix**: In `TableExtractor.convertTable()` (line 152), replace `cellContainer?.text` with logic that reconstructs line breaks from the cell's child text elements. Tabula's `RectangularTextContainer` contains `TextChunk` or `TextElement` children with Y-coordinate positions. When there's a significant Y-coordinate jump between consecutive chunks, insert `\n` instead of a space. This preserves the original line structure from the PDF.

Specifically:
1. In `TableExtractor.kt`, replace `cellContainer?.text?.trim()` with a helper that iterates the container's text children, groups by Y-coordinate (with a tolerance for minor vertical jitter), and joins groups with `\n` and elements within a group with spaces.
2. Add unit tests for the new line-reconstruction logic.
3. **Clean up superseded code in FieldParser.kt**: Remove `parseClaimantCellSingleLine()` entirely — once TableExtractor preserves newlines, the single-line fallback is dead code. Keep `parseClaimantCellMultiLine()` as the primary (and only) parsing path. Update `parseClaimantCell()` to always delegate to the multi-line parser. If there's truly only one line (a degenerate case), the multi-line parser should handle it gracefully.
4. **Update FieldParser tests**: Remove or update any tests that feed single-line space-separated claimant cell text, since that format no longer represents real data. Ensure the integration test (`tableWith(...)`) uses newline-separated cell text to match real Tabula output.

**Acceptance:** Real PDFs with multi-line claimant cells produce correct `streetAddress`, `city`, `state`, `zipCode` fields. The `parseClaimantCellSingleLine()` method is removed. TableExtractor preserves line breaks in all cell text. Build compiles and all tests pass.

---

## WP-67: Fix Drag-and-Drop Cursor Showing "Move" Instead of "Copy" (B23)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/MainScreen.kt`
**Depends on:** WP-59

**Scope:**
When dragging files from Windows Explorer onto the app, the cursor shows a "move" icon instead of "copy". The app only reads files — it doesn't move or modify them. The current code already uses `DnDConstants.ACTION_COPY` in `acceptDrag()`, `acceptDrop()`, and the `DropTarget` constructor, but Windows Explorer still displays the move cursor.

The likely cause is that the `DropTarget` constructor's `actions` parameter needs to be `ACTION_COPY_OR_MOVE` to accept the drag source's offered actions, but then `dragEnter`/`dragOver` must explicitly call `acceptDrag(ACTION_COPY)` to signal the copy cursor. Alternatively, the `DropTargetDragEvent` may need `acceptDrag` called with the right action in a `dragOver` handler (not just `dragEnter`).

Investigate and fix so the OS cursor shows the copy icon (+ badge) during drag-over, not the move icon.

**Acceptance:** Dragging files from Windows Explorer onto the app shows a copy cursor (not move). Files are still correctly added to the batch on drop. Build compiles and all tests pass.

---

## WP-68: Audit Logging Coverage for Triage (R6)

**Status:** done
**Owns:** none
**Reads:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/FieldParser.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/TableExtractor.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/TextExtractor.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ProcessingScreen.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/Main.kt`
**Touches:** All source files under `app/src/main/kotlin/` (logging additions only)
**Depends on:** none

**Scope:**
Audit the existing logging across the application to ensure it's sufficient for remote triage when a user reports an issue. The developer will receive log files, not screen shares.

1. **Review all existing `println()` and logging calls** across extraction, parsing, UI, and navigation code. Identify gaps where errors or important state transitions are not logged.
2. **Add structured logging** where missing. Key areas to cover:
   - PDF file open/close (success/failure, page count)
   - Table extraction results (tables found per page, cell counts)
   - Field parsing outcomes (which fields extracted vs. missed, per file)
   - Navigation events (screen transitions)
   - Export/save operations (path, success/failure)
   - Settings changes
3. **Use a consistent format**: `[Component] message` (e.g., `[Parser] Extracted 8/11 expected fields from file.pdf`). Use `println()` for now — the file logging WP will redirect stdout/stderr to file.
4. Do NOT log any PHI values — see WP-69 for the sanitization audit.

**Acceptance:** All major operations produce log output sufficient for a developer to diagnose common issues (file not parsing, fields missing, export failures) without needing to reproduce locally. Build compiles and all tests pass.

---

## WP-69: Audit Logging for PHI Sanitization (S1)

**Status:** done
**Owns:** none
**Reads:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/FieldParser.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ProcessingScreen.kt`
**Touches:** All source files under `app/src/main/kotlin/` (logging sanitization only)
**Depends on:** none

**Scope:**
Audit all existing and newly-added logging to ensure no PHI (Protected Health Information) leaks into log output. Logs will be saved to files and potentially sent to the developer for triage, so they must be safe to transmit.

1. **Search all `println()`, `print()`, logging calls, and string interpolations** that could include PHI fields: patient names, DOB, SSN, addresses, phone numbers, case IDs, diagnosis codes.
2. **Replace any PHI in log output** with masked/sanitized versions. Use patterns like:
   - File names: OK to log (they may contain PHI in the filename — mask anything after the last path separator that looks like a name, or just log the file count instead)
   - Field values: NEVER log raw values. Log field presence/absence only (e.g., "firstName: present" not "firstName: JOHN")
   - Counts and statistics: OK (e.g., "3 services extracted")
3. **Add a `LogSanitizer` utility** if needed, or use inline masking. Keep it simple.

**Acceptance:** No PHI values appear in any log output under normal operation. A grep for common PHI field names in log-producing code shows only masked/sanitized output. Build compiles and all tests pass.

---

## WP-70: File-Based Logging with Rotation (F6)

**Status:** done
**Owns:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/logging/`
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/Main.kt`
**Depends on:** WP-68, WP-69

**Scope:**
Redirect application log output to a file with a reasonable rotation/size-limiting policy, so logs are available for the "Report an Issue" feature.

1. **Create a logging setup** that writes to a log file in a standard location:
   - Windows: `%LOCALAPPDATA%/CarbonWorks/BatchAuthProcessor/logs/`
   - macOS: `~/Library/Logs/CarbonWorks/BatchAuthProcessor/`
   - Use `System.getProperty("os.name")` to pick the right path
2. **Rotation policy**: Keep the current log file and up to 2 rotated files. Rotate when the file exceeds 5MB. Name format: `app.log`, `app.1.log`, `app.2.log`.
3. **Redirect `System.out` and `System.err`** to a `TeeOutputStream` or similar that writes to both the console and the log file. This way existing `println()` calls automatically go to file without changing every call site.
4. **Initialize early** in `main()` before any other code runs.
5. **Log session start** with timestamp, app version (if available), OS, and Java version.

**Acceptance:** Application logs are written to a file in the platform-appropriate directory. Logs rotate at 5MB with 2 backups. Console output is preserved. Build compiles and all tests pass.

---

## WP-71: Report an Issue Feature — Save Logs to File (F7)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/HelpScreen.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/Main.kt`
**Depends on:** WP-70

**Scope:**
Add a "Report an Issue" button that lets the user save a copy of the application log file to a location of their choosing, so they can email it to the developer.

1. **Add a "Report an Issue" button** to the Help screen (or a prominent location — Help screen is most natural).
2. **On click**: Open a system save-as dialog (`java.awt.FileDialog` in save mode, same pattern as WP-56) with a default filename like `batch-auth-processor-log-YYYY-MM-DD.txt`.
3. **Copy the current log file** to the user-chosen path. If multiple log files exist (rotated), concatenate them in chronological order into a single file.
4. **Show success/error feedback** after the save completes, similar to the spreadsheet save feedback pattern.

**Acceptance:** User can click "Report an Issue" on the Help screen, choose a save location, and receive a copy of the application logs. The saved file contains sanitized logs with no PHI. Build compiles and all tests pass.

---

## WP-72: Rename Save Button to "Save as Spreadsheet" (E30)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`
**Depends on:** none

**Scope:**
Rename the "Save" button on the Results screen to "Save as Spreadsheet" to better communicate what the action does.

**Acceptance:** The button reads "Save as Spreadsheet". Build compiles and all tests pass.

---

## WP-73: Rename "Save as Spreadsheet" to "Export" (E31)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`
**Depends on:** WP-72

**Scope:**
Change the "Save as Spreadsheet" button text on the Results screen to "Export".

**Acceptance:** The button reads "Export". Build compiles and all tests pass.

---

## WP-74: Replace Export Settings Button with Gear Icon on Results Screen (E32)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`
**Depends on:** none

**Scope:**
On the extraction results screen, replace the "Export Settings" text button with a no-outline gear icon button. Use `Icons.Outlined.Settings` (Material) or similar. The icon should be visually lightweight — no filled background, no outline border. It should sit in the same area as the current button (right side of the action bar, next to the Export button) and navigate to the Export Settings screen on click. Add a tooltip or content description for accessibility.

**Acceptance:** A gear icon replaces the "Export Settings" text button on Results. Clicking it navigates to Export Settings. No outline or filled background on the icon. Build compiles and all tests pass.

---

## WP-75: Essential Only Preset Should Not Delete Empty Columns (B24)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ExportSettingsScreen.kt`
**Depends on:** none

**Scope:**
When clicking "Essential Only" in Export Settings, it currently removes non-essential columns from the list entirely. It should instead uncheck them (set `enabled = false`) while keeping them in the list so the user can re-enable them individually without needing to hit "All Fields" and start over. The essential columns should remain checked.

**Acceptance:** Clicking "Essential Only" unchecks non-essential columns but keeps them visible in the list. Clicking "All Fields" re-checks everything. Build compiles and all tests pass.

---

## WP-76: Rename Export Columns Screen to Export Settings (E33)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ExportSettingsScreen.kt`
**Depends on:** none

**Scope:**
The Export Settings screen's title currently reads "Export Columns" (or similar). Change it to "Export Settings" to match the navigation label used elsewhere.

**Acceptance:** The screen title reads "Export Settings". Build compiles and all tests pass.

---

## WP-77: Shorten Settings Screen Export Nav Link to "Export" (E34)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/SettingsScreen.kt`
**Depends on:** none

**Scope:**
On the Settings screen, the navigation row that leads to the Export Settings screen currently reads something like "Export Settings" or "Export Column Configuration". Shorten it to just "Export".

**Acceptance:** The Settings screen nav link reads "Export". Build compiles and all tests pass.

---

## WP-78: Add Spacing Between Back Button and List in Export Settings (E35)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ExportSettingsScreen.kt`
**Depends on:** none

**Scope:**
Add more vertical whitespace between the back button row and the column list in the Export Settings screen. The content currently feels cramped.

**Acceptance:** Visible additional spacing between the back button area and the list content. Build compiles and all tests pass.

---

## WP-79: Per-Item Unmask Button in Data Preview (F8)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`
**Depends on:** none

**Scope:**
Add a per-referral unmask/mask toggle button in the data preview section of the Results screen. Currently, the PHI mask toggle is global (all referrals mask/unmask together). This feature adds an individual eye icon per referral card so the user can unmask one at a time.

1. **Icon placement**: Place the eye toggle icon at the end of the file name row in each referral card header. It should not overlap the "Open PDF" link — if the screen is narrow, the file name already ellipsizes, so the icon stays visible.
2. **State management**: Each referral card gets its own `isMasked` boolean state. The global toggle still works as a "mask all" / "unmask all" control, but individual toggles override per-card.
3. **Icon**: Use a filled eye icon for "visible" (unmasked) and a crossed-out eye icon for "masked". Keep it visually subtle — this is a secondary action.

**Acceptance:** Each referral card in the results preview has its own eye toggle icon at the end of the filename row. Clicking it toggles masking for that card only. The global toggle still affects all cards. Icons don't overlap the Open PDF link. Build compiles and all tests pass.

---

## WP-80: Add Right Padding to Title and Description on Main Screen (B25)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/MainScreen.kt`
**Depends on:** none

**Scope:**
The title text and the description text below it on the main screen get too close to the Help and Settings icons on the right side of the header. Add right padding to the title and description text so they don't crowd the icons when the window is narrow.

**Acceptance:** Title and description text have visible spacing from the icons on the right. Build compiles and all tests pass.

---

## WP-81: Add Open Folder Link to Log Save Feedback on Help Screen (E36)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/HelpScreen.kt`
**Depends on:** WP-71

**Scope:**
When the user saves a log file via "Save Log File" on the Help screen, the success feedback text should include a clickable link to open the folder where the log was saved. The link text can be part of the save result message (e.g., "Log saved! Open folder"). Follow the same pattern used for the "Open folder" link on the Results screen after saving a spreadsheet — clickable text that calls `Desktop.getDesktop().open(parentFolder)`.

**Acceptance:** After successfully saving a log file, the feedback text includes a clickable "Open folder" link that opens the containing directory in the OS file manager. Build compiles and all tests pass.

---

## WP-82: Add Spacing Above Save Log File Button on Help Screen (E37)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/HelpScreen.kt`
**Depends on:** WP-71

**Scope:**
Add more vertical whitespace above the "Save Log File" button on the Help screen. The button currently sits too close to the content above it.

**Acceptance:** Visible additional spacing above the Save Log File button. Build compiles and all tests pass.

---

## WP-83: Update Support Log Save Instructional Text (E38)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/HelpScreen.kt`
**Depends on:** WP-71

**Scope:**
Update the instructional text above the "Save Log File" button on the Help screen to: "If you encounter a problem, save a copy of the application log to attach to your email. PDF data will be in the logs but personal health and other identifying information will be masked."

**Acceptance:** Text matches the specified copy exactly. Build compiles and all tests pass.

---

## WP-84: Add Scrollbar to Selected Files List on Main Screen (B26)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/MainScreen.kt`
**Depends on:** none

**Scope:**
The selected files list on the main screen does not show a scrollbar when there are more files than fit in the visible area. Add a vertical scrollbar so the user can see there is more content and scroll through it. Use the same scrollbar pattern used elsewhere in the app (e.g., `VerticalScrollbar` with `rememberScrollbarAdapter` or `LazyListState`).

**Acceptance:** A vertical scrollbar appears on the selected files list when content overflows. Build compiles and all tests pass.

---

## WP-85: Fix Per-Item Unmask Icon Alignment and Color (B27)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`
**Depends on:** WP-79

**Scope:**
The per-referral unmask eye icon in the results card header is vertically misaligned with the text on the same row. Fix the vertical alignment so the icon centers with the filename text. Also change the icon tint from SoftGray to BrandGreen to match other interactive icons in the app.

**Acceptance:** The eye icon is vertically centered with the filename text on the same row. The icon tint is BrandGreen. Build compiles and all tests pass.

---

## WP-86: Regenerate Multi-Resolution Icon for Windows Installer (E39)

**Status:** done
**Owns:** none
**Reads:** `docs/brand/carbon-works-brand-guidelines.md`
**Touches:** `app/src/main/resources/icon.ico`
**Depends on:** none

**Scope:**
The current `icon.ico` is only 16x16 pixels, causing blurry/pixelated appearance on modern Windows displays in the installer wizard, Start Menu, taskbar, and File Explorer. Regenerate it as a multi-resolution ICO file containing 16, 32, 48, 64, and 256 pixel layers.

The source SVG is at `app/src/main/resources/icon.svg`. Use ImageMagick or a similar tool to:
1. Render the SVG at 16, 32, 48, 64, and 256 px (square, 32-bit RGBA)
2. Combine all sizes into a single `.ico` file
3. Replace `app/src/main/resources/icon.ico`

If ImageMagick is not available, use the Python icon generator script if one exists in `tools/`, or write a small script to do the conversion.

**Acceptance:** `icon.ico` contains multiple resolutions (at minimum 16, 32, 48, 256). The file is a valid ICO. Build compiles and `./gradlew :app:packageMsi` succeeds.

---

## WP-87: Add License File to Windows Installer (E40)

**Status:** done
**Owns:** `app/src/main/resources/LICENSE.txt`
**Reads:** none
**Touches:** `app/build.gradle.kts`
**Depends on:** License research (pending)

**Scope:**
Add a license/EULA file that is displayed during MSI installation. Create `app/src/main/resources/LICENSE.txt` with appropriate license text (to be determined by research). Add `licenseFile.set(project.file("src/main/resources/LICENSE.txt"))` to the `windows {}` block in `app/build.gradle.kts`.

**Acceptance:** The installer shows a license agreement step during installation. Build compiles and `./gradlew :app:packageMsi` succeeds.

---

## WP-88: Update Installer Description to Reflect Brand (E41)

**Status:** done
**Owns:** none
**Reads:** `docs/brand/carbon-works-brand-guidelines.md`
**Touches:** `app/build.gradle.kts`
**Depends on:** none

**Scope:**
The current installer description is generic and technical: "Batch PDF data extraction tool for MD DDS service authorization processing". Update it to something that communicates the Carbon Works brand personality while remaining informative. The description appears in Windows "Apps & Features" and installer properties.

Also review the `packageName` — currently "PDF Authorization Processor". Consider whether it should include "Carbon Works" branding or remain as-is (the menu group already provides "Carbon Works" context in Start Menu).

**Acceptance:** The `description` field in `nativeDistributions` is updated with brand-aligned copy. Build compiles and all tests pass.

---

## WP-89: Rewrite License to Mirror Contract IP Terms (B28)

**Status:** done
**Owns:** `app/src/main/resources/LICENSE.txt`
**Reads:** `docs/consulting/contract-template.md` (Section 10 — Intellectual Property), `docs/consulting/contract-summary.md`
**Touches:** none
**Depends on:** none

**Scope:**
The current LICENSE.txt is overly restrictive and does not align with the executed S&C contract. The contract (Section 10) grants:

> "a perpetual, irrevocable, worldwide, royalty-free, non-exclusive license to use, copy, modify, and create derivative works from the work product for any purpose, without limitation."

The contract also includes full source code delivery. The current EULA prohibits reverse engineering, prohibits distribution, and says "non-transferable" — all of which contradict the contract terms.

Rewrite `app/src/main/resources/LICENSE.txt` to:
1. **License Grant**: Perpetual, irrevocable, worldwide, royalty-free, non-exclusive license. May use, copy, modify, and create derivative works for any purpose without limitation. Remove prohibitions on reverse engineering (source code is delivered). Remove "non-transferable".
2. **Intellectual Property**: Carbon Works retains ownership of all source code, tools, and methodologies. Client owns all data processed by the software. Carbon Works may reuse general approaches for other clients without disclosing client confidential information.
3. **Keep existing sections** (with adjustments for consistency): PHI Processing, Data Validation, Warranty Disclaimer, Limitation of Liability, Open Source Components, Termination, Governing Law.
4. **Add plain-language summary** at the top (per contract template convention): "In plain terms: Carbon Works owns the code, but you can use, modify, and build on it however you want, forever."
5. Keep format as plain text with ~80 char line width. No markdown.

**Acceptance:** LICENSE.txt reflects the contract's IP terms: perpetual, irrevocable, royalty-free license with modification and derivative work rights. No prohibitions that contradict source code delivery. Build compiles and tests pass.

---

## WP-90: Brand MSI Installer with Custom Banner and Dialog Images (E42)

**Status:** ready
**Owns:** `app/src/main/installer/banner.bmp`, `app/src/main/installer/dialog.bmp`, `app/src/main/installer/main.wxs`
**Reads:** `docs/brand/carbon-works-brand-guidelines.md`, `app/src/main/resources/icon.png`
**Touches:** `app/build.gradle.kts`
**Depends on:** none

**Scope:**
The MSI installer shows default WiX red disk images on the welcome and interior pages. Replace these with Carbon Works branded images.

**Technical approach:**
1. Generate two BMP files using Pillow from `icon.png`:
   - `banner.bmp` (493x58 pixels) — Interior page banner. Use a WarmWhite (#FAF8F5) background with the origami bird icon on the left (~48px tall) and "PDF Authorization Processor" text on the right in dark gray.
   - `dialog.bmp` (493x312 pixels) — Welcome/finish page background. Use a WarmWhite background with a larger origami bird icon centered-left (~200px), app name below it, and "Carbon Works" branding. Keep it clean and minimal per brand guidelines.
2. Copy the stock `main.wxs` from the JDK's jpackage resources (`jdk.jpackage.jmod`). Add two `WixVariable` declarations inside the `<Product>` element, before `<UIRef Id="JpUI"/>`:
   ```xml
   <WixVariable Id="WixUIBannerBmp" Value="banner.bmp" />
   <WixVariable Id="WixUIDialogBmp" Value="dialog.bmp" />
   ```
3. Add a Gradle task in `app/build.gradle.kts` that runs before `packageMsi`:
   - Copies `banner.bmp`, `dialog.bmp`, and `main.wxs` from `src/main/installer/` to `build/compose/tmp/resources/`
   - This resource dir is passed as jpackage's `--resource-dir`, so the custom files override the defaults

**Image generation script:** Create a Python script at `tools/generate_installer_images.py` that produces the BMP files from `icon.png`. Run it once to generate the BMPs and commit them. The Gradle task only copies pre-generated BMPs — no Python dependency at build time.

**Acceptance:** Running `./gradlew :app:packageMsi` produces an MSI where the installer wizard shows branded Carbon Works imagery instead of the default red disk images. Both the welcome page and interior pages show branded graphics.

---

## WP-91: Fix Vertical Alignment of Per-Card Header Row (B29)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`
**Depends on:** none

**Scope:**
On the results screen, the per-card header row containing the unmask eye icon, filename text, and "Open PDF" link is vertically misaligned. All elements in this row should be vertically centered with each other.

Inspect the Row composable for the card header in `ResultsScreen.kt` and ensure all children use `Modifier.align(Alignment.CenterVertically)` or that the Row itself uses `verticalAlignment = Alignment.CenterVertically`. Check the eye IconButton, the filename Text, and the "Open PDF" ClickableText for consistent alignment.

**Acceptance:** The eye icon, filename, and Open PDF link are visually centered on the same horizontal line in the card header row. No vertical offset between any of the three elements.

---

## WP-92: Fix Per-Card Mask Toggle Interaction with Global Toggle (B30)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`
**Depends on:** none

**Scope:**
The per-card mask toggle and the screen-level global toggle have incorrect interaction behavior. The correct behavior should be:

1. When the **global toggle** is clicked to unmask, ALL individual card toggles should be set to unmasked. When clicked to mask, ALL should be set to masked. The global toggle is a bulk "set all" operation.
2. The global toggle does NOT lock any individual state. After a global toggle, individual cards can still be flipped independently.
3. Individual card toggles work freely regardless of the current global state — they just flip that one card.

Currently, `perCardMaskOverrides.clear()` is called on global toggle, which causes cards to fall back to the global `isMasked` state. The issue is that individual per-card overrides can persist across or conflict with the global toggle in ways the user doesn't expect. Review the `perCardMaskOverrides` map logic at ~line 168 and the global toggle handler at ~line 206 in `ResultsScreen.kt`. Ensure the global toggle explicitly sets every card's visible state to match, and that subsequent individual toggles still work independently.

**Acceptance:** Clicking the global eye icon sets all cards to the same masked/unmasked state (visually and logically). Individual card eye icons can still be toggled independently after a global toggle. No card retains a stale override that contradicts the most recent global action.

---

## WP-93: Fix Per-Card Mask Toggles When Global Is Unmasked (B31)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`
**Depends on:** WP-92

**Scope:**
After WP-92, the global toggle explicitly sets every card's state in `perCardMaskOverrides`. However, per-card toggles do not work when the global toggle is set to unmasked. They should — clicking a per-card eye icon when everything is unmasked should re-mask that individual card.

Debug the issue in `ResultsScreen.kt`. The relevant code:
- Line ~208: global toggle sets `isMasked = !isMasked`, then `perCardMaskOverrides[i] = isMasked` for all cards
- Line ~408: per-card state resolved as `val cardMasked = perCardMaskOverrides[index] ?: isMasked`
- Line ~413: per-card toggle sets `perCardMaskOverrides[index] = !cardMasked`

Also check the `ReferralCard` composable and its `onToggleMask` callback — the toggle button may not be clickable or may not fire when `isMasked` is false. Trace the full path from the per-card eye icon click through to the state update to find where it breaks.

**Acceptance:** When the global toggle is set to unmasked, clicking a per-card eye icon re-masks that individual card. When the global toggle is set to masked, clicking a per-card eye icon unmasks that individual card. All tests pass.

---

## WP-94: Restrict Logging to Pages 2–3 to Prevent PHI Leakage (S2)

**Status:** done
**Owns:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/FieldParser.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/PdfTextExtractor.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/TableExtractor.kt`
**Reads:** `docs/architecture/architectural-review.md`, `CLAUDE.md`
**Touches:** none
**Depends on:** nothing

**Scope:**
The extraction pipeline has `dump` methods (e.g., `dumpExtractionContext`) that log page content from all pages of each PDF. Pages 2 and 3 contain the structured referral data the app extracts; other pages contain unpredictable free-text content (clinical notes, medical records, etc.) with PHI in formats that cannot be reliably sanitized.

1. Find all `dump` methods in the extraction pipeline and restrict them to only output content from pages 2–3
2. For non-target pages, log only the page number/index — no content
3. Check any other `println` statements that reference page content and apply the same restriction

**Acceptance:** No dump method or log statement in the extraction pipeline outputs text content from pages other than 2 and 3. Page numbers may still be logged for non-target pages, but no content. All existing tests pass.

---

## WP-95: Remove All PDF Content Logging from Extraction Pipeline (S3)

**Status:** done
**Owns:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/FieldParser.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/PdfTextExtractor.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/TableExtractor.kt`
**Reads:** `CLAUDE.md`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ProcessingScreen.kt`
**Depends on:** WP-94

**Scope:**
Remove all capability to log PDF content from the extraction pipeline. The dump methods and any println that outputs text extracted from PDFs should be deleted — not gated, not masked, removed entirely. PDF content should never reach the log file.

1. Delete `dumpPageTexts()` and `dumpPageTextsDetailed()` methods from `FieldParser.kt` and all call sites (including in `ProcessingScreen.kt`)
2. Delete any associated test coverage for the dump methods
3. Remove the `TARGET_PAGES` constant added in WP-94 (no longer needed)
4. Audit remaining `println` statements in the three extraction files — keep only:
   - Structural metadata: page counts, block counts, table dimensions, strategy names
   - Parsing outcomes: field counts, warning counts, stage results
   - Error class names (e.g., `IOException`, `InvalidPasswordException`) — no `e.message` content
5. Remove or replace any `println` that outputs file names/paths (they may contain patient names) — use file index or a generic label instead
6. Remove or replace any `println` that outputs `e.message` from PDF library exceptions (messages may embed PDF content) — log `e::class.simpleName` only

**Acceptance:** No `println` or log statement in the extraction pipeline outputs any text content from PDFs. No dump methods exist. File names are not logged. Exception messages are not logged (class names only). Structural metadata and parsing outcome counts are still logged. All remaining tests pass.

---

## WP-96: Replace EULA with Apache 2.0 License (L1)

**Status:** done
**Owns:** `app/src/main/resources/LICENSE.txt`
**Reads:** `CLAUDE.md`
**Touches:** none
**Depends on:** nothing

**Scope:**
Replace the custom EULA in `app/src/main/resources/LICENSE.txt` with the standard Apache License 2.0 text. Add `Copyright 2026 Carbon Works LLC` as the copyright header above the license body. Remove all client-specific, PHI, data validation, and liability language — those belong in the services contract, not the software license.

Use the canonical Apache 2.0 text from https://www.apache.org/licenses/LICENSE-2.0.txt.

**Acceptance:** `LICENSE.txt` contains the standard Apache 2.0 license text with `Copyright 2026 Carbon Works LLC` header. No client name, PHI clauses, or EULA language remains. File is well-formatted plain text.

---

## WP-97: Add NOTICE File for Third-Party Attributions (L2)

**Status:** done
**Owns:** `app/src/main/resources/NOTICE.txt`
**Reads:** `app/build.gradle.kts` (dependency list), `CLAUDE.md`
**Touches:** none
**Depends on:** WP-96

**Scope:**
Create a `NOTICE.txt` file following the Apache convention for third-party attributions. List all bundled dependencies and their licenses:

- Apache PDFBox 3.0.4 — Apache License 2.0
- Tabula-java 1.0.5 — MIT License
- Apache POI 5.3.0 — Apache License 2.0
- Compose Multiplatform 1.7.3 — Apache License 2.0
- Kotlin 2.1.10 — Apache License 2.0
- kotlinx-serialization 1.7.3 — Apache License 2.0
- Navigation Compose 2.8.0-alpha10 — Apache License 2.0
- Reorderable 3.0.0 — Apache License 2.0
- OpenJDK 17 (bundled JRE) — GPL v2 with Classpath Exception

Place at `app/src/main/resources/NOTICE.txt`.

**Acceptance:** `NOTICE.txt` lists all runtime dependencies with their names, versions, and license types. Format is clean, scannable plain text.

---

## WP-98: Remove Installer License Screen (L3)

**Status:** done
**Owns:** none
**Reads:** `CLAUDE.md`
**Touches:** `app/build.gradle.kts`
**Depends on:** WP-96

**Scope:**
Remove the click-through license agreement dialog from the MSI installer. Apache 2.0 does not require click-through acceptance, and the client relationship is governed by the services contract.

1. Remove the `licenseFile.set(...)` line from the `windows {}` block in `app/build.gradle.kts`

**Acceptance:** Building the MSI with `./gradlew :app:packageMsi` succeeds. The installer no longer shows a license acceptance screen during installation.

---

## WP-99: Add Licensing and Attribution Info to Help Screen (L4)

**Status:** done
**Owns:** none
**Reads:** `app/src/main/resources/LICENSE.txt`, `app/src/main/resources/NOTICE.txt`, `CLAUDE.md`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/HelpScreen.kt`
**Depends on:** WP-96, WP-97

**Scope:**
Add a "Licensing" or "About" section to the Help screen that surfaces:

1. A brief notice: "Licensed under the Apache License, Version 2.0"
2. Copyright line: "Copyright 2026 Carbon Works LLC"
3. A collapsible or scrollable section listing open-source components from `NOTICE.txt`

Keep it concise — this is informational, not a legal gate. Follow the existing Help screen's visual style (use `SectionHeader`, `CwCard`, etc.).

**Acceptance:** Help screen shows Apache 2.0 license notice, copyright, and third-party attributions. Styling matches existing Help screen sections. All tests pass.

---

## WP-100: Add PHI and Data Validation Notices to Help Screen (L5)

**Status:** done
**Owns:** none
**Reads:** `app/src/main/resources/LICENSE.txt` (current EULA for reference), `CLAUDE.md`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/HelpScreen.kt`
**Depends on:** WP-99

**Scope:**
The PHI Processing and Data Validation content currently in the EULA is operationally valuable as user-facing reminders. Move this content into the Help screen as informational sections (not legal acceptance gates).

Add two sections to the Help screen:

1. **Data Privacy**: Brief reminder that the software processes PHI locally, no data leaves the machine, no internet connection required. Remind users to follow their organization's PHI handling policies.

2. **Data Validation**: Brief reminder that automated extraction may produce incomplete or inaccurate results and that users should review all extracted data before relying on it.

Keep the tone informational and concise — operational reminders, not legal boilerplate. Follow the existing Help screen style.

**Acceptance:** Help screen includes Data Privacy and Data Validation informational sections. Content is concise and non-legal in tone. Styling matches existing Help screen. All tests pass.

---

## WP-101: Fix Clipped Ripple on Open-Source Components Expand Control (B32)

**Status:** done
**Owns:** none
**Reads:** `CLAUDE.md`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/HelpScreen.kt`
**Depends on:** nothing

**Scope:**
The "Open-Source Components" expand/collapse row in the Licensing card on the Help screen has a square ripple effect because the parent `CwCard` clips its children. Add a rounded corner clip to the clickable row so the ripple follows the rounded shape instead of being cut off square.

**Acceptance:** The ripple on the expand/collapse row has rounded corners matching the card shape. All tests pass.

---

## WP-102: Fix Inaccurate Text in Help Screen (B33)

**Status:** done
**Owns:** none
**Reads:** `CLAUDE.md`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/MainScreen.kt`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/HelpScreen.kt`
**Depends on:** nothing

**Scope:**
Fix all factually inaccurate text in the Help screen. These are critical and major issues where the Help text does not match the actual UI:

1. Change `Click "Save to XLSX"` → `Click "Export"` (the button is labeled "Export", not "Save to XLSX")
2. Change `preview table` → `results cards` (the results screen uses cards, not a table)
3. Change `warnings panel` → `warnings summary` or `Warnings section` in all occurrences (there is no panel; there is a collapsible Warnings card and per-card orange banners)
4. Change the log masking claim `PDF data will be in the logs but personal health and other identifying information will be masked` → `Processing status and error information are logged, but extracted field values are never written to the log file.` (logs avoid PHI by design but do not actively mask)
5. Change `Maryland DDS service authorization PDFs` → `Maryland DDS authorization PDFs` (consistent with app terminology)
6. Remove `All rights reserved.` from the copyright line to match LICENSE.txt
7. Capitalize `batch authorization processor` → `Batch Authorization Processor` in subtitle to match app title

**Acceptance:** All text in the Help screen accurately matches the actual UI labels, features, and behavior. All tests pass.

---

## WP-103: Add Missing Feature Documentation to Help Screen (E43)

**Status:** done
**Owns:** none
**Reads:** `CLAUDE.md`, `docs/demo/walkthrough.md`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ExportSettingsScreen.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/SettingsScreen.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ProcessingScreen.kt`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/HelpScreen.kt`
**Depends on:** WP-102

**Scope:**
Several major app features are not documented in the Help screen. Add coverage for:

1. **Export Settings**: Add a section or bullets covering the gear icon on the results screen, column visibility toggles, drag-and-drop reordering, "All Fields" and "Essential Only" presets, "Place each service on its own row" option
2. **Settings screen**: Mention the gear icon on the main screen and the "Show extracted data by default" privacy toggle
3. **Per-card masking**: Add a bullet noting each card has its own eye icon to unmask individually without revealing all others
4. **Open PDF link**: Add a tip that each result card has an "Open PDF" link to view the source document
5. **Post-export links**: After exporting, click the filename to open the file or "Open folder" to see it in the file manager
6. **Start Over**: Click "Start Over" on the results screen to return to file selection for a new batch
7. **Processing screen**: Brief note between the "Process" and "Review" steps — green checkmarks indicate success, red X marks indicate errors
8. **Per-card missing field banners**: Each card shows an orange banner listing fields that need manual review
9. **Duplicate file skipping**: Files added more than once are automatically skipped

Use the existing Help screen style (CwCard, SectionHeader, HelpBullet, Tips sections). Keep additions concise — brief bullets, not paragraphs. Reference the walkthrough (`docs/demo/walkthrough.md`) for accurate descriptions.

**Acceptance:** All major app features are covered in the Help screen. New content follows existing visual style. All tests pass.

---

## WP-104: Minor Wording and Consistency Fixes in Help Screen (B34)

**Status:** done
**Owns:** none
**Reads:** `CLAUDE.md`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/HelpScreen.kt`
**Depends on:** WP-103

**Scope:**
Minor wording improvements for accuracy and completeness:

1. Change `Click "Process"` to `Click the Process button` (actual label is dynamic: "Process N File(s)")
2. Update triple-click tip to note that data must be unmasked first to select real values
3. Add note that both the file picker and export save dialog remember the last directory used

**Acceptance:** All wording is accurate and consistent. All tests pass.

---

## WP-105: Replace java.sql.Date with LocalDate in SpreadsheetWriter (B35)

**Status:** done
**Owns:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/SpreadsheetWriter.kt`
**Reads:** none
**Touches:** none
**Depends on:** nothing

**Scope:**
Fix `NoClassDefFoundError: java/sql/Date` crash in packaged MSI installer. The jlink runtime image does not include the `java.sql` module, so `java.sql.Date.valueOf()` fails at runtime.

1. In `SpreadsheetWriter.kt` line 196, replace `cell.setCellValue(java.sql.Date.valueOf(parsedDate))` with `cell.setCellValue(parsedDate)` — POI 5.3.0 natively accepts `java.time.LocalDate` in `setCellValue()`
2. Remove the `java.sql.Date` import if present (it may be used via fully-qualified name only)
3. Verify no other references to `java.sql` exist anywhere in the codebase
4. All existing tests must pass

**Acceptance:** No references to `java.sql` remain in the codebase. All tests pass. Date cells in exported XLSX files still render as formatted dates (mm/dd/yyyy).

---

## WP-106: Extract Provider Name Field (E44)

**Status:** done
**Owns:** TBD
**Reads:** `docs/spec/field-mapping.json`, `reference/python-scripts/extract_referral_fields.py`
**Touches:** TBD
**Depends on:** nothing

**Scope:**
Extract the provider name from the referral PDF. The provider name appears as the first line of the mailing address in the box to the right of the "Pay To" box. It is also the first line of the address on the page that follows. Add this as a new extractable field in `ReferralFields`, include it in the default export column config, and surface it in the UI.

**Acceptance:** Provider name is extracted and appears in results cards and XLSX export. All tests pass.

---

## WP-107: Extract Special Instructions and Examiner Contact (E45)

**Status:** done
**Owns:** TBD
**Reads:** `docs/spec/field-mapping.json`, `reference/python-scripts/extract_referral_fields.py`
**Touches:** TBD
**Depends on:** nothing

**Scope:**
Extract two new fields from the referral PDF:

1. **Special Instructions**: Found below the appointment authorization table
2. **Examiner Name and Contact**: Found after the "Thank you for your help." line

Add these as new extractable fields in `ReferralFields`, include them in the default export column config, and surface them in the UI. Note: the examiner info may have variable formatting — research agent findings should inform the parsing approach.

**Acceptance:** Special instructions and examiner contact are extracted and appear in results cards and XLSX export. All tests pass.

---

## WP-109: CSV Export Option (F9)

**Status:** done
**Owns:** TBD
**Reads:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/SpreadsheetWriter.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/MainScreen.kt`, `CLAUDE.md`
**Touches:** TBD
**Depends on:** nothing

**Scope:**
Add CSV export as an alternative to XLSX:

1. Add a checkbox to the left of the "Add Files" button on the main/file selection screen — checked = export as CSV instead of XLSX
2. Persist the preference (same pattern as other settings)
3. Create a CSV writer (or add CSV mode to the existing SpreadsheetWriter) that produces a standard RFC 4180 CSV file with the same column layout as the XLSX export
4. Wire the export button on the Results screen to use CSV or XLSX based on the preference
5. Update the filename pattern to use `.csv` extension when CSV mode is active

**Acceptance:** Checking the box and exporting produces a valid CSV file. Unchecked produces XLSX as before. Preference persists across sessions. All tests pass.

---

## WP-108: Display New Extraction Fields in Data Preview Cards (E46)

**Status:** done
**Owns:** none
**Reads:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/ReferralFields.kt`, `CLAUDE.md`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`
**Depends on:** WP-106, WP-107

**Scope:**
Add the three new fields from WP-106 and WP-107 to the data preview cards on the Results screen:

1. **Provider Name** (`providerName`) — display in the metadata section alongside existing provider/invoice fields (Federal Tax ID, Vendor Number)
2. **Special Instructions** (`specialInstructions`) — display below the services section or in its own row
3. **Examiner Name/Contact** (`examinerNameContact`) — display near special instructions

Follow the existing pattern used for other fields in ResultsScreen.kt (MetadataRow composable or similar). Respect PHI masking — these fields should be masked when the global or per-card mask toggle is active.

**Acceptance:** All three new fields appear in the data preview cards when present. Fields are masked when masking is active. All tests pass.

---

## WP-110: Update Help Screen for v1.1.0 Feature Changes (E47)

**Status:** done
**Owns:** none
**Reads:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/MainScreen.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/CsvWriter.kt`, `CLAUDE.md`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/HelpScreen.kt`
**Depends on:** WP-108, WP-109

**Scope:**
Update the Help & Support screen to document features added since v1.0.0:

1. **Three new extraction fields**: Provider Name, Special Instructions, and Examiner Name/Contact are now extracted and displayed in result cards. Mention these in the "How It Works" or data preview section.
2. **CSV export**: Document the "Export as CSV" checkbox on the file selection screen. Note that checking it exports a CSV file instead of XLSX. Mention it persists across sessions.
3. Review existing Help text to ensure nothing else is stale after these additions.

Keep additions concise — brief bullets, not paragraphs. Follow the existing Help screen style (CwCard, SectionHeader, HelpBullet).

**Acceptance:** Help screen accurately documents the new extraction fields and CSV export option. All tests pass.

---

## WP-111: Release Candidate v1.1.0-rc1 (REL)

**Status:** done
**Owns:** none
**Reads:** `docs/protocols/release-tagging.md`, `CHANGELOG.md`, `app/build.gradle.kts`
**Touches:** `CHANGELOG.md`, `app/build.gradle.kts`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/HelpScreen.kt`, `app/src/main/resources/NOTICE.txt`
**Depends on:** WP-110, WP-112–WP-121

**Scope:**
Execute the full pre-release checklist from `docs/protocols/release-tagging.md` for v1.1.0-rc1: version bump to 1.1.0, finalize changelog (move Unreleased to 1.1.0), update HelpScreen CHANGELOG_ENTRIES, FOSS licensing review, run all tests.

**Acceptance:** packageVersion is 1.1.0, changelog finalized, NOTICE.txt verified, all tests pass.

---

## WP-112: Move CSV Export Checkbox to Results Screen (U19)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/MainScreen.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`
**Depends on:** WP-109

**Scope:**
The "Export as CSV" checkbox was placed on the main file-selection screen (WP-109) but it belongs on the results screen near the settings gear icon. Move it:

1. **Remove** the "Export as CSV" checkbox and its state from `MainScreen.kt`.
2. **Add** the checkbox to `ResultsScreen.kt`, positioned near the existing settings gear (column configuration) icon. Match the existing UI style and spacing.
3. The checkbox should still read/write the preference via `ExportPreferences.getExportAsCsv()` / `setExportAsCsv()` — no changes to the persistence layer.
4. The export logic in `ResultsScreen.kt` already dispatches to `CsvWriter` or `SpreadsheetWriter` based on the preference — ensure it now reads from the local composable state rather than a passed-in parameter if the wiring changed.

**Acceptance:**
- No CSV checkbox on the main/file-selection screen
- CSV checkbox visible on the results screen near the gear icon
- Toggling the checkbox persists the preference and controls export format
- All existing tests pass

---

## WP-113: Refine Special Instructions Extraction Boundary (E48)

**Status:** done
**Owns:** none
**Reads:** `docs/spec/field-mapping.json`, `reference/python-scripts/extract_referral_fields.py`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/FieldParser.kt`, `app/src/test/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/FieldParserTest.kt`
**Depends on:** WP-107

**Scope:**
The post-table special instructions extraction (WP-107) currently captures too much text. Boilerplate like "Here is what you need to do next" and the paragraph that follows it is being included in the `specialInstructions` field. This boilerplate is a standard part of the referral letter — it is not a special instruction from the examiner.

Fix the extraction boundary in `FieldParser.kt`:

1. **Identify the boilerplate boundary.** The referral letter has a structural section between the services table and the actual special instructions. This section contains standard language like "Here is what you need to do next" (or similar phrasing). The `extractSpecialInstructions()` function must recognize this boilerplate section and exclude it from the captured text.

2. **Prefer structural understanding over pattern matching.** Currently Strategy 2 in `extractSpecialInstructions()` finds the last "section-end marker" (Fee, timezone, Procedure Type Code) and takes everything after it through "Thank you." Instead of adding yet another regex pattern to strip "what you need to do next," rethink the extraction region:
   - The boilerplate paragraph ("Here is what you need to do next…") is a **standard section** of the referral form that appears after the services table. It is not special instructions.
   - True special instructions (if any) appear as a distinct block — either labeled "Special Instructions:" or as freeform text clearly separate from the boilerplate.
   - Consider using "Thank you for your help" as the **end** of the boilerplate section rather than the start of examiner info. If there is a "Special Instructions:" label, Strategy 1 already handles it correctly. For Strategy 2, the extraction should recognize that text between the table and "Thank you" is predominantly boilerplate and should only capture text that is structurally distinct from it (e.g., text after a recognizable boilerplate closing pattern, or text that doesn't match the boilerplate paragraph structure).

3. **Update tests.** Add test cases that include "Here is what you need to do next" boilerplate and verify it is excluded. Update any existing tests whose expected values change.

4. **Run all tests.** Ensure `FieldParserTest` and all other test suites pass.

**Acceptance:**
- "What you need to do next" boilerplate is never included in `specialInstructions`
- Legitimate special instructions (labeled or freeform) still extract correctly
- Examiner name/contact extraction is unaffected
- All existing tests pass (updated as needed)

---

## WP-114: Use "What" as Structural Boundary for Special Instructions (E49)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/FieldParser.kt`, `app/src/test/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/FieldParserTest.kt`
**Depends on:** WP-113

**Scope:**
WP-113 added boilerplate detection via pattern matching (`isBoilerplateText()`), but the real fix is structural: "What You Need To Do Next" is a **form section heading** in the referral document. The special instructions region should end at "What" — it is a structural boundary in the document, not a text-analysis problem.

Fix `extractSpecialInstructions()` in `FieldParser.kt`:

1. **Remove or replace `isBoilerplateText()`.** The pattern-matching approach is fragile and misplaced. Instead, treat `What` (case-insensitive, at a word boundary) as a **structural end boundary** for the special instructions capture region — the same way "Thank you for your help" is a structural boundary.

2. **Adjust Strategy 2.** Currently Strategy 2 captures text between the last section-end marker (Fee/timezone/Procedure Type Code) and "Thank you for your help." Change the upper bound: special instructions end at whichever comes first — a line/sentence beginning with "What" or "Thank you for your help." Everything from "What..." through "Thank you for your help." is the standard boilerplate section of the form and must be excluded.

3. **Strategy 1 is unaffected.** If "Special Instructions:" is explicitly labeled, capture its content up to the "What" boundary (or "Thank you" if "What" isn't present). The label takes precedence but still respects the structural boundary.

4. **Update tests.** Replace or update the 12 boilerplate-pattern tests from WP-113 to reflect the new structural boundary approach. Add tests showing:
   - Text before "What You Need To Do Next" is captured as special instructions
   - Text from "What..." onward is excluded
   - When no "What" section exists, "Thank you" remains the boundary
   - Labeled "Special Instructions:" still works correctly

5. **Run all tests**: `./gradlew :app:test`

**Acceptance:**
- Special instructions text is cut off before "What" (exclusive)
- `isBoilerplateText()` removed or no longer the primary mechanism
- Examiner name/contact extraction is unaffected
- All tests pass

---

## WP-115: Limit Examiner Contact to Two Lines (E50)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/FieldParser.kt`, `app/src/test/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/FieldParserTest.kt`
**Depends on:** WP-113

**Scope:**
The examiner name/contact field currently captures all text after "Thank you for your help." up to a footer boundary regex. In practice the examiner info is exactly 2 lines: the examiner's name and their contact phone number. Additional lines beyond that are unrelated content leaking in.

Fix `extractExaminerInfo()` in `FieldParser.kt`:

1. **Limit capture to 2 lines.** After extracting text from the "Thank you" marker to the end boundary, split on newlines and take only the first 2 non-blank lines. Join them with a space (or preserve as-is per the existing `normalizeWhitespace` pattern).

2. **Preserve existing end boundaries.** The footer boundary regex (Assigned, RQID, Federal Tax ID, etc.) should still act as a hard stop — but within that region, only the first 2 lines matter.

3. **Update tests.** Add tests showing:
   - Examiner info with exactly 2 lines captures both
   - Examiner info with 3+ lines only captures the first 2
   - Single-line examiner info still works
   - Existing boundary tests still pass

4. **Run all tests**: `./gradlew :app:test`

**Acceptance:**
- Examiner contact captures at most 2 lines (name + phone)
- Additional lines beyond the second are excluded
- All existing tests pass (updated as needed)

---

## WP-116: Lay Out Post-Table Fields Horizontally (U20)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`
**Depends on:** WP-108

**Scope:**
The `PostTableSection` composable (around line 941 in `ResultsScreen.kt`) currently stacks Special Instructions and Examiner Contact vertically. Change it to a horizontal `Row` layout matching the `FooterSection` pattern directly below it (line 978):

1. Replace the vertical stacking with a `Row` using `Arrangement.spacedBy(24.dp)`.
2. Each field (Special Instructions, Examiner Contact) gets its own `Column` inside the row, with label above value — same as `FooterSection`.
3. Keep the same font sizes (11.sp label, 13.sp value) and styling. Use `FontWeight.Medium` for values as currently set.
4. If only one field is present, it should still display correctly (single column in the row).

**Acceptance:**
- Special Instructions and Examiner Contact are side-by-side horizontally
- Layout matches the footer section style
- Single-field display still works
- No test changes needed (UI only)

---

## WP-117: Move Post-Table Fields into Patient Metadata Section (U21)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`
**Depends on:** WP-116

**Scope:**
Special Instructions and Examiner Contact are part of the authorization document, not a separate section. Move them into the `PatientMetadataSection` composable and remove the standalone `PostTableSection`.

1. In `PatientMetadataSection`, add special instructions and examiner contact to the `metadataFields` list (after phone, before the list closes). Use labels like "Special Instructions" and "Examiner Contact".
2. Remove the `PostTableSection` composable function entirely.
3. Remove the post-table fields block from the card layout (the `hasPostTableFields` check, divider, spacer, and `PostTableSection` call around lines 712-723).
4. Do not add comments explaining the move.

**Acceptance:**
- Special Instructions and Examiner Contact appear as rows in the patient metadata section
- No standalone post-table section exists
- Layout is clean, consistent with other metadata rows

---

## WP-118: Add Provider Name to Patient Metadata Section (U22)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`
**Depends on:** WP-117

**Scope:**
Provider Name currently only appears in the footer section of the data preview card. Add it to the `PatientMetadataSection` as well, as a metadata row near the other authorization fields (after Appointment, before Address). Remove it from the `FooterSection` since it's no longer needed there.

1. In `PatientMetadataSection`, add `providerName` to the `metadataFields` list.
2. Remove the `providerName` entry from `FooterSection`'s `footerFields` list.

**Acceptance:**
- Provider Name appears in the patient metadata section of the data preview
- Provider Name no longer duplicated in the footer section

---

## WP-119: Align Field Labels to Client Terminology (U23)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/ExportColumn.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/HelpScreen.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/SpreadsheetWriter.kt`, `app/src/test/kotlin/tech/carbonworks/snc/batchreferralparser/output/ExportColumnTest.kt`, `app/src/test/kotlin/tech/carbonworks/snc/batchreferralparser/output/SpreadsheetWriterTest.kt`
**Depends on:** WP-118

**Scope:**
Align two field labels to the client's terminology across the entire app:

1. **Provider Name → Provider/Doctor Name**
   - `ResultsScreen.kt` `PatientMetadataSection`: change `"Provider"` to `"Provider/Doctor Name"`
   - `ExportColumn.kt` `DEFAULT_FIELD_ORDER`: change `"Provider Name"` to `"Provider/Doctor Name"`
   - `SpreadsheetWriter.kt` `COLUMN_HEADINGS`: update if it has its own list (or verify it derives from `DEFAULT_FIELD_ORDER`)
   - `HelpScreen.kt`: update any mention of "Provider Name"

2. **Examiner Name/Contact → Examiner Name & Contact**
   - `ResultsScreen.kt` `PatientMetadataSection`: change `"Examiner Contact"` to `"Examiner Name & Contact"`
   - `ExportColumn.kt` `DEFAULT_FIELD_ORDER`: change `"Examiner Name/Contact"` to `"Examiner Name & Contact"`
   - `HelpScreen.kt`: update any mention of "Examiner Name/Contact"

3. **Update tests** to match the new label strings. Check `ExportColumnTest.kt` and `SpreadsheetWriterTest.kt` for hardcoded label references. Also fix the stale comment on line 54 of `ExportColumnTest.kt` that says "23 fields" — it should say "25 fields".

4. **Run all tests**: `./gradlew :app:test`

**Acceptance:**
- "Provider/Doctor Name" used consistently across preview, export columns, and help text
- "Examiner Name & Contact" used consistently across preview, export columns, and help text
- All tests pass

---

## WP-120: Add Changelog to Help Screen (U24)

**Status:** done
**Owns:** none
**Reads:** `CHANGELOG.md`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/HelpScreen.kt`
**Depends on:** WP-119

**Scope:**
Add a collapsible "What's New" section to the Help screen that displays the changelog. Follow the same collapsible pattern used by `LicensingCard()` (around line 435 in `HelpScreen.kt`):

1. **Create a `ChangelogCard()` composable** in `HelpScreen.kt`, modeled after `LicensingCard()`. It should:
   - Show a "What's New" section header
   - Display the current version's changes by default (collapsed shows just the header row with Expand/Collapse toggle)
   - When expanded, show changelog entries grouped by version, with Added/Changed/Fixed categories
   - Use the same card styling, clickable row, and expand/collapse text pattern as `LicensingCard()`

2. **Define changelog data inline** in `HelpScreen.kt` (similar to how `OPEN_SOURCE_COMPONENTS` is defined). Structure it as a list of version entries, each with a version string, date, and categorized change items. Populate from the current `CHANGELOG.md` content.

3. **Place the card** on the Help screen above the Licensing card (after the Tips section, before Licensing).

4. **Future maintenance**: When the release protocol's changelog step runs, the agent should update both `CHANGELOG.md` and the inline data in `HelpScreen.kt`.

**Acceptance:**
- "What's New" card appears on Help screen with collapsible changelog
- Changelog entries match `CHANGELOG.md` content
- Collapsed by default, expandable on click
- Visual style matches the Licensing card pattern

---

## WP-121: Fix Changelog Bullet Alignment (U25)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/HelpScreen.kt`
**Depends on:** WP-120

**Scope:**
Bullet characters in all Help screen sections render below their associated text instead of beside it. Add `Modifier.weight(1f)` to the text element in every bullet-style `Row` layout: `HelpStep()`, `HelpBullet()`, `ChangelogCard()` item rows, and `LicensingCard()` component rows.

**Acceptance:**
- Bullet characters appear to the left of their associated text, not below
- Multi-line text wraps correctly beside the bullet
- All four bullet patterns (HelpStep, HelpBullet, ChangelogCard, LicensingCard) are consistent

---

## WP-122: Add ExportFormat Enum and TSV Writer (E51)

**Status:** done
**Owns:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/ExportFormat.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/TsvWriter.kt`, `app/src/test/kotlin/tech/carbonworks/snc/batchreferralparser/output/TsvWriterTest.kt`
**Reads:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/CsvWriter.kt`, `app/src/test/kotlin/tech/carbonworks/snc/batchreferralparser/output/CsvWriterTest.kt`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/ExportPreferences.kt`
**Depends on:** nothing

**Scope:**

1. **Create `ExportFormat.kt`** — an enum with three values:
   ```kotlin
   enum class ExportFormat(val displayName: String, val extension: String) {
       XLSX("Excel (.xlsx)", "xlsx"),
       CSV("CSV (.csv)", "csv"),
       TSV("TSV (.tsv)", "tsv"),
   }
   ```

2. **Create `TsvWriter.kt`** — mirrors `CsvWriter` but uses tab delimiter. Key differences from CSV:
   - Delimiter is `\t` instead of `,`
   - Escaping: strip tabs and newlines from field values (no quoting needed for TSV)
   - File extension `.tsv`
   - Otherwise identical structure: same header/data row logic, same column config support, same `expandServices` handling

3. **Update `ExportPreferences.kt`**:
   - Add `getExportFormat(): ExportFormat` — reads a string preference key `"exportFormat"`, returns `ExportFormat.valueOf()` with fallback to `XLSX`. For backward compatibility: if the new key is absent, check the old `exportAsCsv` boolean key — if true return `CSV`, otherwise `XLSX`.
   - Add `setExportFormat(format: ExportFormat)` — stores the enum name as a string.
   - Keep the old `getExportAsCsv()`/`setExportAsCsv()` methods for now (WP-123 will remove them when it rewires the UI).

4. **Create `TsvWriterTest.kt`** — mirror `CsvWriterTest` structure: test tab-delimited output, field escaping (tabs/newlines stripped), column config, expand services.

5. **Run all tests** — existing and new must pass.

**Acceptance:**
- `ExportFormat` enum exists with XLSX, CSV, TSV
- `TsvWriter` produces tab-delimited `.tsv` files
- `ExportPreferences` has format-aware getter/setter with backward compat
- All tests pass (existing + new TSV tests)

---

## WP-123: Replace CSV Checkbox with Format Dropdown (U26)

**Status:** done
**Owns:** none
**Reads:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/ExportFormat.kt`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/ExportPreferences.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/HelpScreen.kt`, `CHANGELOG.md`
**Depends on:** WP-122

**Scope:**

1. **ResultsScreen.kt** — Replace the "Export as CSV" checkbox with a compact dropdown:
   - Replace `exportAsCsv: Boolean` state with `exportFormat: ExportFormat` initialized from `ExportPreferences.getExportFormat()`.
   - Replace the Checkbox + "Export as CSV" text with a small dropdown (use `ExposedDropdownMenuBox` or a `TextButton` + `DropdownMenu` pattern) showing the three format options from `ExportFormat.entries`. Display `format.displayName` for each item.
   - On selection change, call `ExportPreferences.setExportFormat(format)`.
   - In the `saveToXlsx()` function (rename or keep), update the dispatch logic:
     - `XLSX` → `SpreadsheetWriter.write(...)`
     - `CSV` → `CsvWriter.write(...)`
     - `TSV` → `TsvWriter.write(...)`
   - Update `extension` and `defaultFilename` to use `exportFormat.extension`.

2. **ExportPreferences.kt** — Remove the old `getExportAsCsv()`/`setExportAsCsv()` methods and the old `KEY_EXPORT_AS_CSV` constant now that the UI is fully migrated.

3. **HelpScreen.kt** — Update existing help text in-place (do not add new lines):
   - The "Supported Formats" bullet about output: change to mention XLSX, CSV, or TSV.
   - The "Tips" bullet about CSV export: reword to describe the format dropdown and all three options.
   - CHANGELOG_ENTRIES: update the CSV export entry to mention the format dropdown and TSV.

4. **CHANGELOG.md** — Under `[Unreleased]`, add: "TSV export option — use the format dropdown on the results screen to choose between XLSX, CSV, or TSV". Remove or update the existing CSV-only entry.

5. **Run all tests.**

**Acceptance:**
- No checkbox on results screen; format dropdown with XLSX/CSV/TSV visible instead
- Selecting a format persists and controls export output
- Old boolean preference methods removed from ExportPreferences
- Help text updated in-place (no net new lines added)
- CHANGELOG.md updated
- All tests pass

---

## WP-125: Reposition Format Dropdown and Remove Label (U27)

**Status:** done
**Owns:** none
**Reads:** none
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/ResultsScreen.kt`
**Depends on:** WP-124

**Scope:**
On the results screen action bar, move the export format OutlinedButton dropdown so it sits between the gear icon and the Export button. Remove the "Format:" label text and its spacer.

**Acceptance:**
- Format dropdown is positioned between gear icon and Export button
- No "Format:" label visible
- Dropdown still functions correctly (opens menu, persists selection)

---

## WP-126: Release Candidate v1.1.0-rc2 (REL)

**Status:** done
**Owns:** none
**Reads:** `docs/protocols/release-tagging.md`, `CHANGELOG.md`, `app/build.gradle.kts`
**Touches:** `CHANGELOG.md`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/HelpScreen.kt`
**Depends on:** WP-122, WP-123, WP-124, WP-125

**Scope:**
Execute pre-release checklist for v1.1.0-rc2:

1. **Changelog**: Move `[Unreleased]` items in CHANGELOG.md into the `[1.1.0]` section (since this is still the same minor release, just a new RC). Add a fresh empty `[Unreleased]` section. Update HelpScreen.kt `CHANGELOG_ENTRIES` to match.
2. **FOSS licensing review**: Verify NOTICE.txt and OPEN_SOURCE_COMPONENTS match current dependencies.
3. **Run all tests**: `./gradlew :app:test`
4. **Version number**: Confirm `packageVersion` is already `1.1.0` (no change needed).

Do NOT tag or push.

**Acceptance:**
- CHANGELOG.md [1.1.0] section includes TSV/format dropdown entry, fresh empty [Unreleased] above
- HelpScreen.kt CHANGELOG_ENTRIES matches
- All tests pass
- Single commit

---

## WP-127: Release v1.1.0 (REL)

**Status:** done
**Owns:** none
**Reads:** `docs/protocols/release-tagging.md`
**Touches:** `app/build.gradle.kts`
**Depends on:** WP-126

**Scope:**
RC2 passed validation. Finalize the v1.1.0 release:

1. Update `packageVersion` in `app/build.gradle.kts` from `"1.0.902"` to `"1.1.0"`
2. Commit
3. Tag `v1.1.0`
4. Push commit and tag
5. Build the final MSI installer

**Acceptance:**
- `packageVersion` is `"1.1.0"`
- Tag `v1.1.0` exists on origin
- MSI installer built successfully

---

## WP-128: Fix Provider Name Extraction to Use Mailing Address (E52)

**Status:** done
**Owns:** none
**Reads:** `docs/spec/field-mapping.json`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/FieldParser.kt`, `app/src/test/kotlin/tech/carbonworks/snc/batchreferralparser/extraction/FieldParserTest.kt`
**Depends on:** WP-106

**Scope:**
The provider name extraction incorrectly uses "Pay to:" (S&C billing address) instead of "Mailing address:" (actual provider/doctor name). Fix the extraction in `extractInvoiceFields()` in FieldParser.kt:

1. **Replace the "Pay to:" regex** with a "Mailing address:" regex: `Mailing\s+address\s*:\s*(.+)` (case-insensitive). Extract the first line after the label as the provider name, same as the current logic does for "Pay to:".
2. **Update the cross-line fallback** — if "Mailing address:" appears alone on a line, take the next non-blank line as the provider name (same pattern as the existing "Pay to:" cross-line fallback).
3. **Remove the "Pay to:" provider name extraction entirely** — it extracts the wrong data. If "Pay to:" is needed for a different field in the future, it can be re-added then.
4. **Update tests in FieldParserTest.kt** — replace all "Pay to:" provider name tests with equivalent "Mailing address:" tests. Use realistic provider names (e.g., "Lisa Heidelmaier PhD", "John Smith MD") instead of company names.
5. **Run all tests** — `./gradlew :app:test`

**Acceptance:**
- Provider name extracted from "Mailing address:" section, not "Pay to:"
- Same-line and cross-line extraction both work
- All tests pass with updated assertions

---

## WP-129: Release Candidate v1.1.1-rc1 (REL)

**Status:** done
**Owns:** none
**Reads:** `docs/protocols/release-tagging.md`
**Touches:** `app/build.gradle.kts`, `CHANGELOG.md`, `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/screens/HelpScreen.kt`
**Depends on:** WP-128

**Scope:**
Hotfix release for the provider name extraction bug. Execute pre-release checklist:

1. **Version number**: Set `packageVersion` to `"1.1.901"` (rc1 for v1.1.1, using pre-release offset convention).
2. **Changelog**: In CHANGELOG.md, add a `[1.1.1]` section with today's date under Fixed: "Provider/Doctor Name now extracts from the correct field (Mailing address) instead of the billing address (Pay to)". Move any Unreleased items into it. Add fresh empty Unreleased. Update HelpScreen.kt CHANGELOG_ENTRIES to match (add a new 1.1.1 entry at top of list).
3. **Run all tests**: `./gradlew :app:test`

Do NOT tag or push.

**Acceptance:**
- `packageVersion` is `"1.1.901"`
- CHANGELOG.md has [1.1.1] section with the fix noted
- HelpScreen.kt CHANGELOG_ENTRIES matches
- All tests pass
- Single commit

---

## WP-130: Finalize v1.1.1 Release (REL)

**Status:** done
**Owns:** none
**Reads:** `docs/protocols/release-tagging.md`
**Touches:** `app/build.gradle.kts`
**Depends on:** WP-129

**Scope:**
RC1 passed testing. Finalize the v1.1.1 release:

1. **Version number**: Set `packageVersion` to `"1.1.1"` in `app/build.gradle.kts`.
2. **Verify**: CHANGELOG.md already has [1.1.1] section dated 2026-03-10. HelpScreen.kt CHANGELOG_ENTRIES already has 1.1.1 entry. No changes needed to either.
3. **Run all tests**: `./gradlew :app:test`

Do NOT tag or push — that will be done after merge.

**Acceptance:**
- `packageVersion` is `"1.1.1"`
- All tests pass
- Single commit

---

## WP-131: Migrate Saved Export Config to Include New Fields (BUG)

**Status:** done
**Owns:** none
**Reads:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/ExportColumn.kt`
**Touches:** `app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/output/ExportPreferences.kt`, `app/src/test/kotlin/tech/carbonworks/snc/batchreferralparser/output/ExportColumnTest.kt`
**Depends on:** nothing (all touched files already exist on main)

**Scope:**
When a user saves their export column configuration, subsequent app updates that add new fields to `DEFAULT_FIELD_ORDER` are invisible to that user — `ExportPreferences.load()` deserializes the saved JSON as-is without reconciling against the current `DEFAULT_FIELD_ORDER`. Only users who never customized settings (or click "Reset to Defaults") see new fields.

Fix `ExportPreferences.load()` to reconcile the saved config with the current `DEFAULT_FIELD_ORDER` after deserialization:

1. **Add new fields**: After deserializing the saved config, identify any field IDs present in `DEFAULT_FIELD_ORDER` that are missing from the saved `columns` list. Append those new fields (as `ExportColumn.Field`, enabled by default) to the end of the saved column list.
2. **Remove stale fields**: Remove any `ExportColumn.Field` entries whose `fieldId` does not appear in `DEFAULT_FIELD_ORDER` (fields that were removed from the app). Spacer columns are never removed.
3. **Log the migration**: If any fields were added or removed, log a message like `[ExportPreferences] Migrated config: added N new field(s), removed M stale field(s)`.
4. **Auto-save after migration**: If the config was modified by migration, call `save()` with the updated config so the migration only runs once.
5. **Add tests** in `ExportColumnTest.kt`:
   - Test that loading a saved config missing a field from `DEFAULT_FIELD_ORDER` results in that field being appended.
   - Test that loading a saved config with a field ID not in `DEFAULT_FIELD_ORDER` results in that field being removed.
   - Test that spacer columns survive migration.
   - Test that no migration occurs when saved config already matches `DEFAULT_FIELD_ORDER`.
6. **Run all tests**: `./gradlew :app:test`

**Acceptance:**
- `load()` returns a config that always contains every field in `DEFAULT_FIELD_ORDER`, even if the saved JSON predates those fields
- Stale fields from removed versions are pruned
- Spacers are preserved
- Migration is logged and auto-saved
- All existing and new tests pass

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
