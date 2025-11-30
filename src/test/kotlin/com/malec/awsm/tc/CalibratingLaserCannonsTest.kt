package com.malec.awsm.tc

import com.malec.awsm.tc.base.TcTest
import org.junit.Test

internal class CalibratingLaserCannonsTest : TcTest() {
    @Test
    fun test1() {
        val code = """
            fun main() {
                output(input() * 6)
            }
        """
        val expected = """
            main:
            L0_main:
            mov r4, in
            im 0
            mov r3, r0
            mov r1, r4
            mov r2, r3
            add
            mov r1, r4
            mov r2, r3
            add
            mov r1, r4
            mov r2, r3
            add
            mov r1, r4
            mov r2, r3
            add
            mov r1, r4
            mov r2, r3
            add
            mov r1, r4
            mov r2, r3
            add
            mov r5, r3
            mov out, r5
            L1_main:
        """.trimIndent()
        assertEquals(original = code, expected = expected)
    }

    @Test
    fun test2() {
        val code = """
            fun main() {
                var a = input()
                a = a * 6
                output(a)
            }
        """
        val expected = """
            main:
            L0_main:
            mov r5, in
            L1_main:
            im 0
            mov r3, r0
            mov r1, r5
            mov r2, r3
            add
            mov r1, r5
            mov r2, r3
            add
            mov r1, r5
            mov r2, r3
            add
            mov r1, r5
            mov r2, r3
            add
            mov r1, r5
            mov r2, r3
            add
            mov r1, r5
            mov r2, r3
            add
            mov r5, r3
            L2_main:
            mov out, r5
            L3_main:
        """.trimIndent()
        assertEquals(original = code, expected = expected)
    }

}
