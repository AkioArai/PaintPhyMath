package com.paintphymath

import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// =====================================================================================
//  Система координат
// =====================================================================================

class AxesEl() : El() {
    override val type = "axes"
    var ox = 0f; var oy = 0f
    /** пикселей на единицу */
    var unit = 40f
    var xMin = -6f; var xMax = 6f; var yMin = -5f; var yMax = 5f
    var xName = "x"; var yName = "y"
    var grid = true
    var stepX = 0f; var stepY = 0f
    var piTicks = false
    var polar = false
    var showNumbers = true

    init { width = 1.6f; color = Color.rgb(40, 40, 52) }

    fun toWorld(ux: Double, uy: Double) = PointF((ox + ux * unit).toFloat(), (oy - uy * unit).toFloat())
    fun toUnits(x: Float, y: Float) = PointF((x - ox) / unit, (oy - y) / unit)
    fun rect() = RectF(ox + xMin * unit, oy - yMax * unit, ox + xMax * unit, oy - yMin * unit)

    fun sx(): Double = if (stepX > 0) stepX.toDouble() else if (piTicks) PI / 2 else Geo.niceStep(38.0 / unit)
    fun sy(): Double = if (stepY > 0) stepY.toDouble() else Geo.niceStep(38.0 / unit)

    fun snap(x: Float, y: Float): PointF {
        val u = toUnits(x, y)
        val stx = sx() / (if (piTicks) 1.0 else 2.0)
        val sty = sy() / 2.0
        val gx = (u.x / stx).roundToInt() * stx
        val gy = (u.y / sty).roundToInt() * sty
        return toWorld(gx, gy)
    }

    private fun tickLabel(v: Double, pi: Boolean): String {
        if (!pi) return Expr.fmt(v, 3)
        val k = (v / (PI / 2)).roundToInt()
        return when {
            k == 0 -> "0"
            k % 2 == 0 -> { val m = k / 2; when (m) { 1 -> "π"; -1 -> "−π"; else -> "${m}π".replace("-", "−") } }
            else -> (if (k < 0) "−" else "") + (if (abs(k) == 1) "" else "${abs(k)}") + "π/2"
        }
    }

    override fun draw(g: Gfx, ctx: DrawCtx) {
        val r = rect()
        val light = withAlpha(color, 0.13f)
        val mid = withAlpha(color, 0.22f)
        val sx = sx(); val sy = sy()
        if (grid) {
            if (polar) {
                val maxR = max(max(abs(xMin), abs(xMax)), max(abs(yMin), abs(yMax))) * 1.5
                var rr = sx
                while (rr <= maxR) {
                    val p = GPath(); p.addCircle(ox, oy, (rr * unit).toFloat())
                    // рисуем окружности, отсекая их прямоугольником приблизительно — сэмплированием
                    val q = GPath(); var pen = false
                    for (i in 0..180) {
                        val a = i * 2 * PI / 180
                        val px = ox + (rr * unit * cos(a)).toFloat(); val py = oy - (rr * unit * sin(a)).toFloat()
                        if (r.contains(px, py)) { if (pen) q.lineTo(px, py) else q.moveTo(px, py); pen = true } else pen = false
                    }
                    g.path(q, light, 1f)
                    rr += sx
                }
                for (k in 0 until 12) {
                    val a = k * PI / 6
                    val c = Geo.clip(ox.toDouble(), oy.toDouble(), ox + cos(a) * 5000, oy - sin(a) * 5000, r.left.toDouble(), r.top.toDouble(), r.right.toDouble(), r.bottom.toDouble())
                    if (c != null) g.line(c[0].toFloat(), c[1].toFloat(), c[2].toFloat(), c[3].toFloat(), light, 1f)
                }
            } else {
                // мелкая сетка
                val fine = if (sx * unit > 60 && !piTicks) sx / 5 else 0.0
                if (fine > 0) {
                    var v = ceil(xMin / fine) * fine
                    while (v <= xMax + 1e-9) { val X = (ox + v * unit).toFloat(); g.line(X, r.top, X, r.bottom, withAlpha(color, 0.06f), 0.8f); v += fine }
                    v = ceil(yMin / fine) * fine
                    while (v <= yMax + 1e-9) { val Y = (oy - v * unit).toFloat(); g.line(r.left, Y, r.right, Y, withAlpha(color, 0.06f), 0.8f); v += fine }
                }
                var v = ceil(xMin / sx) * sx
                while (v <= xMax + 1e-9) { val X = (ox + v * unit).toFloat(); g.line(X, r.top, X, r.bottom, light, 1f); v += sx }
                v = ceil(yMin / sy) * sy
                while (v <= yMax + 1e-9) { val Y = (oy - v * unit).toFloat(); g.line(r.left, Y, r.right, Y, light, 1f); v += sy }
            }
        } else {
            g.path(GPath().apply { moveTo(r.left, r.top); lineTo(r.right, r.top); lineTo(r.right, r.bottom); lineTo(r.left, r.bottom); close() }, mid, 0.8f, 0, 4f)
        }
        // оси
        val axX = oy.coerceIn(r.top, r.bottom)
        val axY = ox.coerceIn(r.left, r.right)
        g.line(r.left, axX, r.right + 10, axX, color, width)
        arrowHead(g, r.right, axX, r.right + 14, axX, color, width)
        g.line(axY, r.bottom, axY, r.top - 10, color, width)
        arrowHead(g, axY, r.bottom, axY, r.top - 14, color, width)
        val fs = 13f
        label(g, xName, r.right + 12, axX + 16, 18f, color)
        label(g, yName, axY - 16, r.top - 10, 18f, color)
        // деления
        var v = ceil(xMin / sx) * sx
        while (v <= xMax + 1e-9) {
            val X = (ox + v * unit).toFloat()
            if (abs(v) > sx * 1e-6) {
                g.line(X, axX - 4, X, axX + 4, color, 1.2f)
                if (showNumbers) g.text(tickLabel(v, piTicks), X, axX + 17f, fs, withAlpha(color, 0.8f), Font.SANS, 1)
            }
            v += sx
        }
        v = ceil(yMin / sy) * sy
        while (v <= yMax + 1e-9) {
            val Y = (oy - v * unit).toFloat()
            if (abs(v) > sy * 1e-6) {
                g.line(axY - 4, Y, axY + 4, Y, color, 1.2f)
                if (showNumbers) g.text(tickLabel(v, false), axY - 7f, Y + 4.5f, fs, withAlpha(color, 0.8f), Font.SANS, 2)
            }
            v += sy
        }
        if (r.contains(ox, oy)) g.text("0", ox - 6f, oy + 16f, fs, withAlpha(color, 0.8f), Font.SANS, 2)
    }

