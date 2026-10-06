package com.meshgen.core.dsl

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class ExprException(message: String) : Exception(message)

/**
 * Arithmetic over named parameters, e.g. `"diameter / 2 - wall"`.
 * Supports + - * / ^, parentheses, `pi`, and min, max, abs, sqrt, sin, cos, tan (degrees), floor, ceil, round, clamp.
 */
object Expr {
    fun eval(text: String, vars: Map<String, Double>): Double = Parser(text, vars).parseAll()

    private val functions: Map<String, Pair<IntRange, (List<Double>) -> Double>> = mapOf(
        "min" to (2..8 to { a -> a.min() }),
        "max" to (2..8 to { a -> a.max() }),
        "abs" to (1..1 to { a -> abs(a[0]) }),
        "sqrt" to (1..1 to { a -> if (a[0] < 0) throw ExprException("sqrt of a negative number") else sqrt(a[0]) }),
        "sin" to (1..1 to { a -> sin(Math.toRadians(a[0])) }),
        "cos" to (1..1 to { a -> cos(Math.toRadians(a[0])) }),
        "tan" to (1..1 to { a -> tan(Math.toRadians(a[0])) }),
        "floor" to (1..1 to { a -> floor(a[0]) }),
        "ceil" to (1..1 to { a -> ceil(a[0]) }),
        "round" to (1..1 to { a -> a[0].roundToLong().toDouble() }),
        "clamp" to (3..3 to { a -> min(max(a[0], a[1]), a[2]) }),
    )

    private class Parser(val s: String, val vars: Map<String, Double>) {
        var i = 0

        fun parseAll(): Double {
            val v = expr()
            skip()
            if (i < s.length) throw ExprException("unexpected '${s[i]}' at position ${i + 1} in \"$s\"")
            if (!v.isFinite()) throw ExprException("\"$s\" does not give a finite number")
            return v
        }

        fun skip() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun peek(c: Char): Boolean { skip(); return i < s.length && s[i] == c }

        fun expr(): Double {
            var v = term()
            while (true) {
                v = when {
                    peek('+') -> { i++; v + term() }
                    peek('-') -> { i++; v - term() }
                    else -> return v
                }
            }
        }

        fun term(): Double {
            var v = unary()
            while (true) {
                v = when {
                    peek('*') -> { i++; v * unary() }
                    peek('/') -> {
                        i++
                        val d = unary()
                        if (d == 0.0) throw ExprException("division by zero in \"$s\"")
                        v / d
                    }
                    else -> return v
                }
            }
        }

        fun unary(): Double = if (peek('-')) { i++; -unary() } else if (peek('+')) { i++; unary() } else power()

        fun power(): Double {
            val base = primary()
            return if (peek('^')) { i++; base.pow(unary()) } else base
        }

        fun primary(): Double {
            skip()
            if (i >= s.length) throw ExprException("expression \"$s\" ends unexpectedly")
            val c = s[i]
            if (c == '(') {
                i++
                val v = expr()
                if (!peek(')')) throw ExprException("missing ')' in \"$s\"")
                i++
                return v
            }
            if (c.isDigit() || c == '.') {
                val start = i
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                    i++
                    if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                    while (i < s.length && s[i].isDigit()) i++
                }
                return s.substring(start, i).toDoubleOrNull() ?: throw ExprException("bad number '${s.substring(start, i)}' in \"$s\"")
            }
            if (c.isLetter() || c == '_') {
                val start = i
                while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) i++
                val name = s.substring(start, i)
                if (peek('(')) {
                    i++
                    val args = mutableListOf<Double>()
                    if (!peek(')')) {
                        args += expr()
                        while (peek(',')) { i++; args += expr() }
                    }
                    if (!peek(')')) throw ExprException("missing ')' after arguments of $name() in \"$s\"")
                    i++
                    val (arity, f) = functions[name] ?: throw ExprException("unknown function '$name'. Allowed: ${functions.keys.joinToString()}")
                    if (args.size !in arity) throw ExprException("$name() takes ${if (arity.first == arity.last) "${arity.first}" else "${arity.first}-${arity.last}"} arguments")
                    return f(args)
                }
                if (name == "pi") return PI
                return vars[name] ?: throw ExprException(
                    "unknown parameter '$name'" + if (vars.isEmpty()) "" else ". Defined parameters: ${vars.keys.joinToString()}",
                )
            }
            throw ExprException("unexpected '$c' in \"$s\"")
        }
    }
}
