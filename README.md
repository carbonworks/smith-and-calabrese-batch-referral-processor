# S&C Batch Referral Processor

A batch PDF data extraction tool for Smith & Calabrese Assessments. Processes SSA/DDS consultative examination referral PDFs and outputs structured data to XLSX spreadsheets.

## Tech Stack

- **Language**: Kotlin
- **UI**: Compose Multiplatform (Desktop)
- **PDF extraction**: Apache PDFBox + Tabula-java
- **Spreadsheet output**: Apache POI
- **OCR fallback**: Tess4J (Tesseract)
- **Platforms**: Windows (primary), macOS (secondary)

## Project Structure

```
src/                     # Kotlin/Compose Multiplatform Desktop application
docs/                    # Project documentation, research, brand assets
reference/               # Python prototype scripts and sample output
tools/                   # PDF generation toolchain for deliverable documents
```

## Build Instructions

**Prerequisites**: JDK 17+ (e.g., [Eclipse Temurin](https://adoptium.net/))

```bash
# Run the application
./gradlew run

# Build without running
./gradlew build

# Package native installer (MSI for Windows, DMG for macOS)
./gradlew package
```

## Documentation

- [Tech Stack Decision](docs/tech-stack-decision.md) — why Kotlin/Compose Multiplatform
- [Build Plan](docs/build-plan.md) — delivery schedule and work breakdown
- [Solution Analysis](docs/solution-analysis.md) — problem analysis and option evaluation
- [JVM Library Research](docs/research/jvm-pdf-extraction-research.md) — PDF library ecosystem evaluation

## License

Proprietary — Carbon Works. Delivered source code licensed to Smith & Calabrese Assessments per contract terms.