    override fun bounds(ctx: DrawCtx) = rect().apply { inset(-30f, -30f) }

    override fun hit(x: Float, y: Float, tol: Float, ctx: DrawCtx): Boolean {
        val r = rect()
        val axX = oy.coerceIn(r.top, r.bottom)
        val axY = ox.coerceIn(r.left, r.right)
        if (x >= r.left - tol && x <= r.right + 20 && abs(y - axX) <= tol + 4) return true
        if (y >= r.top - 20 && y <= r.bottom + tol && abs(x - axY) <= tol + 4) return true
        return false
    }

    override fun transform(m: Matrix) {
        val p = Geo.mapPt(m, ox, oy); ox = p.x; oy = p.y
        unit *= Geo.scaleOf(m)
    }

    override fun snapPoints(ctx: DrawCtx) = listOf(PointF(ox, oy))

    override fun write(o: JSONObject) {
        o.put("p", farr(floatArrayOf(ox, oy, unit, xMin, xMax, yMin, yMax, stepX, stepY)))
        o.put("xn", xName); o.put("yn", yName); o.put("g", grid); o.put("pi", piTicks); o.put("pol", polar); o.put("num", showNumbers)
    }

    override fun read(o: JSONObject) {
        val p = rfarr(o.optJSONArray("p"))
        if (p.size >= 9) { ox = p[0]; oy = p[1]; unit = p[2]; xMin = p[3]; xMax = p[4]; yMin = p[5]; yMax = p[6]; stepX = p[7]; stepY = p[8] }
        xName = o.optString("xn", "x"); yName = o.optString("yn", "y"); grid = o.optBoolean("g", true)
        piTicks = o.optBoolean("pi"); polar = o.optBoolean("pol"); showNumbers = o.optBoolean("num", true)
    }
}

// =====================================================================================
//  График / последовательность / ряд / данные / линейное отображение / поля
// =====================================================================================

class PlotEl() : El() {
    override val type = "plot"
    var axesId = 0L
    var kind = FX
    var e1 = "x^2"
    var e2 = ""
    var t0 = Float.NaN
    var t1 = Float.NaN
    var params = ""
    var data = FloatArray(0)
    /** 0 нет, 1 линейная, 2 квадратичная, 3 экспонента, 4 степенная */
    var fit = 0
    var m = floatArrayOf(1f, 0f, 0f, 1f)
    var showLabel = true
    var label = ""
    var dash = false
    var shadeA = Float.NaN
    var shadeB = Float.NaN
    var tangentAt = Float.NaN
    var solX = Float.NaN
    var solY = Float.NaN

    override val movable get() = false

    init { width = 2.6f; color = Color.rgb(37, 99, 235) }

    @Transient private var c1: Expr? = null
    @Transient private var c2: Expr? = null
    @Transient private var cacheKey = ""
    @Transient var info = ""

    companion object {
        const val FX = 0; const val PARAM = 1; const val POLAR = 2; const val SEQ = 3; const val SERIES = 4
        const val DATA = 5; const val LINMAP = 6; const val SLOPE = 7; const val VFIELD = 8
    }

