package com.malec.awsm

import com.malec.awsm.expression.ExpressionTranslator
import com.malec.awsm.expression.Operand
import com.malec.awsm.expression.Operation
import com.malec.awsm.isa.IsaDialect
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

internal class KtFileTranslator(
    private val emitter: AsmEmitter,
    private val dialect: IsaDialect
) {
    fun translate(file: KtFile) {
        file.declarations.filterIsInstance<KtNamedFunction>().forEach { translateFunction(it) }
    }

    private fun translateFunction(function: KtNamedFunction) {
        val name = function.name ?: return
        val registerPool = RegisterPool(dialect.registers())
        val symbolTable = SymbolTable(registerPool)
        val expressionTranslator = ExpressionTranslator(symbolTable, emitter, dialect)
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
                        val temp = translator.symbols.declare("__output_const_${argument.hashCode()}", true)
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
        val body = expression.body ?: return
        val bodyStatements = if (body is KtBlockExpression) body.statements else listOf(body)
        bodyStatements.forEach { handleStatement(it, symbols, translator, nextLabel) }
        translator.emitMoveToLabel(Argument.Label(loopLabel))
        emitter.emit(dialect.instruction("jmp", emptyList()))
        emitter.label(exitLabel)
    }

    private fun handleDoWhile(
        expression: KtDoWhileExpression,
        symbols: SymbolTable,
        translator: ExpressionTranslator,
        nextLabel: () -> String
    ) {
        val loopLabel = nextLabel()
        val exitLabel = nextLabel()
        emitter.label(loopLabel)
        val body = expression.body ?: return
        val bodyStatements = if (body is KtBlockExpression) body.statements else listOf(body)
        bodyStatements.forEach { handleStatement(it, symbols, translator, nextLabel) }
        emitCondition(expression.condition, translator, exitLabel)
        translator.emitMoveToLabel(Argument.Label(loopLabel))
        emitter.emit(dialect.instruction("jmp", emptyList()))
        emitter.label(exitLabel)
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
}
