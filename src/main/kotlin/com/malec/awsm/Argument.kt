package com.malec.awsm

sealed interface Argument {
    val kind: Kind

    enum class Kind { REGISTER, IMMEDIATE, LABEL, ADDRESS }

    data class Register(val name: String, val code: String? = null) : Argument {
        override val kind: Kind = Kind.REGISTER
        override fun toString(): String = name
    }

    data class Immediate(val value: Int) : Argument {
        override val kind: Kind = Kind.IMMEDIATE
        override fun toString(): String = value.toString()
    }

    data class Label(val name: String) : Argument {
        override val kind: Kind = Kind.LABEL
        override fun toString(): String = name
    }

    data class Address(val value: Int) : Argument {
        override val kind: Kind = Kind.ADDRESS
        override fun toString(): String = "[$value]"
    }
}