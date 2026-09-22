package com.paintphymath

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Контекст отрисовки: документ (для связей и осей), цвет бумаги. */
class DrawCtx(val doc: Doc) {
    val paperColor get() = doc.paperColor
}

abstract class El {
    var id: Long = Ids.next()
    var color: Int = Color.BLACK
    var width: Float = 3f

    abstract val type: String

    /** 0 — слой маркеров (всегда под всем остальным), 1 — основной слой. */
    open val layer: Int get() = 1

    abstract fun draw(g: Gfx, ctx: DrawCtx)
    abstract fun bounds(ctx: DrawCtx): RectF
    open fun hit(x: Float, y: Float, tol: Float, ctx: DrawCtx): Boolean = bounds(ctx).let {
        RectF(it).apply { inset(-tol, -tol) }.contains(x, y)
    }
    abstract fun transform(m: Matrix)
    open fun snapPoints(ctx: DrawCtx): List<PointF> = emptyList()

    /** Элементы, зависящие от других (связи, графики), не двигаются сами по себе. */
    open val movable get() = true

    protected abstract fun write(o: JSONObject)
    protected abstract fun read(o: JSONObject)

    open fun invalidateCache() {}

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("t", type); o.put("id", id); o.put("c", color); o.put("w", width.toDouble())
        write(o)
        return o
    }

    fun copy(newId: Boolean = true): El {
        val e = fromJson(toJson())!!
        if (newId) e.id = Ids.next()
        return e
    }

    companion object {
        fun create(t: String): El? = when (t) {
            "stroke" -> StrokeEl()
            "line" -> LineEl()
            "circle" -> CircleEl()
            "poly" -> PolyEl()
            "text" -> TextEl()
            "point" -> PointEl()
            "angle" -> AngleEl()
            "axes" -> AxesEl()
            "plot" -> PlotEl()
            "stamp" -> StampEl()
            "link" -> LinkEl()
            "table" -> TableEl()
            "image" -> ImageEl()
            else -> null
        }

        fun fromJson(o: JSONObject): El? {
            val e = create(o.optString("t")) ?: return null
            e.id = o.optLong("id", Ids.next())
            e.color = o.optInt("c", Color.BLACK)
            e.width = o.optDouble("w", 3.0).toFloat()
            e.read(o)
            return e
        }

        fun farr(a: FloatArray, n: Int = a.size, precise: Boolean = false): JSONArray {
            val j = JSONArray()
            for (i in 0 until n) {
                val v = a[i]
                when {
                    v.isNaN() || v.isInfinite() -> j.put(0.0)
                    precise || abs(v) > 1e6f -> j.put(v.toDouble())
                    else -> j.put(Math.round(v * 100.0) / 100.0)
                }
            }
            return j
        }

        fun rfarr(j: JSONArray?): FloatArray {
            if (j == null) return FloatArray(0)
            return FloatArray(j.length()) { j.optDouble(it, 0.0).toFloat() }
        }

        fun withAlpha(c: Int, a: Float) = (c and 0xFFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

        fun arrowHead(g: Gfx, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, w: Float) {
            val a = atan2(y2 - y1, x2 - x1)
            val len = 7f + w * 3.2f
            val spread = 0.42f
            val p = GPath()
            p.moveTo(x2, y2)
            p.lineTo(x2 - len * cos(a - spread), y2 - len * sin(a - spread))
            p.lineTo(x2 - len * 0.72f * cos(a), y2 - len * 0.72f * sin(a))
            p.lineTo(x2 - len * cos(a + spread), y2 - len * sin(a + spread))
            p.close()
            g.path(p, color, w * 0.6f, color)
        }

        /** Подпись-формула с центром в (cx, cy). */
        fun label(g: Gfx, s: String, cx: Float, cy: Float, size: Float, color: Int, bg: Int = 0) {
            if (s.isBlank()) return
            val b = MathText.layout(s, size)
            val h = b.asc + b.desc
            val x = cx - b.w / 2
            val y = cy - h / 2
            if (bg != 0) {
                val p = GPath()
                val r = RectF(x - 4, y - 2, x + b.w + 4, y + h + 2)
                roundRect(p, r, 6f)
                g.path(p, 0, 0f, bg)
            }
            b.draw(g, x, y + b.asc, color)
        }

        fun labelSize(s: String, size: Float): PointF {
            val b = MathText.layout(s, size)
            return PointF(b.w, b.asc + b.desc)
        }

        fun roundRect(p: GPath, r: RectF, rad: Float) {
            val k = rad * 0.4477f
            p.moveTo(r.left + rad, r.top)
            p.lineTo(r.right - rad, r.top)
            p.cubicTo(r.right - k, r.top, r.right, r.top + k, r.right, r.top + rad)
            p.lineTo(r.right, r.bottom - rad)
            p.cubicTo(r.right, r.bottom - k, r.right - k, r.bottom, r.right - rad, r.bottom)
            p.lineTo(r.left + rad, r.bottom)
            p.cubicTo(r.left + k, r.bottom, r.left, r.bottom - k, r.left, r.bottom - rad)
            p.lineTo(r.left, r.top + rad)
            p.cubicTo(r.left, r.top + k, r.left + k, r.top, r.left + rad, r.top)
            p.close()
        }

        fun fmtLen(px: Float, unit: Float, unitName: String): String {
            val v = px / max(unit, 1e-6f)
            return Expr.fmt(v.toDouble(), 2) + if (unitName.isNotEmpty()) " $unitName" else ""
        }
    }
}

