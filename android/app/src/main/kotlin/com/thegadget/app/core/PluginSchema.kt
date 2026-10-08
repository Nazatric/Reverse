package com.thegadget.app.core

/**
 * The Gadget plugin format — a 1:1 port of `src/plugins/schema.ts`.
 *
 * A plugin is a JSON document. Nothing is evaluated, so a plugin can never run code: it can only
 * describe changes to known UI targets (theme tokens, hub orbs, pages made of typed blocks).
 * Every rule, default and error message below matches the TypeScript validator, which is enforced
 * by the differential tests in `verification/`.
 */
class PluginError(message: String) : Exception(message)

data class PluginNodeSpec(
    val id: String,
    val label: String,
    val x: Double,
    val y: Double,
    val d: Double,
    val ly: Double,
    val icon: String? = null,
    val page: String? = null,
)

sealed interface PluginBlock {
    data class Text(val value: String) : PluginBlock
    data class Note(val value: String) : PluginBlock
    data class Header(val value: String) : PluginBlock
    data class Link(val label: String, val url: String) : PluginBlock
    data class Button(val label: String, val page: String? = null, val url: String? = null) : PluginBlock
    data class Tiles(val items: List<TileItem>) : PluginBlock

    data class TileItem(val label: String, val icon: String? = null, val page: String? = null, val url: String? = null)
}

data class PluginPageSpec(
    val id: String,
    val label: String,
    val title: String,
    val subtitle: String,
    val blocks: List<PluginBlock>,
)

data class PluginTheme(
    val accent: String? = null,
    val orb: List<String>? = null,
    val orbScale: Double? = null,
    val hubScale: Double? = null,
    val chainScale: Double? = null,
    val labelScale: Double? = null,
    val glow: Double? = null,
) {
    val isEmpty: Boolean
        get() = accent == null && orb == null && orbScale == null && hubScale == null &&
            chainScale == null && labelScale == null && glow == null
}

data class PluginDocument(
    val id: String,
    val name: String,
    val version: String? = null,
    val author: String? = null,
    val description: String? = null,
    val theme: PluginTheme? = null,
    val nodes: List<PluginNodeSpec> = emptyList(),
    val pages: List<PluginPageSpec> = emptyList(),
)

data class PluginRecord(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    /** pretty-printed JSON source, stored verbatim */
    val source: String,
    val doc: PluginDocument,
    val enabled: Boolean = true,
    val installed: Long = 0L,
)

/** Everything the renderer needs, already validated and flattened. */
data class ActivePlugin(
    val id: String,
    val doc: PluginDocument,
    val nodes: List<PluginNodeSpec>,
    val pages: List<PluginPageSpec>,
    val theme: PluginTheme,
)

object PluginSchema {
    val GLYPHS = listOf("music", "games", "homies", "config", "account", "play", "note", "star")
    val PAGES = listOf(
        "music", "albums", "songs", "playlists", "search", "now",
        "games", "2048", "homies", "account", "settings",
    )
    private val HEX = Regex("^#[0-9a-f]{6}$", RegexOption.IGNORE_CASE)
    private val PLUGIN_ID = Regex("^[a-z0-9][a-z0-9._-]{1,59}$", RegexOption.IGNORE_CASE)
    private val NUMBERY = Regex("^-?\\d+(\\.\\d+)?$")
    const val MAX_SOURCE = 200_000

    fun isGlyphName(value: String): Boolean = value in GLYPHS

    private fun fail(message: String): Nothing = throw PluginError(message)

    private fun str(value: JsonValue?, what: String, max: Int = 80): String {
        val text = (value as? JsonValue.Str)?.value
        if (text == null || text.trim().isEmpty()) fail("$what is required")
        if (text.length > max) fail("$what is too long (max $max characters)")
        return text.trim()
    }

    private fun optStr(value: JsonValue?, what: String, max: Int): String? =
        if (value == null || value is JsonValue.Null) null else str(value, what, max)

    private fun id(value: JsonValue?, what: String): String {
        val v = str(value, what, 60)
        if (!PLUGIN_ID.matches(v)) fail("$what must use letters, numbers, dots, dashes or underscores")
        return v
    }

    private fun asNumber(value: JsonValue?): Double? = when (value) {
        is JsonValue.Num -> value.value
        is JsonValue.Str -> value.value.toDoubleOrNull()
        is JsonValue.Bool -> if (value.value) 1.0 else 0.0
        else -> null
    }

