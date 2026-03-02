# Tech Stack Decision: Kotlin/Compose Multiplatform

**Date**: 2026-02-23
**Decision maker**: Randall Mitchell
**Status**: Final

---

## Decision

Build the S&C Phase 1 PDF referral parser as a **pure Kotlin/Compose Multiplatform Desktop** application using JVM-native PDF libraries.

---

## Production Stack

| Component | Library | Version | License | Purpose |
|-----------|---------|---------|---------|---------|
| PDF text extraction | Apache PDFBox | 2.x/3.x | Apache 2.0 | Core text and coordinate extraction |
| Table extraction | Tabula-java | 1.x | MIT | Structured table data from PDFs |
| XLSX output | Apache POI | 5.x | Apache 2.0 | Spreadsheet generation |
| OCR fallback | Tess4J | 5.x | Apache 2.0 | JNA wrapper for Tesseract (scanned pages) |
| Desktop UI | Compose Multiplatform | 1.x | Apache 2.0 | Cross-platform Windows + macOS |
| Build/packaging | Gradle + jpackage | — | — | Build, bundle JRE, create .msi/.dmg |

---

## Rationale

### Why Kotlin/Compose Multiplatform

1. **Randall's expertise**: Deep Android/Compose experience translates directly to Compose Desktop. Kotlin is the strongest language in the toolbox.
2. **Cross-platform from day one**: Single codebase produces Windows (.msi) and macOS (.dmg) installers. S&C's 6 offices are Windows, but macOS support comes free.
3. **Single runtime**: One JVM, one language, one build system. No polyglot complexity.
4. **Simpler packaging**: jpackage bundles the JRE into a ~60-100MB installer. No Python runtime to manage on client machines.
5. **JVM ecosystem depth**: PDFBox, Tabula-java, Apache POI are mature, well-documented, MIT/Apache-licensed libraries with large communities.

### What Was Evaluated and Dropped

| Option | Why dropped |
|--------|-------------|
| **Python backend** (pdfplumber + Docling + openpyxl) | Would require shipping Python runtime + pip packages on client machines, or running as subprocess from JVM UI. Dual-runtime complexity. |
| **Tauri** (Rust + HTML/CSS/JS) | Good framework, but adds Rust toolchain and still needs Python subprocess for extraction. Three languages. |
| **C# WPF** (.NET 8) | Viable, but Windows-only. Randall's expertise is stronger in Kotlin/Compose. |
| **Electron** | Same capabilities as Tauri but 80-150MB installer. No advantage. |
| **PyQt6 / customtkinter** | Python-native UI but weaker theming, worse data grid, larger than Tauri. |

### Migration Effort from Python

The Python prototype is ~1,900 lines across 6 scripts. Key translation notes:

- **Core field extraction** (`extract_referral_fields.py`, 572 lines): Mostly regex parsing on extracted text. Translates to Kotlin nearly line-for-line.
- **PDF coordinate extraction**: pdfplumber's `.crop()` / `.extract_text()` becomes PDFBox `PDFTextStripper` with coordinate filtering — more code (~2-3x), but equivalent capability.
- **Table extraction**: pdfplumber's `extract_tables()` becomes Tabula-java calls — different API, same data.
- **XLSX generation**: openpyxl → Apache POI — mature equivalent, no capability gap.
- **Docling scripts**: Dropped for production. Docling was the AI-driven fallback; for uniform government forms, deterministic extraction (PDFBox + Tabula-java) is sufficient. Docling-serve available as future fallback if needed.

**Estimated total**: ~2,500-3,000 lines of Kotlin to reach feature parity. The Python scripts are preserved in `reference/python-scripts/` as extraction logic reference.

---

## Key Trade-offs Accepted

1. **More extraction code**: PDFBox requires more boilerplate than pdfplumber for equivalent operations. Accepted because it eliminates the Python runtime dependency.
2. **No Docling AI fallback**: The AI-driven document understanding from Docling is not available in pure JVM. Accepted because SSA referral forms are uniform government-generated PDFs — deterministic extraction should handle 95%+ of cases. Docling-serve (local HTTP API) is available as a future addition if needed.
3. **No visual PDF debugging**: pdfplumber's visual debugging overlay has no JVM equivalent. Accepted; will use logging and test-driven development instead.

---

## Full Research

See [JVM PDF Extraction Ecosystem Research](research/jvm-pdf-extraction-research.md) for the complete library evaluation, architecture analysis, and benchmarks that informed this decision.
