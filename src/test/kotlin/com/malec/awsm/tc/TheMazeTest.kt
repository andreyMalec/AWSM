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
                        continue
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
            im L1_main
            jmp
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
}
