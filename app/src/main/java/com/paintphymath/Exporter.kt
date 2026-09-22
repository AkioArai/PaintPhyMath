package com.paintphymath

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.OutputStream
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Фон «бумаги» — рисуется одинаково на экране и при экспорте. */
object Paper {
    const val GRID = 40f

    val NAMES = listOf("Чистый лист", "Клетка", "Точки", "Линейка", "Миллиметровка", "Полярная сетка")
    val COLORS = listOf(
        Color.WHITE to "Белый", Color.rgb(253, 250, 240) to "Кремовый", Color.rgb(240, 245, 255) to "Голубоватый",
        Color.rgb(30, 30, 38) to "Тёмный", Color.rgb(28, 58, 46) to "Школьная доска"
    )

    fun isDark(c: Int): Boolean {
        val l = 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)
        return l < 110
    }

    fun lineColor(paperColor: Int, strength: Float): Int =
        if (isDark(paperColor)) El.withAlpha(Color.WHITE, 0.10f * strength) else El.withAlpha(Color.rgb(70, 90, 140), 0.16f * strength)

    /** Рисует разлиновку в прямоугольнике r (мировые координаты); zoom — текущий масштаб экрана. */
    fun draw(g: Gfx, type: Int, paperColor: Int, r: RectF, zoom: Float) {
        if (type == 0) return
        val c = lineColor(paperColor, 1f)
        val strong = lineColor(paperColor, 1.8f)
        val hair = 1f / zoom
        when (type) {
            1, 3 -> {
                var step = GRID
                while (step * zoom < 9f) step *= 5
                val p = GPath()
                var x = floor(r.left / step) * step
                if (type == 1) while (x <= r.right) { p.moveTo(x, r.top); p.lineTo(x, r.bottom); x += step }
                var y = floor(r.top / step) * step
                while (y <= r.bottom) { p.moveTo(r.left, y); p.lineTo(r.right, y); y += step }
                g.path(p, c, hair)
                if (type == 3) {
                    // поле слева
                    val m = GPath(); var mx = floor(r.left / (GRID * 20)) * GRID * 20 + GRID * 2
                    while (mx <= r.right) { m.moveTo(mx, r.top); m.lineTo(mx, r.bottom); mx += GRID * 20 }
                    g.path(m, El.withAlpha(Color.rgb(220, 80, 80), 0.35f), hair * 1.5f)
                }
            }
            2 -> {
                var step = GRID
                while (step * zoom < 12f) step *= 5
                val p = GPath()
                val rad = max(1.3f / zoom, 1.2f)
                var x = floor(r.left / step) * step
                val count = ((r.width() / step) * (r.height() / step))
                if (count > 20000) return
                while (x <= r.right) {
                    var y = floor(r.top / step) * step
                    while (y <= r.bottom) { p.addCircle(x, y, rad); y += step }
                    x += step
                }
                g.path(p, 0, 0f, strong)
            }
            4 -> {
                val minor = 8f
                val levels = floatArrayOf(minor, minor * 5, minor * 10)
                for ((li, step) in levels.withIndex()) {
                    if (step * zoom < 6f) continue
                    val p = GPath()
                    var x = floor(r.left / step) * step
                    while (x <= r.right) { p.moveTo(x, r.top); p.lineTo(x, r.bottom); x += step }
                    var y = floor(r.top / step) * step
                    while (y <= r.bottom) { p.moveTo(r.left, y); p.lineTo(r.right, y); y += step }
                    val col = if (isDark(paperColor)) El.withAlpha(Color.rgb(120, 200, 160), 0.08f + li * 0.07f)
                    else El.withAlpha(Color.rgb(230, 120, 60), 0.10f + li * 0.10f)
                    g.path(p, col, hair * (1f + li * 0.4f))
                }
            }
            5 -> {
                val cxp = 0f; val cyp = 0f
                val p = GPath()
                val maxR = max(max(Geo.dist(cxp, cyp, r.left, r.top), Geo.dist(cxp, cyp, r.right, r.bottom)),
                    max(Geo.dist(cxp, cyp, r.right, r.top), Geo.dist(cxp, cyp, r.left, r.bottom)))
                var step = GRID * 2
                while (step * zoom < 12f) step *= 5
                var rr = step
                var n = 0
                while (rr <= maxR && n < 400) { p.addCircle(cxp, cyp, rr); rr += step; n++ }
                for (k in 0 until 24) {
                    val a = Math.toRadians(k * 15.0)
                    p.moveTo(cxp, cyp); p.lineTo((cxp + Math.cos(a) * maxR).toFloat(), (cyp + Math.sin(a) * maxR).toFloat())
                }
                g.path(p, c, hair)
            }
        }
    }

    fun snap(type: Int, x: Float, y: Float): Pair<Float, Float>? = when (type) {
        1, 2 -> Pair(Math.round(x / (GRID / 2)) * (GRID / 2), Math.round(y / (GRID / 2)) * (GRID / 2))
        4 -> Pair(Math.round(x / 8f) * 8f, Math.round(y / 8f) * 8f)
        else -> null
    }
}

