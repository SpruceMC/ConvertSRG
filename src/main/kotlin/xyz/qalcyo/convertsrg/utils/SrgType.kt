package xyz.qalcyo.convertsrg.utils

enum class SrgType(val acronym: String) {
    Package("PK"),
    Class("CL"),
    Field("FD"),
    Method("MD");

    companion object {
        fun find(line: String): SrgType? {
            for (type in values()) {
                if (line.startsWith(type.acronym))
                    return type
            }
            return null
        }
    }
}