    private fun num(value: JsonValue?, what: String, min: Double, max: Double): Double {
        val n = asNumber(value) ?: fail("$what must be a number")
        if (n.isNaN() || n.isInfinite()) fail("$what must be a number")
        if (n < min || n > max) fail("$what must be between ${trimNum(min)} and ${trimNum(max)}")
        return n
    }

    private fun optNum(value: JsonValue?, what: String, min: Double, max: Double): Double? =
        if (value == null || value is JsonValue.Null) null else num(value, what, min, max)

    private fun hex(value: JsonValue?, what: String): String {
        val text = (value as? JsonValue.Str)?.value
        if (text == null || !HEX.matches(text)) fail("$what must be a hex colour like #62BDF1")
        return text.lowercase()
    }

    private fun checkPage(value: JsonValue?, own: Set<String>, what: String): String {
        val v = str(value, what, 60)
        if (v !in PAGES && v !in own) fail("$what must be a built-in page or one declared by this plugin")
        return v
    }

    /** JavaScript prints integral bounds without a trailing `.0`; match that in messages. */
    private fun trimNum(d: Double): String = if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()

    /** Parse and validate plugin JSON. Throws [PluginError] with a readable message. */
    fun parsePlugin(raw: String, installedAt: Long = 0L): PluginRecord {
        if (raw.trim().isEmpty()) fail("The plugin file is empty")
        if (raw.length > MAX_SOURCE) fail("The plugin file is too large (200 KB max)")

        val data: JsonValue = try {
            Json.parse(raw)
        } catch (e: JsonParseException) {
            fail("Invalid JSON — ${e.message}")
        }
        val obj = data as? JsonValue.Obj ?: fail("The plugin must be a JSON object")

        val pluginId = id(obj["id"], "id")
        val name = str(obj["name"], "name", 60)
        val version = if (obj["version"] == null) "1.0.0" else str(obj["version"], "version", 24)
        val author = if (obj["author"] == null) "unknown" else str(obj["author"], "author", 60)
        val description = if (obj["description"] == null) "" else str(obj["description"], "description", 240)

        val pagesRaw = obj["pages"]
        val ownPages = HashSet<String>()
        if (pagesRaw != null && pagesRaw !is JsonValue.Null) {
            val arr = pagesRaw as? JsonValue.Arr ?: fail("pages must be an array")
            for (p in arr.items) {
                ownPages.add(id((p as? JsonValue.Obj)?.get("id"), "page id"))
            }
        }

        val pages = ArrayList<PluginPageSpec>()
        if (pagesRaw is JsonValue.Arr) {
            for (entry in pagesRaw.items) {
                val p = entry as? JsonValue.Obj ?: fail("each page must be an object")
                val pageId = id(p["id"], "page id")
                val label = str(p["label"], "page label", 40)
                val title = if (p["title"] == null) label else str(p["title"], "page title", 60)
                val subtitle = if (p["subtitle"] == null) "" else str(p["subtitle"], "page subtitle", 120)
                val blocksRaw = p["blocks"] ?: fail("page $pageId needs a blocks array")
                val blocksArr = blocksRaw as? JsonValue.Arr ?: fail("page $pageId needs a blocks array")
                if (blocksArr.items.size > 40) fail("page $pageId has too many blocks (40 max)")

                val blocks = blocksArr.items.mapIndexed { bi, rawBlock ->
                    val b = rawBlock as? JsonValue.Obj ?: fail("page $pageId block ${bi + 1} must be an object")
                    val where = "page $pageId block ${bi + 1}"
                    val type = (b["type"] as? JsonValue.Str)?.value
                    when (type) {
                        "text" -> PluginBlock.Text(str(b["value"], "$where value", 600))
                        "note" -> PluginBlock.Note(str(b["value"], "$where value", 600))
                        "header" -> PluginBlock.Header(str(b["value"], "$where value", 80))

                        "link" -> {
                            val link = str(b["url"], "$where url", 400)
                            if (!link.startsWith("http://", true) && !link.startsWith("https://", true)) {
                                fail("$where url must start with http:// or https://")
                            }
                            PluginBlock.Link(str(b["label"], "$where label", 60), link)
                        }

                        "button" -> {
                            val label2 = str(b["label"], "$where label", 60)
                            val hasPage = b["page"] != null && b["page"] !is JsonValue.Null
                            val hasUrl = b["url"] != null && b["url"] !is JsonValue.Null
                            if (!hasPage && !hasUrl) fail("$where needs a page or a url")
                            if (hasPage && hasUrl) fail("$where can't have both a page and a url")
                            PluginBlock.Button(
                                label = label2,
                                page = if (hasPage) checkPage(b["page"], ownPages, "$where page") else null,
                                url = if (hasUrl) str(b["url"], "$where url", 400) else null,
                            )
                        }

                        "tiles" -> {
                            val itemsRaw = b["items"] ?: fail("$where needs an items array")
                            val items = itemsRaw as? JsonValue.Arr ?: fail("$where needs an items array")
                            if (items.items.isEmpty()) fail("$where needs at least one item")
                            if (items.items.size > 12) fail("$where has too many items (12 max)")
                            PluginBlock.Tiles(
                                items.items.mapIndexed { ti, rawItem ->
                                    val item = rawItem as? JsonValue.Obj ?: fail("$where item ${ti + 1} must be an object")
                                    val itemLabel = str(item["label"], "$where item ${ti + 1} label", 40)
                                    val icon = optStr(item["icon"], "$where item ${ti + 1} icon", 20)
                                    if (icon != null && icon !in GLYPHS) {
                                        fail("$where item ${ti + 1} icon must be one of: ${GLYPHS.joinToString(", ")}")
                                    }
                                    val itemHasPage = item["page"] != null && item["page"] !is JsonValue.Null
                                    val itemHasUrl = item["url"] != null && item["url"] !is JsonValue.Null
                                    if (!itemHasPage && !itemHasUrl) fail("$where item ${ti + 1} needs a page or a url")
                                    PluginBlock.TileItem(
                                        label = itemLabel,
                                        icon = icon,
                                        page = if (itemHasPage) checkPage(item["page"], ownPages, "$where item ${ti + 1} page") else null,
                                        url = if (itemHasUrl) str(item["url"], "$where item ${ti + 1} url", 400) else null,
                                    )
                                },
                            )
                        }

                        else -> fail("$where has an unknown type. Use text, note, header, link, button or tiles")
                    }
                }
                pages.add(PluginPageSpec(pageId, label, title, subtitle, blocks))
            }
        }

        val nodes = ArrayList<PluginNodeSpec>()
        val nodesRaw = obj["nodes"]
        if (nodesRaw != null && nodesRaw !is JsonValue.Null) {
            val arr = nodesRaw as? JsonValue.Arr ?: fail("nodes must be an array")
            val seen = HashSet<String>()
            for (rawNode in arr.items) {
                val n = rawNode as? JsonValue.Obj ?: fail("each node must be an object")
                val nodeId = id(n["id"], "node id")
                if (!seen.add(nodeId)) fail("node id $nodeId is used twice")
                if (nodeId in PAGES) fail("node id $nodeId clashes with a built-in page")
                if (!NUMBERY.matches(numbery(n["x"]))) fail("node $nodeId x must be a number")
                if (!NUMBERY.matches(numbery(n["y"]))) fail("node $nodeId y must be a number")
                val x = num(n["x"], "node $nodeId x", -10.0, 110.0)
                val y = num(n["y"], "node $nodeId y", -10.0, 110.0)
                val d = num(n["d"] ?: JsonValue.Num(96.0), "node $nodeId d", 56.0, 260.0)
                val ly = optNum(n["ly"], "node $nodeId ly", 4.0, 60.0) ?: 14.0
                val icon = optStr(n["icon"], "node $nodeId icon", 20)
                if (icon != null && icon !in GLYPHS) fail("node $nodeId icon must be one of: ${GLYPHS.joinToString(", ")}")
                val hasPage = n["page"] != null && n["page"] !is JsonValue.Null
                nodes.add(
                    PluginNodeSpec(
                        id = nodeId,
                        label = str(n["label"], "node $nodeId label", 24),
                        x = x,
                        y = y,
                        d = d,
                        ly = ly,
                        icon = icon,
                        page = if (hasPage) checkPage(n["page"], ownPages, "node $nodeId page") else null,
                    ),
                )
            }
            if (nodes.size > 12) fail("too many nodes (12 max)")
        }

        var theme: PluginTheme? = null
        val themeRaw = obj["theme"]
        if (themeRaw != null && themeRaw !is JsonValue.Null) {
            val t = themeRaw as? JsonValue.Obj ?: fail("theme must be an object")
            theme = PluginTheme(
                accent = t["accent"]?.let { hex(it, "theme accent") },
                orb = t["orb"]?.let { raw ->
                    val items = raw as? JsonValue.Arr ?: fail("theme orb must be an array of 4 hex colours")
                    if (items.items.size != 4) fail("theme orb must be an array of 4 hex colours")
                    items.items.mapIndexed { i, c -> hex(c, "theme orb colour ${i + 1}") }
                },
                orbScale = optNum(t["orbScale"], "theme orbScale", 0.7, 1.5),
                hubScale = optNum(t["hubScale"], "theme hubScale", 0.7, 1.5),
                chainScale = optNum(t["chainScale"], "theme chainScale", 0.5, 2.0),
                labelScale = optNum(t["labelScale"], "theme labelScale", 0.7, 1.7),
                glow = optNum(t["glow"], "theme glow", 0.3, 1.8),
            )
        }

        // The web validator only checks that the `theme` *field* exists: an empty object still
        // counts as "something to apply" (it is then a no-op install).
        if (nodes.isEmpty() && pages.isEmpty() && theme == null) {
            fail("This plugin has nothing to apply. Add nodes, pages or a theme.")
        }

        return PluginRecord(
            id = pluginId,
            name = name,
            version = version,
            author = author,
            description = description,
            source = raw.trim(),
            doc = PluginDocument(
                id = pluginId,
                name = name,
                version = version,
                author = author,
                description = description,
                theme = theme ?: PluginTheme(),
                nodes = nodes,
                pages = pages,
            ),
            enabled = true,
            installed = installedAt,
        )
    }

