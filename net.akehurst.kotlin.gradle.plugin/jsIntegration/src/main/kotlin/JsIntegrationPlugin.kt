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
    val nodeSrcDirectory = objects.directoryProperty()

    val nodeBuildCommand = objects.listProperty(String::class.java)

    /**
     * directory where built code is output default [${project.buildDir}/node]
     */
    val nodeOutDirectory = objects.directoryProperty()

    /**
     * name of the configuration to use for finding depended on modules [default 'nodeModule']
     */
    val nodeModuleConfigurationName = objects.property(String::class.java)

    val nodeModulesDirectory = objects.directoryProperty()

    init {
        // node build configuration
        this.nodeModuleConfigurationName.convention("nodeModule")
        this.nodeOutDirectory.convention(project.layout.buildDirectory.dir("node"))
        this.nodeModulesDirectory.convention(this.nodeSrcDirectory.map { it.dir("node_modules") })
        //this.kotlinStdlibJsDirectory.convention(this.nodeModulesDirectory.map { it.dir("kotlin") })
        this.nodeBuildCommand.convention(listOf("run", "build", "--outputPath=${this.nodeOutDirectory.get()}/dist"))

        //this.overwrite.convention(true)
        //this.localOnly.convention(true)
    }
}

open class JsIntegrationGradlePlugin : Plugin<ProjectInternal> {
    override fun apply(project: ProjectInternal) {
        project.pluginManager.apply(BasePlugin::class.java)

        val ext = project.extensions.create<JsIntegrationGradlePluginExtension>(JsIntegrationGradlePluginExtension.NAME, JsIntegrationGradlePluginExtension::class.java, project)

        val nodeKotlinConfig = project.configurations.create(ext.nodeModuleConfigurationName.get()) {
            it.attributes {
                it.attribute(KotlinPlatformType.attribute, KotlinPlatformType.js)
                it.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, KotlinUsages.KOTLIN_RUNTIME))
            }
        }

        project.gradle.projectsEvaluated {
            if (ext.nodeSrcDirectory.isPresent) {
                val nodeSrcDir = project.file(ext.nodeSrcDirectory.get())
                val nodeOutDir = project.file(ext.nodeOutDirectory.get())
                val cmds = ext.nodeBuildCommand.get()
                val kotlinYarnSetup = project.yarn.yarnSetupTaskProvider.get()
                kotlinYarnSetup.setup()
                val yarn = kotlinYarnSetup.destination.resolve("bin/yarn")
                // use yarn to install the node_modules required by the node code
                project.tasks.create("yarnInstallAll", Exec::class.java) { exec ->
                    exec.group = "nodejs"
                    //exec.dependsOn(NodeJsSetupTask.NAME, YarnSetupTask.NAME)
                    exec.workingDir = nodeSrcDir
                    val args = listOf(yarn, "add", "all", "--outputPath=${nodeOutDir}")
                    exec.commandLine(args)
                }
                project.tasks.create(UnpackJsModulesTask.NAME, UnpackJsModulesTask::class.java) { tsk ->
                    tsk.dependsOn(nodeKotlinConfig, "yarnInstallAll")
                    //tsk.moduleNameMap.set(ext.moduleNameMap)
                    tsk.nodeModulesDirectory.set(ext.nodeModulesDirectory)
                    tsk.unpackConfigurationName.set(ext.nodeModuleConfigurationName)
                    //tsk.excludeModules.set(ext.excludeModules)
                }
            }
        }
    }

}