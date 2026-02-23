---
type: reference
status: active
area: brand
created: 2026-02-02
updated: 2026-02-02
---

# Brand Rules

Granular, actionable brand rules discovered through the [Brand Analysis skill](/system/skills/brand-analysis.md). These extend the high-level guidelines in `/org/brand/carbon-works-brand-guidelines.md` with specific, testable rules.

This file is maintained by the [self-improving validation protocol](/system/skills/protocols/validation-protocol.md). New rules are proposed when the Brand Analysis skill encounters situations not covered by existing guidance, and adopted on user approval.

**Source of truth hierarchy:**
1. `/org/brand/carbon-works-brand-guidelines.md` — high-level brand identity and direction
2. This file — granular, testable rules derived from brand guidelines + real-world application
3. `/reference/strategy/business-values.md` — operating principles that inform edge-case decisions

---

## Prohibited Language

Extends the "What We Say / What We Don't Say" table in brand guidelines. These are specific terms and phrases that should never appear in CW content.

| Prohibited | Replacement | Rationale | Discovered |
|------------|-------------|-----------|------------|
| "Leverage synergies" | "Work well together" | Corporate jargon; violates "neighbor" voice | Brand guidelines (original) |
| "Digital transformation" | "Getting your technology sorted out" | Buzzword; our audience finds this alienating | Brand guidelines (original) |
| "Scalable solutions" | "Solutions that grow with you" | Vague tech-speak; no concrete meaning for SMB owners | Brand guidelines (original) |
| "Circle back" | "Follow up" | Corporate euphemism; say what you mean | Brand guidelines (original) |
| "Pain points" | "Problems" or "frustrations" | Sales jargon; sounds like we're diagnosing, not helping | Brand guidelines (original) |
| "Best-in-class" | Describe what makes it good specifically | Empty superlative; provides no information | Brand guidelines (original) |

*New entries are added below this line as the Brand Analysis skill discovers them.*

| "Investment" (as pricing label) | "Cost", "Price", or "Price Range" | Sales psychology framing; CW differentiates through honesty, not persuasion. Industry consulting convention uses "Investment" — CW intentionally departs from this to stay true to brand voice. | 2026-02-02 brand analysis |
| "Exclusively" (in offers/pricing) | State the eligibility simply: "for", "applies to" | Scarcity/urgency tactic; implies artificial limitation. CW offers discounts because they make sense, not to create FOMO. | 2026-02-03 Tyler service offerings review |

---

## Voice & Tone Calibrations

Specific guidance for tone edge cases encountered in real content reviews.

| Situation | Guidance | Rationale | Discovered |
|-----------|----------|-----------|------------|
| Describing a technical concept | Lead with the benefit/outcome, then explain the mechanism if needed | Audience wants to know "what does this mean for me" before "how it works" | Brand guidelines: "accessible" value |
| Comparing CW to competitors | State CW's strengths; never disparage others | "Quietly confident" personality — competence speaks for itself | Brand guidelines: personality traits |
| Discussing pricing | Be direct about cost; don't hide behind "investment" or "value" language | Honesty value; audience has been burned by hidden costs | Brand guidelines: honest voice |
| Writing for a technical audience (rare) | Still avoid jargon; technical people appreciate clarity too | Brand consistency; the "neighbor" voice works for everyone | Brand guidelines: "never talk down" |

*New entries are added below this line as edge cases are discovered.*

| Using industry-standard acronyms | Spell out on first use with acronym in parentheses. E.g., "Electronic Health Records (EHR)". Limit to 2 acronyms per page where possible. | Undefined acronyms create exactly the barrier CW pledges to remove. Audience is non-technical small business owners. | 2026-02-02 brand analysis; plainlanguage.gov Federal Plain Language Guidelines |
| AI-assisted research deliverables | Include a brief methodology note: "Research compiled with AI-assisted analysis and verified against publicly available sources." Place near end of document, before CW closing. | Makes AI transparency concrete and actionable for client deliverables. Strengthens credibility rather than weakening it. | 2026-02-02 brand analysis; Princeton/Wharton AI disclosure standards |
| Consulting jargon in client emails | Avoid consulting terminology in client-facing content: "solution space", "engagement", "deliverables", "stakeholders", "scope", "alignment." Use plain equivalents: "options", "project", "what we'll deliver", "your team", "what's included", "on the same page." | These terms are natural internally but leak into client content. CW's voice is "neighbor who's good with tech" — a neighbor wouldn't say "narrow down the solution space." | 2026-02-10 S&C status email brand analysis |
| Acronyms for industry-fluent contacts | When writing to professionals in their field, industry-standard acronyms in *their* domain may be used without expansion (e.g., "EHR" to a PsyD). Still expand CW-side or cross-domain acronyms (e.g., "RPA", "API"). | Spelling out terms a professional uses daily feels patronizing — violates "never makes anyone feel stupid." The base acronym rule targets public content for non-technical audiences. | 2026-02-10 S&C status email brand analysis |
| Legal/contract sections | Add a plain-language summary before formal legal language in sections a non-technical client needs to understand (especially IP, liability). Use "In plain terms:" prefix. Formal language follows for enforceability. | Contracts are the highest-trust moment in a client relationship. Hiding behind legalese contradicts CW's "honest, accessible" identity. Both the plain summary and formal language are needed — one for trust, one for enforceability. | 2026-02-13 S&C contract self-review |
| Liability clauses | Always include a negligence/willful misconduct carve-out. CW stands behind its work — if we cause damage through carelessness, we're accountable. Standard boilerplate that shields against consultant negligence is counter to CW brand. | A blanket "trust me at your own risk" clause contradicts every CW value. The carve-out says: "I'm reasonable about risk, but I own my mistakes." | 2026-02-13 S&C contract self-review |
| Contract terminology | "Engagement" is acceptable in contracts (standard legal/contract term). The consulting jargon rule targets casual client communication, not formal agreements where precision matters. | Contracts need gravitas — using "project" everywhere instead of "engagement" undermines the document's seriousness. Context-appropriate formality. | 2026-02-13 S&C contract self-review |

