package com.paintphymath

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

object Dialogs {

    private fun dpi(c: Context, v: Float) = Ui.dpi(c, v)

    private fun column(c: Context, padDp: Float = 20f) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        val p = dpi(c, padDp)
        setPadding(p, dpi(c, 8f), p, dpi(c, 8f))
    }

    private fun scroll(c: Context, v: View) = ScrollView(c).apply { addView(v) }

    private fun f(s: String): Float? = Expr.tryParse(s.trim())?.eval(Expr.baseVars())?.toFloat()?.takeIf { !it.isNaN() }

    // ================================================================== предпросмотр формул

    class MathPreview(ctx: Context) : View(ctx) {
        var src = ""
        var mode = 1
        var color = Color.BLACK
        var size = 24f
        override fun onDraw(c: Canvas) {
            c.drawColor(0xFFF8FAFC.toInt())
            val g = CanvasGfx(c, 1f)
            val d = resources.displayMetrics.density
            val s = size * d * 0.9f
            try {
                if (mode == 1) {
                    val b = MathText.layout(src, s)
                    val h = b.asc + b.desc
                    val k = min(1f, min((width - 24 * d) / max(b.w, 1f), (height - 12 * d) / max(h, 1f)))
                    c.save()
                    c.translate(12 * d, (height - h * k) / 2)
                    c.scale(k, k)
                    b.draw(g, 0f, b.asc, color)
                    c.restore()
                } else {
                    val lines = src.split('\n')
                    val font = if (mode == 2) Font.SANS_BOLD else Font.SANS
                    lines.forEachIndexed { i, l -> g.text(l, 12 * d, 12 * d + s + i * s * 1.3f, s, color, font) }
                }
            } catch (e: Exception) {}
        }
    }

    // ================================================================== текст / формула / название

    private val TEMPLATES = listOf(
        "a/b" to "\\frac{}{}", "xⁿ" to "^{}", "xₙ" to "_{}", "√" to "\\sqrt{}", "ⁿ√" to "\\sqrt[n]{}", "a⃗" to "\\vec{}",
        "x̄" to "\\overline{}", "∑" to "\\sum_{n=1}^{\\infty} ", "∏" to "\\prod_{k=1}^{n} ", "∫" to "\\int_{a}^{b} ", "∮" to "\\oint ",
        "lim" to "\\lim_{x\\to 0} ", "(A)" to "\\pmatrix{a & b \\\\ c & d}", "[A]" to "\\bmatrix{a & b \\\\ c & d}",
        "|A|" to "\\vmatrix{a & b \\\\ c & d}", "{" to "\\cases{x + y = 1 \\\\ x - y = 0}", "()" to "\\left(  \\right)",
        "Cₙᵏ" to "\\binom{n}{k}", "f'" to "f'(x)", "dy/dx" to "\\frac{dy}{dx}", "∂" to "\\frac{\\partial f}{\\partial x}",
        "|x|" to "\\left| x \\right|", "ẋ" to "\\dot{}", "x̂" to "\\hat{}", "текст" to "\\text{}", "sin" to "\\sin ", "cos" to "\\cos ",
        "log" to "\\log_{a} ", "ln" to "\\ln "
    )
    private val GREEK = "α β γ δ ε ζ η θ ι κ λ μ ν ξ π ρ σ τ υ φ χ ψ ω Γ Δ Θ Λ Ξ Π Σ Φ Ψ Ω".split(' ')
    private val SYMS = "± ∓ × · ÷ ≤ ≥ ≠ ≈ ≡ ∼ ∝ ∞ → ⇒ ⇔ ← ↔ ↦ ∈ ∉ ⊂ ⊆ ∪ ∩ ∅ ∀ ∃ ¬ ∧ ∨ ∂ ∇ ∠ △ ⊥ ∥ ° ′ ″ ℝ ℕ ℤ ℚ ℂ ℏ ∴ ∵ … ⟨ ⟩".split(' ')
    private val UNITS = listOf("м", "см", "мм", "км", "с", "мин", "ч", "кг", "г", "м/с", "м/с^2", "км/ч", "Н", "Н·м", "Дж", "кДж", "Вт", "кВт", "Па", "кПа",
        "атм", "К", "°C", "моль", "Кл", "А", "В", "Ом", "Ф", "мкФ", "Гн", "Тл", "Вб", "Гц", "рад", "рад/с", "м^3", "л", "кг/м^3", "эВ")
    private val NAMING = listOf("Задача №", "Дано:", "Найти:", "Решение:", "Ответ:", "Доказательство:", "Пример:", "Определение:", "Теорема:",
        "∠ABC", "△ABC", "|AB|", "\\vec{AB}", "\\overline{AB}", "A_1", "x_0", "y'", "f(x)", "f^{-1}(x)", "\\{a_n\\}", "S_n", "Δx", "\\vec{F}_{тр}", "\\vec{v}_0")

    fun text(a: MainActivity, x: Float, y: Float, existing: TextEl?) {
        val cv = a.canvas
        val col = column(a)
        var mode = existing?.mode ?: 1
        var size = existing?.size ?: 24f
        var bg = existing?.bg ?: 0
        val color = existing?.color ?: cv.colorOf(Tool.TEXT)
        val preview = MathPreview(a).apply { this.color = color }
        val input = Ui.input(a, "Например: \\frac{a}{b}, x^2, \\sum_{n=1}^{\\infty}, \\vec{F} = m\\vec{a}", existing?.text ?: "", multi = true)
        fun upd() { preview.src = input.text.toString(); preview.mode = if (mode == 3) 0 else mode; preview.size = size; preview.invalidate() }
        Ui.onText(input) { upd() }

        val modes = Ui.wrapRow(a)
        val modeNames = listOf("Формула", "Текст", "Заголовок", "Стикер")
        fun buildModes() {
            modes.removeAllViews()
            modeNames.forEachIndexed { i, s ->
                val cur = if (bg != 0) 3 else mode
                modes.addView(Ui.chip(a, s, i == cur) {
                    when (i) {
                        0 -> { mode = 1; bg = 0 }
                        1 -> { mode = 0; bg = 0 }
                        2 -> { mode = 2; bg = 0; if (size < 30f) size = 34f }
                        3 -> { mode = 0; bg = 0xFFFEF3C7.toInt() }
                    }
                    buildModes(); upd()
                })
            }
        }
        buildModes()
        col.addView(Ui.hscroll(a, modes))
        col.addView(preview, LinearLayout.LayoutParams(Ui.MATCH, dpi(a, 96f)).apply { setMargins(0, dpi(a, 6f), 0, dpi(a, 6f)) })
        col.addView(input)

        // палитры
        val tabs = Ui.wrapRow(a)
        val palette = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        val tabNames = listOf("Шаблоны", "Названия", "Греческие", "Символы", "Единицы")
        var tab = 0
        fun buildPalette() {
            palette.removeAllViews()
            val items: List<Pair<String, String>> = when (tab) {
                0 -> TEMPLATES
                1 -> listOf("Точка: ${cv.nextPointName()}" to cv.nextPointName()) + NAMING.map { it to it }
                2 -> GREEK.map { it to it }
                3 -> SYMS.map { it to it }
                else -> UNITS.map { it to "\\ " + it }
            }
            for ((label, snip) in items) palette.addView(Ui.chip(a, label, false) { Ui.insertSnippet(input, snip) })
        }
        fun buildTabs() {
            tabs.removeAllViews()
            tabNames.forEachIndexed { i, s -> tabs.addView(Ui.chip(a, s, i == tab) { tab = i; buildTabs(); buildPalette() }) }
        }
        buildTabs(); buildPalette()
        col.addView(Ui.hscroll(a, tabs))
        col.addView(Ui.hscroll(a, palette))
        col.addView(Ui.labeled(a, "Размер", Ui.seek(a, 90, (size - 10).toInt()) { size = 10f + it; upd() }))
        col.addView(Ui.text(a, "Подсказка: -> → стрелка, <= ≤, != ≠, * → ·, перенос строки — новая строка.", 12f, Ui.MUTED))
        upd()

        val d = AlertDialog.Builder(a)
            .setTitle(if (existing == null || existing.id == -1L) "Текст, формула, название" else "Редактировать")
            .setView(scroll(a, col))
            .setPositiveButton("Готово") { _, _ ->
                val s = input.text.toString()
                if (existing != null && existing.id != -1L) {
                    if (s.isBlank()) { cv.doc.remove(listOf(existing)); cv.contentChanged(); return@setPositiveButton }
                    cv.doc.modify(listOf(existing)) { existing.text = s; existing.mode = mode; existing.size = size; existing.bg = bg; existing.invalidateCache() }
                    cv.contentChanged()
                } else if (s.isNotBlank()) {
                    val t = TextEl().also { it.x = x; it.y = y; it.text = s; it.mode = mode; it.size = size; it.bg = bg; it.color = color }
                    cv.doc.add(t); cv.contentChanged()
                }
            }
            .setNegativeButton("Отмена", null)
            .create()
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        d.show()
        input.requestFocus()
        input.setSelection(input.text.length)
    }

    /** Вставка готовой формулы в центр экрана. */
    fun insertFormula(a: MainActivity, s: String, size: Float = 24f) {
        val c = a.canvas.centerWorld()
        val b = MathText.layout(s, size)
        val t = TextEl().also { it.text = s; it.mode = 1; it.size = size; it.color = a.canvas.colorOf(Tool.TEXT); it.x = c.x - b.w / 2; it.y = c.y - (b.asc + b.desc) / 2 }
        a.canvas.addAtCenter(t, false)
        a.canvas.select(listOf(t))
    }

    fun rename(a: MainActivity, title: String, value: String, cb: (String) -> Unit) {
        val col = column(a)
        val input = Ui.input(a, title, value)
        col.addView(input)
        val quick = Ui.wrapRow(a)
        for (s in listOf("A", "B", "C", "O", "M", "α", "β", "\\vec{a}", "\\vec{F}", "_1", "_0", "'")) quick.addView(Ui.chip(a, s, false) { Ui.insertSnippet(input, s) })
        col.addView(Ui.hscroll(a, quick))
        AlertDialog.Builder(a).setTitle(title).setView(col)
            .setPositiveButton("OK") { _, _ -> cb(input.text.toString()) }
            .setNegativeButton("Отмена", null).show()
        input.requestFocus()
    }

    fun linkLabel(a: MainActivity, link: LinkEl) {
        val cv = a.canvas
        val col = column(a)
        val input = Ui.input(a, "Подпись связи (необязательно): F = ma, y(x), ∝, «зависит от»…", link.label)
        col.addView(input)
        val quick = Ui.wrapRow(a)
        for (s in listOf("зависит от", "⇒", "∝", "=", "f", "y(x)", "\\frac{d}{dt}", "\\int", "F = ma", "причина", "следствие")) quick.addView(Ui.chip(a, s, false) { Ui.insertSnippet(input, s) })
        col.addView(Ui.hscroll(a, quick))
        val styles = Ui.wrapRow(a)
        var st = link.style
        var bend = link.bend
        fun bs() { styles.removeAllViews(); listOf("→", "—", "↔", "⇢").forEachIndexed { i, s -> styles.addView(Ui.chip(a, s, i == st) { st = i; bs() }) } }
        bs()
        col.addView(styles)
        col.addView(Ui.labeled(a, "Изгиб", Ui.seek(a, 200, (bend + 100).toInt().coerceIn(0, 200)) { bend = it - 100f }))
        AlertDialog.Builder(a).setTitle("Связь").setView(col)
            .setPositiveButton("OK") { _, _ ->
                val l = cv.doc.find(link.id) as? LinkEl ?: return@setPositiveButton
                cv.doc.modify(listOf(l)) { l.label = input.text.toString(); l.style = st; l.bend = bend }
                cv.contentChanged()
            }
            .setNegativeButton("Без подписи", null).show()
    }

    // ================================================================== меню вставки

    fun insertMenu(a: MainActivity) {
        val col = column(a, 16f)
        var dlg: Dialog? = null
        fun section(title: String, items: List<Triple<String, String, () -> Unit>>) {
            col.addView(Ui.header(a, title))
            val grid = GridLayout(a).apply { columnCount = 3 }
            for ((icon, label, act) in items) {
                val tile = LinearLayout(a).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = Ui.ripple(Ui.round(Ui.CARD, Ui.dp(a, 14f)), Ui.dp(a, 14f))
                    setPadding(dpi(a, 6f), dpi(a, 10f), dpi(a, 6f), dpi(a, 10f))
                    isClickable = true
                    setOnClickListener { dlg?.dismiss(); act() }
                }
                tile.addView(IconView(a, icon, Ui.ACCENT, 0.7f), LinearLayout.LayoutParams(dpi(a, 34f), dpi(a, 34f)))
                tile.addView(Ui.text(a, label, 12f, Ui.TEXT).apply { gravity = Gravity.CENTER; maxLines = 2 })
                grid.addView(tile, GridLayout.LayoutParams().apply {
                    width = dpi(a, 112f); height = dpi(a, 86f)
                    setMargins(dpi(a, 4f), dpi(a, 4f), dpi(a, 4f), dpi(a, 4f))
                })
            }
            col.addView(grid)
        }
        val cv = a.canvas
        section("Координаты · линейная алгебра · аналитическая геометрия", listOf(
            Triple("axes", "Оси координат") { addAxesAtCenter(a) },
            Triple("graph", "График y = f(x)") { plot(a, PlotEl.FX) },
            Triple("func", "Параметрическая кривая") { plot(a, PlotEl.PARAM) },
            Triple("circle", "Полярная кривая r(θ)") { plot(a, PlotEl.POLAR) },
            Triple("vector", "Вектор") { a.selectTool(Tool.LINE); cv.lineMode = 2; cv.host?.onStateChanged() },
            Triple("matrix", "Матрица и калькулятор") { matrix(a) },
            Triple("@A·x", "Линейное отображение") { plot(a, PlotEl.LINMAP) },
            Triple("point", "Точки с координатами") { a.selectTool(Tool.POINT) },
            Triple("angle", "Угол") { a.selectTool(Tool.ANGLE) },
        ))
        section("Анализ · алгебра · ряды", listOf(
            Triple("series", "Ряд / последовательность") { series(a) },
            Triple("@∫", "Интеграл и касательная") { plot(a, PlotEl.FX) },
            Triple("@y'", "Поле направлений ОДУ") { plot(a, PlotEl.SLOPE) },
            Triple("sqrt", "Формула") { val c = cv.centerWorld(); text(a, c.x, c.y, null) },
            Triple("table", "Таблица значений f(x)") { valueTable(a) },
            Triple("sigma", "Справочник формул") { formulas(a) },
        ))
        section("Физика · связи · зависимости", listOf(
            Triple("stamp", "Шаблоны: механика, схемы, оптика") { stampPicker(a); a.selectTool(Tool.STAMP) },
            Triple("@F⃗", "Векторное поле") { plot(a, PlotEl.VFIELD) },
            Triple("graph", "Данные опыта + МНК") { plot(a, PlotEl.DATA) },
            Triple("const", "Физические константы") { constants(a) },
            Triple("link", "Связь / зависимость") { a.selectTool(Tool.LINK) },
            Triple("@⚛", "Формулы физики") { formulas(a, 2) },
        ))
        section("Другое", listOf(
            Triple("image", "Изображение") { a.pickImage() },
            Triple("table", "Таблица") { table(a, null) },
            Triple("text", "Заголовок") { val c = cv.centerWorld(); insertHeading(a, c.x, c.y) },
            Triple("@✎", "Стикер-заметка") { val c = cv.centerWorld(); text(a, c.x, c.y, TextEl().also { it.mode = 0; it.bg = 0xFFFEF3C7.toInt(); it.x = c.x; it.y = c.y; it.id = -1 }) },
        ))
        dlg = AlertDialog.Builder(a).setTitle("Вставить").setView(scroll(a, col)).setNegativeButton("Закрыть", null).show()
    }

    private fun insertHeading(a: MainActivity, x: Float, y: Float) {
        text(a, x, y, TextEl().also { it.mode = 2; it.size = 34f; it.x = x; it.y = y; it.text = "Задача №"; it.id = -1 })
    }

    fun addAxesAtCenter(a: MainActivity): AxesEl {
        val cv = a.canvas
        val c = cv.centerWorld()
        val s = Paper.snap(1, c.x, c.y) ?: (c.x to c.y)
        val ax = AxesEl().also { it.ox = s.first; it.oy = s.second; it.unit = Paper.GRID }
        val vw = cv.width / cv.scale; val vh = cv.height / cv.scale
        val hx = ((vw * 0.38f) / ax.unit).roundToInt().coerceIn(3, 12).toFloat()
        val hy = ((vh * 0.36f) / ax.unit).roundToInt().coerceIn(3, 10).toFloat()
        ax.xMin = -hx; ax.xMax = hx; ax.yMin = -hy; ax.yMax = hy
        cv.doc.add(ax); cv.contentChanged()
        return ax
    }

    // ================================================================== графики

    private val KIND_NAMES = mapOf(
        PlotEl.FX to "График y = f(x)", PlotEl.PARAM to "Параметрическая кривая", PlotEl.POLAR to "Полярная кривая",
        PlotEl.SEQ to "Последовательность aₙ", PlotEl.SERIES to "Частичные суммы ряда Sₙ", PlotEl.DATA to "Данные + аппроксимация (МНК)",
        PlotEl.LINMAP to "Линейное отображение 2×2", PlotEl.SLOPE to "Поле направлений y' = f(x, y)", PlotEl.VFIELD to "Векторное поле F(x, y)"
    )

    fun plot(a: MainActivity, kind0: Int, existing: PlotEl? = null) {
        val cv = a.canvas
        val col = column(a)
        var kind = existing?.kind ?: kind0
        val e1 = Ui.input(a, "", existing?.e1 ?: "")
        val e2 = Ui.input(a, "", existing?.e2 ?: "")
        fun ns(v: Float?) = if (v == null || v.isNaN()) "" else Expr.fmt(v.toDouble(), 6).replace("−", "-")
        val t0 = Ui.input(a, "", ns(existing?.t0), numeric = true)
        val t1 = Ui.input(a, "", ns(existing?.t1), numeric = true)
        val params = Ui.input(a, "a = 1; b = 2 (необязательно)", existing?.params ?: "")
        val tangent = Ui.input(a, "x₀ (необязательно)", ns(existing?.tangentAt), numeric = true)
        val shA = Ui.input(a, "a", ns(existing?.shadeA), numeric = true)
        val shB = Ui.input(a, "b", ns(existing?.shadeB), numeric = true)
        val solX = Ui.input(a, "x₀", ns(existing?.solX), numeric = true)
        val solY = Ui.input(a, "y₀", ns(existing?.solY), numeric = true)
        val data = Ui.input(a, "x y — по строке на точку:\n0 0\n1 2.1\n2 3.9", existing?.data?.let { d ->
            (0 until d.size / 2).joinToString("\n") { "${Expr.fmt(d[it * 2].toDouble(), 6)} ${Expr.fmt(d[it * 2 + 1].toDouble(), 6)}".replace("−", "-") }
        } ?: "", multi = true)
        val mIn = (0 until 4).map { i -> Ui.input(a, "", existing?.m?.get(i)?.let { Expr.fmt(it.toDouble(), 5).replace("−", "-") } ?: (if (i == 0 || i == 3) "1" else if (i == 1) "1" else "0"), numeric = true) }
        val label = Ui.input(a, "Своя подпись (формула), необязательно", existing?.label ?: "")
        var fit = existing?.fit ?: 1
        var dash = existing?.dash ?: false
        var showLabel = existing?.showLabel ?: true
        var color = existing?.color ?: PLOT_COLORS[(cv.doc.els.count { it is PlotEl }) % PLOT_COLORS.size]
        val info = Ui.text(a, "", 13f, Ui.MUTED)
        val body = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }

        val kinds = Ui.wrapRow(a)
        fun build() {
            body.removeAllViews()
            kinds.removeAllViews()
            if (existing == null) for (k in listOf(PlotEl.FX, PlotEl.PARAM, PlotEl.POLAR, PlotEl.SEQ, PlotEl.SERIES, PlotEl.DATA, PlotEl.LINMAP, PlotEl.SLOPE, PlotEl.VFIELD)) {
                kinds.addView(Ui.chip(a, KIND_NAMES[k]!!.substringBefore(" ("), k == kind) {
                    if (kind != k) { kind = k; e1.setText(""); e2.setText(""); t0.setText(""); t1.setText(""); build() }
                })
            }
            fun add(label: String, v: View) { (v.parent as? ViewGroup)?.removeView(v); body.addView(Ui.labeled(a, label, v)) }
            fun pair(l1: String, v1: View, l2: String, v2: View) {
                (v1.parent as? ViewGroup)?.removeView(v1); (v2.parent as? ViewGroup)?.removeView(v2)
                val r = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
                r.addView(Ui.labeled(a, l1, v1), LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { setMargins(0, 0, dpi(a, 6f), 0) })
                r.addView(Ui.labeled(a, l2, v2), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
                body.addView(r)
            }
            fun defaults(d1: String, d2: String = "") { if (e1.text.isEmpty()) e1.setText(d1); if (e2.text.isEmpty() && d2.isNotEmpty()) e2.setText(d2) }
            when (kind) {
                PlotEl.FX -> {
                    defaults("sin(x) + x/2")
                    add("y = f(x)   (sin, cos, tg, ln, exp, sqrt, abs, ^, !, pi, e…)", e1)
                    pair("x от (необязательно)", t0, "x до", t1)
                    add("Касательная в точке x₀", tangent)
                    pair("Площадь / интеграл: от a", shA, "до b", shB)
                    add("Параметры", params)
                }
                PlotEl.PARAM -> {
                    defaults("cos(3t)", "sin(2t)")
                    add("x(t) =", e1); add("y(t) =", e2)
                    if (t0.text.isEmpty()) t0.setText("0"); if (t1.text.isEmpty()) t1.setText("2pi")
                    pair("t от", t0, "t до", t1); add("Параметры", params)
                }
                PlotEl.POLAR -> {
                    defaults("2 sin(3θ)")
                    add("r(θ) =", e1)
                    if (t0.text.isEmpty()) t0.setText("0"); if (t1.text.isEmpty()) t1.setText("2pi")
                    pair("θ от", t0, "θ до", t1); add("Параметры", params)
                }
                PlotEl.SEQ, PlotEl.SERIES -> {
                    defaults(if (kind == PlotEl.SEQ) "(1 + 1/n)^n" else "1/n^2")
                    add("aₙ =  (переменная n)", e1)
                    if (t0.text.isEmpty()) t0.setText("1"); if (t1.text.isEmpty()) t1.setText("20")
                    pair("n от", t0, "n до", t1); add("Параметры", params)
                }
                PlotEl.DATA -> {
                    add("Измерения (x y). Можно вставить из таблицы", data)
                    val fits = Ui.wrapRow(a)
                    fun bf() { fits.removeAllViews(); listOf("Без линии", "Линейная", "Квадратичная", "Экспонента", "Степенная").forEachIndexed { i, s -> fits.addView(Ui.chip(a, s, i == fit) { fit = i; bf() }) } }
                    bf()
                    body.addView(Ui.text(a, "Аппроксимация методом наименьших квадратов", 12f, Ui.MUTED))
                    body.addView(Ui.hscroll(a, fits))
                }
                PlotEl.LINMAP -> {
                    body.addView(Ui.text(a, "Матрица A (образы базисных векторов — столбцы)", 12f, Ui.MUTED))
                    pair("a₁₁", mIn[0], "a₁₂", mIn[1]); pair("a₂₁", mIn[2], "a₂₂", mIn[3])
                    val presets = Ui.wrapRow(a)
                    for ((n, m) in listOf("Поворот 30°" to listOf("cos(pi/6)", "-sin(pi/6)", "sin(pi/6)", "cos(pi/6)"), "Сдвиг" to listOf("1", "1", "0", "1"),
                        "Растяжение" to listOf("2", "0", "0", "0.5"), "Отражение" to listOf("1", "0", "0", "-1"), "Проекция" to listOf("1", "0", "0", "0"))) {
                        presets.addView(Ui.chip(a, n, false) { for (i in 0 until 4) mIn[i].setText(m[i]) })
                    }
                    body.addView(Ui.hscroll(a, presets))
                }
                PlotEl.SLOPE -> {
                    defaults("x - y")
                    add("y' = f(x, y)", e1)
                    body.addView(Ui.text(a, "Интегральная кривая через точку (метод Рунге–Кутты):", 12f, Ui.MUTED))
                    pair("x₀", solX, "y₀", solY); add("Параметры", params)
                }
                PlotEl.VFIELD -> {
                    defaults("-y", "x")
                    add("Fx = P(x, y)", e1); add("Fy = Q(x, y)", e2)
                    body.addView(Ui.text(a, "Траектория (линия поля) из точки:", 12f, Ui.MUTED))
                    pair("x₀", solX, "y₀", solY); add("Параметры", params)
                }
            }
            val opts = Ui.wrapRow(a)
            for (c in PLOT_COLORS) {
                val sw = Ui.Swatch(a, c, c == color)
                sw.setOnClickListener { color = c; build() }
                opts.addView(sw, LinearLayout.LayoutParams(dpi(a, 34f), dpi(a, 34f)))
            }
            opts.addView(Ui.chip(a, "Пунктир", dash) { dash = !dash; build() })
            opts.addView(Ui.chip(a, "Подпись", showLabel) { showLabel = !showLabel; build() })
            body.addView(Ui.hscroll(a, opts))
            add("", label)
            (info.parent as? ViewGroup)?.removeView(info)
            body.addView(info)
        }
        if (existing == null) col.addView(Ui.hscroll(a, kinds))
        col.addView(body)
        build()

        val d = AlertDialog.Builder(a).setTitle(KIND_NAMES[kind] ?: "График").setView(scroll(a, col))
            .setPositiveButton(if (existing == null) "Построить" else "Сохранить", null)
            .setNegativeButton("Отмена", null)
            .apply { if (existing != null) setNeutralButton("Удалить") { _, _ -> cv.doc.remove(listOf(existing)); cv.contentChanged() } }
            .create()
        d.show()
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                val p = existing ?: PlotEl()
                val tx = cv.doc.begin()
                if (existing != null) tx.touch(existing)
                val prm = Expr.parseParams(params.text.toString())
                val vars = Expr.baseVars().apply { putAll(prm); put("x", 0.5); put("y", 0.5); put("t", 0.5); put("n", 1.0); put("k", 1.0); put("theta", 0.5); put("phi", 0.5) }
                fun check(s: String, name: String) {
                    val ex = Expr.parse(s)
                    val v = ex.eval(vars)
                    if (v.isNaN()) {
                        // допускаем NaN в отдельной точке, но ловим неизвестные переменные
                        try { Expr.parse(s) } catch (e: Exception) { throw IllegalArgumentException("$name: ${e.message}") }
                    }
                }
                p.kind = kind; p.e1 = e1.text.toString().trim(); p.e2 = e2.text.toString().trim()
                p.params = params.text.toString(); p.label = label.text.toString(); p.dash = dash; p.showLabel = showLabel; p.color = color
                p.t0 = f(t0.text.toString()) ?: Float.NaN; p.t1 = f(t1.text.toString()) ?: Float.NaN
                p.tangentAt = f(tangent.text.toString()) ?: Float.NaN
                p.shadeA = f(shA.text.toString()) ?: Float.NaN; p.shadeB = f(shB.text.toString()) ?: Float.NaN
                p.solX = f(solX.text.toString()) ?: Float.NaN; p.solY = f(solY.text.toString()) ?: Float.NaN
                p.fit = fit
                when (kind) {
                    PlotEl.FX, PlotEl.POLAR, PlotEl.SEQ, PlotEl.SERIES, PlotEl.SLOPE -> check(p.e1, "Выражение")
                    PlotEl.PARAM, PlotEl.VFIELD -> { check(p.e1, "Первое выражение"); check(p.e2, "Второе выражение") }
                    PlotEl.DATA -> {
                        val nums = ArrayList<Float>()
                        for (line in data.text.toString().split('\n')) {
                            val parts = line.trim().replace(',', '.').split(Regex("[\\s;|\\t]+")).filter { it.isNotEmpty() }
                            if (parts.size >= 2) {
                                val x = parts[0].toFloatOrNull(); val y = parts[1].toFloatOrNull()
                                if (x != null && y != null) { nums.add(x); nums.add(y) }
                            }
                        }
                        if (nums.size < 2) throw IllegalArgumentException("Введите хотя бы одну точку «x y»")
                        p.data = nums.toFloatArray()
                    }
                    PlotEl.LINMAP -> p.m = FloatArray(4) { i -> f(mIn[i].text.toString()) ?: throw IllegalArgumentException("Элемент матрицы ${i + 1}: не число") }
                }
                p.invalidateCache()
                if (existing == null) {
                    val ax = chooseAxes(a, p)
                    p.axesId = ax.id
                    cv.doc.els.add(p)
                    tx.added(p)
                }
                tx.commit()
                cv.contentChanged()
                d.dismiss()
                cv.post {
                    if (p.info.isNotEmpty()) a.toast(p.info)
                }
            } catch (e: Exception) {
                info.setTextColor(0xFFDC2626.toInt())
                info.text = "Ошибка: ${e.message}"
            }
        }
    }

    val PLOT_COLORS = listOf(0xFF2563EB.toInt(), 0xFFDC2626.toInt(), 0xFF16A34A.toInt(), 0xFF9333EA.toInt(), 0xFFEA580C.toInt(), 0xFF0891B2.toInt(), 0xFF111827.toInt())

    /** Оси под центром экрана или новые (для данных — подобранные по диапазону). */
    private fun chooseAxes(a: MainActivity, p: PlotEl): AxesEl {
        val cv = a.canvas
        val c = cv.centerWorld()
        val vr = cv.viewRectWorld()
        val existing = cv.doc.axesAt(c.x, c.y) ?: cv.doc.allAxes().filter { RectF_intersects(it.rect(), vr) }.minByOrNull {
            val r = it.rect(); Geo.dist(r.centerX(), r.centerY(), c.x, c.y)
        }
        if (existing != null && p.kind != PlotEl.DATA) return existing
        val ax = AxesEl()
        val s = Paper.snap(1, c.x, c.y) ?: (c.x to c.y)
        ax.ox = s.first; ax.oy = s.second; ax.unit = Paper.GRID
        val targetW = min(cv.width / cv.scale * 0.7f, 900f)
        val targetH = min(cv.height / cv.scale * 0.6f, 700f)
        when (p.kind) {
            PlotEl.DATA -> {
                val xs = (0 until p.data.size / 2).map { p.data[it * 2] }
                val ys = (0 until p.data.size / 2).map { p.data[it * 2 + 1] }
                val x0 = min(0f, xs.minOrNull() ?: 0f); val x1 = max(0f, xs.maxOrNull() ?: 1f)
                val y0 = min(0f, ys.minOrNull() ?: 0f); val y1 = max(0f, ys.maxOrNull() ?: 1f)
                val sx = Geo.niceStep(((x1 - x0) / 8).toDouble().coerceAtLeast(1e-9)).toFloat()
                val sy = Geo.niceStep(((y1 - y0) / 6).toDouble().coerceAtLeast(1e-9)).toFloat()
                ax.xMin = (kotlin.math.floor(x0 / sx) * sx); ax.xMax = (kotlin.math.ceil(x1 / sx) * sx + sx)
                ax.yMin = (kotlin.math.floor(y0 / sy) * sy); ax.yMax = (kotlin.math.ceil(y1 / sy) * sy + sy)
                // единица по x и y одинакова, поэтому масштабируем «шаги»
                val ux = targetW / (ax.xMax - ax.xMin); val uy = targetH / (ax.yMax - ax.yMin)
                ax.unit = min(ux, uy)
                ax.stepX = sx; ax.stepY = sy
                if (ux / uy > 3 || uy / ux > 3) {
                    // сильно разные масштабы — нормируем: подписи «x», «y» сохранены, растягиваем ось y
                    ax.unit = ux
                    val k = uy / ux
                    // переводим данные в масштаб оси: y' = y * k (подпись оси покажет множитель)
                    for (i in 0 until p.data.size / 2) p.data[i * 2 + 1] *= k
                    ax.yMin *= k; ax.yMax *= k; ax.stepY = sy * k
                    ax.yName = "y·${Expr.fmt(k.toDouble(), 3)}"
                }
                ax.ox = c.x - (ax.xMin + ax.xMax) / 2 * ax.unit
                ax.oy = c.y + (ax.yMin + ax.yMax) / 2 * ax.unit
            }
            PlotEl.SEQ, PlotEl.SERIES -> {
                val n1 = if (p.t1.isNaN()) 20f else p.t1
                ax.xMin = -1f; ax.xMax = n1 + 1
                ax.unit = min(targetW / (ax.xMax - ax.xMin), 60f)
                ax.stepX = Geo.niceStep((n1 / 10).toDouble().coerceAtLeast(1.0)).toFloat()
                // диапазон y по значениям
                val ex = Expr.tryParse(p.e1)
                val vars = Expr.baseVars().apply { putAll(Expr.parseParams(p.params)) }
                var lo = 0.0; var hi = 1.0; var s = 0.0
                val n0 = if (p.t0.isNaN()) 1 else p.t0.toInt()
                for (k in n0..n1.toInt()) {
                    vars["n"] = k.toDouble(); vars["k"] = k.toDouble()
                    val v = ex?.eval(vars) ?: 0.0
                    s += v
                    val y = if (p.kind == PlotEl.SERIES) s else v
                    if (!y.isNaN() && !y.isInfinite()) { lo = min(lo, y); hi = max(hi, y) }
                }
                val span = (hi - lo).coerceAtLeast(1e-6)
                val k = (targetH / ax.unit) / span
                ax.yMin = (lo * k).toFloat() - 0.5f; ax.yMax = (hi * k).toFloat() + 0.5f
                if (abs(k - 1) > 0.05) {
                    // масштабируем выражение через параметр
                    p.e1 = "(${p.e1})*${Expr.fmt(k, 6).replace("−", "-")}"
                    ax.yName = "y·${Expr.fmt(k, 3)}"
                    ax.stepY = Geo.niceStep((span * k / 6)).toFloat()
                }
                ax.ox = c.x - (ax.xMin + ax.xMax) / 2 * ax.unit
                ax.oy = c.y + (ax.yMin + ax.yMax) / 2 * ax.unit
            }
            else -> {
                val hx = ((targetW / 2) / ax.unit).roundToInt().coerceIn(3, 14).toFloat()
                val hy = ((targetH / 2) / ax.unit).roundToInt().coerceIn(3, 10).toFloat()
                ax.xMin = -hx; ax.xMax = hx; ax.yMin = -hy; ax.yMax = hy
                if (p.kind == PlotEl.FX && (p.e1.contains("sin") || p.e1.contains("cos") || p.e1.contains("tg") || p.e1.contains("tan"))) ax.piTicks = false
                if (p.kind == PlotEl.POLAR) ax.polar = true
            }
        }
        cv.doc.add(ax)
        return ax
    }

    private fun RectF_intersects(a: android.graphics.RectF, b: android.graphics.RectF) = android.graphics.RectF.intersects(a, b)

    fun axes(a: MainActivity, ax: AxesEl) {
        val cv = a.canvas
        val col = column(a)
        fun n(v: Float) = Expr.fmt(v.toDouble(), 5).replace("−", "-")
        val xmin = Ui.input(a, "", n(ax.xMin), numeric = true); val xmax = Ui.input(a, "", n(ax.xMax), numeric = true)
        val ymin = Ui.input(a, "", n(ax.yMin), numeric = true); val ymax = Ui.input(a, "", n(ax.yMax), numeric = true)
        val sx = Ui.input(a, "авто", if (ax.stepX > 0) n(ax.stepX) else "", numeric = true)
        val sy = Ui.input(a, "авто", if (ax.stepY > 0) n(ax.stepY) else "", numeric = true)
        val unit = Ui.input(a, "", n(ax.unit), numeric = true)
        val xn = Ui.input(a, "", ax.xName); val yn = Ui.input(a, "", ax.yName)
        var grid = ax.grid; var pi = ax.piTicks; var polar = ax.polar; var nums = ax.showNumbers
        fun pair(l1: String, v1: View, l2: String, v2: View) {
            val r = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            r.addView(Ui.labeled(a, l1, v1), LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { setMargins(0, 0, dpi(a, 6f), 0) })
            r.addView(Ui.labeled(a, l2, v2), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            col.addView(r)
        }
        pair("x min", xmin, "x max", xmax); pair("y min", ymin, "y max", ymax)
        pair("шаг по x", sx, "шаг по y", sy); pair("Подпись оси x", xn, "Подпись оси y", yn)
        col.addView(Ui.labeled(a, "Пикселей на единицу", unit))
        col.addView(Ui.switch(a, "Сетка", grid) { grid = it })
        col.addView(Ui.switch(a, "Деления кратны π/2 (тригонометрия)", pi) { pi = it })
        col.addView(Ui.switch(a, "Полярная сетка", polar) { polar = it })
        col.addView(Ui.switch(a, "Числа на осях", nums) { nums = it })
        AlertDialog.Builder(a).setTitle("Оси координат").setView(scroll(a, col))
            .setPositiveButton("OK") { _, _ ->
                cv.doc.modify(listOf(ax)) {
                    f(xmin.text.toString())?.let { ax.xMin = it }; f(xmax.text.toString())?.let { ax.xMax = it }
                    f(ymin.text.toString())?.let { ax.yMin = it }; f(ymax.text.toString())?.let { ax.yMax = it }
                    if (ax.xMax <= ax.xMin) ax.xMax = ax.xMin + 1; if (ax.yMax <= ax.yMin) ax.yMax = ax.yMin + 1
                    ax.stepX = f(sx.text.toString()) ?: 0f; ax.stepY = f(sy.text.toString()) ?: 0f
                    f(unit.text.toString())?.let { if (it > 2) ax.unit = it }
                    ax.xName = xn.text.toString(); ax.yName = yn.text.toString()
                    ax.grid = grid; ax.piTicks = pi; ax.polar = polar; ax.showNumbers = nums
                }
                cv.contentChanged()
            }
            .setNeutralButton("График…") { _, _ -> plot(a, PlotEl.FX) }
            .setNegativeButton("Отмена", null).show()
    }

    // ================================================================== ряды

    fun series(a: MainActivity) {
        val col = column(a)
        val an = Ui.input(a, "aₙ, например 1/n^2, (-1)^n/n, x^n/n!", "1/n^2")
        val n0 = Ui.input(a, "", "1", numeric = true)
        val nN = Ui.input(a, "", "100", numeric = true)
        val res = Ui.text(a, "", 14f, Ui.TEXT)
        val prev = MathPreview(a)
        col.addView(Ui.labeled(a, "Общий член ряда aₙ (переменная n)", an))
        val r = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        r.addView(Ui.labeled(a, "n от", n0), LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { setMargins(0, 0, dpi(a, 6f), 0) })
        r.addView(Ui.labeled(a, "до N", nN), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        col.addView(r)
        col.addView(prev, LinearLayout.LayoutParams(Ui.MATCH, dpi(a, 80f)).apply { setMargins(0, dpi(a, 8f), 0, dpi(a, 8f)) })
        col.addView(res)
        var formula = ""
        fun compute() {
            try {
                val ex = Expr.parse(an.text.toString())
                val a0 = n0.text.toString().trim().toIntOrNull() ?: 1
                val nn = (nN.text.toString().trim().toIntOrNull() ?: 100).coerceIn(a0, a0 + 1_000_000)
                val v = Expr.baseVars()
                var s = 0.0
                var last = 0.0; var prevA = 0.0
                for (k in a0..nn) { v["n"] = k.toDouble(); v["k"] = k.toDouble(); prevA = last; last = ex.eval(v); s += last }
                v["n"] = (nn + 1).toDouble(); val next = ex.eval(v)
                val ratio = abs(next / last)
                val root = abs(last).pow(1.0 / nn)
                val s2 = run { var ss = 0.0; for (k in a0..nn * 2) { v["n"] = k.toDouble(); ss += ex.eval(v) }; ss }
                val sb = StringBuilder()
                sb.append("S_N = ${Expr.fmt(s, 8)}\n")
                sb.append("S_2N = ${Expr.fmt(s2, 8)}   (разница ${Expr.fmt(abs(s2 - s), 3)})\n")
                sb.append("a_N = ${Expr.fmt(last, 6)}\n")
                sb.append("Даламбер |a_{N+1}/a_N| ≈ ${Expr.fmt(ratio, 5)}\n")
                sb.append("Коши ⁿ√|a_N| ≈ ${Expr.fmt(root, 5)}\n")
                val verdict = when {
                    abs(last) > 1e-3 && abs(last) > abs(prevA) * 0.999 -> "aₙ не стремится к 0 → ряд, вероятно, расходится"
                    ratio < 0.98 || root < 0.98 -> "признаки Даламбера/Коши: q < 1 → сходится"
                    abs(s2 - s) < 1e-3 * max(1.0, abs(s)) -> "частичные суммы стабилизируются → вероятно, сходится"
                    else -> "q ≈ 1 — признаки не дают ответа; сравните с рядом Σ1/nᵖ"
                }
                sb.append("Вывод: $verdict")
                res.text = sb.toString()
                formula = "\\sum_{n=$a0}^{\\infty} ${an.text.toString().replace("*", "·")} ≈ ${Expr.fmt(s2, 6)}"
                prev.src = formula; prev.invalidate()
            } catch (e: Exception) {
                res.text = "Ошибка: ${e.message}"
            }
        }
        Ui.onText(an) { compute() }
        compute()
        val btns = Ui.wrapRow(a)
        btns.addView(Ui.button(a, "Формулу на холст", true) { compute(); if (formula.isNotEmpty()) insertFormula(a, formula) })
        btns.addView(Ui.button(a, "График aₙ") { plotSeq(a, PlotEl.SEQ, an.text.toString(), n0.text.toString(), nN.text.toString()) })
        btns.addView(Ui.button(a, "График Sₙ") { plotSeq(a, PlotEl.SERIES, an.text.toString(), n0.text.toString(), nN.text.toString()) })
        col.addView(Ui.hscroll(a, btns))
        AlertDialog.Builder(a).setTitle("Ряд Σ aₙ").setView(scroll(a, col)).setNegativeButton("Закрыть", null).show()
    }

    private fun plotSeq(a: MainActivity, kind: Int, e: String, n0: String, n1: String) {
        val cv = a.canvas
        val p = PlotEl().also {
            it.kind = kind; it.e1 = e; it.t0 = n0.toFloatOrNull() ?: 1f
            it.t1 = min(n1.toFloatOrNull() ?: 20f, 60f); it.color = PLOT_COLORS[if (kind == PlotEl.SEQ) 0 else 1]
        }
        if (Expr.tryParse(e) == null) { a.toast("Ошибка в выражении"); return }
        val ax = chooseAxes(a, p)
        p.axesId = ax.id
        cv.doc.add(p); cv.contentChanged()
        a.toast("Построено на осях (до n = ${p.t1.toInt()})")
    }

    // ================================================================== матрицы

    fun matrix(a: MainActivity) {
        val col = column(a)
        var rows = 3; var cols = 3
        val grid = GridLayout(a)
        val cells = ArrayList<EditText>()
        val res = Ui.text(a, "", 14f, Ui.TEXT)
        val prev = MathPreview(a)
        var lastLatex = ""
        var delim = "pmatrix"
        fun buildGrid(keep: List<String> = emptyList()) {
            grid.removeAllViews(); cells.clear()
            grid.columnCount = cols
            for (i in 0 until rows * cols) {
                val e = Ui.input(a, "0", keep.getOrElse(i) { if (i / cols == i % cols) "1" else "0" }, numeric = true)
                e.gravity = Gravity.CENTER
                cells.add(e)
                grid.addView(e, GridLayout.LayoutParams().apply { width = dpi(a, 64f); setMargins(dpi(a, 2f), dpi(a, 2f), dpi(a, 2f), dpi(a, 2f)) })
            }
        }
        fun values(): Array<DoubleArray> = Array(rows) { r -> DoubleArray(cols) { c -> f(cells[r * cols + c].text.toString())?.toDouble() ?: Double.NaN } }
        fun latexOf(m: Array<DoubleArray>, d: String = delim): String =
            "\\$d{" + m.joinToString(" \\\\ ") { r -> r.joinToString(" & ") { Lin.frac(it) } } + "}"
        fun show(label: String, latex: String) {
            lastLatex = latex
            prev.src = latex; prev.invalidate()
            res.text = label
        }
        val sizeRow = Ui.wrapRow(a)
        fun buildSize() {
            sizeRow.removeAllViews()
            sizeRow.addView(Ui.text(a, "Строк: ", 13f, Ui.MUTED))
            for (n in 1..5) sizeRow.addView(Ui.chip(a, "$n", n == rows) { val k = cells.map { it.text.toString() }; rows = n; buildSize(); buildGrid(k) })
            sizeRow.addView(Ui.text(a, "  Столбцов: ", 13f, Ui.MUTED))
            for (n in 1..6) sizeRow.addView(Ui.chip(a, "$n", n == cols) { val k = cells.map { it.text.toString() }; cols = n; buildSize(); buildGrid(k) })
        }
        buildSize(); buildGrid()
        col.addView(Ui.hscroll(a, sizeRow))
        col.addView(Ui.hscroll(a, grid))
        val dl = Ui.wrapRow(a)
        fun buildDl() { dl.removeAllViews(); for ((k, n) in listOf("pmatrix" to "( )", "bmatrix" to "[ ]", "vmatrix" to "| |")) dl.addView(Ui.chip(a, n, k == delim) { delim = k; buildDl(); show("", latexOf(values())) }) }
        buildDl()
        col.addView(dl)
        val ops = Ui.wrapRow(a)
        ops.addView(Ui.chip(a, "A", false) { show("Матрица A", "A = " + latexOf(values())) })
        ops.addView(Ui.chip(a, "det A", false) {
            val m = values()
            if (rows != cols) { res.text = "Определитель только для квадратной матрицы"; return@chip }
            val d = Lin.det(m)
            show("det A = ${Lin.frac(d)}", "\\det " + latexOf(m, "vmatrix") + " = " + Lin.frac(d))
        })
        ops.addView(Ui.chip(a, "A⁻¹", false) {
            val m = values()
            if (rows != cols) { res.text = "Обратная — только для квадратной матрицы"; return@chip }
            val inv = Lin.inverse(m)
            if (inv == null) res.text = "det A = 0 — обратной матрицы нет" else show("Обратная матрица", "A^{-1} = " + latexOf(inv))
        })
        ops.addView(Ui.chip(a, "Aᵀ", false) { val m = values(); val t = Array(cols) { c -> DoubleArray(rows) { r -> m[r][c] } }; show("Транспонированная", "A^T = " + latexOf(t)) })
        ops.addView(Ui.chip(a, "rank", false) { val m = values(); show("Ранг = ${Lin.rank(m)}", "\\text{rg} A = ${Lin.rank(m)}") })
        ops.addView(Ui.chip(a, "Гаусс (СЛАУ)", false) {
            val m = values()
            val r = Lin.rref(m)
            val sol = if (cols >= 2) Lin.solveText(r, cols - 1) else ""
            show("Ступенчатый вид (последний столбец — свободные члены)\n$sol", latexOf(m) + " \\sim " + latexOf(r))
        })
        ops.addView(Ui.chip(a, "A²", false) {
            val m = values()
            if (rows != cols) { res.text = "Только для квадратной матрицы"; return@chip }
            show("A²", "A^2 = " + latexOf(Lin.mul(m, m)))
        })
        ops.addView(Ui.chip(a, "λ (2×2)", false) {
            val m = values()
            if (rows != 2 || cols != 2) { res.text = "Собственные числа считаются для 2×2"; return@chip }
            val tr = m[0][0] + m[1][1]; val det = Lin.det(m); val disc = tr * tr - 4 * det
            val s = if (disc >= 0) "λ_1 = ${Lin.frac((tr + kotlin.math.sqrt(disc)) / 2)},\\; λ_2 = ${Lin.frac((tr - kotlin.math.sqrt(disc)) / 2)}"
            else "λ = ${Lin.frac(tr / 2)} ± ${Lin.frac(kotlin.math.sqrt(-disc) / 2)}i"
            show("Характеристическое уравнение: λ² − ${Lin.frac(tr)}λ + ${Lin.frac(det)} = 0", s)
        })
        col.addView(Ui.hscroll(a, ops))
        col.addView(prev, LinearLayout.LayoutParams(Ui.MATCH, dpi(a, 120f)).apply { setMargins(0, dpi(a, 8f), 0, dpi(a, 4f)) })
        col.addView(res)
        show("", "A = " + latexOf(values()))
        val btns = Ui.wrapRow(a)
        btns.addView(Ui.button(a, "На холст", true) { if (lastLatex.isNotEmpty()) insertFormula(a, lastLatex, 22f) })
        btns.addView(Ui.button(a, "Как отображение 2×2") {
            val m = values()
            if (rows < 2 || cols < 2) { a.toast("Нужна матрица 2×2"); return@button }
            val p = PlotEl().also { it.kind = PlotEl.LINMAP; it.m = floatArrayOf(m[0][0].toFloat(), m[0][1].toFloat(), m[1][0].toFloat(), m[1][1].toFloat()); it.color = 0xFF2563EB.toInt() }
            val ax = chooseAxes(a, p); p.axesId = ax.id
            a.canvas.doc.add(p); a.canvas.contentChanged()
        })
        col.addView(Ui.hscroll(a, btns))
        AlertDialog.Builder(a).setTitle("Матрица · СЛАУ · определитель").setView(scroll(a, col)).setNegativeButton("Закрыть", null).show()
    }

    // ================================================================== таблицы

    fun table(a: MainActivity, existing: TableEl?) {
        val cv = a.canvas
        val col = column(a)
        val input = Ui.input(a, "Ячейки через | , строки — с новой строки\nt, с | x, м\n0 | 0\n1 | 4.9", existing?.toCsv() ?: "x | y\n0 | 0\n1 | 1\n2 | 4", multi = true)
        var header = existing?.header ?: true
        col.addView(input)
        col.addView(Ui.switch(a, "Первая строка — заголовок", header) { header = it })
        col.addView(Ui.text(a, "В ячейках можно писать формулы: x^2, \\frac{1}{2}, \\vec{F}.", 12f, Ui.MUTED))
        val b = AlertDialog.Builder(a).setTitle(if (existing == null) "Таблица" else "Редактировать таблицу").setView(scroll(a, col))
            .setPositiveButton("OK") { _, _ ->
                val cells = TableEl.parseCsv(input.text.toString())
                if (cells.isEmpty()) return@setPositiveButton
                if (existing != null) {
                    cv.doc.modify(listOf(existing)) { existing.cells = cells; existing.header = header; existing.invalidateCache() }
                    cv.contentChanged()
                } else {
                    val c = cv.centerWorld()
                    val t = TableEl().also { it.cells = cells; it.header = header; it.x = c.x - 100; it.y = c.y - 60; it.color = cv.colorOf(Tool.TEXT) }
                    cv.addAtCenter(t); cv.select(listOf(t))
                }
            }
            .setNegativeButton("Отмена", null)
        if (existing != null) b.setNeutralButton("Построить график") { _, _ ->
            val rows = existing.cells.drop(if (existing.header) 1 else 0)
            val txt = rows.joinToString("\n") { r -> r.take(2).joinToString(" ") { it.replace(',', '.') } }
            val p = PlotEl().also { it.kind = PlotEl.DATA; it.fit = 1 }
            plotFromData(a, txt, p, existing)
        }
        b.show()
    }

    private fun plotFromData(a: MainActivity, txt: String, p: PlotEl, source: El?) {
        val nums = ArrayList<Float>()
        for (line in txt.split('\n')) {
            val parts = line.trim().split(Regex("[\\s;|]+")).filter { it.isNotEmpty() }
            if (parts.size >= 2) { val x = parts[0].toFloatOrNull(); val y = parts[1].toFloatOrNull(); if (x != null && y != null) { nums.add(x); nums.add(y) } }
        }
        if (nums.size < 4) { a.toast("Нужны хотя бы две числовые строки"); return }
        p.data = nums.toFloatArray()
        val ax = chooseAxes(a, p)
        p.axesId = ax.id
        val cv = a.canvas
        cv.doc.add(p)
        if (source != null) cv.doc.add(LinkEl().also { it.from = source.id; it.to = ax.id; it.label = "график"; it.color = Ui.MUTED; it.width = 1.6f })
        cv.contentChanged()
    }

    fun valueTable(a: MainActivity) {
        val col = column(a)
        val ex = Ui.input(a, "f(x)", "x^2")
        val from = Ui.input(a, "", "-3", numeric = true); val to = Ui.input(a, "", "3", numeric = true); val step = Ui.input(a, "", "1", numeric = true)
        col.addView(Ui.labeled(a, "f(x) =", ex))
        val r = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        for ((l, v) in listOf("от" to from, "до" to to, "шаг" to step)) r.addView(Ui.labeled(a, l, v), LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { setMargins(0, 0, dpi(a, 4f), 0) })
        col.addView(r)
        var horizontal = true
        col.addView(Ui.switch(a, "Горизонтальная таблица", horizontal) { horizontal = it })
        AlertDialog.Builder(a).setTitle("Таблица значений").setView(col)
            .setPositiveButton("Вставить") { _, _ ->
                val e = Expr.tryParse(ex.text.toString()) ?: run { a.toast("Ошибка в выражении"); return@setPositiveButton }
                val x0 = f(from.text.toString()) ?: -3f; val x1 = f(to.text.toString()) ?: 3f
                val st = (f(step.text.toString()) ?: 1f).let { if (it <= 0) 1f else it }
                val xs = ArrayList<String>(); val ys = ArrayList<String>()
                val v = Expr.baseVars()
                var x = x0.toDouble(); var guard = 0
                while (x <= x1 + 1e-9 && guard < 60) { v["x"] = x; xs.add(Expr.fmt(x, 4)); ys.add(Expr.fmt(e.eval(v), 4)); x += st; guard++ }
                val cells: MutableList<MutableList<String>> = if (horizontal) mutableListOf((listOf("x") + xs).toMutableList(), (listOf("f(x)") + ys).toMutableList())
                else (listOf(mutableListOf("x", "f(x)")) + xs.indices.map { mutableListOf(xs[it], ys[it]) }).toMutableList()
                val c = a.canvas.centerWorld()
                val t = TableEl().also { it.cells = cells; it.header = !horizontal; it.x = c.x - 200; it.y = c.y - 40; it.color = a.canvas.colorOf(Tool.TEXT) }
                a.canvas.addAtCenter(t); a.canvas.select(listOf(t))
            }
            .setNegativeButton("Отмена", null).show()
    }

    // ================================================================== справочники

    val CONSTANTS = listOf(
        "g = 9{,}81\\ м/с^2" to "Ускорение свободного падения",
        "G = 6{,}674·10^{-11}\\ Н·м^2/кг^2" to "Гравитационная постоянная",
        "c = 2{,}998·10^8\\ м/с" to "Скорость света",
        "h = 6{,}626·10^{-34}\\ Дж·с" to "Постоянная Планка",
        "ℏ = 1{,}055·10^{-34}\\ Дж·с" to "Приведённая постоянная Планка",
        "k_B = 1{,}381·10^{-23}\\ Дж/К" to "Постоянная Больцмана",
        "N_A = 6{,}022·10^{23}\\ моль^{-1}" to "Число Авогадро",
        "R = 8{,}314\\ Дж/(моль·К)" to "Универсальная газовая постоянная",
        "e = 1{,}602·10^{-19}\\ Кл" to "Элементарный заряд",
        "m_e = 9{,}109·10^{-31}\\ кг" to "Масса электрона",
        "m_p = 1{,}673·10^{-27}\\ кг" to "Масса протона",
        "ε_0 = 8{,}854·10^{-12}\\ Ф/м" to "Электрическая постоянная",
        "μ_0 = 4π·10^{-7}\\ Гн/м" to "Магнитная постоянная",
        "k = \\frac{1}{4πε_0} = 8{,}988·10^9\\ Н·м^2/Кл^2" to "Коэффициент в законе Кулона",
        "σ = 5{,}670·10^{-8}\\ Вт/(м^2·К^4)" to "Постоянная Стефана–Больцмана",
        "p_0 = 1{,}013·10^5\\ Па" to "Нормальное атмосферное давление",
        "1\\ а.е.м. = 1{,}661·10^{-27}\\ кг" to "Атомная единица массы",
        "1\\ эВ = 1{,}602·10^{-19}\\ Дж" to "Электронвольт",
        "π = 3{,}14159…" to "Число π",
        "e = 2{,}71828…" to "Число e",
    )

    fun constants(a: MainActivity) {
        val col = column(a, 12f)
        var dlg: Dialog? = null
        for ((f, name) in CONSTANTS) {
            val item = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                background = Ui.ripple(null, Ui.dp(a, 10f))
                setPadding(dpi(a, 8f), dpi(a, 6f), dpi(a, 8f), dpi(a, 6f))
                isClickable = true
                setOnClickListener { dlg?.dismiss(); insertFormula(a, f) }
            }
            item.addView(Ui.text(a, name, 12f, Ui.MUTED))
            item.addView(MathPreview(a).apply { src = f; size = 18f }, LinearLayout.LayoutParams(Ui.MATCH, dpi(a, 44f)))
            col.addView(item)
        }
        dlg = AlertDialog.Builder(a).setTitle("Физические константы — нажмите, чтобы вставить").setView(scroll(a, col)).setNegativeButton("Закрыть", null).show()
    }

    val FORMULAS = listOf(
        "Производные и интегралы" to listOf(
            "(x^n)' = n x^{n-1}", "(\\sin x)' = \\cos x", "(\\cos x)' = -\\sin x", "(e^x)' = e^x", "(\\ln x)' = \\frac{1}{x}",
            "(uv)' = u'v + uv'", "\\left(\\frac{u}{v}\\right)' = \\frac{u'v - uv'}{v^2}", "(f(g(x)))' = f'(g(x))·g'(x)",
            "\\int x^n dx = \\frac{x^{n+1}}{n+1} + C", "\\int \\frac{dx}{x} = \\ln|x| + C", "\\int_a^b f(x)dx = F(b) - F(a)",
            "\\int u\\,dv = uv - \\int v\\,du", "\\lim_{x\\to 0} \\frac{\\sin x}{x} = 1", "\\lim_{n\\to\\infty} \\left(1 + \\frac{1}{n}\\right)^n = e"
        ),
        "Ряды" to listOf(
            "e^x = \\sum_{n=0}^{\\infty} \\frac{x^n}{n!}", "\\sin x = \\sum_{n=0}^{\\infty} \\frac{(-1)^n x^{2n+1}}{(2n+1)!}",
            "\\cos x = \\sum_{n=0}^{\\infty} \\frac{(-1)^n x^{2n}}{(2n)!}", "\\ln(1+x) = \\sum_{n=1}^{\\infty} \\frac{(-1)^{n+1} x^n}{n}",
            "\\frac{1}{1-x} = \\sum_{n=0}^{\\infty} x^n,\\ |x| < 1", "f(x) = \\sum_{n=0}^{\\infty} \\frac{f^{(n)}(a)}{n!}(x-a)^n",
            "\\sum_{n=1}^{\\infty} \\frac{1}{n^2} = \\frac{π^2}{6}", "\\sum_{n=1}^{\\infty} \\frac{1}{n^p}\\ \\text{сходится} \\iff p > 1",
            "\\lim_{n\\to\\infty} \\left|\\frac{a_{n+1}}{a_n}\\right| = q < 1 ⇒ \\text{сходится}", "\\lim_{n\\to\\infty} \\sqrt[n]{|a_n|} = q < 1 ⇒ \\text{сходится}",
            "S_n = \\frac{a_1(1 - q^n)}{1 - q}", "S_n = \\frac{(a_1 + a_n) n}{2}", "R = \\lim_{n\\to\\infty} \\left|\\frac{a_n}{a_{n+1}}\\right|"
        ),
        "Алгебра" to listOf(
            "(a ± b)^2 = a^2 ± 2ab + b^2", "a^2 - b^2 = (a - b)(a + b)", "x_{1,2} = \\frac{-b ± \\sqrt{b^2 - 4ac}}{2a}",
            "x_1 + x_2 = -\\frac{b}{a},\\ x_1 x_2 = \\frac{c}{a}", "(a+b)^n = \\sum_{k=0}^{n} \\binom{n}{k} a^{n-k} b^k", "\\log_a(xy) = \\log_a x + \\log_a y",
            "\\sin^2 x + \\cos^2 x = 1", "\\sin 2x = 2\\sin x\\cos x", "\\cos 2x = \\cos^2 x - \\sin^2 x", "e^{iφ} = \\cos φ + i\\sin φ",
            "z = r(\\cos φ + i\\sin φ)", "z^n = r^n(\\cos nφ + i\\sin nφ)"
        ),
        "Линейная алгебра и аналитическая геометрия" to listOf(
            "\\vec{a}·\\vec{b} = |\\vec{a}||\\vec{b}|\\cos φ = a_x b_x + a_y b_y + a_z b_z", "\\cos φ = \\frac{\\vec{a}·\\vec{b}}{|\\vec{a}||\\vec{b}|}",
            "\\vec{a}×\\vec{b} = \\vmatrix{\\vec{i} & \\vec{j} & \\vec{k} \\\\ a_x & a_y & a_z \\\\ b_x & b_y & b_z}", "(\\vec{a}, \\vec{b}, \\vec{c}) = \\vmatrix{a_x & a_y & a_z \\\\ b_x & b_y & b_z \\\\ c_x & c_y & c_z}",
            "\\det\\pmatrix{a & b \\\\ c & d} = ad - bc", "A^{-1} = \\frac{1}{\\det A}\\pmatrix{d & -b \\\\ -c & a}", "A\\vec{x} = λ\\vec{x},\\quad \\det(A - λE) = 0",
            "\\text{rg} A = \\text{rg}(A|b) \\iff \\text{система совместна}", "x_i = \\frac{Δ_i}{Δ}", "|\\vec{AB}| = \\sqrt{(x_2-x_1)^2 + (y_2-y_1)^2}",
            "Ax + By + C = 0", "y = kx + b", "\\frac{x - x_0}{l} = \\frac{y - y_0}{m} = \\frac{z - z_0}{n}", "d = \\frac{|Ax_0 + By_0 + C|}{\\sqrt{A^2 + B^2}}",
            "A(x - x_0) + B(y - y_0) + C(z - z_0) = 0", "(x - a)^2 + (y - b)^2 = R^2", "\\frac{x^2}{a^2} + \\frac{y^2}{b^2} = 1", "\\frac{x^2}{a^2} - \\frac{y^2}{b^2} = 1", "y^2 = 2px"
        ),
        "Физика" to listOf(
            "\\vec{v} = \\frac{d\\vec{r}}{dt},\\quad \\vec{a} = \\frac{d\\vec{v}}{dt}", "x = x_0 + v_0 t + \\frac{at^2}{2}", "v = v_0 + at", "v^2 - v_0^2 = 2as",
            "m\\vec{a} = \\sum \\vec{F}", "F_{тр} = μN", "F_{упр} = kx", "F = G\\frac{m_1 m_2}{r^2}", "a_ц = \\frac{v^2}{R} = ω^2 R",
            "\\vec{p} = m\\vec{v}", "E_k = \\frac{mv^2}{2}", "E_p = mgh", "A = Fs\\cos α", "N = \\frac{A}{t}",
            "T = 2π\\sqrt{\\frac{l}{g}}", "T = 2π\\sqrt{\\frac{m}{k}}", "x(t) = A\\cos(ωt + φ_0)", "M = Fl,\\quad Iε = M",
            "pV = νRT", "Q = cmΔt", "ΔU = Q - A", "η = \\frac{T_1 - T_2}{T_1}",
            "F = k\\frac{|q_1 q_2|}{r^2}", "\\vec{E} = \\frac{\\vec{F}}{q}", "C = \\frac{q}{U} = \\frac{εε_0 S}{d}", "W = \\frac{CU^2}{2}",
            "I = \\frac{U}{R}", "I = \\frac{ε}{R + r}", "R = ρ\\frac{l}{S}", "P = UI = I^2 R", "Q = I^2 R t",
            "F_A = BIl\\sin α", "F_Л = qvB\\sin α", "Φ = BS\\cos α", "ε_i = -\\frac{dΦ}{dt}", "T = 2π\\sqrt{LC}",
            "\\frac{1}{F} = \\frac{1}{d} + \\frac{1}{f}", "n_1\\sin α = n_2\\sin β", "d\\sin φ = kλ",
            "E = hν", "hν = A_{вых} + \\frac{mv^2}{2}", "E = mc^2", "λ = \\frac{h}{p}", "N = N_0·2^{-t/T}"
        ),
    )

    fun formulas(a: MainActivity, startCat: Int = 0) {
        val col = column(a, 12f)
        var dlg: Dialog? = null
        val tabs = Ui.wrapRow(a)
        val list = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        var cat = startCat.coerceIn(0, FORMULAS.size - 1)
        fun build() {
            tabs.removeAllViews()
            FORMULAS.forEachIndexed { i, (n, _) -> tabs.addView(Ui.chip(a, n, i == cat) { cat = i; build() }) }
            list.removeAllViews()
            for (fm in FORMULAS[cat].second) {
                val v = MathPreview(a).apply { src = fm; size = 18f }
                v.background = Ui.ripple(null, Ui.dp(a, 8f))
                v.isClickable = true
                v.setOnClickListener { dlg?.dismiss(); insertFormula(a, fm) }
                list.addView(v, LinearLayout.LayoutParams(Ui.MATCH, dpi(a, 58f)).apply { setMargins(0, dpi(a, 3f), 0, dpi(a, 3f)) })
            }
        }
        build()
        col.addView(Ui.hscroll(a, tabs))
        col.addView(list)
        dlg = AlertDialog.Builder(a).setTitle("Справочник формул — нажмите, чтобы вставить").setView(scroll(a, col)).setNegativeButton("Закрыть", null).show()
    }

    // ================================================================== шаблоны

    class StampPreview(ctx: Context, val kind: String) : View(ctx) {
        override fun onDraw(c: Canvas) {
            val d = Stamps.def(kind)
            val k = min(width * 0.8f / d.w, height * 0.8f / d.h)
            c.save()
            c.translate(width / 2f, height / 2f)
            c.scale(k, k)
            Stamps.draw(kind, CanvasGfx(c, k), d.w, d.h, Ui.TEXT, 2.4f, Color.WHITE)
            c.restore()
        }
    }

    fun stampPicker(a: MainActivity) {
        val col = column(a, 12f)
        var dlg: Dialog? = null
        for (cat in Stamps.ALL.map { it.cat }.distinct()) {
            col.addView(Ui.header(a, cat))
            val grid = GridLayout(a).apply { columnCount = 4 }
            for (d in Stamps.ALL.filter { it.cat == cat }) {
                val tile = LinearLayout(a).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = Ui.ripple(Ui.round(if (d.kind == a.canvas.stampKind) Ui.ACCENT_SOFT else Ui.CARD, Ui.dp(a, 12f)), Ui.dp(a, 12f))
                    isClickable = true
                    setOnClickListener {
                        a.canvas.stampKind = d.kind
                        a.canvas.tool = Tool.STAMP
                        a.toast("${d.title}: коснитесь холста или растяните рамку")
                        dlg?.dismiss()
                    }
                }
                tile.addView(StampPreview(a, d.kind), LinearLayout.LayoutParams(dpi(a, 70f), dpi(a, 56f)))
                tile.addView(Ui.text(a, d.title, 11f, Ui.TEXT).apply { gravity = Gravity.CENTER; maxLines = 2 })
                grid.addView(tile, GridLayout.LayoutParams().apply { width = dpi(a, 88f); height = dpi(a, 96f); setMargins(dpi(a, 3f), dpi(a, 3f), dpi(a, 3f), dpi(a, 3f)) })
            }
            col.addView(grid)
        }
        dlg = AlertDialog.Builder(a).setTitle("Шаблоны").setView(scroll(a, col)).setNegativeButton("Закрыть", null).show()
    }

    // ================================================================== цвет и толщина

    private val PICKER = listOf(
        0xFF111827, 0xFF374151, 0xFF6B7280, 0xFFFFFFFF, 0xFF7F1D1D, 0xFFDC2626, 0xFFF87171, 0xFFEA580C, 0xFFF59E0B, 0xFFFDE047,
        0xFF65A30D, 0xFF16A34A, 0xFF86EFAC, 0xFF0D9488, 0xFF0891B2, 0xFF38BDF8, 0xFF1D4ED8, 0xFF2563EB, 0xFF93C5FD, 0xFF4F46E5,
        0xFF7C3AED, 0xFFC4B5FD, 0xFFDB2777, 0xFFF9A8D4, 0xFF92400E, 0xFFFDBA74
    ).map { it.toInt() }

    fun colorPicker(a: MainActivity, cur: Int, cb: (Int) -> Unit) {
        val col = column(a)
        var dlg: Dialog? = null
        val grid = GridLayout(a).apply { columnCount = 7 }
        for (c in PICKER) {
            val sw = Ui.Swatch(a, c, c == cur)
            sw.setOnClickListener { cb(c); dlg?.dismiss() }
            grid.addView(sw, GridLayout.LayoutParams().apply { width = dpi(a, 42f); height = dpi(a, 42f) })
        }
        col.addView(grid)
        val hsv = FloatArray(3); Color.colorToHSV(cur, hsv)
        val preview = View(a).apply { background = Ui.round(cur, Ui.dp(a, 10f)) }
        var c2 = cur
        fun upd() { c2 = Color.HSVToColor(hsv); preview.background = Ui.round(c2, Ui.dp(a, 10f)) }
        col.addView(Ui.header(a, "Свой цвет"))
        col.addView(preview, LinearLayout.LayoutParams(Ui.MATCH, dpi(a, 36f)))
        col.addView(Ui.labeled(a, "Оттенок", Ui.seek(a, 360, hsv[0].toInt()) { hsv[0] = it.toFloat(); upd() }))
        col.addView(Ui.labeled(a, "Насыщенность", Ui.seek(a, 100, (hsv[1] * 100).toInt()) { hsv[1] = it / 100f; upd() }))
        col.addView(Ui.labeled(a, "Яркость", Ui.seek(a, 100, (hsv[2] * 100).toInt()) { hsv[2] = it / 100f; upd() }))
        val hex = Ui.input(a, "#RRGGBB", String.format("#%06X", cur and 0xFFFFFF))
        col.addView(hex)
        dlg = AlertDialog.Builder(a).setTitle("Цвет").setView(scroll(a, col))
            .setPositiveButton("Выбрать") { _, _ ->
                val h = hex.text.toString().trim()
                val parsed = try { if (h != String.format("#%06X", cur and 0xFFFFFF)) Color.parseColor(if (h.startsWith("#")) h else "#$h") else null } catch (e: Exception) { null }
                cb(parsed ?: c2)
            }
            .setNegativeButton("Отмена", null).show()
    }

    fun widthPicker(a: MainActivity, t: Tool) {
        val cv = a.canvas
        val col = column(a)
        var w = cv.widthOf(t)
        val label = Ui.text(a, "Толщина: ${Expr.fmt(w.toDouble(), 1)}", 15f)
        val dot = Ui.WidthDot(a, w * 2, false, cv.colorOf(t))
        col.addView(label)
        col.addView(dot, LinearLayout.LayoutParams(Ui.MATCH, dpi(a, 60f)))
        col.addView(Ui.seek(a, 200, (w * 4).toInt()) { w = max(0.5f, it / 4f); label.text = "Толщина: ${Expr.fmt(w.toDouble(), 1)}"; dot.w = w * 2; dot.invalidate() })
        if (t == Tool.PEN || t == Tool.PENCIL || t == Tool.MARKER) {
            col.addView(Ui.header(a, "Стабилизация линии"))
            col.addView(Ui.seek(a, 4, a.prefs.smoothing) { a.prefs.smoothing = it })
        }
        AlertDialog.Builder(a).setTitle(t.title).setView(col).setPositiveButton("OK") { _, _ -> cv.setWidth(t, w) }.setNegativeButton("Отмена", null).show()
    }

    // ================================================================== бумага

    fun paper(a: MainActivity) {
        val cv = a.canvas
        val col = column(a)
        col.addView(Ui.header(a, "Разлиновка"))
        val types = Ui.wrapRow(a)
        fun bt() { types.removeAllViews(); Paper.NAMES.forEachIndexed { i, n -> types.addView(Ui.chip(a, n, i == cv.doc.paper) { cv.doc.paper = i; cv.doc.dirty = true; bt(); cv.invalidateCache() }) } }
        bt()
        col.addView(Ui.hscroll(a, types))
        col.addView(Ui.header(a, "Цвет бумаги"))
        val colors = Ui.wrapRow(a)
        fun bc() {
            colors.removeAllViews()
            for ((c, n) in Paper.COLORS) colors.addView(Ui.chip(a, n, c == cv.doc.paperColor) {
                val wasDark = Paper.isDark(cv.doc.paperColor)
                cv.doc.paperColor = c; cv.doc.dirty = true; bc(); cv.invalidateCache()
                if (Paper.isDark(c) != wasDark) {
                    val ink = if (Paper.isDark(c)) Color.WHITE else 0xFF111827.toInt()
                    for (t in listOf(Tool.PEN, Tool.PENCIL, Tool.LINE, Tool.CIRCLE, Tool.SHAPE, Tool.TEXT, Tool.POINT, Tool.ANGLE, Tool.LINK, Tool.STAMP, Tool.AXES)) cv.setColor(t, ink)
                }
                cv.host?.onStateChanged()
            })
        }
        bc()
        col.addView(Ui.hscroll(a, colors))
        AlertDialog.Builder(a).setTitle("Бумага").setView(col).setPositiveButton("Готово", null).show()
    }

    // ================================================================== экспорт

    fun export(a: MainActivity, selOnly: Boolean = false) {
        val cv = a.canvas
        val col = column(a)
        var format = "png"
        var onlySel = selOnly && cv.selection.isNotEmpty()
        var paper = true
        var transparent = false
        var scale = 2f
        val formats = Ui.wrapRow(a)
        val opts = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        fun build() {
            formats.removeAllViews()
            for ((k, n) in listOf("png" to "PNG (картинка)", "svg" to "SVG (вектор)", "pdf" to "PDF", "json" to "Проект (.json)")) formats.addView(Ui.chip(a, n, k == format) { format = k; build() })
            opts.removeAllViews()
            if (format != "json") {
                if (cv.selection.isNotEmpty()) opts.addView(Ui.switch(a, "Только выделенное (${cv.selection.size})", onlySel) { onlySel = it })
                opts.addView(Ui.switch(a, "С разлиновкой бумаги", paper) { paper = it })
                if (format != "pdf") opts.addView(Ui.switch(a, "Прозрачный фон", transparent) { transparent = it })
                if (format == "png") {
                    val sc = Ui.wrapRow(a)
                    fun bs() { sc.removeAllViews(); for (s in listOf(1f, 2f, 3f, 4f)) sc.addView(Ui.chip(a, "×${s.toInt()}", s == scale) { scale = s; bs() }) }
                    bs()
                    opts.addView(Ui.labeled(a, "Качество (масштаб)", sc))
                }
            } else opts.addView(Ui.text(a, "Полная копия холста для переноса на другое устройство или резервной копии. Открывается через «Мои холсты → Импорт».", 13f, Ui.MUTED))
        }
        build()
        col.addView(Ui.hscroll(a, formats))
        col.addView(opts)
        AlertDialog.Builder(a).setTitle("Экспорт").setView(col)
            .setPositiveButton("Сохранить в файл…") { _, _ -> a.exportToFile(MainActivity.PendingExport(format, onlySel, paper, transparent, scale)) }
            .setNeutralButton("Поделиться") { _, _ -> a.share(MainActivity.PendingExport(format, onlySel, paper, transparent, scale)) }
            .setNegativeButton("Отмена", null).show()
    }

    fun quickPng(a: MainActivity) {
        a.exportToFile(MainActivity.PendingExport("png", a.canvas.selection.isNotEmpty(), true, false, 2f))
    }

    // ================================================================== настройки

    private var keyLabel: TextView? = null

    fun onKeyLearned(a: MainActivity) {
        keyLabel?.text = keyText(a)
    }

    private fun keyText(a: MainActivity) = if (a.prefs.penKey == 0) "Клавиша Bluetooth-пера не назначена (кнопки активных стилусов работают сразу)"
    else "Назначена клавиша: ${android.view.KeyEvent.keyCodeToString(a.prefs.penKey)}"

    fun inputMode(a: MainActivity) {
        val p = a.prefs
        val col = column(a)
        col.addView(Ui.text(a, "Как отличать перо от руки", 13f, Ui.MUTED))
        val rg = RadioGroup(a)
        Prefs.INPUT_MODES.forEachIndexed { i, s ->
            rg.addView(RadioButton(a).apply { text = s; id = 100 + i; isChecked = p.inputMode == i; textSize = 15f; setPadding(0, dpi(a, 6f), 0, dpi(a, 6f)) })
        }
        rg.setOnCheckedChangeListener { _, id -> p.inputMode = id - 100; a.canvas.host?.onStateChanged() }
        col.addView(rg)
        val info = Ui.text(a, "Последнее касание пальцем/ручкой: ${Expr.fmt(a.canvas.lastTouchMajor.toDouble(), 1)} px (порог ${p.palmSize.toInt()} px)", 13f, Ui.MUTED)
        col.addView(Ui.header(a, "Порог для пассивных ручек"))
        col.addView(info)
        col.addView(Ui.seek(a, 120, p.palmSize.toInt()) { p.palmSize = max(4f, it.toFloat()); info.text = "Порог: ${p.palmSize.toInt()} px — касания меньше считаются пером" })
        col.addView(Ui.switch(a, "Один палец двигает холст", p.fingerPan) { p.fingerPan = it })
        col.addView(Ui.switch(a, "Касание 2 пальцами — отменить, 3 — повторить", p.gestureUndo) { p.gestureUndo = it })
        col.addView(Ui.button(a, "Сбросить автоопределение стилуса") { p.stylusSeen = false; a.canvas.host?.onStateChanged(); a.toast("Сброшено") })
        AlertDialog.Builder(a).setTitle("Перо и рука").setView(scroll(a, col)).setPositiveButton("Готово") { _, _ -> a.canvas.host?.onStateChanged() }.show()
    }

    fun settings(a: MainActivity) {
        val p = a.prefs
        val col = column(a)

        col.addView(Ui.header(a, "ПЕРО И ЗАЩИТА ОТ КАСАНИЯ ЛАДОНЬЮ"))
        col.addView(Ui.button(a, "Режим ввода: " + Prefs.INPUT_MODES[p.inputMode].substringBefore(" (").substringBefore(":")) { inputMode(a) })
        col.addView(Ui.switch(a, "Сила нажатия (толщина линии)", p.pressure) { p.pressure = it })
        col.addView(Ui.switch(a, "Курсор при наведении пера", p.hoverCursor) { p.hoverCursor = it })
        col.addView(Ui.labeled(a, "Стабилизация линии", Ui.seek(a, 4, p.smoothing) { p.smoothing = it }))

        col.addView(Ui.header(a, "КНОПКА НА РУЧКЕ"))
        col.addView(Ui.text(a, "Работает с кнопками активных стилусов (USI, Wacom, S Pen и др.) и с клавишами Bluetooth-перьев.", 12f, Ui.MUTED))
        val names = Prefs.ACTIONS.map { it.second }
        fun spinner(label: String, cur: Int, set: (Int) -> Unit) {
            val sp = Spinner(a)
            sp.adapter = ArrayAdapter(a, android.R.layout.simple_spinner_dropdown_item, names)
            sp.setSelection(Prefs.ACTIONS.indexOfFirst { it.first == cur }.coerceAtLeast(0))
            sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) = set(Prefs.ACTIONS[pos].first)
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            col.addView(Ui.labeled(a, label, sp))
        }
        spinner("Одно нажатие", p.btnSingle) { p.btnSingle = it }
        spinner("Двойное нажатие", p.btnDouble) { p.btnDouble = it }
        spinner("Тройное нажатие", p.btnTriple) { p.btnTriple = it }
        spinner("Зажатие (удержание)", p.btnHold) { p.btnHold = it }
        keyLabel = Ui.text(a, keyText(a), 13f, Ui.MUTED)
        col.addView(keyLabel)
        val kr = Ui.wrapRow(a)
        kr.addView(Ui.button(a, "Назначить клавишу пера…", true) {
            a.learningKey = true
            keyLabel?.text = "Нажмите кнопку на Bluetooth-пере…"
        })
        kr.addView(Ui.button(a, "Сбросить") { p.penKey = 0; keyLabel?.text = keyText(a) })
        col.addView(Ui.hscroll(a, kr))

        col.addView(Ui.header(a, "РИСОВАНИЕ"))
        col.addView(Ui.switch(a, "Распознавать фигуры (задержите перо в конце штриха)", p.shapeRecog) { p.shapeRecog = it })
        col.addView(Ui.switch(a, "Привязка к сетке и осям", p.snapGrid) { p.snapGrid = it })
        col.addView(Ui.switch(a, "Привязка к точкам объектов", p.snapObjects) { p.snapObjects = it })
        col.addView(Ui.switch(a, "Автоматические имена (A, B, C…, α, β…, a⃗, b⃗…)", p.autoName) { p.autoName = it })
        col.addView(Ui.switch(a, "Показывать длины отрезков", p.showLengths) { p.showLengths = it })

        col.addView(Ui.header(a, "ИНТЕРФЕЙС"))
        col.addView(Ui.switch(a, "Панель инструментов справа (для левшей)", p.leftHanded) { p.leftHanded = it; a.applyHandedness() })
        col.addView(Ui.switch(a, "Не гасить экран", p.keepScreenOn) {
            p.keepScreenOn = it
            if (it) a.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else a.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        })

        col.addView(Ui.header(a, "ГОРЯЧИЕ КЛАВИШИ"))
        col.addView(Ui.text(a, "Ctrl+Z / Ctrl+Y — отмена/повтор, Ctrl+D — дублировать, Del — удалить, P ручка, B карандаш, M маркер, E ластик, S выделение, L линейка, C окружность, R фигуры, T текст, X точка, A оси, H рука, +/− масштаб, 0 — показать всё.", 12f, Ui.MUTED))
        val ver = try { a.packageManager.getPackageInfo(a.packageName, 0).versionName } catch (e: Exception) { "" }
        col.addView(Ui.text(a, "PaintPhyMath $ver", 12f, Ui.MUTED).apply { setPadding(0, dpi(a, 12f), 0, 0) })

        AlertDialog.Builder(a).setTitle("Настройки").setView(scroll(a, col))
            .setPositiveButton("Готово") { _, _ -> a.learningKey = false; a.canvas.host?.onStateChanged() }
            .setOnDismissListener { a.learningKey = false; keyLabel = null }
            .show()
    }

    // ================================================================== документы

    fun documents(a: MainActivity) {
        val dlg = Dialog(a, android.R.style.Theme_DeviceDefault_Light_NoActionBar)
        val root = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFFF1F2F7.toInt()) }
        val bar = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Ui.BAR); setPadding(dpi(a, 8f), 0, dpi(a, 8f), 0)
        }
        bar.addView(Ui.iconButton(a, "close", Color.WHITE) { dlg.dismiss() })
        bar.addView(Ui.text(a, "Мои холсты", 18f, Color.WHITE, true).apply { setPadding(dpi(a, 8f), 0, 0, 0) }, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        bar.addView(Ui.button(a, "Импорт") { dlg.dismiss(); a.importProject() })
        bar.addView(Ui.button(a, "+ Новый холст", true) { dlg.dismiss(); a.newDoc() })
        root.addView(bar, LinearLayout.LayoutParams(Ui.MATCH, dpi(a, 58f)))
        val list = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(a, 12f), dpi(a, 12f), dpi(a, 12f), dpi(a, 12f)) }
        root.addView(ScrollView(a).apply { addView(list) }, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))

        fun build() {
            list.removeAllViews()
            val metas = a.store.list()
            val w = a.resources.displayMetrics.widthPixels
            val cardW = dpi(a, 210f)
            val cols = max(2, w / (cardW + dpi(a, 12f)))
            var row: LinearLayout? = null
            val fmt = java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale("ru"))
            metas.forEachIndexed { i, m ->
                if (i % cols == 0) { row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }; list.addView(row) }
                val card = LinearLayout(a).apply {
                    orientation = LinearLayout.VERTICAL
                    background = Ui.ripple(Ui.round(if (m.id == a.canvas.doc.id) Ui.ACCENT_SOFT else Color.WHITE, Ui.dp(a, 16f)), Ui.dp(a, 16f))
                    elevation = Ui.dp(a, 2f)
                    setPadding(dpi(a, 8f), dpi(a, 8f), dpi(a, 8f), dpi(a, 10f))
                    isClickable = true
                    setOnClickListener { dlg.dismiss(); a.openDocById(m.id) }
                    setOnLongClickListener {
                        AlertDialog.Builder(a).setTitle(m.name).setItems(arrayOf("Открыть", "Переименовать", "Дублировать", "Удалить")) { _, k ->
                            when (k) {
                                0 -> { dlg.dismiss(); a.openDocById(m.id) }
                                1 -> rename(a, "Название", m.name) { n ->
                                    if (m.id == a.canvas.doc.id) { a.canvas.doc.name = n; a.canvas.doc.dirty = true; a.saveNow(false); a.openDoc(a.canvas.doc) } else a.store.rename(m.id, n)
                                    build()
                                }
                                2 -> { a.store.duplicate(m.id); build() }
                                3 -> AlertDialog.Builder(a).setTitle("Удалить «${m.name}»?").setPositiveButton("Удалить") { _, _ ->
                                    a.store.delete(m.id)
                                    if (m.id == a.canvas.doc.id) { dlg.dismiss(); a.newDoc() } else build()
                                }.setNegativeButton("Отмена", null).show()
                            }
                        }.show()
                        true
                    }
                }
                val img = ImageView(a).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    background = Ui.round(0xFFF8FAFC.toInt(), Ui.dp(a, 10f))
                    if (m.thumb.exists()) setImageBitmap(BitmapFactory.decodeFile(m.thumb.absolutePath))
                }
                card.addView(img, LinearLayout.LayoutParams(Ui.MATCH, dpi(a, 130f)))
                card.addView(Ui.text(a, m.name, 14f, Ui.TEXT, true).apply { maxLines = 1; setPadding(dpi(a, 2f), dpi(a, 6f), 0, 0) })
                card.addView(Ui.text(a, fmt.format(java.util.Date(m.modified)), 11f, Ui.MUTED).apply { setPadding(dpi(a, 2f), 0, 0, 0) })
                row!!.addView(card, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { setMargins(dpi(a, 6f), dpi(a, 6f), dpi(a, 6f), dpi(a, 6f)) })
            }
            val rem = if (metas.isEmpty()) 0 else (cols - metas.size % cols) % cols
            for (k in 0 until rem) row?.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f).apply { setMargins(dpi(a, 6f), 0, dpi(a, 6f), 0) })
            if (metas.isEmpty()) list.addView(Ui.text(a, "Пока нет сохранённых холстов", 15f, Ui.MUTED))
            list.addView(Ui.text(a, "Долгое нажатие на карточку — переименовать, дублировать, удалить.", 12f, Ui.MUTED).apply { setPadding(dpi(a, 6f), dpi(a, 12f), 0, 0) })
        }
        build()
        dlg.setContentView(root)
        dlg.show()
    }
}

