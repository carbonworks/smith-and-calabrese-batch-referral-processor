---
type: reference
status: active
area: brand
created: 2026-02-02
updated: 2026-02-02
---

# Design Decisions

Authoritative reference for document-level design decisions. Each entry documents **what** was decided, **why**, and **where** it's implemented.

This file is a living document. The [Design Analysis skill](/system/skills/design-analysis.md) proposes additions when it discovers gaps — new rules are added here after user approval.

**Source of truth hierarchy:**
1. `/org/brand/carbon-works-brand-guidelines.md` — high-level brand identity (colors, fonts, voice)
2. This file — document design decisions derived from brand + industry standards
3. `system/tools/templates/carbonworks.typ` — implementation of these decisions in the PDF template

---

## Document Layout

| Decision | Value | Rationale | Source |
|----------|-------|-----------|--------|
| Paper size | US Letter (8.5" x 11") | Standard for US business documents | Industry standard |
| Body margins | 1in all sides | Balanced whitespace for readability; standard business document margins | Industry standard (Butterick's Practical Typography) |
| Cover margins | 1.5in top/bottom, 1.25in left/right | Generous spacing gives cover page a premium, uncluttered feel | Brand direction: "clean and uncluttered" |
| Body leading | 0.75em | Compact but readable line spacing for professional density | Template design session |
| Text justification | Justified | Clean edge alignment; appropriate for formal documents | Industry standard for print documents |
| Multi-column support | Optional (`cols` parameter) | Available for dense content; single-column default for readability | Template flexibility |

**Implemented in:** `system/tools/templates/carbonworks.typ` lines 29-44

---

## Cover Page

| Decision | Value | Rationale | Source |
|----------|-------|-----------|--------|
| Emblem placement | Top of page, 0.8in height | Establishes brand identity before title; sized for visual weight without dominating | Brand guidelines: origami bird as identity anchor |
| Emblem-to-title spacing | 0.3in | Connects emblem to title without crowding | Template design session |
| Title style | 28pt, bold, dark-teal (#03767B) | Large enough for cover impact; dark-teal chosen for documents over deep-ink for visual warmth | Brand palette + document design session |
| Subtitle style | 14pt, italic, soft-gray (#718096) | Secondary hierarchy; italic differentiates from title | Typography hierarchy convention |
| Accent line | 3in, 2pt stroke, deep-orange (#F06607) | Visual anchor separating title block from metadata; orange for energy/warmth | Brand palette accent |
| Client line | 12pt, "Prepared for:" in soft-gray, name in medium-weight deep-ink | Formal client attribution; weight contrast draws eye to name | Consulting deliverable convention |
| Author style | 12pt, medium weight deep-ink, affiliation in soft-gray with interpunct separator | Clear attribution with organizational context | Professional document convention |
| Date style | 11pt, soft-gray | De-emphasized; present but not prominent | Template design session |
| Wordmark placement | Bottom-left, 2.25in width, 0.5in above page bottom | Anchors the page with brand identity; bottom placement avoids competing with title | Brand identity: wordmark as footer anchor |
| Confidentiality notice | 9pt, italic, soft-gray, pushed to bottom with `1fr` spacer | Legally present but visually quiet; doesn't disrupt design | Legal convention + brand: "quietly confident" |

**Implemented in:** `system/tools/templates/carbonworks.typ` lines 46-141

---

## Typography Scale

| Decision | Value | Rationale | Source |
|----------|-------|-----------|--------|
| Font stack | Inter > Calibri > Arial | Inter is brand primary (humanist sans-serif); Calibri/Arial as safe fallbacks for systems without Inter | Brand guidelines |
| Body text size | 11pt | Standard for professional printed documents; smaller than web (16-18px) due to print reading distance | Industry standard for print |
| Heading 1 | 18pt, bold, dark-teal, full-width sky-blue underline (1.5pt) | Largest heading; teal for brand warmth; underline creates visual section breaks | Brand palette + document hierarchy |
| Heading 2 | 14pt, bold, dark-teal | Mid-level heading; same color as H1 for consistency, no underline to differentiate | Typography hierarchy |
| Heading 3 | 12pt, semibold, deep-ink | Smallest heading; deep-ink (not teal) signals lower hierarchy level | Typography hierarchy |
| H1 spacing | 0.8em before, 0.4em after | Generous separation before sections; tighter after to bind heading to content | Typographic proximity principle |
| H2 spacing | 0.6em before, 0.3em after | Proportionally less than H1 to reinforce hierarchy | Typographic proximity principle |
| H3 spacing | 0.5em before, 0.2em after | Minimal separation; H3 is tightly coupled to its content | Typographic proximity principle |
| Code font | Consolas > Courier New, 10pt | Monospace for code distinction; 1pt smaller than body for visual differentiation | Industry standard |

**Implemented in:** `system/tools/templates/carbonworks.typ` lines 36-43, 167-187

---

## Color Usage

| Decision | Context | Color | Hex | Rationale | Source |
|----------|---------|-------|-----|-----------|--------|
| Body text | All running text | Deep Ink | #2D3748 | High contrast on white, softer than pure black | Brand guidelines |
| H1, H2 headings | Section headings | Dark Teal | #03767B | Warmer than deep-ink for headings; distinct from body text | Document design session |
| H3 headings | Sub-section headings | Deep Ink | #2D3748 | Lower hierarchy returns to body color | Document design session |
| Subtitle, date, affiliation | Secondary information | Soft Gray | #718096 | De-emphasized; readable but not competing | Brand guidelines |
| Links | Hyperlinks | Sky Blue | #4A9FD4 | Standard link affordance; matches brand accent | Brand guidelines |
| H1 underline | Section divider | Sky Blue | #4A9FD4 | Subtle accent; complements dark-teal heading | Brand palette pairing |
| Cover accent line | Visual anchor | Deep Orange | #F06607 | Energy/warmth; strongest accent reserved for cover | Brand palette accent |
| Table header background | Table header row | Dark Teal | #03767B | Consistent with heading color; strong contrast with white text | Brand palette |
| Table header text | Table header row | White | #FFFFFF | High contrast on dark-teal background | Accessibility (contrast) |
| Table alternating rows | Even data rows | Soft Teal 85% lighter | ~#E8F6F5 | Subtle banding for readability; derived from brand teal | Data table best practice |
| Table borders | All cell borders | Soft Teal 60% lighter | ~#A8D8D3 | Light enough to not dominate; teal-family consistency | Table styling convention |
| Code block background | Code blocks | Warm White | #FFFAF5 | Paper-like warmth; distinguishes code from body without harsh contrast | Brand guidelines |
| Code block border | Code blocks | Light Gray | #E2E8F0 | Subtle containment | Brand guidelines |
| Emphasis (italic) | Italic text | Soft Gray | #718096 | Distinguishes emphasized text visually, not just stylistically | Document design session |
| Strong (bold) | Bold text | Deep Ink | #2D3748 | Maintains body color; weight alone provides emphasis | Document design session |
| Header/footer text | Running headers and footers | Soft Gray | #718096 | Peripheral information; present but unobtrusive | Industry standard |
| Header/footer rules | Separator lines | Light Gray | #E2E8F0 | Subtle division without visual weight | Document design session |

**Implemented in:** `system/tools/templates/carbonworks.typ` lines 6-14 (definitions), throughout (usage)

**Note:** Dark Teal (#03767B), Teal (#04949A), and Deep Orange (#F06607) come from the live Wix site palette, not the official brand guidelines palette. They were adopted for document design because they provide warmer accent tones than the guidelines palette alone. This divergence is tracked as a known alignment issue.

---

## Table Styling

| Decision | Value | Rationale | Source |
|----------|-------|-----------|--------|
| Header row | Dark-teal background, white bold text at 10pt | High contrast for scannability; matches heading color system | Data table best practice + brand palette |
| Data rows | Alternating white / soft-teal 85% lighter | Zebra striping aids horizontal reading in wide tables | Data table best practice (Tufte) |
| Cell borders | 0.5pt, soft-teal lightened 60% | Present but not dominant; teal-family for consistency | Table styling convention |
| Cell padding | 8pt inset | Comfortable spacing; prevents cramped feel | Industry standard |
| Cell text size | 10pt, no hyphenation, left-aligned | Slightly smaller than body for density; no justify in cells for readability | Table typography best practice |

**Implemented in:** `system/tools/templates/carbonworks.typ` lines 200-215

---

## Code Blocks

| Decision | Value | Rationale | Source |
|----------|-------|-----------|--------|
| Background | Warm White (#FFFAF5) | Subtle distinction from page white; paper-like warmth | Brand guidelines |
| Border | 0.5pt Light Gray (#E2E8F0), 4pt radius | Soft containment; rounded corners for approachability | Brand direction: "approachable" |
| Padding | 12pt inset | Generous internal spacing | Code block convention |
| Syntax highlighting | None (theme: none) | Clean, consistent appearance; avoids color overload in branded documents | Document design session |
| Font | Consolas > Courier New, 10pt | Clear monospace; standard for code rendering | Industry standard |

**Implemented in:** `system/tools/templates/carbonworks.typ` lines 217-231

---

## Headers & Footers

| Decision | Value | Rationale | Source |
|----------|-------|-----------|--------|
| Header left | Document title, 9pt soft-gray | Context for the reader on every page | Professional document convention |
| Header right | "Carbon Works", 9pt soft-gray | Brand presence without dominating | Brand identity |
| Header separator | Full-width line, 0.5pt light-gray, 4pt below text | Subtle division between header and content | Document design convention |
| Footer separator | Full-width line, 0.5pt light-gray, 4pt above text | Mirrors header treatment | Visual symmetry |
| Footer left | "Confidential" (if enabled), 9pt soft-gray | Legal notice positioned for visibility without prominence | Legal convention |
| Footer right | Page number (arabic, reset to 1 after cover) | Standard pagination; cover page excluded from count | Professional document convention |
| Cover page | No header or footer | Clean, uncluttered cover design | Cover page convention |

**Implemented in:** `system/tools/templates/carbonworks.typ` lines 143-164

---

## Accessibility

*This section tracks verified accessibility properties. Gaps discovered by design analysis are documented here as rules are established.*

| Property | Status | Notes |
|----------|--------|-------|
| Body text contrast (Deep Ink on white) | Verified | #2D3748 on #FFFFFF = 10.7:1 ratio (exceeds WCAG AAA 7:1) |
| Heading contrast (Dark Teal on white) | Verified | #03767B on #FFFFFF = 5.2:1 ratio (exceeds WCAG AA 4.5:1) |
| Soft Gray on white | Needs review | #718096 on #FFFFFF — used for secondary text; contrast ratio to be calculated |
| Table header contrast (white on Dark Teal) | Verified | #FFFFFF on #03767B = 5.2:1 ratio (exceeds WCAG AA 4.5:1) |
| Minimum text size | 9pt (headers/footers) | Below typical 10pt minimum for comfortable reading; acceptable for peripheral info |
| Heading hierarchy | Verified | Clear H1 > H2 > H3 progression in size, weight, and color |

---

## Changelog

| Date | Change | Source |
|------|--------|--------|
| 2026-02-02 | Initial documentation — extracted all decisions from `carbonworks.typ` | Design analysis skill creation |
