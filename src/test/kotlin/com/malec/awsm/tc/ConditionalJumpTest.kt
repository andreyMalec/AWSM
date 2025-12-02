package com.malec.awsm.tc

import com.malec.awsm.tc.base.TcTest
import org.junit.Test

internal class ConditionalJumpTest : TcTest() {
    @Test
    fun test1() {
        val code = """
            fun main() {
                var a = 0
                while(input() != 37) {
                    a++
                }
                a++
                output(a)
            }
        """
        val expected = """
            main:
            L0_main:
            im 0
            mov r5, r0
            L1_main:
            L2_main:
            mov r4, in
            mov r1, r4
            im 37
            mov r2, r0
            sub
            im L3_main
            je
            mov r1, r5
            im 1
            mov r2, r0
            add
            mov r5, r3
            im L2_main
            jmp
            L3_main:
            L4_main:
            mov r1, r5
            im 1
            mov r2, r0
            add
            mov r5, r3
            L5_main:
            mov out, r5
            L6_main:
        """.trimIndent()
        assertEquals(original = code, expected = expected)
    }

    @Test
    fun test2() {
        val code = """
            fun main() {
                var a = 0
                do {
                    a++
                } while(input() != 37)
                output(a)
            }
        """
        val expected = """
            main:
            L0_main:
            im 0
            mov r5, r0
            L1_main:
            L2_main:
            mov r1, r5
            im 1
            mov r2, r0
            add
            mov r5, r3
            L3_main:
            mov r4, in
            mov r1, r4
            im 37
            mov r2, r0
            sub
            im L4_main
            je
            im L2_main
            jmp
            L4_main:
            L5_main:
            mov out, r5
            L6_main:
        """.trimIndent()
        assertEquals(original = code, expected = expected)
    }
}
