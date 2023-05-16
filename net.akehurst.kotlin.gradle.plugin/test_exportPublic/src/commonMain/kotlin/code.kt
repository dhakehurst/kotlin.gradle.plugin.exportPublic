package test.exportPublic

import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

class APublicClass {
    class InnerClass
}

private class APrivateClass
internal class AnInternalClass

@JsExport
class AlreadyExportedClass

object AnObject

enum class AnEnum { X,Y,Z }

class EnumProp {
    val e:AnEnum? = null
}

class WithNullableProp {
    val name:String?=null
}

interface NonExpSuperType: WithStarProjectionProperty

interface NonExpProp {
    val prop:Map<*, *>
}

class NonExpPrivateProp {
    private val prop:Map<*, *>? = null
}

interface NonExpMeth {
    fun f():Map<*, *>
}

interface OverloadedMeths {
    fun f()
    fun f(i:Int)
}

class OverloadedPrivateMeths {
    private fun f() {}
    private fun f(i:Int) {}
}

typealias EnumValuesFunction = ()->Array<Enum<*>>

class AException : Exception()

class ARuntimeException : RuntimeException()

class AThrowable: Throwable()

abstract class AnAbstractClass

abstract class WithAbstractProp {
    abstract val x:Int
}

class WithInternalProperty {
    val pubProp:Int = 1
    internal val internalProp:Int = 1
}

class WithPropKFunctionStar {
    val prop: KFunction<*> get() = TODO()
}

interface WithKClassStar {
    val prop:KClass<*>
}

interface WithKClassAny {
    val prop:KClass<Any>
}

interface WithKClassT<T:Any> {
    val prop:KClass<T>
}

interface IA {
    val prop:String
}

interface IB {
    val a :IA
}

interface IWithCollections {
    val list:List<String>
    val mlist:MutableList<String>
    val set:Set<String>
    val mset:MutableSet<String>
    val map:Map<Int,String>
    val mmap:MutableMap<Int,String>
}

class WithListCollections {
    val list:List<String> = emptyList()
    val mlist:MutableList<String> = mutableListOf()
    val set:Set<String> = emptySet()
    val mset:MutableSet<String> = mutableSetOf()
    val map:Map<Int,String> = emptyMap()
    val mmap:MutableMap<Int,String> = mutableMapOf()
}


interface WithStarProjectionProperty {
    val map: Map<*, *>
}

interface WithStarProjectionFuncParam {
    fun func(map: Map<*, *>)
}

interface WithStarProjectionTypeParamFuncParam {
    fun func(map: Map<KClass<*>, KFunction<*>>)
}

interface WithTypeParameter<T> {
   val prop:T
   fun f(arg:T)
   fun f2():T
}

expect fun expectedFunction()

fun WithTypeParameter<*>.functionWithNonExportableExtensionReceiverParameter():Boolean {
    TODO()
}

fun functionWithNonExportableParameter(cls:WithTypeParameter<*>):Boolean {
    TODO()
}

//-----------------------------------
interface ExportableMap<K, V> {
    val size: Int

    val x:KClass<*>

    @JsName("isEmpty")
    fun isEmpty(): Boolean

    //@JsName("get")
    //operator fun get(key: K): V//?
}

object KotlinxReflect {

    private var _registeredClasses = mutableMapOf<String, KClass<*>>()

    val registeredClasses:Map<String, KClass<*>> = _registeredClasses

    val amap:ExportableMap<String,Int>? = null

    fun classForName(qualifiedName: String): KClass<*> {
        TODO()
    }
}
