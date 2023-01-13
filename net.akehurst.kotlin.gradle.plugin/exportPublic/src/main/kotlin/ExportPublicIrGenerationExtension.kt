package net.akehurst.kotlin.gradle.plugin.exportPublic

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.jvm.codegen.anyTypeArgument
import org.jetbrains.kotlin.backend.wasm.ir2wasm.allSuperInterfaces
import org.jetbrains.kotlin.backend.wasm.ir2wasm.getRuntimeClass
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.utils.asString
import org.jetbrains.kotlin.ir.backend.js.utils.isJsExport
import org.jetbrains.kotlin.ir.backend.js.utils.realOverrideTarget
import org.jetbrains.kotlin.ir.backend.jvm.serialization.JvmIrMangler.signatureString
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.types.impl.IrCapturedType
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.checker.SimpleClassicTypeSystemContext.argumentsCount
import org.jetbrains.kotlin.types.checker.SimpleClassicTypeSystemContext.getArguments
import org.jetbrains.kotlin.types.checker.SimpleClassicTypeSystemContext.isCapturedType
import org.jetbrains.kotlin.types.checker.SimpleClassicTypeSystemContext.isStarProjection


class ExportPublicIrGenerationExtension(
    private val messageCollector: MessageCollector,
    private val exportPatterns: List<String>
) : IrGenerationExtension {

    companion object {
        val javaPackageFqn = FqName.fromSegments(listOf("java"))
        val kotlinCollectionsPackageFqn = FqName.fromSegments(listOf("kotlin", "collections"))
        val kotlinReflectPackageFqn = FqName.fromSegments(listOf("kotlin", "reflect"))
    }

    private val exportPatternsRegex = exportPatterns.mapNotNull {
        if (it.isNullOrBlank()) {
            null
        } else {
            it.toRegexFromGlob('.')
        }
    }

    private val exportableCache = mutableMapOf<Any, Boolean>()
    private lateinit var irBuiltIns: IrBuiltIns

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        irBuiltIns = pluginContext.irBuiltIns

        messageCollector.report(CompilerMessageSeverity.INFO, "Exporting declarations that match one of: ${exportPatterns.map { "'$it'" }}")

        val jsExportSymbol = pluginContext.referenceConstructors(ClassId(FqName("kotlin.js"), Name.identifier("JsExport"))).first()
        //messageCollector.report(CompilerMessageSeverity.INFO, "JsExport class: ${jsExportSymbol.signature}")

        val exportVisitor = object : IrElementTransformerVoid() {
            private fun checkType(declaration: IrClass, list: List<IrType>, message: String): Boolean {
                return if (list.isEmpty()) {
                    false
                } else {
                    val msg = "$message:" + list.joinToString(prefix = "\n", separator = "\n") { "  - ${it.getClass()?.kotlinFqName ?: it}" }
                    messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, "Will not export ${declaration.kotlinFqName} because $msg:")
                    true
                }
            }

            private fun <T : IrDeclaration> checkDecl(declaration: IrClass, list: List<T>, message: String): Boolean {
                return if (list.isEmpty()) {
                    false
                } else {
                    val msg = "$message:" + list.joinToString(prefix = "\n", separator = "\n") { "  - ${it.signatureString}" }
                    messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, "Will not export ${declaration.kotlinFqName} because $msg:")
                    true
                }
            }

            override fun visitClass(declaration: IrClass): IrStatement {
                messageCollector.report(CompilerMessageSeverity.INFO, "Checking for export: ${declaration.kotlinFqName}")
                return if (
                    noPatternsOrMatchesOn(declaration.kotlinFqName.asString(), exportPatternsRegex)
                ) {
                    when {
                        declaration.isJsExport() -> {
                            messageCollector.report(CompilerMessageSeverity.INFO, "Already marked for export: ${declaration.kotlinFqName}")
                            super.visitClass(declaration)  // already exported
                        }

                        declaration.isPotentiallyExportable -> when {
                            checkType(declaration, declaration.nonExportableSuperTypes, "it has superTypes that are not exportable") -> super.visitClass(declaration)
                            checkDecl(declaration, declaration.nonExportableProperties, "it has properties that are not exportable") -> super.visitClass(declaration)
                            checkDecl(declaration, declaration.nonExportableMethods, "it has methods (functions) that are not exportable") -> super.visitClass(declaration)
                            checkDecl(declaration, declaration.overloadedMethods, "it has overloaded methods which could cause a call ambiguity in JavaScript") -> super.visitClass(
                                declaration
                            )

                            else -> {
                                messageCollector.report(CompilerMessageSeverity.INFO, "Exporting: ${declaration.kotlinFqName}")
                                val cc = DeclarationIrBuilder(pluginContext, declaration.symbol).irCallConstructor(jsExportSymbol, emptyList())
                                declaration.annotations += cc
                                super.visitClass(declaration)
                            }
                        }

                        else -> {
                            messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, "Will not export ${declaration.kotlinFqName} it is not exportable")
                            super.visitClass(declaration)
                        }
                    }
                } else {
                    // not interested in exporting this class
                    messageCollector.report(CompilerMessageSeverity.INFO, "Not trying to export ${declaration.kotlinFqName}")
                    super.visitClass(declaration)
                }
            }

            override fun visitFunction(declaration: IrFunction): IrStatement {
                return if (declaration.isTopLevel) {
                    if (noPatternsOrMatchesOn(declaration.kotlinFqName.asString(), exportPatternsRegex)) {
                        if (
                            declaration.isJsExport().not() &&
                            declaration.visibility == DescriptorVisibilities.PUBLIC &&
                            declaration.isExpect.not() &&
                            declaration.isExternal.not() &&
                            declaration.isInline.not() &&
                            declaration.isSuspend.not()
                        ) {
                            messageCollector.report(CompilerMessageSeverity.INFO, "Exporting: ${declaration.signatureString}")
                            val cc = DeclarationIrBuilder(pluginContext, declaration.symbol).irCallConstructor(jsExportSymbol, emptyList())
                            declaration.annotations = declaration.annotations + cc
                            super.visitFunction(declaration)
                        } else {
                            messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, "Will not export ${declaration.signatureString} it is not exportable")
                            super.visitFunction(declaration)
                        }
                    } else {
                        // not interested in exporting this class
                        messageCollector.report(CompilerMessageSeverity.INFO, "Not trying to export ${declaration.signatureString}")
                        super.visitFunction(declaration)
                    }
                } else {
                    //not top-level
                    super.visitFunction(declaration)
                }
            }

            override fun visitProperty(declaration: IrProperty): IrStatement {
                return if (declaration.isTopLevel) {
                    if (noPatternsOrMatchesOn(declaration.kotlinFqName.asString(), exportPatternsRegex)) {
                        if (
                            declaration.isJsExport().not() &&
                            declaration.visibility == DescriptorVisibilities.PUBLIC &&
                            declaration.isExpect.not() &&
                            declaration.isExternal.not() &&
                            declaration.isExportable
                        ) {
                            messageCollector.report(CompilerMessageSeverity.INFO, "Exporting: ${declaration.signatureString}")
                            val cc = DeclarationIrBuilder(pluginContext, declaration.symbol).irCallConstructor(jsExportSymbol, emptyList())
                            declaration.annotations = declaration.annotations + cc
                            super.visitProperty(declaration)
                        } else {
                            messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, "Will not export ${declaration.signatureString} it is not exportable")
                            super.visitProperty(declaration)
                        }
                    } else {
                        // not interested in exporting this class
                        messageCollector.report(CompilerMessageSeverity.INFO, "Not trying to export TopLevel Property ${declaration.signatureString}")
                        super.visitProperty(declaration)
                    }
                } else {
                    //not top-level
                    super.visitProperty(declaration)
                }
            }

        }
        moduleFragment.transform(exportVisitor, null)

    }

    private fun noPatternsOrMatchesOn(qName: String, exportPatternsRegex: List<Regex>): Boolean = exportPatternsRegex.isEmpty() || exportPatternsRegex.any { it.matches(qName) }

    private fun noOverloadedMethods(declaration: IrClass): Map<Name, List<IrSimpleFunction>> {
        val nameGroups = declaration.functions.groupBy { it.name }
        return nameGroups.filter { it.value.size != 1 }
    }

    private fun implementsPublicInterface(declaration: IrClass): Boolean {
        messageCollector.report(CompilerMessageSeverity.LOGGING, "checking super interfaces of ${declaration.name}")
        return declaration.allSuperInterfaces().any {
            messageCollector.report(CompilerMessageSeverity.LOGGING, "  checking ${it.name}")
            it.visibility == DescriptorVisibilities.PUBLIC
        }
    }

    private val IrProperty.type: IrType get() = this.getter?.returnType ?: this.backingField?.type ?: error("property has no getter or backingField")

    /**
     * only exportable if there is a getter
     */
    private val IrProperty.isExportable: Boolean
        get() {
            return this.type.isExportable
        }

    private val IrFunction.isExportable: Boolean
        get() = try {
            this.returnType.isExportable && this.valueParameters.all { it.type.isExportable }
        } catch (t: Throwable) {
            messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, "Exception in isExportable for method/function '${this.signatureString}': $t")
            throw t
        }
    private val IrClass.isPotentiallyExportable: Boolean
        get() {
            return when {
                this.isJsExport() -> true
                this.isExpect -> false
                this.isExternal -> false
                this.isValue -> false
                this.isAnnotationClass -> false
                this.isAnonymousObject -> false
                this.isEnumClass -> true //false
                else -> {
                    (this.visibility == DescriptorVisibilities.PUBLIC)// || implementsPublicInterface(this))
                }
            }
        }
    private val IrClass.isExportable: Boolean
        get() = try {
            when (exportableCache.containsKey(this)) {
                true -> exportableCache[this]!!
                false -> {
                    exportableCache[this] = true // top stop recursion & stop type from making itself not exportable
                    val exportable = when {
                        this.typeWith().isBuiltInExportable -> true
                        this.isPotentiallyExportable.not() -> false
                        this.nonExportableSuperTypes.isNotEmpty() -> false
                        this.nonExportableProperties.isNotEmpty() -> false
                        this.nonExportableMethods.isNotEmpty() -> false
                        else -> true
                    }
                    exportableCache[this] = exportable
                    exportable
                }
            }
        } catch (t: Throwable) {
            messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, "Exception in isExportable for class '${this.kotlinFqName}': $t")
            throw t
        }

    private val IrClass.nonExportableSuperTypes: List<IrType>
        get() = this.superTypes.filter {
            try {
                it.isExportable.not()
            } catch (t: Throwable) {
                messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, "Exception in nonExportableSuperTypes for class '${it.getClass()?.kotlinFqName}': $t")
                throw t
            }
        }
    private val IrClass.nonExportableProperties: List<IrProperty>
        get() =
            this.properties.filter {
                try {
                    if (it.getter?.realOverrideTarget?.parentAsClass?.typeWith()?.isBuiltInExportable == true) {
                        false
                    } else {
                        it.isExportable.not()
                    }
                } catch (t: Throwable) {
                    messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, "Exception in nonExportableProperties for property '${it.signatureString}': $t")
                    throw t
                }
            }.toList()

    private val IrClass.nonExportableMethods: List<IrSimpleFunction>
        get() =
            this.functions.filter {
                try {
                    //messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, "*** ${it.signatureString} real target isExportable ${it.realOverrideTarget.parentAsClass.isExportable}")
                    if (it.realOverrideTarget.parentAsClass.typeWith().isBuiltInExportable) {
                        false
                    } else {
                        it.isExportable.not()
                    }
                } catch (t: Throwable) {
                    messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, "Exception in nonExportableMethods for property '${it.signatureString}': $t")
                    throw t
                }
            }.toList()

    private val IrClass.overloadedMethods: List<IrSimpleFunction>
        get() = this.functions
            .filter { it.realOverrideTarget.parentAsClass.typeWith().isBuiltInExportable.not() }
            .groupBy { it.name }
            .filter { it.value.size != 1 }.values.flatten()


    private val IrType.isBuiltInExportable: Boolean
        get() = when {
            this.isJavaType() -> false
            this.isException() -> false
            this.isRuntimeException() -> false
            this.hasStarProjections -> false
            this.isUnit() -> true
            this.isEnum() -> true
            this.isThrowable() -> true
//                            this.isDynamic() -> true
            this.isAny() -> true
            this.isString() -> true
            this.isStringClassType() -> true
            this.isBoolean() -> true
            this.isByte() -> true
            this.isShort() -> true
            this.isInt() -> true
            this.isFloat() -> true
            this.isDouble() -> true
            this.isBooleanArray() -> true
            this.isByteArray() -> true
            this.isShortArray() -> true
            this.isIntArray() -> true
            this.isFloatArray() -> true
            this.isDoubleArray() -> true
            this.isPrimitiveArray() -> true
            this.isArray() -> {
                val argument = (this as IrSimpleType).arguments.singleOrNull()
                if (null == argument) {
                    true
                } else {
                    this.getArrayElementType(irBuiltIns).isExportable// -> true
                }
            }

            isKClass() -> true
            this.isOneOfCollectionTypes() -> true  //TODO: needs a warning or a conversion when actually exporting!
            this.isOneOfMutableCollectionTypes() -> true  //TODO: needs a warning or a conversion when actually exporting!
            else -> false
        }

    private val IrType.isExportable: Boolean
        get() {
            return when (exportableCache.containsKey(this)) {
                true -> exportableCache[this]!!
                false -> {
                    exportableCache[this] = true // top stop recursion
                    val exportable = when {
                        //this.isTypeParameter() -> true
                        this.isBuiltInExportable -> true
                        this.isJsExport() -> true
                        this.getRuntimeClass(irBuiltIns).isExportable -> true
                        else -> false
                    }
                    exportableCache[this] = exportable
                    exportable
                }
            }
        }

    private val IrType.hasStarProjections get()= when (this) {
        is IrCapturedType -> {
            this.arguments.any { it is IrStarProjection }
        }
            else -> false
    }

    private val IrDeclaration.signatureString
        get() = when (this) {
            is IrProperty -> "${this.getter?.realOverrideTarget?.parent?.kotlinFqName}::${this.name.asString()}: ${this.getter?.returnType?.getClass()?.kotlinFqName?.asString()}"
            is IrFunction -> {
                val owner = this.realOverrideTarget.parent.kotlinFqName
                val fname = this.name.asString()
                val params = this.valueParameters.joinToString {
                    val pn = it.name.asString()
                    val pt = it.type.asString()
                    val pnl = if (it.type.isNullable()) "?" else ""
                    "$pn:$pt$pnl"
                }
                val type = this.returnType.asString()
                "$owner::$fname($params):$type"
            }

            else -> this.signatureString(true)
        }

    private inline fun IrType.isTypeFromPackage(pkg: FqName, namePredicate: (Name) -> Boolean): Boolean {
        if (this is IrSimpleType) {
            val classClassifier = classifier as? IrClassSymbol ?: return false
            if (!namePredicate(classClassifier.owner.name)) return false
            val parent = classClassifier.owner.parent as? IrPackageFragment ?: return false
            return parent.fqName == pkg
        } else return false
    }

    fun IrType.isJavaType(): Boolean {
        if (this is IrSimpleType) {
            val classClassifier = classifier as? IrClassSymbol ?: return false
            val parent = classClassifier.owner.parent as? IrPackageFragment ?: return false
            return parent.fqName == javaPackageFqn
        } else {
            return false
        }
    }

    fun IrType.isEnum(): Boolean = isTypeFromPackage(kotlinPackageFqn) { name -> name.asString() == "Enum" }
    fun IrType.isException(): Boolean = isTypeFromPackage(kotlinPackageFqn) { name -> name.asString() == "Exception" }
    fun IrType.isRuntimeException(): Boolean = isTypeFromPackage(kotlinPackageFqn) { name -> name.asString() == "RuntimeException" }
    fun IrType.isList(): Boolean = isTypeFromPackage(kotlinCollectionsPackageFqn) { name -> name.asString() == "List" }
    fun IrType.isMutableList(): Boolean = isTypeFromPackage(kotlinCollectionsPackageFqn) { name -> name.asString() == "MutableList" }
    fun IrType.isSet(): Boolean = isTypeFromPackage(kotlinCollectionsPackageFqn) { name -> name.asString() == "Set" }
    fun IrType.isMutableSet(): Boolean = isTypeFromPackage(kotlinCollectionsPackageFqn) { name -> name.asString() == "MutableSet" }
    fun IrType.isMap(): Boolean = isTypeFromPackage(kotlinCollectionsPackageFqn) { name -> name.asString() == "Map" }
    fun IrType.isMutableMap(): Boolean = isTypeFromPackage(kotlinCollectionsPackageFqn) { name -> name.asString() == "MutableMap" }
    fun IrType.isCollection(): Boolean = isTypeFromPackage(kotlinCollectionsPackageFqn) { name -> name.asString() == "Collection" }
    fun IrType.isMutableCollection(): Boolean = isTypeFromPackage(kotlinCollectionsPackageFqn) { name -> name.asString() == "MutableCollection" }
    fun IrType.isOneOfCollectionTypes(): Boolean = isCollection() || isList() || isSet() || isMap()
    fun IrType.isOneOfMutableCollectionTypes(): Boolean = isMutableCollection() || isMutableList() || isMutableSet() || isMutableMap()
    fun IrType.isKClass(): Boolean = isTypeFromPackage(kotlinReflectPackageFqn) { name -> name.asString() == "KClass" }

    val IrProperty.kotlinFqName: FqName get() = this.parent.kotlinFqName.child(this.name)
}