package net.akehurst.kotlin.gradle.plugin.exportPublic

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.addChild
import org.jetbrains.kotlin.backend.common.ir.addFakeOverrides
import org.jetbrains.kotlin.backend.common.ir.addSimpleDelegatingConstructor
import org.jetbrains.kotlin.backend.common.ir.createImplicitParameterDeclarationWithWrappedDescriptor
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.FqName

class EnumToSealedClassIrGenerationExtension(
    private val messageCollector: MessageCollector
) : IrGenerationExtension {

    val enumToSealedClassMap = mutableMapOf<IrClass, IrClass>()
    val enumEntryToSealedClassObjectMap = mutableMapOf<IrEnumEntry, IrClass>()

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {

        // transform enums to sealed classes
        val enumToSealedClass = object : IrElementTransformerVoid() {
            override fun visitClass(declaration: IrClass): IrStatement {
                //TODO: remove this it is just for debug
                if (declaration.modality == Modality.SEALED) {
                    messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, declaration.dump())
                    messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, declaration.dumpKotlinLike())
                }

                return if (declaration.visibility.isPublicAPI) {
                    val jsExportSymbol = pluginContext.referenceConstructors(FqName("kotlin.js.JsExport")).first()
                    return when (declaration.kind) {
                        ClassKind.ENUM_CLASS -> {
                            //TODO: change to info
                            messageCollector.report(
                                CompilerMessageSeverity.STRONG_WARNING,
                                "Replacing ${declaration.name} with sealed class for export as enums cannot be exported"
                            )
                            //replace it with a sealed class
                            val replacement = createNewSealedClass(pluginContext, jsExportSymbol, declaration)
                            enumToSealedClassMap[declaration] = replacement
                            //TODO: remove this it is just for debug
                            messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, replacement.dump())
                            messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, replacement.dumpKotlinLike())
                            replacement
                        }
                        else -> super.visitClass(declaration)
                    }
                } else {
                    super.visitClass(declaration)
                }
            }
        }
        moduleFragment.transform(enumToSealedClass, null)
    }

    fun createNewSealedClass(pluginContext: IrPluginContext, jsExportSymbol: IrConstructorSymbol, originalDeclaration: IrClass) = pluginContext.irFactory.buildClass {
        this.modality = Modality.SEALED
        this.kind = ClassKind.CLASS
        this.name = originalDeclaration.name
    }.apply {
        val sealedClass = this
        this.parent = originalDeclaration.parent
        this.superTypes = listOf(pluginContext.symbols.any.typeWith())
        this.annotations += DeclarationIrBuilder(pluginContext, sealedClass.symbol).irCallConstructor(jsExportSymbol, emptyList())
        this.createImplicitParameterDeclarationWithWrappedDescriptor()
        this.addSimpleDelegatingConstructor(
            superConstructor = pluginContext.irBuiltIns.anyClass.owner.constructors.single(),
            irBuiltIns = pluginContext.irBuiltIns,
            isPrimary = true
        ).apply {
            this.visibility = DescriptorVisibilities.PROTECTED
        }
        messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, "declarations ${originalDeclaration.declarations}")
        messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, "suypertype ${sealedClass.typeWith()}")
        originalDeclaration.declarations.filter { it is IrEnumEntry }.forEach {
            val enumEntry = it as IrEnumEntry
            messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, "transforming ${enumEntry.name}")
            val obj = pluginContext.irFactory.buildObject(
                pluginContext = pluginContext,
                parent = sealedClass,
                name = enumEntry.name,
                superTypes = listOf(sealedClass.typeWith()),
                superConstructor = sealedClass.constructors.single(),
                visibility = DescriptorVisibilities.PRIVATE
            )
            sealedClass.addChild(obj)
            enumEntryToSealedClassObjectMap[enumEntry] = obj
        }
        this.addFakeOverrides(pluginContext.irBuiltIns)
    }


}