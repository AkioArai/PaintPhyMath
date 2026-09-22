package com.paintphymath

import kotlin.math.max
import kotlin.math.min

/**
 * Мини-движок вёрстки формул в стиле LaTeX. Рисует через Gfx, поэтому формулы
 * одинаково выглядят на экране, в PNG, PDF и SVG.
 *
 * Поддерживается: \frac \dfrac \binom \sqrt[n] ^ _ ' \sum \prod \int \iint \oint \lim \max \min
 * \vec \overrightarrow \bar \overline \hat \dot \ddot \tilde \underline \text \mathrm \mathbf \mathbb
 * \left( \right) матрицы \pmatrix{a&b\\c&d} \bmatrix \vmatrix \matrix \cases, \begin{pmatrix}…\end{pmatrix},
 * греческие буквы, стрелки, отношения, множества, а также быстрые замены: -> => <= >= != +- ~=.
 */
object MathText {

    abstract class Box {
        var w = 0f
        var asc = 0f
        var desc = 0f
        open val isOp = false
        abstract fun draw(g: Gfx, x: Float, y: Float, color: Int)
    }

    private class Glyph(val s: String, val size: Float, val font: Font, override val isOp: Boolean = false, pad: Float = 0f) : Box() {
        val pad = pad
        init {
            w = TextMetrics.width(s, size, font) + pad * 2
            asc = size * 0.74f
            desc = size * 0.24f
        }
        override fun draw(g: Gfx, x: Float, y: Float, color: Int) = g.text(s, x + pad, y, size, color, font)
    }

    private class Space(width: Float) : Box() {
        init { w = width }
        override fun draw(g: Gfx, x: Float, y: Float, color: Int) {}
    }

    private class HBox(val items: List<Box>) : Box() {
        init {
            for (b in items) { w += b.w; asc = max(asc, b.asc); desc = max(desc, b.desc) }
        }
        override fun draw(g: Gfx, x: Float, y: Float, color: Int) {
            var cx = x
            for (b in items) { b.draw(g, cx, y, color); cx += b.w }
        }
    }

    private class Script(val base: Box, val sup: Box?, val sub: Box?, size: Float) : Box() {
        val up: Float
        val down: Float
        init {
            up = max(size * 0.40f, base.asc - size * 0.30f)
            down = if (sup != null) max(size * 0.28f, base.desc) else max(size * 0.18f, base.desc * 0.8f)
            w = base.w + max(sup?.w ?: 0f, sub?.w ?: 0f) + size * 0.06f
            asc = max(base.asc, if (sup != null) up + sup.asc else 0f)
            desc = max(base.desc, if (sub != null) down + sub.desc else 0f)
        }
        override fun draw(g: Gfx, x: Float, y: Float, color: Int) {
            base.draw(g, x, y, color)
            sup?.draw(g, x + base.w + 1f, y - up, color)
            sub?.draw(g, x + base.w, y + down, color)
        }
    }

    private class Frac(val num: Box, val den: Box, val size: Float, val line: Boolean = true) : Box() {
        val axis = size * 0.27f
        val gap = size * 0.13f
        val t = max(size * 0.055f, 1f)
        init {
            w = max(num.w, den.w) + size * 0.3f
            asc = axis + gap + t / 2 + num.asc + num.desc
            desc = den.asc + den.desc + gap + t / 2 - axis
        }
        override fun draw(g: Gfx, x: Float, y: Float, color: Int) {
            val ly = y - axis
            num.draw(g, x + (w - num.w) / 2, ly - gap - t / 2 - num.desc, color)
            den.draw(g, x + (w - den.w) / 2, ly + gap + t / 2 + den.asc, color)
            if (line) g.line(x + size * 0.06f, ly, x + w - size * 0.06f, ly, color, t)
        }
    }

    private class Sqrt(val body: Box, val index: Box?, val size: Float) : Box() {
        val t = max(size * 0.05f, 1f)
        val gap = size * 0.12f
        val iw = if (index != null) max(0f, index.w - size * 0.25f) else 0f
        init {
            w = iw + size * 0.55f + body.w + size * 0.12f
            asc = body.asc + gap + t
            desc = body.desc + size * 0.04f
        }
        override fun draw(g: Gfx, x: Float, y: Float, color: Int) {
            val top = y - body.asc - gap
            val bot = y + body.desc
            val h = bot - top
            val x0 = x + iw
            val p = GPath()
            p.moveTo(x0, bot - h * 0.42f)
            p.lineTo(x0 + size * 0.12f, bot - h * 0.5f)
            p.lineTo(x0 + size * 0.3f, bot)
            p.lineTo(x0 + size * 0.5f, top)
            p.lineTo(x0 + size * 0.55f + body.w + size * 0.08f, top)
            g.path(p, color, t)
            body.draw(g, x0 + size * 0.55f, y, color)
            index?.draw(g, x, bot - h * 0.5f - size * 0.08f, color)
        }
    }

