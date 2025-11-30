package com.malec.awsm

import com.malec.awsm.expression.ExpressionTranslator
import com.malec.awsm.expression.Operation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

internal class KtFileTranslator(private val emitter: AsmEmitter) {
    fun translate(file: KtFile) {
        file.declarations.filterIsInstance<KtNamedFunction>().forEach { translateFunction(it) }
    }

    private fun translateFunction(function: KtNamedFunction) {
        val name = function.name ?: return
        val symbolTable = SymbolTable()
        val expressionTranslator = ExpressionTranslator(symbolTable, emitter)
        emitter.label(name)
        val body = function.bodyExpression ?: return
        val statements = if (body is KtBlockExpression) body.statements else listOf(body)
        var labelIndex = 0
        fun nextLabel(): String = "L${labelIndex++}_${name}"
        statements.forEach { statement ->
            emitter.label(nextLabel())
            when (statement) {
                is KtProperty -> {
                    val declaredName = statement.name ?: return@forEach
                    val symbol = symbolTable.declare(declaredName, statement.isVar)
                    val initializer = statement.initializer ?: error("Initializer required")
                    expressionTranslator.assign(symbol, initializer)
                }
                is KtBinaryExpression -> handleBinary(statement, symbolTable, expressionTranslator)
                is KtUnaryExpression -> handleUnary(statement, symbolTable, expressionTranslator)
                else -> error("Unsupported statement: ${statement.text}")
            }
        }
        emitter.label(nextLabel())
        emitter.emit(ASM.RET)
        emitter.label(nextLabel())
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
}
