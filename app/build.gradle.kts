import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"
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

    // JSON serialization (export column config persistence)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Compose Navigation (type-safe back stack management)
    implementation("org.jetbrains.androidx.navigation:navigation-compose-desktop:2.8.0-alpha10")

    // Drag-and-drop reordering for LazyColumn
    implementation("sh.calvin.reorderable:reorderable:3.0.0")

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

// Inject branded installer resources into jpackage resource dir right before packaging.
// jpackage only recognizes specific filenames (main.wxs, overrides.wxi, etc.) in the
// resource dir — it ignores BMP files. So we template main.wxs with absolute paths
// to the BMP source files, which WiX's candle/light tools can then resolve.
tasks.matching { it.name == "packageMsi" }.configureEach {
    doFirst {
        val resourceDir = project.layout.buildDirectory.dir("compose/tmp/resources").get().asFile
        resourceDir.mkdirs()
        val installerDir = project.file("src/main/installer")
        val bannerPath = installerDir.resolve("banner.bmp").absolutePath.replace("\\", "\\\\")
        val dialogPath = installerDir.resolve("dialog.bmp").absolutePath.replace("\\", "\\\\")
        val templateWxs = installerDir.resolve("main.wxs").readText()
        val resolvedWxs = templateWxs
            .replace("Value=\"banner.bmp\"", "Value=\"$bannerPath\"")
            .replace("Value=\"dialog.bmp\"", "Value=\"$dialogPath\"")
        resourceDir.resolve("main.wxs").writeText(resolvedWxs)
        println("[installer] Wrote main.wxs with absolute BMP paths to ${resourceDir.absolutePath}")
    }
}

compose.desktop {
    application {
        mainClass = "tech.carbonworks.snc.batchreferralparser.MainKt"

        jvmArgs("-Xmx512m")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "PDF Authorization Processor"
            packageVersion = "1.1.901"
            vendor = "Carbon Works"
            description = "Turns referral PDFs into organized spreadsheets, quickly and accurately."
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
