package com.malec.awsm.tc

import com.malec.awsm.tc.base.TcTest
import org.junit.Test

internal class MaskingTimeTest : TcTest() {
    @Test
    fun test_and() {
        val code = """
            fun main() {
                var a = input()
                a = a and 3
                output(a)
            }
        """
        val expected = """
            main:
            L0_main:
            mov r5, in
            L1_main:
            mov r1, r5
            im 3
            mov r2, r0
            and
            mov r5, r3
            L2_main:
            mov out, r5
            L3_main:
        """.trimIndent()
        assertEquals(original = code, expected = expected)
    }

    @Test
    fun test_or() {
        val code = """
            fun main() {
                var a = input()
                a = a or 5
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
            or
            mov r5, r3
            L2_main:
            mov out, r5
            L3_main:
        """.trimIndent()
        assertEquals(original = code, expected = expected)
    }

    @Test
    fun test_nor() {
        val code = """
            fun main() {
                var a = input()
                a = a nor 10
                output(a)
            }
        """
        val expected = """
            main:
            L0_main:
            mov r5, in
            L1_main:
            mov r1, r5
            im 10
            mov r2, r0
            nor
            mov r5, r3
            L2_main:
            mov out, r5
            L3_main:
        """.trimIndent()
        assertEquals(original = code, expected = expected)
    }

    @Test
    fun test_nand() {
        val code = """
            fun main() {
                var a = input()
                a = a nand 20
                output(a)
            }
        """
        val expected = """
            main:
            L0_main:
            mov r5, in
            L1_main:
            mov r1, r5
            im 20
            mov r2, r0
            nand
            mov r5, r3
            L2_main:
            mov out, r5
            L3_main:
        """.trimIndent()
        assertEquals(original = code, expected = expected)
    }
}
