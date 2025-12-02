package com.malec.awsm

import com.malec.awsm.isa.IsaParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Path

internal class AsmOptimizerTest {
    private val dialect = IsaParser().parseFile(Path.of("isa_spec/Overture.isa"))
    private val optimizer = AsmOptimizer(dialect)

    @Test
    fun removes_redundant_moves() {
        val instructions = listOf(
            mov("r5", "r0"),
            mov("out", "r5")
        )
        val optimized = optimizer.optimize(instructions)
        assertThat(optimized).hasSize(1)
        val single = optimized.single() as ASM.Instruction
        assertThat(single.destinationRegisterName()).isEqualTo("out")
        assertThat(single.sourceRegisterName()).isEqualTo("r0")
    }

    @Test
    fun removes_duplicate_immediate_loads() {
        val instructions = listOf(
            im(2),
            mov("out", dialect.specialRegisters.immediate!!.name),
            im(2),
            mov("out", dialect.specialRegisters.immediate!!.name),
            im(2),
            mov("out", dialect.specialRegisters.immediate!!.name)
        )
        val optimized = optimizer.optimize(instructions)
        assertThat(optimized.count { it is ASM.Instruction && it.name == "im" }).isEqualTo(1)
        assertThat(optimized.size).isEqualTo(4)
    }

    private fun mov(dst: String, src: String): ASM.Instruction {
        val definition = dialect.instructionDefinitions("mov").first { it.operands.size == 2 }
        val operands = linkedMapOf(
            definition.operands[0].placeholder to register(dst) as Argument,
            definition.operands[1].placeholder to register(src)
        )
        return ASM.Instruction(definition, operands)
    }

    private fun im(value: Int): ASM.Instruction {
        val definition = dialect.instructionDefinitions("im").first()
        val operands = linkedMapOf(definition.operands[0].placeholder to Argument.Immediate(value) as Argument)
        return ASM.Instruction(definition, operands)
    }

    private fun register(name: String): Argument.Register =
        dialect.registers().firstOrNull { it.name == name }
            ?: dialect.specialRegisters.output?.takeIf { it.name == name }
            ?: dialect.specialRegisters.input?.takeIf { it.name == name }
            ?: dialect.specialRegisters.immediate?.takeIf { it.name == name }
            ?: error("Unknown register $name")

    private fun ASM.Instruction.destinationRegisterName(): String? =
        (operands[definition.operands[0].placeholder] as? Argument.Register)?.name

    private fun ASM.Instruction.sourceRegisterName(): String? =
        (operands[definition.operands[1].placeholder] as? Argument.Register)?.name
}
