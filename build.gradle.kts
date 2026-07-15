plugins {
    kotlin("jvm") version "2.3.20"
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

repositories {
    mavenCentral()
    maven("https://maven.aliyun.com/repository/public/") {
        name = "Aliyun"
    }
    maven("https://maven.aliyun.com/repository/central") {
        name = "central"
    }
    maven("https://repo.alessiodp.com/releases/") {
        name = "libby"
    }
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://repo.extendedclip.com/releases/") {
        name = "placeholderapi"
    }
    maven("https://repo.codemc.io/repository/maven-releases/") {
        name = "codemc-releases"
    }
    maven("https://repo.codemc.io/repository/maven-snapshots/") {
        name = "codemc-snapshots"
    }
}

dependencies {
    implementation("net.byteflux:libby-bukkit:1.3.0")
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.github.retrooper:packetevents-spigot:2.12.2")
    compileOnly("org.xerial:sqlite-jdbc:3.46.1.0")
    compileOnly("com.zaxxer:HikariCP:5.1.0")
    compileOnly("com.google.code.gson:gson:2.10.1")
    compileOnly("io.lettuce:lettuce-core:6.3.2.RELEASE")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    jar {
        archiveClassifier.set("plain")
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.21.11")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    shadowJar {
        archiveClassifier.set("")
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
