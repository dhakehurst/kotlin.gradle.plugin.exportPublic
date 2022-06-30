package net.akehurst.kotlin.gradle.plugin.exportPublic

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.isTopLevel
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.wasm.ir2wasm.allSuperInterfaces
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.defineProperty
import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.prototypeOf
import org.jetbrains.kotlin.ir.backend.js.utils.isJsExport
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.js.backend.ast.JsExpression
import org.jetbrains.kotlin.js.translate.utils.getReferenceToJsClass
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.TargetPlatform

class ExportPublicIrGenerationExtension(
    private val messageCollector: MessageCollector,
    private val exportPatterns: List<String>
) : IrGenerationExtension {

    private val exportPatternsRegex = exportPatterns.mapNotNull {
        if (it.isNullOrBlank()) {
            null
        } else {
            it.toRegexFromGlob('.')
        }
    }

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        messageCollector.report(CompilerMessageSeverity.INFO,"Exporting declarations that match one of: ${exportPatterns.map { "'$it'" }}")

        val jsExportSymbol = pluginContext.referenceConstructors(FqName("kotlin.js.JsExport")).first()

        val exportVisitor = object : IrElementTransformerVoid() {
            override fun visitClass(declaration: IrClass): IrStatement {
                return if (
                    declaration.isJsExport().not() &&
                    (declaration.visibility == DescriptorVisibilities.PUBLIC || implementsPublicInterface(declaration))&&
                    declaration.isExpect.not() &&
                    declaration.isExternal.not() &&
                    declaration.isValue.not() &&
                    declaration.isAnnotationClass.not() &&
                    declaration.isAnonymousObject.not() &&
                    declaration.isEnumClass.not() &&
                    noPatternsOrMatchesOn(declaration.kotlinFqName.asString(),exportPatternsRegex)
                ) {
                    val overloads = noOverloadedMethods(declaration)
                    return if (overloads.isEmpty()) {
                        val x = declaration.superTypes.joinToString(separator = "; ") { it.render() }
                        messageCollector.report(CompilerMessageSeverity.INFO, "Exporting ${declaration.kotlinFqName}")
                        val cc = DeclarationIrBuilder(pluginContext, declaration.symbol).irCallConstructor(jsExportSymbol, emptyList())
                        declaration.annotations += cc
                        super.visitClass(declaration)
                    } else {
                        //TODO: would like this to be an ERROR, but then it fails for JVM target,
                        // I can't see how to only apply the plugin for JS target
                        messageCollector.report(
                            CompilerMessageSeverity.STRONG_WARNING,
                            "Will not export ${declaration.kotlinFqName} due to overloaded methods ${overloads.keys} - which could cause a call ambiguity in JavaScript"
                        )
                        super.visitClass(declaration)
                    }
                } else {
                    super.visitClass(declaration)
                }
            }

            override fun visitFunction(declaration: IrFunction): IrStatement {
                return if (
                    declaration.isJsExport().not() &&
                    declaration.isTopLevel && // TODO: or isTopLevelDeclaration - what is the difference?
                    declaration.visibility == DescriptorVisibilities.PUBLIC &&
                    declaration.isExpect.not() &&
                    declaration.isExternal.not() &&
                    declaration.isInline.not() &&
                    declaration.isSuspend.not()
                ) {
                    val cc = DeclarationIrBuilder(pluginContext, declaration.symbol).irCallConstructor(jsExportSymbol, emptyList())
                    declaration.annotations = declaration.annotations + cc
                    super.visitFunction(declaration)
                } else {
                    super.visitFunction(declaration)
                }
            }

            override fun visitProperty(declaration: IrProperty): IrStatement {
                return if (
                    declaration.isJsExport().not() &&
                    declaration.isTopLevel && // TODO: or isTopLevelDeclaration - what is the difference?
                    declaration.visibility == DescriptorVisibilities.PUBLIC &&
                    declaration.isExpect.not() &&
                    declaration.isExternal.not()
                ) {
                    val cc = DeclarationIrBuilder(pluginContext, declaration.symbol).irCallConstructor(jsExportSymbol, emptyList())
                    declaration.annotations = declaration.annotations + cc
                    super.visitProperty(declaration)
                } else {
                    super.visitProperty(declaration)
                }
            }

        }
        moduleFragment.transform(exportVisitor, null)
    }

    private fun noPatternsOrMatchesOn(qName:String, exportPatternsRegex: List<Regex>): Boolean
        = exportPatternsRegex.isEmpty() || exportPatternsRegex.any { it.matches(qName) }

    private fun noOverloadedMethods(declaration: IrClass): Map<Name, List<IrSimpleFunction>> {
        val nameGroups = declaration.functions.groupBy { it.name }
        return nameGroups.filter { it.value.size != 1 }
    }

    private fun implementsPublicInterface(declaration: IrClass): Boolean {
        messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, "checking super interfaces of ${declaration.name}")
        return declaration.allSuperInterfaces().any {
            messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, "  checking ${it.name}")
            it.visibility == DescriptorVisibilities.PUBLIC
        }
    }
}