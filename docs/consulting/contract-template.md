# Contract Template: Services Agreement

Reusable template for Carbon Works consulting services agreements. Derived from the S&C Phase 1 contract (February 2026) and refined through self-review.

**Usage**: Copy the Template section below into a new file, fill in the bracketed placeholders, and remove sections that don't apply (e.g., PHI for non-healthcare clients, Feasibility for straightforward projects).

---

## Pre-Flight Checklist

Run this before generating a PDF. Every item was a real mistake caught during the S&C contract review.

| Check | What to Verify |
|-------|----------------|
| **Abbreviations** | Every abbreviation is spelled out on first use with the short form in parentheses |
| **Numbered lists** | All obligations, deliverables, and responsibilities use numbered lists (not bullets) — easier to reference in conversation |
| **No placeholders** | No `[extension]`, `[TBD]`, or similar — every value is concrete |
| **File formats specified** | Output formats named explicitly (e.g., ".xlsx", not "spreadsheet file") |
| **Plain language** | Client responsibilities are written so a non-technical person understands them without consulting jargon |
| **Section numbering** | Sequential with no duplicates — check after adding/removing sections |
| **Signature blocks** | Stacked format (Signature/Name/Date per party), not a table |
| **No trailing tagline** | Document ends at signatures — no "Making sense of technology" after the last signature |
| **Liability carve-out** | Limitation of liability includes negligence/misconduct/PHI carve-out — CW stands behind its work |
| **Support clause** | Post-project support references industry-standard rates and specifies what the client needs to provide (e.g., sample files for format changes) |
| **Timeline specifics** | "Business days" stated, "confirmation" not "receipt" for start triggers |
| **Brand voice** | Professional but accessible — add plain-language summaries for legal sections (especially IP) |

---

## Standard Sections

Every CW services agreement follows this structure. Sections marked *(conditional)* are included only when applicable.

| # | Section | Purpose | Always? |
|---|---------|---------|---------|
| 1 | Situation | Restate client's problem in their language | Yes |
| 2 | Objectives | What the tool/deliverable will do (numbered) | Yes |
| 3 | Scope of Work | Included (numbered) + Not Included (numbered) | Yes |
| 4 | Client Responsibilities | What the client provides (numbered, plain language) | Yes |
| 5 | Timeline | Phases table + total estimate | Yes |
| 6 | Cost | Pricing table + payment terms | Yes |
| 7 | Post-Project Support | Warranty period + ongoing support terms | Yes |
| 8 | Feasibility | Technical go/no-go gate with exit clause | Conditional |
| 9 | Protected Health Information | PHI safeguards, BAA provision | Conditional |
| 10 | Intellectual Property | Consultant-owns + client perpetual license + plain-language summary | Yes |
| 11 | Limitation of Liability | Capped liability with negligence/PHI carve-out | Yes |
| 12 | Acceptance | Signature blocks | Yes |

---

## Template

