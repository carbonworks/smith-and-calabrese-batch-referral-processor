# Architectural Review: S&C Batch Referral Processor

**Date**: 2026-03-05 (revision 2)
**Prior review**: 2026-03-02 (post-Wave 12, WP-22)
**Scope**: All 29 source files (6,451 lines) across 7 packages
**Test count**: 98 tests across 5 files (unchanged since prior review)

---

## 1. Overall Architecture

Clean three-layer design with a linear pipeline:

```
PDF Files -> PdfTextExtractor -> TableExtractor -> FieldParser -> SpreadsheetWriter -> XLSX
```

Each stage has clear input/output contracts via data classes (`ExtractionResult`, `ExtractedTable`, `ParseResult`, `ReferralFields`). The pipeline is orchestrated in `ProcessingScreen.kt` via a `LaunchedEffect` coroutine. Dependency direction is correct throughout: `ui` depends on `extraction` and `output`; `extraction` depends on `util`; no circular dependencies.

**Assessment**: Well-suited for this application's scope. The pipeline is linear and easy to follow.

---

## 2. Code Organization

**Package structure**:
```
tech.carbonworks.snc.batchreferralparser/
  Main.kt                          -- App entry point, NavHost routing (198 lines)
  FeatureFlags.kt                  -- Build-time feature flag constants (13 lines)
  extraction/
    ExtractionResult.kt            -- BoundingBox, TextBlock, PageInfo, ExtractionResult (80 lines)
    PdfTextExtractor.kt            -- PDFBox text extraction (285 lines)
    TableExtractor.kt              -- Tabula table extraction (273 lines)
    FieldParser.kt                 -- Regex-based field parsing (1,166 lines)
    ReferralFields.kt              -- Output data class (77 lines)
    ParseResult.kt                 -- Parse result wrapper (17 lines)
    ParsingWarning.kt              -- Warning data class (20 lines)
  output/
    SpreadsheetWriter.kt           -- Apache POI XLSX writer (219 lines)
    ExportColumn.kt                -- Column config data model, serializable sealed hierarchy (145 lines)
    ExportPreferences.kt           -- JSON persistence for column config (71 lines)
  ui/
    screens/
      MainScreen.kt                -- File selection + drag-and-drop (535 lines)
      ProcessingScreen.kt          -- Batch progress + pipeline orchestration (269 lines)
      ResultsScreen.kt             -- Data preview, PHI masking, save action (1,051 lines)
      SettingsScreen.kt            -- PHI toggle settings (205 lines)
      HelpScreen.kt                -- Usage instructions + log export (403 lines)
      ExportSettingsScreen.kt      -- Drag-and-drop column configuration (689 lines)
    components/
      CwButton.kt                  -- Primary/Secondary/Accent buttons (94 lines)
      CwCard.kt                    -- Styled card container (35 lines)
      StatusIcon.kt                -- Circular status icon + FileStatus enum (74 lines)
      SectionHeader.kt             -- Section heading text (25 lines)
      FilePathText.kt              -- Monospace path display (29 lines)
    theme/
      Theme.kt                     -- Color palette + Material theme (46 lines)
  util/
    PhiMask.kt                     -- Runtime PHI masking (95 lines)
    PhiPreferences.kt              -- Preference persistence (49 lines)
  logging/
    LoggingSetup.kt                -- File logging initialization (145 lines)
    RotatingFileOutputStream.kt    -- Log file rotation (99 lines)
    TeeOutputStream.kt             -- Stdout/stderr duplication (44 lines)
```

**Strengths**:
- Clear separation between extraction, output, and UI concerns
- UI components extracted into reusable pieces (`CwButton`, `CwCard`, `StatusIcon`, etc.)
- Naming conventions are consistent and descriptive throughout
- KDoc coverage is thorough
- `ExportColumn` sealed hierarchy with `kotlinx.serialization` is well-designed
- `getFieldValue()` extension centralizes field ID -> value mapping without reflection

