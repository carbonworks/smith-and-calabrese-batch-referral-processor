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

    // PDF text extraction
    implementation("org.apache.pdfbox:pdfbox:3.0.4")

    // PDF table extraction
    implementation("technology.tabula:tabula:1.0.5")

    // XLSX output
    implementation("org.apache.poi:poi-ooxml:5.3.0")

    // OCR fallback (declared, not wired up yet)
    implementation("net.sourceforge.tess4j:tess4j:5.13.0")
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "tech.carbonworks.snc.batchreferralparser.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "SNC Batch Referral Processor"
            packageVersion = "1.0.0"
            vendor = "Carbon Works"

            windows {
                menuGroup = "Carbon Works"
                upgradeUuid = "e4c8b3a1-5f2d-4e6a-9b7c-1d3e5f7a9b0c"
            }
        }
    }
}
