---
type: knowledge
status: active
created: 2026-02-23
updated: 2026-02-23
---

# JVM/Kotlin PDF Extraction Ecosystem Research

Research into whether the existing Python PDF extraction pipeline (pdfplumber + Docling + openpyxl) can be replicated on the JVM for a single-runtime Compose Desktop application, or whether a dual-runtime (JVM + Python subprocess) approach is more practical.

**Context**: The S&C Phase 1 PDF referral parser currently uses ~1,900 lines of Python across 6 scripts to extract structured fields from SSA/DDS consultative examination referral PDFs. The question is whether this can live natively on the JVM inside a Compose Multiplatform Desktop app, or whether the JVM app should shell out to Python.

---

## 1. JVM PDF Libraries

### Apache PDFBox (Apache 2.0)

The most mature open-source Java PDF library. Provides low-level PDF manipulation, text extraction, and form field access.

**Text extraction**: `PDFTextStripper` extracts text from content streams. By default extracts in content-stream order (not visual order), but `setSortByPosition(true)` reorders by visual position. The `TextPosition` class exposes per-character metadata: X/Y coordinates, font name, font size, width, height. This is the JVM equivalent of pdfplumber's `.chars` — the raw data is there, but you must write the logic to interpret it.

**Table extraction**: PDFBox has **no built-in table extraction**. Tables in PDFs are not stored as tables — they are positioned text chunks and drawn lines. Extracting table structure requires custom logic on top of `TextPosition` data. Community projects like TrapRange and pdf-table build on PDFBox for this, but they are small, niche tools — not comparable to pdfplumber's `extract_tables()`.

**Form field extraction (AcroForm)**: PDFBox has strong AcroForm support via `PDAcroForm`. Can enumerate all form fields, read values by fully qualified name, and export FDF data. **If the SSA referral PDFs use AcroForm fields** (fillable PDF forms), PDFBox can extract field values directly — this would be significantly easier than coordinate-based text extraction. However, government-generated PDFs from web systems like ELRQ are more likely to be flattened (text positioned on page, no interactive fields).

**Comparison to pdfplumber**: PDFBox gives you the same raw data (character positions, font info, line/rectangle coordinates), but pdfplumber wraps this in a much more ergonomic API (`.crop()`, `.extract_words()`, `.extract_tables()`, visual debugging). Replicating pdfplumber's convenience on PDFBox means writing significant glue code.

**Verdict**: Viable but labor-intensive. Good foundation, but expect 2-3x more code for equivalent extraction logic vs. pdfplumber.

### iText 7/9 (AGPL v3 / Commercial)

Powerful commercial-grade PDF library. iText 9.3 (October 2025) added AI/ML-enhanced OCR support.

**Capabilities**: Text extraction, form filling, PDF generation, PDF/A compliance, table rendering. The `pdf2Data` add-on provides improved table extraction strategies. iText's `pdfOCR` module wraps Tesseract 4 and now also supports ONNX-based ML OCR engines.

**Licensing problem**: AGPL v3 means **any application using iText must release its complete source code** under AGPL if deployed to users over a network or distributed. The commercial license removes this requirement but costs money (pricing not publicly listed, reportedly $2,500+/year for small deployments).

**Verdict**: **Disqualified for this use case.** AGPL is incompatible with delivering a closed-source tool to S&C. Commercial license is overkill for a $1,500-budget consulting engagement.

### Tabula-java (MIT)

The engine behind the Tabula desktop app. Focused exclusively on table extraction from PDFs.

**How it works**: Analyzes lines and text block positions to identify table structures. Uses vertical/horizontal lines as column/row separators. Two modes: "lattice" (uses drawn lines) and "stream" (uses text alignment).

**Strengths**: Works well on clean, machine-generated PDFs with clear table structures and line borders. Good for straightforward multi-page documents.