---

## Messaging Patterns

Codified patterns for how CW communicates specific types of information.

| Pattern | Structure | Example | Discovered |
|---------|-----------|---------|------------|
| Service description | Problem → CW approach → Outcome | "Most small businesses struggle with [X]. We [approach]. You get [outcome]." | Brand guidelines: messaging framework |
| Value proposition | Concrete benefit, no superlatives | "Clear answers from someone who speaks your language" — not "best technology consulting" | Brand guidelines: key messages |
| Call to action | Direct, low-pressure, specific | "Let's talk about your [specific thing]" — not "Schedule a consultation today!" | Brand guidelines: "no aggressive popups" + "genuinely helpful" |

*New entries are added below this line as messaging patterns are codified.*

---

## Values Application Rules

Specific rules for applying CW values in content decisions.

| Value | Rule | Example Application | Discovered |
|-------|------|---------------------|------------|
| Credibility Is the Product | Never claim expertise in areas where CW has no track record | If CW hasn't done e-commerce consulting, don't list it as a service | Business values |
| Customer Needs Over Growth | Always recommend the simplest adequate solution first | If a client needs a basic website, recommend Squarespace before custom development | Business values |
| Honesty | Disclose limitations and trade-offs in every recommendation | "This approach works well for X but won't help with Y" | Business values |
| AI Transparency | When AI contributed to content or deliverables, acknowledge it | "This analysis was prepared with AI assistance" in methodology sections | Business values |

*New entries are added below this line as values application rules are discovered.*

---

## Professional Floor (Context-Calibrated)

Minimum professionalism requirements by content type. The brand is approachable — but never unprofessional.

| Content Type | Contractions | Casual Idioms | Minimizers ("just", "simply") | Sentence Fragments |
|--------------|--------------|---------------|-------------------------------|-------------------|
| Client deliverable (report, proposal) | OK sparingly | Avoid | Avoid | Avoid |
| Service descriptions (web, PDF) | OK | Avoid | Avoid | OK for emphasis |
| Email to prospect | OK | OK sparingly | OK | OK |
| Email to friend/warm contact | OK freely | OK | OK | OK |
| Blog/thought leadership | OK | OK sparingly | Limit to 1-2 per piece | OK for emphasis |

**Casual idioms to watch**: "sorted out", "leaving X on the table", "get your ducks in a row", "at the end of the day", "bang for your buck"

**Minimizers to watch**: "just", "simply", "only", "really" (when used to soften rather than emphasize)

**The test**: Would a business owner trust this to send to their accountant or loan officer? If it reads too much like a text message, dial it back.

---

## Changelog

| Date | Change | Source |
|------|--------|--------|
| 2026-02-02 | Initial population — extracted existing rules from brand guidelines and business values | Brand rules file creation |
| 2026-02-02 | Added: pricing terminology rule (prohibited "Investment" as pricing label), acronym handling rule, AI transparency in deliverables rule | Brand analysis of Tyler project deliverables |
| 2026-02-03 | Added: Professional Floor section with context-calibrated professionalism requirements | Professional writing quality review |
| 2026-02-03 | Added: "Exclusively" to prohibited language (scarcity/urgency sales tactic) | Tyler service offerings review |
| 2026-02-10 | Added: consulting jargon avoidance in client emails, acronym exception for industry-fluent contacts | S&C status email brand analysis |
| 2026-02-13 | Added: plain-language summary rule for legal sections, liability negligence carve-out rule, contract terminology exception | S&C contract self-review |
