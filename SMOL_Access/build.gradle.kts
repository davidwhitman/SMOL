plugins {
    kotlin("jvm")
}

group = "com.wisp"
version = "1.0"

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${project.property("kotlin")!!}")
    implementation(project(":Utilities"))
    implementation(project(":VRAM_Checker"))
    api(project(":Mod_Repo"))

    implementation("org.jetbrains.kotlin:kotlin-reflect:${project.property("kotlin")!!}")
    implementation(project.property("coroutines")!!)

    implementation(fileTree("../libs") {
        include("**/*.jar")
        exclude("TinyLog")
    })

    // CSV
    implementation("org.apache.commons:commons-csv:1.8")
    // API
    api("io.ktor:ktor-client-core:1.6.7")
    api("io.ktor:ktor-client-cio:1.6.7")
    api("io.ktor:ktor-client-logging:1.6.7")

    // Version Checker Dependencies
    implementation("de.siegmar:fastcsv:2.1.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.13.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.1")

    // Save file reading
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.13.1")

    // To calculate checksum for archive files
    // https://mvnrepository.com/artifact/commons-codec/commons-codec
    implementation("commons-codec:commons-codec:1.15")

}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "16"
        @Suppress("SuspiciousCollectionReassignment")
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
    }
}


kotlin.sourceSets.main {
    // List of where your Kotlin source code is, if any.
    kotlin.setSrcDirs(listOf("main/kotlin"))
    // List of where resources (the "data" folder) are.
    resources.setSrcDirs(listOf("main/resources"))
}