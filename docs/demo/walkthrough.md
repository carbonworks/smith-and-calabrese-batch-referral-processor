# App Walkthrough: Batch Authorization Processor

**Format**: Left-panel reference document -- keep this open alongside the live app
**Duration**: 5-7 minutes
**Presenter**: Randall Mitchell, Carbon Works LLC
**Audience**: Tyler Calabrese (commissioner) and Smith & Calabrese office staff (daily users)

---

## Before You Start

Have these ready:

- The app running (launch via the installed .msi or `./gradlew :app:run`)
- 3-5 sample authorization PDFs in a folder (use sanitized/test files for a demo, real files for a live walkthrough)
- This document open on the left half of your screen, app on the right half

---

## 1. Opening: What This Tool Does

**Time**: ~30 seconds
**On screen**: App launches to the file selection screen -- "Batch Authorization Processor" header is visible.

**Talking points**:

- This is the Batch Authorization Processor, built specifically for Smith & Calabrese to handle Maryland DDS service authorization PDFs.
- The problem it solves: right now, someone on your team manually opens each authorization PDF, reads the fields, and types them into a tracking spreadsheet. That is slow and error-prone, especially with a stack of 20-30 referrals.
- This tool reads the PDFs for you. You select your files, it extracts the data, and you get a spreadsheet -- same format you already work with.
- Everything runs locally on this computer. No data is sent to the internet. No cloud services involved.

**Pause point**: "Before I show you how it works -- any questions about what this does or doesn't do?"

---

## 2. First Task: Process a Batch of Referrals

**Time**: ~2 minutes
**On screen**: File selection screen

### 2a. Select Files

**Action**: Drag 3-5 PDF files from Explorer onto the app window.

**Point at on screen**:

- The drop zone highlights blue when files are dragged over it -- that means the app recognizes what you are dropping.
- Files appear in the "Selected Files" list below with filenames and sizes.
- The file count updates in the section header ("Selected Files (5)").

**Talking points**:

- You can also click anywhere in the drop zone or use the "Add Files" button at the bottom to open a standard file picker.
- The file picker is filtered to PDFs only and remembers the last folder you used.
- Maximum batch size is 50 PDFs at once.
- Duplicates are automatically ignored -- if you accidentally drop the same file twice, it will not be added again.

**Action**: Click the X next to one file to show removal works, then re-add it via "Add Files".

**Point at on screen**:

- The "Clear All" button in the section header removes everything if you need to start over.
- The "Process N Files" button at the bottom right shows the exact count and is disabled when no files are selected.

### 2b. Process the Batch

**Action**: Click "Process 5 Files" (or whatever your count is).

**Point at on screen**:

- The screen transitions to "Processing Referrals" with a progress bar and per-file status list.
- Each file shows a status icon: a spinner while it is being processed, a green checkmark when it succeeds, or a red X if something goes wrong.
- The progress counter updates in real time (1/5, 2/5, and so on).

**Talking points**:

- Each file goes through a three-stage pipeline: text extraction, table extraction, and field parsing.
- If one file has a problem -- say it is corrupted or is not actually a DDS authorization -- the app skips it and moves on. One bad file does not stop the whole batch.
- Processing takes roughly 1-3 seconds per file depending on complexity. A batch of 20 files takes under a minute.

**Point at on screen**: When the last file finishes, the app automatically moves to the Results screen. Watch for the smooth crossfade transition.

---

## 3. Review the Extracted Data

**Time**: ~1.5 minutes
**On screen**: Results screen

### 3a. Summary at a Glance

**Point at on screen**: The four summary cards at the top of the screen.

- **Processed**: Total number of files.
- **Successful**: How many were fully extracted (green number).
- **Warnings**: Fields that could not be found or looked unusual (orange number). Warnings do not mean failure -- they are flags for manual review.
- **Failed**: Files that could not be read at all (red number). This is rare and usually means the file is not a DDS authorization.

**Talking points**:

- A typical batch should show all or nearly all files as successful.
- If you see warnings, that is normal -- some authorizations have optional fields that are not always present. The tool flags what it cannot find so you can fill it in manually if needed.

### 3b. Referral Cards