    private fun compile() {
        val key = "$e1|$e2|$kind"
        if (key == cacheKey) return
        c1 = Expr.tryParse(e1); c2 = Expr.tryParse(e2); cacheKey = key
    }

    override fun invalidateCache() { cacheKey = "" }

    private fun vars(): HashMap<String, Double> = Expr.baseVars().apply { putAll(Expr.parseParams(params)) }

    fun axes(ctx: DrawCtx) = ctx.doc.find(axesId) as? AxesEl

    /** Построение ломаной в координатах осей с отсечением по прямоугольнику осей. */
    private class Tracer(val ax: AxesEl) {
        val p = GPath()
        private var has = false
        private var lx = 0.0; private var ly = 0.0
        private var penUp = true
        val l = ax.xMin.toDouble(); val r = ax.xMax.toDouble(); val b = ax.yMin.toDouble(); val t = ax.yMax.toDouble()
        private val jump = (t - b) * 3

        fun add(x: Double, y: Double) {
            if (x.isNaN() || y.isNaN() || x.isInfinite() || y.isInfinite()) { has = false; return }
            if (has && abs(y - ly) < jump) {
                val c = Geo.clip(lx, ly, x, y, l, b, r, t)
                if (c != null) {
                    val a = ax.toWorld(c[0], c[1]); val e = ax.toWorld(c[2], c[3])
                    if (penUp || c[4] > 0) p.moveTo(a.x, a.y)
                    p.lineTo(e.x, e.y)
                    penUp = c[5] < 1.0
                } else penUp = true
            } else penUp = true
            lx = x; ly = y; has = true
        }

        fun breakLine() { has = false; penUp = true }
    }

    private fun f(x: Double, v: HashMap<String, Double>): Double { v["x"] = x; return c1?.eval(v) ?: Double.NaN }

    override fun draw(g: Gfx, ctx: DrawCtx) {
        val ax = axes(ctx) ?: return
        compile()
        val v = vars()
        val d = if (dash) width * 3 + 4 else 0f
        val lw = width
        info = ""
        when (kind) {
            FX -> {
                val c = c1 ?: return
                val a = if (t0.isNaN()) ax.xMin.toDouble() else max(t0.toDouble(), ax.xMin.toDouble())
                val b = if (t1.isNaN()) ax.xMax.toDouble() else min(t1.toDouble(), ax.xMax.toDouble())
                if (!shadeA.isNaN() && !shadeB.isNaN()) drawShade(g, ax, v, shadeA.toDouble(), shadeB.toDouble())
                val tr = Tracer(ax)
                val n = max(200, ((b - a) * ax.unit / 1.5).toInt()).coerceAtMost(4000)
                for (i in 0..n) { val x = a + (b - a) * i / n; v["x"] = x; tr.add(x, c.eval(v)) }
                g.path(tr.p, color, lw, 0, d)
                if (!tangentAt.isNaN()) drawTangent(g, ax, v, tangentAt.toDouble())
                if (showLabel) drawEqLabel(g, ax, label.ifBlank { "y = " + pretty(e1) }) { xx -> f(xx, v) }
            }
            PARAM, POLAR -> {
                val cx = c1 ?: return
                val cy = if (kind == PARAM) (c2 ?: return) else null
                val a = if (t0.isNaN()) 0.0 else t0.toDouble()
                val b = if (t1.isNaN()) 2 * PI else t1.toDouble()
                val tr = Tracer(ax)
                val n = 1500
                for (i in 0..n) {
                    val t = a + (b - a) * i / n
                    v["t"] = t; v["theta"] = t; v["phi"] = t
                    if (kind == PARAM) tr.add(cx.eval(v), cy!!.eval(v))
                    else { val rr = cx.eval(v); tr.add(rr * cos(t), rr * sin(t)) }
                }
                g.path(tr.p, color, lw, 0, d)
                if (showLabel) {
                    val s = label.ifBlank { if (kind == PARAM) "x = ${pretty(e1)}, y = ${pretty(e2)}" else "r = ${pretty(e1)}" }
                    val rr = ax.rect()
                    val sz = labelSize(s, 16f)
                    label(g, s, rr.right - sz.x / 2 - 8, rr.top + sz.y / 2 + 6, 16f, color, withAlpha(ctx.paperColor, 0.8f))
                }
            }
            SEQ, SERIES -> {
                val c = c1 ?: return
                val n0 = if (t0.isNaN()) 1 else t0.toInt()
                val n1 = if (t1.isNaN()) 20 else t1.toInt()
                var s = 0.0
                var last = Double.NaN
                val link = GPath(); var first = true
                for (k in n0..min(n1, n0 + 5000)) {
                    v["n"] = k.toDouble(); v["k"] = k.toDouble(); v["x"] = k.toDouble()
                    val a = c.eval(v)
                    s += a
                    val y = if (kind == SERIES) s else a
                    last = y
                    if (k > ax.xMax) continue
                    if (y.isNaN() || y < ax.yMin || y > ax.yMax || k < ax.xMin) continue
                    val p = ax.toWorld(k.toDouble(), y)
                    if (first) link.moveTo(p.x, p.y) else link.lineTo(p.x, p.y)
                    first = false
                    g.circle(p.x, p.y, 2.2f + lw * 0.7f, 0, 0f, color)
                }
                g.path(link, withAlpha(color, 0.35f), 1.2f, 0, 4f)
                val nm = if (kind == SERIES) "S_{$n1}" else "a_{$n1}"
                info = "$nm ≈ ${Expr.fmt(last, 6)}"
                if (showLabel) {
                    val s1 = label.ifBlank {
                        if (kind == SERIES) "\\sum_{n=$n0}^{$n1} ${pretty(e1)} ≈ ${Expr.fmt(last, 5)}"
                        else "a_n = ${pretty(e1)}"
                    }
                    val rr = ax.rect()
                    val sz = labelSize(s1, 16f)
                    label(g, s1, rr.right - sz.x / 2 - 8, rr.top + sz.y / 2 + 6, 16f, color, withAlpha(ctx.paperColor, 0.8f))
                }
            }
            DATA -> drawData(g, ax, ctx, lw)
            LINMAP -> drawLinMap(g, ax, ctx)
            SLOPE, VFIELD -> drawField(g, ax, v, ctx)
        }
    }

