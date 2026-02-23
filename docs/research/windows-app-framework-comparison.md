---
type: knowledge
status: active
created: 2026-02-16
updated: 2026-02-17
---

# Windows Application Framework Comparison

Research into desktop application frameworks for building a branded Windows utility tool for client delivery.

---

## Use Case

A client-facing branded utility for processing PDF files. The UI is a thin shell:
1. File picker / drag-drop to select 1-50 PDFs
2. "Process" button that invokes a Python extraction script
3. Data grid showing extracted results (~50 rows x 15 columns)
4. "Save to XLSX" button
5. Progress indicator during processing

**Key architecture decision**: The PDF extraction engine (pdfplumber + openpyxl) is a separate Python script. The UI framework does NOT do PDF processing — it invokes the script and displays results. The UI can be written in any language.

---

## Branding Requirements

The tool represents a consulting firm's deliverable. Requirements:
- Custom 8-color palette (specific hex values defined in brand guidelines)
- Inter font family (bundled), Segoe UI fallback
- Warm background (#FFFAF5) instead of system white
- Swappable between two brand themes at build time
- Clean, uncluttered design with generous whitespace
- Professional enough for a consulting deliverable

---

## Comparison Table (Client-Facing UI Shell)

| Criterion | **Tauri** | **WPF (.NET 8)** | PyQt6 | ttkbootstrap | customtkinter | Electron |
|---|---|---|---|---|---|---|
| **Theming/Branding** | Excellent (CSS) | Excellent (XAML) | Very Good (QSS) | Limited | Limited | Excellent (CSS) |
| **Data Grid** | Very Good (HTML/JS) | Excellent (built-in) | Very Good | Acceptable | **Poor (none)** | Very Good (HTML/JS) |
| **File Selection** | Excellent (native) | Excellent (native) | Very Good | Acceptable | Acceptable | Very Good |
| **Theme Swappability** | Excellent (CSS vars) | Excellent (ResourceDict) | Good (.qss files) | Moderate | Poor | Excellent (CSS vars) |
| **Professional Look** | Excellent | Excellent | Good-VG | Moderate | Moderate | Excellent |
| **Installer Size** | **2-8 MB** | 25-50 MB | 30-60 MB | 15-25 MB | 15-30 MB | **80-150 MB** |
| **Maintainability** | Excellent (HTML/CSS/JS) | Moderate (XAML/C#) | Good (Python) | Good (Python) | Good (Python) | Excellent (HTML/CSS/JS) |
| **Dev Speed** | 2-4 days | 2-3 days | 2-3 days | 2-3 days | 3-5 days | 1-2 days |

---

## Recommendation

### Primary: Tauri (Rust + HTML/CSS/JS frontend)

**Why:**
- **CSS is the best branding tool that exists.** The 8-color palette, Inter font, warm backgrounds, and whitespace are trivially expressed in CSS. A CSS file is the most natural way to encode a brand system.
- **2-8 MB installer signals craftsmanship.** Uses system-installed WebView2 (present on all Windows 10/11). A 5 MB installer for a focused utility feels intentional. A 120 MB Electron installer feels bloated.
- **Theme swapping is trivial.** CSS custom properties (`--color-primary: #2D3748`) — swap themes by loading a different CSS file or toggling a class.
- **Most maintainable codebase.** HTML/CSS/JS is the most widely known technology stack. Any future developer can modify colors, labels, and layout. The Rust backend is ~20-30 lines of boilerplate (invoke Python subprocess, return results).
- **Custom title bar.** Tauri supports frameless windows — build a branded title bar in HTML/CSS matching the brand palette.

**Trade-off:** Requires Rust toolchain setup. The backend Rust code is minimal boilerplate but unfamiliar if you haven't used Rust before. One-time learning cost.

**Architecture:**
```
[Tauri shell (2-8 MB)]
  → HTML/CSS/JS frontend (branded UI)
  → Rust backend (thin — invokes Python, returns JSON)
    → Python extraction script (pdfplumber + openpyxl)
      → Reads PDFs, outputs XLSX
```

### Strong Alternative: C# WPF (.NET 8)

**Why:**
- Best built-in DataGrid of any framework — no JavaScript dependencies needed
- ResourceDictionary + WindowChrome = most structured native theming system
- Mature, well-documented, professional source code delivery
- 25-50 MB self-contained installer

**Choose WPF if:** You or the team already knows C#/XAML, the client's future developers are likely .NET developers, or you want zero JavaScript dependencies.

### Eliminated

| Framework | Reason |
|---|---|
| **C# WinForms** | No theming system. Microsoft confirmed no plans to add one. Branding requires building a custom theme engine — disproportionate effort. |
| **Electron** | Identical UI capabilities to Tauri but 80-150 MB installer. No advantage. |
| **customtkinter** | No built-in data grid widget. Building a 50x15 sortable grid from label widgets is not professional. |
| **ttkbootstrap** | Theming ceiling too low for consulting deliverable quality. Acceptable for internal tools, not client-facing. |
| **PyQt6** | Awkward middle ground — harder to theme than CSS, worse grid than WPF, larger than Tauri. Legitimate but not the best at anything. |

---

## Context Menu Integration

Regardless of UI framework, Windows Explorer context menu integration uses registry entries:

```
HKEY_CLASSES_ROOT\SystemFileAssociations\.pdf\shell\ProcessWithTool\command
  (Default) = "C:\Path\To\app.exe" "%1"
```

**Multi-file handling**: Single-Instance Accumulator pattern (Mutex + NamedPipe) or SendTo folder. See original research (2026-02-16) for details.

**Windows 11**: Legacy shell entries appear behind "Show more options" — acceptable for a known client tool.

---

## Applied In

- [Smith & Calabrese Consulting](/work/projects/smith-calabrese-consulting/) — PDF referral parser tool

---

## Sources

Research conducted 2026-02-16, updated 2026-02-17. Key sources: Tauri 2.0 documentation (window customization, sidecar), WPF theming documentation (ResourceDictionary, WindowChrome), WinForms dark mode issue #7641 (Microsoft confirming no theming plans), PyQt6 QSS documentation, customtkinter table discussions, ttkbootstrap font handling, Microsoft shell extension documentation, framework comparison benchmarks.

---

*Last updated: 2026-02-17*