// =====================================================================================
//  Штрих: ручка, карандаш, маркер
// =====================================================================================

class StrokeEl() : El() {
    override val type = "stroke"
    var pts = FloatArray(48)   // x, y, давление
    var n = 0
    /** 0 — ручка, 1 — карандаш, 2 — маркер (нижний слой), 3 — ручка без нажима */
    var kind = 0

    override val layer get() = if (kind == 2) 0 else 1

    @Transient private var outline: GPath? = null
    @Transient private var bnd: RectF? = null

    fun add(x: Float, y: Float, p: Float) {
        if (n * 3 + 3 > pts.size) pts = pts.copyOf(pts.size * 2)
        pts[n * 3] = x; pts[n * 3 + 1] = y; pts[n * 3 + 2] = p
        n++
        invalidateCache()
    }

    fun x(i: Int) = pts[i * 3]
    fun y(i: Int) = pts[i * 3 + 1]
    fun p(i: Int) = pts[i * 3 + 2]

    override fun invalidateCache() { outline = null; bnd = null }

    private fun radius(i: Int): Float {
        val pr = p(i).coerceIn(0f, 1f)
        return when (kind) {
            0 -> width * (0.22f + 0.95f * pr) / 2f
            1 -> width * (0.55f + 0.45f * pr) / 2f
            else -> width / 2f
        }
    }

    private fun centerline(): GPath {
        val p = GPath()
        if (n == 0) return p
        p.moveTo(x(0), y(0))
        if (n == 1) { p.lineTo(x(0) + 0.01f, y(0)); return p }
        for (i in 1 until n - 1) {
            val mx = (x(i) + x(i + 1)) / 2
            val my = (y(i) + y(i + 1)) / 2
            p.quadTo(x(i), y(i), mx, my)
        }
        p.lineTo(x(n - 1), y(n - 1))
        return p
    }

    /** Контур штриха переменной толщины — работает одинаково на Canvas и в SVG. */
    private fun buildOutline(): GPath {
        val p = GPath()
        if (n == 0) return p
        if (n == 1 || (n == 2 && Geo.dist(x(0), y(0), x(1), y(1)) < 0.5f)) {
            p.addCircle(x(0), y(0), max(radius(0), 0.6f))
            return p
        }
        val lx = FloatArray(n); val ly = FloatArray(n); val rx = FloatArray(n); val ry = FloatArray(n)
        val nx = FloatArray(n); val ny = FloatArray(n)
        for (i in 0 until n) {
            val a = max(0, i - 1); val b = min(n - 1, i + 1)
            var tx = x(b) - x(a); var ty = y(b) - y(a)
            val l = hypot(tx, ty)
            if (l < 1e-4f) { if (i > 0) { nx[i] = nx[i - 1]; ny[i] = ny[i - 1] } else { nx[i] = 0f; ny[i] = 1f } }
            else { tx /= l; ty /= l; nx[i] = -ty; ny[i] = tx }
            val r = max(radius(i), 0.4f)
            lx[i] = x(i) + nx[i] * r; ly[i] = y(i) + ny[i] * r
            rx[i] = x(i) - nx[i] * r; ry[i] = y(i) - ny[i] * r
        }
        p.moveTo(lx[0], ly[0])
        for (i in 1 until n - 1) p.quadTo(lx[i], ly[i], (lx[i] + lx[i + 1]) / 2, (ly[i] + ly[i + 1]) / 2)
        p.lineTo(lx[n - 1], ly[n - 1])
        val ae = atan2(ny[n - 1], nx[n - 1]).toDouble()
        p.addArc(x(n - 1), y(n - 1), max(radius(n - 1), 0.4f), max(radius(n - 1), 0.4f), 0f, ae, ae - PI, false)
        for (i in n - 2 downTo 1) p.quadTo(rx[i], ry[i], (rx[i] + rx[i - 1]) / 2, (ry[i] + ry[i - 1]) / 2)
        p.lineTo(rx[0], ry[0])
        val a0 = atan2(ny[0], nx[0]).toDouble()
        p.addArc(x(0), y(0), max(radius(0), 0.4f), max(radius(0), 0.4f), 0f, a0 + PI, a0, false)
        p.close()
        return p
    }

    override fun draw(g: Gfx, ctx: DrawCtx) {
        if (n == 0) return
        when (kind) {
            2 -> {
                val o = outline ?: centerline().also { outline = it }
                g.path(o, withAlpha(color, 0.38f), width, 0, 0f, false)
            }
            else -> {
                val o = outline ?: buildOutline().also { outline = it }
                val c = if (kind == 1) withAlpha(color, 0.82f) else color
                g.path(o, 0, 0f, c)
            }
        }
    }

    override fun bounds(ctx: DrawCtx): RectF {
        bnd?.let { return it }
        if (n == 0) return RectF()
        var l = x(0); var t = y(0); var r = l; var b = t
        for (i in 1 until n) { l = min(l, x(i)); r = max(r, x(i)); t = min(t, y(i)); b = max(b, y(i)) }
        val w = width / 2 + 1
        return RectF(l - w, t - w, r + w, b + w).also { bnd = it }
    }

