package com.paintphymath

import kotlin.math.*

/**
 * Компактный парсер математических выражений.
 * Поддерживает: + - * / ^ !, неявное умножение (2x, 3sin(x), (x+1)(x-1)),
 * функции sin cos tg/tan ctg asin acos atan sinh cosh tanh exp ln lg log sqrt cbrt abs floor ceil sign,
 * log(b, x), max, min, pow, atan2, константы pi/π, e, переменные x y t n θ и параметры a=…; b=…
 */
class Expr private constructor(private val root: Node) {

    fun eval(vars: Map<String, Double>): Double = try { root.ev(vars) } catch (e: Throwable) { Double.NaN }

    private abstract class Node { abstract fun ev(v: Map<String, Double>): Double }
    private class Num(val x: Double) : Node() { override fun ev(v: Map<String, Double>) = x }
    private class Var(val n: String) : Node() {
        override fun ev(v: Map<String, Double>) = v[n] ?: throw IllegalArgumentException("Неизвестная переменная $n")
    }
    private class Bin(val op: Char, val a: Node, val b: Node) : Node() {
        override fun ev(v: Map<String, Double>): Double {
            val x = a.ev(v); val y = b.ev(v)
            return when (op) {
                '+' -> x + y; '-' -> x - y; '*' -> x * y; '/' -> x / y
                '^' -> powR(x, y)
                else -> Double.NaN
            }
        }
    }
    private class Neg(val a: Node) : Node() { override fun ev(v: Map<String, Double>) = -a.ev(v) }
    private class Fact(val a: Node) : Node() {
        override fun ev(v: Map<String, Double>): Double {
            val x = a.ev(v)
            if (x < 0 || x > 170) return Double.NaN
            val n = x.roundToInt()
            if (abs(x - n) > 1e-9) return exp(lgamma(x + 1))
            var r = 1.0
            for (i in 2..n) r *= i
            return r
        }
    }
    private class Fn(val name: String, val args: List<Node>) : Node() {
        override fun ev(v: Map<String, Double>): Double {
            val a = args.map { it.ev(v) }
            val x = a.getOrElse(0) { Double.NaN }
            return when (name) {
                "sin" -> sin(x); "cos" -> cos(x); "tan", "tg" -> tan(x); "cot", "ctg" -> 1 / tan(x)
                "sec" -> 1 / cos(x); "csc", "cosec" -> 1 / sin(x)
                "asin", "arcsin" -> asin(x); "acos", "arccos" -> acos(x); "atan", "arctan", "arctg" -> atan(x)
                "acot", "arcctg" -> PI / 2 - atan(x)
                "sinh", "sh" -> sinh(x); "cosh", "ch" -> cosh(x); "tanh", "th" -> tanh(x)
                "exp" -> exp(x); "ln" -> ln(x); "lg" -> log10(x)
                "log" -> if (a.size >= 2) ln(a[1]) / ln(a[0]) else log10(x)
                "log2" -> ln(x) / ln(2.0)
                "sqrt" -> sqrt(x); "cbrt" -> Math.cbrt(x)
                "abs" -> abs(x); "floor" -> floor(x); "ceil" -> ceil(x); "round" -> round(x)
                "sign", "sgn" -> sign(x)
                "max" -> a.maxOrNull() ?: Double.NaN
                "min" -> a.minOrNull() ?: Double.NaN
                "pow" -> powR(a[0], a[1])
                "atan2" -> atan2(a[0], a[1])
                "mod" -> a[0].mod(a[1])
                "gamma" -> exp(lgamma(x))
                "heaviside", "H" -> if (x >= 0) 1.0 else 0.0
                else -> Double.NaN
            }
        }
    }

    companion object {
        val FUNCS = setOf(
            "sin", "cos", "tan", "tg", "cot", "ctg", "sec", "csc", "cosec", "asin", "arcsin", "acos", "arccos",
            "atan", "arctan", "arctg", "acot", "arcctg", "sinh", "sh", "cosh", "ch", "tanh", "th", "exp", "ln", "lg",
            "log", "log2", "sqrt", "cbrt", "abs", "floor", "ceil", "round", "sign", "sgn", "max", "min", "pow",
            "atan2", "mod", "gamma", "heaviside"
        )

        private fun powR(x: Double, y: Double): Double {
            if (x < 0) {
                // вещественный корень нечётной степени: (-8)^(1/3) = -2
                val inv = 1.0 / y
                val ri = inv.roundToInt()
                if (abs(inv - ri) < 1e-9 && ri % 2 != 0) return -(-x).pow(y)
            }
            return x.pow(y)
        }

        private fun lgamma(x: Double): Double {
            // аппроксимация Ланцоша
            val g = 7.0
            val c = doubleArrayOf(
                0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313,
                -176.61502916214059, 12.507343278686905, -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7
            )
            if (x < 0.5) return ln(PI / abs(sin(PI * x))) - lgamma(1 - x)
            val xx = x - 1
            var a = c[0]
            val t = xx + g + 0.5
            for (i in 1 until 9) a += c[i] / (xx + i)
            return 0.5 * ln(2 * PI) + (xx + 0.5) * ln(t) - t + ln(a)
        }

        /** Разбирает строку; бросает IllegalArgumentException с понятным сообщением. */
        fun parse(src: String): Expr = Expr(Parser(normalize(src)).parseAll())

        fun tryParse(src: String): Expr? = try { parse(src) } catch (e: Exception) { null }

        fun normalize(s: String): String = s.trim()
            .replace("·", "*").replace("×", "*").replace("÷", "/").replace("−", "-").replace("–", "-")
            .replace("²", "^2").replace("³", "^3").replace("√", "sqrt").replace("π", "pi")
            .replace("θ", "theta").replace("φ", "phi").replace("∞", "inf")
            .replace(";", ",")

        /** Парсит «a=1, b=2» или «a=1; b=2» в карту параметров. */
        fun parseParams(s: String): Map<String, Double> {
            val m = HashMap<String, Double>()
            for (part in s.split(';', '\n', ',')) {
                val kv = part.split('=')
                if (kv.size == 2) {
                    val k = kv[0].trim()
                    val v = tryParse(kv[1])?.eval(baseVars()) ?: continue
                    if (k.isNotEmpty()) m[k] = v
                }
            }
            return m
        }

        fun baseVars(): HashMap<String, Double> = hashMapOf("pi" to PI, "e" to E, "inf" to Double.POSITIVE_INFINITY)

        fun fmt(v: Double, digits: Int = 4): String {
            if (v.isNaN()) return "—"
            if (v.isInfinite()) return if (v > 0) "∞" else "−∞"
            if (abs(v) >= 1e6 || (abs(v) < 1e-4 && v != 0.0)) {
                return String.format(java.util.Locale.US, "%.${digits - 1}e", v).replace("-", "−")
            }
            val r = BigDecimalFmt.round(v, digits)
            return r.replace("-", "−")
        }
    }

