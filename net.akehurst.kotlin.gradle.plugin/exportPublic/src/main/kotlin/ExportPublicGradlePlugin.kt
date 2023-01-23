package net.akehurst.kotlin.gradle.plugin.exportPublic

import com.google.auto.service.AutoService
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.platform.base.Platform
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.*
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.konan.file.File

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

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean =
        kotlinCompilation.name == KotlinCompilation.MAIN_COMPILATION_NAME &&
                kotlinCompilation.target.platformType ==  KotlinPlatformType.js

    override fun apply(target: Project) {
        val ext = target.extensions.create(ExportPublicGradlePluginExtension.NAME, ExportPublicGradlePluginExtension::class.java)
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val extension = project.extensions.getByType(ExportPublicGradlePluginExtension::class.java)
        return project.provider {
            listOf(
                SubpluginOption(key = ExportPublicCommandLineProcessor.OPTION_exportPatterns, value = extension.exportPatterns.get().joinToString(separator = File.pathSeparator))
            )
        }
        //return project.provider { emptyList() }
    }
}

open class ExportPublicGradlePluginExtension(objects: ObjectFactory) {

    companion object {
        val NAME = "exportPublic"
    }

    val exportPatterns = objects.listProperty(String::class.java)

}

@AutoService(CommandLineProcessor::class)
class ExportPublicCommandLineProcessor : CommandLineProcessor {
    companion object {
         const val OPTION_exportPatterns = "exportPatterns"
        val ARG_exportPatterns = CompilerConfigurationKey<List<String>>(OPTION_exportPatterns)
    }

    override val pluginId: String = KotlinPluginInfo.KOTLIN_PLUGIN_ID

    override val pluginOptions: Collection<CliOption> = listOf(
        CliOption(
            optionName = OPTION_exportPatterns,
            valueDescription = "List<Glob-Pattern-String>",
            description = "items with qualified name that match the pattern will be exported",
            required = false,
        )
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration
    ) {
        return when (option.optionName) {
            OPTION_exportPatterns -> configuration.put(ARG_exportPatterns, value.split(File.pathSeparator).toList())
            else -> throw IllegalArgumentException("Unexpected config option ${option.optionName}")
        }
    }
}

@AutoService(CompilerPluginRegistrar::class)
class KotlinxReflectComponentRegistrar(
    private val defaultExportPatterns: List<String>
) : CompilerPluginRegistrar() {

    @Suppress("unused") // Used by service loader
    constructor() : this(
        defaultExportPatterns = emptyList()
    )

    override val supportsK2: Boolean get() = TODO("not implemented")

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val messageCollector = configuration[CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY]!!//, MessageCollector.NONE)
        val exportPatterns = configuration.get(ExportPublicCommandLineProcessor.ARG_exportPatterns, defaultExportPatterns).filter { it.isNotBlank() }

        IrGenerationExtension.registerExtension(ExportPublicIrGenerationExtension(messageCollector,exportPatterns))

        //val enumToSealedClass = EnumToSealedClassIrGenerationExtension(messageCollector)
        //IrGenerationExtension.registerExtension(project, enumToSealedClass)

       // IrGenerationExtension.registerExtension(project, EnumUseToSealedClassUseIrGenerationExtension(messageCollector, enumToSealedClass.enumToSealedClassMap, enumToSealedClass.enumEntryToSealedClassObjectMap))
        //IrGenerationExtension.registerExtension(project, EnumUseToSealedClassUseIrGenerationExtension(messageCollector, mapOf(), mapOf()))
    }

}