    override fun hit(x: Float, y: Float, tol: Float, ctx: DrawCtx): Boolean {
        val bb = bounds(ctx)
        if (x < bb.left - tol || x > bb.right + tol || y < bb.top - tol || y > bb.bottom + tol) return false
        val r = tol + width / 2
        if (n == 1) return Geo.dist(x, y, x(0), y(0)) <= r
        for (i in 0 until n - 1) if (Geo.segDist(x, y, x(i), y(i), x(i + 1), y(i + 1)) <= r) return true
        return false
    }

    override fun transform(m: Matrix) {
        val a = FloatArray(2)
        for (i in 0 until n) {
            a[0] = x(i); a[1] = y(i); m.mapPoints(a)
            pts[i * 3] = a[0]; pts[i * 3 + 1] = a[1]
        }
        width *= Geo.scaleOf(m)
        invalidateCache()
    }

    /** Частичный ластик: возвращает куски штриха вне круга (x, y, r). */
    fun eraseAt(ex: Float, ey: Float, r: Float): List<StrokeEl>? {
        var any = false
        for (i in 0 until n) if (Geo.dist(ex, ey, x(i), y(i)) <= r + radius(i)) { any = true; break }
        if (!any) {
            // проверка отрезков между точками
            for (i in 0 until n - 1) if (Geo.segDist(ex, ey, x(i), y(i), x(i + 1), y(i + 1)) <= r) { any = true; break }
            if (!any) return null
        }
        val parts = ArrayList<StrokeEl>()
        var cur: StrokeEl? = null
        for (i in 0 until n) {
            val inside = Geo.dist(ex, ey, x(i), y(i)) <= r
            if (inside) { cur = null; continue }
            if (cur == null) {
                cur = StrokeEl().also { it.kind = kind; it.color = color; it.width = width; parts.add(it) }
            }
            cur.add(x(i), y(i), p(i))
        }
        return parts.filter { it.n >= 2 || (n == 1 && it.n == 1) }
    }

    override fun write(o: JSONObject) {
        o.put("k", kind)
        o.put("p", farr(pts, n * 3))
    }

    override fun read(o: JSONObject) {
        kind = o.optInt("k", 0)
        pts = rfarr(o.optJSONArray("p"))
        n = pts.size / 3
        if (pts.isEmpty()) pts = FloatArray(48)
        invalidateCache()
    }
}

// =====================================================================================
//  Отрезок / стрелка / вектор (инструмент «Линейка»)
// =====================================================================================

class LineEl() : El() {
    override val type = "line"
    var x1 = 0f; var y1 = 0f; var x2 = 0f; var y2 = 0f
    var arrowEnd = false
    var arrowStart = false
    var dash = false
    var showLen = false
    /** Прямая (продолжается за концы) */
    var infinite = false
    var unit = 37.8f
    var unitName = "см"
    var label = ""

    override fun draw(g: Gfx, ctx: DrawCtx) {
        var ax = x1; var ay = y1; var bx = x2; var by = y2
        if (infinite) {
            val dx = x2 - x1; val dy = y2 - y1
            val l = hypot(dx, dy).coerceAtLeast(1e-3f)
            val ext = 4000f / l
            ax = x1 - dx * ext; ay = y1 - dy * ext; bx = x2 + dx * ext; by = y2 + dy * ext
        }
        g.line(ax, ay, bx, by, color, width, if (dash) width * 3.5f + 4f else 0f)
        if (arrowEnd) arrowHead(g, x1, y1, x2, y2, color, width)
        if (arrowStart) arrowHead(g, x2, y2, x1, y1, color, width)
        val len = Geo.dist(x1, y1, x2, y2)
        if (len < 1f) return
        val nx = -(y2 - y1) / len
        val ny = (x2 - x1) / len
        val mx = (x1 + x2) / 2
        val my = (y1 + y2) / 2
        // подпись над отрезком (сторона выбирается «вверх»)
        val side = if (ny > 0) -1f else 1f
        val fs = 15f + width * 1.5f
        if (label.isNotBlank()) {
            val sz = labelSize(label, fs)
            val off = sz.y / 2 + 6 + width
            label(g, label, mx + nx * off * side, my + ny * off * side, fs, color)
        }
        if (showLen) {
            val s = fmtLen(len, unit, unitName)
            val sz = labelSize(s, fs * 0.8f)
            val off = sz.y / 2 + 6 + width
            label(g, s, mx - nx * off * side, my - ny * off * side, fs * 0.8f, withAlpha(color, 0.85f), withAlpha(ctx.paperColor, 0.75f))
        }
    }

    override fun bounds(ctx: DrawCtx): RectF {
        val r = RectF(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))
        r.inset(-width - 8, -width - 8)
        if (label.isNotBlank() || showLen) r.inset(-24f, -24f)
        return r
    }

    override fun hit(x: Float, y: Float, tol: Float, ctx: DrawCtx) =
        Geo.segDist(x, y, x1, y1, x2, y2) <= tol + width / 2

    override fun transform(m: Matrix) {
        val a = floatArrayOf(x1, y1, x2, y2)
        m.mapPoints(a)
        x1 = a[0]; y1 = a[1]; x2 = a[2]; y2 = a[3]
        val s = Geo.scaleOf(m)
        width *= s
        unit *= s
    }

    override fun snapPoints(ctx: DrawCtx) = listOf(PointF(x1, y1), PointF(x2, y2), PointF((x1 + x2) / 2, (y1 + y2) / 2))

    override fun write(o: JSONObject) {
        o.put("p", farr(floatArrayOf(x1, y1, x2, y2)))
        o.put("ae", arrowEnd); o.put("as", arrowStart); o.put("d", dash); o.put("sl", showLen); o.put("inf", infinite)
        o.put("u", unit.toDouble()); o.put("un", unitName); o.put("lb", label)
    }

    override fun read(o: JSONObject) {
        val p = rfarr(o.optJSONArray("p"))
        if (p.size >= 4) { x1 = p[0]; y1 = p[1]; x2 = p[2]; y2 = p[3] }
        arrowEnd = o.optBoolean("ae"); arrowStart = o.optBoolean("as"); dash = o.optBoolean("d")
        showLen = o.optBoolean("sl"); infinite = o.optBoolean("inf")
        unit = o.optDouble("u", 37.8).toFloat(); unitName = o.optString("un", "см"); label = o.optString("lb", "")
    }
}

