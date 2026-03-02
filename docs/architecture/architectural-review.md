# Architectural Review: S&C Batch Referral Processor

**Date**: 2026-03-02
**Scope**: Full codebase review after Wave 12 (WP-22)
**Test count at time of review**: 98 tests across 5 files

---

## 1. Overall Architecture

Clean three-layer design with a linear pipeline:

```
PDF Files -> PdfTextExtractor -> TableExtractor -> FieldParser -> SpreadsheetWriter -> XLSX
```

Each stage has clear input/output contracts via data classes (`ExtractionResult`, `ExtractedTable`, `ParseResult`, `ReferralFields`). The pipeline is orchestrated in `ProcessingScreen.kt` via a `LaunchedEffect` coroutine.

**Assessment**: The layered architecture is well-suited for this application's scope. The pipeline is linear and easy to follow.

---

## 2. Code Organization

**Package structure**:
```
tech.carbonworks.snc.batchreferralparser/
  Main.kt                          -- App entry point, screen router
  extraction/
    ExtractionResult.kt            -- BoundingBox, TextBlock, PageInfo, ExtractionResult
    PdfTextExtractor.kt            -- PDFBox text extraction
    TableExtractor.kt              -- Tabula table extraction
    FieldParser.kt                 -- Regex-based field parsing (~1044 lines)
    ReferralFields.kt              -- Output data class
    ParseResult.kt                 -- Parse result wrapper
    ParsingWarning.kt              -- Warning data class
  output/
    SpreadsheetWriter.kt           -- Apache POI XLSX writer
  ui/
    screens/
      MainScreen.kt                -- File selection + drag-and-drop
      ProcessingScreen.kt          -- Batch progress display
      ResultsScreen.kt             -- Data preview + save action
      SettingsScreen.kt            -- PHI toggle settings
      HelpScreen.kt                -- Usage instructions
    components/
      CwButton.kt                  -- Primary/Secondary/Accent buttons
      CwCard.kt                    -- Styled card container
      StatusIcon.kt                -- Processing status indicator
      SectionHeader.kt             -- Section heading text
      FilePathText.kt              -- Monospace path display
    theme/
      Theme.kt                     -- Color palette + Material theme
  util/
    PhiMask.kt                     -- Runtime PHI masking
    PhiPreferences.kt              -- Preference persistence
```

**Strengths**:
- Clear separation between extraction, output, and UI concerns
- UI components extracted into reusable pieces (`CwButton`, `CwCard`, `StatusIcon`, etc.)
- Naming conventions are consistent and descriptive

**Concerns**:
- `FieldParser.kt` at ~1044 lines is the largest file and arguably does too much. It contains the main `parse()` method, five extraction sub-methods, fallback extraction, field merging, name parsing, text reconstruction, diagnostic dump methods, and seven internal data classes. This would benefit from decomposition.
- `ProcessedReferral` and `FileProcessingState` are defined inside `ProcessingScreen.kt` but are used by `ResultsScreen.kt` and `Main.kt`. They should be in their own file in a `model/` or `extraction/` package.
- `FileStatus` enum lives in `StatusIcon.kt` but is referenced across multiple screens. It belongs in its own file or a shared model package.

---

## 3. Design Patterns

**Patterns in use**:

- **Sealed class for result types** (`ExtractionResult`): Forces exhaustive handling of success/error cases. Idiomatic and appropriate.
- **Data classes throughout**: `ReferralFields`, `TextBlock`, `BoundingBox`, `ParseResult`, `ParsingWarning`, `ServiceLine`, etc. Provides value equality, destructuring, and copy semantics.
- **Singleton objects** (`SpreadsheetWriter`, `PhiMask`, `PhiPreferences`): `SpreadsheetWriter` as an `object` is fine since it's stateless. `PhiMask` and `PhiPreferences` as singletons are more problematic (see State Management).
- **Compose state hoisting**: All navigation state (`currentScreen`, `selectedFiles`, `fileStates`, `processingResults`) is hoisted to the `App()` composable. Correct pattern.
- **Strategy pattern (implicit)**: `TableExtractor` uses lattice-first, stream-fallback extraction.
- **Multi-stage extraction with priority merging**: `FieldParser.parse()` runs five extraction stages and merges them with explicit priority.

**Concerns**:
- No dependency injection. Every class instantiates its own dependencies directly, making testing of the pipeline orchestration impossible without real PDF files.
- No interface abstractions for extractors.

---

## 4. Error Handling