    private class BigOp(val sym: String, val lower: Box?, val upper: Box?, val below: Boolean, size: Float, integral: Boolean) : Box() {
        val s = size * (if (integral) 1.75f else 1.45f)
        val op = Glyph(sym, s, if (integral) Font.SERIF_ITALIC else Font.SERIF)
        val axis = size * 0.27f
        val oAsc = s * (if (integral) 0.78f else 0.72f)
        val oDesc = s * (if (integral) 0.24f else 0.02f)
        val baseY = -axis + (oAsc - oDesc) / 2   // смещение базовой линии знака относительно y
        val gap = size * 0.1f
        init {
            val half = (oAsc + oDesc) / 2
            if (below) {
                w = maxOf(op.w, lower?.w ?: 0f, upper?.w ?: 0f) + size * 0.15f
                asc = axis + half + (upper?.let { it.asc + it.desc + gap } ?: 0f)
                desc = half - axis + (lower?.let { it.asc + it.desc + gap } ?: 0f)
            } else {
                w = op.w + max(lower?.w ?: 0f, upper?.w ?: 0f) + size * 0.2f
                asc = max(axis + half, axis + half + (upper?.asc ?: 0f) * 0.4f)
                desc = max(half - axis, half - axis + (lower?.desc ?: 0f) + (lower?.asc ?: 0f) * 0.4f)
            }
        }
        override fun draw(g: Gfx, x: Float, y: Float, color: Int) {
            val by = y + baseY
            val half = (oAsc + oDesc) / 2
            if (below) {
                op.draw(g, x + (w - op.w) / 2, by, color)
                upper?.let { it.draw(g, x + (w - it.w) / 2, y - axis - half - gap - it.desc, color) }
                lower?.let { it.draw(g, x + (w - it.w) / 2, y - axis + half + gap + it.asc, color) }
            } else {
                op.draw(g, x, by, color)
                upper?.draw(g, x + op.w * 0.95f, y - axis - half + (upper.asc) * 0.9f, color)
                lower?.draw(g, x + op.w * 0.6f, y - axis + half + lower.asc * 0.1f, color)
            }
        }
    }

    private class Accent(val body: Box, val kind: String, val size: Float) : Box() {
        val t = max(size * 0.05f, 1f)
        val extra = size * (if (kind == "under") 0f else 0.26f)
        init {
            w = body.w
            asc = body.asc + extra
            desc = body.desc + if (kind == "under") size * 0.15f else 0f
            if (kind == "vec") w = max(body.w, size * 0.5f)
        }
        override fun draw(g: Gfx, x: Float, y: Float, color: Int) {
            body.draw(g, x + (w - body.w) / 2, y, color)
            val ty = y - body.asc - size * 0.1f
            val x0 = x + size * 0.04f
            val x1 = x + w - size * 0.02f
            val mx = (x0 + x1) / 2
            when (kind) {
                "vec" -> {
                    g.line(x0, ty, x1, ty, color, t)
                    val p = GPath()
                    p.moveTo(x1 - size * 0.16f, ty - size * 0.09f); p.lineTo(x1, ty); p.lineTo(x1 - size * 0.16f, ty + size * 0.09f)
                    g.path(p, color, t)
                }
                "bar" -> g.line(x0, ty, x1, ty, color, t)
                "under" -> { val uy = y + body.desc + size * 0.06f; g.line(x0, uy, x1, uy, color, t) }
                "hat" -> {
                    val p = GPath(); p.moveTo(mx - size * 0.18f, ty + size * 0.04f); p.lineTo(mx, ty - size * 0.1f); p.lineTo(mx + size * 0.18f, ty + size * 0.04f)
                    g.path(p, color, t)
                }
                "dot" -> g.circle(mx, ty, size * 0.05f, 0, 0f, color)
                "ddot" -> { g.circle(mx - size * 0.1f, ty, size * 0.05f, 0, 0f, color); g.circle(mx + size * 0.1f, ty, size * 0.05f, 0, 0f, color) }
                "tilde" -> {
                    val p = GPath(); val hw = size * 0.2f
                    p.moveTo(mx - hw, ty + size * 0.03f)
                    p.cubicTo(mx - hw * 0.5f, ty - size * 0.1f, mx - hw * 0.1f, ty - size * 0.02f, mx, ty)
                    p.cubicTo(mx + hw * 0.1f, ty + size * 0.02f, mx + hw * 0.5f, ty + size * 0.1f, mx + hw, ty - size * 0.03f)
                    g.path(p, color, t)
                }
            }
        }
    }

