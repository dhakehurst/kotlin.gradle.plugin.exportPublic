package net.akehurst.kotlin.gradle.plugin.exportPublic

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.addFakeOverrides
import org.jetbrains.kotlin.backend.common.ir.addSimpleDelegatingConstructor
import org.jetbrains.kotlin.backend.common.ir.createImplicitParameterDeclarationWithWrappedDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFactory
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.name.Name

fun IrFactory.buildObject(
    pluginContext: IrPluginContext,
    parent: IrDeclarationParent,
    name: Name,
    superTypes: List<IrType>,
    superConstructor: IrConstructor,
    visibility: DescriptorVisibility
): IrClass = this.buildClass {
    this.kind = ClassKind.OBJECT
    this.name = name
}.apply {
    this.parent = parent
    this.superTypes = superTypes
    this.createImplicitParameterDeclarationWithWrappedDescriptor()
    this.addSimpleDelegatingConstructor(
        superConstructor = superConstructor,
        irBuiltIns = pluginContext.irBuiltIns,
        isPrimary = true
    ).apply {
        this.visibility = visibility
    }
    this.addFakeOverrides(pluginContext.irBuiltIns)
}