package com.malec.awsm

import com.malec.awsm.isa.IsaDialect

internal class AsmOptimizer(private val dialect: IsaDialect) {
    private val passes: List<AsmOptimizationPass> = listOf(
        RedundantMovePass(),
        ImmediateLoadDedupPass()
    )

    fun optimize(instructions: List<ASM>): List<ASM> {
        if (instructions.isEmpty()) return instructions
        val mutable = instructions.toMutableList()
        var iterations = 0
        while (iterations < MAX_ITERATIONS) {
            var changed = false
            passes.forEach { pass ->
                if (pass.apply(mutable, dialect)) {
                    changed = true
                }
            }
            if (!changed) break
            iterations++
        }
        return mutable
    }

    private fun interface AsmOptimizationPass {
        fun apply(instructions: MutableList<ASM>, dialect: IsaDialect): Boolean
    }

    private class RedundantMovePass : AsmOptimizationPass {
        override fun apply(instructions: MutableList<ASM>, dialect: IsaDialect): Boolean {
            var changed = false
            var index = 0
            while (index < instructions.size - 1) {
                val first = instructions[index] as? ASM.Instruction
                if (first == null || !first.isMov()) {
                    index++
                    continue
                }
                val tempRegister = first.destinationRegister()
                val sourceRegister = first.sourceRegister()
                if (tempRegister == null || sourceRegister == null) {
                    index++
                    continue
                }
                val second = instructions[index + 1] as? ASM.Instruction
                if (second == null || !second.isMov()) {
                    index++
                    continue
                }
                val secondDestination = second.destinationRegister()
                val secondSource = second.sourceRegister()
                if (secondDestination == null || secondSource == null) {
                    index++
                    continue
                }
                if (!secondSource.sameRegister(tempRegister)) {
                    index++
                    continue
                }
                if (tempRegister.sameRegister(secondDestination)) {
                    index++
                    continue
                }
                if (registerUsedLaterAsSource(instructions, tempRegister, index + 2)) {
                    index++
                    continue
                }
                second.replaceSourceRegister(sourceRegister)
                instructions.removeAt(index)
                changed = true
                continue
            }
            return changed
        }

        private fun registerUsedLaterAsSource(
            instructions: List<ASM>,
            register: Argument.Register,
            startIndex: Int
        ): Boolean {
            for (i in startIndex until instructions.size) {
                val instruction = instructions[i] as? ASM.Instruction ?: continue
                if (!instruction.isMov()) continue
                val dest = instruction.destinationRegister()
                if (dest != null && dest.sameRegister(register)) return false
                val source = instruction.sourceRegister()
                if (source != null && source.sameRegister(register)) return true
            }
            return false
        }
    }

    private class ImmediateLoadDedupPass : AsmOptimizationPass {
        override fun apply(instructions: MutableList<ASM>, dialect: IsaDialect): Boolean {
            val immediateRegister = dialect.specialRegisters.immediate ?: return false
            var cachedValue: Argument? = null
            var index = 0
            var changed = false
            while (index < instructions.size) {
                val instruction = instructions[index] as? ASM.Instruction
                if (instruction == null) {
                    index++
                    continue
                }
                when {
                    instruction.isImmediateLoad() -> {
                        val value = instruction.argumentAt(0)
                        if (value != null && cachedValue != null && cachedValue == value) {
                            instructions.removeAt(index)
                            changed = true
                            continue
                        } else {
                            cachedValue = value
                        }
                    }
                    instruction.clobbers(immediateRegister) -> {
                        cachedValue = null
                    }
                }
                index++
            }
            return changed
        }
    }

    companion object {
        private const val MAX_ITERATIONS = 5
    }
}

private fun ASM.Instruction.isMov(): Boolean = name.equals("mov", ignoreCase = true)

private fun ASM.Instruction.isImmediateLoad(): Boolean = name.equals("im", ignoreCase = true)

private fun ASM.Instruction.destinationRegister(): Argument.Register? =
    argumentAtOrNull(0) as? Argument.Register

private fun ASM.Instruction.sourceRegister(): Argument.Register? =
    argumentAtOrNull(1) as? Argument.Register

private fun ASM.Instruction.argumentAt(index: Int): Argument? = argumentAtOrNull(index)

private fun ASM.Instruction.argumentAtOrNull(index: Int): Argument? {
    val operand = definition.operands.getOrNull(index) ?: return null
    return operands[operand.placeholder]
}

private fun ASM.Instruction.replaceSourceRegister(register: Argument.Register) {
    val operand = definition.operands.getOrNull(1) ?: return
    operands[operand.placeholder] = register
}

private fun ASM.Instruction.clobbers(register: Argument.Register): Boolean {
    if (isImmediateLoad() && destinationRegister()?.sameRegister(register) != false) {
        return true
    }
    if (isMov()) {
        val dest = destinationRegister()
        if (dest != null && dest.sameRegister(register)) return true
    }
    return false
}

private fun Argument.Register.sameRegister(other: Argument.Register): Boolean =
    name.equals(other.name, ignoreCase = false)
