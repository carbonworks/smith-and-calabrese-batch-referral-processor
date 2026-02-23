---
type: analysis
status: pending
project: smith-calabrese-consulting
created: 2026-02-17
updated: 2026-02-17
---

# Library Comparison: pdfplumber vs Docling

Side-by-side quality assessment of dual-library extraction on SSA referral sample PDFs.

**Status**: Waiting for extraction results from sample PDFs.

---

## Test Conditions

| Item | Value |
|------|-------|
| Sample PDFs | *(count and description TBD — pending from Tyler)* |
| pdfplumber version | |
| Docling version | |
| Python version | 3.13.12 |
| Machine | Windows 11 |

---

## Per-Sample Results

### Sample 1: *(filename)*

| Criterion | pdfplumber | Docling | Winner |
|-----------|-----------|---------|--------|
| Text extraction quality | | | |
| Table detection | | | |
| Field position accuracy | | | |
| Handling of form structure | | | |
| Output size (JSON KB) | | | |
| Processing time | | | |
| Notes | | | |

*(Copy this table for each additional sample.)*

---

## Summary Comparison

| Criterion | pdfplumber | Docling | Notes |
|-----------|-----------|---------|-------|
| **Text quality** | | | Completeness, encoding, whitespace handling |
| **Table detection** | | | Found correct tables? Correct cell boundaries? |
| **Field positions** | | | Can we map fields to coordinates reliably? |
| **Consistency across samples** | | | Same approach works for all samples? |
| **Edge case handling** | | | Multi-page, scanned pages, unusual layouts |
| **Processing speed** | | | Time per PDF |
| **Output usability** | | | How easy to build extraction rules from output |

---

## Recommendation

**Primary library**: *(TBD)*
**Rationale**: *(TBD)*

**Fallback plan**: *(TBD — when would we use the other library?)*

---

## Decision Gate

This document feeds into the **Library Choice** decision gate in the build plan.

**Decision maker**: Randall
**Depends on**: Dual-library extraction results from all sample PDFs
**Status**: [ ] Pending

---

*Last updated: 2026-02-17*