**Concerns**:
- `FieldParser.kt` at 1,166 lines (up from 1,044 at prior review) does too much — see D3
- `ResultsScreen.kt` at 1,051 lines exceeds the 500-line guideline by 2x — see D11
- `ExportSettingsScreen.kt` at 689 lines exceeds the 500-line guideline — see D12
- `ProcessedReferral` and `FileProcessingState` defined in `ProcessingScreen.kt` but used across files — see D2
- `FileStatus` enum lives in `StatusIcon.kt` but is a domain concept referenced across screens

---

## 3. State Management

**What works well**:
- State hoisting in `App()` (`Main.kt:82-84`) is textbook Compose — `selectedFiles`, `fileStates`, and `processingResults` are hoisted to the top-level composable and threaded via parameters/callbacks
- `rememberNavController()` with `NavHost` is the recommended Compose navigation pattern
- Per-card mask overrides in `ResultsScreen.kt:169` using `mutableStateMapOf` is clean per-item state management
- Navigation `popUpTo(Routes.FILE_SELECTION)` correctly prevents back-navigating to the processing screen

**Issues**:

**D6 — PhiMask.maskingEnabled is a plain `var`, not Compose-observable** (`PhiMask.kt:35`)
Compose cannot observe changes to a plain Kotlin `var`. The workaround is a shadow `isMasked` state in `ResultsScreen.kt:160-163` manually kept in sync — a dual-source-of-truth anti-pattern. If `PhiMask.maskingEnabled` is changed outside ResultsScreen, the UI will not recompose.

**D6a — Side effect inside `remember` initializer** (`ResultsScreen.kt:160-163`)
`PhiMask.refreshFromPreferences()` modifies `PhiMask.maskingEnabled` as a side effect during composition. While the `remember` block only runs once, side effects during composition are a Compose anti-pattern. Should be a `LaunchedEffect(Unit)` or handled before the composable is invoked.

**D8 — Preferences scattered across 4 files** (worsened from 2 at prior review)
Four separate `Preferences.userRoot().node(...)` instances all target the same node path:
- `MainScreen.kt:81-82` (file-scope val)
- `ResultsScreen.kt:95-96` (file-scope val)
- `PhiPreferences.kt:22` (object-scope)
- `ExportPreferences.kt:21` (object-scope)

No central key registry — key name conflicts could go undetected.

---

## 4. Design Patterns

**Patterns in use**:
- **Sealed class for result types** (`ExtractionResult`): Forces exhaustive handling. Idiomatic.
- **Immutable data classes throughout**: Value equality, destructuring, copy semantics.
- **Singleton objects** (`SpreadsheetWriter`, `PhiMask`, `PhiPreferences`): `SpreadsheetWriter` is stateless, appropriate. `PhiMask` as a singleton is problematic (D6).
- **Compose state hoisting**: Correct pattern throughout.
- **Strategy pattern (implicit)**: `TableExtractor` uses lattice-first, stream-fallback.
- **Multi-stage extraction with priority merging**: `FieldParser.parse()` runs five stages and merges with explicit priority.
- **Serializable sealed hierarchy**: `ExportColumn` config uses `kotlinx.serialization` with discriminated polymorphism.

**Concerns**:
- No dependency injection — every class instantiates dependencies directly, making pipeline testing impossible without real PDFs.
- No interface abstractions for extractors.

---

## 5. Error Handling

**Strengths**:
- `ExtractionResult` sealed class enforces exhaustive error handling at the extraction layer
- `PdfTextExtractor.extract()` has specific handlers for `InvalidPasswordException`, `IOException`, and generic `Exception`
- Per-file error resilience in the pipeline — one bad file cannot crash the batch
- `ParsingWarning` provides structured diagnostics when labels found but values cannot be extracted
- `FieldParser.generateMissingFieldWarnings()` adds completeness checking

**Gaps**:

**D9 — TableExtractor silently swallows exceptions** (`TableExtractor.kt:95-117`)
Multiple `catch (e: Exception)` blocks return `emptyList()` without logging stack traces. The lattice extraction catch (lines 115-117) is completely silent.

`saveToXlsx()` in `ResultsScreen.kt:1033` catches generic `Exception` and displays it to the user but does not log the stack trace.

