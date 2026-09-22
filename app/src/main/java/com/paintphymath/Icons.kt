package com.paintphymath

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View

/**
 * Векторные иконки в стиле «line icons» (сетка 24×24). Описываются мини-языком путей:
 * M L H V C Q Z — как в SVG (абсолютные), O cx cy r — окружность, F cx cy r — закрашенный круг.
 * Иконка, начинающаяся с «@», рисуется символом шрифта (для математических знаков).
 */
object Icons {
    val PATHS = mapOf(
        "menu" to "M4 6 H20 M4 12 H20 M4 18 H20",
        "undo" to "M9 14 L4 9 L9 4 M4 9 H15 C18 9 20 11.5 20 14.5 C20 17.5 18 20 15 20 H11",
        "redo" to "M15 14 L20 9 L15 4 M20 9 H9 C6 9 4 11.5 4 14.5 C4 17.5 6 20 9 20 H13",
        "pen" to "M12 20 H21 M16.5 3.5 C17.3 2.7 18.7 2.7 19.5 3.5 L20.5 4.5 C21.3 5.3 21.3 6.7 20.5 7.5 L8 20 L3 21 L4 16 Z",
        "pencil" to "M3 21 L4 16 L15 5 L19 9 L8 20 Z M13 7 L17 11 M4 16 L8 20",
        "marker" to "M9 11 L4 16 V20 H8 L13 15 M9 11 L15 5 L19 9 L13 15 M9 11 L13 15 M15 20 H21",
        "eraser" to "M7 21 L3 17 L14 6 L20 12 L11 21 Z M9 11 L15 17 M11 21 H21",
        "select" to "M4 8 C4 5 7.5 3 12 3 C16.5 3 20 5 20 8 C20 11 16.5 13 12 13 C10.8 13 9.7 12.9 8.7 12.6 M6 11 C4.8 12.6 5.3 14.8 7 15.8 C7.2 18 6.2 19.6 4.5 21",
        "ruler" to "M3 17 L17 3 L21 7 L7 21 Z M7 13 L9 15 M10 10 L12 12 M13 7 L15 9",
        "circle" to "O 12 12 8.5 F 12 12 1.2 M12 12 L18 6",
        "shapes" to "M3 3 H11 V11 H3 Z M17 3 L21.5 11 H12.5 Z O 7 17 4 M13 13 H21 V21 H13 Z",
        "text" to "M4 7 V4 H20 V7 M12 4 V20 M9 20 H15",
        "point" to "F 7 16 2.6 M12 10 L15 3 L18 10 M13.2 7.5 H16.8",
        "angle" to "M3 20 H21 M3 20 L16 5 M10 20 C10 17.6 9.2 15.6 7.8 14.2",
        "axes" to "M4 21 V3 M4 3 L2 5 M4 3 L6 5 M3 20 H21 M21 20 L19 18 M21 20 L19 22 M6.5 17 C9 5 13 17 18.5 6",
        "link" to "O 6 6 2.8 O 18 18 2.8 M8.2 8.2 L15.6 15.6 M16 11.5 V16 H11.5",
        "stamp" to "M3 20 H21 L3 8 Z M8 9.5 L12.5 12.4 L10.8 15 L6.3 12.1 Z",
        "laser" to "O 12 12 3 M12 2 V5 M12 19 V22 M2 12 H5 M19 12 H22 M5 5 L7 7 M17 17 L19 19 M5 19 L7 17 M17 7 L19 5",
        "hand" to "M8 13 V5.5 C8 4.7 8.7 4 9.5 4 C10.3 4 11 4.7 11 5.5 V11 M11 5 V3.5 C11 2.7 11.7 2 12.5 2 C13.3 2 14 2.7 14 3.5 V11 M14 5 C14 4.2 14.7 3.5 15.5 3.5 C16.3 3.5 17 4.2 17 5 V12 M17 8 C17 7.2 17.7 6.5 18.5 6.5 C19.3 6.5 20 7.2 20 8 V15 C20 19 17 22 13 22 H12 C9 22 7.5 20.5 6 18.5 L3.5 14.5 C3 13.7 3.2 12.7 4 12.2 C4.8 11.7 5.8 11.9 6.3 12.6 L8 15",
        "plus" to "M12 5 V19 M5 12 H19",
        "graph" to "M3 20 H21 M4 21 V3 M6 17 C9 4 12 18 19 6",
        "table" to "M3 4 H21 V20 H3 Z M3 10 H21 M3 15 H21 M9 4 V20 M15 4 V20",
        "image" to "M3 4 H21 V20 H3 Z O 9 9 2 M21 15 L16 10 L5 20",
        "export" to "M12 3 V15 M7 8 L12 3 L17 8 M5 14 V20 H19 V14",
        "settings" to "M4 6 H13 M17 6 H20 O 15 6 2 M4 12 H6 M10 12 H20 O 8 12 2 M4 18 H11 M15 18 H20 O 13 18 2",
        "paper" to "M3 3 H21 V21 H3 Z M9 3 V21 M15 3 V21 M3 9 H21 M3 15 H21",
        "trash" to "M4 7 H20 M10 11 V17 M14 11 V17 M6 7 L7 20 H17 L18 7 M9 7 V4 H15 V7",
        "copy" to "M8 8 H20 V20 H8 Z M4 16 V4 H16",
        "up" to "M12 19 V5 M5 12 L12 5 L19 12",
        "down" to "M12 5 V19 M5 12 L12 19 L19 12",
        "edit" to "M4 20 H8 L19 9 L15 5 L4 16 Z M13 7 L17 11",
        "check" to "M5 12 L10 17 L20 7",
        "close" to "M6 6 L18 18 M18 6 L6 18",
        "fit" to "M4 9 V4 H9 M15 4 H20 V9 M20 15 V20 H15 M9 20 H4 V15",
        "bluetooth" to "M7 7 L17 17 L12 22 V2 L17 7 L7 17",
        "magnet" to "M6 3 V11 C6 14.3 8.7 17 12 17 C15.3 17 18 14.3 18 11 V3 M6 3 H10 V11 C10 12.1 10.9 13 12 13 C13.1 13 14 12.1 14 11 V3 H18 M6 7 H10 M14 7 H18",
        "folder" to "M3 6 H9 L11 8 H21 V19 H3 Z",
        "newdoc" to "M6 3 H14 L19 8 V21 H6 Z M14 3 V8 H19 M12.5 11 V17 M9.5 14 H15.5",
        "more" to "F 5 12 1.6 F 12 12 1.6 F 19 12 1.6",
        "rotate" to "M20 12 C20 16.4 16.4 20 12 20 C7.6 20 4 16.4 4 12 C4 7.6 7.6 4 12 4 C14.5 4 16.7 5.1 18.2 6.9 M18 3 V7 H14",
        "palette" to "O 12 12 9 F 8 9 1.3 F 12 7 1.3 F 16 9 1.3 F 9 14 1.3",
        "share" to "O 18 5 2.5 O 6 12 2.5 O 18 19 2.5 M8.2 10.8 L15.8 6.2 M8.2 13.2 L15.8 17.8",
        "stylus" to "M3 21 L6 20 L19 7 L17 5 L4 18 Z M15 7 L17 9 M17 5 L18.5 3.5 C19 3 20 3 20.5 3.5 C21 4 21 5 20.5 5.5 L19 7",
        "fullscreen" to "M4 9 V4 H9 M15 4 H20 V9 M20 15 V20 H15 M9 20 H4 V15",
        "sigma" to "@∑",
        "sqrt" to "@√x",
        "matrix" to "@[ ]",
        "const" to "@ℏ",
        "func" to "@ƒ",
        "series" to "@Σₙ",
        "vector" to "M4 20 L19 5 M12 5 H19 V12",
    )

