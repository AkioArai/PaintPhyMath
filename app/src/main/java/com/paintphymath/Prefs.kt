package com.paintphymath

import android.content.Context
import android.os.Handler
import android.os.Looper

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("ppm", Context.MODE_PRIVATE)

    private fun b(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun i(k: String, d: Int) = sp.getInt(k, d)
    private fun f(k: String, d: Float) = sp.getFloat(k, d)
    private fun put(k: String, v: Any) {
        val e = sp.edit()
        when (v) { is Boolean -> e.putBoolean(k, v); is Int -> e.putInt(k, v); is Float -> e.putFloat(k, v); is String -> e.putString(k, v) }
        e.apply()
    }

    /** 0 — авто (как только замечен стилус, пальцы не рисуют), 1 — только стилус, 2 — по размеру касания, 3 — рисует всё */
    var inputMode: Int get() = i("inputMode", 0); set(v) = put("inputMode", v)
    var stylusSeen: Boolean get() = b("stylusSeen", false); set(v) = put("stylusSeen", v)
    var fingerPan: Boolean get() = b("fingerPan", true); set(v) = put("fingerPan", v)
    var pressure: Boolean get() = b("pressure", true); set(v) = put("pressure", v)
    var shapeRecog: Boolean get() = b("shapeRecog", true); set(v) = put("shapeRecog", v)
    var snapGrid: Boolean get() = b("snapGrid", true); set(v) = put("snapGrid", v)
    var snapObjects: Boolean get() = b("snapObjects", true); set(v) = put("snapObjects", v)
    var hoverCursor: Boolean get() = b("hoverCursor", true); set(v) = put("hoverCursor", v)
    var gestureUndo: Boolean get() = b("gestureUndo", true); set(v) = put("gestureUndo", v)
    var leftHanded: Boolean get() = b("leftHanded", false); set(v) = put("leftHanded", v)
    var keepScreenOn: Boolean get() = b("keepScreenOn", true); set(v) = put("keepScreenOn", v)
    var autoName: Boolean get() = b("autoName", true); set(v) = put("autoName", v)
    var showLengths: Boolean get() = b("showLengths", false); set(v) = put("showLengths", v)
    var smoothing: Int get() = i("smoothing", 2); set(v) = put("smoothing", v)
    /** порог «пальца» в режиме по размеру касания (в пикселях touchMajor) */
    var palmSize: Float get() = f("palmSize", 28f); set(v) = put("palmSize", v)
    var penKey: Int get() = i("penKey", 0); set(v) = put("penKey", v)
    var lastDoc: String get() = sp.getString("lastDoc", "") ?: ""; set(v) = put("lastDoc", v)

    var btnSingle: Int get() = i("btn1", A_ERASER_TOGGLE); set(v) = put("btn1", v)
    var btnDouble: Int get() = i("btn2", A_UNDO); set(v) = put("btn2", v)
    var btnTriple: Int get() = i("btn3", A_REDO); set(v) = put("btn3", v)
    var btnHold: Int get() = i("btn4", A_TEMP_ERASER); set(v) = put("btn4", v)

    fun penColor(tool: String, d: Int) = i("color_$tool", d)
    fun setPenColor(tool: String, c: Int) = put("color_$tool", c)
    fun penWidth(tool: String, d: Float) = f("width_$tool", d)
    fun setPenWidth(tool: String, w: Float) = put("width_$tool", w)

    var customColors: String get() = sp.getString("customColors", "") ?: ""; set(v) = put("customColors", v)

    companion object {
        const val A_NONE = 0
        const val A_ERASER_TOGGLE = 1
        const val A_UNDO = 2
        const val A_REDO = 3
        const val A_SELECT_TOGGLE = 4
        const val A_PREV_TOOL = 5
        const val A_MARKER_TOGGLE = 6
        const val A_NEXT_COLOR = 7
        const val A_RULER = 8
        const val A_LASER = 9
        const val A_TEMP_ERASER = 10
        const val A_TEMP_SELECT = 11
        const val A_TEMP_PAN = 12
        const val A_FIT = 13
        const val A_QUICK_PNG = 14
        const val A_TOGGLE_UI = 15
        const val A_TEXT = 16
        const val A_TEMP_MARKER = 17

        val ACTIONS = listOf(
            A_NONE to "Ничего",
            A_ERASER_TOGGLE to "Ластик ⇄ ручка",
            A_TEMP_ERASER to "Ластик, пока кнопка зажата",
            A_UNDO to "Отменить",
            A_REDO to "Повторить",
            A_SELECT_TOGGLE to "Выделение ⇄ ручка",
            A_TEMP_SELECT to "Выделение, пока кнопка зажата",
            A_MARKER_TOGGLE to "Маркер ⇄ ручка",
            A_TEMP_MARKER to "Маркер, пока кнопка зажата",
            A_PREV_TOOL to "Предыдущий инструмент",
            A_NEXT_COLOR to "Следующий цвет",
            A_RULER to "Показать / скрыть линейку",
            A_LASER to "Лазерная указка",
            A_TEMP_PAN to "Двигать холст, пока кнопка зажата",
            A_FIT to "Показать всё",
            A_QUICK_PNG to "Быстрый экспорт PNG",
            A_TOGGLE_UI to "Скрыть / показать панели",
            A_TEXT to "Инструмент «Текст/формула»",
        )

        fun actionName(a: Int) = ACTIONS.firstOrNull { it.first == a }?.second ?: "—"

        val INPUT_MODES = listOf(
            "Авто: при обнаружении стилуса рисует только перо",
            "Только стилус (пальцы — перемещение и масштаб)",
            "По размеру касания (пассивные Bluetooth-ручки)",
            "Рисует всё (палец тоже)"
        )
    }
}

/**
 * Распознавание жестов кнопки пера: одинарное, двойное, тройное нажатие и удержание.
 * Источники нажатий: кнопки стилуса в MotionEvent (BUTTON_STYLUS_PRIMARY/SECONDARY)
 * и клавиши Bluetooth-пера (KeyEvent), в том числе «выученная» клавиша.
 */
class ButtonGestures(private val listener: Listener) {
    interface Listener {
        fun onClicks(count: Int)
        fun onHoldStart()
        fun onHoldEnd()
    }

    private val h = Handler(Looper.getMainLooper())
    private var pressed = false
    private var count = 0
    private var holding = false

    private val holdRun = Runnable {
        holding = true
        count = 0
        listener.onHoldStart()
    }
    private val finishRun = Runnable {
        val c = count
        count = 0
        if (c > 0) listener.onClicks(c.coerceAtMost(3))
    }

    val isHolding get() = holding

    fun press() {
        if (pressed) return
        pressed = true
        count++
        h.removeCallbacks(finishRun)
        h.postDelayed(holdRun, HOLD_MS)
    }

    fun release() {
        if (!pressed) return
        pressed = false
        h.removeCallbacks(holdRun)
        if (holding) {
            holding = false
            listener.onHoldEnd()
            return
        }
        if (count >= 3) {
            h.removeCallbacks(finishRun)
            finishRun.run()
        } else h.postDelayed(finishRun, MULTI_MS)
    }

    companion object {
        const val HOLD_MS = 420L
        const val MULTI_MS = 300L
    }
}
