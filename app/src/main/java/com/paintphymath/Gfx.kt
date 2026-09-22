package com.paintphymath

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Независимое от платформы описание пути. Один и тот же GPath отрисовывается
 * на Android Canvas (экран, PNG, PDF) и сериализуется в SVG — поэтому экспорт
 * всегда совпадает с тем, что видно на экране.
 */
class GPath {
    private var ops = ByteArray(32)
    private var nOps = 0
    private var co = FloatArray(128)
    private var nCo = 0
    private var cached: Path? = null

    val isEmpty get() = nOps == 0

    private fun op(o: Byte, vararg v: Float) {
        if (nOps == ops.size) ops = ops.copyOf(ops.size * 2)
        ops[nOps++] = o
        if (nCo + v.size > co.size) co = co.copyOf(maxOf(co.size * 2, nCo + v.size))
        for (f in v) co[nCo++] = f
        cached = null
    }

    fun moveTo(x: Float, y: Float) = op(M, x, y)
    fun lineTo(x: Float, y: Float) = op(L, x, y)
    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) = op(Q, x1, y1, x2, y2)
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) = op(C, x1, y1, x2, y2, x3, y3)
    fun close() = op(Z)

    fun addCircle(cx: Float, cy: Float, r: Float) = addEllipse(cx, cy, r, r, 0f)

    /** Эллипс из четырёх кубических Безье с поворотом rot (радианы). */
    fun addEllipse(cx: Float, cy: Float, rx: Float, ry: Float, rot: Float) {
        addArc(cx, cy, rx, ry, rot, 0.0, Math.PI * 2, true)
        close()
    }

    /** Дуга эллипса от a0 до a1 (радианы, по часовой на экране при росте угла). */
    fun addArc(cx: Float, cy: Float, rx: Float, ry: Float, rot: Float, a0: Double, a1: Double, move: Boolean) {
        val sweep = a1 - a0
        val segs = maxOf(1, ceil(abs(sweep) / (Math.PI / 2)).toInt())
        val d = sweep / segs
        val k = (4.0 / 3.0 * tan(d / 4)).toFloat()
        val cr = cos(rot.toDouble()).toFloat()
        val sr = sin(rot.toDouble()).toFloat()
        fun px(ex: Float, ey: Float) = cx + ex * cr - ey * sr
        fun py(ex: Float, ey: Float) = cy + ex * sr + ey * cr
        var a = a0
        var ex0 = (rx * cos(a)).toFloat()
        var ey0 = (ry * sin(a)).toFloat()
        if (move) moveTo(px(ex0, ey0), py(ex0, ey0)) else lineTo(px(ex0, ey0), py(ex0, ey0))
        for (i in 0 until segs) {
            val b = a + d
            val ex1 = (rx * cos(b)).toFloat()
            val ey1 = (ry * sin(b)).toFloat()
            // касательные
            val t0x = (-rx * sin(a)).toFloat() * k
            val t0y = (ry * cos(a)).toFloat() * k
            val t1x = (-rx * sin(b)).toFloat() * k
            val t1y = (ry * cos(b)).toFloat() * k
            val c1x = ex0 + t0x; val c1y = ey0 + t0y
            val c2x = ex1 - t1x; val c2y = ey1 - t1y
            cubicTo(px(c1x, c1y), py(c1x, c1y), px(c2x, c2y), py(c2x, c2y), px(ex1, ey1), py(ex1, ey1))
            a = b; ex0 = ex1; ey0 = ey1
        }
    }

    fun android(): Path {
        cached?.let { return it }
        val p = Path()
        var c = 0
        for (i in 0 until nOps) {
            when (ops[i]) {
                M -> { p.moveTo(co[c], co[c + 1]); c += 2 }
                L -> { p.lineTo(co[c], co[c + 1]); c += 2 }
                Q -> { p.quadTo(co[c], co[c + 1], co[c + 2], co[c + 3]); c += 4 }
                C -> { p.cubicTo(co[c], co[c + 1], co[c + 2], co[c + 3], co[c + 4], co[c + 5]); c += 6 }
                Z -> p.close()
            }
        }
        cached = p
        return p
    }

    fun svg(): String {
        val sb = StringBuilder(nCo * 6)
        var c = 0
        for (i in 0 until nOps) {
            val o = ops[i]
            val n = when (o) { M, L -> 2; Q -> 4; C -> 6; else -> 0 }
            sb.append(when (o) { M -> 'M'; L -> 'L'; Q -> 'Q'; C -> 'C'; else -> 'Z' })
            for (j in 0 until n) {
                if (j > 0) sb.append(' ')
                sb.append(Svg.f(co[c++]))
            }
        }
        return sb.toString()
    }

    companion object {
        const val M: Byte = 0
        const val L: Byte = 1
        const val Q: Byte = 2
        const val C: Byte = 3
        const val Z: Byte = 4

        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = GPath().apply { moveTo(x1, y1); lineTo(x2, y2) }
    }
}

