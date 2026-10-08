/*
 * Differential test: the Kotlin port against the reference vectors produced from the real web
 * app sources (verification/gen-vectors.mjs).
 *
 * Usage:
 *   verification/run.sh            # compiles the core + this file, then runs it
 *
 * Every check prints its own PASS/FAIL line; the process exits non-zero if anything fails.
 */
import com.thegadget.app.core.AbsoluteDate
import com.thegadget.app.core.AbsoluteDateFormatter
import com.thegadget.app.core.ByteArraySource
import com.thegadget.app.core.GadgetMetrics
import com.thegadget.app.core.GadgetText
import com.thegadget.app.core.Direction
import com.thegadget.app.core.NavDirection
import com.thegadget.app.core.Game2048
import com.thegadget.app.core.Tile
import com.thegadget.app.core.Json
import com.thegadget.app.core.JsonValue
import com.thegadget.app.core.Library
import com.thegadget.app.core.GadgetSettings
import com.thegadget.app.core.Iris
import com.thegadget.app.core.Links
import com.thegadget.app.core.NavStack
import com.thegadget.app.core.NavState
import com.thegadget.app.core.Origin
import com.thegadget.app.core.Route
import com.thegadget.app.core.key
import com.thegadget.app.core.StorageKeys
import com.thegadget.app.core.PathData
import com.thegadget.app.core.PluginError
import com.thegadget.app.core.PluginSchema
import com.thegadget.app.core.RandomSource
import com.thegadget.app.core.SocialCode
import com.thegadget.app.core.TagReader
import com.thegadget.app.ui.SvgPaths
import com.thegadget.app.core.Track
import java.io.File
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale

var failures = 0
var checks = 0

fun expect(label: String, actual: Any?, expected: Any?) {
    checks++
    if (actual == expected) return
    failures++
    println("  FAIL $label\n       kotlin   = $actual\n       reference = $expected")
}

fun expectClose(label: String, actual: Double, expected: Double, eps: Double = 1e-9) {
    checks++
    if (kotlin.math.abs(actual - expected) <= eps) return
    failures++
    println("  FAIL $label  kotlin=$actual reference=$expected")
}

fun obj(v: JsonValue?): JsonValue.Obj = v as JsonValue.Obj
fun JsonValue.Obj.field(name: String): JsonValue? = this.entries[name]
fun str(v: JsonValue?): String? = (v as? JsonValue.Str)?.value
fun num(v: JsonValue?): Double? = (v as? JsonValue.Num)?.value
fun long(v: JsonValue?): Long? = num(v)?.toLong()
fun arr(v: JsonValue?): List<JsonValue> = (v as? JsonValue.Arr)?.items ?: emptyList()

fun load(dir: File, name: String): JsonValue = Json.parse(File(dir, "$name.json").readText())

/** The same LCG the vector generator used, consumed in the same order. */
class Lcg(seed: Long = 123456789L) : RandomSource {
    private var state = seed
    override fun nextDouble(): Double {
        state = (state * 1103515245L + 12345L) and 0x7fffffffL
        return state.toDouble() / 0x80000000L.toDouble()
    }
}

