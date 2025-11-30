package com.malec.awsm.tc.base

import com.malec.awsm.ASM
import com.malec.awsm.KotlinPsiTest
import com.malec.awsm.KotlinToAsmInterpreter
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Path

abstract class TcTest : KotlinPsiTest() {
    open val isaSpecPath: Path = Path.of("isa_spec/Overture.isa")

    fun assertEquals(original: String, expected: String) {
        val ktFile = parseFile(original)
        val interpreter = KotlinToAsmInterpreter.Companion.fromSpecFile(isaSpecPath)
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