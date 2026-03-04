# Manual End-to-End Test Script

**Target duration**: Under 3 minutes (full suite), under 1 minute (smoke test)
**Platform**: Windows (primary), macOS (secondary)
**App**: PDF Authorization Processor (Compose Desktop)

---

## Prerequisites

### Test PDFs

Prepare the following files in a single folder before testing:

| File | Characteristics | Purpose |
|------|----------------|---------|
| `valid-multi-service.pdf` | Valid DDS authorization with 2+ service lines | Happy path, multi-service extraction |
| `valid-single-service.pdf` | Valid DDS authorization with 1 service line | Single-service extraction |
| `malformed.pdf` | Corrupted or non-authorization PDF | Error handling |

A minimum of 2 valid PDFs and 1 malformed PDF is required. All files must use sanitized/masked data only (no real PHI).

### Launch

Run the application via `./gradlew :app:run` or launch the installed .msi build. The app opens to the file selection screen ("Batch Authorization Processor").

---

## Quick-Pass Smoke Test (under 1 minute)

Run through these checkboxes in order. Stop on any failure.

- [ ] App launches to file selection screen with "Batch Authorization Processor" header
- [ ] Click drop zone or "Add Files" -- file picker opens, filtered to PDF
- [ ] Select 2+ valid PDFs -- files appear in list with names and sizes
- [ ] Click "Process N Files" -- processing screen shows progress bar advancing
- [ ] Processing completes -- auto-navigates to results screen
- [ ] Results screen shows summary cards (Processed, Successful, Warnings, Failed)
- [ ] Referral cards display with field labels and values (masked by default)
- [ ] Click eye toggle (top-right) -- field values become readable
- [ ] Click "Save to XLSX" -- orange filename link and "(Open folder)" appear inline
- [ ] Click "Start Over" -- returns to empty file selection screen
- [ ] Click gear icon (Settings) -- Settings screen renders with privacy toggle
- [ ] Click "Back" on Settings -- returns to file selection
- [ ] Click "?" icon (Help) -- Help screen renders with sections
- [ ] Click "Back" on Help -- returns to file selection

---

## Full Test Suite

### Test 1: File Selection and Input Validation (P0)

**Flow**: Main screen file operations

1. **File picker**: Click the drop zone area. Verify the system file dialog opens with a "PDF Files (*.pdf)" filter.
2. **Select files**: Choose `valid-multi-service.pdf` and `valid-single-service.pdf`. Verify both appear in the "Selected Files (2)" list with filename and size.
3. **Duplicate rejection**: Click "Add Files" and select `valid-multi-service.pdf` again. Verify the file count stays at 2 (no duplicate added, no error shown).
4. **Remove file**: Click the X button next to `valid-single-service.pdf`. Verify it is removed and the count updates to "Selected Files (1)".
5. **Re-add via drag-and-drop**: Drag `valid-single-service.pdf` and `malformed.pdf` from Explorer/Finder onto the app window. Verify both are added and the count becomes "Selected Files (3)".
6. **Clear all**: Click "Clear All". Verify the list is empty and "No files selected" is displayed.
7. **Process button state**: With no files selected, verify "Process 0 Files" button is disabled. Add one file and verify the button becomes "Process 1 File" (enabled).

### Test 2: Processing and Progress (P0)

**Flow**: Main -> Processing -> Results

1. **Setup**: From the file selection screen, add all 3 test PDFs (2 valid + 1 malformed).
2. **Start processing**: Click "Process 3 Files". Verify the screen transitions to "Processing Referrals" with a progress bar at 0/3.
3. **Progress updates**: Observe the progress bar advancing. Each file shows a status icon (spinner for active, checkmark for success, X for error). The counter increments (1/3, 2/3, 3/3).
4. **Completion**: After all files finish, verify automatic navigation to the Results screen. The progress bar should reach 3/3 before the transition.

### Test 3: Results Screen Display (P0)

**Flow**: Continues from Test 2

