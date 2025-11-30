package com.malec.awsm

sealed interface Argument {
    open class Register(val index: Int) : Argument {
        override fun toString(): String {
            return if (index > 0)
                "r$index"
            else
                "zr"
        }

        object ZR : Register(0)
        object R1 : Register(1)
        object R2 : Register(2)
        object R3 : Register(3)
        object R4 : Register(4)
        object R5 : Register(5)
        object R6 : Register(6)
        object R7 : Register(7)
        object R8 : Register(8)
        object R9 : Register(9)
        object R10 : Register(10)
        object R11 : Register(11)
        object R12 : Register(12)
        object R13 : Register(13)
        object SP : Register(14)
        object FLAGS : Register(15)
    }

    sealed class Value(open val value: Any) : Argument {
        final override fun toString(): String {
            return value.toString()
        }

        data class Label(val name: String) : Value(name)
        data class Number(override val value: Int) : Value(value)
    }

    data class Address(val value: Int) : Argument {
        override fun toString(): String {
            return "[${value}]"
        }
    }

    private val String.functionName: String
        get() {
            val dot = indexOf(".").takeIf { it > 0 }?.plus(1) ?: 0
            return substring(dot)
        }
}