No validation that `ReferralFields` has minimum required fields before saving to XLSX.

---

## 6. Coroutines & Threading

**What works well**:
- Pipeline processing in `ProcessingScreen.kt:107` correctly uses `withContext(Dispatchers.IO)` to move PDF extraction off the main thread
- UI state updates from the `LaunchedEffect` coroutine run on the main dispatcher — thread-safe
- Resource cleanup with `.use {}` blocks is consistent throughout

**Issues**:

**D13 — `saveToXlsx()` performs I/O on UI thread** (`ResultsScreen.kt:1011-1029`)
After the file dialog returns, `SpreadsheetWriter.write()`, `tempFile.copyTo()`, and file cleanup all run synchronously on the UI thread. For large batches this could visibly freeze the UI. Should use `rememberCoroutineScope().launch(Dispatchers.IO)`.

**D14 — Blocking file dialogs on UI thread**
`MainScreen.kt:469` (`JFileChooser.showOpenDialog`) and `ResultsScreen.kt:984` (`FileDialog.isVisible = true`) both block the main thread. This is the standard Compose Desktop pattern (no built-in non-blocking file dialog), but worth documenting.

`saveLogFile()` in `HelpScreen.kt:388-395` similarly performs file I/O on the UI thread (small files, unlikely to be noticeable).

---

## 7. Recomposition Safety

**What works well**:
- `LazyColumn` in `MainScreen.kt:359` uses `key = { it.absolutePath }` for stable keys
- `ExportSettingsScreen.kt:326` uses `key = { _, column -> column.stableKey() }` for reorderable list

**Issues**:

**Minor — Lambda allocations in `App()` composable** (`Main.kt:100-194`)
Inline lambda definitions for screen callbacks inside `composable(route)` blocks are re-allocated on every recomposition. Low practical impact since NavHost recomposes infrequently.

**Minor — `ProcessedReferral` contains `java.io.File`** (`ProcessingScreen.kt:54`)
`File` is mutable/unstable — Compose cannot infer stability. May cause unnecessary child recompositions.

**Minor — `LazyColumn` items in `ProcessingScreen` lack stable keys** (`ProcessingScreen.kt:221`)
Items are diffed by index rather than identity when states update.

---

## 8. Compose Desktop Specifics

**What works well**:
- Window management is clean: `rememberWindowState`, `window.minimumSize`, proper icon loading
- Drag-and-drop implementation in `MainScreen.kt:138-220` is thorough — walks the AWT component tree to attach drop targets, working around the known SkiaLayer interception issue
- `DisposableEffect` correctly saves and restores original drop targets on dispose

**Issues**:

**D15 — `UIManager.setLookAndFeel()` called on every file picker open** (`MainScreen.kt:450-453`)
Global Swing L&F is set every time the file picker opens. Should be called once at startup in `main()`.

**Minor — Inconsistent file dialog types**
`MainScreen.kt` uses `JFileChooser` (Swing), while `ResultsScreen.kt` and `HelpScreen.kt` use `java.awt.FileDialog` (AWT/native). Users see different dialog styles.

---

## 9. PHI / Security

**Strengths**:
- `PhiMask` provides centralized masking (first-char + asterisks)
- Default state is masked (safe by default)
- Settings persist via Java Preferences API (OS-level storage, not files)
- Diagnostic dumps use `PhiMask.maskDisplay()` for context lines
- Error messages in the UI pass through `PhiMask.maskDisplay()`

**Risks and gaps**:
- **`println` statements throughout**: With masking disabled, terminal history or log aggregators could capture PHI (D10)
- **Masking bypass via public `var`**: Any code can disable masking and print fields without going through `PhiMask`
- **No masking on XLSX output**: Correct behavior (XLSX is the deliverable), but output files are PHI artifacts
- **No audit logging**: No record of who processed which files or when masking was toggled
- **Masking algorithm is weak**: Preserves word count and first characters; sufficient for shoulder-surfing prevention, not formal anonymization

---

## 10. Performance

