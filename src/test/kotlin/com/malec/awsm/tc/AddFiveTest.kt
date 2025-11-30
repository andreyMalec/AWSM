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
        val expected = """
            mov r4, in
            mov r1, r4
            im 5
            mov r2, r0
            add
            mov r4, r3
            mov out, r4
        """.trimIndent()
        val ktFile = parseFile(code)
        val interpreter = KotlinToAsmInterpreter.Companion.fromSpecFile(Path.of("isa_spec/Overture.isa"))
        val asm = interpreter.interpret(ktFile)
        println("===== ASM =====")
        asm.forEach {
            println(it)
        }
        println("===== ASM =====")
        val filtered = asm.filterNot { it is ASM.Label }
        assertThat(filtered).isNotEmpty

        val actual = filtered.map { it.toString().trim() }
        val expectedList = expected
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        assertThat(actual.size)
            .withFailMessage("Instruction count mismatch: expected ${expectedList.size}, but was ${actual.size}")
            .isEqualTo(expectedList.size)

        expectedList.forEachIndexed { index, expectedInstruction ->
            assertThat(actual[index])
                .withFailMessage(
                    "Instruction mismatch at line ${index + 1}: expected '$expectedInstruction' but was '${actual[index]}'"
                )
                .isEqualTo(expectedInstruction)
        }
    }
}