    private object BigDecimalFmt {
        fun round(v: Double, digits: Int): String {
            var s = String.format(java.util.Locale.US, "%.${digits}f", v)
            if (s.contains('.')) s = s.trimEnd('0').trimEnd('.')
            if (s == "-0") s = "0"
            return s
        }
    }

    private class Parser(val s: String) {
        var i = 0

        fun parseAll(): Node {
            if (s.isBlank()) throw IllegalArgumentException("Пустое выражение")
            val n = sum()
            skip()
            if (i < s.length) throw IllegalArgumentException("Лишний символ «${s[i]}» в позиции ${i + 1}")
            return n
        }

        fun skip() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun peek(): Char { skip(); return if (i < s.length) s[i] else '\u0000' }

        fun sum(): Node {
            var a = product()
            while (true) {
                val c = peek()
                if (c == '+' || c == '-') { i++; a = Bin(c, a, product()) } else return a
            }
        }

        fun product(): Node {
            var a = unary()
            while (true) {
                val c = peek()
                if (c == '*' || c == '/') { i++; a = Bin(c, a, unary()) }
                else if (c == '(' || c.isLetter() || c.isDigit() || c == '.') {
                    // неявное умножение
                    a = Bin('*', a, power())
                } else return a
            }
        }

        fun unary(): Node {
            val c = peek()
            if (c == '-') { i++; return Neg(unary()) }
            if (c == '+') { i++; return unary() }
            return power()
        }

        fun power(): Node {
            var b = postfix()
            if (peek() == '^') {
                i++
                val e = unary()
                b = Bin('^', b, e)
            }
            return b
        }

        fun postfix(): Node {
            var a = atom()
            while (peek() == '!') { i++; a = Fact(a) }
            return a
        }

        fun atom(): Node {
            val c = peek()
            if (c == '(' || c == '[' || c == '{') {
                i++
                val n = sum()
                val cl = peek()
                if (cl != ')' && cl != ']' && cl != '}') throw IllegalArgumentException("Не хватает закрывающей скобки")
                i++
                return n
            }
            if (c == '|') {
                i++
                val n = sum()
                if (peek() != '|') throw IllegalArgumentException("Не хватает закрывающей |")
                i++
                return Fn("abs", listOf(n))
            }
            if (c.isDigit() || c == '.') {
                val st = i
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                if (i < s.length && (s[i] == 'e' || s[i] == 'E') && i + 1 < s.length &&
                    (s[i + 1].isDigit() || ((s[i + 1] == '-' || s[i + 1] == '+') && i + 2 < s.length && s[i + 2].isDigit()))
                ) {
                    i += 2
                    while (i < s.length && s[i].isDigit()) i++
                }
                return Num(s.substring(st, i).toDoubleOrNull() ?: throw IllegalArgumentException("Плохое число"))
            }
            if (c.isLetter()) {
                val st = i
                // самое длинное известное имя (функция/константа) с текущей позиции, иначе — одна буква (+индекс)
                val known = (FUNCS + KNOWN).filter { s.startsWith(it, st) }.maxByOrNull { it.length }
                val name: String
                if (known != null) {
                    i = st + known.length
                    name = known
                } else {
                    i = st + 1
                    while (i < s.length && (s[i].isDigit() || s[i] == '_')) i++
                    name = s.substring(st, i)
                }
                if (name in FUNCS) {
                    skip()
                    // sin^2(x) = (sin x)^2
                    var powE: Node? = null
                    if (peek() == '^') { i++; powE = postfix() }
                    val args = ArrayList<Node>()
                    if (peek() == '(') {
                        i++
                        args.add(sum())
                        while (peek() == ',') { i++; args.add(sum()) }
                        if (peek() != ')') throw IllegalArgumentException("Не хватает ) после $name")
                        i++
                    } else {
                        // sin x, sin 2x
                        args.add(power())
                    }
                    val f = Fn(name, args)
                    return if (powE != null) Bin('^', f, powE) else f
                }
                return when (name) {
                    "pi" -> Num(PI)
                    "e" -> Num(E)
                    "inf" -> Num(Double.POSITIVE_INFINITY)
                    else -> Var(name)
                }
            }
            if (c == '\u0000') throw IllegalArgumentException("Неожиданный конец выражения")
            throw IllegalArgumentException("Неожиданный символ «$c»")
        }

        companion object {
            val KNOWN = setOf("pi", "theta", "phi", "inf", "alpha", "beta", "omega", "tau", "lambda", "mu")
        }
    }
}
