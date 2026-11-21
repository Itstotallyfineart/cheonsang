import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("net.fabricmc.fabric-loom") version "1.19-SNAPSHOT"
    kotlin("jvm") version "2.4.21"
}

version = providers.gradleProperty("version").get()
group = providers.gradleProperty("group").get()

repositories {
    maven("https://api.modrinth.com/maven") { name = "Modrinth" }
}

loom {
    splitEnvironmentSourceSets()
    mods {
        register("cheonsang") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets.getByName("client"))
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    // 개발 실행용: 모드 목록 화면
    compileOnly("maven.modrinth:modmenu:${property("modmenu_version")}")
    runtimeOnly("maven.modrinth:modmenu:${property("modmenu_version")}")
    // 2.0.3-dev4: 설정 화면 라이브러리 Cloth Config (LGPL-3.0) — jar 안에 같이 넣음 (사용자가 따로 안 깔아도 됨)
    implementation("maven.modrinth:cloth-config:${property("cloth_version")}")
    include("maven.modrinth:cloth-config:${property("cloth_version")}")
}

tasks.processResources {
    val v = project.version
    inputs.property("version", v)
    filesMatching("fabric.mod.json") { expand("version" to v) }
}

tasks.withType<JavaCompile>().configureEach { options.release = 25 }

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_25 }
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}
