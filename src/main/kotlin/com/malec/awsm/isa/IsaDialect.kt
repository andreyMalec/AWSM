package com.malec.awsm.isa

import com.malec.awsm.ASM
import com.malec.awsm.Argument
import java.util.Locale

/**
 * Represents a parsed ISA specification with its field definitions and instruction syntax metadata.
 */
data class IsaDialect(
    val name: String,
    val settings: Settings,
    val fields: Map<String, FieldDefinition>,
    val instructions: Map<String, List<InstructionDefinition>>
) {
    data class Settings(
        val variant: String?,
        val endianness: Endianness,
        val lineComments: Set<String>,
        val blockComments: Map<String, String>
    ) {
        enum class Endianness { BIG, LITTLE }
    }

    private val registerEntries: Map<String, FieldEntry> = fields["register"]?.entries.orEmpty()
    private val registerCache: Map<String, Argument.Register> = registerEntries.mapValues { (symbol, entry) ->
        Argument.Register(symbol, entry.bits)
    }

    fun register(name: String): Argument.Register {
        return registerCache[name.lowercase(Locale.ENGLISH)]
            ?: registerCache[name]
            ?: throw IllegalArgumentException("Register '$name' is not defined in dialect '$name'")
    }

    fun instruction(name: String, operands: List<Argument>): ASM.Instruction {
        val signature = instructions[name.lowercase(Locale.ENGLISH)]
            ?: throw IllegalArgumentException("Instruction '$name' is not defined in dialect '$name'")
        val definition = signature.firstOrNull { it.matches(operands) }
            ?: throw IllegalArgumentException("No overload of '$name' matches operands ${operands.joinToString()} in dialect '$name'")
        val mapping = linkedMapOf<String, Argument>()
        definition.operands.forEachIndexed { index, operandDefinition ->
            mapping[operandDefinition.name] = operands.getOrNull(index)
                ?: error("Missing operand ${operandDefinition.name} for '$name'")
        }
        return ASM.Instruction(definition, mapping)
    }

    data class FieldDefinition(
        val name: String,
        val entries: Map<String, FieldEntry>
    )

    data class FieldEntry(
        val symbol: String,
        val bits: String
    )

    data class InstructionDefinition(
        val name: String,
        val syntaxTokens: List<SyntaxToken>,
        val operands: List<OperandDefinition>,
        val virtualOperands: List<VirtualOperand>,
        val assertions: List<Assertion>,
        val bitPattern: String,
        val description: String?
    ) {
        data class OperandDefinition(
            val name: String,
            val placeholder: String,
            val size: OperandSize,
            val fields: List<String>
        ) {
            fun accepts(argument: Argument): Boolean {
                if (fields.isEmpty()) return true
                return fields.any { field ->
                    when (field.lowercase(Locale.ENGLISH)) {
                        "register" -> argument.kind == Argument.Kind.REGISTER
                        "immediate" -> argument.kind == Argument.Kind.IMMEDIATE
                        "label" -> argument.kind == Argument.Kind.LABEL
                        "address" -> argument.kind == Argument.Kind.ADDRESS
                        else -> true
                    }
                }
            }
        }

        data class OperandSize(val signed: Boolean, val bits: Int)

        data class VirtualOperand(val name: String, val size: OperandSize, val expression: String)

        data class Assertion(val left: String, val right: String, val message: String?)

        sealed interface SyntaxToken {
            data class Literal(val value: String) : SyntaxToken
            data class Placeholder(val operandName: String) : SyntaxToken
        }

        fun matches(arguments: List<Argument>): Boolean {
            if (operands.size != arguments.size) return false
            return operands.zip(arguments).all { (definition, argument) -> definition.accepts(argument) }
        }

        fun format(arguments: Map<String, Argument>): String {
            return syntaxTokens.joinToString(separator = " ") { token ->
                when (token) {
                    is SyntaxToken.Literal -> token.value
                    is SyntaxToken.Placeholder -> arguments[token.operandName]?.toString()
                        ?: error("Missing argument ${token.operandName}")
                }
            }
        }
    }
}
