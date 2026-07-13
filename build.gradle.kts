import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
    `java-library`
    alias(libs.plugins.lombok)
    alias(libs.plugins.plugin.yml)
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.shadow)
}

repositories {
    mavenCentral()
    // mavenLocal() - Only use this for testing if ever

    //maven("https://maven.smyler.net/releases/")

    maven("https://maven.buildtheearth.net/releases") // T-- & Porkchop Lib

    exclusiveContent {
        forRepository {
            maven {
                url = uri("https://repo.papermc.io/repository/maven-public/")
            }
        }
        // Ensure papermc repo is only used for paper dependencies
        // Paper also proxies maven central, which has some issues - see https://github.com/PaperMC/Paper/issues/13987
        filter {
            includeGroup("io.papermc")
            includeGroup("io.papermc.paper")
            includeGroup("net.md-5")
            includeGroup("com.mojang")
        }
    }

    maven("https://repo.lushplugins.org/releases") // PluginUpdater
}

dependencies {
    paperLibrary(libs.terraminusminus)
    paperLibrary(libs.daporkchop.lib.common)
    implementation(libs.bstats)
    paperLibrary(libs.pluginupdater.common) {
        exclude(group = "com.google.guava", module = "guava")
    }
    paperLibrary(libs.pluginupdater.paper)
    compileOnly(libs.paper.api)
    // Terra+- itself doesn't need Jackson, but we are hitting this JDK bug: https://bugs.openjdk.org/browse/JDK-8305250.
    // Having a direct compile dependency on Jackson gets rid of the unnecessary warning.
    compileOnly(libs.jackson.databind)
}

group = "de.btegermany"
version = "1.7.1"
description = "A plugin which implements the terra-- api in a paper plugin"
java.sourceCompatibility = JavaVersion.VERSION_21

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}

paper {
    website = "https://bte-germany.de"

    main = "de.btegermany.terraplusminus.Terraplusminus"

    apiVersion = "1.21.4"

    load = BukkitPluginDescription.PluginLoadOrder.STARTUP
    authors = listOf("meysster", "Nudlsupp", "Nachwahl", "Zoriot")

    prefix = "T+-"

    loader = "de.btegermany.terraplusminus.PluginLibrariesLoader"
    generateLibrariesJson = true // https://docs.eldoria.de/pluginyml/libraries/#paper
}

tasks {
    generatePaperPluginDescription {
        useDefaultCentralProxy()
    }
}

tasks.jar {
    archiveClassifier = "UNSHADED"
    enabled = false // Disable the default jar task since we are using shadowJar
}

tasks.shadowJar {
    archiveClassifier = ""
    relocate(
        "org.bstats",
        "de.btegermany.terraplusminus.libs.bstats"
    )
}

tasks.assemble {
    dependsOn(tasks.shadowJar) // Ensure that the shadowJar task runs before the build task
}