package com.malec.awsm.isa

import com.malec.awsm.ASM
import com.malec.awsm.Argument
import java.util.*

/**
 * Represents a parsed ISA specification with its field definitions and instruction syntax metadata.
 */
data class IsaDialect(
    val name: String,
    val settings: Settings,
    val fields: Map<String, FieldDefinition>,
    val instructions: Map<String, List<InstructionDefinition>>,
    val registerPreferences: RegisterPreferences,
    val specialRegisters: SpecialRegisters
) {
    data class Settings(
        val variant: String?,
        val endianness: Endianness,
        val lineComments: Set<String>,
        val blockComments: Map<String, String>
    ) {
        enum class Endianness { BIG, LITTLE }
    }

    data class RegisterPreferences(
        val lhs: Argument.Register?,
        val rhs: Argument.Register?,
        val result: Argument.Register?
    )

    data class SpecialRegisters(
        val zero: Argument.Register?,
        val stackPointer: Argument.Register?,
        val flags: Argument.Register?,
        val immediate: Argument.Register?,
        val input: Argument.Register?,
        val output: Argument.Register?,
        val custom: Map<String, Argument.Register>
    )

    private val registerEntries: Map<String, FieldEntry> = fields["register"]?.entries.orEmpty()
    private val registerCache: LinkedHashMap<String, Argument.Register> = LinkedHashMap<String, Argument.Register>().apply {
        registerEntries.forEach { (symbol, entry) ->
            put(symbol.lowercase(Locale.ENGLISH), Argument.Register(entry.symbol, entry.bits))
        }
    }
    private val instructionsByName: Map<String, List<InstructionDefinition>> = instructions.mapKeys { it.key.lowercase(Locale.ENGLISH) }
    private val immediateLoadDefinition: InstructionDefinition? = instructionsByName.values
        .flatten()
        .firstOrNull { definition ->
            definition.operands.size == 1 && definition.operands.first().isImmediateOnly()
        }
    private val reservedRegisterNames: Set<String> = buildSet {
        registerPreferences.lhs?.let { add(it.name.lowercase(Locale.ENGLISH)) }
        registerPreferences.rhs?.let { add(it.name.lowercase(Locale.ENGLISH)) }
        registerPreferences.result?.let { add(it.name.lowercase(Locale.ENGLISH)) }
        specialRegisters.zero?.let { add(it.name.lowercase(Locale.ENGLISH)) }
        specialRegisters.stackPointer?.let { add(it.name.lowercase(Locale.ENGLISH)) }
        specialRegisters.flags?.let { add(it.name.lowercase(Locale.ENGLISH)) }
        specialRegisters.immediate?.let { add(it.name.lowercase(Locale.ENGLISH)) }
        specialRegisters.input?.let { add(it.name.lowercase(Locale.ENGLISH)) }
        specialRegisters.output?.let { add(it.name.lowercase(Locale.ENGLISH)) }
        specialRegisters.custom.values.forEach { add(it.name.lowercase(Locale.ENGLISH)) }
    }

    fun registers(): List<Argument.Register> = registerCache
        .filterKeys { it !in reservedRegisterNames }
        .values
        .toList()

    fun immediateLoad(value: Argument): ASM.Instruction? {
        val definition = immediateLoadDefinition ?: return null
        return instruction(definition.name, listOf(value))
    }

    fun register(name: String): Argument.Register {
        return registerCache[name.lowercase(Locale.ENGLISH)]
            ?: registerCache[name]
            ?: throw IllegalArgumentException("Register '$name' is not defined in dialect '$name'")
    }

    fun anyRegister(): Argument.Register {
        return registers().firstOrNull()
            ?: throw IllegalStateException("Dialect '$name' does not define general-purpose registers")
    }

    fun instruction(name: String, operands: List<Argument>): ASM.Instruction {
        val normalized = name.lowercase(Locale.ENGLISH)
        val signature = instructionsByName[normalized]
            ?: throw IllegalArgumentException("Instruction '$name' is not defined in dialect '${this.name}'")
        val definition = signature.firstOrNull { it.matches(operands) }
            ?: throw IllegalArgumentException("No overload of '$name' matches operands ${operands.joinToString()} in dialect '${this.name}'")
        val mapping = linkedMapOf<Char, Argument>()
        definition.operands.forEachIndexed { index, operandDefinition ->
            mapping[operandDefinition.placeholder] = operands.getOrNull(index)
                ?: error("Missing operand ${operandDefinition.name} for '$name'")
        }
        return ASM.Instruction(definition, mapping)
    }

    fun hasInstruction(name: String): Boolean = instructionsByName.containsKey(name.lowercase(Locale.ENGLISH))

    fun instructionDefinitions(name: String): List<InstructionDefinition> =
        instructionsByName[name.lowercase(Locale.ENGLISH)] ?: emptyList()

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
        val syntax: String,
        val operands: List<OperandDefinition>,
        val virtualOperands: List<VirtualOperand>,
        val assertions: List<Assertion>,
        val bitPattern: String,
        val description: String?
    ) {
        data class OperandDefinition(
            val name: String,
            val placeholder: Char,
            val size: OperandSize,
            val fields: List<String>
        ) {
            data class OperandSize(val signed: Boolean, val bits: Int)

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

            fun isImmediateOnly(): Boolean = fields.isNotEmpty() && fields.all { field ->
                val lower = field.lowercase(Locale.ENGLISH)
                lower == "immediate" || lower == "label"
            }
        }

        data class VirtualOperand(val name: String, val size: OperandDefinition.OperandSize, val expression: String)

        data class Assertion(val left: String, val right: String, val message: String?)

        fun matches(arguments: List<Argument>): Boolean {
            if (operands.size != arguments.size) return false
            return operands.zip(arguments).all { (definition, argument) -> definition.accepts(argument) }
        }

        fun format(arguments: Map<Char, Argument>): String {
            var rendered = syntax
            operands.forEach { operand ->
                val value = arguments[operand.placeholder]
                    ?: error("Missing argument ${operand.name}")
                val regex = Regex("%${operand.placeholder}\\([^)]*\\)")
                rendered = regex.replace(rendered, value.toString())
            }
            return rendered
        }
    }
}
