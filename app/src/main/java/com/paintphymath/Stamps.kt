package com.paintphymath

import android.graphics.Matrix
import android.graphics.RectF
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Шаблон (физика, электрические схемы, стереометрия). */
class StampEl() : El() {
    override val type = "stamp"
    var kind = "block"
    var cx = 0f; var cy = 0f; var w = 120f; var h = 80f
    /** градусы */
    var rot = 0f
    var label = ""

    init { width = 2.4f }

    override fun draw(g: Gfx, ctx: DrawCtx) {
        g.save()
        g.translate(cx, cy)
        if (rot != 0f) g.rotate(rot, 0f, 0f)
        Stamps.draw(kind, g, w, h, color, width, ctx.paperColor)
        g.restore()
        if (label.isNotBlank()) {
            val sz = labelSize(label, 16f + width)
            val bb = bounds(ctx)
            label(g, label, cx, bb.top + sz.y / 2, 16f + width, color)
        }
    }

    override fun bounds(ctx: DrawCtx): RectF {
        val a = Math.toRadians(rot.toDouble())
        val c = abs(cos(a)).toFloat(); val s = abs(sin(a)).toFloat()
        val bw = w * c + h * s; val bh = w * s + h * c
        val r = RectF(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2)
        r.inset(-width - 4, -width - 4)
        if (label.isNotBlank()) r.top -= 30
        return r
    }

    override fun transform(m: Matrix) {
        val p = Geo.mapPt(m, cx, cy)
        cx = p.x; cy = p.y
        val s = Geo.scaleOf(m)
        w *= s; h *= s
        rot += Geo.rotationOf(m)
        width = (width * s).coerceIn(0.5f, 40f)
    }

    override fun write(o: JSONObject) {
        o.put("k", kind); o.put("r", farr(floatArrayOf(cx, cy, w, h, rot))); o.put("lb", label)
    }

    override fun read(o: JSONObject) {
        kind = o.optString("k", "block")
        val r = rfarr(o.optJSONArray("r"))
        if (r.size >= 5) { cx = r[0]; cy = r[1]; w = r[2]; h = r[3]; rot = r[4] }
        label = o.optString("lb")
    }
}

object Stamps {
    /** kind → (название, категория, пропорции w:h) */
    data class Def(val kind: String, val title: String, val cat: String, val w: Float, val h: Float)

    val ALL = listOf(
        Def("block", "Брусок", "Механика", 110f, 70f),
        Def("incline", "Наклонная плоскость", "Механика", 220f, 120f),
        Def("spring", "Пружина", "Механика", 180f, 44f),
        Def("pulley", "Блок", "Механика", 90f, 150f),
        Def("pendulum", "Маятник", "Механика", 140f, 190f),
        Def("cart", "Тележка", "Механика", 150f, 80f),
        Def("ground", "Опора / пол", "Механика", 220f, 26f),
        Def("wall", "Стена", "Механика", 26f, 200f),
        Def("ball", "Шар / тело", "Механика", 70f, 70f),
        Def("forces", "Силы на тело", "Механика", 200f, 200f),
        Def("resistor", "Резистор", "Электричество", 120f, 36f),
        Def("capacitor", "Конденсатор", "Электричество", 70f, 80f),
        Def("battery", "Источник тока", "Электричество", 70f, 80f),
        Def("lamp", "Лампа", "Электричество", 70f, 70f),
        Def("switch", "Ключ", "Электричество", 120f, 50f),
        Def("ammeter", "Амперметр", "Электричество", 70f, 70f),
        Def("voltmeter", "Вольтметр", "Электричество", 70f, 70f),
        Def("inductor", "Катушка", "Электричество", 140f, 40f),
        Def("diode", "Диод", "Электричество", 100f, 50f),
        Def("earth", "Заземление", "Электричество", 60f, 60f),
        Def("charge_p", "Заряд +", "Электричество", 60f, 60f),
        Def("charge_n", "Заряд −", "Электричество", 60f, 60f),
        Def("lens", "Собирающая линза", "Оптика", 50f, 200f),
        Def("lens_d", "Рассеивающая линза", "Оптика", 50f, 200f),
        Def("mirror", "Зеркало", "Оптика", 30f, 200f),
        Def("prism", "Призма", "Оптика", 140f, 120f),
        Def("axes3d", "Оси XYZ", "Стереометрия", 200f, 200f),
        Def("cube", "Параллелепипед", "Стереометрия", 170f, 150f),
        Def("pyramid", "Пирамида", "Стереометрия", 170f, 160f),
        Def("cylinder", "Цилиндр", "Стереометрия", 120f, 170f),
        Def("cone", "Конус", "Стереометрия", 130f, 170f),
        Def("sphere", "Шар (сфера)", "Стереометрия", 160f, 160f),
    )

