package com.malec.awsm.tc

import com.malec.awsm.tc.base.TcTest
import org.junit.Test

internal class AddFiveTest : TcTest() {
    @Test
    fun add_five() {
        val code = """
            fun main() {
                var a = input()
                a += 5
                output(a)
            }
        """
        val expected = """
            main:
            L0_main:
            mov r5, in
            L1_main:
            mov r1, r5
            im 5
            mov r2, r0
            add
            mov r5, r3
            L2_main:
            mov out, r5
            L3_main:
        """.trimIndent()
        assertEquals(original = code, expected = expected)
    }

    @Test
    fun add_vide_with_one_line() {
        val code = """
            fun main() {
                output(input() + 5)
            }
        """
        val expected = """
            main:
            L0_main:
            mov r4, in
            mov r1, r4
            im 5
            mov r2, r0
            add
            mov r5, r3
            mov out, r5
            L1_main:
        """.trimIndent()
        assertEquals(original = code, expected = expected)
    }
}
