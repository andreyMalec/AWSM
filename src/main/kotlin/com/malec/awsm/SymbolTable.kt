package com.malec.awsm

import java.util.ArrayDeque

internal class SymbolTable(private val registerPool: RegisterPool = RegisterPool()) {
    private val variables = linkedMapOf<String, Symbol>()

    fun declare(name: String, mutable: Boolean): Symbol {
        val register = registerPool.acquire()
        return Symbol(name, mutable, register).also { variables[name] = it }
    }

    fun require(name: String): Symbol = variables[name]
        ?: throw IllegalStateException("Variable '$name' is not defined")
}

internal data class Symbol(val name: String, val mutable: Boolean, val register: Argument.Register)

internal class RegisterPool(private val available: List<Argument.Register> = emptyList()) {
    private val free = ArrayDeque(available)

    fun acquire(): Argument.Register {
        if (free.isEmpty()) throw IllegalStateException("No registers available for symbols")
        return free.removeFirst()
    }

    fun release(register: Argument.Register) {
        if (register !in free) {
            free.addLast(register)
        }
    }
}
