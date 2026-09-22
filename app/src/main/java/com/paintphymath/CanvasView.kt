package com.paintphymath

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class Tool(val icon: String, val title: String) {
    PEN("pen", "Ручка"),
    PENCIL("pencil", "Карандаш"),
    MARKER("marker", "Маркер (фон)"),
    ERASER("eraser", "Ластик"),
    SELECT("select", "Выделение"),
    LINE("ruler", "Линейка"),
    CIRCLE("circle", "Окружности"),
    SHAPE("shapes", "Фигуры"),
    TEXT("text", "Текст, формулы, названия"),
    POINT("point", "Точка с именем"),
    ANGLE("angle", "Угол"),
    AXES("axes", "Оси координат"),
    LINK("link", "Связь / зависимость"),
    STAMP("stamp", "Шаблоны физики и геометрии"),
    LASER("laser", "Лазерная указка"),
    HAND("hand", "Перемещение"),
}

@SuppressLint("ClickableViewAccessibility", "ViewConstructor")
class CanvasView(ctx: Context, val prefs: Prefs) : View(ctx) {

    interface Host {
        fun onStateChanged()
        fun requestText(x: Float, y: Float, existing: TextEl?)
        fun requestEdit(e: El)
        fun requestLinkLabel(link: LinkEl)
        fun toast(msg: String)
        fun onStylusDetected()
        fun onUiAction(action: Int)
    }

    var host: Host? = null

    var doc: Doc = Doc()
        set(v) {
            field = v
            selection.clear()
            scale = v.viewScale.coerceIn(MIN_SCALE, MAX_SCALE); tx = v.viewTx; ty = v.viewTy
            cancelOp()
            invalidateCache()
        }

    val dctx get() = DrawCtx(doc)

    // ------------------------------------------------------------------ вид

    var scale = 1f; private set
    var tx = 0f; private set
    var ty = 0f; private set

    fun wx(sx: Float) = (sx - tx) / scale
    fun wy(sy: Float) = (sy - ty) / scale
    fun sx(wx: Float) = wx * scale + tx
    fun sy(wy: Float) = wy * scale + ty

    fun viewRectWorld() = RectF(wx(0f), wy(0f), wx(width.toFloat()), wy(height.toFloat()))

    private fun saveView() { doc.viewScale = scale; doc.viewTx = tx; doc.viewTy = ty }

    fun zoomAround(f: Float, cx: Float, cy: Float) {
        val ns = (scale * f).coerceIn(MIN_SCALE, MAX_SCALE)
        val wxp = wx(cx); val wyp = wy(cy)
        scale = ns
        tx = cx - wxp * ns; ty = cy - wyp * ns
        saveView()
        invalidateCache()
        host?.onStateChanged()
    }

    fun resetZoom() = zoomAround(1f / scale, width / 2f, height / 2f)

    fun fitAll(only: Collection<El>? = null) {
        val r = doc.contentBounds(dctx, only)
        if (r == null || width == 0) { scale = 1f; tx = width / 2f; ty = height / 2f; saveView(); invalidateCache(); return }
        r.inset(-40f, -40f)
        val s = min(width / r.width(), height / r.height()).coerceIn(MIN_SCALE, 3f)
        scale = s
        tx = width / 2f - r.centerX() * s
        ty = height / 2f - r.centerY() * s
        saveView()
        invalidateCache()
        host?.onStateChanged()
    }

    fun centerWorld() = PointF(wx(width / 2f), wy(height / 2f))

    // ------------------------------------------------------------------ инструменты и их настройки

    var tool = Tool.PEN
        set(v) {
            if (field != v) { cancelOp(); if (v != Tool.SELECT) clearSelection(); prevTool = field }
            field = v
            host?.onStateChanged()
            invalidate()
        }
    var prevTool = Tool.PEN
    var tempTool: Tool? = null
        set(v) { if (field != v) cancelOp(); field = v; host?.onStateChanged(); invalidate() }
    private var eraserTip = false
    val activeTool get() = if (eraserTip) Tool.ERASER else tempTool ?: tool

    private val colors = HashMap<Tool, Int>()
    private val widths = HashMap<Tool, Float>()

    fun colorOf(t: Tool): Int = colors.getOrPut(t) { prefs.penColor(t.name, defaultColor(t)) }
    fun setColor(t: Tool, c: Int) { colors[t] = c; prefs.setPenColor(t.name, c); host?.onStateChanged() }
    fun widthOf(t: Tool): Float = widths.getOrPut(t) { prefs.penWidth(t.name, defaultWidth(t)) }
    fun setWidth(t: Tool, w: Float) { widths[t] = w; prefs.setPenWidth(t.name, w); host?.onStateChanged() }

    private fun defaultColor(t: Tool) = when (t) {
        Tool.MARKER -> Color.rgb(253, 224, 71)
        Tool.LASER -> Color.rgb(239, 68, 68)
        Tool.AXES -> Color.rgb(40, 40, 52)
        else -> if (Paper.isDark(doc.paperColor)) Color.WHITE else Color.rgb(17, 24, 39)
    }

    private fun defaultWidth(t: Tool) = when (t) {
        Tool.PEN -> 3.2f; Tool.PENCIL -> 2f; Tool.MARKER -> 22f; Tool.ERASER -> 28f
        Tool.LINE, Tool.CIRCLE, Tool.SHAPE, Tool.ANGLE -> 2.4f
        Tool.LINK -> 2f; Tool.STAMP -> 2.4f; Tool.POINT -> 3f
        else -> 2.5f
    }

    /** 0 отрезок, 1 стрелка, 2 вектор, 3 пунктир, 4 прямая, 5 двусторонняя стрелка */
    var lineMode = 0
    /** 0 центр+радиус, 1 по диаметру, 2 эллипс, 3 по трём точкам */
    var circleMode = 0
    var circleShowRadius = false
    /** 0 прямоугольник, 1 квадрат, 2 треугольник, 3 прямоугольный треугольник, 4 параллелограмм, 5 трапеция, 6 многоугольник, 7 правильный шестиугольник */
    var shapeMode = 0
    var shapeFill = false
    /** 0 — целиком, 1 — частично */
    var eraserMode = 0
    /** 0 — лассо, 1 — рамка */
    var selectMode = 0
    var linkStyle = 0
    var stampKind = "block"

    var rulerVisible = false
        set(v) { field = v; if (v && rcx == 0f) { rcx = width / 2f; rcy = height / 2f; rAng = -0.35f }; invalidate(); host?.onStateChanged() }
    private var rcx = 0f
    private var rcy = 0f
    private var rAng = 0f
    private val rThick get() = 96f * resources.displayMetrics.density / 2.2f + 48f
    private val rLen get() = max(width, height) * 0.9f

    val pxPerCm get() = resources.displayMetrics.xdpi / 2.54f

    val selection = ArrayList<El>()

    fun clearSelection() {
        if (selection.isEmpty()) return
        selection.clear()
        host?.onStateChanged()
        invalidate()
    }

    fun select(list: Collection<El>) {
        selection.clear(); selection.addAll(list)
        if (tool != Tool.SELECT) { prevTool = tool; tool = Tool.SELECT }
        host?.onStateChanged(); invalidate()
    }

    // ------------------------------------------------------------------ кэш отрисовки

    private var markerBmp: Bitmap? = null
    private var mainBmp: Bitmap? = null
    private var cacheValid = false
    private var cScale = 1f
    private var cTx = 0f
    private var cTy = 0f
    private var lastRenderMs = 0L
    private val liveIds = HashSet<Long>()
    private var navigating = false

    fun invalidateCache() { cacheValid = false; invalidate() }

    /** Вызывается после любого изменения документа. */
    fun contentChanged() {
        selection.retainAll { s -> doc.els.any { it === s } }
        // после undo объекты пересоздаются — обновим выделение по id
        invalidateCache()
        host?.onStateChanged()
    }

    private fun ensureBitmaps() {
        val w = max(1, width); val h = max(1, height)
        if (mainBmp?.width != w || mainBmp?.height != h) {
            mainBmp?.recycle(); markerBmp?.recycle()
            mainBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            markerBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            cacheValid = false
        }
    }

    private fun visibleEls(): List<El> {
        val vr = viewRectWorld()
        vr.inset(-20f / scale, -20f / scale)
        val ctx = dctx
        return doc.els.filter { e ->
            if (e.id in liveIds) false
            else {
                val b = try { e.bounds(ctx) } catch (t: Throwable) { RectF() }
                RectF.intersects(b, vr) || (b.width() == 0f && b.height() == 0f)
            }
        }
    }

    private fun renderCache() {
        ensureBitmaps()
        val t0 = SystemClock.uptimeMillis()
        val mb = markerBmp!!; val bb = mainBmp!!
        mb.eraseColor(0); bb.eraseColor(0)
        val vis = visibleEls()
        for ((bmp, layer) in listOf(mb to 0, bb to 1)) {
            val c = Canvas(bmp)
            c.translate(tx, ty); c.scale(scale, scale)
            Exporter.render(doc, CanvasGfx(c, scale), vis, emptySet(), layer)
        }
        cScale = scale; cTx = tx; cTy = ty
        cacheValid = true
        lastRenderMs = SystemClock.uptimeMillis() - t0
    }

    // ------------------------------------------------------------------ состояние операций

    private var preview: El? = null
    private var curStroke: StrokeEl? = null
    private var hud: String? = null
    private var hudX = 0f
    private var hudY = 0f
    private var snapMark: PointF? = null
    private var dragStart = PointF()
    private var dragCur = PointF()
    private val taps = ArrayList<PointF>()
    private var opTx: Doc.Tx? = null
    private var lastW = PointF()
    private var smooth = PointF()
    private var rulerLock = 0
    private var recognized: El? = null
    private var stillPos = PointF()
    private val handler = Handler(Looper.getMainLooper())

