package com.paintphymath

import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

object Ids {
    private val c = AtomicLong(System.currentTimeMillis() * 1000)
    fun next() = c.incrementAndGet()
}

object Geo {
    fun dist(x1: Float, y1: Float, x2: Float, y2: Float) = hypot(x2 - x1, y2 - y1)

    /** Расстояние от точки до отрезка. */
    fun segDist(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val l2 = dx * dx + dy * dy
        if (l2 < 1e-9f) return dist(px, py, x1, y1)
        val t = (((px - x1) * dx + (py - y1) * dy) / l2).coerceIn(0f, 1f)
        return dist(px, py, x1 + t * dx, y1 + t * dy)
    }

    fun angleDeg(x1: Float, y1: Float, x2: Float, y2: Float) =
        Math.toDegrees(atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())).toFloat()

    fun mapPt(m: Matrix, x: Float, y: Float): PointF {
        val a = floatArrayOf(x, y)
        m.mapPoints(a)
        return PointF(a[0], a[1])
    }

    /** Коэффициент масштаба аффинной матрицы (корень из |det|). */
    fun scaleOf(m: Matrix): Float {
        val v = FloatArray(9)
        m.getValues(v)
        return sqrt(abs(v[0] * v[4] - v[1] * v[3]))
    }

    fun rotationOf(m: Matrix): Float {
        val v = FloatArray(9)
        m.getValues(v)
        return Math.toDegrees(atan2(v[3].toDouble(), v[0].toDouble())).toFloat()
    }

    fun pointInPoly(x: Float, y: Float, poly: FloatArray, n: Int): Boolean {
        var inside = false
        var j = n - 1
        for (i in 0 until n) {
            val xi = poly[i * 2]; val yi = poly[i * 2 + 1]
            val xj = poly[j * 2]; val yj = poly[j * 2 + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    fun union(a: RectF?, b: RectF): RectF = if (a == null) RectF(b) else RectF(a).apply { union(b) }

    /**
     * Отсечение отрезка прямоугольником (Лианг–Барски). Возвращает null, если отрезок снаружи.
     */
    fun clip(x0: Double, y0: Double, x1: Double, y1: Double, l: Double, t: Double, r: Double, b: Double): DoubleArray? {
        var t0 = 0.0
        var t1 = 1.0
        val dx = x1 - x0
        val dy = y1 - y0
        val p = doubleArrayOf(-dx, dx, -dy, dy)
        val q = doubleArrayOf(x0 - l, r - x0, y0 - t, b - y0)
        for (i in 0 until 4) {
            if (p[i] == 0.0) {
                if (q[i] < 0) return null
            } else {
                val u = q[i] / p[i]
                if (p[i] < 0) { if (u > t1) return null; if (u > t0) t0 = u }
                else { if (u < t0) return null; if (u < t1) t1 = u }
            }
        }
        return doubleArrayOf(x0 + t0 * dx, y0 + t0 * dy, x0 + t1 * dx, y0 + t1 * dy, t0, t1)
    }

    /** Упрощение ломаной (Рамер–Дуглас–Пекер). */
    fun rdp(xs: FloatArray, ys: FloatArray, eps: Float): List<Int> {
        val n = xs.size
        if (n < 3) return (0 until n).toList()
        val keep = BooleanArray(n)
        keep[0] = true; keep[n - 1] = true
        val stack = ArrayDeque<IntArray>()
        stack.add(intArrayOf(0, n - 1))
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast().let { it[0] to it[1] }
            var best = -1
            var bd = 0f
            for (i in a + 1 until b) {
                val d = segDist(xs[i], ys[i], xs[a], ys[a], xs[b], ys[b])
                if (d > bd) { bd = d; best = i }
            }
            if (best >= 0 && bd > eps) {
                keep[best] = true
                stack.add(intArrayOf(a, best)); stack.add(intArrayOf(best, b))
            }
        }
        return (0 until n).filter { keep[it] }
    }

    /** «Красивый» шаг сетки: 1, 2, 5 × 10^k, не меньше minStep. */
    fun niceStep(minStep: Double): Double {
        if (minStep <= 0 || minStep.isNaN()) return 1.0
        val p = Math.pow(10.0, Math.floor(Math.log10(minStep)))
        for (m in doubleArrayOf(1.0, 2.0, 5.0, 10.0)) if (m * p >= minStep) return m * p
        return 10 * p
    }
}