**Point at on screen**: Scroll down to the Data Preview section -- each referral is displayed as its own card.

**Talking points**:

- Each card shows the source filename at the top, so you always know which PDF the data came from.
- Notice the data is masked by default -- all the field values are replaced with dots. This is a privacy feature to protect patient information when someone might be looking over your shoulder.

**Action**: Click the eye toggle icon in the top right of the screen (next to the "?" help icon).

**Point at on screen**:

- All field values across all cards are now visible.
- The icon changes from a crossed-out eye to an open eye.

**Talking points**:

- Click the eye toggle again to re-mask everything.
- You can also toggle individual cards: each card has its own small eye icon in the card header.
- Field values are selectable text. Triple-click any value to select it for copying.

### 3c. What Each Card Shows

**Point at on screen**: Walk through one card from top to bottom.

- **Header**: Source PDF filename, per-card mask toggle, and "Open PDF" link (opens the original PDF in your viewer).
- **Missing fields warning** (orange banner): If the card has one, it lists exactly which fields could not be found. For example: "Missing fields: middleName, phone". These are the fields you may need to fill in manually in the spreadsheet.
- **Left column**: Patient metadata -- Claimant name, DOB, Case ID, Authorization number, Request ID, Date of Issue, Applicant, Appointment date/time, Address, Phone.
- **Right column**: Services Authorized -- CPT codes with descriptions and fees.
- **Footer**: Invoice/case fields -- Federal Tax ID, Vendor Number, Case Number, Assigned Code, DCC Number.

### 3d. Errors and Warnings (if present)

**Point at on screen**: If any files failed, an expandable "Errors" section appears below the summary cards. Click "Expand" to show which files failed and why.

**Action**: If warnings exist, click "Expand" on the "Warnings" section.

**Point at on screen**: Warnings are grouped by file, each with a stage tag (like `[parse]` or `[completeness]`) and a description.

**Talking points**:

- Errors mean the file could not be read. Common causes: the file is not a real DDS authorization, the PDF is corrupted, or it is a scanned image without text (rare).
- Warnings mean the file was read but some fields were empty or unusual. This is informational -- the file still produced a row in the spreadsheet.

**Pause point**: "Let me stop here -- does the data look right compared to what you would see if you opened the PDF manually? Any questions about how the results are displayed?"

---

## 4. Export to Spreadsheet

**Time**: ~1 minute
**On screen**: Results screen, bottom action bar

### 4a. Save the Spreadsheet

**Action**: Click the "Export" button (bottom right, green).

**Point at on screen**:

- A standard Save dialog appears with a pre-filled filename: `patient-referrals-YYYY-MM-DD-HHmmss.xlsx`. The date and time are stamped automatically.
- The dialog remembers the last folder you saved to.

**Action**: Click Save.

**Point at on screen**:

- An orange filename link and "Open folder" link appear above the action buttons.
- Click the filename to open the spreadsheet directly.
- Click "Open folder" to open the containing folder in Explorer.

### 4b. What the Spreadsheet Looks Like

**Action**: Click the filename link to open the XLSX.

**Talking points**:

- Row 1 is a bold, frozen header row -- it stays visible as you scroll.
- One row per authorization PDF.
- Date columns (Date of Issue, DOB, Appointment Date) are stored as proper Excel dates, not text. They sort and filter correctly.
- The format is Google Sheets compatible. You can open it directly in Sheets or import it -- no Excel-specific features to break.
- The output file is never masked -- it always contains the actual extracted values regardless of the masking toggle in the app.

### 4c. Export Settings

**Action**: Close the spreadsheet. Back in the app, click the gear icon to the left of the Export button (or navigate via Settings).

**Point at on screen**: The Export Settings screen.

**Talking points**:

- You can choose which columns appear in the spreadsheet and their order.
- Drag-and-drop or use the arrow buttons to reorder columns.
- Uncheck columns you do not need.
- The "Essential Only" preset gives you just the core fields: First Name, Last Name, DOB, Case ID, Authorization #, Appointment Date, Appointment Time, and Services.
- The "All Fields" preset restores everything.
- The "Place each service on its own row" checkbox splits multi-service referrals into separate rows -- useful if you want one row per CPT code instead of a comma-separated list.
- The "Reset" button restores factory defaults (requires typing "reset" to confirm, so you will not hit it by accident).
- These settings are saved automatically and persist between sessions.

