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
            is KtCallExpression -> assignCall(target, expr)
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
        move(source.register, resultRegister)
        move(resultRegister, target.register)
    }

    private fun assignCall(target: Symbol, expression: KtCallExpression) {
        val calleeName = (expression.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
            ?: error("Unsupported call")
        when (calleeName) {
            "input" -> {
                val inRegister = dialect.specialRegisters.input
                    ?: dialect.specialRegisters.immediate
                    ?: error("ISA does not define input register")
                val targetRegister = target.register
                move(inRegister, targetRegister)
            }

            "output" -> {
                val outRegister = dialect.specialRegisters.output
                    ?: error("ISA does not define an output register")
                val targetRegister = target.register
                move(outRegister, targetRegister)
            }

            else -> error("Unsupported call: ${expression.text}")
        }
    }

    private fun assignBinaryExpression(target: Symbol, expression: KtBinaryExpression) {
        val operation = when (expression.operationToken) {
            KtTokens.PLUS -> Operation.ADD
            KtTokens.MINUS -> Operation.SUB
            KtTokens.MUL -> Operation.MUL
            else -> when (expression.operationReference.getReferencedName()) {
                "and" -> Operation.AND
                "or" -> Operation.OR
                "nor" -> Operation.NOR
                "nand" -> Operation.NAND
                else -> unsupported(expression)
            }
        }
        val left = resolveOperand(expression.left?.unwrapParentheses())
        val right = resolveOperand(expression.right?.unwrapParentheses())
        executeBinaryOperation(left, right, target, operation)
    }

    fun augmentedAssignment(target: Symbol, valueExpression: KtExpression, operation: Operation) {
        val operand = resolveOperand(valueExpression)
        executeBinaryOperation(Operand.Variable(target), operand, target, operation)
    }

    fun increment(variable: Symbol, delta: Int) {
        val operand = if (delta >= 0) Operand.Constant(delta) else Operand.Constant(-delta)
        val op = if (delta >= 0) Operation.ADD else Operation.SUB
        executeBinaryOperation(Operand.Variable(variable), operand, variable, op)
    }

    fun resolveOperand(expression: KtExpression?): Operand {
        val expr = expression?.unwrapParentheses() ?: unsupportedExpression("Missing operand")
        return when (expr) {
            is KtNameReferenceExpression -> Operand.Variable(symbols.require(expr.getReferencedName()))
            is KtConstantExpression -> Operand.Constant(
                expr.asIntConstant() ?: unsupportedExpression("Non-int constant")
            )

            else -> {
                val temp =
//                    if (dialect.specialRegisters.immediate != null)
//                    Symbol("__tmp_${expr.hashCode()}", true, dialect.specialRegisters.immediate)
//                else
                    symbols.declare("__tmp_${expr.hashCode()}", mutable = true)
                assign(temp, expr)
                Operand.Variable(temp)
            }
        }
    }

    fun emitMoveToLabel(value: Argument.Label) {
        val immediateInstruction = dialect.immediateLoad(value)
        if (immediateInstruction != null) {
            emitter.emit(immediateInstruction)
            return
        }
    }

    private fun emitConstantStore(target: Symbol, value: Int) {
        emitConstantLoad(value, target.register)
    }

    private fun emitConstantLoad(value: Int, register: Argument.Register) {
        val immediateInstruction = dialect.immediateLoad(Argument.Immediate(value))
        if (immediateInstruction != null) {
            emitter.emit(immediateInstruction)
            val immediateRegister = dialect.specialRegisters.immediate
            if (immediateRegister != null && immediateRegister != register) {
                move(immediateRegister, register)
            }
            return
        }
        val immediateRegister = dialect.specialRegisters.immediate ?: register
        val loadRegister = if (immediateRegister == register) register else immediateRegister
        emitter.emit(dialect.instruction("mov", listOf(loadRegister, Argument.Immediate(value))))
        if (loadRegister != register) {
            move(loadRegister, register)
        }
    }

    private fun move(src: Argument.Register, dst: Argument.Register) {
        if (src == dst) return
        emitter.emit(dialect.instruction("mov", listOf(dst, src)))
    }

    private fun emitBinaryOp(operation: Operation) {
        val instructionName = instructionNameFor(operation)
        val definitions = dialect.instructionDefinitions(instructionName)
        val fixedForm = definitions.any { it.operands.isEmpty() }
        if (fixedForm) {
            emitter.emit(dialect.instruction(instructionName, emptyList()))
        } else {
            emitter.emit(dialect.instruction(instructionName, listOf(resultRegister, leftRegister, rightRegister)))
        }
    }

    private fun executeBinaryOperation(left: Operand, right: Operand, target: Symbol, operation: Operation) {
        if (operation == Operation.MUL && !dialect.hasInstruction("mul")) {
            emitMultiplicationByAddition(left, right, target)
            return
        }
        if (left is Operand.Constant && right is Operand.Constant) {
            val result = when (operation) {
                Operation.ADD -> left.value + right.value
                Operation.SUB -> left.value - right.value
                Operation.MUL -> left.value * right.value
                Operation.AND -> left.value and right.value
                Operation.OR -> left.value or right.value
                Operation.NOR -> left.value nor right.value
                Operation.NAND -> left.value nand right.value
            }
            emitConstantStore(target, result)
            return
        }
        val instructionName = instructionNameFor(operation)
        if (!dialect.hasInstruction(instructionName)) {
            emitBitwiseFallback(left, right, target, operation)
            return
        }
        val definitions = dialect.instructionDefinitions(instructionName)
        val fixedForm = definitions.any { it.operands.isEmpty() }
        if (fixedForm) {
            loadOperandIntoRegister(left, leftRegister)
            loadOperandIntoRegister(right, rightRegister)
            emitBinaryOp(operation)
            move(resultRegister, target.register)
        } else {
            loadOperandIntoRegister(left, resultRegister)
            loadOperandIntoRegister(right, rightRegister)
            emitBinaryOp(operation)
            move(resultRegister, target.register)
        }
    }

    fun emitSubtraction(leftExpression: KtExpression?, rightExpression: KtExpression?) {
        val leftOperand = resolveOperand(leftExpression)
        val rightOperand = resolveOperand(rightExpression)
        loadOperandIntoRegister(leftOperand, leftRegister)
        loadOperandIntoRegister(rightOperand, rightRegister)
        emitBinaryOp(Operation.SUB)
    }

    fun loadOperandInto(register: Argument.Register, expression: KtExpression) {
        val operand = resolveOperand(expression)
        loadOperandIntoRegister(operand, register)
    }

    private fun emitMultiplicationByAddition(left: Operand, right: Operand, target: Symbol) {
        val (multiplicandOperand, multiplierOperand) = when {
            left is Operand.Variable && right is Operand.Constant -> left to right
            left is Operand.Constant && right is Operand.Variable -> right to left
            else -> unsupportedExpression("Multiplication requires at least one constant operand when 'mul' instruction missing")
        }
        val multiplicandRegister = ensureOperandInDedicatedRegister(multiplicandOperand, target)
        emitConstantLoad(0, resultRegister)
        val multiplier = multiplierOperand.value
        if (multiplier == 0) return
        val iterations = abs(multiplier)
        repeat(iterations) {
            move(multiplicandRegister, leftRegister)
            move(resultRegister, rightRegister)
            emitBinaryOp(Operation.ADD)
        }
        move(resultRegister, target.register)
    }

    private fun ensureOperandInDedicatedRegister(operand: Operand, target: Symbol): Argument.Register {
        return when (operand) {
            is Operand.Variable -> operand.symbol.register
            is Operand.Constant -> {
                val tempRegister = symbols.acquireRegister()
                emitConstantLoad(operand.value, tempRegister)
                tempRegister
            }
        }
    }

    private fun loadOperandIntoRegister(operand: Operand, register: Argument.Register) {
        moveOperandIntoRegister(operand, register)
    }

    private fun moveOperandIntoRegister(operand: Operand, register: Argument.Register) {
        when (operand) {
            is Operand.Variable -> move(operand.symbol.register, register)
            is Operand.Constant -> emitConstantLoad(operand.value, register)
        }
    }

    private fun emitBitwiseFallback(left: Operand, right: Operand, target: Symbol, operation: Operation) {
        fun emitBitwise(opName: String) {
            loadOperandIntoRegister(left, leftRegister)
            loadOperandIntoRegister(right, rightRegister)
            emitter.emit(dialect.instruction(opName, emptyList()))
            move(resultRegister, target.register)
        }
        when (operation) {
            Operation.AND -> emitBitwise("and")
            Operation.OR -> emitBitwise("or")
            Operation.NOR -> emitBitwise("nor")
            Operation.NAND -> emitBitwise("nand")
            else -> error("Unsupported fallback for $operation")
        }
    }

    private fun instructionNameFor(operation: Operation): String = when (operation) {
        Operation.ADD -> "add"
        Operation.SUB -> "sub"
        Operation.MUL -> "mul"
        Operation.AND -> "and"
        Operation.OR -> "or"
        Operation.NOR -> "nor"
        Operation.NAND -> "nand"
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

internal enum class Operation { ADD, SUB, MUL, AND, OR, NOR, NAND }

internal sealed interface Operand {
    data class Variable(val symbol: Symbol) : Operand
    data class Constant(val value: Int) : Operand
}