**Strengths**:
- `ExtractionResult` sealed class enforces exhaustive error handling at the extraction layer
- `PdfTextExtractor.extract()` has specific handlers for `InvalidPasswordException`, `IOException`, and generic `Exception`
- `TableExtractor` returns `emptyList()` on failure rather than propagating exceptions -- batch processing continues even if table extraction fails for one file
- `ProcessingScreen` wraps each file's processing in try/catch so one bad file cannot crash the batch
- `ParsingWarning` provides structured diagnostic information when labels are found but values cannot be extracted

**Gaps**:
- `TableExtractor` silently swallows all exceptions, returning empty lists. Zero logging or reporting when table extraction fails.
- `saveToXlsx()` in `ResultsScreen.kt` catches `Exception` and displays it to the user, but does not log the stack trace.
- No validation that `ReferralFields` has minimum required fields before saving to XLSX. A referral with zero fields extracted still produces a blank row.

---

## 5. State Management

**UI State**: Follows Compose best practices. All state is hoisted to `App()` using `remember { mutableStateOf(...) }`. Screen transitions are driven by the `currentScreen` enum.

**Problem: Singleton mutable state in `PhiMask`**: `PhiMask.maskingEnabled` is a plain Kotlin `var`, not Compose state. When it changes, Compose has no way to know that composables reading `maskDisplay()` need recomposition. The code works around this by maintaining a separate `isMasked` state in `ResultsScreen` that is toggled in sync -- a fragile dual-source-of-truth. It would be cleaner to make the masking state a Compose `mutableStateOf` or pass it through the composable tree.

**Preferences duplication**: Both `MainScreen.kt` and `PhiPreferences.kt` create their own `Preferences.userRoot().node(...)` instances for the same node path. Should be centralized.

---

## 6. Testability

**Well-covered (98 tests)**:
- `FieldParserTest` (~69 tests): Excellent coverage of regex-based field extraction using clever test helpers that construct `ExtractionResult.Success` from plain text strings, avoiding real PDFs.
- `SpreadsheetWriterTest` (12 tests): Covers single/multiple referrals, empty list, column headings, services flattening, date cell formatting, header formatting, unparseable date fallback.
- `PhiMaskTest` (17 tests): Covers `maskValue`, `maskDisplay`, `PhiPreferences` round-trips, and `SpreadsheetWriter` boundary (unmasked output despite masking enabled).
- `PdfTextExtractorTest` (5 tests, not counted in @Test grep): Programmatically creates PDFs to test normal extraction, multi-page, empty pages, corrupt files, encrypted files.
- `TableExtractorTest` (3 tests, not counted in @Test grep): Programmatically creates PDFs with drawn tables.

**Gaps**:
- **No tests for pipeline orchestration**: The extraction pipeline wiring is embedded in a `@Composable` function with a `LaunchedEffect`. Untestable without UI.
- **No column/field alignment test**: `SpreadsheetWriter.extractRowValues()` must keep its list in sync with `COLUMN_HEADINGS`. If a field is added to `ReferralFields` but not to both places, columns silently shift.
- **`reconstructPageTexts` primary path untested**: Tests construct `ExtractionResult.Success` with empty `strippedText`, exercising only the fallback path. In production, the `strippedText` path is used.

---

## 7. PHI/Security

**Strengths**:
- `PhiMask` provides centralized masking (first-char + asterisks)
- Default state is masked (safe by default)
- Settings persist via Java Preferences API (OS-level storage, not files)
- Diagnostic dumps use `PhiMask.maskDisplay()` for context lines
- Error messages displayed in the UI are passed through `PhiMask.maskDisplay()`

**Risks and gaps**:
- **`println` statements throughout**: If masking is disabled at runtime, any log aggregator or terminal history could capture PHI.
- **Masking bypass via public `var`**: `PhiMask.maskingEnabled` is a public `var` -- any code can disable masking. If a developer adds a `println(someField)` without going through `PhiMask`, PHI leaks.
- **No masking on XLSX output**: Correct behavior (the XLSX is the deliverable), but means output files are PHI artifacts requiring HIPAA handling.
- **No audit logging**: No record of who processed which files or when masking was toggled.
- **Masking algorithm is weak**: Preserves word count and first characters. `"CA"` becomes `"C*"`, `"90210"` becomes `"9****"`. Sufficient for shoulder-surfing prevention, not formal anonymization.

---

## 8. Performance

**Bottlenecks and concerns**:
- **Double PDF loading**: `PdfTextExtractor` and `TableExtractor` each call `Loader.loadPDF(file)` independently, loading the same PDF into memory twice. For 50-file batches, this doubles memory pressure.
- **Sequential processing**: Files are processed one at a time. Parallel processing could significantly speed up batches.
- **Regex recompilation**: `FieldParser` compiles multiple `Regex` objects on every invocation. These static patterns should be compiled once as `companion object` constants.
- **JVM memory limit**: `build.gradle.kts` sets `-Xmx512m`. With Apache POI, PDFBox, and Tabula all loaded, 512MB could be tight for 50 large PDFs.

