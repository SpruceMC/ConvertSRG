package xyz.qalcyo.convertsrg.types

class ClassType(val name: String) {
    val fields = mutableMapOf<String, Field>()
    val methods = mutableMapOf<String, Method>()
}
