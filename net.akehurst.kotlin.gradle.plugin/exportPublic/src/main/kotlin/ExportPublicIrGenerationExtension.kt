package net.akehurst.kotlin.gradle.plugin.exportPublic

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.wasm.ir2wasm.allSuperInterfaces
import org.jetbrains.kotlin.backend.wasm.ir2wasm.getRuntimeClass
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.utils.isJsExport
import org.jetbrains.kotlin.ir.backend.js.utils.realOverrideTarget
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class ExportPublicIrGenerationExtension(
    private val messageCollector: MessageCollector,
    private val exportPatterns: List<String>
) : IrGenerationExtension {

    companion object {
        //val javaPackageFqn = FqName.fromSegments(listOf("java"))
        val kotlinCollectionsPackageFqn = FqName.fromSegments(listOf("kotlin", "collections"))
        val kotlinReflectPackageFqn = FqName.fromSegments(listOf("kotlin", "reflect"))
    }

    private val exportPatternsRegex = exportPatterns.mapNotNull {
        if (it.isBlank()) {
            null
        } else {
            it.toRegexFromGlob('.')
        }
    }

    private val exportableCache = mutableMapOf<Any, Boolean>()
    private lateinit var irBuiltIns: IrBuiltIns

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        irBuiltIns = pluginContext.irBuiltIns

        messageCollector.report(
            CompilerMessageSeverity.INFO,
            "Exporting declarations that match one of: ${exportPatterns.map { "'$it'" }}"
        )
        messageCollector.report(
            CompilerMessageSeverity.LOGGING,
            "Exporting declarations from files: ${moduleFragment.files.joinToString(separator = "\n") { "  ${it.path}" }}"
        )

        val jsExportSymbol =
            pluginContext.referenceConstructors(ClassId(FqName("kotlin.js"), Name.identifier("JsExport"))).first()
        //messageCollector.report(CompilerMessageSeverity.INFO, "JsExport class: ${jsExportSymbol.signature}")

        val exportVisitor = object : IrElementTransformerVoid() {
            private val <E : IrAnnotationContainer> List<E>.asLineSeparatedString
                get() = this.joinToString(
                    prefix = ":\n",
                    separator = "\n"
                ) { "  - ${it.signatureString}" }

            private fun checkIf(
                level: CompilerMessageSeverity,
                test: () -> Boolean,
                lazyMessage: () -> String
            ): Boolean {
                return if (test.invoke()) {
                    messageCollector.report(level, lazyMessage.invoke())
                    true
                } else {
                    false
                }
            }

            private fun checkIfStrong(test: () -> Boolean, lazyMessage: () -> String): Boolean =
                checkIf(CompilerMessageSeverity.STRONG_WARNING, test, lazyMessage)

            private fun checkIfInfo(test: () -> Boolean, lazyMessage: () -> String): Boolean =
                checkIf(CompilerMessageSeverity.INFO, test, lazyMessage)

            private fun checkIfLog(test: () -> Boolean, lazyMessage: () -> String): Boolean =
                checkIf(CompilerMessageSeverity.LOGGING, test, lazyMessage)

            override fun visitClass(declaration: IrClass): IrStatement {
                return try {
                    messageCollector.report(
                        CompilerMessageSeverity.LOGGING,
                        "Checking class for export: ${declaration.kotlinFqName}"
                    )
                    val msg = "Will not export ${declaration.kotlinFqName},"
                    when {
                        checkIfLog(
                            { declaration.isPublic.not() },
                            { "$msg it is not a public class" }) -> super.visitClass(declaration)
                        //checkIfLog({ declaration.isTopLevel.not() }, { "$msg it is not a top-level class" }) -> super.visitClass(declaration)
                        checkIfInfo(
                            { declaration.isJsExport() },
                            { "$msg it is already exported by annotation" }) -> super.visitClass(declaration)

                        checkIfInfo(
                            { noPatternsOrMatchesOn(declaration.kotlinFqName.asString(), exportPatternsRegex).not() },
                            { "$msg it is not matched by the glob filter" }) -> super.visitClass(declaration)

                        checkIfStrong(
                            { declaration.isExpect },
                            { "$msg it is an 'expect' class" }) -> super.visitClass(declaration)

                        checkIfStrong(
                            { declaration.isExternal },
                            { "$msg it is an 'external' class" }) -> super.visitClass(declaration)

                        checkIfStrong(
                            { declaration.isValue },
                            { "$msg it is a 'value' class" }) -> super.visitClass(declaration)

                        checkIfStrong(
                            { declaration.isAnnotationClass },
                            { "$msg it is an annotation class" }) -> super.visitClass(declaration)

                        checkIfStrong(
                            { declaration.nonExportableSuperTypes.isNotEmpty() },
                            { "$msg it has superTypes that are not exportable ${declaration.nonExportableSuperTypes.asLineSeparatedString}" })
                        -> super.visitClass(declaration)

                        checkIfStrong({ declaration.nonExportableConstructors.isNotEmpty() },
                            { "$msg it has constructors that are not exportable ${declaration.nonExportableConstructors.asLineSeparatedString}" })
                            -> super.visitClass(declaration)

                        checkIfStrong({ declaration.nonExportableProperties.isNotEmpty() },
                            { "$msg it has properties that are not exportable ${declaration.nonExportableProperties.asLineSeparatedString}" })
                        -> super.visitClass(declaration)

                        checkIfStrong({ declaration.nonExportableMethods.isNotEmpty() },
                            { "$msg it has methods that are not exportable ${declaration.nonExportableMethods.asLineSeparatedString}" })
                        -> super.visitClass(declaration)

                        checkIfStrong({ declaration.overloadedMethods.isNotEmpty() },
                            { "$msg it has overloaded methods which could cause a call ambiguity in JavaScript ${declaration.overloadedMethods.asLineSeparatedString}" })
                        -> super.visitClass(declaration)

                        else -> {
                            messageCollector.report(
                                CompilerMessageSeverity.INFO,
                                "Exporting: ${declaration.kotlinFqName}"
                            )
                            val cc = DeclarationIrBuilder(
                                pluginContext,
                                declaration.symbol
                            ).irCallConstructor(jsExportSymbol, emptyList())
                            declaration.annotations += cc
                            super.visitClass(declaration)
                        }
                    }
                } catch (t: Throwable) {
                    messageCollector.report(
                        CompilerMessageSeverity.ERROR,
                        "Error exporting class : ${declaration.kotlinFqName}"
                    )
                    super.visitClass(declaration)
                }
            }

            override fun visitFunction(declaration: IrFunction): IrStatement {
                return try {
                    messageCollector.report(
                        CompilerMessageSeverity.LOGGING,
                        "Checking function for export: ${declaration.kotlinFqName}"
                    )
                    val msg = "Will not export ${declaration.kotlinFqName},"
                    when {
                        checkIfLog(
                            { declaration.isPublic.not() },
                            { "$msg it is not a public function" }) -> super.visitFunction(declaration)

                        checkIfLog(
                            { declaration.isTopLevel.not() },
                            { "$msg it is not a top-level function" }) -> super.visitFunction(declaration)

                        checkIfInfo(
                            { declaration.isJsExport() },
                            { "$msg it is already exported by annotation" }) -> super.visitFunction(declaration)

                        checkIfInfo(
                            { noPatternsOrMatchesOn(declaration.kotlinFqName.asString(), exportPatternsRegex).not() },
                            { "$msg not matched by the glob filter" }) -> super.visitFunction(declaration)

                        checkIfStrong(
                            { declaration.isExpect },
                            { "$msg it is an 'expect' function" }) -> super.visitFunction(declaration)

                        checkIfStrong(
                            { declaration.isExternal },
                            { "$msg it is an 'external' function" }) -> super.visitFunction(declaration)

                        checkIfStrong(
                            { declaration.isInline },
                            { "$msg it is an 'inline' function" }) -> super.visitFunction(declaration)

                        checkIfStrong(
                            { declaration.isSuspend },
                            { "$msg it is a 'suspend' function" }) -> super.visitFunction(declaration)

                        checkIfStrong(
                            { declaration.returnType.isExportable.not() },
                            { "$msg it has a non-exportable return type" }) -> super.visitFunction(declaration)

                        checkIfStrong(
                            { null != declaration.dispatchReceiverParameter && declaration.dispatchReceiverParameter!!.type.isExportable.not() },
                            { "$msg it has a non-exportable dispatch receiver parameter" }) -> super.visitFunction(
                            declaration
                        )

                        checkIfStrong(
                            { null != declaration.extensionReceiverParameter && declaration.extensionReceiverParameter!!.type.isExportable.not() },
                            { "$msg it has a non-exportable extension receiver parameter" }) -> super.visitFunction(
                            declaration
                        )

                        checkIfStrong(
                            { declaration.valueParameters.all { it.type.isExportable }.not() },
                            { "$msg it has a non-exportable parameter" }) -> super.visitFunction(declaration)

                        else -> {
                            messageCollector.report(
                                CompilerMessageSeverity.INFO,
                                "Exporting: ${declaration.signatureString}"
                            )
                            val cc = DeclarationIrBuilder(pluginContext, declaration.symbol).irCallConstructor(
                                jsExportSymbol,
                                emptyList()
                            )
                            declaration.annotations = declaration.annotations + cc
                            super.visitFunction(declaration)
                        }
                    }
                } catch (t: Throwable) {
                    messageCollector.report(
                        CompilerMessageSeverity.ERROR,
                        "Error exporting function : ${declaration.kotlinFqName}"
                    )
                    super.visitFunction(declaration)
                }
            }

            override fun visitProperty(declaration: IrProperty): IrStatement {
                return try {
                    messageCollector.report(
                        CompilerMessageSeverity.LOGGING,
                        "Checking property for export: ${declaration.kotlinFqName}"
                    )
                    val msg = "Will not export ${declaration.kotlinFqName}, it is"
                    when {
                        checkIfLog(
                            { declaration.isPublic.not() },
                            { "$msg not a public property" }) -> super.visitProperty(declaration)

                        checkIfLog(
                            { declaration.isTopLevel.not() },
                            { "$msg not a top-level property" }) -> super.visitProperty(declaration)

                        checkIfInfo(
                            { declaration.isJsExport() },
                            { "$msg already exported by annotation" }) -> super.visitProperty(declaration)

                        checkIfInfo({ declaration.isExpect }, { "$msg an 'expect' property" }) -> super.visitProperty(
                            declaration
                        )

                        checkIfInfo(
                            { declaration.isExternal },
                            { "$msg an 'external' property" }) -> super.visitProperty(declaration)

                        checkIfInfo(
                            { noPatternsOrMatchesOn(declaration.kotlinFqName.asString(), exportPatternsRegex).not() },
                            { "$msg not matched by the glob filter" }) -> super.visitProperty(declaration)

                        else -> {
                            messageCollector.report(
                                CompilerMessageSeverity.INFO,
                                "Exporting: ${declaration.signatureString}"
                            )
                            val cc = DeclarationIrBuilder(
                                pluginContext,
                                declaration.symbol
                            ).irCallConstructor(jsExportSymbol, emptyList())
                            declaration.annotations = declaration.annotations + cc
                            super.visitProperty(declaration)
                        }
                    }
                } catch (t: Throwable) {
                    messageCollector.report(
                        CompilerMessageSeverity.ERROR,
                        "Error exporting property : ${declaration.kotlinFqName}"
                    )
                    super.visitProperty(declaration)
                }
            }
        }
        moduleFragment.transform(exportVisitor, null)

    }

    private fun noPatternsOrMatchesOn(qName: String, exportPatternsRegex: List<Regex>): Boolean =
        exportPatternsRegex.isEmpty() || exportPatternsRegex.any { it.matches(qName) }

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

    private val IrProperty.type: IrType
        get() = this.getter?.returnType ?: this.backingField?.type ?: error("property has no getter or backingField")

    private val IrProperty.isExportable: Boolean
        get() = try {
            when {
                this.isExpect -> false
                this.isExternal -> false
                null != this.getter?.dispatchReceiverParameter && this.getter?.dispatchReceiverParameter!!.type.isExportable.not() -> false
                null != this.getter?.extensionReceiverParameter && this.getter?.extensionReceiverParameter!!.type.isExportable.not() -> false
                else -> true //this.type.isExportable
            }
        } catch (t: Throwable) {
            messageCollector.report(
                CompilerMessageSeverity.STRONG_WARNING,
                "Exception in isExportable for property '${this.signatureString}': $t"
            )
            throw t
        }

    private val IrDeclarationWithVisibility.isPublic: Boolean get() = this.visibility == DescriptorVisibilities.PUBLIC
    private val IrFunction.isExportable: Boolean
        get() = try {
            when {
                this.isPublic.not() -> false
                this.isExpect -> false
                this.isExternal -> false
                this.isInline -> false
                //this.isSuspend -> false
                this.returnType.isExportable.not() -> false
                null != this.dispatchReceiverParameter && this.dispatchReceiverParameter!!.type.isExportable.not() -> false
                null != this.extensionReceiverParameter && this.extensionReceiverParameter!!.type.isExportable.not() -> false
                else -> this.valueParameters.all { it.type.isExportable }
            }
        } catch (t: Throwable) {
            messageCollector.report(
                CompilerMessageSeverity.STRONG_WARNING,
                "Exception in isExportable for method/function '${this.signatureString}': $t"
            )
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
                        this.isValue -> false
                        this.typeWith().isBuiltInExportable -> true
                        this.isPotentiallyExportable.not() -> false
                        this.nonExportableSuperTypes.isNotEmpty() -> false
                        this.nonExportableConstructors.isNotEmpty() -> false
                        this.nonExportableProperties.isNotEmpty() -> false
                        this.nonExportableMethods.isNotEmpty() -> false
                        else -> true
                    }
                    exportableCache[this] = exportable
                    exportable
                }
            }
        } catch (t: Throwable) {
            messageCollector.report(
                CompilerMessageSeverity.STRONG_WARNING,
                "Exception in isExportable for class '${this.kotlinFqName}': $t"
            )
            throw t
        }

    private val IrClass.nonExportableSuperTypes: List<IrType>
        get() = this.superTypes.filter {
            try {
                it.isExportable.not()
            } catch (t: Throwable) {
                messageCollector.report(
                    CompilerMessageSeverity.STRONG_WARNING,
                    "Exception in nonExportableSuperTypes for class '${it.getClass()?.kotlinFqName}': $t"
                )
                throw t
            }
        }
    private val IrClass.nonExportableProperties: List<IrProperty>
        get() =
            this.properties
                .filter { it.isPublic }
                .filter {
                    try {
                        if (it.getter?.realOverrideTarget?.parentAsClass?.typeWith()?.isBuiltInExportable == true) {
                            false
                        } else {
                            it.isExportable.not()
                        }
                    } catch (t: Throwable) {
                        messageCollector.report(
                            CompilerMessageSeverity.STRONG_WARNING,
                            "Exception in nonExportableProperties for property '${it.signatureString}': $t"
                        )
                        throw t
                    }
                }.toList()

    private val IrClass.nonExportableConstructors: List<IrConstructor>
        get() = this.constructors
            .filter { it.isPublic }
            .filter {
                it.isExportable.not()
            }.toList()

    private val IrClass.nonExportableMethods: List<IrSimpleFunction>
        get() =
            this.functions
                .filter { it.isPublic }
                .filter {
                    try {
                        //messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, "*** ${it.signatureString} real target isExportable ${it.realOverrideTarget.parentAsClass.isExportable}")
                        if (it.realOverrideTarget.parentAsClass.typeWith().isBuiltInExportable) {
                            false
                        } else {
                            it.isExportable.not()
                        }
                    } catch (t: Throwable) {
                        messageCollector.report(
                            CompilerMessageSeverity.STRONG_WARNING,
                            "Exception in nonExportableMethods for property '${it.signatureString}': $t"
                        )
                        throw t
                    }
                }.toList()

    private val IrClass.overloadedMethods: List<IrSimpleFunction>
        get() = this.functions
            .filter { it.isPublic }
            .filter { it.realOverrideTarget.parentAsClass.typeWith().isBuiltInExportable.not() }
            .groupBy { it.name }
            .filter { it.value.size != 1 }.values.flatten()


    private val IrType.isBuiltInExportable: Boolean
        get() = when {
            //this.isJavaType() -> false
            //this.isException() -> false
            //this.isRuntimeException() -> false
            this.isKClass() -> true

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
                        this.hasStarProjections -> false
                        this.isBuiltInExportable -> true
                        this.isJsExport() -> true
                        this.isDynamic() -> true
                        this.getRuntimeClass(irBuiltIns).isExportable -> true
                        else -> true //false
                    }
                    exportableCache[this] = exportable
                    exportable
                }
            }
        }

    private val IrType.hasStarProjections: Boolean
        get() = when (this) {
            is IrSimpleType -> {
                this.arguments.any {
                    when (it) {
                        is IrStarProjection -> true
                        is IrTypeProjection -> it.type.hasStarProjections
                        else -> error("Unsupported subtype of IrTypeArgument")
                    }
                }
            }

            else -> false
        }

    private val IrAnnotationContainer.signatureString
        get() = when (this) {
            is IrType -> this.signatureString
            is IrProperty -> "${this.getter?.realOverrideTarget?.parent?.kotlinFqName}::${this.name.asString()}: ${this.type.signatureString}"
            is IrFunction -> {
                val owner = this.realOverrideTarget.parent.kotlinFqName
                val fname = this.name.asString()
                val params = this.valueParameters.joinToString {
                    val pn = it.name.asString()
                    val pt = it.type.signatureString
                    val pnl = if (it.type.isNullable()) "?" else ""
                    "$pn:$pt$pnl"
                }
                val type: String = this.returnType.signatureString
                "$owner::$fname($params):$type"
            }

            else -> this.toString()
        }

    private val IrType.signatureString: String
        get() {
            val cls = this.getClass()
            return if (null == cls) {
                this.toString()
            } else {
                val typeArgs = when {
                    this is IrSimpleType -> when {
                        this.arguments.isEmpty() -> ""
                        else -> this.arguments.joinToString(prefix = "<", postfix = ">") { it.render() }
                    }

                    else -> ""
                }
                cls.kotlinFqName.asString() + typeArgs
            }
        }

    private inline fun IrType.isTypeFromPackage(pkg: FqName, namePredicate: (Name) -> Boolean): Boolean {
        if (this is IrSimpleType) {
            val classClassifier = classifier as? IrClassSymbol ?: return false
            if (!namePredicate(classClassifier.owner.name)) return false
            val parent = classClassifier.owner.parent as? IrPackageFragment ?: return false
            return parent.packageFqName == pkg
        } else return false
    }

    /*
    fun IrType.isJavaType(): Boolean {
        if (this is IrSimpleType) {
            val classClassifier = classifier as? IrClassSymbol ?: return false
            val parent = classClassifier.owner.parent as? IrPackageFragment ?: return false
            return parent.fqName == javaPackageFqn
        } else {
            return false
        }
    }
*/

    fun IrType.isEnum(): Boolean = isTypeFromPackage(kotlinPackageFqn) { name -> name.asString() == "Enum" }

    //fun IrType.isException(): Boolean = isTypeFromPackage(kotlinPackageFqn) { name -> name.asString() == "Exception" }
    fun IrType.isRuntimeException(): Boolean =
        isTypeFromPackage(kotlinPackageFqn) { name -> name.asString() == "RuntimeException" }

    fun IrType.isList(): Boolean = isTypeFromPackage(kotlinCollectionsPackageFqn) { name -> name.asString() == "List" }
    fun IrType.isMutableList(): Boolean =
        isTypeFromPackage(kotlinCollectionsPackageFqn) { name -> name.asString() == "MutableList" }

    fun IrType.isSet(): Boolean = isTypeFromPackage(kotlinCollectionsPackageFqn) { name -> name.asString() == "Set" }
    fun IrType.isMutableSet(): Boolean =
        isTypeFromPackage(kotlinCollectionsPackageFqn) { name -> name.asString() == "MutableSet" }

    fun IrType.isMap(): Boolean = isTypeFromPackage(kotlinCollectionsPackageFqn) { name -> name.asString() == "Map" }
    fun IrType.isMutableMap(): Boolean =
        isTypeFromPackage(kotlinCollectionsPackageFqn) { name -> name.asString() == "MutableMap" }

    fun IrType.isCollection(): Boolean =
        isTypeFromPackage(kotlinCollectionsPackageFqn) { name -> name.asString() == "Collection" }

    fun IrType.isMutableCollection(): Boolean =
        isTypeFromPackage(kotlinCollectionsPackageFqn) { name -> name.asString() == "MutableCollection" }

    fun IrType.isOneOfCollectionTypes(): Boolean = isCollection() || isList() || isSet() || isMap()
    fun IrType.isOneOfMutableCollectionTypes(): Boolean =
        isMutableCollection() || isMutableList() || isMutableSet() || isMutableMap()

    fun IrType.isKClass(): Boolean = isTypeFromPackage(kotlinReflectPackageFqn) { name -> name.asString() == "KClass" }
    fun IrType.isDynamic(): Boolean = this is IrDynamicType

    val IrProperty.kotlinFqName: FqName get() = this.parent.kotlinFqName.child(this.name)
}