    /** `String(n.x ?? "")` from the TypeScript validator, used by its pre-check regex. */
    private fun numbery(value: JsonValue?): String = when (value) {
        null, is JsonValue.Null -> ""
        is JsonValue.Num -> if (value.value == Math.floor(value.value) && !value.value.isInfinite()) {
            value.value.toLong().toString()
        } else {
            value.value.toString()
        }
        is JsonValue.Str -> value.value
        is JsonValue.Bool -> value.value.toString()
        else -> ""
    }

    /**
     * Merge semantics of `registry.ts`: only enabled plugins contribute, themes are applied in
     * install order with `Object.assign` so a later plugin overrides earlier per-property values.
     */
    fun mergeTheme(records: List<PluginRecord>): PluginTheme {
        var accent: String? = null
        var orb: List<String>? = null
        var orbScale: Double? = null
        var hubScale: Double? = null
        var chainScale: Double? = null
        var labelScale: Double? = null
        var glow: Double? = null
        for (r in records) {
            if (!r.enabled) continue
            val t = r.doc.theme ?: continue
            t.accent?.let { accent = it }
            t.orb?.let { orb = it }
            t.orbScale?.let { orbScale = it }
            t.hubScale?.let { hubScale = it }
            t.chainScale?.let { chainScale = it }
            t.labelScale?.let { labelScale = it }
            t.glow?.let { glow = it }
        }
        return PluginTheme(accent, orb, orbScale, hubScale, chainScale, labelScale, glow)
    }

