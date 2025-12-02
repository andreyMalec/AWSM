package com.malec.awsm.tc

import com.malec.awsm.tc.base.TcTest
import org.junit.Test

internal class TheMazeTest : TcTest() {
    @Test
    fun test1() {
        val code = """
            fun main() {
                while (true) {
                    var cell = input()   
            
                    if (cell == 1) { 
                        output(4)
                        output(2)
                    }
            
                    if (cell == 0) {
                        output(1)
                        output(0)
                    }
                }
            }
        """
        val expected = """
            main:
            L0_main:
            L1_main:
            mov r5, in
            mov r1, r5
            im 1
            mov r2, r0
            sub
            im L3_main
            jne
            im 4
            mov out, r0
            im 2
            mov out, r0
            L3_main:
            mov r1, r5
            im 0
            mov r2, r0
            sub
            im L4_main
            jne
            im 1
            mov out, r0
            im 0
            mov out, r0
            L4_main:
            im L1_main
            jmp
            L2_main:
            L5_main:
        """.trimIndent()
        assertEquals(original = code, expected = expected)
    }

    @Test
    fun test2() {
        val code = """
            fun main() {
                while (true) {
                    when (input()) { 
                        1 -> {
                             output(4)
                             output(2)
                        }
                        0 -> {
                            output(1)
                            output(0)
                        }
                        else -> {}
                    }
                }
            }
        """
        val expected = """
            main:
            L0_main:
            L1_main:
            mov r5, in
            mov r1, r5
            im 1
            mov r2, r0
            sub
            im L5_main
            je
            im L6_main
            jmp
            L5_main:
            im 4
            mov out, r0
            im 2
            mov out, r0
            im L3_main
            jmp
            L6_main:
            mov r1, r5
            im 0
            mov r2, r0
            sub
            im L7_main
            je
            im L4_main
            jmp
            L7_main:
            im 1
            mov out, r0
            im 0
            mov out, r0
            im L3_main
            jmp
            L4_main:
            L3_main:
            im L1_main
            jmp
            L2_main:
            L8_main:
        """.trimIndent()
        assertEquals(original = code, expected = expected)
    }
}
