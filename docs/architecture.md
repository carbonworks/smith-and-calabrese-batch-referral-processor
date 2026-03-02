# Architecture: S&C Batch Referral Processor

**Status**: Prescriptive (governs all future development)
**Last updated**: 2026-03-02

This document defines the target architecture for the S&C Batch Referral Processor. Where current code conforms, that is noted. Where it deviates, the deviation and required resolution are stated. All new code must follow these rules. Refactoring existing code to conform should be done incrementally via work packages.

---

## 1. System Overview

The S&C Batch Referral Processor is a desktop application that extracts structured data from SSA/DDS consultative examination referral PDFs and writes it to XLSX spreadsheets. It processes up to 50 PDFs per batch through a linear extraction pipeline.

**Target platforms**: Windows (primary), macOS (secondary), Linux (build/dev).
**Distribution**: jpackage installer with bundled JRE.
**Tech stack**: Kotlin, Compose Multiplatform (Desktop), Apache PDFBox, Tabula-java, Apache POI.

---

## 2. Layer Architecture

The application has three layers with strict dependency direction:

```
UI Layer  -->  Domain Layer  -->  Infrastructure Layer
```

### 2.1 Infrastructure Layer (`extraction/`, `output/`)

Contains all external library integrations: PDFBox text extraction, Tabula table extraction, Apache POI spreadsheet writing. These classes must not import anything from `ui/`.

### 2.2 Domain Layer (`pipeline/`, `model/`, `util/`)

Contains pipeline orchestration, shared data types, and cross-cutting utilities. Must not import anything from `ui/`. May import from `extraction/` and `output/`.

**Current deviation**: No `pipeline/` or `model/` packages exist yet. Pipeline orchestration is embedded in `ProcessingScreen.kt` (a UI composable). Shared model types (`ProcessedReferral`, `FileProcessingState`) are defined inside `ProcessingScreen.kt`. `FileStatus` is defined inside `StatusIcon.kt`. These must be extracted per sections 3 and 4.

### 2.3 UI Layer (`ui/`)

Contains Compose screens, components, and theming. May depend on domain and infrastructure layers. Must not contain business logic, pipeline orchestration, or file I/O beyond what is necessary for file selection dialogs.

### 2.4 Dependency Rules

- **UI must not instantiate extractors or writers directly.** All pipeline execution must go through a domain-layer orchestrator.
- **Domain must not import Compose.** No `androidx.compose.*` imports in `pipeline/`, `model/`, or `util/`.
- **Infrastructure classes must not depend on each other.** `PdfTextExtractor`, `TableExtractor`, and `SpreadsheetWriter` must not import one another.
- **No circular dependencies.** If package A imports from package B, package B must not import from package A.
- **Current conformance**: Dependency direction is already correct at the package level. The only violation is the pipeline-in-UI coupling described above.

---

## 3. Package Structure

### 3.1 Current Structure

```
tech.carbonworks.snc.batchreferralparser/
  Main.kt
  extraction/     -- PDF text extraction, table extraction, field parsing, data types
  output/         -- XLSX spreadsheet writing
  ui/
    screens/      -- Full-screen composables (MainScreen, ProcessingScreen, etc.)
    components/   -- Reusable UI components (CwButton, CwCard, StatusIcon, etc.)
    theme/        -- Color palette and Material theme
  util/           -- PhiMask, PhiPreferences
```

### 3.2 Target Structure

```
tech.carbonworks.snc.batchreferralparser/
  Main.kt
  extraction/     -- PDF text extraction, table extraction, field parsing
  model/          -- Shared data types used across layers
  output/         -- XLSX spreadsheet writing
  pipeline/       -- Batch processing orchestration
  ui/
    screens/
    components/
    theme/
  util/           -- Cross-cutting utilities (PHI masking, preferences)
```

### 3.3 Required Migrations