enum class Font { SERIF, SERIF_ITALIC, SANS, SANS_BOLD, SERIF_BOLD, MONO }

/** Общая метрика текста для экрана и для SVG. */
object TextMetrics {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val typefaces = mapOf(
        Font.SERIF to Typeface.create(Typeface.SERIF, Typeface.NORMAL),
        Font.SERIF_ITALIC to Typeface.create(Typeface.SERIF, Typeface.ITALIC),
        Font.SERIF_BOLD to Typeface.create(Typeface.SERIF, Typeface.BOLD),
        Font.SANS to Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL),
        Font.SANS_BOLD to Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
        Font.MONO to Typeface.MONOSPACE,
    )

    @Synchronized
    fun width(s: String, size: Float, font: Font): Float {
        paint.typeface = typefaces[font]
        paint.textSize = size
        return paint.measureText(s)
    }
}

/** Абстрактная «графика»: одна реализация для Canvas, другая для SVG. */
interface Gfx {
    /** Текущий масштаб экрана (1 для экспорта) — для «волосяных» линий и маркеров. */
    val zoom: Float
    fun path(p: GPath, stroke: Int = 0, width: Float = 0f, fill: Int = 0, dash: Float = 0f, round: Boolean = true)
    fun text(s: String, x: Float, y: Float, size: Float, color: Int, font: Font = Font.SANS, align: Int = 0)
    fun image(bmp: Bitmap, dst: RectF, b64: String?)
    fun save()
    fun restore()
    fun translate(dx: Float, dy: Float)
    fun rotate(deg: Float, px: Float, py: Float)
    fun scale(sx: Float, sy: Float, px: Float, py: Float)

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, w: Float, dash: Float = 0f) =
        path(GPath.line(x1, y1, x2, y2), color, w, 0, dash)

    fun circle(cx: Float, cy: Float, r: Float, stroke: Int, w: Float, fill: Int = 0) =
        path(GPath().apply { addCircle(cx, cy, r) }, stroke, w, fill)
}

class CanvasGfx(var canvas: Canvas, override var zoom: Float = 1f) : Gfx {
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val bp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dashes = HashMap<Float, DashPathEffect>()

    override fun path(p: GPath, stroke: Int, width: Float, fill: Int, dash: Float, round: Boolean) {
        val ap = p.android()
        if (fill != 0) {
            fp.color = fill
            canvas.drawPath(ap, fp)
        }
        if (stroke != 0 && width > 0f) {
            sp.color = stroke
            sp.strokeWidth = width
            sp.strokeCap = if (round) Paint.Cap.ROUND else Paint.Cap.BUTT
            sp.strokeJoin = if (round) Paint.Join.ROUND else Paint.Join.MITER
            sp.pathEffect = if (dash > 0f) dashes.getOrPut(dash) { DashPathEffect(floatArrayOf(dash, dash * 0.8f), 0f) } else null
            canvas.drawPath(ap, sp)
        }
    }

    override fun text(s: String, x: Float, y: Float, size: Float, color: Int, font: Font, align: Int) {
        tp.typeface = TextMetrics.typefaces[font]
        tp.textSize = size
        tp.color = color
        tp.textAlign = when (align) { 1 -> Paint.Align.CENTER; 2 -> Paint.Align.RIGHT; else -> Paint.Align.LEFT }
        canvas.drawText(s, x, y, tp)
    }

    override fun image(bmp: Bitmap, dst: RectF, b64: String?) {
        canvas.drawBitmap(bmp, null, dst, bp)
    }

    override fun save() { canvas.save() }
    override fun restore() { canvas.restore() }
    override fun translate(dx: Float, dy: Float) = canvas.translate(dx, dy)
    override fun rotate(deg: Float, px: Float, py: Float) = canvas.rotate(deg, px, py)
    override fun scale(sx: Float, sy: Float, px: Float, py: Float) = canvas.scale(sx, sy, px, py)
}

object Svg {
    fun f(v: Float): String {
        if (v == v.toInt().toFloat() && abs(v) < 1e7) return v.toInt().toString()
        return String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
    }