    /** Масштабируемая скобка. */
    private class Delim(val ch: String, val height: Float, val center: Float, val size: Float) : Box() {
        val t = max(size * 0.06f, 1f)
        init {
            w = if (ch == "." || ch.isEmpty()) size * 0.05f else size * (if (ch == "|" || ch == "‖") 0.3f else 0.42f)
            asc = height / 2 + center
            desc = height / 2 - center
        }
        override fun draw(g: Gfx, x: Float, y: Float, color: Int) {
            val top = y - asc
            val bot = y + desc
            val mid = (top + bot) / 2
            val l = x + size * 0.08f
            val r = x + w - size * 0.08f
            val p = GPath()
            when (ch) {
                "(" -> { p.moveTo(r, top); p.cubicTo(l - size * 0.06f, top + (bot - top) * 0.25f, l - size * 0.06f, bot - (bot - top) * 0.25f, r, bot) }
                ")" -> { p.moveTo(l, top); p.cubicTo(r + size * 0.06f, top + (bot - top) * 0.25f, r + size * 0.06f, bot - (bot - top) * 0.25f, l, bot) }
                "[" -> { p.moveTo(r, top); p.lineTo(l + size * 0.04f, top); p.lineTo(l + size * 0.04f, bot); p.lineTo(r, bot) }
                "]" -> { p.moveTo(l, top); p.lineTo(r - size * 0.04f, top); p.lineTo(r - size * 0.04f, bot); p.lineTo(l, bot) }
                "|" -> { val cx = (l + r) / 2; p.moveTo(cx, top); p.lineTo(cx, bot) }
                "‖" -> { val cx = (l + r) / 2; p.moveTo(cx - size * 0.06f, top); p.lineTo(cx - size * 0.06f, bot); p.moveTo(cx + size * 0.06f, top); p.lineTo(cx + size * 0.06f, bot) }
                "{" -> {
                    val cx = (l + r) / 2
                    p.moveTo(r, top)
                    p.quadTo(cx, top, cx, top + (mid - top) * 0.3f)
                    p.lineTo(cx, mid - (mid - top) * 0.25f)
                    p.quadTo(cx, mid, l, mid)
                    p.quadTo(cx, mid, cx, mid + (bot - mid) * 0.25f)
                    p.lineTo(cx, bot - (bot - mid) * 0.3f)
                    p.quadTo(cx, bot, r, bot)
                }
                "}" -> {
                    val cx = (l + r) / 2
                    p.moveTo(l, top)
                    p.quadTo(cx, top, cx, top + (mid - top) * 0.3f)
                    p.lineTo(cx, mid - (mid - top) * 0.25f)
                    p.quadTo(cx, mid, r, mid)
                    p.quadTo(cx, mid, cx, mid + (bot - mid) * 0.25f)
                    p.lineTo(cx, bot - (bot - mid) * 0.3f)
                    p.quadTo(cx, bot, l, bot)
                }
                "⟨" -> { p.moveTo(r, top); p.lineTo(l, mid); p.lineTo(r, bot) }
                "⟩" -> { p.moveTo(l, top); p.lineTo(r, mid); p.lineTo(l, bot) }
                "⌊" -> { p.moveTo(l + size * 0.04f, top); p.lineTo(l + size * 0.04f, bot); p.lineTo(r, bot) }
                "⌋" -> { p.moveTo(r - size * 0.04f, top); p.lineTo(r - size * 0.04f, bot); p.lineTo(l, bot) }
                else -> return
            }
            g.path(p, color, t)
        }
    }