object Exporter {
    /** Рисует документ: сначала слой маркеров, затем основной слой. */
    fun render(doc: Doc, g: Gfx, only: Collection<El>? = null, skip: Set<Long> = emptySet(), layer: Int = -1) {
        val ctx = DrawCtx(doc)
        val list = only ?: doc.els
        if (layer == -1 || layer == 0) for (e in list) if (e.layer == 0 && e.id !in skip) safeDraw(e, g, ctx)
        if (layer == -1 || layer == 1) for (e in list) if (e.layer != 0 && e.id !in skip) safeDraw(e, g, ctx)
    }

    private fun safeDraw(e: El, g: Gfx, ctx: DrawCtx) {
        try { e.draw(g, ctx) } catch (t: Throwable) { }
    }

    fun bounds(doc: Doc, only: Collection<El>?): RectF {
        val r = doc.contentBounds(DrawCtx(doc), only) ?: RectF(0f, 0f, 800f, 600f)
        r.inset(-32f, -32f)
        return r
    }

    fun png(doc: Doc, only: Collection<El>?, scale: Float, withPaper: Boolean, transparent: Boolean): Bitmap {
        val r = bounds(doc, only)
        var s = scale
        val maxPx = 40_000_000f
        if (r.width() * r.height() * s * s > maxPx) s = sqrt(maxPx / (r.width() * r.height()))
        s = min(s, 8192f / max(r.width(), r.height()))
        val w = max(1, ceil(r.width() * s).toInt())
        val h = max(1, ceil(r.height() * s).toInt())
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (!transparent) c.drawColor(doc.paperColor)
        c.scale(s, s)
        c.translate(-r.left, -r.top)
        val g = CanvasGfx(c, s)
        if (withPaper && !transparent) Paper.draw(g, doc.paper, doc.paperColor, r, 1f)
        render(doc, g, only)
        return bmp
    }

    fun svg(doc: Doc, only: Collection<El>?, withPaper: Boolean, transparent: Boolean): String {
        val r = bounds(doc, only)
        val g = SvgGfx()
        if (withPaper && !transparent) Paper.draw(g, doc.paper, doc.paperColor, r, 1f)
        render(doc, g, only)
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" ")
            .append("width=\"").append(Svg.f(r.width())).append("\" height=\"").append(Svg.f(r.height()))
            .append("\" viewBox=\"").append(Svg.f(r.left)).append(' ').append(Svg.f(r.top)).append(' ')
            .append(Svg.f(r.width())).append(' ').append(Svg.f(r.height())).append("\">\n")
        sb.append("<title>").append(Svg.esc(doc.name)).append("</title>\n")
        if (!transparent) {
            sb.append("<rect x=\"").append(Svg.f(r.left)).append("\" y=\"").append(Svg.f(r.top))
                .append("\" width=\"").append(Svg.f(r.width())).append("\" height=\"").append(Svg.f(r.height()))
                .append("\" fill=\"").append(Svg.color(doc.paperColor)).append("\"/>\n")
        }
        sb.append(g.sb)
        sb.append("</svg>\n")
        return sb.toString()
    }

    fun pdf(doc: Doc, only: Collection<El>?, withPaper: Boolean, out: OutputStream) {
        val r = bounds(doc, only)
        val k = 0.75f // px → pt
        val pdf = PdfDocument()
        val info = PdfDocument.PageInfo.Builder(max(1, (r.width() * k).toInt()), max(1, (r.height() * k).toInt()), 1).create()
        val page = pdf.startPage(info)
        val c = page.canvas
        c.drawColor(doc.paperColor)
        c.scale(k, k)
        c.translate(-r.left, -r.top)
        val g = CanvasGfx(c, 1f)
        if (withPaper) Paper.draw(g, doc.paper, doc.paperColor, r, 1f)
        render(doc, g, only)
        pdf.finishPage(page)
        pdf.writeTo(out)
        pdf.close()
    }
}

/** Минимальный провайдер для «Поделиться» файлами из кэша приложения (без AndroidX). */
class ShareProvider : ContentProvider() {
    override fun onCreate() = true

    private fun file(uri: Uri): File? {
        val ctx = context ?: return null
        val name = uri.lastPathSegment ?: return null
        val f = File(File(ctx.cacheDir, "share"), name)
        return if (f.canonicalPath.startsWith(File(ctx.cacheDir, "share").canonicalPath)) f else null
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val f = file(uri) ?: return null
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = when (uri.lastPathSegment?.substringAfterLast('.')) {
        "png" -> "image/png"; "svg" -> "image/svg+xml"; "pdf" -> "application/pdf"; "json" -> "application/json"
        else -> "application/octet-stream"
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val f = file(uri)
        val c = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
        if (f != null) c.addRow(arrayOf<Any>(f.name, f.length()))
        return c
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        const val AUTH = "com.paintphymath.share"
        fun uriFor(name: String): Uri = Uri.parse("content://$AUTH/$name")
    }
}