```markdown
---
title: "Services Agreement"
subtitle: "[Project Name]"
author:
  - name: Randall Mitchell
    affiliation: Carbon Works
date: [Month Year]
client: "[Client Legal Name]"
confidential: true
---

# Services Agreement
## Carbon Works LLC — [Client Legal Name]

**Date**: [Full Date]

**Project**: [Project Name]

## 1. Situation

[Client Legal Name] ("Client") [describe the client's current situation and the problem
this engagement addresses, in their language — 2-3 sentences].

## 2. Objectives

Provide the Client with [a tool / a report / a system] that:

1. [Primary objective]
2. [Secondary objective]
3. [Tertiary objective, if applicable]

## 3. Scope of Work

### Included

1. [Deliverable or activity]
2. [Deliverable or activity]
3. [...]
4. [Standard: Full source code delivery — if software]
5. [Standard: Technical specification document]
6. [Standard: Written instructions for use and maintenance]
7. [Standard: 90-day post-delivery warranty]

### Not Included

1. [Explicit exclusion]
2. [Explicit exclusion]
3. Ongoing maintenance beyond the 90-day warranty period (available separately — see Section 7)

## 4. Client Responsibilities

1. [What client provides — plain language, no jargon]
2. [What client provides]
3. Designate a point of contact for questions during development
4. Provide feedback on [deliverable format] before final delivery

## 5. Timeline

Work begins upon [trigger event] and continues during business days.

| Phase | Duration | Activity |
|-------|----------|----------|
| 1. [Phase name] | [Duration] | [Activity description] |
| 2. [Phase name] | [Duration] | [Activity description] |
| 3. [Phase name] | [Duration] | [Activity description] |
| 4. Delivery | [Day/Week] | Deliver final [deliverables list] |

Estimated total: **[N weeks]** from [trigger event].

## 6. Cost

**Project fee: $[amount]** [If discounted: "(first-engagement rate; standard rate: $[full amount])"]

**Payment terms**: Due upon delivery and acceptance of all contractual obligations.

## 7. Post-Project Support

This engagement includes a **90-day warranty** from the date of delivery, covering:

1. Bug fixes for issues discovered during normal use
2. Minor adjustments needed due to real-world usage patterns
3. Updates required due to external changes (e.g., [example relevant to this project])

[If applicable: For warranty or post-warranty support involving changes to [format/system/dependency], Client agrees to provide [what's needed]. Consultant cannot diagnose or resolve [type] issues without [what's needed].]

After the warranty period, Carbon Works is available for ongoing support, updates, and modifications at Carbon Works' then-current hourly consulting rate, consistent with prevailing rates for comparable technology consulting services, billed in 30-minute increments. The delivered source code and technical specification are designed to allow any qualified technical person to maintain and modify the tool independently.

## 8. Feasibility

[Include this section when the project depends on external factors that could make it infeasible — e.g., file formats, API access, data quality.]

This project depends on [condition]. During the [Phase name] phase, Consultant will evaluate [what] and confirm whether [deliverable] is feasible. If [condition] is determined to be [infeasible state]:

1. Consultant will notify the Client with a written explanation of the technical limitation
2. Either party may terminate this agreement with no fees owed
3. If partial [deliverable] is feasible, the parties may mutually agree to a revised scope

## 9. Protected Health Information

[Include this section when the engagement involves PHI or healthcare client data.]

The Client's [materials] may contain protected health information ("PHI") as defined under the Health Insurance Portability and Accountability Act of 1996 ("HIPAA"). To the extent that Consultant receives, accesses, or processes PHI in the course of performing the services described in this agreement, the following provisions apply:

**Purpose limitation.** Consultant will use PHI provided by Client solely for the purpose of developing and testing the [deliverable] described in this agreement. PHI will not be used for any other purpose.

**Safeguards.** Consultant will store PHI on encrypted, password-protected devices. PHI will not be uploaded to cloud services, shared with third parties, or transmitted over unencrypted channels. Consultant will not retain copies of PHI beyond the completion of this engagement.

**Return or destruction.** Upon project completion or termination, Consultant will permanently delete all copies of Client-provided PHI and confirm deletion in writing.

**Breach notification.** In the event of unauthorized access to or disclosure of PHI, Consultant will notify Client within 72 hours of becoming aware of the breach.

**Business Associate Agreement.** If Client determines that a formal HIPAA Business Associate Agreement ("BAA") is required for this engagement, the parties agree to negotiate and execute a BAA as an addendum to this agreement prior to the transfer of any PHI.

## 10. Intellectual Property

In plain terms: Carbon Works owns the code, but Client can use, modify, and build on it however they want, forever. Carbon Works can reuse the general approach for other clients but will never share Client's data or business information.

**Consultant Work Product.** Carbon Works LLC retains ownership of all tools, source code, documentation, methodologies, and other work product created in the performance of this engagement. Client is hereby granted a perpetual, irrevocable, worldwide, royalty-free, non-exclusive license to use, copy, modify, and create derivative works from the work product for any purpose, without limitation.

**Consultant's Right to Reuse.** Carbon Works may reuse the work product, including any code, techniques, and general methodologies, for other clients or purposes without limitation, provided that Client's confidential information is not disclosed.

**Client Materials.** Client retains all right, title, and interest in any data, documents, or other materials provided to Carbon Works for use in this engagement. Carbon Works will use Client materials solely for the purpose of performing the services described in this agreement.

## 11. Limitation of Liability

Consultant's total liability under this agreement shall not exceed the total fees paid by Client. Neither party shall be liable for indirect, incidental, consequential, or special damages arising out of or related to this agreement, including but not limited to lost profits, lost data, or business interruption. These limitations shall not apply to damages arising from Consultant's negligence, willful misconduct, or breach of the confidentiality and data protection obligations in [Section N — update to match PHI section, or remove PHI reference if no PHI section].

## 12. Acceptance

Both parties agree to the terms described above. This document serves as the full agreement for this engagement.

**Carbon Works LLC**

Signature: ________________________________________

Name: Randall Mitchell

Date: ________________________________________

&nbsp;

**[Client Legal Name]**

Signature: ________________________________________

Name: ________________________________________

Date: ________________________________________
```

---

## Notes

- **PHI section**: Only include for healthcare or any engagement where client data could contain personal health information. If omitted, update the liability carve-out to remove the PHI cross-reference.
- **Feasibility section**: Include when the project depends on external factors outside your control (file formats, API availability, data quality). Omit for straightforward deliverables.
- **Discount line in Cost**: Only include when offering a reduced rate. Remove the "Standard project rate" row if billing at full rate.
- **Pricing table clarity**: When showing a discounted rate alongside a standard rate, the table can visually read as two line items that sum (e.g., $1,000 + $500 = $1,500) rather than a discount. Use strikethrough on the standard rate (`~~$1,000~~`) or replace the table with a single line: "**Project fee: $500** (first-engagement rate; standard rate: $1,000)". Caught on S&C Phase 1 — Tyler had already agreed so it wasn't changed, but fix for future contracts.
- **IP plain-language summary**: Always include. Tyler's feedback confirmed this is valuable — non-technical clients need to understand what "perpetual, irrevocable, non-exclusive license" means in plain English.

---

*Created: 2026-02-13 from S&C Phase 1 contract experience*
