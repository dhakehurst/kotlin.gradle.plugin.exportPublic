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
import org.jetbrains.kotlin.ir.util.isSuspend
import org.jetbrains.kotlin.ir.util.isTopLevelDeclaration
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.FqName

class ExportPublicIrGenerationExtension(
    private val messageCollector: MessageCollector
) : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val jsExportSymbol = pluginContext.referenceConstructors(FqName("kotlin.js.JsExport")).first()

        val enumToSealedClass = object : IrElementTransformerVoid() {
            override fun visitClass(declaration: IrClass): IrStatement {
                messageCollector.report(CompilerMessageSeverity.STRONG_WARNING,"look at ${declaration.name}")
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
                            messageCollector.report(CompilerMessageSeverity.STRONG_WARNING,"Exporting ${declaration.name}")
                            val cc = DeclarationIrBuilder(pluginContext, declaration.symbol).irCallConstructor(jsExportSymbol, emptyList())
                            declaration.annotations += cc
                            super.visitClass(declaration)
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
                    //val cc = DeclarationIrBuilder(pluginContext, declaration.symbol).irCallConstructor(jsExportSymbol, emptyList())
                   // declaration.annotations = declaration.annotations + cc
                    super.visitFunction(declaration)
                } else {
                    super.visitFunction(declaration)
                }
            }

        }
        moduleFragment.transform(enumToSealedClass, null)
    }

}