- **`ProcessedReferral`** must move from `ui.screens.ProcessingScreen` to `model/`.
- **`FileProcessingState`** must move from `ui.screens.ProcessingScreen` to `model/`.
- **`FileStatus`** must move from `ui.components.StatusIcon` to `model/`.
- **`Screen` enum** may remain in `Main.kt` (it is navigation-only and used exclusively there).
- **Pipeline orchestration** must move from the `LaunchedEffect` in `ProcessingScreen` to a new `BatchProcessor` class in `pipeline/`. See section 4.

---

## 4. Data Flow and Pipeline Contract

### 4.1 Pipeline Stages

The extraction pipeline is a linear sequence of four stages:

```
PDF File --> PdfTextExtractor --> TableExtractor --> FieldParser --> ReferralFields
```

Each stage has a defined input/output contract:

| Stage | Input | Output |
|-------|-------|--------|
| PdfTextExtractor | `File` | `ExtractionResult` (sealed: `Success` / `Error`) |
| TableExtractor | `File` | `List<ExtractedTable>` |
| FieldParser | `ExtractionResult.Success` + `List<ExtractedTable>` | `ParseResult` (fields + warnings) |
| SpreadsheetWriter | `List<ReferralFields>` + `File` (output dir) | `File` (written XLSX path) |

**Current conformance**: All stage contracts are well-defined via data classes and sealed types. This is correct.

### 4.2 Pipeline Orchestrator

A `BatchProcessor` class must own the pipeline wiring. It must:

1. Accept a list of `File` objects and a progress callback.
2. Instantiate extractors and parser internally.
3. Process each file through the full pipeline, catching per-file exceptions.
4. Return a `List<ProcessedReferral>` when the batch completes.
5. Be a plain Kotlin class with no Compose dependencies, testable with JUnit.

The `ProcessingScreen` composable must call `BatchProcessor` rather than instantiating `PdfTextExtractor`, `TableExtractor`, and `FieldParser` directly.

**Current deviation**: Pipeline orchestration lives in a `LaunchedEffect` inside `ProcessingScreen.kt` (lines 95-157). This is untestable without the Compose UI framework.

### 4.3 Single-Load PDF Document Sharing

`PdfTextExtractor` and `TableExtractor` currently each call `Loader.loadPDF(file)` independently, loading the same PDF into memory twice per file. The pipeline orchestrator should load the `PDDocument` once and pass it to both extractors.

To support this without coupling the extractors to each other, each extractor should accept an overloaded method that takes a `PDDocument` in addition to the current `File`-based method. The `File`-based method remains for standalone use and testing.

**Current deviation**: Both extractors independently load the PDF. For a 50-file batch, this doubles memory pressure.

### 4.4 Result Types

- `ExtractionResult` must remain a sealed class. All consumers must handle both `Success` and `Error` exhaustively.
- `ParseResult` wraps `ReferralFields` and `List<ParsingWarning>`. Parsing must never throw; it returns empty/partial fields and warnings instead.
- `ProcessedReferral` is the per-file pipeline output, carrying the source `File`, nullable `ReferralFields`, nullable error message, and warnings list.

---

## 5. State Management

### 5.1 Compose State Hoisting

All navigation and workflow state must be hoisted to the `App()` composable: `currentScreen`, `selectedFiles`, `fileStates`, `processingResults`. Screens must receive state as parameters and report changes via callbacks. No screen should own navigation decisions.

**Current conformance**: This pattern is correctly implemented in `App()`.

### 5.2 PHI Masking State

PHI masking state must be Compose-observable so that toggling masking triggers automatic recomposition of all affected composables. The current implementation uses a plain `var` in `PhiMask` and maintains a shadow `isMasked` state in `ResultsScreen` -- a dual-source-of-truth that is fragile and error-prone.

**Required resolution**: `PhiMask.maskingEnabled` must become a Compose `mutableStateOf` property, or masking state must be hoisted into the `App()` composable and threaded through screens as a parameter. The shadow state in `ResultsScreen` must be removed.

**Current deviation**: `PhiMask.maskingEnabled` is a plain Kotlin `var`. `ResultsScreen` maintains a separate `isMasked` that it manually syncs with `PhiMask.maskingEnabled`.

### 5.3 Preferences

All persistent preferences must be managed through a single centralized object using a single `Preferences` node.