    private fun pretty(s: String) = s.replace("*", "·").replace("sqrt", "√")

    private fun drawEqLabel(g: Gfx, ax: AxesEl, s: String, fn: (Double) -> Double) {
        // ищем правую точку графика внутри осей для подписи
        val xs = ax.xMax - (ax.xMax - ax.xMin) * 0.12
        var x = xs
        var y = fn(x)
        var tries = 0
        while ((y.isNaN() || y < ax.yMin || y > ax.yMax) && tries < 40) { x -= (ax.xMax - ax.xMin) / 50.0; y = fn(x); tries++ }
        if (y.isNaN() || y < ax.yMin || y > ax.yMax) return
        val p = ax.toWorld(x, y)
        val sz = labelSize(s, 16f)
        val above = y < (ax.yMin + ax.yMax) / 2
        label(g, s, p.x - sz.x / 2, p.y + (if (above) -1 else 1) * (sz.y / 2 + 8), 16f, color)
    }

    private fun drawShade(g: Gfx, ax: AxesEl, v: HashMap<String, Double>, a0: Double, b0: Double) {
        val a = max(min(a0, b0), ax.xMin.toDouble()); val b = min(max(a0, b0), ax.xMax.toDouble())
        if (b <= a) return
        val p = GPath()
        val base = ax.toWorld(a, 0.0)
        p.moveTo(base.x, base.y)
        val n = 300
        for (i in 0..n) {
            val x = a + (b - a) * i / n
            val y = f(x, v).let { if (it.isNaN()) 0.0 else it.coerceIn(ax.yMin.toDouble(), ax.yMax.toDouble()) }
            val q = ax.toWorld(x, y); p.lineTo(q.x, q.y)
        }
        val e = ax.toWorld(b, 0.0); p.lineTo(e.x, e.y); p.close()
        g.path(p, 0, 0f, withAlpha(color, 0.18f))
        // интеграл методом Симпсона
        val m = 1000
        val h = (b0 - a0) / m
        var s = f(a0, v) + f(b0, v)
        for (i in 1 until m) s += f(a0 + i * h, v) * (if (i % 2 == 1) 4 else 2)
        val integral = s * h / 3
        info = "∫ = ${Expr.fmt(integral, 5)}"
        val mid = ax.toWorld((a + b) / 2, 0.0)
        val t = "\\int_{${Expr.fmt(a0, 3)}}^{${Expr.fmt(b0, 3)}} f\\,dx ≈ ${Expr.fmt(integral, 4)}"
        label(g, t, mid.x, mid.y + 30, 15f, color)
    }

    private fun drawTangent(g: Gfx, ax: AxesEl, v: HashMap<String, Double>, x0: Double) {
        val h = 1e-5 * max(1.0, abs(x0))
        val y0 = f(x0, v)
        val k = (f(x0 + h, v) - f(x0 - h, v)) / (2 * h)
        if (y0.isNaN() || k.isNaN()) return
        val tc = Color.rgb(220, 38, 38)
        val c = Geo.clip(ax.xMin.toDouble(), y0 + k * (ax.xMin - x0), ax.xMax.toDouble(), y0 + k * (ax.xMax - x0),
            ax.xMin.toDouble(), ax.yMin.toDouble(), ax.xMax.toDouble(), ax.yMax.toDouble())
        if (c != null) {
            val a = ax.toWorld(c[0], c[1]); val b = ax.toWorld(c[2], c[3])
            g.line(a.x, a.y, b.x, b.y, tc, max(1.5f, width * 0.7f), 7f)
        }
        val p = ax.toWorld(x0, y0)
        g.circle(p.x, p.y, 4f, 0, 0f, tc)
        val bb = y0 - k * x0
        val s = "k = f'(${Expr.fmt(x0, 3)}) ≈ ${Expr.fmt(k, 4)}"
        label(g, s, p.x + 10 + labelSize(s, 14f).x / 2, p.y + 22, 14f, tc)
        info = "y = ${Expr.fmt(k, 4)}x + ${Expr.fmt(bb, 4)}"
    }

