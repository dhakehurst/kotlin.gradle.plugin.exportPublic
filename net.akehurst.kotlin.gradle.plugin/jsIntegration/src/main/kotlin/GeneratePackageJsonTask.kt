/**
 * Copyright (C) 2018 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.akehurst.kotlin.gradle.plugin.jsIntegration

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import org.slf4j.LoggerFactory
import java.io.File


open class GeneratePackageJsonTask : DefaultTask() {

    companion object {
        val NAME = "generatePackageJsonWithTypes"
        private val LOGGER = LoggerFactory.getLogger(GeneratePackageJsonTask::class.java)

        fun readOrCreatePackageJson(file: File, jsModuleName: String, _moduleVersion: String, mainFileName: String): JsonObject {
            val json = Json
            return if (file.exists()) {
                json.parseToJsonElement(file.readText()).jsonObject
            } else {
                //error("package.json file not found, should have been created by kotlin compiler")
                file.parentFile.mkdirs()
                file.printWriter().use { out ->
                    out.println(
                        """
                        {
                            "name": "${jsModuleName}",
                            "version": "${_moduleVersion}",
                            "main": "./${mainFileName}"
                        }
                        """.trimIndent()
                    )
                }
                json.parseToJsonElement(file.readText()).jsonObject
            }
        }
    }

    @get:OutputDirectory
    val packageJsonDir: DirectoryProperty = project.objects.directoryProperty()

    @get:Input
    @get:Optional
    val moduleGAV: Property<String> = project.objects.property(String::class.java)

    @get:Input
    @get:Optional
    val moduleName: Property<String> = project.objects.property(String::class.java)

    @get:Input
    @get:Optional
    val moduleGroup: Property<String> = project.objects.property(String::class.java)

    @get:Input
    @get:Optional
    val moduleVersion: Property<String> = project.objects.property(String::class.java)

    @get:Input
    val mainFileName: Property<String> = project.objects.property(String::class.java)

    init {
        this.group = "generate"
        this.description = "Generate package.json file that defines 'types', or modify existing package.json file to add it"
        this.moduleGAV.convention("${project.group}:${project.name}:${project.version}")
        this.moduleGroup.convention(moduleGAV.map { it.split(':')[0] })
        this.moduleName.convention(moduleGAV.map { it.split(':')[1] })
        this.moduleVersion.convention(moduleGAV.map { it.split(':')[2] })
        val defaultMainFileName = if (moduleName.get().endsWith("-js")) {
            "${moduleGroup.get()}-${moduleName.get().substringBeforeLast("-js")}.js"
        } else {
            "${moduleGroup.get()}-${moduleName.get()}.js"
        }
        this.mainFileName.convention(defaultMainFileName)
    }

    @TaskAction
    internal fun exec() {
        val _packageJsonFile = packageJsonDir.get().file("package.json").asFile
        val _moduleGroup = moduleGroup.get()
        val _moduleName = moduleName.get()
        val _moduleVersion = moduleVersion.get()

        val jsModuleId = if (moduleName.get().endsWith("-js")) {
            "${moduleGroup.get()}-${moduleName.get().substringBeforeLast("-js")}"
        } else {
            "${moduleGroup.get()}-${moduleName.get()}"
        }
        val _mainFileName = if (mainFileName.isPresent) {
            mainFileName.get()
        } else {
            "$jsModuleId.js"
        }
        LOGGER.info("Trying to update existing file $_packageJsonFile")
        val json = readOrCreatePackageJson(_packageJsonFile, jsModuleId, _moduleVersion, _mainFileName)
        val mutable = mutableMapOf<String, JsonElement>()
        mutable.putAll(json)
        //mutable["types"] = JsonLiteral("./${_moduleGroup}-${_moduleName}.d.ts")
        val newJson = Json {
            buildJsonObject {
               mutable.forEach{
                   put(it.key, it.value)
               }
                put("types", "./${_moduleGroup}-${_moduleName}.d.ts")
            }
        }
        _packageJsonFile.printWriter().use { out ->
            out.println(Json.encodeToString(newJson))
        }
    }

}