// =====================================================================================
//  Окружность / эллипс
// =====================================================================================

class CircleEl() : El() {
    override val type = "circle"
    var cx = 0f; var cy = 0f; var rx = 10f; var ry = 10f
    /** поворот в радианах */
    var rot = 0f
    var fill = 0
    var dash = false
    var showCenter = true
    var showRadius = false
    var unit = 37.8f
    var unitName = "см"
    var label = ""

    val isCircle get() = abs(rx - ry) < 0.5f

    override fun draw(g: Gfx, ctx: DrawCtx) {
        val p = GPath().apply { addEllipse(cx, cy, rx, ry, rot) }
        g.path(p, color, width, fill, if (dash) width * 3.5f + 4f else 0f)
        if (showCenter) g.circle(cx, cy, 1.6f + width * 0.6f, 0, 0f, color)
        if (showRadius && isCircle) {
            val a = -PI.toFloat() / 5
            val ex = cx + rx * cos(a); val ey = cy + rx * sin(a)
            g.line(cx, cy, ex, ey, color, max(1f, width * 0.6f), 6f)
            val s = "r = " + fmtLen(rx, unit, unitName)
            label(g, s, (cx + ex) / 2 + 14, (cy + ey) / 2 - 14, 13f + width, color, withAlpha(ctx.paperColor, 0.75f))
        }
        if (label.isNotBlank()) {
            val a = -PI.toFloat() * 0.75f
            label(g, label, cx + (rx + 14 + width) * cos(a), cy + (ry + 14 + width) * sin(a), 16f + width * 1.5f, color)
        }
    }

    override fun bounds(ctx: DrawCtx): RectF {
        val r = max(rx, ry) + width + 4
        return RectF(cx - r, cy - r, cx + r, cy + r).also { if (label.isNotBlank()) it.inset(-30f, -30f) }
    }

    override fun hit(x: Float, y: Float, tol: Float, ctx: DrawCtx): Boolean {
        val c = cos(-rot); val s = sin(-rot)
        val dx = x - cx; val dy = y - cy
        val lx = dx * c - dy * s; val ly = dx * s + dy * c
        val k = sqrt((lx / rx) * (lx / rx) + (ly / ry) * (ly / ry))
        if (fill != 0 && k <= 1f) return true
        return abs(k - 1f) * min(rx, ry) <= tol + width / 2 || (showCenter && Geo.dist(x, y, cx, cy) < tol)
    }

    override fun transform(m: Matrix) {
        val a = floatArrayOf(cx, cy, cx + rx * cos(rot), cy + rx * sin(rot), cx - ry * sin(rot), cy + ry * cos(rot))
        m.mapPoints(a)
        cx = a[0]; cy = a[1]
        rx = Geo.dist(a[0], a[1], a[2], a[3])
        ry = Geo.dist(a[0], a[1], a[4], a[5])
        rot = atan2(a[3] - a[1], a[2] - a[0])
        val s = Geo.scaleOf(m)
        width *= s; unit *= s
    }

    override fun snapPoints(ctx: DrawCtx): List<PointF> {
        val l = arrayListOf(PointF(cx, cy))
        for (k in 0 until 4) {
            val a = rot + k * PI.toFloat() / 2
            val r = if (k % 2 == 0) rx else ry
            l.add(PointF(cx + r * cos(a), cy + r * sin(a)))
        }
        return l
    }

    override fun write(o: JSONObject) {
        o.put("p", farr(floatArrayOf(cx, cy, rx, ry, rot)))
        o.put("f", fill); o.put("d", dash); o.put("sc", showCenter); o.put("sr", showRadius)
        o.put("u", unit.toDouble()); o.put("un", unitName); o.put("lb", label)
    }

    override fun read(o: JSONObject) {
        val p = rfarr(o.optJSONArray("p"))
        if (p.size >= 5) { cx = p[0]; cy = p[1]; rx = p[2]; ry = p[3]; rot = p[4] }
        fill = o.optInt("f"); dash = o.optBoolean("d"); showCenter = o.optBoolean("sc", true)
        showRadius = o.optBoolean("sr"); unit = o.optDouble("u", 37.8).toFloat(); unitName = o.optString("un", "см")
        label = o.optString("lb", "")
    }
}

// =====================================================================================
//  Многоугольник / ломаная
// =====================================================================================

class PolyEl() : El() {
    override val type = "poly"
    var pts = FloatArray(0)
    var closed = true
    var fill = 0
    var dash = false
    /** Подписи вершин через запятую: «A,B,C» */
    var names = ""

