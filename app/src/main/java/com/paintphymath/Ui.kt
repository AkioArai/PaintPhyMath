package com.paintphymath

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

object Ui {
    const val ACCENT = 0xFF4F46E5.toInt()
    const val ACCENT_SOFT = 0xFFE0E7FF.toInt()
    const val BAR = 0xFF15152A.toInt()
    const val BAR2 = 0xFF232342.toInt()
    const val TEXT = 0xFF1F2937.toInt()
    const val MUTED = 0xFF6B7280.toInt()
    const val SURFACE = 0xFFFFFFFF.toInt()
    const val CARD = 0xFFF5F5FA.toInt()

    fun dp(ctx: Context, v: Float) = v * ctx.resources.displayMetrics.density
    fun dpi(ctx: Context, v: Float) = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun round(color: Int, radius: Float, strokeW: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius
        if (strokeW > 0) setStroke(strokeW, strokeColor)
    }

    fun ripple(content: Drawable?, radius: Float, rippleColor: Int = 0x33000000): Drawable =
        RippleDrawable(ColorStateList.valueOf(rippleColor), content, round(Color.WHITE, radius))

    fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    fun text(ctx: Context, s: String, size: Float = 14f, color: Int = TEXT, bold: Boolean = false) = TextView(ctx).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(color)
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    fun header(ctx: Context, s: String) = text(ctx, s, 13f, ACCENT, true).apply {
        setPadding(0, dpi(ctx, 14f), 0, dpi(ctx, 6f))
        letterSpacing = 0.04f
    }

    fun iconButton(ctx: Context, icon: String, tint: Int, sizeDp: Float = 44f, bgColor: Int = 0, onClick: () -> Unit): IconView {
        val v = IconView(ctx, icon, tint)
        val s = dpi(ctx, sizeDp)
        v.layoutParams = LinearLayout.LayoutParams(s, s)
        v.background = ripple(if (bgColor != 0) round(bgColor, s / 2f) else null, s / 2f, if (Color.alpha(tint) > 0 && tint == Color.WHITE) 0x44FFFFFF else 0x22000000)
        v.isClickable = true
        v.setOnClickListener { onClick() }
        return v
    }

    fun chip(ctx: Context, label: String, selected: Boolean, onClick: () -> Unit) = TextView(ctx).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(if (selected) Color.WHITE else TEXT)
        gravity = Gravity.CENTER
        val r = dp(ctx, 16f)
        background = ripple(round(if (selected) ACCENT else CARD, r), r)
        setPadding(dpi(ctx, 12f), dpi(ctx, 6f), dpi(ctx, 12f), dpi(ctx, 6f))
        layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(dpi(ctx, 3f), dpi(ctx, 3f), dpi(ctx, 3f), dpi(ctx, 3f)) }
        isClickable = true
        setOnClickListener { onClick() }
    }

    fun button(ctx: Context, label: String, primary: Boolean = false, onClick: () -> Unit) = TextView(ctx).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(if (primary) Color.WHITE else ACCENT)
        gravity = Gravity.CENTER
        val r = dp(ctx, 12f)
        background = ripple(round(if (primary) ACCENT else ACCENT_SOFT, r), r)
        setPadding(dpi(ctx, 14f), dpi(ctx, 10f), dpi(ctx, 14f), dpi(ctx, 10f))
        layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(dpi(ctx, 4f), dpi(ctx, 4f), dpi(ctx, 4f), dpi(ctx, 4f)) }
        isClickable = true
        setOnClickListener { onClick() }
    }

    fun input(ctx: Context, hint: String, value: String = "", multi: Boolean = false, numeric: Boolean = false) = EditText(ctx).apply {
        this.hint = hint
        setText(value)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(TEXT)
        background = round(CARD, dp(ctx, 10f), dpi(ctx, 1f), 0xFFE5E7EB.toInt())
        setPadding(dpi(ctx, 12f), dpi(ctx, 10f), dpi(ctx, 12f), dpi(ctx, 10f))
        inputType = when {
            numeric -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            multi -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        if (multi) { minLines = 2; maxLines = 8; gravity = Gravity.TOP or Gravity.START }
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dpi(ctx, 4f), 0, dpi(ctx, 4f)) }
    }

    fun labeled(ctx: Context, label: String, v: View): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        addView(text(ctx, label, 12f, MUTED))
        addView(v)
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dpi(ctx, 4f), 0, 0) }
    }

    fun row(ctx: Context, vararg views: View): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        for (v in views) addView(v)
    }

    fun wrapRow(ctx: Context): LinearLayout = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

    fun hscroll(ctx: Context, content: View) = HorizontalScrollView(ctx).apply {
        isHorizontalScrollBarEnabled = false
        addView(content)
    }

    fun switch(ctx: Context, label: String, value: Boolean, onChange: (Boolean) -> Unit) = Switch(ctx).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextColor(TEXT)
        isChecked = value
        setPadding(0, dpi(ctx, 6f), 0, dpi(ctx, 6f))
        setOnCheckedChangeListener { _, b -> onChange(b) }
    }

    fun seek(ctx: Context, max: Int, value: Int, onChange: (Int) -> Unit) = SeekBar(ctx).apply {
        this.max = max
        progress = value
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) onChange(p) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    fun onText(e: EditText, f: (String) -> Unit) {
        e.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { f(s?.toString() ?: "") }
        })
    }

    /** Вставка фрагмента в EditText; «{}» — курсор ставится внутрь первых скобок. */
    fun insertSnippet(e: EditText, snippet: String) {
        val st = e.selectionStart.coerceAtLeast(0)
        val en = e.selectionEnd.coerceAtLeast(0)
        val a = minOf(st, en); val b = maxOf(st, en)
        e.text.replace(a, b, snippet)
        val brace = snippet.indexOf("{}")
        val amp = snippet.indexOf("&")
        val pos = when {
            brace >= 0 -> a + brace + 1
            amp >= 0 && snippet.startsWith("\\") -> a + amp
            else -> a + snippet.length
        }
        e.setSelection(pos.coerceIn(0, e.text.length))
        e.requestFocus()
    }

    /** Цветной кружок-образец. */
    class Swatch(ctx: Context, var color: Int, var picked: Boolean, val marker: Boolean = false) : View(ctx) {
        private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            val r = minOf(width, height) / 2f
            val cx = width / 2f; val cy = height / 2f
            if (picked) {
                p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = dp(context, 2.5f); p.color = ACCENT
                c.drawCircle(cx, cy, r - dp(context, 1.5f), p)
            }
            p.style = android.graphics.Paint.Style.FILL
            p.color = color
            c.drawCircle(cx, cy, r * 0.62f, p)
            if (color == Color.WHITE || Color.alpha(color) < 60) {
                p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = dp(context, 1f); p.color = 0xFFCBD5E1.toInt()
                c.drawCircle(cx, cy, r * 0.62f, p)
            }
        }
    }

    /** Предпросмотр толщины линии. */
    class WidthDot(ctx: Context, var w: Float, var picked: Boolean, var color: Int) : View(ctx) {
        private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            val cx = width / 2f; val cy = height / 2f
            if (picked) {
                p.style = android.graphics.Paint.Style.FILL; p.color = ACCENT_SOFT
                c.drawCircle(cx, cy, minOf(width, height) / 2f, p)
            }
            p.style = android.graphics.Paint.Style.FILL; p.color = color
            c.drawCircle(cx, cy, (w / 2f).coerceIn(dp(context, 1.5f), minOf(width, height) / 2.6f), p)
        }
    }

    fun frameLp(w: Int, h: Int, gravity: Int) = FrameLayout.LayoutParams(w, h, gravity)
}