1. **Summary cards**: Verify four summary cards at the top: "Processed" (3), "Successful" (2), "Warnings" (count >= 0), "Failed" (1). Successful should be green, Failed should be red.
2. **Referral cards**: Verify 2 referral cards are displayed in the "Data Preview" section, one per valid PDF. Each card shows the source filename in the header.
3. **Field values present**: Each card should display labeled fields: Claimant (name), DOB, Case ID, Authorization #, Appointment date/time, Services Authorized with CPT codes.
4. **PHI masking default**: Verify all field values are masked (displayed as character-replacement patterns, not readable text).
5. **Eye toggle -- unmask**: Click the eye toggle icon (top-right, `VisibilityOff` icon). Verify all field values across all cards become readable. The icon changes to `Visibility`.
6. **Eye toggle -- re-mask**: Click the eye toggle again. Verify values are masked again and the icon returns to `VisibilityOff`.
7. **Text selection**: Triple-click on any unmasked field value. Verify the entire value is selected (blue highlight). The card body is wrapped in a `SelectionContainer`.
8. **Error summary**: Verify an "Errors (1)" expandable section is visible. Click "Expand". Verify the malformed PDF filename and an error message are shown.
9. **Warnings section** (P1): If any warnings exist, verify the "Warnings (N)" section is expandable and shows stage tags (e.g., `[parse]`) alongside messages.
10. **Open PDF link** (P1): On any referral card, click "Open PDF" in the card header. Verify the source PDF opens in the system's default PDF viewer.

### Test 4: XLSX Export (P0)

**Flow**: Continues from Test 3

1. **Save to XLSX**: Click "Save to XLSX". Verify an orange filename link appears inline to the left of the buttons (format: `patient-referrals-YYYY-MM-DD-HHmmss.xlsx`) along with "(Open folder)".
2. **Open file**: Click the orange filename link. Verify the XLSX opens in Excel or the default spreadsheet application.
3. **Verify content**: In the spreadsheet:
   - Row 1 is a bold, frozen header row.
   - Column order matches the default export config: First Name, Middle Name, Last Name, Case ID, Authorization #, Request ID, Date of Issue, DOB, Applicant, Appointment Date, Appointment Time, Street Address, City, State, ZIP, Phone, Services, Federal Tax ID, Vendor Number, Case Number (Footer), Assigned Code, DCC Number.
   - Date columns (Date of Issue, DOB, Appointment Date) contain actual Excel dates formatted as mm/dd/yyyy, not plain text strings.
   - Data rows exist for each successfully processed referral (2 rows expected).
   - Services column contains comma-separated CPT codes.
4. **Open folder**: Return to the app and click "(Open folder)". Verify Explorer/Finder opens to the folder containing the XLSX.
5. Close the spreadsheet application.

### Test 5: Export Settings Screen (P0)

**Flow**: Results -> Export Settings -> Back

1. **Navigate from Results**: On the Results screen, click "Export Settings" (secondary button). Verify navigation to the "Export Columns" screen.
2. **Column list**: Verify all 22 fields are listed with checkboxes (all checked by default), drag handles (6-dot grip), up/down arrows, and three-dot overflow menus.
3. **Essential Only preset**: Click "Essential Only". Verify only these fields remain checked: First Name, Last Name, DOB, Case ID, Authorization #, Appointment Date, Appointment Time, Services. All others should be unchecked.
4. **All Fields preset**: Click "All Fields". Verify all 22 fields are checked again.
5. **Checkbox toggle**: Uncheck "Middle Name". Verify the label dims (grayed out). Re-check it. Verify the label returns to normal color.
6. **Drag reorder** (P1): Grab the drag handle of "Last Name" and drag it above "First Name". Verify the list reorders and the item animates into position.
7. **Arrow reorder**: Use the up/down arrow buttons on any field to move it. Verify the list order updates.
8. **Overflow menu -- spacer insertion**: Click the three-dot menu on any field. Click "Insert Spacer Below". Verify a new "Empty Column" row appears with italic text and an inline X (close) button.
9. **Remove spacer**: Click the X button on the spacer row. Verify it is removed.
10. **Overflow menu -- Move to Top/Bottom** (P1): Via the overflow menu, move a field to the top and verify it jumps to position 1. Move it to bottom and verify it jumps to the last position.
11. **Expand services checkbox**: Check "Place each service on its own row (duplicate other fields)". Leave it checked for Test 6.
12. **Reset confirmation dialog**: Click "Reset". Verify a dialog appears asking to type "reset". Type "hello" -- verify the Confirm button remains disabled. Clear the field, type "reset" -- verify the Confirm button enables. Click "Confirm". Verify all columns return to default order, all checked, and the expand services checkbox is unchecked.
13. **Back navigation**: Click "Back". Verify return to the Results screen.

