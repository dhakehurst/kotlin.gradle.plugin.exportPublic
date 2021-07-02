package net.akehurst.kotlin.gradle.plugin.exportPublic

import com.google.auto.service.AutoService
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class ExportPublicGradlePlugin : KotlinCompilerPluginSupportPlugin {

    override fun getCompilerPluginId(): String = KotlinPluginInfo.KOTLIN_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = KotlinPluginInfo.PROJECT_GROUP,
        artifactId = KotlinPluginInfo.PROJECT_NAME,
        version = KotlinPluginInfo.PROJECT_VERSION
    )

    override fun getPluginArtifactForNative(): SubpluginArtifact = SubpluginArtifact(
        groupId = KotlinPluginInfo.PROJECT_GROUP,
        artifactId = KotlinPluginInfo.PROJECT_NAME+"-native",
        version = KotlinPluginInfo.PROJECT_VERSION
    )

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun apply(target: Project) {
        val ext = target.extensions.create(ExportPublicGradlePluginExtension.NAME, ExportPublicGradlePluginExtension::class.java)
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val extension = project.extensions.getByType(ExportPublicGradlePluginExtension::class.java)
        //return project.provider {
        //    extension.forReflection.get().map {
        //        SubpluginOption(key = "forReflection", value = it)
        //    }
        //}
        return project.provider { emptyList() }
    }
}

open class ExportPublicGradlePluginExtension(objects: ObjectFactory) {

    companion object {
        val NAME = "exportPublic"
    }

}

@AutoService(CommandLineProcessor::class)
class ExportPublicCommandLineProcessor : CommandLineProcessor {
    companion object {
        //private const val OPTION_forReflection = "forReflection"
        //val ARG_forReflection = CompilerConfigurationKey<String>(OPTION_forReflection)
    }

    override val pluginId: String = KotlinPluginInfo.KOTLIN_PLUGIN_ID

    override val pluginOptions: Collection<CliOption> = listOf(
        //CliOption(
        //    optionName = OPTION_forReflection,
        //    valueDescription = "string",
        //    description = "list of libraries for reflection access",
        //    required = false,
        //)
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration
    ) {
        return when (option.optionName) {
            //OPTION_forReflection -> configuration.put(ARG_forReflection, value)
            else -> throw IllegalArgumentException("Unexpected config option ${option.optionName}")
        }
    }
}

@AutoService(ComponentRegistrar::class)
class KotlinxReflectComponentRegistrar(
    private val defaultReflectionLibs: String
) : ComponentRegistrar {

    @Suppress("unused") // Used by service loader
    constructor() : this(
        defaultReflectionLibs = ""
    )

    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        val messageCollector = configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)

        IrGenerationExtension.registerExtension(project, ExportPublicIrGenerationExtension(messageCollector))

        //val enumToSealedClass = EnumToSealedClassIrGenerationExtension(messageCollector)
        //IrGenerationExtension.registerExtension(project, enumToSealedClass)

       // IrGenerationExtension.registerExtension(project, EnumUseToSealedClassUseIrGenerationExtension(messageCollector, enumToSealedClass.enumToSealedClassMap, enumToSealedClass.enumEntryToSealedClassObjectMap))
        //IrGenerationExtension.registerExtension(project, EnumUseToSealedClassUseIrGenerationExtension(messageCollector, mapOf(), mapOf()))
    }

}
