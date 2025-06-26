/**
 * Copyright (C) 2023 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.Exec
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsCompilerAttribute
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsSetupTask
import org.jetbrains.kotlin.gradle.targets.js.yarn.*

open class JsIntegrationGradlePluginExtension(project: Project, objects: ObjectFactory) {
    companion object {
        val NAME = "jsIntegration"
    }

    /**
     * directory where the Node.js code is perhaps "${project.projectDir}/src/node"
     * when this value is set, the node build tasks are created
     */
    val nodeSrcDirectoryDev = objects.directoryProperty()
    val nodeSrcDirectoryProd = objects.directoryProperty()

    /**
     * directory where built code is output default [${project.buildDir}/node]
     */
    val nodeOutDirectoryDev = objects.directoryProperty()
    val nodeOutDirectoryProd = objects.directoryProperty()

    /**
     * name of the configuration to use for finding depended on modules [default 'nodeModule']
     */
    val nodeModuleConfigurationName = objects.property(String::class.java)

    val nodeModulesDirectory = objects.directoryProperty()

    /**
     * name of the kotlin js target for this module [default 'js']
     */
    val jsTargetName = objects.property(String::class.java)

    /**
     * whether to use production build or not
     */
    val production = objects.property(Boolean::class.java)

    /**
     * map of commands to execute if production
     */
    val productionCommand = objects.mapProperty(String::class.java, String::class.java)

    /**
     * map of commands to execute if development
     */
    val developmentCommand = objects.mapProperty(String::class.java, String::class.java)

    init {
        // node build configuration
        this.nodeModuleConfigurationName.convention("nodeModule")
        this.nodeOutDirectoryDev.convention(project.layout.buildDirectory.dir("node"))
        this.nodeOutDirectoryProd.convention(project.layout.buildDirectory.dir("node"))
        this.nodeModulesDirectory.convention(this.nodeSrcDirectoryDev.map { it.dir("node_modules") })
        //this.kotlinStdlibJsDirectory.convention(this.nodeModulesDirectory.map { it.dir("kotlin") })
        //this.nodeBuildCommand.convention(listOf("run", "build", "--outputPath=${this.nodeOutDirectory.get()}/dist"))
        this.jsTargetName.convention("js")
        //this.overwrite.convention(true)
        //this.localOnly.convention(true)
        this.production.convention(false)
    }
}

open class JsIntegrationGradlePlugin : Plugin<ProjectInternal> {
    override fun apply(project: ProjectInternal) {
        project.pluginManager.apply(BasePlugin::class.java)

        val ext = project.extensions.create<JsIntegrationGradlePluginExtension>(JsIntegrationGradlePluginExtension.NAME, JsIntegrationGradlePluginExtension::class.java, project)
        val jsTargetName = ext.jsTargetName.get()

        val nodeKotlinConfig = project.configurations.create(ext.nodeModuleConfigurationName.get()) {
            //it.extendsFrom(project.configurations.getByName("${jsTargetName}MainImplementation"))
            it.attributes {
                it.attribute(KotlinPlatformType.attribute, KotlinPlatformType.js)
                it.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, KotlinUsages.KOTLIN_RUNTIME))
                it.attribute(KotlinJsCompilerAttribute.jsCompilerAttribute, KotlinJsCompilerAttribute.ir)
            }
        }

        project.gradle.projectsEvaluated {
            val prodCommands = ext.productionCommand.get().entries.associate { (k, v) -> Pair(k, v.split(Regex("\\s+")).toTypedArray()) }
            val devCommands = ext.developmentCommand.get().entries.associate { (k, v) -> Pair(k, v.split(Regex("\\s+")).toTypedArray()) }

            val kotlinYarnSetup = project.yarn.yarnSetupTaskProvider.get()
            kotlinYarnSetup.exec()
            val yarn = kotlinYarnSetup.destinationProvider.asFile.get().resolve("bin/yarn")

            val isProduction = ext.production.get()
            val compileTaskName = when (isProduction) {
                true -> "compileProductionLibraryKotlinJs"  //TODO: construct task name, JS name
                false -> "compileDevelopmentLibraryKotlinJs"
            }

            if (ext.nodeSrcDirectoryProd.isPresent && ext.nodeOutDirectoryProd.isPresent) {
                ext.nodeOutDirectoryProd.get().asFile.mkdirs()
                val nodeSrcDirProd = project.file(ext.nodeSrcDirectoryProd.get())
                val nodeOutDirProd = project.file(ext.nodeOutDirectoryProd.get())

                // use yarn to install the node_modules required by the node code
                val yarnInstallAllTaskProd = project.tasks.create("yarnInstallAllProd", Exec::class.java) { exec ->
                    exec.dependsOn(nodeKotlinConfig, compileTaskName)
                    exec.group = "nodejs"
                    //exec.dependsOn(NodeJsSetupTask.NAME, YarnSetupTask.NAME)
                    exec.workingDir = nodeSrcDirProd
                    val args = listOf(yarn, "add", "all", "--outputPath=${nodeOutDirProd}")
                    exec.commandLine(args)
                }


                val prodTaskDeps = mutableListOf<String>(":kotlinNodeJsSetup", "yarnInstallAllProd")
                prodCommands.entries.forEachIndexed { idx, (name, cmd) ->
                    val taskName = "${name}_jsIntegrationBuildProduction"
                    project.tasks.create(taskName, Exec::class.java) { exec ->
                        exec.group = "nodejs"
                        prodTaskDeps.forEach { exec.dependsOn(it) }
                        exec.workingDir = nodeSrcDirProd
                        project.logger.warn("Executing: $cmd")
                        exec.commandLine(yarn, *cmd)
                    }
                    prodTaskDeps.add(taskName)
                }

            } else {
                project.logger.error("nodeSrcDirectoryProd && nodeOutDirectoryProd must both be defined")
            }

            if (ext.nodeSrcDirectoryDev.isPresent && ext.nodeOutDirectoryDev.isPresent) {
                ext.nodeOutDirectoryDev.get().asFile.mkdirs()
                val nodeSrcDirDev = project.file(ext.nodeSrcDirectoryDev.get())
                val nodeOutDirDev = project.file(ext.nodeOutDirectoryDev.get())

                val yarnInstallAllTaskDev = project.tasks.create("yarnInstallAllDev", Exec::class.java) { exec ->
                    exec.dependsOn(nodeKotlinConfig, compileTaskName)
                    exec.group = "nodejs"
                    //exec.dependsOn(NodeJsSetupTask.NAME, YarnSetupTask.NAME)
                    exec.workingDir = nodeSrcDirDev
                    val args = listOf(yarn, "add", "all", "--outputPath=${nodeOutDirDev}")
                    exec.commandLine(args)
                }

                val devTaskDeps = mutableListOf<String>(":kotlinNodeJsSetup", "yarnInstallAllDev")
                devCommands.entries.forEachIndexed { idx, (name, cmd) ->
                    val taskName = "${name}_jsIntegrationBuildDevelopment"
                    project.tasks.create(taskName, Exec::class.java) { exec ->
                        exec.group = "nodejs"
                        devTaskDeps.forEach { exec.dependsOn(it) }
                        exec.workingDir = nodeSrcDirDev
                        project.logger.warn("Executing: $cmd")
                        exec.commandLine(yarn, *cmd)
                    }
                    devTaskDeps.add(taskName)
                }
            } else {
                project.logger.error("nodeSrcDirectoryDev && nodeOutDirectoryDev must both be defined")
            }
        }
    }

}