    // ---------------------------------------------------------------- данные и МНК

    fun fitCoeffs(): DoubleArray? {
        val n = data.size / 2
        if (n < 2) return null
        val xs = DoubleArray(n) { data[it * 2].toDouble() }
        val ys = DoubleArray(n) { data[it * 2 + 1].toDouble() }
        return when (fit) {
            1 -> linFit(xs, ys)
            2 -> quadFit(xs, ys)
            3 -> {
                if (ys.any { it <= 0 }) null
                else linFit(xs, DoubleArray(n) { ln(ys[it]) })?.let { doubleArrayOf(exp(it[1]), it[0]) } // y = A e^{Bx}
            }
            4 -> {
                if (ys.any { it <= 0 } || xs.any { it <= 0 }) null
                else linFit(DoubleArray(n) { ln(xs[it]) }, DoubleArray(n) { ln(ys[it]) })?.let { doubleArrayOf(exp(it[1]), it[0]) } // y = A x^B
            }
            else -> null
        }
    }

    private fun fitEval(c: DoubleArray, x: Double) = when (fit) {
        1 -> c[0] * x + c[1]
        2 -> c[0] * x * x + c[1] * x + c[2]
        3 -> c[0] * exp(c[1] * x)
        4 -> c[0] * Math.pow(x, c[1])
        else -> Double.NaN
    }

    private fun linFit(xs: DoubleArray, ys: DoubleArray): DoubleArray? {
        val n = xs.size
        val mx = xs.average(); val my = ys.average()
        var sxy = 0.0; var sxx = 0.0
        for (i in 0 until n) { sxy += (xs[i] - mx) * (ys[i] - my); sxx += (xs[i] - mx) * (xs[i] - mx) }
        if (sxx == 0.0) return null
        val k = sxy / sxx
        return doubleArrayOf(k, my - k * mx)
    }

    private fun quadFit(xs: DoubleArray, ys: DoubleArray): DoubleArray? {
        if (xs.size < 3) return null
        val s = DoubleArray(5); val t = DoubleArray(3)
        for (i in xs.indices) {
            var p = 1.0
            for (k in 0..4) { s[k] += p; if (k <= 2) t[k] += p * ys[i]; p *= xs[i] }
        }
        // система для a x² + b x + c
        val a = arrayOf(doubleArrayOf(s[4], s[3], s[2], t[2]), doubleArrayOf(s[3], s[2], s[1], t[1]), doubleArrayOf(s[2], s[1], s[0], t[0]))
        for (c in 0 until 3) {
            var piv = c
            for (r in c + 1 until 3) if (abs(a[r][c]) > abs(a[piv][c])) piv = r
            val tmp = a[c]; a[c] = a[piv]; a[piv] = tmp
            if (abs(a[c][c]) < 1e-12) return null
            for (r in 0 until 3) if (r != c) {
                val f = a[r][c] / a[c][c]
                for (k in c..3) a[r][k] -= f * a[c][k]
            }
        }
        return doubleArrayOf(a[0][3] / a[0][0], a[1][3] / a[1][1], a[2][3] / a[2][2])
    }

    private fun r2(c: DoubleArray): Double {
        val n = data.size / 2
        val my = (0 until n).sumOf { data[it * 2 + 1].toDouble() } / n
        var ssr = 0.0; var sst = 0.0
        for (i in 0 until n) {
            val y = data[i * 2 + 1].toDouble()
            val f = fitEval(c, data[i * 2].toDouble())
            ssr += (y - f) * (y - f); sst += (y - my) * (y - my)
        }
        return if (sst == 0.0) 1.0 else 1 - ssr / sst
    }