**D4 — Regex compiled per-invocation in FieldParser**
~25+ `Regex(...)` calls inside methods, recompiled on every file. For a 50-file batch, that's ~1,250 unnecessary compilations. Should be `companion object` constants. Specific locations:

- `extractHeaderBlock()` (line 376): `headerRegex`
- `extractHeaderFieldsIndividually()` (lines 445-452): 6 regex objects
- `extractCaseNumberComponents()` (line 556): `footerRegex`
- `parseClaimantCellMultiLine()` (lines 677-678): `phoneRegex`, `cityStateZipRegex`
- `parseAppointmentCell()` (lines 756-762): `dateRegex`, `timeRegex`
- `parseServicesCell()` (line 782+): split regex + 4 per chunk
- `extractInvoiceFields()` (lines 842-888): 4 per page per field
- `extractPhone()` (line 946): `phoneRegex`
- `extractFallbackFields()` (lines 976, 982): 2 regex objects
- `splitCamelCaseName()` (line 505), `parseNameParts()` (line 522): 1 each

**D5 — Double PDF loading per file**
`PdfTextExtractor.extract()` and `TableExtractor.extract()` each call `Loader.loadPDF(file)` independently. Doubles memory pressure and I/O for every file in the batch.

**Minor**: Sequential file processing — parallel processing with `Dispatchers.IO.limitedParallelism(4)` could speed up large batches. JVM memory limit (`-Xmx512m`) could be tight with 50 large PDFs loaded through PDFBox + Tabula + POI simultaneously.

---

## 11. Testability

**Well-covered (98 tests)**:
- `FieldParserTest` (~69 tests): Excellent coverage with test helpers that construct `ExtractionResult.Success` from plain text, avoiding real PDFs
- `SpreadsheetWriterTest` (12 tests): Single/multiple referrals, empty list, column headings, services flattening, date formatting
- `PhiMaskTest` (17 tests): `maskValue`, `maskDisplay`, preferences round-trips, boundary tests
- `PdfTextExtractorTest` (5 tests): Programmatically creates PDFs for extraction testing
- `TableExtractorTest` (3 tests): Programmatically creates PDFs with drawn tables

**Gaps**:
- **No tests for pipeline orchestration**: Embedded in `@Composable` `LaunchedEffect`, untestable without UI (D1)
- **No column/field alignment test**: `SpreadsheetWriter` row values must stay in sync with column headings (mitigated by `ExportColumn.getFieldValue()`)
- **`reconstructPageTexts` primary path untested**: Tests use empty `strippedText`, exercising only the fallback path

---

## Deviation Tracker

| # | Deviation | Severity | Status | Notes |
|---|-----------|----------|--------|-------|
| D1 | Pipeline orchestration in `ProcessingScreen` LaunchedEffect | Critical | Open | Core logic untestable without Compose runtime |
| D2 | Model types (`ProcessedReferral`, `FileProcessingState`, `FileStatus`) in UI files | Major | Open | Used across multiple files, belong in `model/` |
| D3 | `FieldParser.kt` at 1,166 lines with multiple responsibilities | Critical | Worsened | Was 1,044 at prior review; architecture doc prescribes decomposition |
| D4 | Regex compiled per-invocation in FieldParser | Critical | Open | ~25+ patterns × 50 files = ~1,250 unnecessary compilations per batch |
| D5 | Double PDF loading per file | Major | Open | Both extractors load independently |
| D6 | `PhiMask.maskingEnabled` is a plain `var`, not Compose-observable | Major | Open | Shadow state in ResultsScreen creates dual source of truth |
| D6a | Side effect inside `remember` initializer | Major | Open | `PhiMask.refreshFromPreferences()` during composition |
| D8 | Preferences scattered across 4 files | Major | Worsened | Was 2 instances, now 4; no central key registry |
| D9 | `TableExtractor` silent exception swallowing | Major | Improved | Some println logging added, but stack traces still lost |
| D10 | `println` logging with no log levels | Minor | Mitigated | `logging/` package captures to file, but no level filtering |
| D11 | `ResultsScreen.kt` at 1,051 lines | Major | New | Exceeds 500-line guideline by 2x; mixed UI + business logic |
| D12 | `ExportSettingsScreen.kt` at 689 lines | Major | New | Exceeds 500-line guideline |
| D13 | `saveToXlsx()` I/O on UI thread | Major | New | Should use `Dispatchers.IO` |
| D14 | Blocking file dialogs on UI thread | Minor | New | Standard Compose Desktop pattern, documented limitation |
| D15 | `UIManager.setLookAndFeel()` on every file picker open | Major | New | Global side effect, should call once at startup |