fun main(args: Array<String>) {
    val here = File(args.firstOrNull() ?: "/home/user/Reverse/verification/vectors")
    println("vectors: ${here.absolutePath}")

    /* ------------------------------------------------------------ metrics */
    run {
        val vectors = load(here, "metrics") as JsonValue.Arr
        var worst = 0.0
        for (v in vectors.items) {
            val o = obj(v)
            val w = num(o.field("w"))!!.toFloat()
            val h = num(o.field("h"))!!.toFloat()
            val m = GadgetMetrics.compute(w, h)
            val e = o
            worst = maxOf(
                worst,
                kotlin.math.abs(m.sw - num(e.field("sw"))!!.toFloat()).toDouble(),
                kotlin.math.abs(m.sh - num(e.field("sh"))!!.toFloat()).toDouble(),
                kotlin.math.abs(m.fx - num(e.field("fx"))!!.toFloat()).toDouble(),
                kotlin.math.abs(m.fy - num(e.field("fy"))!!.toFloat()).toDouble(),
                kotlin.math.abs(m.u - num(e.field("u"))!!.toFloat()).toDouble(),
            )
            if (worst > 1e-4) {
                failures++
                println("  FAIL metrics ${w}x$h  sw=${m.sw}/${num(e.field("sw"))} sh=${m.sh}/${num(e.field("sh"))} u=${m.u}/${num(e.field("u"))}")
                break
            }
            checks++
        }
        println(if (worst <= 1e-4) "PASS metrics        ${vectors.items.size} viewports, max delta ${"%.6f".format(worst)}px" else "FAIL metrics")
    }

    /* ------------------------------------------------------------ text */
    run {
        val v = obj(load(here, "text"))
        var bad = 0
        for (row in arr(v.field("titles"))) {
            val o = obj(row)
            val got = GadgetText.titleFromFile(str(o.field("in"))!!)
            if (got != str(o.field("out"))) { bad++; println("  FAIL titleFromFile(${str(o.field("in"))}) = '$got' reference '${str(o.field("out"))}'") }
        }
        for (row in arr(v.field("formatTime"))) {
            val o = obj(row)
            val got = GadgetText.formatTime(num(o.field("in"))!!)
            if (got != str(o.field("out"))) { bad++; println("  FAIL formatTime(${num(o.field("in"))}) = '$got' reference '${str(o.field("out"))}'") }
        }
        for (row in arr(v.field("durations"))) {
            val o = obj(row)
            val got = GadgetText.duration(num(o.field("in"))!!.toInt())
            if (got != str(o.field("out"))) { bad++; println("  FAIL duration(${num(o.field("in"))}) = '$got' reference '${str(o.field("out"))}'") }
        }
        for (row in arr(v.field("fmtDuration"))) {
            val o = obj(row)
            val got = GadgetText.fmtDuration(num(o.field("in"))!!)
            if (got != str(o.field("out"))) { bad++; println("  FAIL fmtDuration(${num(o.field("in"))}) = '$got' reference '${str(o.field("out"))}'") }
        }
        for (row in arr(v.field("links"))) {
            val o = obj(row)
            val got = Links.normalise(str(o.field("in"))!!)
            if (got != str(o.field("out"))) { bad++; println("  FAIL normaliseLink('${str(o.field("in"))}') = '$got' reference '${str(o.field("out"))}'") }
        }
        // `ago` falls back to a locale date past 30 days; pin the JVM formatter to en-US short.
        AbsoluteDate.formatter = object : AbsoluteDateFormatter {
            val fmt = SimpleDateFormat("M/d/yyyy", Locale.US)
            override fun format(epochMillis: Long): String = fmt.format(Date(epochMillis))
        }
        for (row in arr(v.field("ago"))) {
            val o = obj(row)
            val ts = long(o.field("in"))?.takeIf { it != 0L }
            val got = GadgetText.ago(ts, long(o.field("now"))!!)
            if (got != str(o.field("out"))) { bad++; println("  FAIL ago(${o.field("in")}) = '$got' reference '${str(o.field("out"))}'") }
        }
        for (row in arr(v.field("letters"))) {
            val o = obj(row)
            val got = Library.letterOf(str(o.field("in"))!!)
            if (got != str(o.field("out"))) { bad++; println("  FAIL letterOf('${str(o.field("in"))}') = '$got' reference '${str(o.field("out"))}'") }
        }
        run {
            val o = obj(v.field("byTitle"))
            val input = arr(o.field("in")).map { str(it)!! }
            val sorted = input.map { Track(id = it, title = it, folder = "f", uri = "") }
                .sortedWith(Library.byTitle).map { it.title }
            val want = arr(o.field("out")).map { str(it)!! }
            if (sorted != want) { bad++; println("  FAIL byTitle order = $sorted reference $want") }
        }
        for (row in arr(v.field("topTracks"))) {
            val o = obj(row)
            val lib = arr(o.field("lib")).map { obj(it) }.map { Track(id = str(it.field("id"))!!, title = str(it.field("title"))!!, folder = "f", uri = "") }
            val stats = obj(o.field("stats")).entries.mapValues { num(it.value)!! }
            val got = stats.entries.filter { it.value >= 30 }.sortedByDescending { it.value }.take(3).map { (id, sec) ->
                (lib.find { it.id == id }?.title ?: "song") to sec
            }
            val want = arr(o.field("out")).map { obj(it) }.map { str(it.field("title"))!! to num(it.field("sec"))!! }
            if (got != want) { bad++; println("  FAIL topTracks = $got reference $want") }
        }
        checks++
        if (bad == 0) println("PASS text           all formatter/collation/topTracks rows match") else { failures += bad; println("FAIL text           $bad mismatches") }
    }

    /* ------------------------------------------------------------ plugins */
    run {
        val vectors = load(here, "plugins") as JsonValue.Arr
        var bad = 0
        for (row in vectors.items) {
            val o = obj(row)
            val label = str(o.field("label"))!!
            val wantOk = (o.field("ok") as JsonValue.Bool).value
            try {
                val rec = PluginSchema.parsePlugin(PLUGIN_RAW[label]!!)
                if (!wantOk) { bad++; println("  FAIL plugins[$label] accepted, reference rejected: ${str(o.field("error"))}") ; continue }
                if (rec.id != str(o.field("id"))) { bad++; println("  FAIL plugins[$label] id ${rec.id} != ${str(o.field("id"))}") }
                if (rec.name != str(o.field("name"))) { bad++; println("  FAIL plugins[$label] name ${rec.name} != ${str(o.field("name"))}") }
                if (rec.version != str(o.field("version"))) { bad++; println("  FAIL plugins[$label] version ${rec.version} != ${str(o.field("version"))}") }
                if (rec.author != str(o.field("author"))) { bad++; println("  FAIL plugins[$label] author ${rec.author} != ${str(o.field("author"))}") }
                val nodes = rec.doc.nodes?.size ?: 0
                val pages = rec.doc.pages?.size ?: 0
                if (nodes != num(o.field("nodes"))!!.toInt()) { bad++; println("  FAIL plugins[$label] nodes $nodes != ${num(o.field("nodes"))}") }
                if (pages != num(o.field("pages"))!!.toInt()) { bad++; println("  FAIL plugins[$label] pages $pages != ${num(o.field("pages"))}") }
                if (rec.source != PLUGIN_RAW[label]) { bad++; println("  FAIL plugins[$label] source should be stored verbatim") }
            } catch (err: PluginError) {
                val want = str(o.field("error"))!!
                // Syntax errors come from the JSON engine, so only the *category* is comparable:
                // V8 says 'Invalid JSON … Expected property name or '}' in JSON at position 1',
                // the Kotlin parser says 'Invalid JSON — Expected "…'. Everything the validator
                // itself produces (ranges, required fields, clashing ids) must match exactly.
                val syntaxOnly = want.startsWith("Invalid JSON") && err.message!!.startsWith("Invalid JSON")
                if (wantOk) { bad++; println("  FAIL plugins[$label] rejected: ${err.message}, reference accepted") }
                else if (!syntaxOnly && err.message != want) { bad++; println("  FAIL plugins[$label] error '${err.message}' != '$want'") }
            }
        }
        checks++
        if (bad == 0) println("PASS plugins        ${vectors.items.size} documents (valid + every rejection branch)") else { failures += bad; println("FAIL plugins        $bad mismatches") }
    }

    /* ------------------------------------------------------------ social */
    run {
        val v = obj(load(here, "social"))
        var bad = 0
        for (row in arr(v.field("normalize"))) {
            val o = obj(row)
            if (SocialCode.normalize(str(o.field("in"))!!) != str(o.field("out"))) { bad++; println("  FAIL normalize('${str(o.field("in"))}') -> '${SocialCode.normalize(str(o.field("in"))!!)}' != '${str(o.field("out"))}'") }
        }
        for (row in arr(v.field("valid"))) {
            val o = obj(row)
            if (SocialCode.isValid(str(o.field("in"))!!) != (o.field("out") as JsonValue.Bool).value) { bad++; println("  FAIL isValid('${str(o.field("in"))}')") }
        }
        for (row in arr(v.field("chatTopic"))) {
            val o = obj(row)
            val got = SocialCode.chatTopic(str(o.field("a"))!!, str(o.field("b"))!!)
            if (got != str(o.field("out"))) { bad++; println("  FAIL chatTopic = '$got' != '${str(o.field("out"))}'") }
        }
        for (row in arr(v.field("presenceTopic"))) {
            val o = obj(row)
            if (SocialCode.presenceTopic(str(o.field("in"))!!) != str(o.field("out"))) { bad++; println("  FAIL presenceTopic('${str(o.field("in"))}')") }
        }
        checks++
        if (bad == 0) println("PASS social         codes, normalisation and topic construction") else { failures += bad; println("FAIL social") }
    }

    /* ------------------------------------------------------------ 2048 engine */
    run {
        val v = obj(load(here, "game2048"))
        var bad = 0
        val random = Lcg()
        var board = Game2048.fresh({ idCounter++ }, random)
        for (row in arr(v.field("trace"))) {
            val o = obj(row)
            val op = str(o.field("op"))!!
            val want = arr(o.field("tiles")).map { str(it)!! }
            if (op == "fresh") {
                if (print(board) != want) { bad++; println("  FAIL 2048 fresh\n     ${print(board)}\n     $want") }
                continue
            }
            if (op.startsWith("move:")) {
                val dir = when (op.removePrefix("move:")) {
                    "up" -> Direction.UP; "down" -> Direction.DOWN
                    "left" -> Direction.LEFT; else -> Direction.RIGHT
                }
                val r = Game2048.move(board, dir) { idCounter++ }
                if (r.moved != (o.field("moved") as JsonValue.Bool).value) { bad++; println("  FAIL 2048 $op moved=${r.moved} != ${(o.field("moved") as JsonValue.Bool).value}") }
                if (r.gained != num(o.field("gained"))!!.toInt()) { bad++; println("  FAIL 2048 $op gained=${r.gained} != ${num(o.field("gained"))}") }
                if (print(r.tiles) != want) { bad++; println("  FAIL 2048 $op\n     ${print(r.tiles)}\n     $want") }
                board = r.tiles
            } else if (op == "spawn") {
                board = Game2048.spawn(board, { idCounter++ }, random)
                if (print(board) != want) { bad++; println("  FAIL 2048 spawn\n     ${print(board)}\n     $want") }
            } else if (op == "stuck") {
                val values = arr(o.field("values")).map { (it as JsonValue.Bool).value }
                val boards = arr(o.field("boards")).map { b -> arr(b).map { s -> str(s)!! } }
                for ((i, tiles) in boards.withIndex()) {
                    val rebuilt = tiles.map { deserialize(it) }
                    if (Game2048.isStuck(rebuilt) { idCounter++ } != values[i]) {
                        bad++
                        println("  FAIL 2048 isStuck(${tiles}) = ${Game2048.isStuck(rebuilt) { idCounter++ }} != ${values[i]}")
                    }
                }
            }
        }
        checks++
        if (bad == 0) println("PASS game2048       full move/spawn trace (${arr(v.field("trace")).size} steps) + stuck detection") else { failures += bad; println("FAIL game2048       $bad mismatches") }
    }

    /* ------------------------------------------------------------ metadata */
    run {
        val vectors = load(here, "metadata") as JsonValue.Arr
        var bad = 0
        for (row in vectors.items) {
            val o = obj(row)
            val bytes = Base64.getDecoder().decode(str(o.field("bytes"))!!)
            val meta = TagReader.readTags(ByteArraySource(bytes))
            val e = obj(o.field("expect"))
            val label = str(o.field("label"))!!
            if (meta.title != str(e.field("title"))) { bad++; println("  FAIL meta[$label] title '${meta.title}' != '${str(e.field("title"))}'") }
            if (meta.artist != str(e.field("artist"))) { bad++; println("  FAIL meta[$label] artist '${meta.artist}' != '${str(e.field("artist"))}'") }
            if (meta.album != str(e.field("album"))) { bad++; println("  FAIL meta[$label] album '${meta.album}' != '${str(e.field("album"))}'") }
            if ((meta.cover != null) != (e.field("hasCover") as JsonValue.Bool).value) { bad++; println("  FAIL meta[$label] cover present=${meta.cover != null} != ${(e.field("hasCover") as JsonValue.Bool).value}") }
        }
        checks++
        if (bad == 0) println("PASS metadata       ${vectors.items.size} real fixtures (ID3v2.3/2.4, ID3v1.1, FLAC, MP4)") else { failures += bad; println("FAIL metadata       $bad mismatches") }
    }

    /* ------------------------------------------------------------ navigation + settings
     * These are invariant tests rather than differential vectors: the web app's navigation lives
     * inside a React context, so the *rules* (exactly one level per back, hub pushes capture the
     * origin, Escape/Back === history.back(), motion-off is instant) are asserted directly. */
    run {
        val home = NavState()
        var st = NavStack.push(home, Route.Music, Origin(10f, 20f, 30f), fromHub = true)
        expect("nav origin captured on hub push", st.origin, Origin(10f, 20f, 30f))
        st = NavStack.push(st, Route.Albums, Origin(0f, 0f, 1f), fromHub = false)
        st = NavStack.push(st, Route.Album("chrome"), Origin(0f, 0f, 1f), fromHub = false)
        expect("nav stack depth", st.stack.size, 3)
        expect("nav route key", st.route?.key(), "album:chrome")
        val back1 = NavStack.back(st)
        expect("back pops exactly one level", back1.stack.map { it.key() }, listOf("music", "albums"))
        expect("back direction", back1.dir, NavDirection.BACK)
        val back2 = NavStack.back(back1)
        expect("back again", back2.stack.map { it.key() }, listOf("music"))
        expect("back at hub is a no-op", NavStack.back(NavStack.back(back2)).isEmpty, true)
        expect("home clears the stack", NavStack.home(back1).stack, emptyList<Route>())
        expect("plugin page resolves by qualified id", NavStack.resolvePluginPage("plugin:example.arcade:arcade.page", listOf("example.arcade" to "arcade.page")), "example.arcade" to "arcade.page")
        expect("plugin page resolves by bare id", NavStack.resolvePluginPage("arcade.page", listOf("example.arcade" to "arcade.page")), "example.arcade" to "arcade.page")
        expect("unknown plugin page", NavStack.resolvePluginPage("nope", listOf("a" to "b")), null)

        // iris geometry: start = max(.06, r/300), end = (far/300)*1.06
        expectClose("iris start min", Iris.start(0f).toDouble(), 0.06, 1e-6)
        expectClose("iris start from node", Iris.start(150f).toDouble(), 0.5, 1e-6)
        expectClose("iris end", Iris.end(Origin(195f, 422f, 40f), 390f, 844f).toDouble(), (kotlin.math.hypot(195.0, 422.0) / 300.0 * 1.06), 1e-4)

        val d = GadgetSettings()
        expect("defaults", listOf(d.sounds, d.haptics, d.hour24, d.ambient, d.parallax, d.chainSway, d.reduceMotion, d.y2k, d.autoImmersive, d.notify, d.keepAwake, d.artwork),
            listOf(true, true, false, true, true, true, false, false, true, false, true, true))
        expect("motion off disables ambient + sway", listOf(GadgetSettings(reduceMotion = true).ambientOn, GadgetSettings(reduceMotion = true).swayOn), listOf(false, false))
        expect("slider clamps", GadgetSettings().with("orbScale", 9f).orbScale, 1.5f)
        expect("glow clamps", GadgetSettings().with("glow", 0.1f).glow, 0.3f)
        expect("scale keys", GadgetSettings.SCALE_KEYS, listOf("orbScale", "hubScale", "chainScale", "labelScale", "glow"))
        expect("storage keys", listOf(StorageKeys.PROFILE, StorageKeys.SETTINGS, StorageKeys.BEST_2048, StorageKeys.HANDLE_KEY),
            listOf("gadget:profile", "gadget:settings", "gadget:best2048", "music-folder"))
        checks++
        println("PASS navigation     stack semantics, iris geometry, settings defaults and clamps")
    }

    /* ------------------------------------------------------------ svg path geometry
     * Every icon, the wireframe, the logo and the mascot are re-drawn natively from the same `d`
     * strings the web sources carry (ui/SvgPaths.kt, generated by extract-svg.mjs). This compares
     * the Kotlin parser + elliptical-arc converter + arc-length sampler against samples taken in
     * Chromium by verification/gen-path-vectors.mjs (getTotalLength / getPointAtLength).
     *
     * The tolerance is a flattening budget, not a fudge factor: the browser evaluates arcs and
     * curves adaptively, SvgPath.kt flattens at 0.25 user units. Both sides sample at the same
     * arc-length fractions of *their own* total length, so a slightly different total shifts
     * samples along the curve — that is what the second term covers. */
    run {
        // Measured worst case over all 70 paths: 0.0093u of sample error, 0.0118u of length error.
        // 0.05u keeps a 5x margin for float/JVM drift while still catching a real parser regression
        // (the arc-flag bug this block was written to catch was worth ~2u of length).
        val POINT_EPS = 0.05
        val LENGTH_EPS = 0.05
        val doc = obj(load(here, "paths"))
        val paths = arr(doc.field("paths"))
        var bad = 0
        var worstPoint = 0.0
        var worstPointAt = ""
        var worstLength = 0.0
        var worstLengthAt = ""
        for (row in paths) {
            val r = obj(row)
            val label = "${str(r.field("group"))}#${long(r.field("index"))}"
            val refLen = num(r.field("length")) ?: 0.0
            val path = PathData.parse(str(r.field("d")) ?: "")
            val lenDelta = kotlin.math.abs(path.totalLength.toDouble() - refLen)
            if (lenDelta > worstLength) { worstLength = lenDelta; worstLengthAt = label }
            val pts = arr(r.field("points"))
            val last = pts.size - 1
            var worstHere = 0.0
            for (i in 0..last) {
                val p = arr(pts[i])
                val got = path.sampleAtFraction(if (last == 0) 0f else i.toFloat() / last.toFloat())
                val d = kotlin.math.hypot(got.first.toDouble() - (num(p[0]) ?: 0.0), got.second.toDouble() - (num(p[1]) ?: 0.0))
                if (d > worstHere) worstHere = d
                if (d > worstPoint) { worstPoint = d; worstPointAt = "$label @ ${i}/${last}" }
            }
            expectClose("path $label", worstHere, 0.0, POINT_EPS)
            if (lenDelta > LENGTH_EPS) { bad++; println("  FAIL length $label kotlin=${path.totalLength} reference=$refLen") }
        }
        failures += bad
        if (bad == 0 && worstPoint <= POINT_EPS && worstLength <= LENGTH_EPS) {
            println("PASS paths          ${paths.size} paths, worst sample delta ${"%.4f".format(worstPoint)}u ($worstPointAt), worst length delta ${"%.4f".format(worstLength)}u ($worstLengthAt)")
        } else {
            println("FAIL paths          ${paths.size} paths, worst sample delta ${"%.4f".format(worstPoint)}u, worst length delta ${"%.4f".format(worstLength)}u")
        }
    }

    /* ------------------------------------------------------------ generated svg catalogue
     * ui/SvgPaths.kt is generated from the web sources by extract-svg.mjs. This asserts the
     * checked-in copy is exactly the set of paths that was sampled in the browser
     * (vectors/paths.json), that every primitive carries the geometry its kind needs, and that
     * every gradient / clip reference resolves inside its own group — so a stale or half-parsed
     * generated file can never quietly cost the native app a curve. */
    run {
        val doc = obj(load(here, "paths"))
        val sampled = arr(doc.field("paths"))
        val groups = SvgPaths.groups
        val elementCount = groups.sumOf { it.elements.size }
        val pathCount = groups.sumOf { g -> g.elements.count { it.kind == "path" } }
        expect("svg group count", groups.size, 47)
        expect("svg element count", elementCount, 132)
        expect("svg element kinds", groups.flatMap { g -> g.elements.map { it.kind } }.groupingBy { it }.eachCount(),
            mapOf("path" to 90, "circle" to 23, "ellipse" to 15, "rect" to 3, "text" to 1))
        expect("svg gradient count", groups.sumOf { it.gradients.size }, 9)
        expect("svg clip count", groups.sumOf { it.clips.size }, 2)
        expect("svg group names unique", groups.map { it.name }.distinct().size, groups.size)

        var bad = 0
        fun problem(msg: String) { bad++; println("  FAIL $msg") }
        val refId = { v: String -> v.removePrefix("url(#").removeSuffix(")") }

        val referenced = mutableSetOf<String>()
        for (g in groups) {
            if (g.viewBox.split(" ").mapNotNull { it.toFloatOrNull() }.size != 4) problem("viewBox ${g.name} = '${g.viewBox}'")
            if (g.gradients.any { it.stops.size < 2 }) problem("gradient with <2 stops in ${g.name}")
            if (g.gradients.map { it.id to it.variant }.distinct().size != g.gradients.size) problem("duplicate gradient id in ${g.name}")
            if (g.clips.map { it.id }.distinct().size != g.clips.size) problem("duplicate clip id in ${g.name}")

            val all = g.elements + g.clips.flatMap { it.elements }
            for (e in all) {
                when (e.kind) {
                    "path" -> {
                        val d = e.d ?: ""
                        if (d.isBlank()) problem("empty d in ${g.name}")
                        else runCatching { PathData.parse(d) }.onFailure { problem("${g.name}: ${it.message}") }
                    }
                    "circle" -> if (e.r == null) problem("circle without r in ${g.name}")
                    "ellipse" -> if (e.rx == null || e.ry == null) problem("ellipse without rx/ry in ${g.name}")
                    "rect" -> if (e.width == null || e.height == null) problem("rect without width/height in ${g.name}")
                    "text" -> if (e.text == null) problem("text without content in ${g.name}")
                    else -> problem("unknown kind '${e.kind}' in ${g.name}")
                }
                for (paint in listOfNotNull(e.fill, e.stroke)) {
                    if (paint.startsWith("url(#")) {
                        // A variant-specific gradient (the mascot face) has no variant-less match on
                        // purpose, so resolve by id and let the renderer pick the variant.
                        val id = refId(paint)
                        if (g.gradients.none { it.id == id } && g.clips.none { it.id == id }) {
                            problem("${g.name}: paint '$paint' resolves to nothing")
                        }
                        referenced += id
                    }
                    if (paint.startsWith("token:")) {
                        val token = paint.removePrefix("token:")
                        if (g.resolveToken(token, "yellow") == null || g.resolveToken(token, "mono") == null) {
                            problem("${g.name}: token '$token' has no yellow/mono value")
                        }
                    }
                }
                val clip = e.clipPath
                if (clip != null && g.clip(refId(clip)) == null) problem("${g.name}: clip '$clip' resolves to nothing")
                // the generated file must never carry an unresolved JSX expression
                for (v in listOfNotNull(e.d, e.fill, e.stroke, e.transform, e.clipPath, e.text)) {
                    if (v.contains("\${")) problem("${g.name}: unresolved expression in '$v'")
                }
            }
        }

        // …and the other way round: a gradient nothing references means a reference got mangled.
        for (g in groups) {
            for (id in g.gradients.map { it.id }.distinct()) {
                if (id !in referenced) problem("${g.name}: gradient '$id' is defined but never referenced")
            }
        }

        var mismatch = 0
        for (row in sampled) {
            val r = obj(row)
            val gName = str(r.field("group")) ?: ""
            val idx = (long(r.field("index")) ?: -1L).toInt()
            val got = groups.find { it.name == gName }?.elements?.getOrNull(idx)?.d
            if (got != str(r.field("d"))) { mismatch++; println("  FAIL sampled path $gName#$idx kotlin=$got reference=${str(r.field("d"))}") }
        }
        expect("every browser-sampled path is in the catalogue", mismatch, 0)
        expect("sampled path count matches catalogue", sampled.size, pathCount)
        failures += bad
        if (bad == 0 && mismatch == 0) {
            println("PASS svg catalogue   ${groups.size} groups / $elementCount elements (digest ${SvgPaths.DIGEST}); every d parses, every paint and clip resolves")
        } else {
            println("FAIL svg catalogue   $bad problems, $mismatch path mismatches")
        }
    }

    println("\n$checks checks, $failures failures")
    if (failures > 0) kotlin.system.exitProcess(1)
}