    fun activePlugins(records: List<PluginRecord>): List<ActivePlugin> =
        records.filter { it.enabled }.map { r ->
            ActivePlugin(
                id = r.id,
                doc = r.doc,
                nodes = r.doc.nodes,
                pages = r.doc.pages,
                theme = r.doc.theme ?: PluginTheme(),
            )
        }

    /** Flat list of every plugin-declared page, in install order. */
    fun pluginPages(records: List<PluginRecord>): List<Pair<String, PluginPageSpec>> =
        records.filter { it.enabled }.flatMap { r -> r.doc.pages.map { r.id to it } }

    /** Re-installing keeps the previous `enabled` and `installed` values (see `installPlugin`). */
    fun install(records: List<PluginRecord>, record: PluginRecord, now: Long): List<PluginRecord> {
        val existing = records.firstOrNull { it.id == record.id }
        return if (existing != null) {
            records.map { if (it.id == record.id) record.copy(enabled = existing.enabled, installed = existing.installed) else it }
        } else {
            records + record.copy(installed = if (record.installed == 0L) now else record.installed)
        }
    }

    /** The renderer resolves `plugin:<pluginId>:<pageId>` ids as well as bare page ids. */
    fun resolvePage(
        pages: List<Pair<String, PluginPageSpec>>,
        pageRef: String,
    ): Pair<String, PluginPageSpec>? =
        pages.firstOrNull { (pluginId, page) -> "plugin:$pluginId:${page.id}" == pageRef } ?: pages.firstOrNull { it.second.id == pageRef }
}
