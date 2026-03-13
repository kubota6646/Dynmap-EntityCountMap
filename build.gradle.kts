plugins {
    java
}

group = "com.github.kubota6646"
version = "1.0.0"
description = "Dynmap extension that adds a colour-coded entity-count heat-map layer"

// ---------------------------------------------------------------------------
// Java Toolchain – compiles and tests with Java 21 regardless of which JDK
// is used to run Gradle itself.
// ---------------------------------------------------------------------------
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

// ---------------------------------------------------------------------------
// Repositories
// ---------------------------------------------------------------------------
repositories {
    // Paper API (Minecraft 1.21.x)
    maven("https://repo.papermc.io/repository/maven-public/")
    // Dynmap API
    maven("https://repo.mikeprimm.com/")
    mavenCentral()
}

// ---------------------------------------------------------------------------
// Dependencies  (all "compileOnly" – provided by the server at runtime)
// ---------------------------------------------------------------------------
dependencies {
    // Paper API for Minecraft 1.21.4 (compatible with 1.21.x including 1.21.8)
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    // Dynmap API
    compileOnly("us.dynmap:dynmap-api:3.7-beta-3") {
        isTransitive = false
    }
}

// ---------------------------------------------------------------------------
// Process resources – replace ${project.version} inside plugin.yml
// ---------------------------------------------------------------------------
tasks.processResources {
    val props = mapOf("project.version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

// ---------------------------------------------------------------------------
// JAR naming
// ---------------------------------------------------------------------------
tasks.jar {
    archiveBaseName = "dynmap-entitycountmap"
    archiveVersion  = project.version.toString()
}