    val count get() = pts.size / 2

    override fun draw(g: Gfx, ctx: DrawCtx) {
        if (count < 2) return
        val p = GPath()
        p.moveTo(pts[0], pts[1])
        for (i in 1 until count) p.lineTo(pts[i * 2], pts[i * 2 + 1])
        if (closed) p.close()
        g.path(p, color, width, fill, if (dash) width * 3.5f + 4f else 0f)
        if (names.isNotBlank()) {
            val nm = names.split(',').map { it.trim() }
            var cxs = 0f; var cys = 0f
            for (i in 0 until count) { cxs += pts[i * 2]; cys += pts[i * 2 + 1] }
            cxs /= count; cys /= count
            for (i in 0 until min(count, nm.size)) {
                if (nm[i].isEmpty()) continue
                val vx = pts[i * 2] - cxs; val vy = pts[i * 2 + 1] - cys
                val l = hypot(vx, vy).coerceAtLeast(1f)
                label(g, nm[i], pts[i * 2] + vx / l * 16, pts[i * 2 + 1] + vy / l * 16, 17f + width, color)
            }
        }
    }

    override fun bounds(ctx: DrawCtx): RectF {
        if (count == 0) return RectF()
        val r = RectF(pts[0], pts[1], pts[0], pts[1])
        for (i in 1 until count) r.union(pts[i * 2], pts[i * 2 + 1])
        r.inset(-width - 4, -width - 4)
        if (names.isNotBlank()) r.inset(-26f, -26f)
        return r
    }

    override fun hit(x: Float, y: Float, tol: Float, ctx: DrawCtx): Boolean {
        val segs = if (closed) count else count - 1
        for (i in 0 until segs) {
            val j = (i + 1) % count
            if (Geo.segDist(x, y, pts[i * 2], pts[i * 2 + 1], pts[j * 2], pts[j * 2 + 1]) <= tol + width / 2) return true
        }
        return fill != 0 && Geo.pointInPoly(x, y, pts, count)
    }

    override fun transform(m: Matrix) {
        m.mapPoints(pts)
        width *= Geo.scaleOf(m)
    }

    override fun snapPoints(ctx: DrawCtx) = (0 until count).map { PointF(pts[it * 2], pts[it * 2 + 1]) }

    override fun write(o: JSONObject) {
        o.put("p", farr(pts)); o.put("cl", closed); o.put("f", fill); o.put("d", dash); o.put("nm", names)
    }

    override fun read(o: JSONObject) {
        pts = rfarr(o.optJSONArray("p")); closed = o.optBoolean("cl", true); fill = o.optInt("f")
        dash = o.optBoolean("d"); names = o.optString("nm", "")
    }
}

// =====================================================================================
//  Текст / название / формула / заметка
// =====================================================================================

class TextEl() : El() {
    override val type = "text"
    var x = 0f; var y = 0f
    var text = ""
    var size = 22f
    /** 0 — текст, 1 — формула, 2 — заголовок */
    var mode = 1
    /** фон-стикер (0 — без фона) */
    var bg = 0

    @Transient private var cw = -1f
    @Transient private var ch = 0f

    private fun measure() {
        if (cw >= 0) return
        when (mode) {
            1 -> { val b = MathText.layout(text, size); cw = b.w; ch = b.asc + b.desc }
            else -> {
                val lines = text.split('\n')
                val f = if (mode == 2) Font.SANS_BOLD else Font.SANS
                cw = lines.maxOf { TextMetrics.width(it, size, f) }
                ch = lines.size * size * 1.3f
            }
        }
    }

    override fun invalidateCache() { cw = -1f }

    override fun draw(g: Gfx, ctx: DrawCtx) {
        measure()
        if (bg != 0) {
            val p = GPath()
            roundRect(p, RectF(x - 10, y - 8, x + cw + 10, y + ch + 8), 10f)
            g.path(p, 0, 0f, bg)
        }
        when (mode) {
            1 -> MathText.draw(g, text, x, y, size, color)
            else -> {
                val f = if (mode == 2) Font.SANS_BOLD else Font.SANS
                text.split('\n').forEachIndexed { i, line ->
                    g.text(line, x, y + size * 0.95f + i * size * 1.3f, size, color, f)
                }
                if (mode == 2) g.line(x, y + ch + 3, x + cw, y + ch + 3, withAlpha(color, 0.5f), max(1.5f, size / 12f))
            }
        }
    }

    override fun bounds(ctx: DrawCtx): RectF {
        measure()
        val pad = if (bg != 0) 10f else 2f
        return RectF(x - pad, y - pad, x + cw + pad, y + ch + pad + (if (mode == 2) 6 else 0))
    }

    override fun transform(m: Matrix) {
        val p = Geo.mapPt(m, x, y)
        x = p.x; y = p.y
        size = (size * Geo.scaleOf(m)).coerceIn(4f, 600f)
        invalidateCache()
    }

    override fun write(o: JSONObject) {
        o.put("x", x.toDouble()); o.put("y", y.toDouble()); o.put("tx", text); o.put("s", size.toDouble())
        o.put("m", mode); o.put("bg", bg)
    }

    override fun read(o: JSONObject) {
        x = o.optDouble("x").toFloat(); y = o.optDouble("y").toFloat(); text = o.optString("tx")
        size = o.optDouble("s", 22.0).toFloat(); mode = o.optInt("m", 1); bg = o.optInt("bg")
        invalidateCache()
    }
}