    private fun drawData(g: Gfx, ax: AxesEl, ctx: DrawCtx, lw: Float) {
        val n = data.size / 2
        val c = fitCoeffs()
        if (c != null) {
            val tr = Tracer(ax)
            val steps = 400
            for (i in 0..steps) {
                val x = ax.xMin + (ax.xMax - ax.xMin) * i.toDouble() / steps
                tr.add(x, fitEval(c, x))
            }
            g.path(tr.p, withAlpha(color, 0.7f), lw * 0.8f, 0, if (dash) 8f else 0f)
            val eq = when (fit) {
                1 -> "y = ${Expr.fmt(c[0], 4)}x ${sgn(c[1])}"
                2 -> "y = ${Expr.fmt(c[0], 4)}x^2 ${sgn(c[1])}x ${sgn(c[2])}"
                3 -> "y = ${Expr.fmt(c[0], 4)}·e^{${Expr.fmt(c[1], 4)}x}"
                4 -> "y = ${Expr.fmt(c[0], 4)}·x^{${Expr.fmt(c[1], 4)}}"
                else -> ""
            }
            val s = "$eq,  R^2 = ${Expr.fmt(r2(c), 4)}"
            info = s
            if (showLabel) {
                val rr = ax.rect()
                val sz = labelSize(s, 15f)
                label(g, s, rr.right - sz.x / 2 - 8, rr.top + sz.y / 2 + 6, 15f, color, withAlpha(ctx.paperColor, 0.85f))
            }
        }
        for (i in 0 until n) {
            val x = data[i * 2].toDouble(); val y = data[i * 2 + 1].toDouble()
            if (x < ax.xMin || x > ax.xMax || y < ax.yMin || y > ax.yMax) continue
            val p = ax.toWorld(x, y)
            val r = 3f + lw * 0.8f
            g.line(p.x - r, p.y - r, p.x + r, p.y + r, color, lw * 0.8f)
            g.line(p.x - r, p.y + r, p.x + r, p.y - r, color, lw * 0.8f)
        }
    }

    private fun sgn(v: Double): String {
        val s = Expr.fmt(abs(v), 4)
        return if (v < 0) "− $s" else "+ $s"
    }

    // ---------------------------------------------------------------- линейное отображение

    private fun drawLinMap(g: Gfx, ax: AxesEl, ctx: DrawCtx) {
        val a = m[0].toDouble(); val b = m[1].toDouble(); val c = m[2].toDouble(); val d = m[3].toDouble()
        val gridC = withAlpha(color, 0.35f)
        val span = max(max(abs(ax.xMin), abs(ax.xMax)), max(abs(ax.yMin), abs(ax.yMax))).toInt() * 3 + 2
        fun seg(x0: Double, y0: Double, x1: Double, y1: Double, col: Int, w: Float) {
            val p0x = a * x0 + b * y0; val p0y = c * x0 + d * y0
            val p1x = a * x1 + b * y1; val p1y = c * x1 + d * y1
            val cl = Geo.clip(p0x, p0y, p1x, p1y, ax.xMin.toDouble(), ax.yMin.toDouble(), ax.xMax.toDouble(), ax.yMax.toDouble()) ?: return
            val q0 = ax.toWorld(cl[0], cl[1]); val q1 = ax.toWorld(cl[2], cl[3])
            g.line(q0.x, q0.y, q1.x, q1.y, col, w)
        }
        for (k in -span..span) {
            seg(k.toDouble(), -span.toDouble(), k.toDouble(), span.toDouble(), gridC, 1f)
            seg(-span.toDouble(), k.toDouble(), span.toDouble(), k.toDouble(), gridC, 1f)
        }
        // образ единичного квадрата
        val sq = GPath()
        val o = ax.toWorld(0.0, 0.0)
        val e1 = ax.toWorld(a, c); val e2 = ax.toWorld(b, d); val e3 = ax.toWorld(a + b, c + d)
        sq.moveTo(o.x, o.y); sq.lineTo(e1.x, e1.y); sq.lineTo(e3.x, e3.y); sq.lineTo(e2.x, e2.y); sq.close()
        g.path(sq, 0, 0f, withAlpha(Color.rgb(250, 204, 21), 0.35f))
        val ci = Color.rgb(22, 163, 74); val cj = Color.rgb(220, 38, 38)
        g.line(o.x, o.y, e1.x, e1.y, ci, 3f); arrowHead(g, o.x, o.y, e1.x, e1.y, ci, 3f)
        g.line(o.x, o.y, e2.x, e2.y, cj, 3f); arrowHead(g, o.x, o.y, e2.x, e2.y, cj, 3f)
        label(g, "A\\vec{i}", e1.x + 18, e1.y - 10, 15f, ci)
        label(g, "A\\vec{j}", e2.x + 18, e2.y - 10, 15f, cj)
        val det = a * d - b * c
        val tr = a + d
        val disc = tr * tr - 4 * det
        val eig = if (disc >= 0) {
            val l1 = (tr + sqrt(disc)) / 2; val l2 = (tr - sqrt(disc)) / 2
            "λ_1 = ${Expr.fmt(l1, 3)}, λ_2 = ${Expr.fmt(l2, 3)}"
        } else {
            "λ = ${Expr.fmt(tr / 2, 3)} ± ${Expr.fmt(sqrt(-disc) / 2, 3)}i"
        }
        // собственные направления
        if (disc >= 0) {
            val ec = Color.rgb(147, 51, 234)
            for (l in doubleArrayOf((tr + sqrt(disc)) / 2, (tr - sqrt(disc)) / 2)) {
                val vx: Double; val vy: Double
                if (abs(b) > 1e-9) { vx = b; vy = l - a } else if (abs(c) > 1e-9) { vx = l - d; vy = c } else {
                    if (abs(l - a) < 1e-9) { vx = 1.0; vy = 0.0 } else { vx = 0.0; vy = 1.0 }
                }
                val len = hypot(vx, vy); if (len < 1e-12) continue
                val cl = Geo.clip(-vx / len * 1000, -vy / len * 1000, vx / len * 1000, vy / len * 1000,
                    ax.xMin.toDouble(), ax.yMin.toDouble(), ax.xMax.toDouble(), ax.yMax.toDouble()) ?: continue
                val q0 = ax.toWorld(cl[0], cl[1]); val q1 = ax.toWorld(cl[2], cl[3])
                g.line(q0.x, q0.y, q1.x, q1.y, withAlpha(ec, 0.7f), 1.6f, 6f)
            }
        }
        val s = "A = \\pmatrix{${Expr.fmt(a, 3)} & ${Expr.fmt(b, 3)} \\\\ ${Expr.fmt(c, 3)} & ${Expr.fmt(d, 3)}},\\; \\det A = ${Expr.fmt(det, 4)}"
        info = "det = ${Expr.fmt(det, 4)}; $eig"
        if (showLabel) {
            val rr = ax.rect()
            val sz = labelSize(s, 15f)
            label(g, s, rr.right - sz.x / 2 - 8, rr.top + sz.y / 2 + 8, 15f, color, withAlpha(ctx.paperColor, 0.85f))
            val sz2 = labelSize(eig, 14f)
            label(g, eig, rr.right - sz2.x / 2 - 8, rr.top + sz.y + sz2.y / 2 + 14, 14f, Color.rgb(147, 51, 234), withAlpha(ctx.paperColor, 0.85f))
        }
    }