    private val cache = HashMap<String, Path>()
    private val filled = HashMap<String, Path>()

    private fun parse(spec: String): Pair<Path, Path> {
        val stroke = Path()
        val fill = Path()
        val tok = spec.replace(",", " ").split(Regex("\\s+|(?<=[A-Za-z])(?=[-0-9.])|(?<=[0-9.])(?=[A-Za-z])")).filter { it.isNotEmpty() }
        var i = 0
        var cmd = 'M'
        var cx = 0f; var cy = 0f
        fun num() = tok[i++].toFloat()
        while (i < tok.size) {
            val t = tok[i]
            if (t[0].isLetter()) { cmd = t[0]; i++; if (cmd == 'Z') { stroke.close(); continue } }
            when (cmd) {
                'M' -> { cx = num(); cy = num(); stroke.moveTo(cx, cy); cmd = 'L' }
                'L' -> { cx = num(); cy = num(); stroke.lineTo(cx, cy) }
                'H' -> { cx = num(); stroke.lineTo(cx, cy) }
                'V' -> { cy = num(); stroke.lineTo(cx, cy) }
                'C' -> { val a = num(); val b = num(); val c = num(); val d = num(); cx = num(); cy = num(); stroke.cubicTo(a, b, c, d, cx, cy) }
                'Q' -> { val a = num(); val b = num(); cx = num(); cy = num(); stroke.quadTo(a, b, cx, cy) }
                'O' -> { val x = num(); val y = num(); val r = num(); stroke.addCircle(x, y, r, Path.Direction.CW) }
                'F' -> { val x = num(); val y = num(); val r = num(); fill.addCircle(x, y, r, Path.Direction.CW) }
                else -> i++
            }
        }
        return stroke to fill
    }

    private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = 1.8f
    }
    private val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL) }

    fun draw(c: Canvas, name: String, cx: Float, cy: Float, size: Float, color: Int) {
        val spec = PATHS[name] ?: name
        if (spec.startsWith("@")) {
            val s = spec.substring(1)
            tp.color = color
            tp.textSize = size * (if (s.length > 2) 0.7f else 0.95f)
            val fm = tp.fontMetrics
            c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2, tp)
            return
        }
        val p = cache.getOrPut(name) { parse(spec).also { filled[name] = it.second }.first }
        val f = filled[name]
        c.save()
        c.translate(cx - size / 2, cy - size / 2)
        c.scale(size / 24f, size / 24f)
        sp.color = color
        c.drawPath(p, sp)
        if (f != null && !f.isEmpty) { fp.color = color; c.drawPath(f, fp) }
        c.restore()
    }
}

/** Простая вьюшка-иконка. */
class IconView(ctx: Context, var icon: String, var tint: Int, private val sizeRatio: Float = 0.55f) : View(ctx) {
    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height) * sizeRatio
        Icons.draw(canvas, icon, width / 2f, height / 2f, s, tint)
    }
}
