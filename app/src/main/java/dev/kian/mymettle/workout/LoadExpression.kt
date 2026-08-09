package dev.kian.mymettle.workout

private const val MAX_EXPRESSION_LENGTH = 64
private const val MAX_RESULT = 1_000_000.0

fun evaluateLoadExpression(input: String): Double {
    val source = input
        .trim()
        .replace(" ", "")
        .replace('×', '*')
        .replace('x', '*')
        .replace('X', '*')
        .replace('÷', '/')
        .replace('−', '-')
        .replace('–', '-')
        .replace('—', '-')

    require(source.isNotEmpty()) { "Enter a value." }
    require(source.length <= MAX_EXPRESSION_LENGTH) { "Formula is too long." }
    require(source.all { it.isDigit() || it in ".+-*/()" }) { "Use numbers and calculator symbols only." }

    val result = Parser(source).parse()
    require(result.isFinite()) { "The formula has no usable result." }
    require(result >= 0.0) { "The result cannot be negative." }
    require(result <= MAX_RESULT) { "The result is too large." }
    return kotlin.math.round(result * 1_000_000.0) / 1_000_000.0
}

private class Parser(private val source: String) {
    private var index = 0

    fun parse(): Double {
        val value = expression()
        require(index == source.length) { "Check the formula." }
        return value
    }

    private fun expression(): Double {
        var value = term()
        while (peek() == '+' || peek() == '-') {
            val operator = consume()
            val right = term()
            value = if (operator == '+') value + right else value - right
        }
        return value
    }

    private fun term(): Double {
        var value = factor()
        while (peek() == '*' || peek() == '/') {
            val operator = consume()
            val right = factor()
            require(!(operator == '/' && right == 0.0)) { "Cannot divide by zero." }
            value = if (operator == '*') value * right else value / right
        }
        return value
    }

    private fun factor(): Double {
        return when (val token = peek()) {
            '+', '-' -> {
                consume()
                val value = factor()
                if (token == '-') -value else value
            }
            '(' -> {
                consume()
                val value = expression()
                require(consume() == ')') { "Close the bracket." }
                value
            }
            else -> number()
        }
    }

    private fun number(): Double {
        val start = index
        var decimalPoints = 0
        while (index < source.length) {
            val token = source[index]
            if (token == '.') {
                decimalPoints += 1
                if (decimalPoints > 1) break
                index += 1
                continue
            }
            if (!token.isDigit()) break
            index += 1
        }
        val raw = source.substring(start, index)
        require(raw.isNotEmpty() && raw != ".") { "Enter a number." }
        return raw.toDoubleOrNull() ?: throw IllegalArgumentException("Check the number.")
    }

    private fun peek(): Char? = source.getOrNull(index)

    private fun consume(): Char? = source.getOrNull(index++)
}