    private class Grid(val rows: List<List<Box>>, val size: Float, val leftAlign: Boolean) : Box() {
        val colW: FloatArray
        val rowA: FloatArray
        val rowD: FloatArray
        val colGap = size * 0.7f
        val rowGap = size * 0.3f
        val axis = size * 0.27f
        init {
            val nc = rows.maxOfOrNull { it.size } ?: 0
            colW = FloatArray(nc)
            rowA = FloatArray(rows.size)
            rowD = FloatArray(rows.size)
            rows.forEachIndexed { r, row ->
                row.forEachIndexed { c, b ->
                    colW[c] = max(colW[c], b.w)
                    rowA[r] = max(rowA[r], b.asc)
                    rowD[r] = max(rowD[r], b.desc)
                }
                if (row.isEmpty()) { rowA[r] = size * 0.7f; rowD[r] = size * 0.2f }
            }
            w = colW.sum() + colGap * max(0, nc - 1)
            val h = (0 until rows.size).sumOf { (rowA[it] + rowD[it]).toDouble() }.toFloat() + rowGap * max(0, rows.size - 1)
            asc = h / 2 + axis
            desc = h / 2 - axis
        }
        override fun draw(g: Gfx, x: Float, y: Float, color: Int) {
            var cy = y - asc
            rows.forEachIndexed { r, row ->
                val by = cy + rowA[r]
                var cx = x
                row.forEachIndexed { c, b ->
                    val off = if (leftAlign) 0f else (colW[c] - b.w) / 2
                    b.draw(g, cx + off, by, color)
                    cx += colW[c] + colGap
                }
                cy += rowA[r] + rowD[r] + rowGap
            }
        }
    }

    private class Stack(val lines: List<Box>, val gap: Float) : Box() {
        init {
            for (l in lines) w = max(w, l.w)
            asc = lines.firstOrNull()?.asc ?: 0f
            desc = lines.foldIndexed(0f) { i, acc, b -> if (i == 0) b.desc else acc + gap + b.asc + b.desc }
        }
        override fun draw(g: Gfx, x: Float, y: Float, color: Int) {
            var by = y
            lines.forEachIndexed { i, b ->
                if (i > 0) by += lines[i - 1].desc + gap + b.asc
                b.draw(g, x, by, color)
            }
        }
    }

    // ------------------------------------------------------------------ таблицы символов

