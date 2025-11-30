package com.malec.awsm.expression

import com.malec.awsm.*
import com.malec.awsm.isa.IsaDialect
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import kotlin.math.abs

internal class ExpressionTranslator(
    internal val symbols: SymbolTable,
    private val emitter: AsmEmitter,
    private val dialect: IsaDialect
) {
    private val resultRegister: Argument.Register = dialect.registerPreferences.result ?: dialect.anyRegister()
    private val leftRegister: Argument.Register = dialect.registerPreferences.lhs ?: dialect.anyRegister()
    private val rightRegister: Argument.Register = dialect.registerPreferences.rhs ?: dialect.anyRegister()

    fun assign(target: Symbol, expression: KtExpression) {
        when (val expr = expression.unwrapParentheses()) {
            is KtConstantExpression -> storeConstant(target, expr)
            is KtNameReferenceExpression -> copyVariable(target, expr)
            is KtBinaryExpression -> assignBinaryExpression(target, expr)
            else -> unsupported(expr)
        }
    }

    private fun KtExpression.unwrapParentheses(): KtExpression {
        var current: KtExpression = this
        while (current is KtParenthesizedExpression) {
            current = current.expression ?: break
        }
        return current
    }

    private fun storeConstant(target: Symbol, constant: KtConstantExpression) {
        val value = constant.asIntConstant() ?: unsupported(constant)
        emitConstantStore(target, value)
    }

    private fun copyVariable(target: Symbol, reference: KtNameReferenceExpression) {
        val source = symbols.require(reference.getReferencedName())
        loadToRegister(source, resultRegister)
        storeRegister(target, resultRegister)
    }

    private fun assignBinaryExpression(target: Symbol, expression: KtBinaryExpression) {
        val operation = when (expression.operationToken) {
            KtTokens.PLUS -> Operation.ADD
            KtTokens.MINUS -> Operation.SUB
            else -> unsupported(expression)
        }
        val left = resolveOperand(expression.left?.unwrapParentheses())
        val right = resolveOperand(expression.right?.unwrapParentheses())
        when {
            left is Operand.Constant && right is Operand.Constant -> {
                val result = if (operation == Operation.ADD) left.value + right.value else left.value - right.value
                emitConstantStore(target, result)
            }
            left is Operand.Variable && right is Operand.Variable -> {
                loadToRegister(left.symbol, leftRegister)
                loadToRegister(right.symbol, rightRegister)
                emitBinaryOp(operation, resultRegister, leftRegister, rightRegister)
                storeRegister(target, resultRegister)
            }
            left is Operand.Variable && right is Operand.Constant -> {
                loadToRegister(left.symbol, resultRegister)
                emitBinaryOp(operation, resultRegister, resultRegister, right.value.toArgument())
                storeRegister(target, resultRegister)
            }
            left is Operand.Constant && right is Operand.Variable -> {
                when (operation) {
                    Operation.ADD -> {
                        loadToRegister(right.symbol, resultRegister)
                        emitBinaryOp(Operation.ADD, resultRegister, resultRegister, left.value.toArgument())
                    }
                    Operation.SUB -> {
                        emitConstantLoad(left.value, leftRegister)
                        loadToRegister(right.symbol, rightRegister)
                        emitBinaryOp(Operation.SUB, resultRegister, leftRegister, rightRegister)
                    }
                }
                storeRegister(target, resultRegister)
            }
            else -> unsupported(expression)
        }
    }

    fun augmentedAssignment(target: Symbol, valueExpression: KtExpression, operation: Operation) {
        val operand = resolveOperand(valueExpression)
        loadToRegister(target, resultRegister)
        when (operand) {
            is Operand.Constant -> emitBinaryOp(operation, resultRegister, resultRegister, operand.value.toArgument())
            is Operand.Variable -> {
                loadToRegister(operand.symbol, rightRegister)
                emitBinaryOp(operation, resultRegister, resultRegister, rightRegister)
            }
        }
        storeRegister(target, resultRegister)
    }

    fun increment(variable: Symbol, delta: Int) {
        loadToRegister(variable, resultRegister)
        val operation = if (delta >= 0) Operation.ADD else Operation.SUB
        val magnitude = abs(delta)
        emitBinaryOp(operation, resultRegister, resultRegister, magnitude.toArgument())
        storeRegister(variable, resultRegister)
    }

    private fun resolveOperand(expression: KtExpression?): Operand {
        val expr = expression?.unwrapParentheses() ?: unsupportedExpression("Missing operand")
        return when (expr) {
            is KtNameReferenceExpression -> Operand.Variable(symbols.require(expr.getReferencedName()))
            is KtConstantExpression -> Operand.Constant(expr.asIntConstant() ?: unsupportedExpression("Non-int constant"))
            else -> unsupportedExpression("Unsupported operand: ${expr.text}")
        }
    }

    private fun emitConstantStore(target: Symbol, value: Int) {
        emitConstantLoad(value, resultRegister)
        storeRegister(target, resultRegister)
    }

    private fun emitConstantLoad(value: Int, register: Argument.Register) {
        emitter.emit(dialect.instruction("mov", listOf(register, Argument.Immediate(value))))
    }

    private fun loadToRegister(symbol: Symbol, register: Argument.Register) {
        emitter.emit(dialect.instruction("load", listOf(register, symbol.address)))
    }

    private fun storeRegister(target: Symbol, register: Argument.Register) {
        emitter.emit(dialect.instruction("store", listOf(target.address, register)))
    }

    private fun emitBinaryOp(operation: Operation, destination: Argument.Register, a: Argument.Register, b: Argument) {
        val instructionName = when (operation) {
            Operation.ADD -> "add"
            Operation.SUB -> "sub"
        }
        emitter.emit(dialect.instruction(instructionName, listOf(destination, a, b)))
    }

    private fun unsupported(element: KtExpression): Nothing {
        throw UnsupportedOperationException(element.text)
    }

    private fun unsupportedExpression(message: String): Nothing {
        throw UnsupportedOperationException(message)
    }

    private fun KtExpression.asIntConstant(): Int? {
        return when (this) {
            is KtConstantExpression -> text.replace("_", "").toIntOrNull()
            is KtPrefixExpression -> {
                val value = baseExpression?.asIntConstant() ?: return null
                when (operationReference.getReferencedNameElementType()) {
                    KtTokens.MINUS -> -value
                    KtTokens.PLUS -> value
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun Int.toArgument(): Argument = Argument.Immediate(this)
}

internal enum class Operation { ADD, SUB }

internal sealed interface Operand {
    data class Variable(val symbol: Symbol) : Operand
    data class Constant(val value: Int) : Operand
}