// =====================================================================================
//  Точка с именем и координатами
// =====================================================================================

class PointEl() : El() {
    override val type = "point"
    var x = 0f; var y = 0f
    var name = ""
    var showCoords = true

    fun caption(ctx: DrawCtx): String {
        val ax = ctx.doc.axesAt(x, y)
        if (!showCoords || ax == null) return name
        val u = ax.toUnits(x, y)
        return name + "(" + Expr.fmt(u.x.toDouble(), 2) + "; " + Expr.fmt(u.y.toDouble(), 2) + ")"
    }

    override fun draw(g: Gfx, ctx: DrawCtx) {
        val r = 2.5f + width * 0.9f
        g.circle(x, y, r, withAlpha(ctx.paperColor, 1f), 1.5f, color)
        val c = caption(ctx)
        if (c.isNotBlank()) {
            val sz = labelSize(c, 17f)
            label(g, c, x + r + 4 + sz.x / 2, y - r - 2 - sz.y / 2, 17f, color)
        }
    }

    override fun bounds(ctx: DrawCtx): RectF {
        val r = 4f + width
        val sz = labelSize(caption(ctx), 17f)
        return RectF(x - r, y - r - sz.y - 4, x + r + sz.x + 6, y + r)
    }

    override fun hit(x: Float, y: Float, tol: Float, ctx: DrawCtx) = Geo.dist(x, y, this.x, this.y) <= tol + 4 + width

    override fun transform(m: Matrix) {
        val p = Geo.mapPt(m, x, y); x = p.x; y = p.y
    }

    override fun snapPoints(ctx: DrawCtx) = listOf(PointF(x, y))

    override fun write(o: JSONObject) {
        o.put("x", x.toDouble()); o.put("y", y.toDouble()); o.put("nm", name); o.put("sc", showCoords)
    }

    override fun read(o: JSONObject) {
        x = o.optDouble("x").toFloat(); y = o.optDouble("y").toFloat(); name = o.optString("nm"); showCoords = o.optBoolean("sc", true)
    }
}

// =====================================================================================
//  Угол (3 точки) с величиной
// =====================================================================================

class AngleEl() : El() {
    override val type = "angle"
    var ax = 0f; var ay = 0f; var vx = 0f; var vy = 0f; var bx = 0f; var by = 0f
    var showValue = true
    var label = ""
    var drawSides = true

    fun degrees(): Float {
        val a1 = atan2(ay - vy, ax - vx)
        val a2 = atan2(by - vy, bx - vx)
        var d = Math.toDegrees((a2 - a1).toDouble()).toFloat()
        while (d < 0) d += 360f
        while (d >= 360f) d -= 360f
        return if (d > 180f) 360f - d else d
    }

    override fun draw(g: Gfx, ctx: DrawCtx) {
        if (drawSides) {
            val p = GPath(); p.moveTo(ax, ay); p.lineTo(vx, vy); p.lineTo(bx, by)
            g.path(p, color, width)
        }
        val a1 = atan2(ay - vy, ax - vx).toDouble()
        val a2 = atan2(by - vy, bx - vx).toDouble()
        var sweep = a2 - a1
        while (sweep > PI) sweep -= 2 * PI
        while (sweep < -PI) sweep += 2 * PI
        val r = 26f + width * 2
        val deg = degrees()
        val ac = Color.rgb(220, 60, 60).let { if (color == Color.BLACK) it else color }
        if (abs(deg - 90f) < 0.6f) {
            val s = r * 0.6f
            val u1x = cos(a1).toFloat(); val u1y = sin(a1).toFloat()
            val u2x = cos(a2).toFloat(); val u2y = sin(a2).toFloat()
            val p = GPath()
            p.moveTo(vx + u1x * s, vy + u1y * s)
            p.lineTo(vx + (u1x + u2x) * s, vy + (u1y + u2y) * s)
            p.lineTo(vx + u2x * s, vy + u2y * s)
            g.path(p, ac, max(1.2f, width * 0.7f))
        } else {
            val p = GPath()
            p.addArc(vx, vy, r, r, 0f, a1, a1 + sweep, true)
            g.path(p, ac, max(1.2f, width * 0.7f), withAlpha(ac, 0f))
        }
        val mid = a1 + sweep / 2
        val text = buildString {
            if (label.isNotBlank()) append(label)
            if (showValue) { if (isNotEmpty()) append(" = "); append(Expr.fmt(deg.toDouble(), 1)); append("°") }
        }
        if (text.isNotEmpty()) {
            val sz = labelSize(text, 15f)
            val rr = r + 8 + max(sz.x, sz.y) / 2
            label(g, text, vx + rr * cos(mid).toFloat(), vy + rr * sin(mid).toFloat(), 15f, ac)
        }
    }

    override fun bounds(ctx: DrawCtx): RectF {
        val r = RectF(min(ax, min(vx, bx)), min(ay, min(vy, by)), max(ax, max(vx, bx)), max(ay, max(vy, by)))
        r.inset(-60f, -60f)
        return r
    }

    override fun hit(x: Float, y: Float, tol: Float, ctx: DrawCtx) =
        Geo.segDist(x, y, ax, ay, vx, vy) <= tol + width || Geo.segDist(x, y, vx, vy, bx, by) <= tol + width ||
            Geo.dist(x, y, vx, vy) < 40f