    fun def(kind: String) = ALL.firstOrNull { it.kind == kind } ?: ALL[0]

    fun draw(kind: String, g: Gfx, w: Float, h: Float, c: Int, lw: Float, paper: Int) {
        val l = -w / 2; val t = -h / 2; val r = w / 2; val b = h / 2
        val thin = max(1f, lw * 0.55f)
        val dashC = El.withAlpha(c, 0.7f)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, ww: Float = lw, dash: Float = 0f) = g.line(x1, y1, x2, y2, c, ww, dash)
        fun hatch(x1: Float, y1: Float, x2: Float, y2: Float, below: Boolean) {
            line(x1, y1, x2, y2)
            val len = Geo.dist(x1, y1, x2, y2)
            val n = max(2, (len / 14).toInt())
            val dx = (x2 - x1) / n; val dy = (y2 - y1) / n
            val nx = -(y2 - y1) / len * 12 * (if (below) -1 else 1)
            val ny = (x2 - x1) / len * 12 * (if (below) -1 else 1)
            for (i in 0..n) {
                val px = x1 + dx * i; val py = y1 + dy * i
                line(px, py, px + nx - dx * 0.6f, py + ny - dy * 0.6f, thin)
            }
        }
        fun txt(s: String, x: Float, y: Float, size: Float) = El.label(g, s, x, y, size, c)
        fun circle(x: Float, y: Float, rr: Float, fill: Int = 0) = g.circle(x, y, rr, c, lw, fill)