    // выделение
    private val lasso = ArrayList<PointF>()
    private var selOp = 0 // 0 нет, 1 лассо/рамка, 2 перенос, 3 масштаб, 4 поворот
    private var selAnchor = PointF()
    private var selCenter = PointF()
    private var lastTapTime = 0L
    private var lastTapPos = PointF()

    // связь
    private var linkFrom: El? = null

    // лазер
    private class LaserPt(val x: Float, val y: Float, val t: Long)
    private val laser = ArrayList<LaserPt>()

    // наведение пера
    private var hovering = false
    private var hoverX = 0f
    private var hoverY = 0f

    // кнопка пера
    val buttons = ButtonGestures(object : ButtonGestures.Listener {
        override fun onClicks(count: Int) {
            val a = when (count) { 1 -> prefs.btnSingle; 2 -> prefs.btnDouble; else -> prefs.btnTriple }
            performAction(a, false)
        }
        override fun onHoldStart() = performAction(prefs.btnHold, true)
        override fun onHoldEnd() {
            when (prefs.btnHold) {
                Prefs.A_TEMP_ERASER, Prefs.A_TEMP_SELECT, Prefs.A_TEMP_PAN, Prefs.A_LASER, Prefs.A_TEMP_MARKER -> {
                    if (penId != -1) toolUp(lastW.x, lastW.y)
                    tempTool = null
                    penId = -1
                }
            }
        }
    })
    private var lastButtons = 0

    private val colorCycle = intArrayOf(
        Color.rgb(17, 24, 39), Color.rgb(37, 99, 235), Color.rgb(220, 38, 38), Color.rgb(22, 163, 74), Color.rgb(234, 88, 12), Color.rgb(124, 58, 237)
    )

    fun performAction(a: Int, hold: Boolean) {
        when (a) {
            Prefs.A_NONE -> {}
            Prefs.A_ERASER_TOGGLE -> tool = if (tool == Tool.ERASER) prevTool.takeIf { it != Tool.ERASER } ?: Tool.PEN else Tool.ERASER
            Prefs.A_SELECT_TOGGLE -> tool = if (tool == Tool.SELECT) prevTool.takeIf { it != Tool.SELECT } ?: Tool.PEN else Tool.SELECT
            Prefs.A_MARKER_TOGGLE -> tool = if (tool == Tool.MARKER) prevTool.takeIf { it != Tool.MARKER } ?: Tool.PEN else Tool.MARKER
            Prefs.A_PREV_TOOL -> tool = prevTool
            Prefs.A_UNDO -> undo()
            Prefs.A_REDO -> redo()
            Prefs.A_NEXT_COLOR -> {
                val t = if (tool in listOf(Tool.ERASER, Tool.SELECT, Tool.HAND)) Tool.PEN else tool
                val cur = colorOf(t)
                val i = colorCycle.indexOf(cur)
                setColor(t, colorCycle[(i + 1) % colorCycle.size])
                host?.toast("Цвет изменён")
            }
            Prefs.A_RULER -> rulerVisible = !rulerVisible
            Prefs.A_LASER -> if (hold) tempTool = Tool.LASER else tool = if (tool == Tool.LASER) prevTool else Tool.LASER
            Prefs.A_TEMP_ERASER -> if (hold) tempTool = Tool.ERASER else tool = if (tool == Tool.ERASER) prevTool else Tool.ERASER
            Prefs.A_TEMP_SELECT -> if (hold) tempTool = Tool.SELECT else tool = if (tool == Tool.SELECT) prevTool else Tool.SELECT
            Prefs.A_TEMP_MARKER -> if (hold) tempTool = Tool.MARKER else tool = if (tool == Tool.MARKER) prevTool else Tool.MARKER
            Prefs.A_TEMP_PAN -> if (hold) tempTool = Tool.HAND else tool = if (tool == Tool.HAND) prevTool else Tool.HAND
            Prefs.A_FIT -> fitAll()
            else -> host?.onUiAction(a)
        }
        if (a != Prefs.A_NONE) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun undo() {
        cancelOp()
        if (doc.undo()) { selection.clear(); contentChanged() } else host?.toast("Нечего отменять")
    }

    fun redo() {
        cancelOp()
        if (doc.redo()) { selection.clear(); contentChanged() } else host?.toast("Нечего повторять")
    }

    // ------------------------------------------------------------------ отрисовка

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val uiStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val uiFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val uiText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f * resources.displayMetrics.density }
    private val dashEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    private val screenGfx = CanvasGfx(Canvas())
    private val accent = Color.rgb(79, 70, 229)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (oldw == 0 && doc.viewTx == 0f && doc.viewTy == 0f) { tx = w / 2f; ty = h / 2f; saveView() }
        if (rcx == 0f) { rcx = w / 2f; rcy = h / 2f }
        invalidateCache()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(doc.paperColor)
        // разлиновка
        canvas.save()
        canvas.translate(tx, ty); canvas.scale(scale, scale)
        screenGfx.canvas = canvas; screenGfx.zoom = scale
        Paper.draw(screenGfx, doc.paper, doc.paperColor, viewRectWorld(), scale)
        canvas.restore()

