package xyz.qalcyo.convertsrg.types

class ClassType(
    val name: String,
    val pkg: String,
) {
    val fields = mutableMapOf<String, Field>()
    val methods = mutableListOf<Method>()
}
