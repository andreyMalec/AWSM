package com.malec.awsm.tc

import com.malec.awsm.tc.base.TcTest
import org.junit.Test

internal class AddFiveTest2 : TcTest() {
    @Test
    fun translates_variable_declarations_and_assignments() {
        val code = """
            fun main() {
                output(input() + 5)
            }
        """
        val expected = """
            mov r5, in
            mov r1, r5
            im 5
            mov r2, r0
            add
            mov r4, r3
            mov out, r4
        """.trimIndent()
        assertEquals(original = code, expected = expected)
    }
}
