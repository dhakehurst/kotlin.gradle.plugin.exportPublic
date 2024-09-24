import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile

plugins {
    kotlin("multiplatform")
    id("net.akehurst.kotlin.gradle.plugin.exportPublic") version("2.0.20")
}
val kotlin_languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0
val kotlin_apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0
val jvmTargetVersion = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8

kotlin {
    jvm("jvm8") {
        val main by compilations.getting {
            compilerOptions.configure {
                languageVersion.set(kotlin_languageVersion)
                apiVersion.set(kotlin_apiVersion)
                jvmTarget.set(jvmTargetVersion)
            }
        }
        val test by compilations.getting {
            compilerOptions.configure {
                languageVersion.set(kotlin_languageVersion)
                apiVersion.set(kotlin_apiVersion)
                jvmTarget.set(jvmTargetVersion)
            }
        }
    }
    js("js") {
        binaries.library()
        generateTypeScriptDefinitions()
        compilerOptions {
            target.set("es2015")
        }
        nodejs {}
        browser {}
    }
}

dependencies {
    "commonTestImplementation"(kotlin("test"))
    "commonTestImplementation"(kotlin("test-annotations-common"))
}

tasks.withType<PublishToMavenLocal> {
    onlyIf { false }
}