    fun color(c: Int): String = String.format(Locale.US, "#%06X", c and 0xFFFFFF)
    fun alpha(c: Int): Float = ((c ushr 24) and 0xFF) / 255f

    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

class SvgGfx : Gfx {
    override val zoom = 1f
    val sb = StringBuilder()
    private val stack = ArrayList<Matrix>()
    private var m = Matrix()

    private fun tf(): String {
        if (m.isIdentity) return ""
        val v = FloatArray(9)
        m.getValues(v)
        return " transform=\"matrix(${Svg.f(v[0])} ${Svg.f(v[3])} ${Svg.f(v[1])} ${Svg.f(v[4])} ${Svg.f(v[2])} ${Svg.f(v[5])})\""
    }

    override fun path(p: GPath, stroke: Int, width: Float, fill: Int, dash: Float, round: Boolean) {
        if (p.isEmpty) return
        sb.append("<path d=\"").append(p.svg()).append('"')
        if (fill != 0) {
            sb.append(" fill=\"").append(Svg.color(fill)).append('"')
            val a = Svg.alpha(fill)
            if (a < 1f) sb.append(" fill-opacity=\"").append(Svg.f(a)).append('"')
        } else sb.append(" fill=\"none\"")
        if (stroke != 0 && width > 0f) {
            sb.append(" stroke=\"").append(Svg.color(stroke)).append("\" stroke-width=\"").append(Svg.f(width)).append('"')
            val a = Svg.alpha(stroke)
            if (a < 1f) sb.append(" stroke-opacity=\"").append(Svg.f(a)).append('"')
            if (round) sb.append(" stroke-linecap=\"round\" stroke-linejoin=\"round\"")
            if (dash > 0f) sb.append(" stroke-dasharray=\"").append(Svg.f(dash)).append(' ').append(Svg.f(dash * 0.8f)).append('"')
        }
        sb.append(tf()).append("/>\n")
    }

    override fun text(s: String, x: Float, y: Float, size: Float, color: Int, font: Font, align: Int) {
        if (s.isEmpty()) return
        val family = when (font) {
            Font.SERIF, Font.SERIF_ITALIC, Font.SERIF_BOLD -> "'Times New Roman', 'Noto Serif', serif"
            Font.MONO -> "monospace"
            else -> "Roboto, 'Noto Sans', Arial, sans-serif"
        }
        sb.append("<text x=\"").append(Svg.f(x)).append("\" y=\"").append(Svg.f(y))
            .append("\" font-size=\"").append(Svg.f(size)).append("\" font-family=\"").append(family).append('"')
            .append(" fill=\"").append(Svg.color(color)).append('"')
        val a = Svg.alpha(color)
        if (a < 1f) sb.append(" fill-opacity=\"").append(Svg.f(a)).append('"')
        if (font == Font.SERIF_ITALIC) sb.append(" font-style=\"italic\"")
        if (font == Font.SANS_BOLD || font == Font.SERIF_BOLD) sb.append(" font-weight=\"bold\"")
        when (align) {
            1 -> sb.append(" text-anchor=\"middle\"")
            2 -> sb.append(" text-anchor=\"end\"")
        }
        sb.append(" xml:space=\"preserve\"").append(tf()).append('>').append(Svg.esc(s)).append("</text>\n")
    }

    override fun image(bmp: Bitmap, dst: RectF, b64: String?) {
        val data = b64 ?: run {
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
            Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        }
        val mime = if (data.startsWith("/9j/")) "image/jpeg" else "image/png"
        sb.append("<image x=\"").append(Svg.f(dst.left)).append("\" y=\"").append(Svg.f(dst.top))
            .append("\" width=\"").append(Svg.f(dst.width())).append("\" height=\"").append(Svg.f(dst.height()))
            .append("\" preserveAspectRatio=\"none\" href=\"data:").append(mime).append(";base64,").append(data).append('"')
            .append(tf()).append("/>\n")
    }

    override fun save() { stack.add(Matrix(m)) }
    override fun restore() { if (stack.isNotEmpty()) m = stack.removeAt(stack.size - 1) }
    override fun translate(dx: Float, dy: Float) { m.preTranslate(dx, dy) }
    override fun rotate(deg: Float, px: Float, py: Float) { m.preRotate(deg, px, py) }
    override fun scale(sx: Float, sy: Float, px: Float, py: Float) { m.preScale(sx, sy, px, py) }
}
