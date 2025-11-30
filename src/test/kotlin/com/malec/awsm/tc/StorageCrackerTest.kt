package com.malec.awsm.tc

import com.malec.awsm.tc.base.TcTest
import org.junit.Test

internal class StorageCrackerTest : TcTest() {
    @Test
    fun test1() {
        val code = """
            fun main() {
                var a = 0
                while(true) {
                    output(a)
                    a++
                }
            }
        """
        val expected = """
            main:
            L0_main:
            im 0
            mov r5, r0
            L1_main:
            L2_main:
            mov out, r5
            mov r1, r5
            im 1
            mov r2, r0
            add
            mov r5, r3
            im L2_main
            jmp
            L3_main:
            L4_main:
        """.trimIndent()
        assertEquals(original = code, expected = expected)
    }
}