    val SYMBOLS: Map<String, String> = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε", "varepsilon" to "ε",
        "zeta" to "ζ", "eta" to "η", "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ",
        "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ", "pi" to "π", "rho" to "ρ", "sigma" to "σ",
        "tau" to "τ", "upsilon" to "υ", "phi" to "φ", "varphi" to "φ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ", "Pi" to "Π", "Sigma" to "Σ",
        "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
        "infty" to "∞", "to" to "→", "rightarrow" to "→", "leftarrow" to "←", "gets" to "←", "Rightarrow" to "⇒",
        "Leftarrow" to "⇐", "Leftrightarrow" to "⇔", "iff" to "⇔", "implies" to "⇒", "leftrightarrow" to "↔",
        "mapsto" to "↦", "uparrow" to "↑", "downarrow" to "↓", "rightleftharpoons" to "⇌",
        "cdot" to "·", "times" to "×", "div" to "÷", "pm" to "±", "mp" to "∓", "ast" to "∗", "star" to "⋆",
        "le" to "≤", "leq" to "≤", "ge" to "≥", "geq" to "≥", "ne" to "≠", "neq" to "≠", "approx" to "≈",
        "equiv" to "≡", "sim" to "∼", "simeq" to "≃", "cong" to "≅", "propto" to "∝", "perp" to "⊥",
        "parallel" to "∥", "ll" to "≪", "gg" to "≫",
        "angle" to "∠", "triangle" to "△", "circ" to "∘", "deg" to "°", "degree" to "°",
        "partial" to "∂", "nabla" to "∇", "in" to "∈", "notin" to "∉", "ni" to "∋", "subset" to "⊂",
        "subseteq" to "⊆", "supset" to "⊃", "supseteq" to "⊇", "cup" to "∪", "cap" to "∩",
        "emptyset" to "∅", "varnothing" to "∅", "setminus" to "∖",
        "forall" to "∀", "exists" to "∃", "nexists" to "∄", "neg" to "¬", "lnot" to "¬", "land" to "∧", "wedge" to "∧",
        "lor" to "∨", "vee" to "∨", "oplus" to "⊕", "otimes" to "⊗",
        "dots" to "…", "ldots" to "…", "cdots" to "⋯", "vdots" to "⋮", "ddots" to "⋱",
        "hbar" to "ℏ", "ell" to "ℓ", "aleph" to "ℵ", "Re" to "ℜ", "Im" to "ℑ", "wp" to "℘",
        "prime" to "′", "langle" to "⟨", "rangle" to "⟩", "lfloor" to "⌊", "rfloor" to "⌋", "lceil" to "⌈", "rceil" to "⌉",
        "R" to "ℝ", "N" to "ℕ", "Z" to "ℤ", "Q" to "ℚ", "C" to "ℂ", "square" to "□", "blacksquare" to "■",
        "therefore" to "∴", "because" to "∵", "checkmark" to "✓", "degC" to "°C", "Omicron" to "Ο", "micro" to "µ",
        "lbrace" to "{", "rbrace" to "}", "|" to "‖", "{" to "{", "}" to "}", "%" to "%", "$" to "$", "#" to "#", "_" to "_", "&" to "&",
    )

    private val OPS = setOf(
        "+", "−", "-", "=", "<", ">", "≤", "≥", "≠", "≈", "≡", "∼", "≃", "≅", "∝", "→", "←", "⇒", "⇐", "⇔", "↔", "↦",
        "±", "∓", "×", "·", "÷", "∈", "∉", "∋", "⊂", "⊆", "⊃", "⊇", "∪", "∩", "∖", "∧", "∨", "⊕", "⊗", "⊥", "∥",
        "≪", "≫", "⇌", ":=", "∗", "↑", "↓", "∘"
    )

    val FUNCS = setOf(
        "sin", "cos", "tan", "tg", "cot", "ctg", "sec", "csc", "arcsin", "arccos", "arctan", "arctg", "arcctg",
        "sinh", "cosh", "tanh", "sh", "ch", "th", "cth", "exp", "ln", "lg", "log", "lim", "det", "rank", "rg", "dim",
        "max", "min", "sup", "inf", "arg", "gcd", "sgn", "sign", "tr", "Ker", "Im", "grad", "div", "rot", "const", "Pr",
        "limsup", "liminf", "mod", "deg", "span", "proj", "Res", "Arg"
    )
    private val LIMIT_FUNCS = setOf("lim", "max", "min", "sup", "inf", "limsup", "liminf")
    private val BIGOPS = mapOf(
        "sum" to "∑", "prod" to "∏", "coprod" to "∐", "bigcup" to "⋃", "bigcap" to "⋂",
        "int" to "∫", "iint" to "∬", "iiint" to "∭", "oint" to "∮"
    )
    private val MATRIX_DELIMS = mapOf(
        "matrix" to ("" to ""), "pmatrix" to ("(" to ")"), "bmatrix" to ("[" to "]"),
        "vmatrix" to ("|" to "|"), "Vmatrix" to ("‖" to "‖"), "Bmatrix" to ("{" to "}"), "cases" to ("{" to ""),
        "array" to ("" to ""), "aligned" to ("" to ""), "system" to ("{" to "")
    )

    /** Быстрые текстовые замены для удобного ввода со стилуса/клавиатуры. */
    fun preprocess(src: String): String {
        var s = src
        val rep = listOf(
            "<=>" to "⇔", "==>" to "⇒", "=>" to "⇒", "<=" to "≤", ">=" to "≥", "!=" to "≠", "->" to "→", "<-" to "←",
            "+-" to "±", "~=" to "≈", "..." to "…", "*" to "·"
        )
        for ((a, b) in rep) s = s.replace(a, b)
        return s
    }

    // ------------------------------------------------------------------ публичный API

    private val cache = object : LinkedHashMap<String, Box>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Box>?) = size > 200
    }

    @Synchronized
    fun layout(src: String, size: Float): Box {
        val key = "$size|$src"
        cache[key]?.let { return it }
        val lines = preprocess(src).split('\n')
        val boxes = lines.map { line ->
            try { Parser(line, size).parseTop() } catch (e: Exception) { Glyph(line, size, Font.SERIF) }
        }
        val b: Box = if (boxes.size == 1) boxes[0] else Stack(boxes, size * 0.35f)
        cache[key] = b
        return b
    }

    /** Рисует формулу; (x, y) — левый верхний угол. */
    fun draw(g: Gfx, src: String, x: Float, y: Float, size: Float, color: Int) {
        val b = layout(src, size)
        b.draw(g, x, y + b.asc, color)
    }

    // ------------------------------------------------------------------ парсер

    private class Parser(val s: String, val base: Float) {
        var i = 0
        var matrixDepth = 0

        fun parseTop(): Box = HBox(parseRow(base, null))

        private fun atEnd() = i >= s.length

        private fun rowBreak(): Boolean {
            if (matrixDepth == 0 || atEnd()) return false
            if (s[i] == '&') return true
            if (s.startsWith("\\\\", i)) return true
            if (s.startsWith("\\end", i)) return true
            return false
        }

        fun parseRow(size: Float, end: Char?): MutableList<Box> {
            val out = ArrayList<Box>()
            while (!atEnd()) {
                val c = s[i]
                if (end != null && c == end) { i++; return out }
                if (rowBreak()) return out
                if (end == null && c == '}') { i++; continue }
                if (c == ' ') {
                    i++
                    val prevOp = out.lastOrNull()?.isOp == true
                    val nextOp = !atEnd() && (s[i].toString() in OPS || s[i] == ' ')
                    if (!prevOp && !nextOp && out.isNotEmpty()) out.add(Space(size * 0.26f))
                    continue
                }
                val atom = parseAtom(size, out) ?: continue
                out.add(parseScripts(atom, size))
            }
            return out
        }

        private fun parseScripts(base: Box, size: Float): Box {
            var sup: Box? = null
            var sub: Box? = null
            var primes = ""
            while (!atEnd()) {
                when (s[i]) {
                    '^' -> { i++; sup = parseArg(scriptSize(size)) }
                    '_' -> { i++; sub = parseArg(scriptSize(size)) }
                    '\'' -> { i++; primes += "′" }
                    else -> break
                }
            }
            if (primes.isNotEmpty()) {
                val pb = Glyph(primes, scriptSize(size), Font.SERIF)
                sup = if (sup == null) pb else HBox(listOf(pb, sup))
            }
            if (sup == null && sub == null) return base
            if (base is BigOpHolder) return base.build(sub, sup)
            return Script(base, sup, sub, size)
        }

        private fun scriptSize(size: Float) = max(size * 0.7f, base * 0.45f)
        private fun fracSize(size: Float) = max(size * 0.88f, base * 0.5f)

        /** Аргумент команды: {группа} или один символ/команда. */
        private fun parseArg(size: Float): Box {
            while (!atEnd() && s[i] == ' ') i++
            if (atEnd()) return Space(0f)
            if (s[i] == '{') { i++; return HBox(parseRow(size, '}')) }
            return parseAtom(size, null) ?: Space(0f)
        }

        private fun rawGroup(): String {
            while (!atEnd() && s[i] == ' ') i++
            if (atEnd()) return ""
            if (s[i] != '{') { val c = s[i].toString(); i++; return c }
            i++
            val st = i
            var depth = 1
            while (!atEnd()) {
                if (s[i] == '{') depth++
                if (s[i] == '}') { depth--; if (depth == 0) break }
                i++
            }
            val r = s.substring(st, min(i, s.length))
            if (!atEnd()) i++
            return r
        }

        private fun readCmd(): String {
            // s[i] == '\\'
            i++
            if (atEnd()) return ""
            if (!s[i].isLetter()) { val c = s[i].toString(); i++; return c }
            val st = i
            while (!atEnd() && s[i].isLetter()) i++
            return s.substring(st, i)
        }

        private fun symbolBox(t: String, size: Float): Box {
            if (t in OPS) {
                val shown = if (t == "-") "−" else t
                return Glyph(shown, size, Font.SERIF, true, size * 0.17f)
            }
            if (t == ",") return HBox(listOf(Glyph(",", size, Font.SERIF), Space(size * 0.15f)))
            val c = t[0]
            val italic = t.length == 1 && ((c in 'a'..'z') || (c in 'A'..'Z') || (c in 'α'..'ω'))
            return Glyph(t, size, if (italic) Font.SERIF_ITALIC else Font.SERIF)
        }

        private fun parseMatrix(kind: String, size: Float, body: String?): Box {
            val sub = if (body != null) Parser(body, base).also { it.matrixDepth = 1 } else this.also { matrixDepth++ }
            val rows = ArrayList<List<Box>>()
            var cur = ArrayList<Box>()
            val cellSize = size
            while (!sub.atEnd()) {
                val cell = HBox(sub.parseRow(cellSize, null))
                cur.add(cell)
                if (sub.atEnd()) break
                if (sub.s[sub.i] == '&') { sub.i++; continue }
                if (sub.s.startsWith("\\\\", sub.i)) { sub.i += 2; rows.add(cur); cur = ArrayList(); continue }
                if (sub.s.startsWith("\\end", sub.i)) { sub.i += 4; sub.rawGroup(); break }
                break
            }
            if (cur.isNotEmpty() && !(cur.size == 1 && cur[0].w == 0f)) rows.add(cur)
            if (body == null) matrixDepth--
            val left = kind == "cases" || kind == "system" || kind == "aligned"
            val grid = Grid(rows, size, left)
            val (l, r) = MATRIX_DELIMS[kind] ?: ("" to "")
            if (l.isEmpty() && r.isEmpty()) return grid
            val h = grid.asc + grid.desc + size * 0.2f
            val c = (grid.asc - grid.desc) / 2
            val items = ArrayList<Box>()
            if (l.isNotEmpty()) items.add(Delim(l, h, c, size))
            items.add(Space(size * 0.08f)); items.add(grid); items.add(Space(size * 0.08f))
            if (r.isNotEmpty()) items.add(Delim(r, h, c, size))
            return HBox(items)
        }

        private fun delimToken(): String {
            while (!atEnd() && s[i] == ' ') i++
            if (atEnd()) return "."
            if (s[i] == '\\') {
                val cmd = readCmd()
                return when (cmd) {
                    "{", "lbrace" -> "{"; "}", "rbrace" -> "}"; "|", "Vert" -> "‖"; "langle" -> "⟨"; "rangle" -> "⟩"
                    "lfloor" -> "⌊"; "rfloor" -> "⌋"; else -> "."
                }
            }
            val c = s[i].toString(); i++
            return when (c) { "<" -> "⟨"; ">" -> "⟩"; else -> c }
        }

        private fun parseAtom(size: Float, row: MutableList<Box>?): Box? {
            val c = s[i]
            when (c) {
                '{' -> { i++; return HBox(parseRow(size, '}')) }
                '\\' -> return parseCommand(size)
                '^', '_' -> return Space(0f)
            }
            // слово из латинских букв: функция (sin, lim…) или переменные
            if (c in 'a'..'z' || c in 'A'..'Z') {
                var j = i
                while (j < s.length && (s[j] in 'a'..'z' || s[j] in 'A'..'Z')) j++
                val word = s.substring(i, j)
                val fn = FUNCS.filter { f ->
                    word == f || (word.startsWith(f) && ((f.length >= 3 && word.length - f.length <= 2) ||
                        (f.length == 2 && word.length == 3)))
                }.maxByOrNull { it.length }
                if (fn != null && fn.length >= 2) {
                    i += fn.length
                    return funcBox(fn, size)
                }
                i++
                return symbolBox(c.toString(), size)
            }
            // кириллица и прочий текст — прямым шрифтом целым словом
            if (c.isLetter() && !(c in 'α'..'ω') && !(c in 'Α'..'Ω')) {
                var j = i
                while (j < s.length && s[j].isLetter() && !(s[j] in 'α'..'ω')) j++
                val w = s.substring(i, j)
                i = j
                return Glyph(w, size, Font.SERIF)
            }
            if (c.isDigit() || c == '.') {
                var j = i
                while (j < s.length && (s[j].isDigit() || (s[j] == '.' && j + 1 < s.length && s[j + 1].isDigit()))) j++
                if (j == i) j = i + 1
                val num = s.substring(i, j)
                i = j
                return Glyph(num, size, Font.SERIF)
            }
            if (c == ':' && i + 1 < s.length && s[i + 1] == '=') { i += 2; return symbolBox(":=", size) }
            i++
            // суррогатные пары (эмодзи и редкие символы)
            if (Character.isHighSurrogate(c) && !atEnd()) { val t = "$c${s[i]}"; i++; return Glyph(t, size, Font.SERIF) }
            return symbolBox(c.toString(), size)
        }

        private fun funcBox(name: String, size: Float): Box {
            val g = Glyph(name, size, Font.SERIF)
            val withSpace = HBox(listOf(g, Space(size * 0.12f)))
            return if (name in LIMIT_FUNCS) BigOpHolder(null, withSpace, size) else withSpace
        }

        private fun parseCommand(size: Float): Box? {
            val cmd = readCmd()
            BIGOPS[cmd]?.let { return BigOpHolder(it, null, size, integral = cmd.contains("int")) }
            if (cmd in FUNCS) return funcBox(cmd, size)
            SYMBOLS[cmd]?.let { return symbolBox(it, size) }
            when (cmd) {
                "frac", "dfrac", "tfrac", "cfrac" -> {
                    val fs = if (cmd == "tfrac") scriptSize(size) else fracSize(size)
                    val n = parseArg(fs); val d = parseArg(fs)
                    return Frac(n, d, size)
                }
                "binom" -> {
                    val fs = fracSize(size)
                    val n = parseArg(fs); val d = parseArg(fs)
                    val fr = Frac(n, d, size, line = false)
                    val h = fr.asc + fr.desc
                    val cc = (fr.asc - fr.desc) / 2
                    return HBox(listOf(Delim("(", h, cc, size), fr, Delim(")", h, cc, size)))
                }
                "sqrt" -> {
                    var idx: Box? = null
                    if (!atEnd() && s[i] == '[') {
                        i++
                        val st = i
                        while (!atEnd() && s[i] != ']') i++
                        idx = Parser(s.substring(st, i), base).let { HBox(it.parseRow(scriptSize(size) * 0.85f, null)) }
                        if (!atEnd()) i++
                    }
                    return Sqrt(parseArg(size), idx, size)
                }
                "vec", "overrightarrow" -> return Accent(parseArg(size), "vec", size)
                "bar", "overline" -> return Accent(parseArg(size), "bar", size)
                "underline" -> return Accent(parseArg(size), "under", size)
                "hat", "widehat" -> return Accent(parseArg(size), "hat", size)
                "dot" -> return Accent(parseArg(size), "dot", size)
                "ddot" -> return Accent(parseArg(size), "ddot", size)
                "tilde", "widetilde" -> return Accent(parseArg(size), "tilde", size)
                "text", "mathrm", "operatorname", "textrm" -> return Glyph(rawGroup(), size, Font.SERIF)
                "textbf", "mathbf", "bf" -> return Glyph(rawGroup(), size, Font.SERIF_BOLD)
                "mathit", "textit" -> return Glyph(rawGroup(), size, Font.SERIF_ITALIC)
                "mathbb" -> {
                    val t = rawGroup()
                    return Glyph(t.map { SYMBOLS[it.toString()]?.takeIf { s -> s.length == 1 } ?: it.toString() }.joinToString(""), size, Font.SERIF)
                }
                "overset", "stackrel" -> {
                    val top = parseArg(scriptSize(size)); val body = parseArg(size)
                    return BigOpHolder(null, body, size).build(null, top)
                }
                "underset" -> {
                    val bot = parseArg(scriptSize(size)); val body = parseArg(size)
                    return BigOpHolder(null, body, size).build(bot, null)
                }
                "quad" -> return Space(size)
                "qquad" -> return Space(size * 2)
                ",", "thinspace" -> return Space(size * 0.17f)
                ";", ":" -> return Space(size * 0.28f)
                "!" -> return Space(-size * 0.1f)
                " " -> return Space(size * 0.25f)
                "left" -> {
                    val l = delimToken()
                    val inner = ArrayList<Box>()
                    var r = "."
                    while (!atEnd()) {
                        if (s.startsWith("\\right", i)) { i += 6; r = delimToken(); break }
                        if (rowBreak()) break
                        if (s[i] == ' ') { i++; continue }
                        val a = parseAtom(size, inner) ?: continue
                        inner.add(parseScripts(a, size))
                    }
                    val body = HBox(inner)
                    val h = max(body.asc + body.desc, size) + size * 0.15f
                    val cc = (body.asc - body.desc) / 2
                    return HBox(listOf(Delim(l, h, cc, size), body, Delim(r, h, cc, size)))
                }
                "right" -> { delimToken(); return null }
                "begin" -> {
                    val env = rawGroup()
                    return parseMatrix(env, size, null)
                }
                "end" -> { rawGroup(); return null }
            }
            if (cmd in MATRIX_DELIMS) {
                val body = rawGroup()
                return parseMatrix(cmd, size, body)
            }
            // неизвестная команда — показываем как текст
            return Glyph("\\" + cmd, size, Font.SERIF)
        }
    }

    /** Заготовка большого оператора/функции с пределами — индексы присоединяются позже. */
    private class BigOpHolder(val sym: String?, val word: Box?, val size: Float, val integral: Boolean = false) : Box() {
        private val plain: Box = if (sym != null) BigOp(sym, null, null, !integral, size, integral) else word!!
        init { w = plain.w; asc = plain.asc; desc = plain.desc }
        override fun draw(g: Gfx, x: Float, y: Float, color: Int) = plain.draw(g, x, y, color)

        fun build(lower: Box?, upper: Box?): Box {
            if (sym != null) return BigOp(sym, lower, upper, !integral, size, integral)
            return Limits(word!!, lower, upper, size)
        }
    }

    private class Limits(val body: Box, val lower: Box?, val upper: Box?, val size: Float) : Box() {
        val gap = size * 0.08f
        init {
            w = maxOf(body.w, lower?.w ?: 0f, upper?.w ?: 0f)
            asc = body.asc + (upper?.let { it.asc + it.desc + gap } ?: 0f)
            desc = body.desc + (lower?.let { it.asc + it.desc + gap } ?: 0f)
        }
        override fun draw(g: Gfx, x: Float, y: Float, color: Int) {
            body.draw(g, x + (w - body.w) / 2, y, color)
            upper?.let { it.draw(g, x + (w - it.w) / 2, y - body.asc - gap - it.desc, color) }
            lower?.let { it.draw(g, x + (w - it.w) / 2, y + body.desc + gap + it.asc, color) }
        }
    }
}
