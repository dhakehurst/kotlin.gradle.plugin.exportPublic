package test.exportPublic

import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.reflect.KClass

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

interface NonExpSuperType: WithStarProjection

interface NonExpProp {
    val prop:Map<*, *>
}

interface NonExpMeth {
    fun f():Map<*, *>
}

interface OverloadedMeths {
    fun f()
    fun f(i:Int)
}

typealias EnumValuesFunction = ()->Array<Enum<*>>

class AException : Exception()

class ARuntimeException : RuntimeException()

class AThrowable: Throwable()

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

interface WithStarProjection {
    val map: Map<*, *>
}

interface WithTypeParameter<T> {
   val prop:T
   fun f(arg:T)
   fun f2():T
}

expect fun expectedFunction()


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