### Test 6: Export with Custom Config (P1)

**Flow**: Export Settings customization -> Save -> Verify

1. Navigate to Export Settings (from Results screen).
2. Click "Essential Only". Check "Place each service on its own row".
3. Click "Back" to return to Results.
4. Click "Save to XLSX".
5. Open the new XLSX file. Verify:
   - Only the essential columns are present (First Name, Last Name, DOB, Case ID, Authorization #, Appointment Date, Appointment Time, Services).
   - For any referral with 2+ services, each service has its own row. Other field values are duplicated across those rows.
6. Return to Export Settings and click "Reset" (type "reset", confirm). Click "Back".

### Test 7: Settings Screen (P0)

**Flow**: Main -> Settings -> Export Settings -> Back -> Back

1. **Navigate to Settings**: From the file selection screen, click the gear icon. Verify "Settings" screen loads with "Configure application preferences" subtitle.
2. **Privacy toggle**: The "Show extracted data by default" toggle should be off (default). Toggle it on. Note: this changes whether the Results screen starts with data unmasked.
3. **Export Columns row**: Verify the "Export Columns" card is visible with a description and a right-chevron arrow icon. Click it. Verify navigation to the "Export Columns" screen.
4. **Back from Export Settings**: Click "Back". Verify return to the Settings screen (not the Main screen).
5. **Back from Settings**: Click "Back". Verify return to the file selection screen.
6. **Verify privacy setting effect** (P1): Process files and navigate to Results. Verify data is unmasked by default (eye icon shows `Visibility`). Return to Settings, toggle it off, then re-process to confirm masking returns.

### Test 8: Help Screen Navigation (P0)

**Flow**: Verify Help is reachable from Main and Results, and Back returns correctly

1. **From Main**: Click the "?" icon on the file selection screen. Verify "Help & Support" screen loads with sections: Getting Started, Supported Formats, Tips, Support.
2. **Back to Main**: Click "Back". Verify return to the file selection screen.
3. **From Results**: Process files to reach the Results screen. Click the "?" icon. Verify the Help screen loads.
4. **Back to Results**: Click "Back". Verify return to the Results screen (not Main).
5. **Support email** (P1): Click "support@carbonworks.tech". Verify the system email client opens with the address pre-filled.

### Test 9: Navigation and State Management (P0)

**Flow**: Verify Start Over clears state and transitions animate

1. **Process and reach Results**: Add files, process them, arrive at Results screen.
2. **Start Over**: Click "Start Over". Verify:
   - Navigation returns to the file selection screen.
   - The file list is empty ("No files selected").
   - The "Process 0 Files" button is disabled.
3. **Screen transitions**: Throughout all tests, verify screen changes use a fade animation (300ms crossfade), not abrupt cuts.
4. **File picker remembers directory** (P1): Open the file picker, select files from a specific folder. Close. Open the file picker again. Verify it opens to the same directory as last time.

---

## Priority Summary

| Priority | Tests | Coverage |
|----------|-------|----------|
| **P0** (must pass) | 1, 2, 3, 4, 5 (steps 1-5, 8-9, 12-13), 7 (steps 1-5), 8 (steps 1-4), 9 (steps 1-3) | Core workflow, export, settings, help, navigation |
| **P1** (important) | 3 (steps 9-10), 5 (steps 6, 10), 6 (all), 7 (step 6), 8 (step 5), 9 (step 4) | Drag reorder, expand services, privacy effect, email link, directory memory |
