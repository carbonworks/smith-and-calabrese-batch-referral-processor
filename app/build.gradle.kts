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

// ---------------------------------------------------------------------------
// Icon generation task: renders the CarbonWorks origami bird emblem at
// multiple resolutions and produces icon.ico (Windows) and icon.png (general).
// Run: ./gradlew :app:generateIcons
// ---------------------------------------------------------------------------
tasks.register("generateIcons") {
    group = "build"
    description = "Generate application icon files (ICO, PNG) from the origami bird emblem"

    val resourcesDir = file("src/main/resources")
    val icoFile = resourcesDir.resolve("icon.ico")
    val pngFile = resourcesDir.resolve("icon.png")

    outputs.files(icoFile, pngFile)

    doLast {
        resourcesDir.mkdirs()

        // CarbonWorks brand teal: #03757A
        val brandTeal = java.awt.Color(0x03, 0x75, 0x7A)

        // Origami bird polygon vertices (from cw-emblem.svg path data).
        // Y coordinates already include the SVG translate(0,-952.36218) offset.
        data class Poly(val xs: DoubleArray, val ys: DoubleArray)

        val birdPolygons = listOf(
            // Upper right wing triangle
            Poly(
                doubleArrayOf(60.7828, 87.9637, 60.7828),
                doubleArrayOf(964.36215, 965.24555, 991.24135)
            ),
            // Upper left wing triangle
            Poly(
                doubleArrayOf(58.8083, 58.8083, 33.5402),
                doubleArrayOf(965.81345, 992.59795, 992.59795)
            ),
            // Beak triangle
            Poly(
                doubleArrayOf(86.5136, 90.0, 77.4123),
                doubleArrayOf(969.44145, 970.64035, 978.11715)
            ),
            // Left wing / body pentagon
            Poly(
                doubleArrayOf(18.3301, 23.8527, 36.7181, 30.7327, 18.3301),
                doubleArrayOf(972.40705, 972.40705, 986.47755, 992.59795, 992.59795)
            ),
            // Small tail triangle
            Poly(
                doubleArrayOf(16.3555, 16.3555, 10.0),
                doubleArrayOf(973.63745, 979.47385, 979.47385)
            ),
            // Lower belly triangle
            Poly(
                doubleArrayOf(19.7185, 58.3455, 47.578),
                doubleArrayOf(994.61705, 994.61705, 1024.0517)
            ),
            // Right body / tail triangle
            Poly(
                doubleArrayOf(58.8083, 58.8083, 46.2205),
                doubleArrayOf(999.15991, 1040.3622, 1033.4847)
            ),
        )

        // Compute bounding box
        val allXs = birdPolygons.flatMap { it.xs.toList() }
        val allYs = birdPolygons.flatMap { it.ys.toList() }
        val minX = allXs.min()
        val maxX = allXs.max()
        val minY = allYs.min()
        val maxY = allYs.max()
        val birdW = maxX - minX
        val birdH = maxY - minY

        fun renderBird(size: Int): java.awt.image.BufferedImage {
            val img = java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            g.setRenderingHint(
                java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON
            )

            // Transparent background (already default for TYPE_INT_ARGB)

            // Scale and center with 6% padding
            val padding = size * 0.06
            val available = size - 2 * padding
            val scale = available / maxOf(birdW, birdH)
            val scaledW = birdW * scale
            val scaledH = birdH * scale
            val offsetX = (size - scaledW) / 2 - minX * scale
            val offsetY = (size - scaledH) / 2 - minY * scale

            g.color = brandTeal

            for (poly in birdPolygons) {
                val n = poly.xs.size
                val pxs = IntArray(n) { ((poly.xs[it] * scale + offsetX) + 0.5).toInt() }
                val pys = IntArray(n) { ((poly.ys[it] * scale + offsetY) + 0.5).toInt() }
                g.fillPolygon(pxs, pys, n)
            }

            g.dispose()
            return img
        }

        // Render at standard icon sizes
        val sizes = listOf(16, 32, 48, 256)
        val images = sizes.map { renderBird(it) }

        // Save 256x256 PNG
        javax.imageio.ImageIO.write(images.last(), "PNG", pngFile)
        println("  Generated ${pngFile.absolutePath}")

        // Write Windows ICO file (multi-resolution).
        // ICO format: 6-byte header, then N * 16-byte directory entries,
        // then N PNG image blobs.
        val pngBlobs = images.map { img ->
            val baos = java.io.ByteArrayOutputStream()
            javax.imageio.ImageIO.write(img, "PNG", baos)
            baos.toByteArray()
        }

        java.io.DataOutputStream(java.io.BufferedOutputStream(java.io.FileOutputStream(icoFile))).use { out ->
            fun writeLE16(v: Int) {
                out.write(v and 0xFF)
                out.write((v shr 8) and 0xFF)
            }
            fun writeLE32(v: Int) {
                out.write(v and 0xFF)
                out.write((v shr 8) and 0xFF)
                out.write((v shr 16) and 0xFF)
                out.write((v shr 24) and 0xFF)
            }

            // ICO header
            writeLE16(0)          // reserved
            writeLE16(1)          // type: 1 = ICO
            writeLE16(sizes.size) // number of images

            // Directory entries
            var dataOffset = 6 + sizes.size * 16  // header + all directory entries
            for (i in sizes.indices) {
                val s = sizes[i]
                val blob = pngBlobs[i]
                out.write(if (s >= 256) 0 else s)  // width (0 = 256)
                out.write(if (s >= 256) 0 else s)  // height (0 = 256)
                out.write(0)  // color palette count
                out.write(0)  // reserved
                writeLE16(1)  // color planes
                writeLE16(32) // bits per pixel
                writeLE32(blob.size) // image data size
                writeLE32(dataOffset) // offset to image data
                dataOffset += blob.size
            }

            // Image data (PNG blobs)
            for (blob in pngBlobs) {
                out.write(blob)
            }
        }
        println("  Generated ${icoFile.absolutePath}")
        println("Icon generation complete: icon.ico (${sizes.joinToString(", ") { "${it}x${it}" }}), icon.png (256x256)")
    }
}

compose.desktop {
    application {
        mainClass = "tech.carbonworks.snc.batchreferralparser.MainKt"

        jvmArgs("-Xmx512m")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "SNC Batch Referral Processor"
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
