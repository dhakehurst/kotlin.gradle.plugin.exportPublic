import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.ZoneId

plugins {
    alias(libs.plugins.kotlin.jvm) apply true
    alias(libs.plugins.dokka) apply true
    alias(libs.plugins.buildconfig) apply true
    alias(libs.plugins.credentials) apply true
    `java-gradle-plugin`
    `maven-publish`
    signing
    id("com.gradle.plugin-publish") version "1.1.0"
}

val kotlin_languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1
val kotlin_apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1
val jvmTargetVersion = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
buildConfig {
    val now = Instant.now()
    fun fBbuildStamp(): String = DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.of("UTC")).format(now)
    fun fBuildDate(): String = DateTimeFormatter.ofPattern("yyyy-MMM-dd").withZone(ZoneId.of("UTC")).format(now)
    fun fBuildTime(): String = DateTimeFormatter.ofPattern("HH:mm:ss z").withZone(ZoneId.of("UTC")).format(now)

    buildConfigField("String", "version", "\"${project.version}\"")
    buildConfigField("String", "buildStamp", "\"${fBbuildStamp()}\"")
    buildConfigField("String", "buildDate", "\"${fBuildDate()}\"")
    buildConfigField("String", "buildTime", "\"${fBuildTime()}\"")
}

dependencies {
    implementation(kotlin("gradle-plugin-api"))
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlinx.serialization.json)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions>>().configureEach {
    compilerOptions {
        languageVersion.set(kotlin_languageVersion)
        apiVersion.set(kotlin_apiVersion)
        jvmTarget.set(jvmTargetVersion)
        //freeCompilerArgs.add("-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    }
}

gradlePlugin {
    website.set("https://github.com/dhakehurst/kotlin.gradle.plugin.exportPublic")
    vcsUrl.set("https://github.com/dhakehurst/kotlin.gradle.plugin.exportPublic")
    plugins {
        create(project.name) {
            id = "${project.group}.${project.name}"
            implementationClass = "${project.group}.${project.name}.JsIntegrationGradlePlugin"
            displayName = project.name
            description = "Gradle plugin enable use of Kotlin's node/yarn infrastructure for building "
            tags.set(listOf("kotlin", "javascript", "typescript", "kotlin-js", "kotlin-multiplatform", "node", "yarn"))
        }
    }
}

configure<SigningExtension> {
    useGpgCmd()
    val publishing = project.properties["publishing"] as PublishingExtension
    sign(publishing.publications)
}