**Required resolution**: Merge the standalone `Preferences.userRoot().node(...)` instance in `MainScreen.kt` with `PhiPreferences` into a unified `AppPreferences` object in `util/`. All preference reads and writes must go through this object.

**Current deviation**: `MainScreen.kt` creates its own `Preferences` instance at file scope (line 77-78) for the `lastDirectory` preference, separate from `PhiPreferences`.

---

## 6. PHI Safety Architecture

This application processes Protected Health Information. PHI safety is not optional -- it is a HIPAA compliance requirement.

### 6.1 Default-Masked Display

All PHI field values displayed in the UI must be masked by default on application launch. Masking must be enabled unless the user has explicitly opted into "show by default" via the Settings screen.

**Current conformance**: `PhiMask.maskingEnabled` initializes from `PhiPreferences.getShowByDefault()` (inverted), defaulting to masked.

### 6.2 Masking Contract

All UI composables that display extracted field values must call `PhiMask.maskDisplay()` or `PhiMask.maskValue()`. Direct display of `ReferralFields` property values in composable text without masking is prohibited.

**Current conformance**: All field display paths in `ResultsScreen` route through `PhiMask.maskDisplay()`.

### 6.3 XLSX Output

XLSX output must never be masked. The spreadsheet is the deliverable artifact and must contain raw extracted values regardless of the UI masking state. XLSX files are PHI artifacts subject to HIPAA handling requirements.

**Current conformance**: `SpreadsheetWriter` reads directly from `ReferralFields` and does not reference `PhiMask`.

### 6.4 Logging and Console Output

Log statements (currently `println`) must never output raw PHI field values. Permitted log content:

- File names, counts, progress indicators
- Column names, field names, structural metadata
- Stage names, success/failure status
- Warning and error messages (which must be passed through `PhiMask.maskDisplay()` if they may contain extracted content)

Diagnostic dump methods (see `FieldParser.dumpPageTextsDetailed()`) must pass all content lines through `PhiMask.maskDisplay()`.

**Current conformance**: Diagnostic dumps use `PhiMask.maskDisplay()`. Pipeline logging in `ProcessingScreen` logs counts and statuses, not field values. However, if masking is disabled at runtime, error messages containing PHI could reach the console unmasked. Error messages displayed in the UI are correctly masked.

### 6.5 Repository PHI Rules

- Real PHI must never be committed to the repository.
- Sample and test data must use synthetic/sanitized values.
- Test helpers that construct `ExtractionResult.Success` from strings must use obviously fake data.

---

## 7. Error Handling

### 7.1 Per-File Resilience

One file's failure must never abort the batch. The pipeline orchestrator must catch all exceptions per-file and record the error in `ProcessedReferral.error`. Processing must continue with the next file.

**Current conformance**: The `LaunchedEffect` in `ProcessingScreen` wraps each file in try/catch.

### 7.2 Sealed Result Types

`ExtractionResult` is a sealed class (`Success` / `Error`). All code consuming an `ExtractionResult` must handle both cases exhaustively via `when` expressions. The `Error` variant must carry a human-readable message, the source filename, and an optional `Throwable` cause.

**Current conformance**: `ExtractionResult` follows this pattern correctly.

### 7.3 Silent Failure Prohibition

Extraction stages must not silently swallow exceptions. When `TableExtractor` catches an exception and returns an empty list, it should log the failure (filename and exception type, not PHI content). This enables debugging without exposing PHI.

**Current deviation**: `TableExtractor` catches all exceptions and returns `emptyList()` with zero logging or reporting.

### 7.4 XLSX Save Errors

Save failures must be reported to the user via the UI. Stack traces should be logged to the console (when a logging framework is available). The error message displayed to the user must be passed through `PhiMask.maskDisplay()`.

**Current conformance**: `ResultsScreen.saveToXlsx()` catches exceptions and displays messages. Stack traces are not logged.

---

## 8. Performance Rules

### 8.1 Regex Compilation

All `Regex` objects used in `FieldParser` must be compiled once and stored as `companion object` properties or class-level `val` properties. Regex objects must not be constructed inside methods that execute per-file or per-page.

