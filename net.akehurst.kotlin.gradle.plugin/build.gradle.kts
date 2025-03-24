/**
 * Copyright (C) 2021 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.buildconfig) apply false
    alias(libs.plugins.credentials) apply true
    alias(libs.plugins.kotlin.kapt) apply false
}

fun getProjectProperty(s: String) = project.findProperty(s) as String?

allprojects {

    repositories {
        mavenCentral()
        mavenLocal {
            content{
                includeGroupByRegex("net\\.akehurst.+")
            }
        }
    }

    group = rootProject.name
    version = getProjectProperty("PUB_VERSION") ?: rootProject.libs.versions.project.get()

    project.layout.buildDirectory = File(rootProject.projectDir, ".gradle-build/${project.name}")
}


subprojects {

    apply(plugin = "maven-publish")

    val creds = project.properties["credentials"] as nu.studer.gradle.credentials.domain.CredentialsContainer
    configure<PublishingExtension> {
        repositories {
                maven {
                    name = "Other"
                    setUrl(getProjectProperty("PUB_URL")?: "<use -P PUB_URL=<...> to set>")
                    credentials {
                        username = getProjectProperty("PUB_USERNAME")
                            ?: error("Must set project property with Username (-P PUB_USERNAME=<...> or set in ~/.gradle/gradle.properties)")
                        password = getProjectProperty("PUB_PASSWORD")?: creds.forKey(getProjectProperty("PUB_USERNAME"))
                    }
                }
        }

    }
}