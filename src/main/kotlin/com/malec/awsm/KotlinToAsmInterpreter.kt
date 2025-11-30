package com.malec.awsm

import com.malec.awsm.isa.IsaDialect
import com.malec.awsm.isa.IsaParser
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Path

class KotlinToAsmInterpreter(
    private val dialect: IsaDialect
) {
    fun interpret(ktFile: KtFile): List<ASM> {
        val emitter = AsmEmitter()
        KtFileTranslator(emitter, dialect).translate(ktFile)
        return emitter.instructions
    }

    companion object {
        fun fromSpecFile(path: Path): KotlinToAsmInterpreter {
            val dialect = IsaParser().parseFile(path)
            return KotlinToAsmInterpreter(dialect)
        }
    }
}
