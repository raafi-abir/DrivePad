import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    kotlin("jvm") version "2.2.10"
    application
}

val companionVersionProp = (findProperty("COMPANION_VERSION") as String?) ?: "1.0.0"
val drivepadReleaseProp = (findProperty("DRIVEPAD_RELEASE_VERSION") as String?) ?: companionVersionProp
val protocolVersionProp = (findProperty("DRIVEPAD_PROTOCOL_VERSION") as String?) ?: "1"

allprojects {
    group = "com.drivepad.companion"
    version = companionVersionProp
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    kotlin {
        jvmToolchain(21)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnit()
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.drivepad.companion.MainKt")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":linux"))
    implementation(project(":windows"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("net.java.dev.jna:jna:5.15.0")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

tasks.test {
    useJUnit()
    dependsOn(":core:test", ":linux:test", ":windows:test")
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "com.drivepad.companion.MainKt",
            "Implementation-Title" to "DrivePad Companion",
            "Implementation-Version" to companionVersionProp,
            "X-DrivePad-Version" to drivepadReleaseProp,
            "X-DrivePad-Protocol-Version" to protocolVersionProp
        )
    }
}

val fatJar = tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Assembles an executable standalone cross-platform JAR including :core, :linux, :windows, Kotlin runtime, coroutines, and JNA."
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(
            "Main-Class" to "com.drivepad.companion.MainKt",
            "Implementation-Title" to "DrivePad Companion",
            "Implementation-Version" to companionVersionProp,
            "X-DrivePad-Version" to drivepadReleaseProp,
            "X-DrivePad-Protocol-Version" to protocolVersionProp
        )
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })
}

val linuxDistTar = tasks.register<Tar>("linuxDistTar") {
    group = "distribution"
    description = "Packages portable Linux x64/Arch distribution archive (drivepad-companion-linux-x64.tar.gz)."
    dependsOn(fatJar)
    archiveBaseName.set("drivepad-companion")
    archiveVersion.set(companionVersionProp)
    archiveClassifier.set("linux-x64")
    archiveExtension.set("tar.gz")
    compression = Compression.GZIP

    val rootPrefix = "drivepad-companion-${companionVersionProp}"
    into(rootPrefix) {
        from(fatJar.flatMap { it.archiveFile }) {
            into("lib")
        }
        from(layout.projectDirectory.file("packaging/linux/bin/drivepad-companion")) {
            into("bin")
            filePermissions { unix("0755") }
        }
        from(layout.projectDirectory.dir("packaging/linux/scripts")) {
            into("scripts")
            filePermissions { unix("0755") }
        }
        from(layout.projectDirectory.file("packaging/linux/udev/99-drivepad-uinput.rules")) {
            into("udev")
            filePermissions { unix("0644") }
        }
        from(layout.projectDirectory.dir("packaging/linux/arch")) {
            into("arch")
            filePermissions { unix("0644") }
        }
        from(layout.projectDirectory.file("packaging/linux/README.txt")) {
            filePermissions { unix("0644") }
        }
        from(layout.projectDirectory.file("../docs/THIRD_PARTY_NOTICES.md")) {
            filePermissions { unix("0644") }
        }
    }
}

