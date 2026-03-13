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
    // Spigot API – works on both Spigot and Paper servers
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    // Bukkit dependency required by spigot-api
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    // Dynmap API
    maven("https://repo.mikeprimm.com/")
    mavenCentral()
    // Local Maven repo (.m2) – useful when using BuildTools offline
    mavenLocal()
}

// ---------------------------------------------------------------------------
// Dependencies  (all "compileOnly" – provided by the server at runtime)
// ---------------------------------------------------------------------------
dependencies {
    // Spigot API for Minecraft 1.21.4 (compatible with 1.21.x including 1.21.8).
    // Compiling against spigot-api guarantees the plugin runs on both
    // Spigot and Paper without modification.
    compileOnly("org.spigotmc:spigot-api:1.21.4-R0.1-SNAPSHOT")

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