---

## 9. Coupling & Dependencies

**Tight couplings**:
- **`ProcessingScreen` couples UI to pipeline execution**: The extraction pipeline is directly coded inside a `@Composable` `LaunchedEffect`. Cannot be tested, reused, or invoked without the UI.
- **`ResultsScreen` directly calls `SpreadsheetWriter.write()`**: Save action embedded in composable rather than handled by a callback or use case.
- **`PhiMask` static coupling**: Every display composable calls `PhiMask.maskDisplay()` directly. No abstraction layer.
- **`SpreadsheetWriter` column order implicitly coupled to `ReferralFields`**: No compile-time guarantee of alignment between `COLUMN_HEADINGS` and `extractRowValues()`.

**Dependency direction**: Dependencies flow correctly: `ui` depends on `extraction` and `output`; `extraction` depends on `util`; `output` depends on `extraction`. No circular dependencies.

**External dependencies**: PDFBox 3.0.4, Tabula 1.0.5, POI 5.3.0 -- mature, well-maintained libraries. `DuplicatesStrategy.EXCLUDE` in `build.gradle.kts` handles transitive dependency conflicts.

---

## 10. Prioritized Suggestions

### High Impact

1. **Extract pipeline from UI** -- Move `PdfTextExtractor -> TableExtractor -> FieldParser` orchestration into a standalone `BatchProcessor` class. Benefits: testable without UI, reusable for CLI mode, separates concerns.

2. **Decompose `FieldParser`** -- Extract internal data classes into their own file. Split extraction methods into focused classes (e.g., `HeaderExtractor`, `TableFieldExtractor`, `InvoiceExtractor`) that `FieldParser` delegates to. Each extraction stage becomes independently testable.

3. **Promote shared models** -- Move `ProcessedReferral`, `FileProcessingState`, and `FileStatus` to a `model/` package.

4. **Compile regexes once** -- Move repeated `Regex(...)` calls to `companion object` properties. Free performance improvement.

### Medium Impact

5. **Share PDF document load** -- Refactor so `PdfTextExtractor` and `TableExtractor` share a single `PDDocument`, or introduce a `PdfDocumentProvider` that loads once and provides to both extractors.

6. **Replace `PhiMask.maskingEnabled` with Compose-observable state** -- Use `mutableStateOf` in the `PhiMask` object or pass masking state through the composable tree. Eliminates dual-source-of-truth.

7. **Add column/field alignment safety** -- Generate `extractRowValues()` reflectively from `ReferralFields`, or add a test asserting `COLUMN_HEADINGS.size == extractRowValues(emptyReferral).size`.

8. **Centralize preferences** -- Merge `MainScreen.kt`'s prefs instance with `PhiPreferences` into a single `AppPreferences` object.

### Low Impact

9. **Replace `println` with a logging framework** (e.g., SLF4J) -- Enable log levels, structured output, and ability to disable verbose output in production.

10. **Consider parallel file processing** -- For batches approaching 50 files, process files in parallel with `Dispatchers.IO.limitedParallelism(4)`.

11. **Add application icons** -- TODOs exist in `build.gradle.kts` for `.ico`, `.icns`, and `.png` icons.

12. **Add `ExtractionPipelineResult` type** -- Replace ad-hoc tuple built in `ProcessingScreen` with an explicit wrapper for the full pipeline output per file.

13. **Guard against `strippedText`/`reconstructPageText` divergence** -- Add a test exercising the `strippedText`-preferred path.

---

## Summary

This is a well-structured, purpose-built application that does one thing and does it competently. The codebase is clean, consistently styled, and documented with KDoc comments. The sealed-class result types, data class models, and Compose state hoisting all follow idiomatic Kotlin/Compose patterns.

The main architectural weaknesses are: (1) pipeline logic embedded in a UI composable rather than a testable standalone class, (2) `FieldParser` accumulating too much responsibility in a single 1044-line file, (3) global mutable state in `PhiMask` outside the Compose state system, and (4) double PDF loading.

The test suite is strong for the extraction layer (98 tests, 2200+ lines of test code) but absent for pipeline orchestration and UI layers. **For a Phase 1 deliverable targeting 6 office machines, the current architecture is fully adequate.** The suggestions above are investments that would pay off if the tool evolves into Phase 2 or handles higher volumes.