        when (kind) {
            "block" -> {
                val p = GPath(); El.roundRect(p, RectF(l, t, r, b), 4f)
                g.path(p, c, lw, El.withAlpha(c, 0.08f))
                txt("m", 0f, 0f, min(w, h) * 0.38f)
            }
            "ball" -> {
                circle(0f, 0f, min(w, h) / 2, El.withAlpha(c, 0.1f))
                txt("m", 0f, 0f, min(w, h) * 0.38f)
            }
            "incline" -> {
                val p = GPath(); p.moveTo(l, b); p.lineTo(r, b); p.lineTo(l, t); p.close()
                g.path(p, c, lw, El.withAlpha(c, 0.06f))
                hatch(l - 10, b, r + 10, b, true)
                val ang = Math.atan2(h.toDouble(), w.toDouble())
                val p2 = GPath(); p2.addArc(r, b, 36f, 36f, 0f, PI, PI + ang, true)
                g.path(p2, c, thin)
                txt("α", r - 52, b - 12, 18f)
            }
            "spring" -> {
                val p = GPath()
                val n = 9
                val start = l + w * 0.12f; val end = r - w * 0.12f
                p.moveTo(l, 0f); p.lineTo(start, 0f)
                for (i in 0 until n * 2) {
                    val x = start + (end - start) * (i + 0.5f) / (n * 2)
                    p.lineTo(x, if (i % 2 == 0) t + 3 else b - 3)
                }
                p.lineTo(end, 0f); p.lineTo(r, 0f)
                g.path(p, c, lw)
            }
            "pulley" -> {
                val rr = min(w / 2, h / 3.2f)
                hatch(-rr * 0.9f, t, rr * 0.9f, t, false)
                line(0f, t, 0f, t + h * 0.18f, thin)
                val cyp = t + h * 0.18f + rr
                circle(0f, cyp, rr)
                g.circle(0f, cyp, 3f, 0, 0f, c)
                line(-rr, cyp, -rr, b, lw)
                line(rr, cyp, rr, b - h * 0.2f, lw)
            }
            "pendulum" -> {
                hatch(l, t, r, t, false)
                val ang = 0.45f
                val len = h * 0.78f
                val bx = len * sin(ang); val by = t + len * cos(ang)
                line(0f, t, 0f, t + len, thin, 6f)
                line(0f, t, bx, by, lw)
                circle(bx, by, min(w, h) * 0.1f, El.withAlpha(c, 0.25f))
                val p2 = GPath(); p2.addArc(0f, t, len * 0.3f, len * 0.3f, 0f, PI / 2 - ang, PI / 2, true)
                g.path(p2, c, thin)
                txt("φ", len * 0.1f, t + len * 0.38f, 16f)
                txt("l", bx * 0.55f + 12, t + (by - t) * 0.5f, 17f)
            }
            "cart" -> {
                val p = GPath(); El.roundRect(p, RectF(l, t, r, b - h * 0.3f), 6f)
                g.path(p, c, lw, El.withAlpha(c, 0.08f))
                val wr = h * 0.15f
                circle(l + w * 0.22f, b - wr, wr, paper or 0xFF000000.toInt())
                circle(r - w * 0.22f, b - wr, wr, paper or 0xFF000000.toInt())
            }
            "ground" -> hatch(l, t, r, t, true)
            "wall" -> hatch(r, t, r, b, true)
            "forces" -> {
                val s = min(w, h)
                val p = GPath(); El.roundRect(p, RectF(-s * 0.18f, -s * 0.14f, s * 0.18f, s * 0.14f), 3f)
                g.path(p, c, lw, El.withAlpha(c, 0.08f))
                val red = 0xFFDC2626.toInt(); val blue = 0xFF2563EB.toInt(); val green = 0xFF16A34A.toInt()
                fun arrow(x2: Float, y2: Float, col: Int, name: String, lx: Float, ly: Float) {
                    g.line(0f, 0f, x2, y2, col, lw); El.arrowHead(g, 0f, 0f, x2, y2, col, lw)
                    El.label(g, name, lx, ly, 17f, col)
                }
                arrow(0f, s * 0.48f, red, "m\\vec{g}", 22f, s * 0.44f)
                arrow(0f, -s * 0.48f, blue, "\\vec{N}", 20f, -s * 0.44f)
                arrow(s * 0.48f, 0f, green, "\\vec{F}", s * 0.44f, -16f)
                arrow(-s * 0.4f, 0f, 0xFF9333EA.toInt(), "\\vec{F}_{тр}", -s * 0.36f, -16f)
            }
            "resistor" -> {
                line(l, 0f, l + w * 0.2f, 0f); line(r - w * 0.2f, 0f, r, 0f)
                val p = GPath(); El.roundRect(p, RectF(l + w * 0.2f, t + 4, r - w * 0.2f, b - 4), 1f)
                g.path(p, c, lw, El.withAlpha(paper, 1f))
                txt("R", 0f, t - 12, 16f)
            }
            "capacitor" -> {
                line(l, 0f, -6f, 0f); line(6f, 0f, r, 0f)
                line(-6f, t, -6f, b, lw * 1.3f); line(6f, t, 6f, b, lw * 1.3f)
                txt("C", 0f, t - 12, 16f)
            }
            "battery" -> {
                line(l, 0f, -6f, 0f); line(6f, 0f, r, 0f)
                line(-6f, t, -6f, b, lw * 1.2f); line(6f, t + h * 0.25f, 6f, b - h * 0.25f, lw * 2.4f)
                txt("+", -18f, t + 4, 14f); txt("ε", 0f, t - 12, 16f)
            }
            "lamp" -> {
                val rr = min(w, h) / 2 * 0.8f
                circle(0f, 0f, rr)
                val k = rr * 0.7071f
                line(-k, -k, k, k, thin); line(-k, k, k, -k, thin)
                line(l, 0f, -rr, 0f); line(rr, 0f, r, 0f)
            }
            "switch" -> {
                line(l, 0f, l + w * 0.3f, 0f); line(r - w * 0.3f, 0f, r, 0f)
                g.circle(l + w * 0.3f, 0f, 3.5f, 0, 0f, c); g.circle(r - w * 0.3f, 0f, 3.5f, 0, 0f, c)
                line(l + w * 0.3f, 0f, r - w * 0.34f, t + 2)
                txt("K", 0f, b + 4, 15f)
            }
            "ammeter", "voltmeter" -> {
                val rr = min(w, h) / 2 * 0.8f
                circle(0f, 0f, rr)
                txt(if (kind == "ammeter") "A" else "V", 0f, 0f, rr * 1.1f)
                line(l, 0f, -rr, 0f); line(rr, 0f, r, 0f)
            }
            "inductor" -> {
                val start = l + w * 0.15f; val end = r - w * 0.15f
                line(l, 0f, start, 0f); line(end, 0f, r, 0f)
                val n = 4
                val seg = (end - start) / n
                val p = GPath(); p.moveTo(start, 0f)
                for (i in 0 until n) p.addArc(start + seg * (i + 0.5f), 0f, seg / 2, h * 0.4f, 0f, PI, 2 * PI, false)
                g.path(p, c, lw)
                txt("L", 0f, t - 8, 16f)
            }
            "diode" -> {
                line(l, 0f, -h * 0.4f, 0f); line(h * 0.4f, 0f, r, 0f)
                val p = GPath(); p.moveTo(-h * 0.4f, t + 4); p.lineTo(h * 0.4f, 0f); p.lineTo(-h * 0.4f, b - 4); p.close()
                g.path(p, c, lw, El.withAlpha(c, 0.15f))
                line(h * 0.4f, t + 4, h * 0.4f, b - 4, lw * 1.2f)
            }
            "earth" -> {
                line(0f, t, 0f, 0f)
                line(-w * 0.45f, 0f, w * 0.45f, 0f); line(-w * 0.3f, h * 0.15f, w * 0.3f, h * 0.15f)
                line(-w * 0.15f, h * 0.3f, w * 0.15f, h * 0.3f)
            }
            "charge_p", "charge_n" -> {
                val rr = min(w, h) / 2 * 0.9f
                val col = if (kind == "charge_p") 0xFFDC2626.toInt() else 0xFF2563EB.toInt()
                g.circle(0f, 0f, rr, col, lw, El.withAlpha(col, 0.15f))
                g.line(-rr * 0.5f, 0f, rr * 0.5f, 0f, col, lw * 1.2f)
                if (kind == "charge_p") g.line(0f, -rr * 0.5f, 0f, rr * 0.5f, col, lw * 1.2f)
            }
            "lens", "lens_d" -> {
                line(0f, t, 0f, b)
                val conv = kind == "lens"
                val a = w * 0.35f
                fun head(y: Float, dir: Int) {
                    val p = GPath()
                    if (conv) { p.moveTo(-a, y - dir * a); p.lineTo(0f, y); p.lineTo(a, y - dir * a) }
                    else { p.moveTo(-a, y + dir * a); p.lineTo(0f, y); p.lineTo(a, y + dir * a) }
                    g.path(p, c, lw)
                }
                head(t, -1); head(b, 1)
                g.line(-w * 2.2f, 0f, w * 2.2f, 0f, dashC, thin, 6f)
                g.circle(-w * 1.6f, 0f, 3f, 0, 0f, c); g.circle(w * 1.6f, 0f, 3f, 0, 0f, c)
                txt("F", -w * 1.6f, 14f, 14f); txt("F", w * 1.6f, 14f, 14f)
            }
            "mirror" -> hatch(0f, t, 0f, b, false)
            "prism" -> {
                val p = GPath(); p.moveTo(0f, t); p.lineTo(r, b); p.lineTo(l, b); p.close()
                g.path(p, c, lw, El.withAlpha(0xFF60A5FA.toInt(), 0.18f))
            }
            "axes3d" -> {
                val s = min(w, h) / 2
                val blue = El.withAlpha(c, 1f)
                g.line(0f, 0f, 0f, -s, blue, lw); El.arrowHead(g, 0f, 0f, 0f, -s, blue, lw)
                g.line(0f, 0f, s, 0f, blue, lw); El.arrowHead(g, 0f, 0f, s, 0f, blue, lw)
                g.line(0f, 0f, -s * 0.62f, s * 0.62f, blue, lw); El.arrowHead(g, 0f, 0f, -s * 0.62f, s * 0.62f, blue, lw)
                txt("z", 14f, -s + 4, 18f); txt("y", s - 4, 16f, 18f); txt("x", -s * 0.62f - 12, s * 0.62f - 8, 18f)
                txt("O", -12f, -10f, 15f)
            }
            "cube" -> {
                val dx = w * 0.3f; val dy = h * 0.3f
                val fx = l; val fy = t + dy; val fr = r - dx; val fb = b
                // передняя грань
                val p = GPath(); p.moveTo(fx, fy); p.lineTo(fr, fy); p.lineTo(fr, fb); p.lineTo(fx, fb); p.close()
                g.path(p, c, lw)
                line(fx, fy, fx + dx, fy - dy); line(fr, fy, fr + dx, fy - dy); line(fr, fb, fr + dx, fb - dy)
                line(fx + dx, fy - dy, fr + dx, fy - dy); line(fr + dx, fy - dy, fr + dx, fb - dy)
                // невидимые рёбра пунктиром
                g.line(fx, fb, fx + dx, fb - dy, dashC, thin, 6f)
                g.line(fx + dx, fb - dy, fr + dx, fb - dy, dashC, thin, 6f)
                g.line(fx + dx, fb - dy, fx + dx, fy - dy, dashC, thin, 6f)
            }
            "pyramid" -> {
                val ax = l; val ay = b - h * 0.12f
                val bx = l + w * 0.62f; val by = b
                val cxp = r; val cyp = b - h * 0.28f
                val dx = l + w * 0.38f; val dy = b - h * 0.36f
                val sx = l + w * 0.5f; val sy = t
                line(ax, ay, bx, by); line(bx, by, cxp, cyp)
                g.line(cxp, cyp, dx, dy, dashC, thin, 6f); g.line(dx, dy, ax, ay, dashC, thin, 6f)
                line(sx, sy, ax, ay); line(sx, sy, bx, by); line(sx, sy, cxp, cyp)
                g.line(sx, sy, dx, dy, dashC, thin, 6f)
            }
            "cylinder" -> {
                val ry = h * 0.1f
                val top = GPath(); top.addEllipse(0f, t + ry, w / 2, ry, 0f)
                g.path(top, c, lw)
                line(l, t + ry, l, b - ry); line(r, t + ry, r, b - ry)
                val bot = GPath(); bot.addArc(0f, b - ry, w / 2, ry, 0f, 0.0, PI, true)
                g.path(bot, c, lw)
                val back = GPath(); back.addArc(0f, b - ry, w / 2, ry, 0f, PI, 2 * PI, true)
                g.path(back, dashC, thin, 0, 6f)
            }
            "cone" -> {
                val ry = h * 0.1f
                line(0f, t, l, b - ry); line(0f, t, r, b - ry)
                val bot = GPath(); bot.addArc(0f, b - ry, w / 2, ry, 0f, 0.0, PI, true)
                g.path(bot, c, lw)
                val back = GPath(); back.addArc(0f, b - ry, w / 2, ry, 0f, PI, 2 * PI, true)
                g.path(back, dashC, thin, 0, 6f)
                g.line(0f, t, 0f, b - ry, dashC, thin, 6f)
                g.line(0f, b - ry, r, b - ry, dashC, thin, 6f)
            }
            "sphere" -> {
                val rr = min(w, h) / 2
                circle(0f, 0f, rr)
                val eq = GPath(); eq.addArc(0f, 0f, rr, rr * 0.3f, 0f, 0.0, PI, true)
                g.path(eq, c, thin)
                val back = GPath(); back.addArc(0f, 0f, rr, rr * 0.3f, 0f, PI, 2 * PI, true)
                g.path(back, dashC, thin, 0, 6f)
                g.circle(0f, 0f, 2.5f, 0, 0f, c)
                g.line(0f, 0f, rr, 0f, dashC, thin, 5f)
            }
            else -> {
                val p = GPath(); El.roundRect(p, RectF(l, t, r, b), 4f)
                g.path(p, c, lw)
            }
        }
    }
}