**Current deviation**: `FieldParser` compiles multiple `Regex` objects on every invocation of extraction methods. These must be lifted to the companion object.

### 8.2 PDF Document Lifecycle

Each PDF file must be loaded into memory at most once per pipeline execution. The loaded `PDDocument` must be closed promptly after both text and table extraction complete for that file. See section 4.3.

**Current deviation**: Each PDF is loaded twice (once by `PdfTextExtractor`, once by `TableExtractor`).

### 8.3 JVM Memory

The application runs with `-Xmx512m`. Pipeline code must be mindful of memory when processing 50-file batches. Close all `PDDocument`, `XSSFWorkbook`, and stream resources via `.use {}` blocks. Do not accumulate large intermediate data structures across files.

**Current conformance**: Both extractors use `.use {}` for document lifecycle. `SpreadsheetWriter` uses `.use {}` for the workbook and output stream.

### 8.4 Sequential Processing

Files are currently processed sequentially. This is acceptable for Phase 1. If parallel processing is introduced in the future, it must use `Dispatchers.IO.limitedParallelism()` to bound concurrent PDF loads and must ensure all state updates are thread-safe.

---

## 9. FieldParser Decomposition

`FieldParser.kt` is the largest file (~1044 lines) and concentrates multiple responsibilities: text reconstruction, five extraction stages, field merging, name parsing, diagnostic dump methods, and seven internal data classes.

### 9.1 Decomposition Strategy

`FieldParser` should be decomposed along extraction-stage boundaries:

1. **Internal data classes** (`HeaderFields`, `CaseFields`, `TableFields`, `InvoiceFields`, `ClaimantInfo`, `AppointmentInfo`, `FallbackFields`) must be extracted to their own file (e.g., `extraction/InternalFieldTypes.kt` or individual files).

2. **Extraction stage methods** should be extractable into focused classes that `FieldParser` delegates to. Each stage class takes page texts or table data as input and returns its stage-specific data class. Candidates:
   - `HeaderExtractor` -- `extractHeaderBlock()`
   - `TableFieldExtractor` -- `extractTableFields()`, `extractClaimantInfo()`, `extractAppointmentInfo()`, `extractServicesAuthorized()`
   - `InvoiceExtractor` -- `extractInvoiceFields()`
   - `CaseFieldExtractor` -- `extractCaseNumberComponents()`
   - `FallbackExtractor` -- `extractFallbackFields()`, `extractPhone()`

3. **`FieldParser.parse()`** remains the top-level orchestrator that calls stage extractors and merges results with defined priority: header > table > invoice > fallback.

4. **Diagnostic dump methods** (`dumpPageTexts`, `dumpPageTextsDetailed`) may remain in `FieldParser`'s companion object or move to a dedicated `DiagnosticDump` utility.

### 9.2 File Size Policy

No single `.kt` file should exceed 500 lines. Files approaching this limit should be evaluated for decomposition. This is a guideline, not a hard gate -- some files may legitimately be longer if they contain a single cohesive responsibility.

---

## 10. SpreadsheetWriter Column Alignment

`SpreadsheetWriter.COLUMN_HEADINGS` and `SpreadsheetWriter.extractRowValues()` must be kept in strict alignment: the N-th heading must correspond to the N-th value in the returned list. If a field is added to or removed from `ReferralFields`, both `COLUMN_HEADINGS` and `extractRowValues()` must be updated in the same commit.

### 10.1 Alignment Safety

A unit test must assert that `COLUMN_HEADINGS.size` equals the size of the list returned by `extractRowValues()` for an empty `ReferralFields()`. This test catches misalignment at compile time rather than at runtime.

**Current deviation**: No such test exists. The alignment is maintained only by developer discipline.

### 10.2 DATE_COLUMN_INDICES

The `DATE_COLUMN_INDICES` set (currently `{6, 7, 9}`) must match the positions of date-typed columns within `COLUMN_HEADINGS`. When columns are reordered or inserted, `DATE_COLUMN_INDICES` must be updated. The alignment test described above should also verify that each index in `DATE_COLUMN_INDICES` corresponds to a heading containing "Date" or "DOB".

