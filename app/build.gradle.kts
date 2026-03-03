import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.10"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.10"
}

group = "tech.carbonworks.snc.batchreferralparser"
version = "0.1.0"

repositories {
    google()
    mavenCentral()
}

dependencies {
    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // PDF text extraction
    implementation("org.apache.pdfbox:pdfbox:3.0.4")

    // PDF table extraction
    implementation("technology.tabula:tabula:1.0.5")

    // XLSX output
    implementation("org.apache.poi:poi-ooxml:5.3.0")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

tasks.test {
    useJUnitPlatform()
}

// Resolve duplicate transitive resources (e.g., vx.json from PDFBox/Tabula overlap)
tasks.withType<Copy> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

kotlin {
    jvmToolchain(17)
}

// Icon generation: run `python tools/generate-icons.py` to produce
// icon.ico and icon.png from the origami bird emblem SVG data.
// The generated files go into app/src/main/resources/ and should be
// committed to the repository.

compose.desktop {
    application {
        mainClass = "tech.carbonworks.snc.batchreferralparser.MainKt"

        jvmArgs("-Xmx512m")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "PDF Referral Parser"
            packageVersion = "1.0.0"
            vendor = "Carbon Works"
            description = "Batch PDF data extraction tool for SSA/DDS referral processing"
            copyright = "Copyright 2026 Carbon Works"

            windows {
                menuGroup = "Carbon Works"
                upgradeUuid = "e4c8b3a1-5f2d-4e6a-9b7c-1d3e5f7a9b0c"
                perUserInstall = true
                dirChooser = true
                shortcut = true
                iconFile.set(project.file("src/main/resources/icon.ico"))
            }

            macOS {
                bundleID = "tech.carbonworks.snc.batchreferralparser"
                // macOS .icns can be generated from icon.png using iconutil on macOS.
                // For now, jpackage will auto-generate from the PNG if no .icns is set.
                // iconFile.set(project.file("src/main/resources/icon.icns"))
            }

            linux {
                debMaintainer = "dev@carbonworks.tech"
                menuGroup = "Office"
                iconFile.set(project.file("src/main/resources/icon.png"))
            }
        }
    }
}
