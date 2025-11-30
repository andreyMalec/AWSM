package com.malec.awsm

internal class SymbolTable {
    private val variables = linkedMapOf<String, Symbol>()
    private var nextAddressSlot = 0

    fun declare(name: String, mutable: Boolean): Symbol {
        val address = Argument.Address(nextAddressSlot++ * MEM)
        return Symbol(name, mutable, address).also { variables[name] = it }
    }

    fun require(name: String): Symbol = variables[name]
        ?: throw IllegalStateException("Variable '$name' is not defined")
}

internal data class Symbol(val name: String, val mutable: Boolean, val address: Argument.Address)