---

## 11. Testing Requirements

### 11.1 Mandatory Test Coverage

| Area | Requirement |
|------|------------|
| FieldParser regex patterns | Every field extraction pattern must have at least one positive and one negative test case. |
| SpreadsheetWriter | Column count alignment test (section 10.1). Date formatting for each supported format. |
| Pipeline orchestration | Once `BatchProcessor` is extracted, it must have tests covering: successful batch, mixed success/error batch, empty batch, single-file batch. |
| PhiMask | Masking correctness for representative input shapes. Verify XLSX output is unmasked regardless of masking state. |
| PdfTextExtractor | Programmatic PDF creation tests for normal, multi-page, empty, corrupt, and encrypted inputs. |
| TableExtractor | Programmatic PDF creation tests for tables with borders and without. |

### 11.2 Test Independence

Tests must not depend on real PDF files from the client. All test PDFs must be programmatically generated or use synthetic data. Tests must not depend on file system state, network access, or display environment.

**Current conformance**: All existing tests use programmatic PDF generation or synthetic text construction.

### 11.3 Test Naming

Test methods should follow the pattern `methodName_condition_expectedResult` or a descriptive phrase that reads as a specification. Existing tests use descriptive phrases and this is acceptable.

### 11.4 No UI Tests Required (Phase 1)

Compose UI testing is not required for Phase 1. Extracting pipeline logic from the UI layer (section 4.2) is the higher priority, as it makes the core logic testable without a UI framework.

---

## 12. Conventions

### 12.1 Naming

- Packages: lowercase, dot-separated (`tech.carbonworks.snc.batchreferralparser.extraction`).
- Classes: PascalCase. Data classes for intermediate results use descriptive names (`HeaderFields`, `ParseResult`).
- Functions: camelCase. Extraction methods use `extract` prefix (`extractHeaderBlock`, `extractTableFields`).
- Constants: UPPER_SNAKE_CASE for `companion object` constants (`COLUMN_HEADINGS`, `DATE_COLUMN_INDICES`).
- Files: Match the primary class or object they contain. One public class per file.

### 12.2 Data Classes

All data transfer types between pipeline stages must be data classes. Data classes must be immutable (use `val` properties). Mutable `var` properties in data classes are prohibited.

**Current conformance**: All data classes use `val` properties.

### 12.3 Singleton Objects

Use `object` declarations only for truly stateless utilities (`SpreadsheetWriter`) or application-scoped singletons (`PhiMask`, `PhiPreferences`/`AppPreferences`). Singleton objects with mutable state must use Compose-observable state if that state affects the UI.

### 12.4 KDoc

All public classes and public methods must have KDoc comments. Internal/private methods should have KDoc if their purpose is not obvious from the name and signature.

**Current conformance**: KDoc coverage is thorough across the codebase.

---

## 13. Deviation Summary

For quick reference, these are the deviations from the target architecture that exist in the current code and should be resolved via future work packages:

| # | Deviation | Section | Priority |
|---|-----------|---------|----------|
| D1 | Pipeline orchestration embedded in `ProcessingScreen` `LaunchedEffect` | 4.2 | High |
| D2 | `ProcessedReferral`, `FileProcessingState`, `FileStatus` defined in UI files | 3.3 | High |
| D3 | `FieldParser.kt` at ~1044 lines with multiple responsibilities | 9.1 | High |
| D4 | Regex objects compiled per-invocation in `FieldParser` | 8.1 | High |
| D5 | Double PDF loading (text + table extractors each load independently) | 4.3, 8.2 | Medium |
| D6 | `PhiMask.maskingEnabled` is a plain `var`, not Compose-observable | 5.2 | Medium |
| D7 | No column/field alignment test in `SpreadsheetWriter` | 10.1 | Medium |
| D8 | Preferences duplicated between `MainScreen.kt` and `PhiPreferences` | 5.3 | Medium |
| D9 | `TableExtractor` silently swallows all exceptions | 7.3 | Low |
| D10 | `println` used throughout instead of a logging framework | 6.4 | Low |