/** Линейная алгебра на double с красивым выводом дробей. */
object Lin {
    fun frac(v: Double): String {
        if (v.isNaN()) return "?"
        if (abs(v - v.roundToInt()) < 1e-9) return v.roundToInt().toString().replace("-", "−")
        // цепные дроби
        var bestN = 0L; var bestD = 1L
        var h1 = 1L; var h0 = 0L; var k1 = 0L; var k0 = 1L
        var x = abs(v)
        for (i in 0 until 20) {
            val a = kotlin.math.floor(x).toLong()
            val h2 = a * h1 + h0; val k2 = a * k1 + k0
            if (k2 > 200) break
            h0 = h1; h1 = h2; k0 = k1; k1 = k2
            bestN = h2; bestD = k2
            if (abs(abs(v) - h2.toDouble() / k2) < 1e-9) break
            val fr = x - a
            if (fr < 1e-12) break
            x = 1 / fr
        }
        if (bestD > 1 && abs(abs(v) - bestN.toDouble() / bestD) < 1e-9) {
            return (if (v < 0) "-" else "") + "\\frac{$bestN}{$bestD}"
        }
        return Expr.fmt(v, 4).replace("−", "-")
    }

    fun det(m: Array<DoubleArray>): Double {
        val n = m.size
        val a = Array(n) { m[it].copyOf() }
        var d = 1.0
        for (c in 0 until n) {
            var p = c
            for (r in c + 1 until n) if (abs(a[r][c]) > abs(a[p][c])) p = r
            if (abs(a[p][c]) < 1e-12) return 0.0
            if (p != c) { val t = a[p]; a[p] = a[c]; a[c] = t; d = -d }
            d *= a[c][c]
            for (r in c + 1 until n) {
                val f = a[r][c] / a[c][c]
                for (k in c until n) a[r][k] -= f * a[c][k]
            }
        }
        return d
    }