var idCounter = 1

fun print(tiles: List<Tile>): List<String> = tiles.map { "${it.r},${it.c},${it.v}" }.sorted()

fun deserialize(s: String): Tile {
    val parts = s.split(",")
    return Tile(0, parts[2].toInt(), parts[0].toInt(), parts[1].toInt())
}

/**
 * The raw plugin documents the vectors were built from, in the same order. Keeping them here
 * (rather than re-deriving) means the Kotlin side parses exactly the strings the reference parsed.
 */
val PLUGIN_RAW: Map<String, String> = mapOf(
    "minimal" to """{"id":"example.arcade","name":"Arcade Shelf"}""",
    "version+author" to """{"id":"example.arcade","name":"Arcade Shelf","version":"2.0.1","author":"Someone"}""",
    "nodes" to """{"id":"example.arcade","name":"Arcade Shelf","nodes":[{"id":"n1","label":"arc","x":9,"y":46,"d":96,"ly":16,"icon":"games","page":"games"}]}""",
    "node defaults" to """{"id":"example.arcade","name":"Arcade Shelf","nodes":[{"id":"n2","label":"x"}]}""",
    "theme full" to """{"id":"example.arcade","name":"Arcade Shelf","theme":{"accent":"#FFC94D","orb":["#FFE59B","#C08A32","#573614","#1A1308"],"orbScale":1.4,"hubScale":0.7,"chainScale":2,"labelScale":1.7,"glow":0.3}}""",
    "page blocks" to """{"id":"example.arcade","name":"Arcade Shelf","pages":[{"id":"p1","label":"arcade","blocks":[{"type":"text","value":"hi"},{"type":"header","value":"h"},{"type":"note","value":"n"},{"type":"link","label":"g","url":"https://g.co"},{"type":"button","label":"b","page":"games"},{"type":"tiles","items":[{"label":"t","icon":"star","page":"games"}]}]}]}""",
    "no id" to """{"name":"x"}""",
    "bad id" to """{"id":"A_B","name":"x"}""",
    "short id" to """{"id":"a","name":"x"}""",
    "no name" to """{"id":"ok.id"}""",
    "name too long" to """{"id":"ok.id","name":"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"}""",
    "version not semver" to """{"id":"example.arcade","name":"Arcade Shelf","version":"2.0"}""",
    "empty" to """{}""",
    "nodes not array" to """{"id":"example.arcade","name":"Arcade Shelf","nodes":{}}""",
    "node no id" to """{"id":"example.arcade","name":"Arcade Shelf","nodes":[{"label":"x"}]}""",
    "node bad x" to """{"id":"example.arcade","name":"Arcade Shelf","nodes":[{"id":"n","label":"x","x":"abc"}]}""",
    "node x range" to """{"id":"example.arcade","name":"Arcade Shelf","nodes":[{"id":"n","label":"x","x":111}]}""",
    "node d range" to """{"id":"example.arcade","name":"Arcade Shelf","nodes":[{"id":"n","label":"x","d":20}]}""",
    "node label long" to """{"id":"example.arcade","name":"Arcade Shelf","nodes":[{"id":"n","label":"xxxxxxxxxxxxxxxxxxxxxxxxx"}]}""",
    "node dup id" to """{"id":"example.arcade","name":"Arcade Shelf","nodes":[{"id":"n","label":"a"},{"id":"n","label":"b"}]}""",
    "node page clash" to """{"id":"example.arcade","name":"Arcade Shelf","nodes":[{"id":"n","label":"a","page":"music"}]}""",
    "too many nodes" to """{"id":"example.arcade","name":"Arcade Shelf","nodes":[{"id":"n0","label":"n"},{"id":"n1","label":"n"},{"id":"n2","label":"n"},{"id":"n3","label":"n"},{"id":"n4","label":"n"},{"id":"n5","label":"n"},{"id":"n6","label":"n"},{"id":"n7","label":"n"},{"id":"n8","label":"n"},{"id":"n9","label":"n"},{"id":"n10","label":"n"},{"id":"n11","label":"n"},{"id":"n12","label":"n"}]}""",
    "pages not array" to """{"id":"example.arcade","name":"Arcade Shelf","pages":[]}""",
    "page no id" to """{"id":"example.arcade","name":"Arcade Shelf","pages":[{"label":"x"}]}""",
    "page no label" to """{"id":"example.arcade","name":"Arcade Shelf","pages":[{"id":"p"}]}""",
    "page title long" to """{"id":"example.arcade","name":"Arcade Shelf","pages":[{"id":"p","label":"x","title":"ttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttt"}]}""",
    "page too many blocks" to """{"id":"example.arcade","name":"Arcade Shelf","pages":[{"id":"p","label":"x","blocks":[{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"},{"type":"text","value":"t"}]}]}""",
    "block unknown" to """{"id":"example.arcade","name":"Arcade Shelf","pages":[{"id":"p","label":"x","blocks":[{"type":"video","value":"v"}]}]}""",
    "block text long" to """{"id":"example.arcade","name":"Arcade Shelf","pages":[{"id":"p","label":"x","blocks":[{"type":"text","value":"REPLACED"}]}]}""",
    "link not http" to """{"id":"example.arcade","name":"Arcade Shelf","pages":[{"id":"p","label":"x","blocks":[{"type":"link","label":"l","url":"ftp://x"}]}]}""",
    "button both" to """{"id":"example.arcade","name":"Arcade Shelf","pages":[{"id":"p","label":"x","blocks":[{"type":"button","label":"b","page":"games","url":"https://x.co"}]}]}""",
    "tiles empty" to """{"id":"example.arcade","name":"Arcade Shelf","pages":[{"id":"p","label":"x","blocks":[{"type":"tiles","items":[]}]}]}""",
    "theme bad accent" to """{"id":"example.arcade","name":"Arcade Shelf","theme":{"accent":"red"}}""",
    "theme orb 3" to """{"id":"example.arcade","name":"Arcade Shelf","theme":{"orb":["#fff","#000","#111"]}}""",
    "theme glow range" to """{"id":"example.arcade","name":"Arcade Shelf","theme":{"glow":2}}""",
    "empty theme" to """{"id":"example.arcade","name":"Arcade Shelf","theme":{}}""",
    "not json" to "{oops",
    "json array" to "[]",
    "json number" to "42",
).mapValues { (k, v) ->
    // the two length-bomb documents are generated so the file stays readable
    when (k) {
        "block text long" -> v.replace("REPLACED", "t".repeat(601))
        else -> v
    }
}
