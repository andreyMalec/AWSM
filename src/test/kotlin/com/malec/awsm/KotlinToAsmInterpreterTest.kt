package com.malec.awsm

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

internal class KotlinToAsmInterpreterTest : KotlinPsiTest() {
    @Test
    fun translates_variable_declarations_and_assignments() {
        val code = """
            fun main() {
                var a = 1
                var b = 2
                a = a + b
                b += 3
                a--
            }
        """
        val ktFile = parseFile(code)
        val interpreter = KotlinToAsmInterpreter()
        val asm = interpreter.interpret(ktFile)
        println("===== ASM =====")
        println(asm.joinToString { "\n$it" })
        println("===== FILTERED ASM =====")
        assertThat(asm.filterNot { it is ASM.LABEL }).containsSequence(
            listOf(
                ASM.MOV(Argument.Register.R13, Argument.Value.Number(1)),
                ASM.STORE(Argument.Address(0), Argument.Register.R13),
                ASM.MOV(Argument.Register.R13, Argument.Value.Number(2)),
                ASM.STORE(Argument.Address(2), Argument.Register.R13)
            )
        )
    }
}
