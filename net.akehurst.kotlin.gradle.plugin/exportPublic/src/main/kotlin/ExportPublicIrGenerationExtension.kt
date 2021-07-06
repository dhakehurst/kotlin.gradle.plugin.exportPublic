package net.akehurst.kotlin.gradle.plugin.exportPublic

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.isTopLevel
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isSuspend
import org.jetbrains.kotlin.ir.util.isTopLevelDeclaration
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class ExportPublicIrGenerationExtension(
    private val messageCollector: MessageCollector
) : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val jsExportSymbol = pluginContext.referenceConstructors(FqName("kotlin.js.JsExport")).first()

        val enumToSealedClass = object : IrElementTransformerVoid() {
            override fun visitClass(declaration: IrClass): IrStatement {
                return if (
                    declaration.visibility == DescriptorVisibilities.PUBLIC &&
                    declaration.isExpect.not()&&
                    declaration.isExternal.not()&&
                    declaration.isInline.not()
                ) {
                    return when (declaration.kind) {
                        ClassKind.ANNOTATION_CLASS -> super.visitClass(declaration) //cannot export enums
                        ClassKind.ENUM_CLASS -> super.visitClass(declaration) //cannot export enums
                        else -> {
                            val overloads = noOverloadedMethods(declaration)
                            if (overloads.isEmpty()) {
                                messageCollector.report(CompilerMessageSeverity.INFO, "Exporting ${declaration.fqNameWhenAvailable}")
                                val cc = DeclarationIrBuilder(pluginContext, declaration.symbol).irCallConstructor(jsExportSymbol, emptyList())
                                declaration.annotations += cc
                                super.visitClass(declaration)
                            } else {
                                messageCollector.report(CompilerMessageSeverity.ERROR, "Will not export ${declaration.fqNameWhenAvailable} due to overloaded methods ${overloads.keys} - which could cause a call ambiguity in JavaScript")
                                super.visitClass(declaration)
                            }
                        }
                    }
                } else {
                    super.visitClass(declaration)
                }
            }

            override fun visitFunction(declaration: IrFunction): IrStatement {
                return if (
                    declaration.isTopLevel && // TODO: or isTopLevelDeclaration - what is the difference?
                    declaration.visibility== DescriptorVisibilities.PUBLIC &&
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

        }
        moduleFragment.transform(enumToSealedClass, null)
    }

    private fun noOverloadedMethods(declaration: IrClass): Map<Name, List<IrSimpleFunction>> {
        val nameGroups = declaration.functions.groupBy { it.name }
        return nameGroups.filter { it.value.size!=1 }
    }

}