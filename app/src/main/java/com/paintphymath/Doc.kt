package com.paintphymath

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Документ = бесконечный холст + история изменений. */
class Doc(var id: String = "doc_" + System.currentTimeMillis(), var name: String = "Новый холст") {
    val els = ArrayList<El>()
    /** 0 чистый, 1 клетка, 2 точки, 3 линейка, 4 миллиметровка, 5 полярная сетка */
    var paper = 1
    var paperColor = Color.WHITE
    var created = System.currentTimeMillis()
    var modified = System.currentTimeMillis()
    var viewScale = 1f
    var viewTx = 0f
    var viewTy = 0f
    var dirty = false

    // ------------------------------------------------------------------ история

    class Item(val id: Long, val before: JSONObject?, val beforeIdx: Int) {
        var after: JSONObject? = null
        var afterIdx = -1
    }

    class Change(val items: List<Item>)

    private val undoStack = ArrayList<Change>()
    private val redoStack = ArrayList<Change>()
    var onChanged: (() -> Unit)? = null

    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()

    fun find(id: Long): El? = els.firstOrNull { it.id == id }

    fun axesAt(x: Float, y: Float): AxesEl? =
        els.lastOrNull { it is AxesEl && it.rect().apply { inset(-2f, -2f) }.contains(x, y) } as? AxesEl

    fun allAxes() = els.filterIsInstance<AxesEl>()

    /** Зависимые элементы: связи и графики, ссылающиеся на указанные id. */
    fun dependents(ids: Set<Long>): List<El> = els.filter {
        (it is LinkEl && (it.from in ids || it.to in ids)) || (it is PlotEl && it.axesId in ids)
    }

    private fun record(ch: Change) {
        undoStack.add(ch)
        if (undoStack.size > 300) undoStack.removeAt(0)
        redoStack.clear()
        touched()
    }

    private fun touched() {
        modified = System.currentTimeMillis()
        dirty = true
        onChanged?.invoke()
    }

    /** Транзакция: сначала touch()/added() для изменяемых элементов, затем commit(). */
    inner class Tx {
        private val idx = HashMap<Long, Int>().apply { els.forEachIndexed { i, e -> put(e.id, i) } }
        private val items = LinkedHashMap<Long, Item>()
        val isEmpty get() = items.isEmpty()

        fun touch(e: El) {
            if (items.containsKey(e.id)) return
            val i = idx[e.id]
            items[e.id] = if (i == null) Item(e.id, null, -1) else Item(e.id, e.toJson(), i)
        }

        fun added(e: El) { if (!items.containsKey(e.id)) items[e.id] = Item(e.id, null, -1) }

        fun commit(): Boolean {
            if (items.isEmpty()) return false
            val pos = HashMap<Long, Int>().apply { els.forEachIndexed { i, e -> put(e.id, i) } }
            val list = ArrayList<Item>()
            for (it in items.values) {
                val i = pos[it.id]
                val e = if (i != null) els[i] else null
                it.after = e?.toJson(); it.afterIdx = i ?: -1
                if (it.before == null && it.after == null) continue
                if (it.before != null && it.after != null && it.beforeIdx == it.afterIdx && it.before.toString() == it.after.toString()) continue
                list.add(it)
            }
            if (list.isEmpty()) return false
            record(Change(list))
            return true
        }
    }

    fun begin() = Tx()

    fun add(list: List<El>) {
        if (list.isEmpty()) return
        val items = list.map { Item(it.id, null, -1) }
        els.addAll(list)
        for (it in items) { it.afterIdx = els.indexOfFirst { e -> e.id == it.id }; it.after = find(it.id)?.toJson() }
        record(Change(items))
    }

    fun add(e: El) = add(listOf(e))

    fun remove(list: Collection<El>) {
        if (list.isEmpty()) return
        val all = LinkedHashSet<El>(list)
        all.addAll(dependents(list.map { it.id }.toSet()))
        val items = all.map { Item(it.id, it.toJson(), els.indexOf(it)) }
        els.removeAll(all)
        record(Change(items))
    }

    /** Изменить элементы с записью в историю. */
    fun modify(list: Collection<El>, block: () -> Unit) {
        if (list.isEmpty()) { block(); return }
        val items = list.map { Item(it.id, it.toJson(), els.indexOf(it)) }
        block()
        for (it in items) {
            val e = find(it.id)
            it.after = e?.toJson(); it.afterIdx = if (e != null) els.indexOf(e) else -1
        }
        record(Change(items))
    }

    /** Заменить набор элементов другим (например, частичный ластик). */
    fun replace(old: Collection<El>, new: List<El>) {
        val items = ArrayList<Item>()
        for (e in old) items.add(Item(e.id, e.toJson(), els.indexOf(e)))
        val pos = old.minOfOrNull { els.indexOf(it) }?.coerceAtLeast(0) ?: els.size
        els.removeAll(old.toSet())
        els.addAll(pos.coerceAtMost(els.size), new)
        for (e in new) items.add(Item(e.id, null, -1))
        for (it in items) {
            val e = find(it.id)
            it.after = e?.toJson(); it.afterIdx = if (e != null) els.indexOf(e) else -1
        }
        record(Change(items))
    }

