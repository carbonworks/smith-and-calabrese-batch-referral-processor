#!/bin/bash
# Carbon Works — PDF Generation Script
#
# ╔═══════════════════════════════════════════════════════════════════════════╗
# ║  ⚠️  AI ASSISTANT: ASK BEFORE EDITING THIS FILE                           ║
# ║                                                                           ║
# ║  This file is part of a 3-file PDF generation system:                     ║
# ║    1. generate-pdf.sh    — THIS FILE, entry point                         ║
# ║    2. carbonworks.typst  — pandoc wrapper, imports and invokes conf()     ║
# ║    3. carbonworks.typ    — defines conf() function (styling/layout)       ║
# ║                                                                           ║
# ║  This script:                                                             ║
# ║    • Sets up PATH for pandoc and typst                                    ║
# ║    • Passes the correct template (carbonworks.typst, not .typ)            ║
# ║    • Sets logo paths and other variables                                  ║
# ║                                                                           ║
# ║  Before making changes, please:                                           ║
# ║    1. Explain what you want to change and why                             ║
# ║    2. Confirm with the user which file(s) need modification               ║
# ║    3. Test PDF generation after changes                                   ║
# ╚═══════════════════════════════════════════════════════════════════════════╝
#
# Usage:
#   bash system/tools/templates/generate-pdf.sh <input.md> <output.pdf> [--client "Name"]
#
# Examples:
#   bash system/tools/templates/generate-pdf.sh consulting/service-offerings.md system/tools/outputs/service-offerings.pdf
#   bash system/tools/templates/generate-pdf.sh meetings/.../technology-report.md system/tools/outputs/technology-report.pdf --client "Tyler Calabrese"

set -e

# ── Setup PATH for pandoc and typst ──
export PATH="$PATH:/c/Users/rmdev/AppData/Local/Pandoc:/c/Users/rmdev/AppData/Local/Microsoft/WinGet/Packages/Typst.Typst_Microsoft.Winget.Source_8wekyb3d8bbwe/typst-x86_64-pc-windows-msvc"

# ── Parse arguments ──
INPUT=""
OUTPUT=""
CLIENT=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --client)
      CLIENT="$2"
      shift 2
      ;;
    *)
      if [[ -z "$INPUT" ]]; then
        INPUT="$1"
      elif [[ -z "$OUTPUT" ]]; then
        OUTPUT="$1"
      fi
      shift
      ;;
  esac
done

if [[ -z "$INPUT" || -z "$OUTPUT" ]]; then
  echo "Usage: bash system/tools/templates/generate-pdf.sh <input.md> <output.pdf> [--client \"Name\"]"
  exit 1
fi

if [[ ! -f "$INPUT" ]]; then
  echo "Error: Input file not found: $INPUT"
  exit 1
fi

# ── Ensure output directory exists ──
mkdir -p "$(dirname "$OUTPUT")"

# ── Build pandoc command ──
CMD=(
  pandoc "$INPUT"
  -o "$OUTPUT"
  --pdf-engine=typst
  --template=system/tools/templates/carbonworks.typst
  -V "template=system/tools/templates/carbonworks.typ"
  -V "emblem=../../../org/brand/assets/logo/symbol/symbol-green.svg"
  -V "wordmark=../../../org/brand/assets/logo/svg/wordmark-green.svg"
  --pdf-engine-opt=--root=.
  --pdf-engine-opt="--font-path=C:/Users/rmdev/AppData/Local/Microsoft/Windows/Fonts"
)

if [[ -n "$CLIENT" ]]; then
  CMD+=(-V "client=$CLIENT")
fi

echo "Generating: $OUTPUT"
echo "  Input:  $INPUT"
echo "  Client: ${CLIENT:-[none]}"
echo ""

"${CMD[@]}"

echo "Done: $OUTPUT"