**Limitations**:
- **Only works on text-based PDFs** — no OCR support for scanned documents.
- **No general text extraction** — tables only. You still need PDFBox or similar for non-table text.
- **Accuracy on complex layouts**: A 2025 benchmark found Tabula achieved ~67.9% average accuracy on diverse table banks, vs. pdfplumber's higher accuracy on complex structures, and TableFormer (Docling's AI model) at ~93.6%.
- **Over-detection**: In comparative tests, Tabula-py identified 21 "tables" in a paper containing only a few actual tables — it over-detects structure.
- Government forms with merged cells, nested headers, or non-standard layouts may be problematic.

**For the S&C use case**: The SSA referral tables are relatively simple (3 cells: claimant info, appointment, services). Tabula-java should handle these adequately, but the extraction still requires combining Tabula (tables) + PDFBox (header text, footer text, invoice fields).

**Verdict**: Useful as a supplement to PDFBox for table extraction. Not a standalone solution.

### Apache Tika (Apache 2.0)

A document-format detection and content extraction framework. Uses PDFBox internally for PDF parsing.

**What it adds over PDFBox**:
- Unified API across 1,000+ file formats (PDF, DOCX, images, etc.)
- Metadata extraction (author, creation date, etc.)
- Language detection
- For PDF/UA (accessible PDFs), Tika can extract marked content including tagged tables — if the SSA PDFs are tagged/accessible, this could simplify table extraction significantly.
- XHTML output preserves some layout structure.

**What it does NOT add**: Better text position extraction, better table detection for non-tagged PDFs, or any AI-driven document understanding.

**Verdict**: Marginal value over PDFBox alone for this use case. The SSA PDFs are unlikely to be PDF/UA tagged. Tika adds format detection overhead without improving extraction quality.

---

## 2. Key Questions Answered

### Q1: Can JVM libraries match pdfplumber's extraction quality?

**Short answer**: Yes, for raw data. No, for developer ergonomics.

PDFBox's `TextPosition` provides the same character-level data as pdfplumber's `.chars` (position, font, size). You can build equivalent `.crop()` and `.extract_words()` functionality on top of PDFBox. But pdfplumber gives you this out of the box with a clean API, visual debugging, and battle-tested table extraction.

**Specific gaps on the JVM**:
- No equivalent to `page.crop(bbox).extract_text()` — must manually filter `TextPosition` objects by coordinate range.
- No equivalent to `extract_tables()` — must use Tabula-java (different library, different paradigm) or build custom logic.
- No visual debugging tool to overlay extracted elements on the PDF page.
- No `.lines` / `.rects` convenience accessors for form structure detection.

**For government PDFs with consistent layouts**: The JVM can absolutely extract the same data. It just requires more custom code. Estimate 2-3x the line count for equivalent extraction logic.

### Q2: Is there a JVM equivalent to Docling's AI-driven document understanding?

**No native JVM equivalent exists.** However, there are two paths:

**Path A: Docling-serve (REST API)**
Docling now offers `docling-serve`, a FastAPI-based REST server with an official Java client (`docling-serve-client`). You can:
1. Run `docling-serve` locally (Docker/Podman or Python package) on the same machine.
2. Call it from the JVM app via HTTP on `localhost:5001`.
3. Get the same TableFormer + Granite-Docling AI capabilities.

This means you lose single-runtime simplicity but gain Docling's full AI pipeline. The Java client uses Java's built-in `HttpClient` and Jackson for JSON. Arconia framework provides Spring Boot integration, but for a Compose Desktop app you would use the client directly.

**Path B: ONNX Runtime on JVM**
The Docling models (TableFormer, Granite-Docling) could theoretically be loaded via ONNX Runtime's Java bindings. This would be true single-runtime, but it is an unsupported/undocumented path — you would need to export the models to ONNX format yourself and handle all pre/post-processing.

**Path C: Accept the loss**
For uniform government forms, Docling's AI capabilities are the fallback, not the primary path. The deterministic pdfplumber approach (coordinate-based templates) is the primary extractor. If the JVM deterministic extraction works for 95%+ of PDFs, the Docling fallback becomes a rarely-used safety net that may not justify the complexity.

### Q3: How does Tabula-java compare to pdfplumber for table extraction?

