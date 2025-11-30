package com.malec.awsm

internal class AsmEmitter {
    private val _instructions = mutableListOf<ASM>()
    val instructions: List<ASM> get() = _instructions

    fun label(name: String) {
        _instructions.add(ASM.Label(name))
    }

    fun emit(instruction: ASM) {
        _instructions.add(instruction)
    }
}
