---
type: knowledge
status: active
created: 2026-02-17
updated: 2026-02-17
---

# Docling PDF Processing

IBM's open-source document conversion library with local AI models for table extraction and document understanding. Potential alternative to pdfplumber for complex PDF layouts.

---

## License

**MIT License** — one of the most permissive open-source licenses available.

| Permission | Allowed |
|---|---|
| Commercial use | Yes — free for paid products and internal business tools |
| Modification | Yes — can customize for specific workflows |
| Distribution | Yes — can package inside delivered tools |
| Private use | Yes — no obligation to share source code |

**Only condition**: Include the original copyright notice and MIT license text in distributed copies.

---

## AI Models

Docling bundles specialized AI models that run **locally** — no cloud services, no API credits, no per-page costs.

| Model | Purpose | License | Notes |
|---|---|---|---|
| TableFormer | Table structure recognition | Permissive (⚠️ verify exact license) | Detects table boundaries and cell structure |
| Granite-Docling | Document understanding | ⚠️ Claimed Apache 2.0 — verify | IBM's document layout model |

⚠️ **Verification needed**: Model license claims (e.g., "Apache 2.0" for Granite-Docling) come from AI-generated research and should be confirmed against IBM's official model cards before relying on them for license compliance.

**Key advantage**: Because models run locally on the user's hardware, there are zero ongoing costs and no data leaves the machine — important for PHI compliance.

---

## Library Comparison

| Library | License | Approach | Tables | Coordinates | Cloud Required | PHI Safe |
|---|---|---|---|---|---|---|
| **Docling** | MIT | AI-driven (layout models) | Excellent (TableFormer) | Yes | No | Yes |
| **pdfplumber** | MIT | Coordinate-based (deterministic) | Excellent (rule-based) | Yes (`.crop()`) | No | Yes |
| **PyMuPDF (fitz)** | AGPL | Low-level PDF parsing | Good | Yes | No | Yes — but AGPL complicates source delivery |
| **Amazon Textract** | Commercial/SaaS | Cloud AI OCR | Good | Yes | **Yes** | **No** — data sent to AWS |

---

## Docling vs pdfplumber: Trade-offs

| Dimension | Docling | pdfplumber |
|---|---|---|
| **Extraction approach** | AI-driven — models identify document structure automatically | Coordinate-based — developer defines bounding boxes per field |
| **Setup complexity** | Higher — requires model downloads (~1-2 GB), GPU beneficial | Lower — pure Python, pip install, no models |
| **Consistency on uniform PDFs** | Good, but AI introduces variability | Excellent — deterministic, same input = same output |
| **Handling layout changes** | Better — models adapt to structural variation | Brittle — layout changes break coordinate mappings |
| **Performance** | Slower (model inference per page) | Fast (0.1-0.3 sec/page for native text) |
| **Best for** | Varied document layouts, complex tables, mixed formats | Uniform government forms, known templates, high-volume batch |

**For the S&C use case** (standardized SSA referral PDFs, 40-50/week): pdfplumber is the primary choice due to deterministic extraction on uniform government forms. Docling is a viable alternative if PDFs turn out to have more structural variation than expected.

---

## PHI Compliance

| Criterion | Assessment |
|---|---|
| Data leaves machine | No — all processing local |
| Cloud dependency | None |
| Telemetry | None known (⚠️ verify in source) |
| Model inference | Local CPU/GPU only |
| HIPAA compatible | Yes — same profile as pdfplumber |

---

## When to Consider Docling Over pdfplumber

1. **PDF layouts vary significantly** — AI models handle structural variation better than fixed coordinates
2. **Complex nested tables** — TableFormer may outperform rule-based table detection
3. **Mixed document types** — processing multiple form types without per-type template definitions
4. **OCR + structure in one pass** — Docling combines OCR and layout analysis

## When pdfplumber Is Better

1. **Uniform, known templates** — deterministic extraction is faster and more reliable
2. **High-volume batch processing** — 10-100x faster per page
3. **Minimal dependencies** — no model downloads, no GPU considerations
4. **Debugging** — visual debugging tools make coordinate mapping transparent

---

## Applied In

- [Smith & Calabrese Consulting](/work/projects/smith-calabrese-consulting/) — evaluated as alternative to pdfplumber for Phase 1 PDF referral parser

---

## Sources

Source material: AI-generated research conversation about IBM's Docling library (Feb 2026). Cross-referenced against Docling GitHub repository (github.com/DS4SD/docling) for license confirmation. Model license claims flagged for independent verification.

---

*Last updated: 2026-02-17*
