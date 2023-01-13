package test.exportPublic

import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.reflect.KClass

class AClass

object AObject

enum class AEnum { X,Y,Z }
class EnumProp {
    val e:AEnum? = null
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

interface WithStart {
    val map: Map<*, *>
}

interface WithTypeParameter<T> {
   val prop:T
   fun f(arg:T)
   fun f2():T
}
/*
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
*/