    fun rref(m: Array<DoubleArray>): Array<DoubleArray> {
        val a = Array(m.size) { m[it].copyOf() }
        val rows = a.size; val cols = if (rows > 0) a[0].size else 0
        var lead = 0
        for (r in 0 until rows) {
            if (lead >= cols) break
            var i = r
            while (abs(a[i][lead]) < 1e-10) {
                i++
                if (i == rows) { i = r; lead++; if (lead == cols) return clean(a) }
            }
            val t = a[i]; a[i] = a[r]; a[r] = t
            val lv = a[r][lead]
            for (k in 0 until cols) a[r][k] /= lv
            for (j in 0 until rows) if (j != r) {
                val f = a[j][lead]
                for (k in 0 until cols) a[j][k] -= f * a[r][k]
            }
            lead++
        }
        return clean(a)
    }

    private fun clean(a: Array<DoubleArray>): Array<DoubleArray> {
        for (r in a) for (k in r.indices) if (abs(r[k]) < 1e-10) r[k] = 0.0
        return a
    }

    fun rank(m: Array<DoubleArray>) = rref(m).count { r -> r.any { abs(it) > 1e-9 } }

    fun inverse(m: Array<DoubleArray>): Array<DoubleArray>? {
        val n = m.size
        if (abs(det(m)) < 1e-12) return null
        val aug = Array(n) { r -> DoubleArray(2 * n) { c -> if (c < n) m[r][c] else if (c - n == r) 1.0 else 0.0 } }
        val r = rref(aug)
        return Array(n) { i -> DoubleArray(n) { j -> r[i][n + j] } }
    }

    fun mul(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> =
        Array(a.size) { i -> DoubleArray(b[0].size) { j -> (b.indices).sumOf { k -> a[i][k] * b[k][j] } } }

    /** Решение СЛАУ по ступенчатому виду расширенной матрицы. */
    fun solveText(r: Array<DoubleArray>, nVars: Int): String {
        for (row in r) {
            if ((0 until nVars).all { abs(row[it]) < 1e-9 } && abs(row[nVars]) > 1e-9) return "Система несовместна (0 = ${frac(row[nVars]).replace("\\frac", "")})"
        }
        val rank = r.count { row -> (0 until nVars).any { abs(row[it]) > 1e-9 } }
        if (rank < nVars) return "Бесконечно много решений (свободных переменных: ${nVars - rank})"
        val sb = StringBuilder("Единственное решение: ")
        for (i in 0 until nVars) {
            val row = r.firstOrNull { abs(it[i] - 1) < 1e-9 && (0 until nVars).all { k -> k == i || abs(it[k]) < 1e-9 } } ?: continue
            sb.append("x${i + 1} = ${Expr.fmt(row[nVars], 6)}  ")
        }
        return sb.toString()
    }
}
