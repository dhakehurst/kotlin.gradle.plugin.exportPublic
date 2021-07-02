package net.akehurst.kotlin.gradle.plugin.exportPublic

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irGetObjectValue
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

class EnumUseToSealedClassUseIrGenerationExtension(
    private val messageCollector: MessageCollector,
    private val enumToSealedClassMap: Map<IrClass, IrClass>,
    private val enumEntryToSealedClassObjectMap: Map<IrEnumEntry, IrClass>
) : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {

        //transform usage of enum entries into usage of sealed class member objects
        val enumUseToSealedClassUse = object : IrElementTransformerVoid() {

            override fun visitFunction(declaration: IrFunction): IrStatement {
                messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, "expression ${declaration.dump()}")
                return super.visitFunction(declaration)
            }

            override fun visitClassReference(expression: IrClassReference): IrExpression {
                val cls = expression.classType.getClass()
                return if (null != cls && enumToSealedClassMap.containsKey(cls)) {
                    val replc = enumToSealedClassMap[cls]

                    val b = DeclarationIrBuilder(pluginContext, expression.symbol)
//TODO
                    //messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, "replace class ref with ${expression.dump()}")
                    super.visitClassReference(expression)
                } else {
                    super.visitClassReference(expression)
                }
            }

            override fun visitGetEnumValue(expression: IrGetEnumValue): IrExpression {
                messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, "getEnum ${expression.dump()}")

                val enumEntry = expression.symbol.owner
                return if (enumEntryToSealedClassObjectMap.contains(enumEntry)) {
                    val b = DeclarationIrBuilder(pluginContext, expression.symbol)
                    val obj = enumEntryToSealedClassObjectMap[enumEntry]!!
                    val objSymbol = obj.symbol
                    val getObj = b.irGetObjectValue(obj.typeWith(), objSymbol)
                    messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, "replace getEnum with ${getObj.dump()}")
                    getObj
                } else {
                    super.visitGetEnumValue(expression)
                }
            }
        }
        moduleFragment.transform(enumUseToSealedClassUse, null)

    }

}