    override fun transform(m: Matrix) {
        val a = floatArrayOf(ax, ay, vx, vy, bx, by)
        m.mapPoints(a)
        ax = a[0]; ay = a[1]; vx = a[2]; vy = a[3]; bx = a[4]; by = a[5]
    }

    override fun snapPoints(ctx: DrawCtx) = listOf(PointF(ax, ay), PointF(vx, vy), PointF(bx, by))

    override fun write(o: JSONObject) {
        o.put("p", farr(floatArrayOf(ax, ay, vx, vy, bx, by))); o.put("sv", showValue); o.put("lb", label); o.put("ds", drawSides)
    }

    override fun read(o: JSONObject) {
        val p = rfarr(o.optJSONArray("p"))
        if (p.size >= 6) { ax = p[0]; ay = p[1]; vx = p[2]; vy = p[3]; bx = p[4]; by = p[5] }
        showValue = o.optBoolean("sv", true); label = o.optString("lb"); drawSides = o.optBoolean("ds", true)
    }
}

// =====================================================================================
//  Связь между элементами (стрелка, следующая за объектами)
// =====================================================================================

class LinkEl() : El() {
    override val type = "link"
    var from = 0L
    var to = 0L
    var label = ""
    /** 0 — стрелка, 1 — линия, 2 — двойная стрелка, 3 — пунктирная стрелка */
    var style = 0
    var bend = 0f

    override val movable get() = false

    private fun anchor(e: El, tx: Float, ty: Float, ctx: DrawCtx): PointF {
        val b = e.bounds(ctx)
        val cx = b.centerX(); val cy = b.centerY()
        val dx = tx - cx; val dy = ty - cy
        if (abs(dx) < 1e-3f && abs(dy) < 1e-3f) return PointF(cx, cy)
        val hw = b.width() / 2 + 4; val hh = b.height() / 2 + 4
        val k = min(if (dx != 0f) hw / abs(dx) else Float.MAX_VALUE, if (dy != 0f) hh / abs(dy) else Float.MAX_VALUE)
        return PointF(cx + dx * k, cy + dy * k)
    }

    /** Геометрия связи: начало, контрольная точка, конец. */
    fun geometry(ctx: DrawCtx): FloatArray? {
        val a = ctx.doc.find(from) ?: return null
        val b = ctx.doc.find(to) ?: return null
        val ba = a.bounds(ctx); val bb = b.bounds(ctx)
        val mxc = (ba.centerX() + bb.centerX()) / 2
        val myc = (ba.centerY() + bb.centerY()) / 2
        val dx = bb.centerX() - ba.centerX(); val dy = bb.centerY() - ba.centerY()
        val l = hypot(dx, dy).coerceAtLeast(1f)
        val cxp = mxc - dy / l * bend
        val cyp = myc + dx / l * bend
        val p1 = anchor(a, cxp, cyp, ctx)
        val p2 = anchor(b, cxp, cyp, ctx)
        return floatArrayOf(p1.x, p1.y, cxp, cyp, p2.x, p2.y)
    }

    override fun draw(g: Gfx, ctx: DrawCtx) {
        val q = geometry(ctx) ?: return
        val p = GPath()
        p.moveTo(q[0], q[1])
        // контрольная точка квадратичной кривой проходит через (cxp, cyp)
        val kx = 2 * q[2] - (q[0] + q[4]) / 2
        val ky = 2 * q[3] - (q[1] + q[5]) / 2
        p.quadTo(kx, ky, q[4], q[5])
        g.path(p, color, width, 0, if (style == 3) width * 3 + 4 else 0f)
        if (style == 0 || style == 2 || style == 3) arrowHead(g, kx, ky, q[4], q[5], color, width)
        if (style == 2) arrowHead(g, kx, ky, q[0], q[1], color, width)
        if (label.isNotBlank()) label(g, label, q[2], q[3], 15f + width, color, withAlpha(ctx.paperColor, 0.9f))
    }

    override fun bounds(ctx: DrawCtx): RectF {
        val q = geometry(ctx) ?: return RectF()
        val r = RectF(min(q[0], q[4]), min(q[1], q[5]), max(q[0], q[4]), max(q[1], q[5]))
        r.union(q[2], q[3])
        r.inset(-16f, -16f)
        return r
    }

    override fun hit(x: Float, y: Float, tol: Float, ctx: DrawCtx): Boolean {
        val q = geometry(ctx) ?: return false
        val kx = 2 * q[2] - (q[0] + q[4]) / 2
        val ky = 2 * q[3] - (q[1] + q[5]) / 2
        var px = q[0]; var py = q[1]
        for (i in 1..20) {
            val t = i / 20f
            val u = 1 - t
            val nx = u * u * q[0] + 2 * u * t * kx + t * t * q[4]
            val ny = u * u * q[1] + 2 * u * t * ky + t * t * q[5]
            if (Geo.segDist(x, y, px, py, nx, ny) <= tol + width) return true
            px = nx; py = ny
        }
        return false
    }

    override fun transform(m: Matrix) {}

    override fun write(o: JSONObject) {
        o.put("f", from); o.put("to", to); o.put("lb", label); o.put("st", style); o.put("bd", bend.toDouble())
    }

    override fun read(o: JSONObject) {
        from = o.optLong("f"); to = o.optLong("to"); label = o.optString("lb"); style = o.optInt("st")
        bend = o.optDouble("bd", 0.0).toFloat()
    }
}

