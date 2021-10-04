package xyz.qalcyo.convertsrg.types

class PackageType(val name: String) {
    val classes = mutableMapOf<String, ClassType>()
}
