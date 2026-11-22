package com.novoagentehb.app.agent.computer

import com.novoagentehb.app.bridges.WorkspaceWriteOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** A bounded arithmetic interpreter, not a shell, Python, PRoot or an OS sandbox. */
internal object WorkspaceProgram {
    private const val MAX_EXPRESSION = 256
    private const val MAX_INPUT = 128_000
    private const val MAX_ROWS = 10_000
    private const val MAX_OUTPUT = 1_000_000

    private sealed interface Expr {
        fun eval(x: Double): Double
    }
    private data class Constant(val number: Double) : Expr { override fun eval(x: Double) = number }
    private object Variable : Expr { override fun eval(x: Double) = x }
    private data class Unary(val inner: Expr) : Expr { override fun eval(x: Double) = -inner.eval(x) }
    private data class Binary(val left: Expr, val op: Char, val right: Expr) : Expr {
        override fun eval(x: Double): Double {
            val l = left.eval(x); val r = right.eval(x)
            return when (op) {
                '+' -> l + r
                '-' -> l - r
                '*' -> l * r
                '/' -> { require(r != 0.0) { "Divisão por zero." }; l / r }
                else -> error("Operador inválido.")
            }.also { require(it.isFinite()) { "Resultado fora do intervalo numérico." } }
        }
    }
    private class Parser(private val source: String) {
        private var pos = 0
        private fun skip() { while (pos < source.length && source[pos].isWhitespace()) pos++ }
        private fun take(ch: Char): Boolean { skip(); if (pos < source.length && source[pos] == ch) { pos++; return true }; return false }
        fun parse(): Expr {
            val expr = sum()
            skip(); require(pos == source.length) { "Expressão contém sintaxe não suportada." }
            return expr
        }
        private fun sum(): Expr {
            var expr = product()
            while (true) expr = when {
                take('+') -> Binary(expr, '+', product())
                take('-') -> Binary(expr, '-', product())
                else -> return expr
            }
        }
        private fun product(): Expr {
            var expr = atom()
            while (true) expr = when {
                take('*') -> Binary(expr, '*', atom())
                take('/') -> Binary(expr, '/', atom())
                else -> return expr
            }
        }
        private fun atom(): Expr {
            skip()
            if (take('-')) return Unary(atom())
            if (take('(')) {
                val inner = sum(); require(take(')')) { "Parêntese não fechado." }; return inner
            }
            if (take('x')) return Variable
            skip(); val start = pos
            while (pos < source.length && (source[pos].isDigit() || source[pos] == '.')) pos++
            require(pos > start) { "Esperado número ou variável x." }
            val number = source.substring(start, pos).toDoubleOrNull()
            require(number != null && number.isFinite()) { "Número inválido." }
            return Constant(number)
        }
    }

    data class Result(val path: String, val sha256: String, val rowCount: Int)

    suspend fun execute(
        expression: String,
        inputs: String,
        confirmed: Boolean,
        write: (String, String) -> WorkspaceWriteOutcome,
        read: (String) -> String?
    ): Result = withContext(Dispatchers.Default) {
        require(confirmed) { "Confirmação necessária para executar o programa." }
        require(expression.isNotBlank() && expression.length <= MAX_EXPRESSION) { "Expressão vazia ou longa demais." }
        require(inputs.length <= MAX_INPUT) { "Dados de entrada longos demais." }
        val program = Parser(expression).parse()
        val rows = inputs.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        require(rows.isNotEmpty() && rows.size <= MAX_ROWS) { "Forneça de 1 a $MAX_ROWS números." }
        val result = StringBuilder("entrada,resultado\n")
        for (row in rows) {
            yield() // lets the orchestrator's stop button cancel before any artifact is committed
            val value = row.toDoubleOrNull()
            require(value != null && value.isFinite()) { "Entrada numérica inválida." }
            val evaluated = program.eval(value)
            require(evaluated.isFinite()) { "Resultado fora do intervalo numérico." }
            result.append(row).append(',').append(evaluated).append('\n')
            require(result.length <= MAX_OUTPUT) { "Resultado excedeu o limite de tamanho." }
        }
        coroutineContext.ensureActive()
        val body = result.toString()
        val path = "computations/${UUID.randomUUID()}.csv"
        val written = withContext(Dispatchers.IO) { write(path, body) }
        check(written.success) { "Falha ao gravar o artefato." }
        val actual = withContext(Dispatchers.IO) { read(written.relativePath) }
        check(actual == body) { "Arquivo criado, mas a releitura não corresponde ao resultado." }
        Result(written.relativePath, written.sha256, rows.size)
    }
}
