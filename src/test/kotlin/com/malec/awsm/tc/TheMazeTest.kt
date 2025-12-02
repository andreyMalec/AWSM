package com.malec.awsm.tc

import com.malec.awsm.tc.base.TcTest
import org.junit.Test

internal class TheMazeTest : TcTest() {
    @Test
    fun test1() {
        val code = """
fun main() {
    while (true) {

        // try right
        output(2)
        var c = input()
        if (c == 1) { output(4); c = input() }
        if (c == 0) { output(1); continue }
        output(0) // restore dir

        // try forward
        c = input()
        if (c == 1) { output(4); c = input() }
        if (c == 0) { output(1); continue }

        // try left
        output(0)
        c = input()
        if (c == 1) { output(4); c = input() }
        if (c == 0) { output(1); continue }
        output(2) // restore dir

        // dead end → turn around
        output(2); output(2)
        c = input()
        if (c == 1) { output(4); c = input() }
        if (c == 0) { output(1); continue }
    }
}
        """
        val expected = """
            
        """.trimIndent()
        assertEquals(original = code, expected = expected)
    }
}