val windowsDistZip = tasks.register<Zip>("windowsDistZip") {
    group = "distribution"
    description = "Packages portable Windows x64 distribution archive (drivepad-companion-windows-x64.zip) without unverified third-party drivers."
    dependsOn(fatJar)
    archiveBaseName.set("drivepad-companion")
    archiveVersion.set(companionVersionProp)
    archiveClassifier.set("windows-x64")

    val rootPrefix = "drivepad-companion-${companionVersionProp}"
    into(rootPrefix) {
        from(fatJar.flatMap { it.archiveFile }) {
            into("lib")
        }
        from(layout.projectDirectory.dir("packaging/windows/bin")) {
            into("bin")
        }
        from(layout.projectDirectory.dir("packaging/windows/scripts")) {
            into("scripts")
        }
        from(layout.projectDirectory.dir("packaging/windows/native")) {
            into("native")
        }
        from(layout.projectDirectory.file("packaging/windows/README.txt"))
        from(layout.projectDirectory.file("../docs/THIRD_PARTY_NOTICES.md"))
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val generateReleaseManifest = tasks.register("generateReleaseManifest") {
    group = "distribution"
    description = "Generates release-manifest.json and SHA256SUMS.txt for all packaged companion distributions."
    dependsOn(fatJar, linuxDistTar, windowsDistZip)

    val distDir = layout.buildDirectory.dir("distributions")
    val fatJarFile = fatJar.flatMap { it.archiveFile }
    val linuxTarFile = linuxDistTar.flatMap { it.archiveFile }
    val windowsZipFile = windowsDistZip.flatMap { it.archiveFile }
    val dVer = drivepadReleaseProp
    val dCode = ((findProperty("DRIVEPAD_VERSION_CODE") as String?)?.toIntOrNull() ?: 1)
    val cVer = companionVersionProp
    val pVer = protocolVersionProp

    outputs.files(
        distDir.map { it.file("release-manifest.json") },
        distDir.map { it.file("SHA256SUMS.txt") },
        distDir.map { it.file(fatJarFile.get().asFile.name) }
    )

    doLast {
        fun sha256(file: java.io.File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(8192)
                var read = input.read(buf)
                while (read != -1) {
                    digest.update(buf, 0, read)
                    read = input.read(buf)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        val outDir = distDir.get().asFile
        outDir.mkdirs()
        val stagedFatJar = fatJarFile.get().asFile.let { src ->
            val dst = outDir.resolve(src.name)
            src.copyTo(dst, overwrite = true)
            dst
        }
        val buildTimestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))

        data class PackageEntry(
            val targetOs: String,
            val arch: String,
            val file: java.io.File,
            val dependencies: List<String>,
            val limitations: List<String>
        )

        val entries = listOf(
            PackageEntry(
                targetOs = "linux",
                arch = "x64",
                file = linuxTarFile.get().asFile,
                dependencies = listOf("Java 21+ JRE/JDK", "Linux kernel uinput module + udev rule for non-root access"),
                limitations = listOf(
                    "Physical kernel /dev/uinput and Steam/ETS2 hardware testing pending outside headless build container",
                    "UDP port 42100 must be permitted in host firewall (ufw/firewalld)"
                )
            ),
            PackageEntry(
                targetOs = "windows",
                arch = "x64",
                file = windowsZipFile.get().asFile,
                dependencies = listOf("Java 21+ (64-bit) JRE/JDK", "Manual Nefarius ViGEmBus v1.22.0 + 64-bit ViGEmClient.dll"),
                limitations = listOf(
                    "ViGEmBus is archived/EOL upstream (Nov 2023) and must be installed manually; never disable HVCI or Secure Boot",
                    "Physical Windows 10/11, HVCI, Steam/ETS2 hardware validation pending outside Linux build host"
                )
            ),
            PackageEntry(
                targetOs = "cross-platform-jvm",
                arch = "any",
                file = stagedFatJar,
                dependencies = listOf("Java 21+ JRE/JDK", "OS gamepad backend prerequisite (uinput on Linux, ViGEmBus on Windows)"),
                limitations = listOf(
                    "Standalone executable JAR; macOS/other hosts run in --backend noop UDP diagnostic mode only"
                )
            )
        )

        val checksumLines = entries.joinToString("\n") { e ->
            "${sha256(e.file)}  ${e.file.name}"
        } + "\n"
        outDir.resolve("SHA256SUMS.txt").writeText(checksumLines)

        fun jsonArray(items: List<String>): String =
            items.joinToString(", ") { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" }

        val artifactsJson = entries.joinToString(",\n") { e ->
            val sum = sha256(e.file)
            """
            |    {
            |      "targetOs": "${e.targetOs}",
            |      "architecture": "${e.arch}",
            |      "packageFilename": "${e.file.name}",
            |      "packageSizeBytes": ${e.file.length()},
            |      "sha256": "$sum",
            |      "requiredExternalDependencies": [${jsonArray(e.dependencies)}],
            |      "knownLimitations": [${jsonArray(e.limitations)}]
            |    }""".trimMargin()
        }

        val manifestJson = """
            |{
            |  "drivepadVersion": "$dVer",
            |  "androidApplicationId": "com.drivepad.app",
            |  "androidVersionName": "$dVer",
            |  "androidVersionCode": $dCode,
            |  "companionVersion": "$cVer",
            |  "protocolVersion": $pVer,
            |  "protocolSpec": "33-byte Little-Endian UDP v1",
            |  "thirdPartyNotices": "THIRD_PARTY_NOTICES.md",
            |  "buildTimestampUtc": "$buildTimestamp",
            |  "reproducibilityNote": "Archives use fixed file timestamps and deterministic entry ordering; release-manifest.json records buildTimestampUtc externally so package binaries remain reproducible.",
            |  "artifacts": [
            |$artifactsJson
            |  ]
            |}
            |""".trimMargin()
        outDir.resolve("release-manifest.json").writeText(manifestJson)
    }
}

tasks.register("packageDistributions") {
    group = "distribution"
    description = "Builds reproducible fat JAR, Linux tar.gz, Windows x64 zip, release-manifest.json, and SHA256SUMS.txt."
    dependsOn(fatJar, linuxDistTar, windowsDistZip, generateReleaseManifest)
}
