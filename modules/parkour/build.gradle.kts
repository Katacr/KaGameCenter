plugins {
    kotlin("jvm") version "2.3.20"
}

repositories {
    mavenCentral()
    maven("https://maven.aliyun.com/repository/public/")
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(rootProject)
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    jar {
        archiveFileName.set("parkour.jar")
    }
}