**Action**: Click "Back" to return.

---

## 5. Support and Troubleshooting

**Time**: ~30 seconds
**On screen**: File selection screen

**Action**: Click the "?" help icon (top right).

**Point at on screen**: The Help & Support screen with four sections:

1. **Getting Started**: Quick 4-step summary of the workflow (select, process, review, export).
2. **Supported Formats**: Maryland DDS service authorization PDFs, up to 50 per batch, XLSX output.
3. **Tips**: Helpful shortcuts -- the file picker remembers your last folder, drag-and-drop is available, triple-click to select a value.
4. **Support**: Contact email (support@carbonworks.tech) and a "Save Log File" button.

**Talking points**:

- If something goes wrong and you need to report an issue, click "Save Log File". It creates a text file with the application log. Personal health information in the log is automatically masked.
- Attach that file to an email to support@carbonworks.tech and describe what happened. Clicking the email address opens your mail client with the address pre-filled.

**Action**: Click "Back" to return.

---

## 6. Settings

**Time**: ~30 seconds
**On screen**: File selection screen

**Action**: Click the gear icon (top right, next to the help icon).

**Point at on screen**: The Settings screen.

**Talking points**:

- **Privacy toggle**: "Show extracted data by default" controls whether the Results screen starts with data masked or unmasked. Off by default (masked). If your office environment is private and you always want to see the data immediately, turn it on.
- **Export row**: Takes you to the Export Settings screen we already looked at -- column selection and ordering.
- Settings are saved automatically and persist between sessions and app restarts.

**Action**: Click "Back" to return.

---

## 7. Wrap-Up: What You Got

**Time**: ~30 seconds

**Talking points**:

- To recap what has been delivered:
  - A batch processing tool that reads your DDS authorization PDFs and produces a structured spreadsheet. One click to process, one click to export.
  - Runs on Windows. Installed via a standard .msi installer -- no Java setup, no command line, no configuration needed.
  - Full source code is included with the delivery, along with a technical specification and this documentation.
  - Privacy protection is built in: data is masked by default on screen, and the log files mask personal information automatically.
- This tool comes with a **90-day warranty** from the date of delivery. That covers bug fixes, adjustments based on real-world usage, and updates if the DDS authorization format changes (with a sample PDF provided).
- After the warranty period, Carbon Works is available for ongoing support at standard consulting rates.

**Pause point**: "That is the full walkthrough. What questions do you have?"

---

## Quick Reference Card

For office staff to keep handy after the demo.

| Task | How |
|------|-----|
| **Process referrals** | Drag PDFs onto the app (or click "Add Files") then click "Process" |
| **See masked data** | Click the eye icon (top-right on Results screen) |
| **Export spreadsheet** | Click "Export" on the Results screen, choose where to save |
| **Change export columns** | Settings (gear icon) then Export, or gear icon on Results screen |
| **Start a new batch** | Click "Start Over" on the Results screen |
| **Get help** | Click the "?" icon, or email support@carbonworks.tech |
| **Report a problem** | Help screen then "Save Log File", attach to email |

---

## Contract Deliverables Checklist

For the presenter to reference internally. Do not show this section on screen -- it is for your own confidence that every contractual item has been demonstrated.

| # | Deliverable (Contract Section 3) | Demonstrated In |
|---|----------------------------------|-----------------|
| 1 | Batch PDF processing tool for Windows | Sections 2-3: file selection, drag-and-drop, batch processing |
| 2 | XLSX output (`patient-referrals-[date]-[time].xlsx`) | Section 4a: export with timestamped filename |
| 3 | Formatted for Google Sheets migration | Section 4b: no Excel-only features, proper date types |
| 4 | Full source code delivery | Section 7: mentioned in wrap-up |
| 5 | Technical specification document | Section 7: mentioned in wrap-up |
| 6 | Written user guide | Section 7: mentioned in wrap-up (this document + Help screen) |
| 7 | 90-day post-delivery warranty | Section 7: stated in wrap-up |

---

*Carbon Works LLC -- Making sense of technology.*