    // ---------------------------------------------------------------- поля направлений

    private fun drawField(g: Gfx, ax: AxesEl, v: HashMap<String, Double>, ctx: DrawCtx) {
        val c = c1 ?: return
        val step = ax.sx().let { if (it * ax.unit < 28) Geo.niceStep(28.0 / ax.unit) else it }
        val half = (step * ax.unit * 0.38).toFloat()
        var maxMag = 1e-9
        val vals = ArrayList<DoubleArray>()
        var x = ceil(ax.xMin / step) * step
        while (x <= ax.xMax + 1e-9) {
            var y = ceil(ax.yMin / step) * step
            while (y <= ax.yMax + 1e-9) {
                v["x"] = x; v["y"] = y
                if (kind == SLOPE) {
                    val k = c.eval(v)
                    vals.add(doubleArrayOf(x, y, 1.0, k))
                } else {
                    val p = c.eval(v); val q = c2?.eval(v) ?: 0.0
                    val mag = hypot(p, q)
                    if (!mag.isNaN()) maxMag = max(maxMag, mag)
                    vals.add(doubleArrayOf(x, y, p, q))
                }
                y += step
            }
            x += step
        }
        val col = withAlpha(color, 0.75f)
        for (a in vals) {
            val p0 = ax.toWorld(a[0], a[1])
            var dx = a[2]; var dy = a[3]
            if (dx.isNaN() || dy.isNaN()) continue
            val len = hypot(dx, dy)
            if (len < 1e-12) { g.circle(p0.x, p0.y, 1.5f, 0, 0f, col); continue }
            if (kind == SLOPE && dy.isInfinite()) { dx = 0.0; dy = 1.0 }
            val k = if (kind == SLOPE) 1.0 else (0.35 + 0.65 * len / maxMag)
            val ux = (dx / len * half * k).toFloat(); val uy = (-dy / len * half * k).toFloat()
            if (kind == SLOPE) g.line(p0.x - ux, p0.y - uy, p0.x + ux, p0.y + uy, col, max(1.2f, width * 0.5f))
            else {
                g.line(p0.x - ux, p0.y - uy, p0.x + ux, p0.y + uy, col, max(1.2f, width * 0.5f))
                arrowHead(g, p0.x - ux, p0.y - uy, p0.x + ux, p0.y + uy, col, 1f)
            }
        }
        // интегральная кривая через (solX, solY) методом Рунге–Кутты
        if (!solX.isNaN() && !solY.isNaN()) {
            val sc = Color.rgb(220, 38, 38)
            val h = (ax.xMax - ax.xMin) / 800.0
            for (dir in intArrayOf(1, -1)) {
                val tr = Tracer(ax)
                var xx = solX.toDouble(); var yy = solY.toDouble()
                tr.add(xx, yy)
                for (i in 0 until 2000) {
                    val hh = h * dir
                    if (kind == SLOPE) {
                        fun fn(a: Double, b: Double): Double { v["x"] = a; v["y"] = b; return c.eval(v) }
                        val k1 = fn(xx, yy); val k2 = fn(xx + hh / 2, yy + hh * k1 / 2)
                        val k3 = fn(xx + hh / 2, yy + hh * k2 / 2); val k4 = fn(xx + hh, yy + hh * k3)
                        yy += hh * (k1 + 2 * k2 + 2 * k3 + k4) / 6; xx += hh
                    } else {
                        fun fx(a: Double, b: Double): DoubleArray { v["x"] = a; v["y"] = b; return doubleArrayOf(c.eval(v), c2?.eval(v) ?: 0.0) }
                        val a1 = fx(xx, yy); val a2 = fx(xx + hh * a1[0] / 2, yy + hh * a1[1] / 2)
                        val a3 = fx(xx + hh * a2[0] / 2, yy + hh * a2[1] / 2); val a4 = fx(xx + hh * a3[0], yy + hh * a3[1])
                        xx += hh * (a1[0] + 2 * a2[0] + 2 * a3[0] + a4[0]) / 6
                        yy += hh * (a1[1] + 2 * a2[1] + 2 * a3[1] + a4[1]) / 6
                    }
                    if (xx.isNaN() || yy.isNaN() || xx < ax.xMin - 1 || xx > ax.xMax + 1 || yy < ax.yMin - 5 || yy > ax.yMax + 5) break
                    tr.add(xx, yy)
                }
                g.path(tr.p, sc, 2.4f)
            }
            val p = ax.toWorld(solX.toDouble(), solY.toDouble())
            g.circle(p.x, p.y, 4f, 0, 0f, sc)
        }
        if (showLabel) {
            val s = label.ifBlank { if (kind == SLOPE) "y' = ${pretty(e1)}" else "\\vec{F} = (${pretty(e1)};\\ ${pretty(e2)})" }
            val rr = ax.rect()
            val sz = labelSize(s, 16f)
            label(g, s, rr.right - sz.x / 2 - 8, rr.top + sz.y / 2 + 6, 16f, color, withAlpha(ctx.paperColor, 0.85f))
        }
    }

