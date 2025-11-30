package com.malec.awsm

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Path

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
        val interpreter = KotlinToAsmInterpreter.fromSpecFile(Path.of("isa_spec/Overture.isa"))
        val asm = interpreter.interpret(ktFile)
        println("===== ASM =====")
        println(asm.joinToString { "\n$it" })
        println("===== FILTERED ASM =====")
        assertThat(asm.filterNot { it is ASM.Label }).isNotEmpty
    }
}
