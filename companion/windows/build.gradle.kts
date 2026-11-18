dependencies {
    implementation(project(":core"))
    implementation("net.java.dev.jna:jna:5.15.0")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
