package com.malec.awsm.tc

import com.malec.awsm.ASM
import com.malec.awsm.KotlinPsiTest
import com.malec.awsm.KotlinToAsmInterpreter
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Path

internal class AddFiveTest : KotlinPsiTest() {
    @Test
    fun translates_variable_declarations_and_assignments() {
        val code = """
            fun main() {
                var a = input()
                a += 5
                output(a)
            }
        """
        val ktFile = parseFile(code)
        val interpreter = KotlinToAsmInterpreter.Companion.fromSpecFile(Path.of("isa_spec/Overture.isa"))
        val asm = interpreter.interpret(ktFile)
        println("===== ASM =====")
        asm.forEach {
            println(it)
        }
        println("===== ASM =====")
        assertThat(asm.filterNot { it is ASM.Label }).isNotEmpty
    }
}
