package com.malec.awsm

import com.malec.awsm.isa.IsaDialect

sealed interface ASM {
    data class Label(val name: String) : ASM {
        override fun toString(): String = "$name:"
    }

    data class Instruction(
        val definition: IsaDialect.InstructionDefinition,
        val operands: LinkedHashMap<Char, Argument>
    ) : ASM {
        val name: String get() = definition.name

        override fun toString(): String {
            return definition.format(operands)
        }
    }
}