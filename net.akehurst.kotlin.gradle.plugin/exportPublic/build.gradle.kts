// *************************************************
//
// NOTE: This is a kotlin compiler plugin and needs
// to be published to a maven repo as well as
// the gradle plugin portal (i.e. mavenCentral)
//
// *************************************************

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.ZoneId

plugins {
    alias(libs.plugins.kotlin.jvm) apply true
    alias(libs.plugins.dokka) apply true
    alias(libs.plugins.buildconfig) apply true
    `java-gradle-plugin`
    `maven-publish`
    signing
    id("com.gradle.plugin-publish") version "1.3.0"
    alias(libs.plugins.kotlin.kapt) apply true
}

val kotlin_languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2
val kotlin_apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2
val jvmTargetVersion = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable")
    implementation(kotlin("gradle-plugin-api"))
    compileOnly("com.google.auto.service:auto-service:1.1.1")
    kapt("com.google.auto.service:auto-service:1.1.1")
    "implementation"(kotlin("test-junit"))
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
buildConfig {
    //val project = project(":kotlinx-gradle-plugin")
    packageName("${project.group}.${project.name}")
    className("KotlinPluginInfo")
    buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${project.group}.${project.name}\"")
    buildConfigField("String", "PROJECT_GROUP", "\"${project.group}\"")
    buildConfigField("String", "PROJECT_NAME", "\"${project.name}\"")
    buildConfigField("String", "PROJECT_VERSION", "\"${project.version}\"")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions>>().configureEach {
    compilerOptions {
        languageVersion.set(kotlin_languageVersion)
        apiVersion.set(kotlin_apiVersion)
        jvmTarget.set(jvmTargetVersion)
        freeCompilerArgs.add("-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    }
}

gradlePlugin {
    website.set("https://github.com/dhakehurst/kotlin.gradle.plugin.exportPublic")
    vcsUrl.set("https://github.com/dhakehurst/kotlin.gradle.plugin.exportPublic")
    plugins {
        create(project.name) {
            id = "${project.group}.${project.name}"
            implementationClass = "${project.group}.${project.name}.ExportPublicGradlePlugin"
            displayName = project.name
            description = "Kotlin compiler plugin to 'JsExport' all public declarations"
            tags.set(listOf("JsExport", "kotlin", "javascript", "typescript", "kotlin-js", "kotlin-multiplatform"))
        }
    }
}

fun getProjectProperty(s: String) = project.findProperty(s) as String?
val sonatype_pwd =  getProjectProperty("SONATYPE_PASSWORD")
    ?: error("Must set project property with Sonatype Password (-P SONATYPE_PASSWORD=<...> or set in ~/.gradle/gradle.properties)")
project.ext.set("signing.password", sonatype_pwd)

configure<PublishingExtension> {
    repositories {
        maven {
            name = "sonatype"
            setUrl("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = getProjectProperty("SONATYPE_USERNAME")
                    ?: error("Must set project property with Sonatype Username (-P SONATYPE_USERNAME=<...> or set in ~/.gradle/gradle.properties)")
                password = sonatype_pwd
            }
        }
//        maven {
//            name = "Other"
//            setUrl(getProjectProperty("PUB_URL") ?: "<use -P PUB_URL=<...> to set>")
//            credentials {
//                username = getProjectProperty("PUB_USERNAME") ?: error("Must set project property with Username (-P PUB_USERNAME=<...> or set in ~/.gradle/gradle.properties)")
//                password = getProjectProperty("PUB_PASSWORD") ?: error("Must set project property with Password (-P PUB_PASSWORD=<...> or set in ~/.gradle/gradle.properties)")
//            }
//        }
    }
    publications.withType<MavenPublication> {
        pom {
            name.set("Export Public Declarations")
            description.set("Kotlin compiler plugin to export all public declarations")
            url.set("https://github.com/dhakehurst/kotlin.gradle.plugin.exportPublic")

            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    name.set("Dr. David H. Akehurst")
                    email.set("dr.david.h@akehurst.net")
                }
            }
            scm {
                url.set("https://github.com/dhakehurst/kotlin.gradle.plugin.exportPublic")
            }
        }
    }
}

configure<SigningExtension> {
    useGpgCmd()
    val publishing = project.properties["publishing"] as PublishingExtension
    sign(publishing.publications)
}


tasks.named {
    it=="publishExportPublicPluginMarkerMavenPublicationToSonatypeRepository"
}.configureEach {
    dependsOn("signPluginMavenPublication", "signExportPublicPluginMarkerMavenPublication")
}
tasks.named {
    it=="publishPluginMavenPublicationToSonatypeRepository"
}.configureEach {
    dependsOn( "signExportPublicPluginMarkerMavenPublication")
}