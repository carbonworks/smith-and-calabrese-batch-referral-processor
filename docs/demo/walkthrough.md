# Batch Authorization Processor

### From referral PDFs to organized spreadsheets — quickly and accurately.

Built for Smith & Calabrese Assessments by Carbon Works LLC.

---

## What It Does

The Batch Authorization Processor reads Maryland DDS service authorization PDFs and extracts the data you need into a clean, organized spreadsheet. Select your files, review the results, and export — no manual data entry required.

Everything runs locally on your computer. No data leaves your machine. No internet connection needed.

---

## Getting Started

### 1. Select Your Files

Drag authorization PDFs from a folder directly onto the app window, or click **Add Files** to browse. The app accepts up to 50 PDFs at a time and automatically skips duplicates.

Files appear in the **Selected Files** list with their names and sizes. Remove any file with the **X** button, or use **Clear All** to start fresh.

When your batch is ready, click **Process**.

### 2. Watch It Work

The processing screen shows real-time progress for each file. A green checkmark means the file was read successfully. A red X means something went wrong — but one problem file won't stop the rest of the batch.

Most files process in 1-3 seconds. A batch of 20 takes under a minute.

### 3. Review the Results

The results screen opens automatically when processing is complete.

**Summary cards** at the top give you the big picture: how many files were processed, how many succeeded, and whether any had warnings or errors.

Below the summary, each referral gets its own **data card** showing the extracted fields — claimant information, appointment details, services authorized, and case identifiers. Every field is pulled directly from the PDF.

**Missing field warnings** appear as orange banners on individual cards, listing exactly which fields couldn't be found. These are flags for manual review, not failures — the rest of the data is still extracted and included in the spreadsheet.

### 4. Privacy at a Glance

Extracted data is **masked by default**. Field values appear as dots until you choose to reveal them. This protects patient information when others may be nearby.

Toggle visibility for all cards at once with the **eye icon** in the top-right corner of the results screen. Or unmask individual cards using the small eye icon on each card header.

The privacy default can be changed in **Settings** if your workspace is private and you prefer data to be visible immediately.

### 5. Export Your Spreadsheet

Click **Export** to save the extracted data as an Excel spreadsheet. The filename is automatically timestamped (`authorizations-2026-03-05-143022.xlsx`) and the save dialog remembers your last folder.

After saving, a link appears to open the file directly or to open the containing folder.

**What the spreadsheet includes:**

- One row per authorization PDF
- A bold, frozen header row that stays visible as you scroll
- Date fields stored as proper Excel dates (not text) — they sort and filter correctly
- Full compatibility with Google Sheets

The spreadsheet always contains the actual extracted values, regardless of the masking toggle in the app.

---

## Customizing Your Export

Click the **gear icon** next to the Export button (or go through **Settings**) to open **Export Settings**.

From here you can:

- **Choose which columns** appear in the spreadsheet and **drag to reorder** them
- Use the **Essential Only** preset for just the core fields: name, DOB, case ID, authorization number, appointment, and services
- Use **All Fields** to restore the complete column set
- Toggle **Place each service on its own row** if you prefer one row per CPT code instead of a comma-separated list

Your export configuration is saved automatically and persists between sessions.

---

## Getting Help

Click the **?** icon in the top-right corner to open the Help & Support screen.

If you run into a problem, click **Save Log File** to create a copy of the application log. Personal health information in the log is automatically masked. Attach the saved file to an email to **support@carbonworks.tech** with a description of what happened.

---

## What's Included

| Deliverable | Description |
|---|---|
| **Batch processing tool** | Windows desktop application — installed via a standard .msi installer, no setup required |
| **Spreadsheet output** | Timestamped .xlsx files formatted for direct use or Google Sheets import |
| **Source code** | Full source code delivered with the project |
| **Technical specification** | Architecture documentation and field mapping reference |
| **User guide** | This document and the in-app Help screen |
| **90-day warranty** | Bug fixes, real-world adjustments, and format change support (with a sample PDF provided) |

After the warranty period, Carbon Works is available for ongoing support at standard consulting rates.

---

## Quick Reference

| Task | How |
|---|---|
| Process referrals | Drag PDFs onto the app, then click **Process** |
| Unmask data | Click the eye icon on the results screen |
| Export spreadsheet | Click **Export**, choose where to save |
| Change export columns | Gear icon on the results screen, or Settings > Export |
| Start a new batch | Click **Start Over** on the results screen |
| Get help | Click **?**, or email support@carbonworks.tech |
| Report an issue | Help screen > **Save Log File**, attach to email |
