dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

val generatedMetadataDir = layout.buildDirectory.dir("generated/resources/release-metadata")

val generateReleaseMetadata by tasks.registering {
    val outDir = generatedMetadataDir
    val companionVer = project.version.toString()
    val drivepadVer = (project.findProperty("DRIVEPAD_RELEASE_VERSION") as String?) ?: companionVer
    val protocolVer = (project.findProperty("DRIVEPAD_PROTOCOL_VERSION") as String?) ?: "1"
    inputs.property("companionVersion", companionVer)
    inputs.property("drivepadVersion", drivepadVer)
    inputs.property("protocolVersion", protocolVer)
    outputs.dir(outDir)
    doLast {
        val dir = outDir.get().asFile
        dir.mkdirs()
        dir.resolve("drivepad-release.properties").writeText(
            """
            drivepad.version=$drivepadVer
            companion.version=$companionVer
            protocol.version=$protocolVer
            """.trimIndent() + "\n"
        )
    }
}

sourceSets.main {
    resources.srcDir(generatedMetadataDir)
}

tasks.named("processResources") {
    dependsOn(generateReleaseMetadata)
}