        val useStale = navigating && cacheValid && lastRenderMs > 14
        if (!useStale && (!cacheValid || cScale != scale || cTx != tx || cTy != ty)) renderCache()
        val m = Matrix()
        if (useStale) {
            val s = scale / cScale
            m.setScale(s, s)
            m.postTranslate(tx - cTx * s, ty - cTy * s)
        }
        canvas.drawBitmap(markerBmp!!, m, paint)
        drawLive(canvas, 0)
        canvas.drawBitmap(mainBmp!!, m, paint)
        drawLive(canvas, 1)
        drawWorldOverlays(canvas)
        drawScreenOverlays(canvas)
    }

    private fun drawLive(canvas: Canvas, layer: Int) {
        canvas.save()
        canvas.translate(tx, ty); canvas.scale(scale, scale)
        val g = CanvasGfx(canvas, scale)
        val ctx = dctx
        if (liveIds.isNotEmpty()) for (e in doc.els) if (e.id in liveIds && e.layer == layer) try { e.draw(g, ctx) } catch (t: Throwable) {}
        val p = preview
        if (p != null && p.layer == layer) try { p.draw(g, ctx) } catch (t: Throwable) {}
        canvas.restore()
    }

    private fun selBounds(): RectF? {
        if (selection.isEmpty()) return null
        return doc.contentBounds(dctx, selection)
    }

    private fun drawWorldOverlays(canvas: Canvas) {
        val d = resources.displayMetrics.density
        // выделение
        val sb = selBounds()
        if (sb != null) {
            val r = RectF(sx(sb.left), sy(sb.top), sx(sb.right), sy(sb.bottom))
            r.inset(-6f, -6f)
            uiFill.color = Color.argb(18, 79, 70, 229)
            canvas.drawRoundRect(r, 8f, 8f, uiFill)
            uiStroke.color = accent; uiStroke.strokeWidth = 1.6f * d; uiStroke.pathEffect = dashEffect
            canvas.drawRoundRect(r, 8f, 8f, uiStroke)
            uiStroke.pathEffect = null
            val hr = 7f * d
            for ((hx, hy) in listOf(r.left to r.top, r.right to r.top, r.left to r.bottom, r.right to r.bottom)) {
                uiFill.color = Color.WHITE; canvas.drawCircle(hx, hy, hr, uiFill)
                canvas.drawCircle(hx, hy, hr, uiStroke)
            }
            val rx = r.centerX(); val ry = r.top - 34f * d
            canvas.drawLine(rx, r.top, rx, ry, uiStroke)
            uiFill.color = accent; canvas.drawCircle(rx, ry, hr + 2, uiFill)
            Icons.draw(canvas, "rotate", rx, ry, hr * 1.7f, Color.WHITE)
        }
        // лассо
        if (lasso.size > 1) {
            val p = Path()
            if (selectMode == 1 && selOp == 1) {
                val a = lasso.first(); val b = lasso.last()
                p.addRect(min(sx(a.x), sx(b.x)), min(sy(a.y), sy(b.y)), max(sx(a.x), sx(b.x)), max(sy(a.y), sy(b.y)), Path.Direction.CW)
            } else {
                p.moveTo(sx(lasso[0].x), sy(lasso[0].y))
                for (pt in lasso) p.lineTo(sx(pt.x), sy(pt.y))
            }
            uiFill.color = Color.argb(22, 79, 70, 229); canvas.drawPath(p, uiFill)
            uiStroke.color = accent; uiStroke.strokeWidth = 1.5f * d; uiStroke.pathEffect = dashEffect
            canvas.drawPath(p, uiStroke); uiStroke.pathEffect = null
        }
        // точки многоугольника / угла / окружности по 3 точкам
        if (taps.isNotEmpty()) {
            uiFill.color = accent
            for (t in taps) canvas.drawCircle(sx(t.x), sy(t.y), 5f * d, uiFill)
        }
        // лазер
        if (laser.isNotEmpty()) {
            val now = SystemClock.uptimeMillis()
            laser.removeAll { now - it.t > 1100 }
            val lc = colorOf(Tool.LASER)
            for (i in 1 until laser.size) {
                val a = laser[i - 1]; val b = laser[i]
                if (b.t - a.t > 120) continue
                val age = (now - b.t) / 1100f
                val alpha = (1f - age).coerceIn(0f, 1f)
                uiStroke.strokeCap = Paint.Cap.ROUND
                uiStroke.color = El.withAlpha(lc, alpha * 0.25f); uiStroke.strokeWidth = 14f * d
                canvas.drawLine(sx(a.x), sy(a.y), sx(b.x), sy(b.y), uiStroke)
                uiStroke.color = El.withAlpha(lc, alpha); uiStroke.strokeWidth = 4f * d
                canvas.drawLine(sx(a.x), sy(a.y), sx(b.x), sy(b.y), uiStroke)
            }
            uiStroke.strokeCap = Paint.Cap.BUTT
            if (laser.isNotEmpty()) postInvalidateOnAnimation()
        }
        // привязка
        snapMark?.let {
            uiStroke.color = Color.rgb(236, 72, 153); uiStroke.strokeWidth = 2f * d
            canvas.drawCircle(sx(it.x), sy(it.y), 8f * d, uiStroke)
        }
    }

    private fun drawScreenOverlays(canvas: Canvas) {
        val d = resources.displayMetrics.density
        if (rulerVisible) drawRuler(canvas)
        // курсор наведения
        if (hovering && prefs.hoverCursor) {
            val t = activeTool
            val r = when (t) {
                Tool.ERASER -> widthOf(Tool.ERASER) / 2
                Tool.PEN, Tool.PENCIL, Tool.MARKER -> max(widthOf(t) * scale / 2, 2f)
                else -> 3f * d
            }
            uiStroke.color = if (t == Tool.ERASER) Color.argb(160, 100, 100, 100) else El.withAlpha(colorOf(t), 0.7f)
            uiStroke.strokeWidth = 1.2f * d
            canvas.drawCircle(hoverX, hoverY, r, uiStroke)
            if (t != Tool.ERASER && t != Tool.PEN && t != Tool.PENCIL && t != Tool.MARKER) {
                canvas.drawLine(hoverX - 9 * d, hoverY, hoverX - 4 * d, hoverY, uiStroke)
                canvas.drawLine(hoverX + 4 * d, hoverY, hoverX + 9 * d, hoverY, uiStroke)
                canvas.drawLine(hoverX, hoverY - 9 * d, hoverX, hoverY - 4 * d, uiStroke)
                canvas.drawLine(hoverX, hoverY + 4 * d, hoverX, hoverY + 9 * d, uiStroke)
            }
        }
        // подсказка с размерами
        val h = hud
        if (h != null) {
            uiText.color = Color.WHITE
            val tw = uiText.measureText(h)
            val bx = (hudX + 24 * d).coerceAtMost(width - tw - 24 * d)
            val by = (hudY - 30 * d).coerceAtLeast(40 * d)
            uiFill.color = Color.argb(215, 24, 24, 40)
            canvas.drawRoundRect(bx - 10 * d, by - 20 * d, bx + tw + 10 * d, by + 8 * d, 10 * d, 10 * d, uiFill)
            canvas.drawText(h, bx, by, uiText)
        }
    }

    private fun drawRuler(canvas: Canvas) {
        val d = resources.displayMetrics.density
        canvas.save()
        canvas.translate(rcx, rcy)
        canvas.rotate(Math.toDegrees(rAng.toDouble()).toFloat())
        val L = rLen; val T = rThick
        uiFill.color = Color.argb(150, 235, 238, 250)
        canvas.drawRoundRect(-L / 2, -T / 2, L / 2, T / 2, 10 * d, 10 * d, uiFill)
        uiStroke.color = Color.argb(200, 79, 70, 229); uiStroke.strokeWidth = 1.5f * d
        canvas.drawRoundRect(-L / 2, -T / 2, L / 2, T / 2, 10 * d, 10 * d, uiStroke)
        // деления в сантиметрах мира
        val cm = pxPerCm * scale
        var stepMm = cm / 10f
        val showMm = stepMm >= 4f
        uiStroke.strokeWidth = 1f * d
        uiStroke.color = Color.argb(220, 40, 40, 60)
        uiText.color = Color.argb(230, 40, 40, 60)
        uiText.textAlign = Paint.Align.CENTER
        val n = (L / 2 / (if (showMm) stepMm else cm)).toInt()
        if (!showMm) stepMm = cm
        var labelEvery = 1
        while (cm * labelEvery < 34 * d) labelEvery *= 2
        for (k in -n..n) {
            val x = k * stepMm
            val isCm = if (showMm) k % 10 == 0 else true
            val isHalf = showMm && k % 5 == 0
            val len = if (isCm) 18f * d else if (isHalf) 12f * d else 7f * d
            canvas.drawLine(x, -T / 2, x, -T / 2 + len, uiStroke)
            canvas.drawLine(x, T / 2, x, T / 2 - len, uiStroke)
            if (isCm) {
                val cmIdx = if (showMm) k / 10 else k
                if (cmIdx % labelEvery == 0) canvas.drawText("${abs(cmIdx)}", x, -T / 2 + 32 * d, uiText)
            }
        }
        val deg = ((-Math.toDegrees(rAng.toDouble()) % 180 + 180) % 180).roundToInt()
        uiText.textSize = 15f * d
        canvas.drawText("$deg°", 0f, T / 2 - 22 * d, uiText)
        uiText.textSize = 13f * d
        uiText.textAlign = Paint.Align.LEFT
        canvas.restore()
    }

    // ------------------------------------------------------------------ ввод: роли указателей

    private val ROLE_PEN = 1
    private val ROLE_NAV = 2
    private val ROLE_IGNORE = 3
    private val ROLE_RULER = 4
    private val roles = HashMap<Int, Int>()
    private var penId = -1
    private var penIsStylus = false
    private var lastStylusTime = 0L
    private val navPts = HashMap<Int, PointF>()
    private var navPrevDist = 0f
    private var navPrevCx = 0f
    private var navPrevCy = 0f
    private var tapStart = 0L
    private var tapMaxFingers = 0
    private var tapMoved = false
    private val rulerPts = HashMap<Int, PointF>()
    var lastTouchMajor = 0f; private set

    /** Эффективный режим ввода с учётом автоопределения. */
    var passivePenConnected = false

    fun effectiveInputMode(): Int = when (prefs.inputMode) {
        0 -> if (prefs.stylusSeen) 1 else if (passivePenConnected) 2 else 3
        else -> prefs.inputMode
    }

    private fun stylusDetected() {
        lastStylusTime = SystemClock.uptimeMillis()
        if (!prefs.stylusSeen) {
            prefs.stylusSeen = true
            host?.onStylusDetected()
        }
    }

    private fun isStylusType(tt: Int) = tt == MotionEvent.TOOL_TYPE_STYLUS || tt == MotionEvent.TOOL_TYPE_ERASER

    private fun classify(e: MotionEvent, idx: Int): Int {
        val tt = e.getToolType(idx)
        if (isStylusType(tt)) { stylusDetected(); return ROLE_PEN }
        if (tt == MotionEvent.TOOL_TYPE_MOUSE) return ROLE_PEN
        lastTouchMajor = e.getTouchMajor(idx)
        // ладонь: касание во время работы стилуса или сразу после наведения
        val now = SystemClock.uptimeMillis()
        if ((penId != -1 && penIsStylus) || now - lastStylusTime < 350) return ROLE_IGNORE
        if (rulerVisible && rulerHit(e.getX(idx), e.getY(idx)) && penId == -1) return ROLE_RULER
        return when (effectiveInputMode()) {
            1 -> ROLE_NAV
            2 -> {
                val sz = lastTouchMajor
                when {
                    sz > prefs.palmSize * 3f -> ROLE_IGNORE
                    sz <= prefs.palmSize -> if (navPts.isEmpty()) ROLE_PEN else ROLE_NAV
                    else -> ROLE_NAV
                }
            }
            else -> if (navPts.isEmpty() && penId == -1) ROLE_PEN else ROLE_NAV
        }
    }

    private fun handleButtons(e: MotionEvent) {
        val tt = e.getToolType(0)
        var mask = MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY
        if (isStylusType(tt)) mask = mask or MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_TERTIARY
        val st = e.buttonState and mask
        if ((st != 0) != (lastButtons != 0)) {
            if (st != 0) buttons.press() else buttons.release()
        }
        lastButtons = st
    }

    override fun onHoverEvent(e: MotionEvent): Boolean {
        handleButtons(e)
        val tt = e.getToolType(0)
        if (isStylusType(tt)) stylusDetected()
        when (e.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                hovering = true; hoverX = e.x; hoverY = e.y
                eraserTip = tt == MotionEvent.TOOL_TYPE_ERASER
                invalidate()
            }
            MotionEvent.ACTION_HOVER_EXIT -> { hovering = false; invalidate() }
        }
        return true
    }

    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        handleButtons(e)
        if (e.actionMasked == MotionEvent.ACTION_SCROLL) {
            val v = e.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (e.metaState and android.view.KeyEvent.META_CTRL_ON != 0) zoomAround(if (v > 0) 1.1f else 0.9f, e.x, e.y)
            else { ty += v * 60f; tx -= e.getAxisValue(MotionEvent.AXIS_HSCROLL) * 60f; saveView(); invalidateCache() }
            return true
        }
        return super.onGenericMotionEvent(e)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        handleButtons(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> pointerDown(e, e.actionIndex)
            MotionEvent.ACTION_MOVE -> pointerMove(e)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> pointerUp(e, e.actionIndex)
            MotionEvent.ACTION_CANCEL -> {
                if (penId != -1) cancelOp()
                penId = -1; roles.clear(); navPts.clear(); rulerPts.clear(); endNav()
            }
        }
        return true
    }

    private fun pointerDown(e: MotionEvent, idx: Int) {
        val id = e.getPointerId(idx)
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            tapStart = SystemClock.uptimeMillis(); tapMaxFingers = 0; tapMoved = false
        }
        var role = classify(e, idx)
        val tt = e.getToolType(idx)
        if (role == ROLE_PEN) {
            if (penId != -1) {
                // второй палец при рисовании пальцем → переходим к навигации
                if (!penIsStylus && !isStylusType(tt)) {
                    val pIdx = e.findPointerIndex(penId)
                    cancelOp()
                    roles[penId] = ROLE_NAV
                    if (pIdx >= 0) navPts[penId] = PointF(e.getX(pIdx), e.getY(pIdx))
                    penId = -1
                    role = ROLE_NAV
                } else { roles[id] = ROLE_IGNORE; return }
            } else if (isStylusType(tt) && navPts.isNotEmpty()) {
                // стилус важнее жестов пальцами
                for (k in navPts.keys) roles[k] = ROLE_IGNORE
                navPts.clear(); endNav()
            }
        }
        roles[id] = role
        when (role) {
            ROLE_PEN -> {
                penId = id
                penIsStylus = isStylusType(tt)
                eraserTip = tt == MotionEvent.TOOL_TYPE_ERASER
                if (penIsStylus) { requestUnbufferedDispatch(e); hovering = false }
                val x = e.getX(idx); val y = e.getY(idx)
                toolDown(wx(x), wy(y), pressureOf(e, idx, -1), x, y)
            }
            ROLE_NAV -> {
                navPts[id] = PointF(e.getX(idx), e.getY(idx))
                tapMaxFingers = max(tapMaxFingers, navPts.size)
                resetNav()
            }
            ROLE_RULER -> {
                rulerPts[id] = PointF(e.getX(idx), e.getY(idx))
            }
        }
    }

    private fun pressureOf(e: MotionEvent, idx: Int, h: Int): Float {
        val tt = e.getToolType(idx)
        if (!prefs.pressure || !isStylusType(tt)) return 0.55f
        val p = if (h < 0) e.getPressure(idx) else e.getHistoricalPressure(idx, h)
        return p.coerceIn(0.02f, 1f)
    }

    private fun pointerMove(e: MotionEvent) {
        // перо
        if (penId != -1) {
            val idx = e.findPointerIndex(penId)
            if (idx >= 0) {
                val act = activeTool
                val hist = if (act == Tool.PEN || act == Tool.PENCIL || act == Tool.MARKER || act == Tool.ERASER || act == Tool.LASER) e.historySize else 0
                for (h in 0 until hist) {
                    val x = e.getHistoricalX(idx, h); val y = e.getHistoricalY(idx, h)
                    toolMove(wx(x), wy(y), pressureOf(e, idx, h), x, y)
                }
                val x = e.getX(idx); val y = e.getY(idx)
                toolMove(wx(x), wy(y), pressureOf(e, idx, -1), x, y)
            }
        }
        // навигация
        if (navPts.isNotEmpty()) {
            for (k in navPts.keys) {
                val i = e.findPointerIndex(k)
                if (i >= 0) {
                    val p = navPts[k]!!
                    if (hypot(e.getX(i) - p.x, e.getY(i) - p.y) > 12f && SystemClock.uptimeMillis() - tapStart > 0) tapMoved = tapMoved || hypot(e.getX(i) - p.x, e.getY(i) - p.y) > 18f
                    p.set(e.getX(i), e.getY(i))
                }
            }
            navUpdate()
        }
        // линейка
        if (rulerPts.isNotEmpty()) {
            val ids = rulerPts.keys.toList()
            val old = ids.map { PointF(rulerPts[it]!!.x, rulerPts[it]!!.y) }
            for (k in ids) { val i = e.findPointerIndex(k); if (i >= 0) rulerPts[k]!!.set(e.getX(i), e.getY(i)) }
            val nw = ids.map { rulerPts[it]!! }
            if (ids.size == 1) {
                rcx += nw[0].x - old[0].x; rcy += nw[0].y - old[0].y
            } else if (ids.size >= 2) {
                val oc = PointF((old[0].x + old[1].x) / 2, (old[0].y + old[1].y) / 2)
                val nc = PointF((nw[0].x + nw[1].x) / 2, (nw[0].y + nw[1].y) / 2)
                val oa = atan2(old[1].y - old[0].y, old[1].x - old[0].x)
                val na = atan2(nw[1].y - nw[0].y, nw[1].x - nw[0].x)
                rcx += nc.x - oc.x; rcy += nc.y - oc.y
                rAng += na - oa
                // мягкая привязка к 0/45/90°
                val deg = Math.toDegrees(rAng.toDouble())
                val sn = Math.round(deg / 45.0) * 45.0
                if (abs(deg - sn) < 1.2) rAng = Math.toRadians(sn).toFloat()
            }
            invalidate()
        }
    }

    private fun pointerUp(e: MotionEvent, idx: Int) {
        val id = e.getPointerId(idx)
        val role = roles.remove(id)
        when (role) {
            ROLE_PEN -> if (id == penId) {
                val x = e.getX(idx); val y = e.getY(idx)
                toolUp(wx(x), wy(y))
                penId = -1
                if (penIsStylus) lastStylusTime = SystemClock.uptimeMillis()
                eraserTip = false
            }
            ROLE_NAV -> {
                navPts.remove(id)
                if (navPts.isEmpty()) {
                    val dt = SystemClock.uptimeMillis() - tapStart
                    if (!tapMoved && dt < 280 && prefs.gestureUndo) {
                        if (tapMaxFingers == 2) undo() else if (tapMaxFingers == 3) redo()
                    }
                    endNav()
                } else resetNav()
            }
            ROLE_RULER -> rulerPts.remove(id)
        }
        if (e.actionMasked == MotionEvent.ACTION_UP) { roles.clear(); navPts.clear(); rulerPts.clear(); if (navigating) endNav() }
    }

    private fun resetNav() {
        if (navPts.isEmpty()) return
        var cx = 0f; var cy = 0f
        for (p in navPts.values) { cx += p.x; cy += p.y }
        cx /= navPts.size; cy /= navPts.size
        navPrevCx = cx; navPrevCy = cy
        navPrevDist = if (navPts.size >= 2) navPts.values.take(2).let { hypot(it[0].x - it[1].x, it[0].y - it[1].y) } else 0f
    }

    private fun navUpdate() {
        if (navPts.size == 1 && !prefs.fingerPan && effectiveInputMode() != 3) return
        var cx = 0f; var cy = 0f
        for (p in navPts.values) { cx += p.x; cy += p.y }
        cx /= navPts.size; cy /= navPts.size
        navigating = true
        if (navPts.size >= 2) {
            val pts = navPts.values.take(2)
            val dist = hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y)
            if (navPrevDist > 10f && dist > 10f) {
                val f = dist / navPrevDist
                val ns = (scale * f).coerceIn(MIN_SCALE, MAX_SCALE)
                val wxp = wx(navPrevCx); val wyp = wy(navPrevCy)
                scale = ns
                tx = cx - wxp * ns; ty = cy - wyp * ns
            }
            navPrevDist = dist
        } else {
            tx += cx - navPrevCx; ty += cy - navPrevCy
        }
        navPrevCx = cx; navPrevCy = cy
        saveView()
        invalidate()
    }

    private fun endNav() {
        if (navigating) {
            navigating = false
            invalidateCache()
            host?.onStateChanged()
        }
    }

    private fun rulerLocal(sx: Float, sy: Float): PointF {
        val dx = sx - rcx; val dy = sy - rcy
        val c = cos(-rAng); val s = sin(-rAng)
        return PointF(dx * c - dy * s, dx * s + dy * c)
    }

    private fun rulerHit(sx: Float, sy: Float): Boolean {
        val p = rulerLocal(sx, sy)
        return abs(p.x) <= rLen / 2 && abs(p.y) <= rThick / 2
    }

    /** Проекция экранной точки на край линейки. */
    private fun rulerProject(sx: Float, sy: Float, edge: Int): PointF {
        val p = rulerLocal(sx, sy)
        val v = edge * rThick / 2
        val c = cos(rAng); val s = sin(rAng)
        return PointF(rcx + p.x * c - v * s, rcy + p.x * s + v * c)
    }

    // ------------------------------------------------------------------ привязки

    private fun snap(x: Float, y: Float, objects: Boolean = true): PointF {
        snapMark = null
        val tol = 16f / scale
        if (objects && prefs.snapObjects) {
            var best: PointF? = null
            var bd = tol
            val ctx = dctx
            for (e in doc.els) {
                if (e is StrokeEl) continue
                for (p in e.snapPoints(ctx)) {
                    val dd = Geo.dist(x, y, p.x, p.y)
                    if (dd < bd) { bd = dd; best = p }
                }
            }
            if (best != null) { snapMark = best; return PointF(best.x, best.y) }
        }
        if (prefs.snapGrid) {
            val ax = doc.axesAt(x, y)
            if (ax != null) {
                val s = ax.snap(x, y)
                if (Geo.dist(s.x, s.y, x, y) < tol * 0.8f) return s
                return PointF(x, y)
            }
            val s = Paper.snap(doc.paper, x, y)
            if (s != null && Geo.dist(s.first, s.second, x, y) < tol * 0.7f) return PointF(s.first, s.second)
        }
        return PointF(x, y)
    }

    private fun snapAngle(ax: Float, ay: Float, bx: Float, by: Float): PointF {
        val len = Geo.dist(ax, ay, bx, by)
        if (len < 1f) return PointF(bx, by)
        val a = Math.toDegrees(atan2((by - ay).toDouble(), (bx - ax).toDouble()))
        val sn = Math.round(a / 15.0) * 15.0
        if (abs(a - sn) < 3.5) {
            val r = Math.toRadians(sn)
            return PointF(ax + (len * cos(r)).toFloat(), ay + (len * sin(r)).toFloat())
        }
        return PointF(bx, by)
    }

    private fun unitAt(x1: Float, y1: Float, x2: Float, y2: Float): Pair<Float, String> {
        val a = doc.axesAt(x1, y1)
        if (a != null && a === doc.axesAt(x2, y2)) return a.unit to ""
        return pxPerCm to "см"
    }

    private fun lenText(px: Float, x1: Float, y1: Float, x2: Float, y2: Float): String {
        val (u, n) = unitAt(x1, y1, x2, y2)
        return Expr.fmt((px / u).toDouble(), 2) + if (n.isNotEmpty()) " $n" else " ед."
    }

    // ------------------------------------------------------------------ автоматические имена

    fun nextPointName(): String {
        val used = HashSet<String>()
        for (e in doc.els) {
            if (e is PointEl) used.add(e.name)
            if (e is PolyEl) e.names.split(',').forEach { used.add(it.trim()) }
        }
        for (k in 0..9) for (c in 'A'..'Z') {
            val n = if (k == 0) "$c" else "${c}_$k"
            if (n !in used) return n
        }
        return "P"
    }

    private fun nextNames(count: Int): String {
        val used = HashSet<String>()
        for (e in doc.els) {
            if (e is PointEl) used.add(e.name)
            if (e is PolyEl) e.names.split(',').forEach { used.add(it.trim()) }
        }
        val out = ArrayList<String>()
        var k = 0
        outer@ while (out.size < count) {
            for (c in 'A'..'Z') {
                val n = if (k == 0) "$c" else "${c}_$k"
                if (n !in used && n !in out) { out.add(n); if (out.size == count) break@outer }
            }
            k++
            if (k > 20) break
        }
        return out.joinToString(",")
    }

    private fun nextVectorName(): String {
        val used = doc.els.filterIsInstance<LineEl>().map { it.label }.joinToString(" ")
        for (c in "abcdefghklmnpqrsuvw") if (!used.contains("\\vec{$c}")) return "\\vec{$c}"
        return "\\vec{v}"
    }

    private fun nextAngleName(): String {
        val used = doc.els.filterIsInstance<AngleEl>().map { it.label }.toSet()
        for (c in listOf("α", "β", "γ", "δ", "φ", "ψ", "θ", "ω")) if (c !in used) return c
        return "α"
    }

    // ------------------------------------------------------------------ операции инструментов

    private fun cancelOp() {
        handler.removeCallbacks(recogRun)
        preview = null; curStroke = null; hud = null; snapMark = null; recognized = null
        lasso.clear(); selOp = 0; linkFrom = null
        if (activeTool != Tool.SHAPE && activeTool != Tool.ANGLE && activeTool != Tool.CIRCLE) taps.clear()
        opTx?.commit(); opTx = null
        if (liveIds.isNotEmpty()) { liveIds.clear(); invalidateCache() }
        invalidate()
    }

    private val recogRun = Runnable { tryRecognize() }

    private fun toolDown(x: Float, y: Float, p: Float, sxp: Float, syp: Float) {
        lastW.set(x, y)
        dragStart.set(x, y); dragCur.set(x, y)
        hudX = sxp; hudY = syp
        when (val t = activeTool) {
            Tool.PEN, Tool.PENCIL, Tool.MARKER -> {
                val s = StrokeEl()
                s.kind = when (t) { Tool.PENCIL -> 1; Tool.MARKER -> 2; else -> if (prefs.pressure) 0 else 1 }
                s.color = colorOf(t); s.width = widthOf(t)
                rulerLock = 0
                var px = x; var py = y
                if (rulerVisible) {
                    val lp = rulerLocal(sxp, syp)
                    if (abs(lp.x) <= rLen / 2) {
                        if (abs(lp.y + rThick / 2) < 40f) rulerLock = -1
                        else if (abs(lp.y - rThick / 2) < 40f) rulerLock = 1
                    }
                    if (rulerLock != 0) { val q = rulerProject(sxp, syp, rulerLock); px = wx(q.x); py = wy(q.y) }
                }
                s.add(px, py, p)
                smooth.set(px, py)
                curStroke = s; preview = s; recognized = null
                stillPos.set(x, y)
                if (prefs.shapeRecog && rulerLock == 0) { handler.removeCallbacks(recogRun); handler.postDelayed(recogRun, 650) }
            }
            Tool.ERASER -> {
                opTx = doc.begin()
                eraseAt(x, y, x, y)
            }
            Tool.SELECT -> selectDown(x, y, sxp, syp)
            Tool.LINE -> {
                val s = snap(x, y); dragStart.set(s.x, s.y)
                val l = makeLine(s.x, s.y, s.x, s.y)
                preview = l
            }
            Tool.CIRCLE -> {
                if (circleMode == 3) return
                val s = snap(x, y); dragStart.set(s.x, s.y)
                preview = CircleEl().also { it.cx = s.x; it.cy = s.y; it.rx = 0.1f; it.ry = 0.1f; it.color = colorOf(t); it.width = widthOf(t); it.showCenter = circleMode == 0 }
            }
            Tool.SHAPE -> {
                if (shapeMode == 6) return
                val s = snap(x, y); dragStart.set(s.x, s.y)
            }
            Tool.AXES, Tool.STAMP -> {
                val s = snap(x, y, false); dragStart.set(s.x, s.y); dragCur.set(s.x, s.y)
            }
            Tool.LINK -> {
                linkFrom = hitTop(x, y, 14f / scale) { it !is LinkEl }
                if (linkFrom == null) host?.toast("Начните связь на объекте")
            }
            Tool.LASER -> { laser.add(LaserPt(x, y, SystemClock.uptimeMillis())); invalidate() }
            Tool.HAND -> {}
            else -> {}
        }
        invalidate()
    }

    private fun toolMove(x: Float, y: Float, p: Float, sxp: Float, syp: Float) {
        val px = lastW.x; val py = lastW.y
        lastW.set(x, y)
        hudX = sxp; hudY = syp
        when (val t = activeTool) {
            Tool.PEN, Tool.PENCIL, Tool.MARKER -> {
                val rec = recognized
                if (rec != null) { adjustRecognized(rec, x, y); invalidate(); return }
                val s = curStroke ?: return
                var nx = x; var ny = y
                if (rulerLock != 0) { val q = rulerProject(sxp, syp, rulerLock); nx = wx(q.x); ny = wy(q.y) }
                else {
                    val k = when (prefs.smoothing) { 0 -> 1f; 1 -> 0.75f; 2 -> 0.55f; 3 -> 0.38f; else -> 0.25f }
                    nx = smooth.x + (x - smooth.x) * k
                    ny = smooth.y + (y - smooth.y) * k
                }
                val lx = s.x(s.n - 1); val ly = s.y(s.n - 1)
                if (Geo.dist(lx, ly, nx, ny) * scale < 0.9f) return
                smooth.set(nx, ny)
                s.add(nx, ny, p)
                if (prefs.shapeRecog && rulerLock == 0 && Geo.dist(stillPos.x, stillPos.y, x, y) * scale > 7f) {
                    stillPos.set(x, y)
                    handler.removeCallbacks(recogRun); handler.postDelayed(recogRun, 650)
                }
                if (t == Tool.MARKER) invalidate() else invalidate()
            }
            Tool.ERASER -> eraseAt(px, py, x, y)
            Tool.SELECT -> selectMove(x, y)
            Tool.LINE -> {
                var e = snap(x, y)
                if (snapMark == null) e = snapAngle(dragStart.x, dragStart.y, e.x, e.y)
                val l = preview as? LineEl ?: return
                l.x2 = e.x; l.y2 = e.y
                val len = Geo.dist(l.x1, l.y1, l.x2, l.y2)
                val ang = -Geo.angleDeg(l.x1, l.y1, l.x2, l.y2)
                hud = "${lenText(len, l.x1, l.y1, l.x2, l.y2)}   ∠ ${Expr.fmt(((ang + 360) % 360).toDouble(), 1)}°"
            }
            Tool.CIRCLE -> {
                val c = preview as? CircleEl ?: return
                val e = snap(x, y)
                when (circleMode) {
                    0 -> { val r = Geo.dist(c.cx, c.cy, e.x, e.y); c.rx = r; c.ry = r; hud = "r = ${lenText(r, c.cx, c.cy, e.x, e.y)}" }
                    1 -> {
                        c.cx = (dragStart.x + e.x) / 2; c.cy = (dragStart.y + e.y) / 2
                        val r = Geo.dist(dragStart.x, dragStart.y, e.x, e.y) / 2; c.rx = r; c.ry = r
                        hud = "d = ${lenText(r * 2, dragStart.x, dragStart.y, e.x, e.y)}"
                    }
                    2 -> {
                        c.cx = (dragStart.x + e.x) / 2; c.cy = (dragStart.y + e.y) / 2
                        c.rx = abs(e.x - dragStart.x) / 2; c.ry = abs(e.y - dragStart.y) / 2; c.showCenter = false
                        hud = "a = ${lenText(c.rx, c.cx, c.cy, e.x, e.y)}, b = ${lenText(c.ry, c.cx, c.cy, e.x, e.y)}"
                    }
                }
            }
            Tool.SHAPE -> {
                if (shapeMode == 6) return
                val e = snap(x, y)
                preview = makeShape(dragStart.x, dragStart.y, e.x, e.y, false)
                hud = "${lenText(abs(e.x - dragStart.x), dragStart.x, dragStart.y, e.x, e.y)} × ${lenText(abs(e.y - dragStart.y), dragStart.x, dragStart.y, e.x, e.y)}"
            }
            Tool.AXES -> {
                val e = snap(x, y, false); dragCur.set(e.x, e.y)
                preview = makeAxes(dragStart.x, dragStart.y, e.x, e.y)
            }
            Tool.STAMP -> {
                dragCur.set(x, y)
                preview = makeStamp(dragStart.x, dragStart.y, x, y)
            }
            Tool.LINK -> {
                val f = linkFrom ?: return
                val b = f.bounds(dctx)
                preview = LineEl().also { it.x1 = b.centerX(); it.y1 = b.centerY(); it.x2 = x; it.y2 = y; it.dash = true; it.arrowEnd = true; it.color = colorOf(Tool.LINK); it.width = widthOf(Tool.LINK) }
            }
            Tool.LASER -> { laser.add(LaserPt(x, y, SystemClock.uptimeMillis())); invalidate() }
            Tool.HAND -> {
                tx += (x - px) * scale; ty += (y - py) * scale
                lastW.set(px, py) // мир под пером не меняется
                saveView(); navigating = true
            }
            else -> {}
        }
        invalidate()
    }

    private fun toolUp(x: Float, y: Float) {
        handler.removeCallbacks(recogRun)
        hud = null
        when (val t = activeTool) {
            Tool.PEN, Tool.PENCIL, Tool.MARKER -> {
                val rec = recognized
                if (rec != null) { doc.add(rec) }
                else curStroke?.let { if (it.n > 0) doc.add(it) }
                curStroke = null; preview = null; recognized = null
                contentChanged()
            }
            Tool.ERASER -> { opTx?.commit(); opTx = null; contentChanged() }
            Tool.SELECT -> selectUp(x, y)
            Tool.LINE -> {
                val l = preview as? LineEl
                preview = null
                if (l != null && Geo.dist(l.x1, l.y1, l.x2, l.y2) * scale > 6f) {
                    val (u, n) = unitAt(l.x1, l.y1, l.x2, l.y2)
                    l.unit = u; l.unitName = n
                    if (lineMode == 2 && prefs.autoName) {
                        l.label = nextVectorName()
                        val ax = doc.axesAt(l.x1, l.y1)
                        if (ax != null && ax === doc.axesAt(l.x2, l.y2)) {
                            val a = ax.toUnits(l.x1, l.y1); val b = ax.toUnits(l.x2, l.y2)
                            l.label += " = (${Expr.fmt((b.x - a.x).toDouble(), 2)}; ${Expr.fmt((b.y - a.y).toDouble(), 2)})"
                        }
                    }
                    doc.add(l); contentChanged()
                }
            }
            Tool.CIRCLE -> {
                if (circleMode == 3) { circleTap(x, y); return }
                val c = preview as? CircleEl
                preview = null
                if (c != null && max(c.rx, c.ry) * scale > 4f) {
                    val (u, n) = unitAt(c.cx, c.cy, c.cx + c.rx, c.cy)
                    c.unit = u; c.unitName = n; c.showRadius = circleShowRadius && c.isCircle
                    if (shapeFill) c.fill = El.withAlpha(c.color, 0.15f)
                    doc.add(c); contentChanged()
                }
            }
            Tool.SHAPE -> {
                if (shapeMode == 6) { polygonTap(x, y); return }
                val e = snap(x, y)
                preview = null
                if (Geo.dist(dragStart.x, dragStart.y, e.x, e.y) * scale > 8f) {
                    doc.add(makeShape(dragStart.x, dragStart.y, e.x, e.y, true)); contentChanged()
                }
            }
            Tool.TEXT -> {
                val hit = hitTop(x, y, 10f / scale) { it is TextEl || it is TableEl }
                when (hit) {
                    is TextEl -> host?.requestText(hit.x, hit.y, hit)
                    is TableEl -> host?.requestEdit(hit)
                    else -> host?.requestText(x, y, null)
                }
            }
            Tool.POINT -> {
                val s = snap(x, y)
                val pe = PointEl().also {
                    it.x = s.x; it.y = s.y; it.color = colorOf(t); it.width = widthOf(t)
                    it.name = if (prefs.autoName) nextPointName() else ""
                    it.showCoords = doc.axesAt(s.x, s.y) != null
                }
                doc.add(pe); contentChanged()
            }
            Tool.ANGLE -> angleTap(x, y)
            Tool.AXES -> {
                val a = makeAxes(dragStart.x, dragStart.y, dragCur.x, dragCur.y)
                preview = null
                doc.add(a); contentChanged()
                host?.toast("Оси созданы. «+» → График функции, чтобы построить на них")
            }
            Tool.STAMP -> {
                val s = makeStamp(dragStart.x, dragStart.y, dragCur.x, dragCur.y)
                preview = null
                doc.add(s); contentChanged()
            }
            Tool.LINK -> {
                val f = linkFrom
                preview = null; linkFrom = null
                if (f != null) {
                    val to = hitTop(x, y, 14f / scale) { it !is LinkEl && it !== f }
                    if (to != null) {
                        val l = LinkEl().also { it.from = f.id; it.to = to.id; it.style = linkStyle; it.color = colorOf(t); it.width = widthOf(t) }
                        doc.add(l); contentChanged()
                        host?.requestLinkLabel(l)
                    } else host?.toast("Отпустите перо на втором объекте")
                }
            }
            Tool.HAND -> { navigating = false; invalidateCache() }
            else -> {}
        }
        snapMark = null
        invalidate()
    }

    // ------------------------------------------------------------------ фигуры

    private fun makeLine(x1: Float, y1: Float, x2: Float, y2: Float) = LineEl().also {
        it.x1 = x1; it.y1 = y1; it.x2 = x2; it.y2 = y2
        it.color = colorOf(Tool.LINE); it.width = widthOf(Tool.LINE)
        it.arrowEnd = lineMode == 1 || lineMode == 2 || lineMode == 5
        it.arrowStart = lineMode == 5
        it.dash = lineMode == 3
        it.infinite = lineMode == 4
        it.showLen = prefs.showLengths && lineMode != 4
    }

    private fun makeShape(x1: Float, y1: Float, x2: Float, y2: Float, final: Boolean): PolyEl {
        val p = PolyEl()
        p.color = colorOf(Tool.SHAPE); p.width = widthOf(Tool.SHAPE)
        if (shapeFill) p.fill = El.withAlpha(p.color, 0.15f)
        var bx = x2; var by = y2
        if (shapeMode == 1) {
            val s = max(abs(x2 - x1), abs(y2 - y1))
            bx = x1 + s * (if (x2 >= x1) 1 else -1); by = y1 + s * (if (y2 >= y1) 1 else -1)
        }
        val l = min(x1, bx); val r = max(x1, bx); val t = min(y1, by); val b = max(y1, by)
        p.pts = when (shapeMode) {
            0, 1 -> floatArrayOf(l, t, r, t, r, b, l, b)
            2 -> floatArrayOf((l + r) / 2, t, r, b, l, b)
            3 -> floatArrayOf(l, t, r, b, l, b)
            4 -> { val k = (r - l) * 0.25f; floatArrayOf(l + k, t, r, t, r - k, b, l, b) }
            5 -> { val k = (r - l) * 0.22f; floatArrayOf(l + k, t, r - k, t, r, b, l, b) }
            7 -> {
                val cx = (l + r) / 2; val cy = (t + b) / 2; val rr = min(r - l, b - t) / 2
                FloatArray(12) { i -> val a = (i / 2) * PI / 3 - PI / 2; if (i % 2 == 0) cx + (rr * cos(a)).toFloat() else cy + (rr * sin(a)).toFloat() }
            }
            else -> floatArrayOf(l, t, r, t, r, b, l, b)
        }
        if (final && prefs.autoName) p.names = nextNames(p.count)
        return p
    }

    private fun polygonTap(x: Float, y: Float) {
        val s = snap(x, y)
        if (taps.size >= 3 && Geo.dist(s.x, s.y, taps[0].x, taps[0].y) * scale < 24f) {
            finishPolygon(true); return
        }
        val now = SystemClock.uptimeMillis()
        if (taps.size >= 2 && now - lastTapTime < 350 && Geo.dist(s.x, s.y, lastTapPos.x, lastTapPos.y) * scale < 20f) {
            finishPolygon(false); return
        }
        lastTapTime = now; lastTapPos.set(s.x, s.y)
        taps.add(s)
        val p = PolyEl(); p.color = colorOf(Tool.SHAPE); p.width = widthOf(Tool.SHAPE); p.closed = false
        p.pts = FloatArray(taps.size * 2) { if (it % 2 == 0) taps[it / 2].x else taps[it / 2].y }
        preview = p
        hud = "Вершин: ${taps.size}. Коснитесь первой вершины, чтобы замкнуть; двойное касание — ломаная"
        invalidate()
    }

    fun finishPolygon(closed: Boolean) {
        if (taps.size >= 2) {
            val p = PolyEl(); p.color = colorOf(Tool.SHAPE); p.width = widthOf(Tool.SHAPE); p.closed = closed
            if (closed && shapeFill) p.fill = El.withAlpha(p.color, 0.15f)
            p.pts = FloatArray(taps.size * 2) { if (it % 2 == 0) taps[it / 2].x else taps[it / 2].y }
            if (prefs.autoName) p.names = nextNames(p.count)
            doc.add(p); contentChanged()
        }
        taps.clear(); preview = null; hud = null
        invalidate()
    }

    private fun angleTap(x: Float, y: Float) {
        val s = snap(x, y)
        taps.add(s)
        val a = AngleEl(); a.color = colorOf(Tool.ANGLE); a.width = widthOf(Tool.ANGLE)
        when (taps.size) {
            1 -> { hud = "Коснитесь вершины угла"; preview = null }
            2 -> {
                hud = "Коснитесь второй стороны"
                preview = LineEl().also { it.x1 = taps[0].x; it.y1 = taps[0].y; it.x2 = taps[1].x; it.y2 = taps[1].y; it.color = a.color; it.width = a.width }
            }
            else -> {
                a.ax = taps[0].x; a.ay = taps[0].y; a.vx = taps[1].x; a.vy = taps[1].y; a.bx = taps[2].x; a.by = taps[2].y
                if (prefs.autoName) a.label = nextAngleName()
                doc.add(a); contentChanged()
                taps.clear(); preview = null; hud = null
            }
        }
        invalidate()
    }

    private fun circleTap(x: Float, y: Float) {
        val s = snap(x, y)
        taps.add(s)
        if (taps.size < 3) { hud = "Окружность по 3 точкам: ${taps.size}/3"; invalidate(); return }
        val (ax, ay) = taps[0].x to taps[0].y
        val (bx, by) = taps[1].x to taps[1].y
        val (cx, cy) = taps[2].x to taps[2].y
        val d = 2 * (ax * (by - cy) + bx * (cy - ay) + cx * (ay - by))
        taps.clear(); hud = null
        if (abs(d) < 1e-3f) { host?.toast("Точки лежат на одной прямой"); invalidate(); return }
        val ux = ((ax * ax + ay * ay) * (by - cy) + (bx * bx + by * by) * (cy - ay) + (cx * cx + cy * cy) * (ay - by)) / d
        val uy = ((ax * ax + ay * ay) * (cx - bx) + (bx * bx + by * by) * (ax - cx) + (cx * cx + cy * cy) * (bx - ax)) / d
        val r = Geo.dist(ux, uy, ax, ay)
        val c = CircleEl().also {
            it.cx = ux; it.cy = uy; it.rx = r; it.ry = r; it.color = colorOf(Tool.CIRCLE); it.width = widthOf(Tool.CIRCLE)
            val (u, n) = unitAt(ux, uy, ux + r, uy); it.unit = u; it.unitName = n; it.showRadius = circleShowRadius
        }
        doc.add(c); contentChanged()
        invalidate()
    }

    private fun makeAxes(x1: Float, y1: Float, x2: Float, y2: Float): AxesEl {
        val a = AxesEl()
        a.color = colorOf(Tool.AXES)
        val w = abs(x2 - x1); val h = abs(y2 - y1)
        val u = Paper.GRID
        a.unit = u
        if (w * scale < 40f || h * scale < 40f) {
            a.ox = x1; a.oy = y1
            a.xMin = -6f; a.xMax = 6f; a.yMin = -5f; a.yMax = 5f
        } else {
            val cx = Paper.snap(1, (x1 + x2) / 2, (y1 + y2) / 2) ?: ((x1 + x2) / 2 to (y1 + y2) / 2)
            a.ox = cx.first; a.oy = cx.second
            a.xMin = ((min(x1, x2) - a.ox) / u).roundToInt().toFloat().coerceAtMost(-1f)
            a.xMax = ((max(x1, x2) - a.ox) / u).roundToInt().toFloat().coerceAtLeast(1f)
            a.yMin = -((max(y1, y2) - a.oy) / u).roundToInt().toFloat().coerceAtLeast(1f)
            a.yMax = -((min(y1, y2) - a.oy) / u).roundToInt().toFloat().coerceAtMost(-1f)
        }
        return a
    }

    private fun makeStamp(x1: Float, y1: Float, x2: Float, y2: Float): StampEl {
        val d = Stamps.def(stampKind)
        val s = StampEl()
        s.kind = stampKind; s.color = colorOf(Tool.STAMP); s.width = widthOf(Tool.STAMP)
        if (Geo.dist(x1, y1, x2, y2) * scale < 20f) {
            s.cx = x1; s.cy = y1; s.w = d.w; s.h = d.h
        } else {
            s.cx = (x1 + x2) / 2; s.cy = (y1 + y2) / 2
            s.w = max(abs(x2 - x1), 10f); s.h = max(abs(y2 - y1), 10f)
        }
        return s
    }

    // ------------------------------------------------------------------ распознавание фигур (удержание пера)

    private fun tryRecognize() {
        val s = curStroke ?: return
        if (penId == -1 || s.n < 6) return
        val n = s.n
        val xs = FloatArray(n) { s.x(it) }; val ys = FloatArray(n) { s.y(it) }
        var len = 0f
        for (i in 1 until n) len += Geo.dist(xs[i - 1], ys[i - 1], xs[i], ys[i])
        val b = s.bounds(dctx)
        val diag = hypot(b.width(), b.height())
        if (len * scale < 40f) return
        val c = s.color; val w = max(1.5f, s.width * 0.8f)
        val endGap = Geo.dist(xs[0], ys[0], xs[n - 1], ys[n - 1])
        val shape: El? = if (endGap > 0.22f * len && endGap > 0.3f * diag) {
            // незамкнутая: прямая?
            if (endGap / len > 0.92f) LineEl().also {
                it.x1 = xs[0]; it.y1 = ys[0]
                val e = snapAngle(xs[0], ys[0], xs[n - 1], ys[n - 1]); it.x2 = e.x; it.y2 = e.y
                it.color = c; it.width = w
            } else {
                val keep = Geo.rdp(xs, ys, diag * 0.07f)
                if (keep.size in 3..6) PolyEl().also {
                    it.closed = false; it.color = c; it.width = w
                    it.pts = FloatArray(keep.size * 2) { k -> if (k % 2 == 0) xs[keep[k / 2]] else ys[keep[k / 2]] }
                } else null
            }
        } else {
            // замкнутая: окружность/эллипс или многоугольник
            var cx = 0f; var cy = 0f
            for (i in 0 until n) { cx += xs[i]; cy += ys[i] }
            cx /= n; cy /= n
            val ds = FloatArray(n) { Geo.dist(cx, cy, xs[it], ys[it]) }
            val mean = ds.average().toFloat()
            val std = sqrt(ds.map { (it - mean) * (it - mean) }.average()).toFloat()
            val keep = Geo.rdp(xs, ys, diag * 0.085f)
            val corners = keep.size - (if (Geo.dist(xs[keep.first()], ys[keep.first()], xs[keep.last()], ys[keep.last()]) < diag * 0.2f) 1 else 0)
            if (std / mean < 0.13f && corners > 4) {
                CircleEl().also { it.cx = cx; it.cy = cy; it.rx = mean; it.ry = mean; it.color = c; it.width = w; it.showCenter = false }
            } else if (corners in 3..6 && std / mean >= 0.06f) {
                val idx = keep.take(corners)
                var pts = FloatArray(corners * 2) { k -> if (k % 2 == 0) xs[idx[k / 2]] else ys[idx[k / 2]] }
                if (corners == 4) {
                    // почти прямоугольник, выровненный по осям → идеальный прямоугольник
                    val bl = b.left + s.width; val br = b.right - s.width; val bt = b.top + s.width; val bb = b.bottom - s.width
                    var aligned = true
                    for (k in 0 until 4) {
                        val x1 = pts[k * 2]; val y1 = pts[k * 2 + 1]; val x2 = pts[(k * 2 + 2) % 8]; val y2 = pts[(k * 2 + 3) % 8]
                        val a = abs(Geo.angleDeg(x1, y1, x2, y2)) % 90f
                        if (a > 12f && a < 78f) aligned = false
                    }
                    if (aligned) pts = floatArrayOf(bl, bt, br, bt, br, bb, bl, bb)
                }
                PolyEl().also { it.closed = true; it.color = c; it.width = w; it.pts = pts }
            } else if (std / mean < 0.22f) {
                CircleEl().also { it.cx = b.centerX(); it.cy = b.centerY(); it.rx = b.width() / 2 - s.width / 2; it.ry = b.height() / 2 - s.width / 2; it.color = c; it.width = w; it.showCenter = false }
            } else null
        }
        if (shape != null) {
            if (s.kind == 2) { host?.toast("Маркер не превращается в фигуры"); return }
            recognized = shape
            preview = shape
            curStroke = null
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            invalidate()
        }
    }

    private fun adjustRecognized(e: El, x: Float, y: Float) {
        when (e) {
            is LineEl -> { val p = snapAngle(e.x1, e.y1, x, y); e.x2 = p.x; e.y2 = p.y }
            is CircleEl -> if (e.isCircle) { val r = Geo.dist(e.cx, e.cy, x, y); e.rx = r; e.ry = r }
        }
    }

    // ------------------------------------------------------------------ ластик

    private fun eraseAt(x0: Float, y0: Float, x1: Float, y1: Float) {
        val tx = opTx ?: return
        val r = widthOf(Tool.ERASER) / 2 / scale
        val steps = max(1, (Geo.dist(x0, y0, x1, y1) / (r * 0.7f)).toInt())
        var changed = false
        val ctx = dctx
        for (k in 0..steps) {
            val x = x0 + (x1 - x0) * k / steps
            val y = y0 + (y1 - y0) * k / steps
            val it = doc.els.listIterator(doc.els.size)
            val toReplace = ArrayList<Pair<El, List<StrokeEl>>>()
            val toRemove = ArrayList<El>()
            while (it.hasPrevious()) {
                val e = it.previous()
                if (eraserMode == 1 && e is StrokeEl) {
                    val b = e.bounds(ctx)
                    if (x < b.left - r || x > b.right + r || y < b.top - r || y > b.bottom + r) continue
                    val parts = e.eraseAt(x, y, r) ?: continue
                    toReplace.add(e to parts)
                } else if (e.hit(x, y, r, ctx)) {
                    if (e is AxesEl || e is PlotEl) continue // оси и графики стираются через выделение
                    toRemove.add(e)
                }
            }
            for ((e, parts) in toReplace) {
                val i = doc.els.indexOf(e)
                if (i < 0) continue
                tx.touch(e)
                doc.els.removeAt(i)
                doc.els.addAll(i, parts)
                for (p in parts) tx.added(p)
                changed = true
            }
            if (toRemove.isNotEmpty()) {
                val all = LinkedHashSet<El>(toRemove)
                all.addAll(doc.dependents(toRemove.map { it.id }.toSet()))
                for (e in all) { tx.touch(e) }
                doc.els.removeAll(all)
                changed = true
            }
        }
        if (changed) invalidateCache()
    }

    // ------------------------------------------------------------------ выделение

    private fun hitTop(x: Float, y: Float, tol: Float, filter: (El) -> Boolean = { true }): El? {
        val ctx = dctx
        for (i in doc.els.indices.reversed()) {
            val e = doc.els[i]
            if (e.layer == 1 && filter(e) && e.hit(x, y, tol, ctx)) return e
        }
        for (i in doc.els.indices.reversed()) {
            val e = doc.els[i]
            if (e.layer == 0 && filter(e) && e.hit(x, y, tol, ctx)) return e
        }
        return null
    }

    private fun selectDown(x: Float, y: Float, sxp: Float, syp: Float) {
        val sb = selBounds()
        val d = resources.displayMetrics.density
        if (sb != null) {
            val r = RectF(sx(sb.left), sy(sb.top), sx(sb.right), sy(sb.bottom)); r.inset(-6f, -6f)
            val hr = 22f * d
            // поворот
            if (hypot(sxp - r.centerX(), syp - (r.top - 34f * d)) < hr) {
                selOp = 4; selCenter.set(sb.centerX(), sb.centerY()); beginTransform(); return
            }
            val corners = listOf(r.left to r.top, r.right to r.top, r.left to r.bottom, r.right to r.bottom)
            for ((i, c) in corners.withIndex()) {
                if (hypot(sxp - c.first, syp - c.second) < hr) {
                    val opp = corners[3 - i]
                    selAnchor.set(wx(opp.first), wy(opp.second))
                    selOp = 3; beginTransform(); return
                }
            }
            if (r.contains(sxp, syp)) { selOp = 2; beginTransform(); return }
        }
        selOp = 1
        lasso.clear(); lasso.add(PointF(x, y))
    }

    private fun beginTransform() {
        opTx = doc.begin()
        val movable = selection.filter { it.movable }
        for (e in movable) opTx!!.touch(e)
        liveIds.clear()
        liveIds.addAll(selection.map { it.id })
        liveIds.addAll(doc.dependents(selection.map { it.id }.toSet()).map { it.id })
        invalidateCache()
    }

    private fun selectMove(x: Float, y: Float) {
        when (selOp) {
            1 -> { lasso.add(PointF(x, y)); invalidate() }
            2, 3, 4 -> {
                val m = Matrix()
                when (selOp) {
                    2 -> m.setTranslate(x - dragCur.x, y - dragCur.y)
                    3 -> {
                        val d0 = Geo.dist(selAnchor.x, selAnchor.y, dragCur.x, dragCur.y)
                        val d1 = Geo.dist(selAnchor.x, selAnchor.y, x, y)
                        if (d0 < 1e-3f) return
                        val k = (d1 / d0).coerceIn(0.2f, 5f)
                        m.setScale(k, k, selAnchor.x, selAnchor.y)
                    }
                    4 -> {
                        val a0 = atan2(dragCur.y - selCenter.y, dragCur.x - selCenter.x)
                        val a1 = atan2(y - selCenter.y, x - selCenter.x)
                        m.setRotate(Math.toDegrees((a1 - a0).toDouble()).toFloat(), selCenter.x, selCenter.y)
                    }
                }
                for (e in selection) if (e.movable) { e.transform(m); e.invalidateCache() }
                dragCur.set(x, y)
                invalidate()
            }
        }
    }

    private fun selectUp(x: Float, y: Float) {
        when (selOp) {
            1 -> {
                val pts = lasso
                val ctx = dctx
                var len = 0f
                for (i in 1 until pts.size) len += Geo.dist(pts[i - 1].x, pts[i - 1].y, pts[i].x, pts[i].y)
                selection.clear()
                if (len * scale < 12f) {
                    // касание: выбрать верхний объект
                    val hit = hitTop(x, y, 12f / scale)
                    val now = SystemClock.uptimeMillis()
                    if (hit != null) {
                        selection.add(hit)
                        if (now - lastTapTime < 400 && Geo.dist(x, y, lastTapPos.x, lastTapPos.y) * scale < 30f) host?.requestEdit(hit)
                    }
                    lastTapTime = now; lastTapPos.set(x, y)
                } else if (selectMode == 1) {
                    val a = pts.first(); val b = pts.last()
                    val r = RectF(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))
                    for (e in doc.els) { val bb = e.bounds(ctx); if (r.contains(bb.centerX(), bb.centerY()) && (r.contains(bb) || e !is AxesEl)) selection.add(e) }
                } else {
                    val poly = FloatArray(pts.size * 2) { if (it % 2 == 0) pts[it / 2].x else pts[it / 2].y }
                    for (e in doc.els) {
                        val inside = if (e is StrokeEl) {
                            var c = 0
                            val step = max(1, e.n / 12)
                            var tot = 0
                            var i = 0
                            while (i < e.n) { tot++; if (Geo.pointInPoly(e.x(i), e.y(i), poly, pts.size)) c++; i += step }
                            c >= tot * 0.6f
                        } else {
                            val bb = e.bounds(ctx)
                            Geo.pointInPoly(bb.centerX(), bb.centerY(), poly, pts.size)
                        }
                        if (inside) selection.add(e)
                    }
                    // графики выбираются вместе с их осями
                    val axIds = selection.filterIsInstance<AxesEl>().map { it.id }.toSet()
                    for (e in doc.els) if (e is PlotEl && e.axesId in axIds && e !in selection) selection.add(e)
                }
                lasso.clear()
                host?.onStateChanged()
            }
            2, 3, 4 -> {
                opTx?.commit(); opTx = null
                liveIds.clear()
                contentChanged()
                // заменить ссылки выделения на актуальные объекты
            }
        }
        selOp = 0
        invalidate()
    }

    // ------------------------------------------------------------------ операции над выделением

    fun deleteSelection() {
        if (selection.isEmpty()) return
        doc.remove(selection.toList())
        selection.clear()
        contentChanged()
    }

    fun duplicateSelection() {
        if (selection.isEmpty()) return
        val map = HashMap<Long, Long>()
        val copies = ArrayList<El>()
        val m = Matrix().apply { setTranslate(30f / scale, 30f / scale) }
        for (e in selection) {
            val c = e.copy(true)
            map[e.id] = c.id
            copies.add(c)
        }
        for (c in copies) {
            if (c is PlotEl) map[c.axesId]?.let { c.axesId = it }
            if (c is LinkEl) { map[c.from]?.let { c.from = it }; map[c.to]?.let { c.to = it } }
            if (c.movable) c.transform(m)
        }
        doc.add(copies)
        selection.clear(); selection.addAll(copies)
        contentChanged()
    }

    fun recolorSelection(c: Int) {
        if (selection.isEmpty()) return
        doc.modify(selection) {
            for (e in selection) {
                e.color = if (e is TextEl && e.bg != 0) c else c
                if (e is CircleEl && e.fill != 0) e.fill = El.withAlpha(c, Color.alpha(e.fill) / 255f)
                if (e is PolyEl && e.fill != 0) e.fill = El.withAlpha(c, Color.alpha(e.fill) / 255f)
                e.invalidateCache()
            }
        }
        contentChanged()
    }

    fun rewidthSelection(k: Float) {
        if (selection.isEmpty()) return
        doc.modify(selection) {
            for (e in selection) {
                when (e) {
                    is TextEl -> e.size = (e.size * k).coerceIn(6f, 400f)
                    is TableEl -> e.size = (e.size * k).coerceIn(6f, 200f)
                    else -> e.width = (e.width * k).coerceIn(0.5f, 80f)
                }
                e.invalidateCache()
            }
        }
        contentChanged()
    }

    fun reorderSelection(front: Boolean) {
        if (selection.isEmpty()) return
        doc.reorder(selection.toList(), front)
        refreshSelectionRefs()
        contentChanged()
    }

    private fun refreshSelectionRefs() {
        val ids = selection.map { it.id }.toSet()
        selection.clear(); selection.addAll(doc.els.filter { it.id in ids })
    }

    fun selectAll() {
        select(doc.els.toList())
    }

    /** Добавить элемент в центр видимой области. */
    fun addAtCenter(e: El, select: Boolean = false) {
        doc.add(e)
        if (select) { selection.clear(); selection.add(e) }
        contentChanged()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
    }

    /** Миниатюра документа для списка холстов. */
    fun thumbnail(): Bitmap? {
        return try {
            val bmp = Exporter.png(doc, null, 1f, true, false)
            val s = 360f / max(bmp.width, bmp.height)
            val t = Bitmap.createScaledBitmap(bmp, max(1, (bmp.width * s).toInt()), max(1, (bmp.height * s).toInt()), true)
            if (t !== bmp) bmp.recycle()
            t
        } catch (e: Throwable) { null }
    }

    companion object {
        const val MIN_SCALE = 0.08f
        const val MAX_SCALE = 12f
    }
}
