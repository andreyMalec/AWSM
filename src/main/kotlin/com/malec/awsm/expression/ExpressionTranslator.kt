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
        loadToRegister(source, RESULT_REGISTER)
        storeRegister(target, RESULT_REGISTER)
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
                loadToRegister(left.symbol, LEFT_REGISTER)
                loadToRegister(right.symbol, RIGHT_REGISTER)
                emitBinaryOp(operation, RESULT_REGISTER, LEFT_REGISTER, RIGHT_REGISTER)
                storeRegister(target, RESULT_REGISTER)
            }
            left is Operand.Variable && right is Operand.Constant -> {
                loadToRegister(left.symbol, RESULT_REGISTER)
                emitBinaryOp(operation, RESULT_REGISTER, RESULT_REGISTER, right.value.toArgument())
                storeRegister(target, RESULT_REGISTER)
            }
            left is Operand.Constant && right is Operand.Variable -> {
                when (operation) {
                    Operation.ADD -> {
                        loadToRegister(right.symbol, RESULT_REGISTER)
                        emitBinaryOp(Operation.ADD, RESULT_REGISTER, RESULT_REGISTER, left.value.toArgument())
                    }
                    Operation.SUB -> {
                        emitConstantLoad(left.value, LEFT_REGISTER)
                        loadToRegister(right.symbol, RIGHT_REGISTER)
                        emitBinaryOp(Operation.SUB, RESULT_REGISTER, LEFT_REGISTER, RIGHT_REGISTER)
                    }
                }
                storeRegister(target, RESULT_REGISTER)
            }
            else -> unsupported(expression)
        }
    }

    fun augmentedAssignment(target: Symbol, valueExpression: KtExpression, operation: Operation) {
        val operand = resolveOperand(valueExpression)
        loadToRegister(target, RESULT_REGISTER)
        when (operand) {
            is Operand.Constant -> emitBinaryOp(operation, RESULT_REGISTER, RESULT_REGISTER, operand.value.toArgument())
            is Operand.Variable -> {
                loadToRegister(operand.symbol, RIGHT_REGISTER)
                emitBinaryOp(operation, RESULT_REGISTER, RESULT_REGISTER, RIGHT_REGISTER)
            }
        }
        storeRegister(target, RESULT_REGISTER)
    }

    fun increment(variable: Symbol, delta: Int) {
        loadToRegister(variable, RESULT_REGISTER)
        val operation = if (delta >= 0) Operation.ADD else Operation.SUB
        val magnitude = abs(delta)
        emitBinaryOp(operation, RESULT_REGISTER, RESULT_REGISTER, magnitude.toArgument())
        storeRegister(variable, RESULT_REGISTER)
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
        emitConstantLoad(value, RESULT_REGISTER)
        storeRegister(target, RESULT_REGISTER)
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

    companion object {
        private val RESULT_REGISTER = Argument.Register("r13")
        private val LEFT_REGISTER = Argument.Register("r1")
        private val RIGHT_REGISTER = Argument.Register("r2")
    }
}

internal enum class Operation { ADD, SUB }

internal sealed interface Operand {
    data class Variable(val symbol: Symbol) : Operand
    data class Constant(val value: Int) : Operand
}