// =====================================================================================
//  Таблица (значения, измерения)
// =====================================================================================

class TableEl() : El() {
    override val type = "table"
    var x = 0f; var y = 0f
    var cells: MutableList<MutableList<String>> = mutableListOf()
    var size = 18f
    var header = true

    @Transient private var colW: FloatArray? = null
    @Transient private var rowH = 0f

    private fun measure() {
        if (colW != null) return
        val nc = cells.maxOfOrNull { it.size } ?: 0
        val cw = FloatArray(nc) { size * 2f }
        var rh = size * 1.6f
        for (r in cells) for ((c, s) in r.withIndex()) {
            val b = MathText.layout(s, size)
            cw[c] = max(cw[c], b.w + size * 1.1f)
            rh = max(rh, b.asc + b.desc + size * 0.7f)
        }
        colW = cw; rowH = rh
    }

    override fun invalidateCache() { colW = null }

    override fun draw(g: Gfx, ctx: DrawCtx) {
        measure()
        val cw = colW ?: return
        val tw = cw.sum()
        val th = rowH * cells.size
        if (header && cells.isNotEmpty()) {
            val p = GPath(); p.moveTo(x, y); p.lineTo(x + tw, y); p.lineTo(x + tw, y + rowH); p.lineTo(x, y + rowH); p.close()
            g.path(p, 0, 0f, withAlpha(color, 0.08f))
        }
        val lw = max(1f, width * 0.5f)
        for (r in 0..cells.size) g.line(x, y + r * rowH, x + tw, y + r * rowH, color, if (r == 1 && header) lw * 1.8f else lw)
        var cx = x
        for (c in 0..cw.size) {
            g.line(cx, y, cx, y + th, color, lw)
            if (c < cw.size) cx += cw[c]
        }
        for ((ri, row) in cells.withIndex()) {
            var xx = x
            for ((ci, s) in row.withIndex()) {
                val b = MathText.layout(s, size)
                val h = b.asc + b.desc
                b.draw(g, xx + (cw[ci] - b.w) / 2, y + ri * rowH + (rowH - h) / 2 + b.asc, color)
                xx += cw[ci]
            }
        }
    }

    override fun bounds(ctx: DrawCtx): RectF {
        measure()
        return RectF(x - 2, y - 2, x + (colW?.sum() ?: 0f) + 2, y + rowH * cells.size + 2)
    }

    override fun transform(m: Matrix) {
        val p = Geo.mapPt(m, x, y); x = p.x; y = p.y
        size = (size * Geo.scaleOf(m)).coerceIn(4f, 300f)
        invalidateCache()
    }

    override fun write(o: JSONObject) {
        o.put("x", x.toDouble()); o.put("y", y.toDouble()); o.put("s", size.toDouble()); o.put("h", header)
        val a = JSONArray()
        for (r in cells) a.put(JSONArray(r))
        o.put("cells", a)
    }

    override fun read(o: JSONObject) {
        x = o.optDouble("x").toFloat(); y = o.optDouble("y").toFloat(); size = o.optDouble("s", 18.0).toFloat()
        header = o.optBoolean("h", true)
        val a = o.optJSONArray("cells") ?: JSONArray()
        cells = MutableList(a.length()) { i ->
            val r = a.optJSONArray(i) ?: JSONArray()
            MutableList(r.length()) { r.optString(it) }
        }
        invalidateCache()
    }

    fun toCsv(): String = cells.joinToString("\n") { it.joinToString(" | ") }

    companion object {
        fun parseCsv(s: String): MutableList<MutableList<String>> =
            s.trim().split('\n').filter { it.isNotBlank() }.map { line ->
                val sep = when { line.contains('|') -> "|"; line.contains('\t') -> "\t"; line.contains(';') -> ";"; else -> "|" }
                line.split(sep).map { it.trim() }.toMutableList()
            }.toMutableList()
    }
}

// =====================================================================================
//  Изображение
// =====================================================================================

class ImageEl() : El() {
    override val type = "image"
    var x = 0f; var y = 0f; var w = 100f; var h = 100f
    var data = ""

    @Transient private var bmp: Bitmap? = null

    fun bitmap(): Bitmap? {
        if (bmp == null && data.isNotEmpty()) {
            try {
                val bytes = Base64.decode(data, Base64.DEFAULT)
                bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) { }
        }
        return bmp
    }

    override fun draw(g: Gfx, ctx: DrawCtx) {
        val b = bitmap() ?: return
        g.image(b, RectF(x, y, x + w, y + h), data)
    }

    override fun bounds(ctx: DrawCtx) = RectF(x, y, x + w, y + h)

    override fun transform(m: Matrix) {
        val a = floatArrayOf(x, y, x + w, y + h)
        m.mapPoints(a)
        x = min(a[0], a[2]); y = min(a[1], a[3]); w = abs(a[2] - a[0]); h = abs(a[3] - a[1])
    }

    override fun write(o: JSONObject) {
        o.put("r", farr(floatArrayOf(x, y, w, h))); o.put("d", data)
    }

    override fun read(o: JSONObject) {
        val r = rfarr(o.optJSONArray("r"))
        if (r.size >= 4) { x = r[0]; y = r[1]; w = r[2]; h = r[3] }
        data = o.optString("d"); bmp = null
    }
}
