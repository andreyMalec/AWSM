package com.malec.awsm

import com.malec.awsm.Argument
import com.malec.awsm.expression.ExpressionTranslator
import com.malec.awsm.expression.Operand
import com.malec.awsm.expression.Operation
import com.malec.awsm.isa.IsaDialect
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import java.util.ArrayDeque

internal class KtFileTranslator(
    private val emitter: AsmEmitter,
    private val dialect: IsaDialect
) {
    private val loopStack = ArrayDeque<LoopContext>()

    fun translate(file: KtFile) {
        file.declarations.filterIsInstance<KtNamedFunction>().forEach { translateFunction(it) }
    }

    private fun translateFunction(function: KtNamedFunction) {
        val name = function.name ?: return
        val registerPool = RegisterPool(dialect.registers())
        val symbolTable = SymbolTable(registerPool)
        val expressionTranslator = ExpressionTranslator(symbolTable, emitter, dialect)
        loopStack.clear()
        emitter.label(name)
        val body = function.bodyExpression ?: return
        val statements = if (body is KtBlockExpression) body.statements else listOf(body)
        var labelIndex = 0
        fun nextLabel(): String = "L${labelIndex++}_${name}"
        statements.forEach { statement ->
            emitter.label(nextLabel())
            handleStatement(statement, symbolTable, expressionTranslator, ::nextLabel)
        }
        emitter.label(nextLabel())
        if (dialect.hasInstruction("ret")) {
            emitter.emit(dialect.instruction("ret", emptyList()))
            emitter.label(nextLabel())
        }
    }

    private fun handleStatement(
        statement: KtExpression,
        symbols: SymbolTable,
        translator: ExpressionTranslator,
        nextLabel: () -> String
    ) {
        when (statement) {
            is KtProperty -> {
                val declaredName = statement.name ?: return
                val symbol = symbols.declare(declaredName, statement.isVar)
                val initializer = statement.initializer ?: error("Initializer required")
                translator.assign(symbol, initializer)
            }

            is KtBinaryExpression -> handleBinary(statement, symbols, translator)
            is KtUnaryExpression -> handleUnary(statement, symbols, translator)
            is KtCallExpression -> handleCall(statement, translator)
            is KtWhileExpression -> handleWhile(statement, symbols, translator, nextLabel)
            is KtDoWhileExpression -> handleDoWhile(statement, symbols, translator, nextLabel)
            is KtIfExpression -> handleIf(statement, symbols, translator, nextLabel)
            is KtContinueExpression -> handleContinue(translator)
            else -> error("Unsupported statement: ${statement.text}")
        }
    }

    private fun handleBinary(
        expression: KtBinaryExpression,
        symbols: SymbolTable,
        translator: ExpressionTranslator
    ) {
        val left = expression.left as? KtNameReferenceExpression ?: error("Expected variable on left")
        val symbol = symbols.require(left.getReferencedName())
        val rhs = expression.right ?: error("Assignment requires RHS")
        when (expression.operationToken) {
            KtTokens.EQ -> translator.assign(symbol, rhs)
            KtTokens.PLUSEQ -> translator.augmentedAssignment(symbol, rhs, Operation.ADD)
            KtTokens.MINUSEQ -> translator.augmentedAssignment(symbol, rhs, Operation.SUB)
            KtTokens.MULTEQ -> translator.augmentedAssignment(symbol, rhs, Operation.MUL)
            else -> error("Unsupported operation: ${expression.text}")
        }
    }

    private fun handleUnary(
        expression: KtUnaryExpression,
        symbols: SymbolTable,
        translator: ExpressionTranslator
    ) {
        val name = (expression.baseExpression as? KtNameReferenceExpression)?.getReferencedName()
            ?: error("Unary operator requires variable")
        val symbol = symbols.require(name)
        val delta = when (expression.operationReference.getReferencedNameElementType()) {
            KtTokens.PLUSPLUS -> 1
            KtTokens.MINUSMINUS -> -1
            else -> error("Unsupported unary op: ${expression.text}")
        }
        translator.increment(symbol, delta)
    }

    private fun handleCall(
        expression: KtCallExpression,
        translator: ExpressionTranslator
    ) {
        when ((expression.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()) {
            "output" -> {
                val argument = expression.valueArguments.singleOrNull()?.getArgumentExpression()
                    ?: error("output requires an argument")
                val operand = translator.resolveOperand(argument)
                val outRegister = dialect.specialRegisters.output
                    ?: error("ISA does not define an output register")
                when (operand) {
                    is Operand.Variable ->
                        emitter.emit(dialect.instruction("mov", listOf(outRegister, operand.symbol.register)))

                    is Operand.Constant -> {
                        val temp = if (dialect.specialRegisters.immediate != null)
                            Symbol("__output_const_${argument.hashCode()}", true, dialect.specialRegisters.immediate)
                        else
                            translator.symbols.declare("__output_const_${argument.hashCode()}", true)
                        translator.assign(temp, argument)
                        emitter.emit(dialect.instruction("mov", listOf(outRegister, temp.register)))
                    }
                }
            }

            else -> error("Unsupported call: ${expression.text}")
        }
    }

    private fun handleWhile(
        expression: KtWhileExpression,
        symbols: SymbolTable,
        translator: ExpressionTranslator,
        nextLabel: () -> String
    ) {
        val loopLabel = nextLabel()
        val exitLabel = nextLabel()
        emitter.label(loopLabel)
        emitCondition(expression.condition, translator, exitLabel)
        val context = LoopContext(loopLabel, exitLabel)
        loopStack.addLast(context)
        try {
            statementsOf(expression.body).forEach { handleStatement(it, symbols, translator, nextLabel) }
        } finally {
            loopStack.removeLast()
        }
        jumpTo(loopLabel, translator)
        emitter.label(exitLabel)
    }

    private fun handleDoWhile(
        expression: KtDoWhileExpression,
        symbols: SymbolTable,
        translator: ExpressionTranslator,
        nextLabel: () -> String
    ) {
        val loopLabel = nextLabel()
        val conditionLabel = nextLabel()
        val exitLabel = nextLabel()
        emitter.label(loopLabel)
        val context = LoopContext(conditionLabel, exitLabel)
        loopStack.addLast(context)
        try {
            statementsOf(expression.body).forEach { handleStatement(it, symbols, translator, nextLabel) }
        } finally {
            loopStack.removeLast()
        }
        emitter.label(conditionLabel)
        emitCondition(expression.condition, translator, exitLabel)
        jumpTo(loopLabel, translator)
        emitter.label(exitLabel)
    }

    private fun handleIf(
        expression: KtIfExpression,
        symbols: SymbolTable,
        translator: ExpressionTranslator,
        nextLabel: () -> String
    ) {
        val elseLabel = nextLabel()
        val endLabel = if (expression.`else` != null) nextLabel() else elseLabel
        emitCondition(expression.condition, translator, elseLabel)
        statementsOf(expression.then).forEach { handleStatement(it, symbols, translator, nextLabel) }
        if (expression.`else` != null) {
            jumpTo(endLabel, translator)
        }
        emitter.label(elseLabel)
        expression.`else`?.let {
            statementsOf(it).forEach { stmt -> handleStatement(stmt, symbols, translator, nextLabel) }
            emitter.label(endLabel)
        }
    }

    private fun handleContinue(translator: ExpressionTranslator) {
        val context = loopStack.peekLast() ?: error("'continue' used outside of loop")
        jumpTo(context.continueLabel, translator)
    }

    private fun emitCondition(
        condition: KtExpression?,
        translator: ExpressionTranslator,
        exitLabel: String
    ) {
        if (condition is KtBinaryExpression) {
            val left = condition.left ?: error("Condition missing left operand")
            val right = condition.right ?: error("Condition missing right operand")
            translator.emitSubtraction(left, right)
            translator.emitMoveToLabel(Argument.Label(exitLabel))
            val jumpInstruction = when (condition.operationToken) {
                KtTokens.EXCLEQ -> "je"
                KtTokens.EQEQ -> "jne"
                KtTokens.LT -> "jae"
                KtTokens.LTEQ -> "ja"
                KtTokens.GT -> "jbe"
                KtTokens.GTEQ -> "jb"
                else -> error("Unsupported loop condition: ${condition.text}")
            }
            emitter.emit(dialect.instruction(jumpInstruction, emptyList()))
        } else if (condition is KtConstantExpression) {
            val valueText = condition.text
            if (valueText != true.toString()) {
                translator.emitMoveToLabel(Argument.Label(exitLabel))
                emitter.emit(dialect.instruction("jmp", emptyList()))
            }
        } else {
            error("Unsupported loop condition: ${condition?.text}")
        }
    }

    private fun statementsOf(body: KtExpression?): List<KtExpression> {
        val expression = body ?: return emptyList()
        return if (expression is KtBlockExpression) expression.statements else listOf(expression)
    }

    private fun jumpTo(label: String, translator: ExpressionTranslator) {
        translator.emitMoveToLabel(Argument.Label(label))
        emitter.emit(dialect.instruction("jmp", emptyList()))
    }

    private data class LoopContext(
        val continueLabel: String,
        val breakLabel: String
    )
}