| Dimension | Tabula-java | pdfplumber |
|-----------|------------|------------|
| **Accuracy (benchmarks)** | ~67.9% on diverse table banks | Higher on complex structures |
| **Best for** | Clean PDFs with clear line borders | Complex layouts, irregular structures |
| **API** | Table-only; returns List of Table objects | Full page access; tables, text, chars, lines |
| **OCR support** | None | None (but works with pre-OCR'd text) |
| **False positives** | Over-detects tables in complex documents | More conservative, fewer false positives |
| **Integration** | Standalone JAR, MIT license | Python only |

For the SSA referral forms specifically: both tools should handle the relatively simple 3-cell table structure adequately. The difference matters more for complex, varied documents.

### Q4: OCR on the JVM?

**Tess4J** (Apache 2.0): JNA wrapper around Tesseract OCR. Ships with native libraries included in the JAR. Supports TIFF, JPEG, PNG, BMP, and PDF input. Uses Tesseract 4.1.3 under the hood.

**Tesseract Platform (JavaCPP Presets)**: JNI-based alternative to Tess4J. Lower-level but potentially better performance.

**iText pdfOCR**: Wraps Tesseract 4 and also supports ONNX-based ML OCR engines (new in iText 9.3). But AGPL-licensed — same disqualifier as iText itself.

**Practical note**: OCR is likely unnecessary for the ELRQ-generated SSA referral PDFs, which are natively digital. Tess4J is the safe choice if OCR is needed — same underlying Tesseract engine already installed (5.4.0), just accessed via JNA instead of subprocess.

**Packaging consideration**: Tess4J bundles native Tesseract libraries for the target platform. On Windows, this means shipping Tesseract DLLs inside the JAR (or alongside the app). The tessdata language files (~15MB for English) must also be bundled or downloaded.

### Q5: XLSX generation on JVM?

**Apache POI** (Apache 2.0): The standard. Full Excel format support (HSSF for .xls, XSSF for .xlsx, SXSSF for streaming .xlsx). Supports cell formatting, formulas, conditional formatting, charts, pivot tables. This is the direct equivalent of openpyxl.

**FastExcel** (Apache 2.0): 5-10x faster than POI for large files, much lower memory footprint. Limited feature set (basic styling, no charts). For generating formatted XLSX output with specific cell styles, Apache POI is the better fit.

**Kotlin wrappers**: ExcelKt and VSXSSF provide Kotlin DSL syntax over POI. Nice ergonomics but thin wrappers.

**Verdict**: Apache POI is the direct, full-featured equivalent of openpyxl. No capability gap.

---

## 3. Architecture Analysis: Single Runtime vs. Dual Runtime

### Option A: Pure JVM (Single Runtime)

**Stack**: PDFBox + Tabula-java + Apache POI + (optional) Tess4J

**Pros**:
- Single jpackage installer (~60-100MB with bundled JRE)
- No Python dependency to install/manage on 6 office machines
- One language for the whole app (Kotlin)
- Simpler deployment and updates
- No subprocess coordination or error handling across runtimes

**Cons**:
- Must rewrite ~1,900 lines of working Python extraction logic in Kotlin
- Lose pdfplumber's ergonomic API — more boilerplate code for equivalent extraction
- Lose Docling AI fallback unless running docling-serve separately
- No visual debugging tool for PDF extraction development
- Estimated 3-5x development time for the extraction layer vs. using existing Python scripts

**Migration effort estimate**: The current Python scripts are ~1,900 lines across 6 files. The core field extraction (`extract_referral_fields.py`, 572 lines) is mostly regex parsing on text — this translates to Kotlin almost line-for-line. The pdfplumber bridge code (`pdf_to_docling_shape`, ~65 lines) would become 150-200 lines of PDFBox code. The Docling scripts would be replaced by docling-serve HTTP calls or dropped entirely. Total: ~2,500-3,000 lines of Kotlin, estimated 2-3 weeks of development to reach feature parity.

### Option B: JVM + Python Subprocess (Dual Runtime)

**Stack**: Compose Desktop (UI) + `ProcessBuilder` calling Python scripts

**Pros**:
- Keep working Python extraction code as-is (zero rewrite)
- Preserve pdfplumber and Docling capabilities at full fidelity
- Faster time to market — focus on building the UI, not reimplementing extraction
- Can migrate extraction to JVM incrementally later if desired

**Cons**:
- Must bundle Python runtime (embeddable Python ~20MB, plus pip packages)
- jpackage installer grows to ~150-200MB (JRE + Python + packages)
- Two languages to maintain (Kotlin UI + Python extraction)
- Subprocess error handling complexity (exit codes, stderr parsing, encoding issues)
- Python version management on 6 Windows machines
- Cross-platform path handling (Windows backslashes, spaces in paths, etc.)

**Subprocess mechanics**: `ProcessBuilder` in the JVM can execute Python scripts:
```kotlin
val process = ProcessBuilder("python", "scripts/extract_referral_fields.py", pdfPath)
    .directory(File(appDir))
    .redirectErrorStream(true)
    .start()
val output = process.inputStream.bufferedReader().readText()
val exitCode = process.waitFor()
```

### Option C: JVM UI + docling-serve (Microservice)

**Stack**: Compose Desktop (UI) + PDFBox (basic extraction) + docling-serve (AI fallback)

**Pros**:
- Primary extraction in Kotlin (no Python subprocess for the common case)
- AI fallback available via local HTTP API
- docling-serve can run as a system service or Docker container
- Official Java client available

**Cons**:
- Requires Docker or Python environment for docling-serve
- Two services to install and manage
- More complex than pure JVM, less complex than full Python subprocess

### Recommendation

**For the S&C Phase 1 tool (near-term, 2-week engagement)**: Option B (dual runtime) is the pragmatic choice. The extraction code works. The client needs the tool fast. Spend time on UI, not reimplementing extraction.

**For a productized version (long-term)**: Option A (pure JVM) with Option C as fallback. Once the extraction logic is proven and stable, migrating to Kotlin/PDFBox is a bounded effort that eliminates the Python dependency and simplifies distribution.

---

## 4. Compose Multiplatform Desktop Specifics

### File System Operations

Compose Desktop runs on the JVM, so standard `java.io.File` and `java.nio.file.Path` APIs work without restriction. No sandboxing like mobile platforms. The app has full filesystem access to read PDFs, write XLSX, create temp files, etc.

### Subprocess Execution

`java.lang.ProcessBuilder` and `Runtime.exec()` work as expected. No restrictions on spawning subprocesses. This is how Option B would call Python scripts. Key considerations:
- Use absolute paths for the Python interpreter
- Handle Windows path separators
- Set working directory explicitly
- Read stdout/stderr on separate threads to avoid deadlocks
- Set timeouts with `process.waitFor(timeout, TimeUnit.SECONDS)`

### Native OS Integration Gotchas

Based on production experience reports (Composables.com blog, various 2025 discussions):

- **File picker**: No built-in native file picker. Must use AWT/Swing `FileDialog` or `JFileChooser` (which falls back to Swing look and feel). Third-party library `compose-multiplatform-file-picker` (Wavesonics) wraps native dialogs.
- **System tray**: The Tray composable works but "looks really dated on Windows" with limited customization.
- **Notifications**: Basic only — cannot dismiss programmatically, set actions, or customize icons/sounds.
- **Auto-update**: No built-in mechanism. Must implement yourself or use a third-party solution.
- **Native APIs**: Using platform-specific APIs via Java Bridge is possible but poorly documented. The community is small compared to Electron or native frameworks.

### jpackage + Compose Desktop Distribution

**Installer sizes**:
- Hello World: ~30-40MB (.msi, .dmg, .deb)
- Real app with dependencies: ~60-120MB (after jlink optimization)
- Full JDK bundled without jlink: ~300-400MB

**Windows requirements**:
- WiX Toolset 3.x must be installed on the build machine (not the target machine)
- Produces .msi or .exe installers
- Must build on Windows for Windows targets (no cross-compilation)

**macOS requirements**:
- Must build on macOS for macOS targets
- DMG icon may show Java's Duke icon instead of custom icon (known bug)
- Universal (ARM + Intel) apps not supported by default
- No auto-prompt to move app to Applications folder

**Deployment to 6 office machines**:
- Generate .msi on a Windows build machine
- Distribute via USB, network share, or simple download link
- No auto-update mechanism — manual reinstall for updates
- Alternative: Hydraulic Conveyor (JetBrains-recommended) provides better packaging and auto-update, but adds another dependency

---

## 5. Summary Decision Matrix

| Factor | Pure JVM (Option A) | JVM + Python (Option B) | JVM + docling-serve (Option C) |
|--------|-------------------|----------------------|-------------------------------|
| **Installer size** | 60-100MB | 150-200MB | 60-100MB + Docker/Python |
| **Deployment complexity** | Low (single .msi) | Medium (msi + Python) | High (msi + service) |
| **Development effort** | 2-3 weeks for extraction | Days (existing code) | 1-2 weeks for PDFBox basics |
| **Extraction quality** | Good (but more code) | Proven (pdfplumber) | Good + AI fallback |
| **Maintenance burden** | One language | Two languages | Two services |
| **AI fallback (Docling)** | No (unless add service) | Yes (full) | Yes (via HTTP) |
| **Risk** | Extraction parity risk | Subprocess reliability | Service management |
| **Best for** | Productized tool | Quick delivery | Balanced approach |

---

## Sources

Research conducted 2026-02-23. Key sources:

- [Apache PDFBox FAQ & Text Extraction docs](https://pdfbox.apache.org/2.0/faq.html)
- [PDFBox TextPosition API](https://pdfbox.apache.org/docs/2.0.13/javadocs/org/apache/pdfbox/text/TextPosition.html)
- [Parsing structured data with PDFBox (Robin Howlett)](https://robinhowlett.com/blog/2019/11/29/parsing-structured-data-complex-pdf-layouts/)
- [iText AGPL License terms](https://itextpdf.com/how-buy/AGPLv3-license)
- [iText Suite 9.3 release (October 2025)](https://itextpdf.com/blog/itext-suite-93-smarter-validation-enhanced-ocr-smaller-files)
- [Tabula-java GitHub](https://github.com/tabulapdf/tabula-java)
- [OpenNews: Search for best tabular extraction tool (2024)](https://source.opennews.org/articles/our-search-best-tabular-data-extraction-tool-2024-/)
- [Comparing 6 frameworks for rule-based PDF parsing](https://www.ai-bites.net/comparing-6-frameworks-for-rule-based-pdf-parsing/)
- [Tess4J GitHub](https://github.com/nguyenq/tess4j)
- [FastExcel GitHub](https://github.com/dhatim/fastexcel)
- [Apache POI documentation](https://poi.apache.org/)
- [ExcelKt - Kotlin wrapper for POI](https://github.com/evanrupert/ExcelKt)
- [Docling Java support (Thomas Vitale)](https://www.thomasvitale.com/ai-document-processing-docling-java-arconia-spring-boot/)
- [docling-serve GitHub](https://github.com/docling-project/docling-serve)
- [Docling Java client API docs](https://docling-project.github.io/docling-java/dev/docling-serve/serve-api/)
- [Compose Desktop in Production: Gotchas (Composables.com)](https://composables.com/blog/compose-desktop)
- [Compose Multiplatform native distribution docs](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html)
- [Kotlin Multiplatform 2025 roadmap (JetBrains)](https://blog.jetbrains.com/kotlin/2025/08/kmp-roadmap-aug-2025/)
- [Unstract: Best Python table extraction libraries (2026)](https://unstract.com/blog/extract-tables-from-pdf-python/)
- [Comparative study of PDF parsing tools (arXiv:2410.09871)](https://arxiv.org/html/2410.09871v1)

---

*Last updated: 2026-02-23*
