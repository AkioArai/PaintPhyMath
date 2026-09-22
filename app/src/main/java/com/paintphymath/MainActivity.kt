package com.paintphymath

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity(), CanvasView.Host {

    lateinit var prefs: Prefs
    lateinit var store: DocStore
    lateinit var canvas: CanvasView
    private lateinit var root: FrameLayout
    private lateinit var topBar: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var undoBtn: IconView
    private lateinit var redoBtn: IconView
    private lateinit var rulerBtn: IconView
    private lateinit var railScroll: ScrollView
    private lateinit var rail: LinearLayout
    private lateinit var optionsHolder: FrameLayout
    private lateinit var options: LinearLayout
    private lateinit var selBar: LinearLayout
    private lateinit var statusChip: TextView
    private lateinit var zoomChip: TextView
    private val toolButtons = HashMap<Tool, IconView>()
    private val handler = Handler(Looper.getMainLooper())
    private var uiHidden = false
    var learningKey = false
    private var passivePen: String? = null
    private var activePen: String? = null
    private var lastOptionsKey = ""

    private val saveRun = Runnable { saveNow(false) }

    // ------------------------------------------------------------------ жизненный цикл

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        store = DocStore(this)
        if (prefs.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildUi()
        val doc = prefs.lastDoc.takeIf { it.isNotEmpty() }?.let { store.load(it) } ?: store.list().firstOrNull()?.let { store.load(it.id) } ?: Doc()
        openDoc(doc)
        detectPens()
        (getSystemService(Context.INPUT_SERVICE) as InputManager).registerInputDeviceListener(object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(id: Int) = detectPens(true)
            override fun onInputDeviceRemoved(id: Int) = detectPens(false)
            override fun onInputDeviceChanged(id: Int) = detectPens(false)
        }, handler)
    }

    override fun onPause() {
        super.onPause()
        saveNow(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    fun openDoc(doc: Doc) {
        doc.onChanged = { scheduleSave() }
        canvas.doc = doc
        prefs.lastDoc = doc.id
        titleView.text = doc.name
        if (doc.els.isEmpty() && doc.viewTx == 0f && doc.viewTy == 0f) canvas.post { canvas.resetZoom(); canvas.fitAll() }
        refreshUi()
    }

    private fun scheduleSave() {
        handler.removeCallbacks(saveRun)
        handler.postDelayed(saveRun, 1500)
    }

    fun saveNow(withThumb: Boolean) {
        handler.removeCallbacks(saveRun)
        val d = canvas.doc
        if (!d.dirty && !withThumb) return
        if (d.els.isEmpty() && !d.dirty) return
        try {
            store.save(d, if (withThumb || d.dirty) canvas.thumbnail() else null)
        } catch (e: Exception) {
            toast("Не удалось сохранить: ${e.message}")
        }
    }

    // ------------------------------------------------------------------ построение интерфейса

    private fun dp(v: Float) = Ui.dp(this, v)
    private fun dpi(v: Float) = Ui.dpi(this, v)

    private fun buildUi() {
        root = FrameLayout(this)
        root.setBackgroundColor(Color.WHITE)
        canvas = CanvasView(this, prefs)
        canvas.host = this
        root.addView(canvas, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))

        // верхняя панель
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Ui.BAR)
            elevation = dp(6f)
            setPadding(dpi(6f), 0, dpi(6f), 0)
        }
        val white = Color.WHITE
        topBar.addView(Ui.iconButton(this, "folder", white) { showDocs() })
        titleView = Ui.text(this, "", 16f, white, true).apply {
            setPadding(dpi(8f), 0, dpi(8f), 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setOnClickListener { renameDoc() }
        }
        topBar.addView(titleView, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        undoBtn = Ui.iconButton(this, "undo", white) { canvas.undo() }
        redoBtn = Ui.iconButton(this, "redo", white) { canvas.redo() }
        topBar.addView(undoBtn); topBar.addView(redoBtn)
        topBar.addView(View(this).apply { setBackgroundColor(0x33FFFFFF) }, LinearLayout.LayoutParams(dpi(1f), dpi(26f)).apply { setMargins(dpi(6f), 0, dpi(6f), 0) })
        topBar.addView(Ui.iconButton(this, "plus", white, 44f, Ui.ACCENT) { Dialogs.insertMenu(this) })
        rulerBtn = Ui.iconButton(this, "ruler", white) { canvas.rulerVisible = !canvas.rulerVisible }
        topBar.addView(rulerBtn)
        topBar.addView(Ui.iconButton(this, "paper", white) { Dialogs.paper(this) })
        topBar.addView(Ui.iconButton(this, "export", white) { Dialogs.export(this) })
        topBar.addView(Ui.iconButton(this, "fullscreen", white) { toggleUi() })
        topBar.addView(Ui.iconButton(this, "settings", white) { Dialogs.settings(this) })
        root.addView(topBar, FrameLayout.LayoutParams(Ui.MATCH, dpi(54f), Gravity.TOP))

        // панель инструментов
        rail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpi(4f), dpi(6f), dpi(4f), dpi(6f))
        }
        for (t in Tool.values()) {
            val b = Ui.iconButton(this, t.icon, Ui.TEXT, 46f) { selectTool(t) }
            b.contentDescription = t.title
            b.setOnLongClickListener { toast(t.title); true }
            rail.addView(b, LinearLayout.LayoutParams(dpi(46f), dpi(46f)).apply { setMargins(0, dpi(1f), 0, dpi(1f)) })
            toolButtons[t] = b
        }
        railScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            background = Ui.round(Color.WHITE, dp(26f))
            elevation = dp(8f)
            addView(rail)
        }
        root.addView(railScroll, FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.START or Gravity.TOP).apply {
            setMargins(dpi(10f), dpi(66f), dpi(10f), dpi(60f))
        })

        // контекстная панель параметров
        options = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpi(8f), dpi(4f), dpi(8f), dpi(4f))
        }
        optionsHolder = FrameLayout(this).apply {
            background = Ui.round(Color.WHITE, dp(24f))
            elevation = dp(6f)
            addView(Ui.hscroll(this@MainActivity, options))
        }
        root.addView(optionsHolder, FrameLayout.LayoutParams(Ui.WRAP, dpi(52f), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            setMargins(dpi(76f), dpi(64f), dpi(12f), 0)
        })

        // панель действий с выделением
        selBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.round(Ui.BAR, dp(24f))
            elevation = dp(8f)
            setPadding(dpi(8f), dpi(2f), dpi(8f), dpi(2f))
            visibility = View.GONE
        }
        val w = Color.WHITE
        selBar.addView(Ui.iconButton(this, "edit", w) { canvas.selection.firstOrNull()?.let { requestEdit(it) } })
        selBar.addView(Ui.iconButton(this, "palette", w) { Dialogs.colorPicker(this, canvas.selection.firstOrNull()?.color ?: Color.BLACK) { c -> canvas.recolorSelection(c) } })
        selBar.addView(Ui.iconButton(this, "@A⁻", w) { canvas.rewidthSelection(0.8f) })
        selBar.addView(Ui.iconButton(this, "@A⁺", w) { canvas.rewidthSelection(1.25f) })
        selBar.addView(Ui.iconButton(this, "copy", w) { canvas.duplicateSelection() })
        selBar.addView(Ui.iconButton(this, "up", w) { canvas.reorderSelection(true) })
        selBar.addView(Ui.iconButton(this, "down", w) { canvas.reorderSelection(false) })
        selBar.addView(Ui.iconButton(this, "link", w) { linkSelection() })
        selBar.addView(Ui.iconButton(this, "export", w) { Dialogs.export(this, true) })
        selBar.addView(Ui.iconButton(this, "trash", w) { canvas.deleteSelection() })
        selBar.addView(Ui.iconButton(this, "close", w) { canvas.clearSelection() })
        root.addView(selBar, FrameLayout.LayoutParams(Ui.WRAP, dpi(52f), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            setMargins(0, 0, 0, dpi(18f))
        })

        // статус пера и масштаб
        statusChip = Ui.text(this, "", 12f, Color.WHITE, true).apply {
            background = Ui.ripple(Ui.round(0xE6232342.toInt(), dp(16f)), dp(16f))
            setPadding(dpi(12f), dpi(7f), dpi(12f), dpi(7f))
            elevation = dp(4f)
            setOnClickListener { Dialogs.inputMode(this@MainActivity) }
        }
        root.addView(statusChip, FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.BOTTOM or Gravity.START).apply { setMargins(dpi(12f), 0, 0, dpi(14f)) })
        zoomChip = Ui.text(this, "100%", 12f, Color.WHITE, true).apply {
            background = Ui.ripple(Ui.round(0xE6232342.toInt(), dp(16f)), dp(16f))
            setPadding(dpi(12f), dpi(7f), dpi(12f), dpi(7f))
            elevation = dp(4f)
            setOnClickListener { zoomMenu() }
        }
        root.addView(zoomChip, FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, dpi(12f), dpi(14f)) })

        setContentView(root)
        applyHandedness()
    }

    fun applyHandedness() {
        val lp = railScroll.layoutParams as FrameLayout.LayoutParams
        lp.gravity = (if (prefs.leftHanded) Gravity.END else Gravity.START) or Gravity.TOP
        railScroll.layoutParams = lp
        val olp = optionsHolder.layoutParams as FrameLayout.LayoutParams
        if (prefs.leftHanded) olp.setMargins(dpi(12f), dpi(64f), dpi(76f), 0) else olp.setMargins(dpi(76f), dpi(64f), dpi(12f), 0)
        optionsHolder.layoutParams = olp
    }

    private fun toggleUi() {
        uiHidden = !uiHidden
        val v = if (uiHidden) View.GONE else View.VISIBLE
        topBar.visibility = v; railScroll.visibility = v; optionsHolder.visibility = v
        if (uiHidden) toast("Панели скрыты. Нажмите ⤢ в углу или кнопку пера, чтобы вернуть")
        zoomChip.text = if (uiHidden) "⤢ Панели" else zoomText()
    }

    private fun zoomText() = "${(canvas.scale * 100).toInt()}%"

    private fun zoomMenu() {
        if (uiHidden) { toggleUi(); return }
        val items = arrayOf("100%", "Показать всё", "Увеличить ×2", "Уменьшить ×0.5", "Показать выделение")
        AlertDialog.Builder(this).setItems(items) { _, i ->
            when (i) {
                0 -> canvas.resetZoom()
                1 -> canvas.fitAll()
                2 -> canvas.zoomAround(2f, canvas.width / 2f, canvas.height / 2f)
                3 -> canvas.zoomAround(0.5f, canvas.width / 2f, canvas.height / 2f)
                4 -> if (canvas.selection.isNotEmpty()) canvas.fitAll(canvas.selection)
            }
        }.show()
    }

    fun selectTool(t: Tool) {
        canvas.tempTool = null
        canvas.tool = t
        if (t == Tool.STAMP) Dialogs.stampPicker(this)
    }

    // ------------------------------------------------------------------ обновление интерфейса

    private fun refreshUi() {
        val active = canvas.activeTool
        for ((t, b) in toolButtons) {
            val sel = t == active
            b.tint = if (sel) Color.WHITE else Ui.TEXT
            b.background = Ui.ripple(if (sel) Ui.round(if (canvas.tempTool == t) 0xFFEC4899.toInt() else Ui.ACCENT, dp(23f)) else null, dp(23f))
            b.invalidate()
        }
        undoBtn.alpha = if (canvas.doc.canUndo) 1f else 0.35f
        redoBtn.alpha = if (canvas.doc.canRedo) 1f else 0.35f
        rulerBtn.background = Ui.ripple(if (canvas.rulerVisible) Ui.round(Ui.BAR2, dp(22f)) else null, dp(22f), 0x44FFFFFF)
        rulerBtn.tint = if (canvas.rulerVisible) 0xFFA5B4FC.toInt() else Color.WHITE
        rulerBtn.invalidate()
        selBar.visibility = if (canvas.selection.isNotEmpty() && !uiHidden) View.VISIBLE else View.GONE
        statusChip.text = statusText()
        if (!uiHidden) zoomChip.text = zoomText()
        rebuildOptions()
    }

    private fun statusText(): String {
        val mode = canvas.effectiveInputMode()
        val pen = activePen ?: passivePen
        val base = when (mode) {
            1 -> "✎ Рисует только перо"
            2 -> "✎ Перо по размеру касания"
            else -> "☝ Рисует и палец"
        }
        return if (pen != null) "$base · ⌁ $pen" else base
    }

    private fun rebuildOptions() {
        val t = canvas.activeTool
        val key = "$t|${canvas.colorOf(t)}|${canvas.widthOf(t)}|${canvas.lineMode}|${canvas.circleMode}|${canvas.shapeMode}|${canvas.eraserMode}|" +
            "${canvas.selectMode}|${canvas.shapeFill}|${canvas.circleShowRadius}|${prefs.showLengths}|${canvas.linkStyle}|${canvas.stampKind}|${prefs.snapGrid}|${prefs.snapObjects}|${prefs.pressure}|${canvas.selection.size}"
        if (key == lastOptionsKey) return
        lastOptionsKey = key
        options.removeAllViews()
        val drawing = t !in listOf(Tool.ERASER, Tool.SELECT, Tool.HAND, Tool.TEXT)
        options.addView(Ui.text(this, t.title, 13f, Ui.MUTED, true).apply { setPadding(dpi(6f), 0, dpi(8f), 0) })
        if (drawing) {
            addColors(t)
            addSeparator()
            addWidths(t)
        }
        fun modeChips(labels: List<String>, cur: Int, set: (Int) -> Unit) {
            addSeparator()
            labels.forEachIndexed { i, s -> options.addView(Ui.chip(this, s, i == cur) { set(i); lastOptionsKey = ""; refreshUi() }) }
        }
        fun toggle(label: String, v: Boolean, set: (Boolean) -> Unit) {
            options.addView(Ui.chip(this, label, v) { set(!v); lastOptionsKey = ""; refreshUi() })
        }
        when (t) {
            Tool.PEN, Tool.PENCIL -> { addSeparator(); toggle("Нажим", prefs.pressure) { prefs.pressure = it } }
            Tool.ERASER -> {
                modeChips(listOf("Объект целиком", "Частично"), canvas.eraserMode) { canvas.eraserMode = it }
                addSeparator()
                for (s in listOf(16f, 32f, 60f)) {
                    val dot = Ui.WidthDot(this, s * 0.55f, canvas.widthOf(Tool.ERASER) == s, 0xFF9CA3AF.toInt())
                    dot.setOnClickListener { canvas.setWidth(Tool.ERASER, s); lastOptionsKey = ""; refreshUi() }
                    options.addView(dot, LinearLayout.LayoutParams(dpi(38f), dpi(38f)))
                }
                addSeparator()
                options.addView(Ui.chip(this, "Очистить холст", false) { confirmClear() })
            }
            Tool.SELECT -> {
                modeChips(listOf("Лассо", "Рамка"), canvas.selectMode) { canvas.selectMode = it }
                addSeparator()
                options.addView(Ui.chip(this, "Выделить всё", false) { canvas.selectAll() })
                if (canvas.selection.isNotEmpty()) options.addView(Ui.chip(this, "Выбрано: ${canvas.selection.size}", true) {})
            }
            Tool.LINE -> {
                modeChips(listOf("Отрезок", "Стрелка", "Вектор", "Пунктир", "Прямая", "↔"), canvas.lineMode) { canvas.lineMode = it }
                addSeparator(); toggle("Длина", prefs.showLengths) { prefs.showLengths = it }
                toggle("Привязка", prefs.snapObjects) { prefs.snapObjects = it }
            }
            Tool.CIRCLE -> {
                modeChips(listOf("Центр+радиус", "Диаметр", "Эллипс", "3 точки"), canvas.circleMode) { canvas.circleMode = it }
                addSeparator(); toggle("Показать r", canvas.circleShowRadius) { canvas.circleShowRadius = it }
                toggle("Заливка", canvas.shapeFill) { canvas.shapeFill = it }
            }
            Tool.SHAPE -> {
                modeChips(listOf("▭", "□", "△", "◺", "▱", "⏢", "Многоугольник", "⬡"), canvas.shapeMode) { canvas.shapeMode = it }
                addSeparator(); toggle("Заливка", canvas.shapeFill) { canvas.shapeFill = it }
                toggle("Имена вершин", prefs.autoName) { prefs.autoName = it }
                if (canvas.shapeMode == 6) options.addView(Ui.chip(this, "Замкнуть", false) { canvas.finishPolygon(true) })
            }
            Tool.POINT, Tool.ANGLE -> {
                addSeparator(); toggle("Автоимена", prefs.autoName) { prefs.autoName = it }
                toggle("Сетка", prefs.snapGrid) { prefs.snapGrid = it }
                toggle("Объекты", prefs.snapObjects) { prefs.snapObjects = it }
            }
            Tool.AXES -> {
                addSeparator()
                options.addView(Ui.chip(this, "ƒ(x) График…", false) { Dialogs.plot(this, PlotEl.FX) })
                options.addView(Ui.chip(this, "Параметры осей…", false) { editNearestAxes() })
            }
            Tool.LINK -> modeChips(listOf("→", "—", "↔", "⇢"), canvas.linkStyle) { canvas.linkStyle = it }
            Tool.STAMP -> {
                addSeparator()
                options.addView(Ui.chip(this, Stamps.def(canvas.stampKind).title + " ▾", true) { Dialogs.stampPicker(this) })
            }
            Tool.TEXT -> {
                addSeparator()
                options.addView(Ui.chip(this, "Коснитесь холста, чтобы добавить текст или формулу", false) {})
            }
            Tool.HAND -> {
                addSeparator()
                options.addView(Ui.chip(this, "Показать всё", false) { canvas.fitAll() })
                options.addView(Ui.chip(this, "100%", false) { canvas.resetZoom() })
            }
            else -> {}
        }
    }

    private fun addSeparator() {
        options.addView(View(this).apply { setBackgroundColor(0xFFE5E7EB.toInt()) }, LinearLayout.LayoutParams(dpi(1f), dpi(26f)).apply { setMargins(dpi(6f), 0, dpi(6f), 0) })
    }

    private fun addColors(t: Tool) {
        val palette = if (t == Tool.MARKER) MARKER_COLORS else if (Paper.isDark(canvas.doc.paperColor)) DARK_COLORS else PEN_COLORS
        val cur = canvas.colorOf(t)
        for (c in palette) {
            val sw = Ui.Swatch(this, c, c == cur)
            sw.setOnClickListener { canvas.setColor(t, c) }
            options.addView(sw, LinearLayout.LayoutParams(dpi(36f), dpi(36f)))
        }
        if (cur !in palette) options.addView(Ui.Swatch(this, cur, true), LinearLayout.LayoutParams(dpi(36f), dpi(36f)))
        val more = Ui.iconButton(this, "palette", Ui.TEXT, 36f) { Dialogs.colorPicker(this, cur) { c -> canvas.setColor(t, c) } }
        options.addView(more)
    }

    private fun addWidths(t: Tool) {
        val presets = when (t) {
            Tool.MARKER -> listOf(12f, 22f, 36f)
            Tool.PENCIL -> listOf(1.2f, 2f, 3.5f)
            Tool.PEN -> listOf(1.8f, 3.2f, 6f)
            else -> listOf(1.4f, 2.4f, 4f)
        }
        val cur = canvas.widthOf(t)
        for (w in presets) {
            val dot = Ui.WidthDot(this, w * 1.6f + 2, cur == w, if (t == Tool.MARKER) El.withAlpha(canvas.colorOf(t), 0.8f) else canvas.colorOf(t))
            dot.setOnClickListener { canvas.setWidth(t, w) }
            options.addView(dot, LinearLayout.LayoutParams(dpi(36f), dpi(36f)))
        }
        if (cur !in presets) {
            options.addView(Ui.chip(this, Expr.fmt(cur.toDouble(), 1), true) {})
        }
        options.addView(Ui.iconButton(this, "settings", Ui.MUTED, 34f) {
            Dialogs.widthPicker(this, t)
        })
    }

    private fun confirmClear() {
        AlertDialog.Builder(this).setTitle("Очистить холст?").setMessage("Все объекты будут удалены (можно отменить).")
            .setPositiveButton("Очистить") { _, _ -> canvas.doc.remove(canvas.doc.els.toList()); canvas.selection.clear(); canvas.contentChanged() }
            .setNegativeButton("Отмена", null).show()
    }

    private fun linkSelection() {
        val s = canvas.selection.filter { it !is LinkEl }
        if (s.size != 2) { toast("Выделите ровно два объекта, чтобы связать их"); return }
        val l = LinkEl().also { it.from = s[0].id; it.to = s[1].id; it.style = canvas.linkStyle; it.color = canvas.colorOf(Tool.LINK); it.width = canvas.widthOf(Tool.LINK) }
        canvas.doc.add(l); canvas.contentChanged()
        requestLinkLabel(l)
    }

    fun editNearestAxes() {
        val c = canvas.centerWorld()
        val ax = canvas.doc.axesAt(c.x, c.y) ?: canvas.doc.allAxes().minByOrNull { val r = it.rect(); Geo.dist(r.centerX(), r.centerY(), c.x, c.y) }
        if (ax == null) { toast("На холсте нет осей"); return }
        Dialogs.axes(this, ax)
    }

    // ------------------------------------------------------------------ CanvasView.Host

    override fun onStateChanged() {
        refreshUi()
    }

    override fun requestText(x: Float, y: Float, existing: TextEl?) = Dialogs.text(this, x, y, existing)

    override fun requestEdit(e: El) {
        when (e) {
            is TextEl -> Dialogs.text(this, e.x, e.y, e)
            is PlotEl -> Dialogs.plot(this, e.kind, e)
            is AxesEl -> Dialogs.axes(this, e)
            is TableEl -> Dialogs.table(this, e)
            is PointEl -> Dialogs.rename(this, "Имя точки", e.name) { n -> canvas.doc.modify(listOf(e)) { e.name = n }; canvas.contentChanged() }
            is LineEl -> Dialogs.rename(this, "Подпись (формула)", e.label) { n -> canvas.doc.modify(listOf(e)) { e.label = n }; canvas.contentChanged() }
            is CircleEl -> Dialogs.rename(this, "Подпись окружности", e.label) { n -> canvas.doc.modify(listOf(e)) { e.label = n }; canvas.contentChanged() }
            is PolyEl -> Dialogs.rename(this, "Имена вершин через запятую", e.names) { n -> canvas.doc.modify(listOf(e)) { e.names = n }; canvas.contentChanged() }
            is AngleEl -> Dialogs.rename(this, "Обозначение угла", e.label) { n -> canvas.doc.modify(listOf(e)) { e.label = n }; canvas.contentChanged() }
            is StampEl -> Dialogs.rename(this, "Подпись шаблона", e.label) { n -> canvas.doc.modify(listOf(e)) { e.label = n }; canvas.contentChanged() }
            is LinkEl -> requestLinkLabel(e)
            else -> toast("Для этого объекта доступны цвет, толщина и трансформации")
        }
    }

    override fun requestLinkLabel(link: LinkEl) {
        Dialogs.linkLabel(this, link)
    }

    override fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onStylusDetected() {
        if (prefs.inputMode == 0) toast("Обнаружен стилус: теперь рисует только перо, пальцы двигают и масштабируют холст")
        refreshUi()
    }

    override fun onUiAction(action: Int) {
        when (action) {
            Prefs.A_QUICK_PNG -> Dialogs.quickPng(this)
            Prefs.A_TOGGLE_UI -> toggleUi()
            Prefs.A_TEXT -> selectTool(Tool.TEXT)
        }
    }

    // ------------------------------------------------------------------ Bluetooth / внешние перья

    private fun isPenName(n: String): Boolean {
        val s = n.lowercase()
        return listOf("pen", "stylus", "pencil", "перо", "стилус", "ручка", "s pen", "spen").any { s.contains(it) }
    }

    fun detectPens(announce: Boolean = false) {
        var active: String? = null
        var passive: String? = null
        for (id in InputDevice.getDeviceIds()) {
            val d = InputDevice.getDevice(id) ?: continue
            val external = if (Build.VERSION.SDK_INT >= 29) d.isExternal else true
            val stylus = d.supportsSource(InputDevice.SOURCE_STYLUS) || (Build.VERSION.SDK_INT >= 23 && d.supportsSource(InputDevice.SOURCE_BLUETOOTH_STYLUS))
            if (stylus && (external || isPenName(d.name))) active = d.name
            else if (external && isPenName(d.name)) passive = d.name
        }
        val newlyActive = active != null && activePen == null
        val newlyPassive = passive != null && passivePen == null
        activePen = active
        passivePen = passive
        if (active != null && prefs.inputMode == 0 && !prefs.stylusSeen) prefs.stylusSeen = true
        canvas.passivePenConnected = passive != null && active == null
        if (announce) {
            if (newlyActive) toast("Подключено перо «$active»: рука не рисует, рисует только перо")
            else if (newlyPassive) toast("Подключена Bluetooth-ручка «$passive»: включено распознавание пера по размеру касания")
        }
        refreshUi()
    }

    // ------------------------------------------------------------------ клавиатура и кнопки BT-пера

    private fun isPenKey(e: KeyEvent): Boolean {
        val k = e.keyCode
        if (prefs.penKey != 0 && k == prefs.penKey) return true
        if (k in 308..311) return true // KEYCODE_STYLUS_BUTTON_PRIMARY/SECONDARY/TERTIARY/TAIL
        val dev = e.device
        if (dev != null && isPenName(dev.name) && k != KeyEvent.KEYCODE_BACK) return true
        return false
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (learningKey && e.action == KeyEvent.ACTION_DOWN && e.keyCode != KeyEvent.KEYCODE_BACK) {
            prefs.penKey = e.keyCode
            learningKey = false
            toast("Кнопка пера назначена: ${KeyEvent.keyCodeToString(e.keyCode)}")
            Dialogs.onKeyLearned(this)
            return true
        }
        if (isPenKey(e)) {
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) canvas.buttons.press()
            else if (e.action == KeyEvent.ACTION_UP) canvas.buttons.release()
            return true
        }
        if (e.action == KeyEvent.ACTION_DOWN && currentFocus !is android.widget.EditText) {
            val ctrl = e.isCtrlPressed
            when {
                ctrl && e.keyCode == KeyEvent.KEYCODE_Z && e.isShiftPressed -> { canvas.redo(); return true }
                ctrl && e.keyCode == KeyEvent.KEYCODE_Z -> { canvas.undo(); return true }
                ctrl && e.keyCode == KeyEvent.KEYCODE_Y -> { canvas.redo(); return true }
                ctrl && e.keyCode == KeyEvent.KEYCODE_S -> { saveNow(true); toast("Сохранено"); return true }
                ctrl && e.keyCode == KeyEvent.KEYCODE_D -> { canvas.duplicateSelection(); return true }
                ctrl && e.keyCode == KeyEvent.KEYCODE_A -> { canvas.selectAll(); return true }
                e.keyCode == KeyEvent.KEYCODE_FORWARD_DEL || e.keyCode == KeyEvent.KEYCODE_DEL -> { canvas.deleteSelection(); return true }
                e.keyCode == KeyEvent.KEYCODE_ESCAPE -> { canvas.clearSelection(); return true }
                !ctrl -> {
                    val t = when (e.keyCode) {
                        KeyEvent.KEYCODE_P -> Tool.PEN; KeyEvent.KEYCODE_B -> Tool.PENCIL; KeyEvent.KEYCODE_M -> Tool.MARKER
                        KeyEvent.KEYCODE_E -> Tool.ERASER; KeyEvent.KEYCODE_S -> Tool.SELECT; KeyEvent.KEYCODE_L -> Tool.LINE
                        KeyEvent.KEYCODE_C -> Tool.CIRCLE; KeyEvent.KEYCODE_R -> Tool.SHAPE; KeyEvent.KEYCODE_T -> Tool.TEXT
                        KeyEvent.KEYCODE_H -> Tool.HAND; KeyEvent.KEYCODE_A -> Tool.AXES; KeyEvent.KEYCODE_X -> Tool.POINT
                        else -> null
                    }
                    if (t != null) { selectTool(t); return true }
                    when (e.keyCode) {
                        KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_NUMPAD_ADD -> { canvas.zoomAround(1.25f, canvas.width / 2f, canvas.height / 2f); return true }
                        KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> { canvas.zoomAround(0.8f, canvas.width / 2f, canvas.height / 2f); return true }
                        KeyEvent.KEYCODE_0 -> { canvas.fitAll(); return true }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(e)
    }

    // ------------------------------------------------------------------ документы

    fun newDoc() {
        saveNow(true)
        val d = Doc()
        d.name = "Холст " + java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale("ru")).format(java.util.Date())
        store.save(d, null)
        openDoc(d)
    }

    fun openDocById(id: String) {
        saveNow(true)
        val d = store.load(id)
        if (d == null) { toast("Не удалось открыть"); return }
        openDoc(d)
    }

    fun renameDoc() {
        Dialogs.rename(this, "Название холста", canvas.doc.name) { n ->
            if (n.isNotBlank()) { canvas.doc.name = n; canvas.doc.dirty = true; titleView.text = n; saveNow(false) }
        }
    }

    fun showDocs() {
        saveNow(true)
        Dialogs.documents(this)
    }

    // ------------------------------------------------------------------ файлы: экспорт/импорт

    class PendingExport(val format: String, val onlySel: Boolean, val withPaper: Boolean, val transparent: Boolean, val scale: Float)

    var pending: PendingExport? = null

    fun exportToFile(p: PendingExport) {
        pending = p
        val mime = when (p.format) { "png" -> "image/png"; "svg" -> "image/svg+xml"; "pdf" -> "application/pdf"; else -> "application/json" }
        val name = safeName(canvas.doc.name) + "." + (if (p.format == "json") "ppm.json" else p.format)
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = mime; putExtra(Intent.EXTRA_TITLE, name)
        }
        try { startActivityForResult(i, REQ_EXPORT) } catch (e: Exception) { toast("Нет приложения для сохранения файлов") }
    }

    private fun safeName(s: String) = s.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "canvas" }

    private fun writeExport(p: PendingExport, out: java.io.OutputStream) {
        val only = if (p.onlySel && canvas.selection.isNotEmpty()) canvas.selection.toList() else null
        when (p.format) {
            "png" -> {
                val bmp = Exporter.png(canvas.doc, only, p.scale, p.withPaper, p.transparent)
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                bmp.recycle()
            }
            "svg" -> out.write(Exporter.svg(canvas.doc, only, p.withPaper, p.transparent).toByteArray(Charsets.UTF_8))
            "pdf" -> Exporter.pdf(canvas.doc, only, p.withPaper, out)
            "json" -> out.write(canvas.doc.toJson().toString().toByteArray(Charsets.UTF_8))
        }
    }

    fun share(p: PendingExport) {
        try {
            val dir = File(cacheDir, "share").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val ext = if (p.format == "json") "ppm.json" else p.format
            val f = File(dir, safeName(canvas.doc.name).replace(' ', '_') + "." + ext)
            f.outputStream().use { writeExport(p, it) }
            val uri = ShareProvider.uriFor(f.name)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = when (p.format) { "png" -> "image/png"; "svg" -> "image/svg+xml"; "pdf" -> "application/pdf"; else -> "application/json" }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(i, "Поделиться"))
        } catch (e: Exception) {
            toast("Ошибка: ${e.message}")
        }
    }

    fun pickImage() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*" }
        try { startActivityForResult(i, REQ_IMAGE) } catch (e: Exception) { toast("Нет приложения для выбора изображений") }
    }

    fun importProject() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
        try { startActivityForResult(i, REQ_IMPORT) } catch (e: Exception) { toast("Нет приложения для выбора файлов") }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        when (requestCode) {
            REQ_EXPORT -> {
                val p = pending ?: return
                try {
                    contentResolver.openOutputStream(uri)?.use { writeExport(p, it) }
                    toast("Сохранено: ${p.format.uppercase()}")
                } catch (e: Exception) { toast("Ошибка сохранения: ${e.message}") }
            }
            REQ_IMAGE -> insertImage(uri)
            REQ_IMPORT -> {
                try {
                    val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
                    val d = Doc.fromJson(JSONObject(text))
                    d.id = "doc_" + System.currentTimeMillis()
                    saveNow(true)
                    store.save(d, null)
                    openDoc(d)
                    toast("Холст импортирован")
                } catch (e: Exception) { toast("Это не файл холста PaintPhyMath") }
            }
        }
    }

    private fun insertImage(uri: Uri) {
        try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (max(opts.outWidth, opts.outHeight) / sample > 1800) sample *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) } ?: return
            val bos = ByteArrayOutputStream()
            val hasAlpha = bmp.hasAlpha()
            bmp.compress(if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 88, bos)
            val img = ImageEl()
            img.data = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
            val c = canvas.centerWorld()
            val maxW = canvas.width * 0.6f / canvas.scale
            val k = min(1f, maxW / bmp.width)
            img.w = bmp.width * k; img.h = bmp.height * k
            img.x = c.x - img.w / 2; img.y = c.y - img.h / 2
            canvas.addAtCenter(img, true)
            canvas.tool = Tool.SELECT
            canvas.select(listOf(img))
        } catch (e: Exception) { toast("Не удалось вставить изображение") }
    }

    companion object {
        const val REQ_EXPORT = 11
        const val REQ_IMAGE = 12
        const val REQ_IMPORT = 13

        val PEN_COLORS = listOf(
            0xFF111827.toInt(), 0xFF2563EB.toInt(), 0xFFDC2626.toInt(), 0xFF16A34A.toInt(), 0xFFEA580C.toInt(), 0xFF7C3AED.toInt()
        )
        val DARK_COLORS = listOf(
            0xFFFFFFFF.toInt(), 0xFF93C5FD.toInt(), 0xFFFCA5A5.toInt(), 0xFF86EFAC.toInt(), 0xFFFDE047.toInt(), 0xFFD8B4FE.toInt()
        )
        val MARKER_COLORS = listOf(
            0xFFFDE047.toInt(), 0xFF86EFAC.toInt(), 0xFFF9A8D4.toInt(), 0xFF93C5FD.toInt(), 0xFFFDBA74.toInt(), 0xFFC4B5FD.toInt()
        )
    }
}