---

## Prioritized Recommendations

### Critical (highest value)

1. **Extract pipeline from UI** (D1) — Move orchestration into `pipeline/BatchProcessor.kt`. Enables testing without Compose runtime, reuse for potential CLI mode, clean separation of concerns.

2. **Compile regexes once** (D4) — Lift all `Regex(...)` calls in `FieldParser` to `companion object` constants. Mechanical refactor with immediate, measurable performance benefit.

### High

3. **Decompose `FieldParser.kt`** (D3) — Extract internal data classes and split extraction methods into focused stage extractors (e.g., `HeaderExtractor`, `TableFieldExtractor`, `InvoiceExtractor`). Each stage becomes independently testable.

4. **Decompose `ResultsScreen.kt`** (D11) — Extract `ReferralCard` and sub-composables to `ui/components/ReferralCard.kt`. Extract `saveToXlsx()` to a domain-layer export service.

5. **Promote shared models** (D2) — Move `ProcessedReferral`, `FileProcessingState`, `FileStatus` to a `model/` package.

6. **Make PhiMask Compose-observable** (D6) — Convert `maskingEnabled` to `mutableStateOf` and eliminate the shadow state in ResultsScreen.

7. **Move `saveToXlsx()` off the UI thread** (D13) — Wrap in `rememberCoroutineScope().launch(Dispatchers.IO)`.

### Medium

8. **Centralize preferences** (D8) — Create `util/AppPreferences.kt` with a single Preferences instance and all key constants.

9. **Share PDF document load** (D5) — Have pipeline orchestrator load `PDDocument` once and pass to both extractors.

10. **Move `UIManager.setLookAndFeel()` to `main()`** (D15) — Call once before `application { ... }`.

11. **Add error red constant to Theme.kt** — Replace hardcoded `Color(0xFFE53E3E)` in 3 files.

12. **Migrate legacy color aliases** — Replace `SkyBlue`, `WarmWhite`, `PaperTan` with actual color names.

### Low

13. **Add stable keys to `ProcessingScreen` LazyColumn** items.
14. **Use `FileDialog` consistently** instead of mixing with `JFileChooser`.
15. **Use type-safe `@Serializable` navigation routes** instead of string constants.
16. **Use UUID for spacer IDs** instead of `System.currentTimeMillis()`.

---

## Summary

The codebase is well-structured for its scope as a Phase 1 desktop tool. Naming is consistent, KDoc coverage is thorough, sealed result types enforce correctness, and the UI follows Compose state hoisting patterns correctly. The extraction pipeline stages have clear contracts and the `ExportColumn` configuration system is cleanly designed.

Since the prior review (2026-03-02), the main additions are the export column configuration system (WP-35 through WP-37), per-card mask toggling (WP-91 through WP-93), and packaging/branding work (WP-86 through WP-90). The export column system is well-implemented. The mask toggle fix (WP-93) resolved a stale closure anti-pattern. However, the Preferences scattering has worsened (4 instances), `FieldParser.kt` has grown by ~120 lines, and `ResultsScreen.kt` now exceeds 1,000 lines.

The two most impactful issues remain unchanged from the prior review: (1) extraction pipeline embedded in a UI composable, making core business logic untestable, and (2) regex objects compiled on every invocation in `FieldParser`, imposing unnecessary CPU cost per batch run.

**For a Phase 1 deliverable targeting 6 office machines, the current architecture is fully adequate.** The recommendations above are investments for Phase 2 or if the tool evolves to handle higher volumes or additional features.
