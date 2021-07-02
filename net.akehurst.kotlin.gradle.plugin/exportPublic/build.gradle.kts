plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "0.15.0"
    kotlin("jvm")
    kotlin("kapt")
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

gradlePlugin {
    plugins {
        create(project.name) {
            id = "${project.group}.${project.name}"
            implementationClass = "${project.group}.${project.name}.ExportPublicGradlePlugin"
        }
    }
}
pluginBundle {
    website = "https://github.com/dhakehurst/kotlin.gradle.plugin.exportPublic"
    vcsUrl = "https://github.com/dhakehurst/kotlin.gradle.plugin.exportPublic"
    description = "Kotlin compiler plugin to 'JsExport' all public declarations"
    tags = listOf("JsExport", "kotlin", "javascript", "typescript", "kotlin-js", "kotlin-multiplatform")

    plugins {
        this.getByName(project.name) {
            // id is captured from java-gradle-plugin configuration
            displayName = project.name
        }
    }
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable")
    implementation(kotlin("gradle-plugin-api"))
    compileOnly("com.google.auto.service:auto-service:1.0-rc7")
    kapt("com.google.auto.service:auto-service:1.0-rc7")
}