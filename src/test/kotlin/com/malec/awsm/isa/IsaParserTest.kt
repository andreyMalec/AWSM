package com.malec.awsm.isa

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class IsaParserTest {
    private val parser = IsaParser()

    @Test
    fun parses_fields_and_instructions() {
        val spec = """
            [fields]
            
            register
            r0 000
            r1 001
            
            [instructions]
            
            mov %a(register), %b(register)
            00aaabbb
            Move
        """.trimIndent()
        val dialect = parser.parse("test", spec)
        assertThat(dialect.fields).containsKey("register")
        assertThat(dialect.instructions).containsKey("mov")
        val movOverloads = dialect.instructions.getValue("mov")
        assertThat(movOverloads).hasSize(1)
        assertThat(movOverloads.first().operands).hasSize(2)
    }
}
