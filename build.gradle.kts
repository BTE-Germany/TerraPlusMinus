import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import net.minecrell.pluginyml.paper.PaperPluginDescription

plugins {
    `java`
    id("de.eldoria.plugin-yml.paper") version "0.7.1"
    id("io.freefair.lombok") version "8.14"
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    maven {
        url = uri("https://oss.sonatype.org/content/groups/public/")
    }

    maven {
        url = uri("https://repo.opencollab.dev/snapshot/")
    }

    maven {
        url = uri("https://jitpack.io")
    }

    maven {
        url = uri("https://maven.daporkchop.net/")
    }

    maven {
        url = uri("https://repo.dmulloy2.net/repository/public/")
    }

    maven {
        url = uri("https://maven.enginehub.org/repo/")
    }

    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

dependencies {
    paperLibrary(libs.io.papermc.paperlib)
    paperLibrary(libs.com.google.code.gson.gson)
    paperLibrary(libs.com.github.bteuk.terraminusminus)
    paperLibrary(libs.com.google.guava.guava)
    paperLibrary(libs.org.apache.commons.commons.lang3)
    paperLibrary(libs.com.fasterxml.jackson.core.jackson.databind)
    paperLibrary(libs.io.netty.netty.buffer)
    paperLibrary(libs.net.daporkchop.lib.common)
    paperLibrary(libs.lzma.lzma)
    compileOnly(libs.io.papermc.paper.paper.api)
    compileOnly(libs.org.jetbrains.annotations)
    compileOnly(libs.commons.io.commons.io)
    compileOnly(libs.com.comphenix.protocol.protocollib)
    compileOnly(libs.com.fastasyncworldedit.fastasyncworldedit.core)
    compileOnly(libs.com.fastasyncworldedit.fastasyncworldedit.bukkit)
}

group = "de.btegermany"
version = "1.5.0"
description = "A plugin which implements the terra-- api in a spigot plugin"
java.sourceCompatibility = JavaVersion.VERSION_21

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}

paper {
    website = "https://bte-germany.de"

    // Plugin main class (required)
    main = "de.btegermany.terraplusminus.Terraplusminus"
    loader = "de.btegermany.terraplusminus.PluginLibrariesLoader"

    // Generate paper-libraries.json from `library` and `paperLibrary` in `dependencies`
    generateLibrariesJson = true

    // Mark plugin for supporting Folia
    foliaSupported = false

    // API version (Needs to be 1.19 or higher)
    apiVersion = "1.21.4"

    // Other possible properties from plugin.yml (optional)
    load = BukkitPluginDescription.PluginLoadOrder.STARTUP
    authors = listOf("meysster", "Nudlsupp", "Nachwahl", "Zoriot")

    prefix = "T+-"
    defaultPermission = BukkitPluginDescription.Permission.Default.OP // TRUE, FALSE, OP or NOT_OP

    serverDependencies {
        register("FastAsyncWorldEdit") {
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            required = false
        }
    }
}