    /** Порядок: на передний/задний план. */
    fun reorder(list: Collection<El>, front: Boolean) {
        modify(list) {
            els.removeAll(list.toSet())
            if (front) els.addAll(list) else els.addAll(0, list)
        }
    }

    private fun applyState(ch: Change, useBefore: Boolean) {
        val ids = ch.items.map { it.id }.toSet()
        els.removeAll { it.id in ids }
        val restore = ch.items.mapNotNull { item ->
            val js = if (useBefore) item.before else item.after
            val idx = if (useBefore) item.beforeIdx else item.afterIdx
            if (js != null) idx to js else null
        }.sortedBy { it.first }
        for ((idx, js) in restore) {
            val e = El.fromJson(js) ?: continue
            els.add(idx.coerceIn(0, els.size), e)
        }
    }

    fun undo(): Boolean {
        val ch = undoStack.removeLastOrNull() ?: return false
        applyState(ch, true)
        redoStack.add(ch)
        touched()
        return true
    }

    fun redo(): Boolean {
        val ch = redoStack.removeLastOrNull() ?: return false
        applyState(ch, false)
        undoStack.add(ch)
        touched()
        return true
    }

    fun clearHistory() { undoStack.clear(); redoStack.clear() }

    fun contentBounds(ctx: DrawCtx, only: Collection<El>? = null): RectF? {
        var r: RectF? = null
        for (e in only ?: els) {
            val b = e.bounds(ctx)
            if (b.isEmpty && b.width() == 0f) continue
            r = Geo.union(r, b)
        }
        return r
    }

    // ------------------------------------------------------------------ сериализация

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("v", 1); o.put("id", id); o.put("name", name); o.put("paper", paper); o.put("pc", paperColor)
        o.put("created", created); o.put("modified", modified)
        o.put("view", JSONArray().put(viewScale.toDouble()).put(viewTx.toDouble()).put(viewTy.toDouble()))
        val a = JSONArray()
        for (e in els) a.put(e.toJson())
        o.put("els", a)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): Doc {
            val d = Doc(o.optString("id", "doc_" + System.currentTimeMillis()), o.optString("name", "Холст"))
            d.paper = o.optInt("paper", 1); d.paperColor = o.optInt("pc", Color.WHITE)
            d.created = o.optLong("created", System.currentTimeMillis()); d.modified = o.optLong("modified", d.created)
            o.optJSONArray("view")?.let {
                d.viewScale = it.optDouble(0, 1.0).toFloat(); d.viewTx = it.optDouble(1, 0.0).toFloat(); d.viewTy = it.optDouble(2, 0.0).toFloat()
            }
            val a = o.optJSONArray("els") ?: JSONArray()
            for (i in 0 until a.length()) {
                val e = a.optJSONObject(i)?.let { El.fromJson(it) } ?: continue
                d.els.add(e)
            }
            return d
        }
    }
}

/** Хранилище документов во внутренней памяти приложения. */
class DocStore(ctx: Context) {
    private val dir = File(ctx.filesDir, "docs").apply { mkdirs() }

    data class Meta(val id: String, val name: String, val modified: Long, val thumb: File)

    fun list(): List<Meta> = dir.listFiles { f -> f.name.endsWith(".json") }?.mapNotNull { f ->
        try {
            // читаем только заголовок, не разбирая все элементы
            val text = f.readText()
            val o = JSONObject(text)
            Meta(o.optString("id"), o.optString("name"), o.optLong("modified"), File(dir, o.optString("id") + ".png"))
        } catch (e: Exception) { null }
    }?.sortedByDescending { it.modified } ?: emptyList()

    fun load(id: String): Doc? = try {
        Doc.fromJson(JSONObject(File(dir, "$id.json").readText()))
    } catch (e: Exception) { null }

    fun save(doc: Doc, thumb: Bitmap?) {
        val tmp = File(dir, doc.id + ".json.tmp")
        tmp.writeText(doc.toJson().toString())
        tmp.renameTo(File(dir, doc.id + ".json"))
        if (thumb != null) {
            File(dir, doc.id + ".png").outputStream().use { thumb.compress(Bitmap.CompressFormat.PNG, 90, it) }
        }
        doc.dirty = false
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
        File(dir, "$id.png").delete()
    }

    fun rename(id: String, name: String) {
        val d = load(id) ?: return
        d.name = name
        save(d, null)
    }

    fun duplicate(id: String): String? {
        val d = load(id) ?: return null
        val copy = Doc.fromJson(d.toJson())
        copy.id = "doc_" + System.currentTimeMillis()
        copy.name = d.name + " (копия)"
        save(copy, null)
        File(dir, "$id.png").takeIf { it.exists() }?.copyTo(File(dir, copy.id + ".png"), true)
        return copy.id
    }
}
