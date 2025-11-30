package com.malec.awsm

import org.jetbrains.kotlin.psi.KtFile

class KotlinToAsmInterpreter {
    fun interpret(ktFile: KtFile): List<ASM> {
        val emitter = AsmEmitter()
        KtFileTranslator(emitter).translate(ktFile)
        return emitter.instructions
    }
}
