package com.malec.awsm

import com.malec.awsm.isa.IsaDialect
import com.malec.awsm.isa.IsaParser
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Path

class KotlinToAsmInterpreter(
    private val dialect: IsaDialect
) {
    private val emitter = AsmEmitter()
    private val optimizer = AsmOptimizer(dialect)

    fun interpret(ktFile: KtFile): List<ASM> {
        val translator = KtFileTranslator(emitter, dialect)
        translator.translate(ktFile)
        val optimized = optimizer.optimize(emitter.instructions)
        return optimized
    }

    companion object {
        fun fromSpecFile(path: Path): KotlinToAsmInterpreter {
            val dialect = IsaParser().parseFile(path)
            return KotlinToAsmInterpreter(dialect)
        }
    }
}
