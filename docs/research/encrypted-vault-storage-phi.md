---
type: knowledge
status: active
created: 2026-02-16
updated: 2026-02-16
---

# Encrypted Vault-Style Storage for PHI

Research into vault-style encrypted storage solutions suitable for a solopreneur technology consultant who needs to securely receive and store files containing PHI (Protected Health Information) from clients.

---

## Requirements

1. Consultant OWNS the vault/storage (not the client)
2. Clients can securely upload files without needing their own paid account
3. Visibly secure experience for security-conscious clients (psychologists handling PHI)
4. HIPAA compliant or at minimum zero-knowledge encrypted; BAA available
5. Cost effective for solopreneur (ideally under $10/mo)
6. Vault-style encrypted storage with controlled access

---

## Comparison Table

| Criterion | Keeper (Personal + File Storage) | Tresorit (Personal Pro) | Sync.com (Solo Unlimited) | Proton Drive (Plus/Unlimited) | Encyro (Pro) |
|---|---|---|---|---|---|
| **Monthly cost** | ~$2.83/mo ($34/yr) | $27.49/mo ($330/yr) | $90/mo ($1,080/yr) | $4.99-12.99/mo | $9.99/mo |
| **Client upload without account** | Yes (one-time share links) | Yes (file requests) | Yes (upload-enabled links) | Partial (shared folder, not upload-only) | Yes (branded upload page) |
| **HIPAA/BAA** | Claims BAA not needed (zero-knowledge); no formal BAA on Personal | Yes, BAA available | Yes, BAA on this plan | Yes, BAA available | Yes, BAA standard |
| **Zero-knowledge/E2EE** | Yes (AES-256, zero-knowledge) | Yes (AES-256, E2EE) | Yes (E2EE all plans) | Yes (E2EE, Swiss jurisdiction) | No (server-side encryption) |
| **Client experience** | Functional but password-manager UX | Clean, professional | Clean folder-based upload | Poor (no upload-only mode) | Excellent (branded portal) |
| **Under $10/mo?** | YES (~$2.83/mo) | NO ($27.49/mo) | NO ($90/mo) | YES ($4.99-12.99/mo) | YES ($9.99/mo) |
| **Designed for this use case?** | No (password manager first) | Partially (file storage + requests) | Partially (cloud storage + sharing) | No (missing file requests) | YES (built for professional file exchange) |

**Disqualified**: SpiderOak (no upload capability), Boxcryptor (discontinued), Cryptomator (local tool, no sharing).

---

## Product Details

### Keeper Security (Personal + File Storage add-on)

**Bidirectional One-Time Share** (introduced May 2025): Create a record, generate a time-limited link with "Allow recipient to edit record fields and upload files," send it to client. Recipient clicks link, uploads files in browser — no Keeper account needed. Files sync to vault encrypted with AES-256. Link is device-locked and expires after set time.

- Personal plan: ~$2/mo ($24/yr) + Secure File Storage add-on: $9.99/yr for 10GB
- Total: ~$2.83/mo
- Zero-knowledge architecture; claims BAA not required (legally debatable)
- UX is functional but clearly a password manager, not a file portal
- One-time links expire — need a new link for each file transfer session

### Encyro (Pro)

Purpose-built for professional services firms receiving sensitive documents. Branded upload page with your logo where clients visit a URL, attach files, and submit.

- Pro plan: $9.99/mo (unlimited clients, unlimited storage for shared files)
- HIPAA compliant with BAA as standard feature
- **Not** zero-knowledge (server-side encryption, not client-side E2EE)
- Best client UX of all options — mobile-friendly, no accounts/installs needed
- Also includes encrypted email, e-signatures, invoicing

### Tresorit (Personal Pro)

Premium E2EE file storage with File Requests feature. Clean, professional client experience.

- Personal Pro: $27.49/mo ($330/yr)
- BAA available; AES-256 E2EE
- File requests: no account needed for uploaders (5GB per session browser limit)
- Strong security brand recognition
- File requests NOT available on cheaper Essential plan ($11.99/mo)

### Proton Drive

Strong E2EE and Swiss jurisdiction, but **no file request/upload-only feature** as of Feb 2026.

- Drive Plus: $4.99/mo; Unlimited: $12.99/mo
- BAA available; E2EE
- Shared folders expose all contents to editors (no upload-only mode)
- Per-client folders workaround is manageable but not elegant

### Sync.com

Upload-enabled links work well, but HIPAA compliance requires expensive Solo Unlimited plan.

- Solo Unlimited (HIPAA): $90/mo
- Teams Standard (HIPAA): $6/user/mo, 3-user minimum ($18/mo)
- E2EE on all plans; BAA only on HIPAA tiers

---

## Recommendation

1. **Encyro Pro** ($9.99/mo) — Best overall for client-facing PHI uploads. Purpose-built, HIPAA+BAA, branded portal.
2. **Keeper Personal + File Storage** (~$2.83/mo) — Best budget option with zero-knowledge. Functional but password-manager UX.
3. **Tresorit Personal Pro** ($27.49/mo) — Best security optics. Premium brand, E2EE, BAA. Over budget.

---

## Applied In

- [Smith & Calabrese Consulting](/work/projects/smith-calabrese-consulting/) — secure PHI file transfer from client

---

## Sources

Research conducted 2026-02-16. Key sources: Keeper press releases (May 2025), Tresorit documentation, Sync.com help articles, Encyro website and Capterra reviews, Proton Drive legal/BAA page, academic and trade reviews.

---

*Last updated: 2026-02-16*