    override fun bounds(ctx: DrawCtx): RectF = axes(ctx)?.rect() ?: RectF()

    override fun hit(x: Float, y: Float, tol: Float, ctx: DrawCtx): Boolean {
        val ax = axes(ctx) ?: return false
        compile()
        val u = ax.toUnits(x, y)
        val tu = (tol + width) / ax.unit
        val v = vars()
        return when (kind) {
            FX -> {
                // проверяем окрестность по x
                var hit = false
                val n = 16
                var px = Double.NaN; var py = Double.NaN
                for (i in -n..n) {
                    val xx = (u.x + tu * 2 * i / n).toDouble()
                    val yy = f(xx, v)
                    if (!px.isNaN() && !yy.isNaN()) {
                        val a = ax.toWorld(px, py); val b = ax.toWorld(xx, yy)
                        if (Geo.segDist(x, y, a.x, a.y, b.x, b.y) <= tol + width) { hit = true; break }
                    }
                    px = xx; py = yy
                }
                hit
            }
            DATA -> (0 until data.size / 2).any { i ->
                val p = ax.toWorld(data[i * 2].toDouble(), data[i * 2 + 1].toDouble()); Geo.dist(x, y, p.x, p.y) < tol + 6
            }
            SEQ, SERIES -> {
                val k = u.x.roundToInt()
                val c = c1 ?: return false
                var s = 0.0
                val n0 = if (t0.isNaN()) 1 else t0.toInt()
                if (k < n0) return false
                for (j in n0..k) { v["n"] = j.toDouble(); v["k"] = j.toDouble(); val a = c.eval(v); s += a; if (j == k) { val yy = if (kind == SERIES) s else a; val p = ax.toWorld(k.toDouble(), yy); return Geo.dist(x, y, p.x, p.y) < tol + 6 } }
                false
            }
            else -> false
        }
    }

    override fun transform(m: Matrix) {}

    override fun write(o: JSONObject) {
        o.put("ax", axesId); o.put("k", kind); o.put("e1", e1); o.put("e2", e2)
        o.put("r", farr(floatArrayOf(t0, t1, shadeA, shadeB, tangentAt, solX, solY).map { if (it.isNaN()) -9.99e30f else it }.toFloatArray(), precise = true))
        o.put("pr", params); o.put("d", farr(data, precise = true)); o.put("fit", fit); o.put("m", farr(m, precise = true)); o.put("sl", showLabel)
        o.put("lb", label); o.put("ds", dash)
    }

    override fun read(o: JSONObject) {
        axesId = o.optLong("ax"); kind = o.optInt("k"); e1 = o.optString("e1"); e2 = o.optString("e2")
        val r = rfarr(o.optJSONArray("r")).map { if (it < -9e30f) Float.NaN else it }
        if (r.size >= 7) { t0 = r[0]; t1 = r[1]; shadeA = r[2]; shadeB = r[3]; tangentAt = r[4]; solX = r[5]; solY = r[6] }
        params = o.optString("pr"); data = rfarr(o.optJSONArray("d")); fit = o.optInt("fit")
        m = rfarr(o.optJSONArray("m")).let { if (it.size == 4) it else floatArrayOf(1f, 0f, 0f, 1f) }
        showLabel = o.optBoolean("sl", true); label = o.optString("lb"); dash = o.optBoolean("ds")
        invalidateCache()
    }
}
