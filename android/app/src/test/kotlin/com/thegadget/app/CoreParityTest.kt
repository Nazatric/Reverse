package com.thegadget.app

import com.thegadget.app.core.Direction
import com.thegadget.app.core.GadgetText
import com.thegadget.app.core.Game2048
import com.thegadget.app.core.GadgetMetrics
import com.thegadget.app.core.Ids
import com.thegadget.app.core.key
import com.thegadget.app.core.Library
import com.thegadget.app.core.NavStack
import com.thegadget.app.core.NavState
import com.thegadget.app.core.Origin
import com.thegadget.app.core.PluginError
import com.thegadget.app.core.PluginSchema
import com.thegadget.app.core.RandomSource
import com.thegadget.app.core.Route
import com.thegadget.app.core.Tile
import com.thegadget.app.core.Track
import com.thegadget.app.ui.SvgPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Parity tests for the ported core. Every expected value below is the behaviour of the web
 * implementation (`src/utils` (all of it), `src/state/nav.tsx`, `src/pages/Games/2048.tsx`,
 * `src/utils/pluginSchema.ts`), so a regression here means the native app has drifted from the
 * source of truth rather than that a test was updated to match the code.
 */
class CoreParityTest {

    /* -------------------------------------------------- utils/musicLibrary, utils/link */

    @Test
    fun `jsRound rounds ties towards positive infinity like Math_round`() {
        assertEquals(1.0, GadgetText.jsRound(0.5), 0.0)
        assertEquals(2.0, GadgetText.jsRound(1.5), 0.0)
        assertEquals(-0.0, GadgetText.jsRound(-0.5), 0.0)
    }

    @Test
    fun `formatTime is m_ss and floors both parts`() {
        assertEquals("0:00", GadgetText.formatTime(0.0))
        assertEquals("0:05", GadgetText.formatTime(5.9))
        assertEquals("0:59", GadgetText.formatTime(59.999))
        assertEquals("1:00", GadgetText.formatTime(60.0))
        assertEquals("4:20", GadgetText.formatTime(260.7))
        assertEquals("0:00", GadgetText.formatTime(-3.0))
        assertEquals("0:00", GadgetText.formatTime(Double.NaN))
    }

    @Test
    fun `formatClock matches the 12h and 24h status pill`() {
        assertEquals("4:20PM", GadgetText.formatClock(16, 20, hour24 = false))
        assertEquals("12:05AM", GadgetText.formatClock(0, 5, hour24 = false))
        assertEquals("12:05PM", GadgetText.formatClock(12, 5, hour24 = false))
        assertEquals("9:07AM", GadgetText.formatClock(9, 7, hour24 = false))
        assertEquals("16:20", GadgetText.formatClock(16, 20, hour24 = true))
        assertEquals("00:05", GadgetText.formatClock(0, 5, hour24 = true))
    }

    @Test
    fun `duration and fmtDuration use the web thresholds`() {
        assertEquals("45s", GadgetText.duration(45))
        assertEquals("3m", GadgetText.duration(180))
        assertEquals("1h 2m", GadgetText.duration(3720))
        assertEquals("12s", GadgetText.fmtDuration(12.7))
        assertEquals("4m", GadgetText.fmtDuration(299.9))
        assertEquals("1h", GadgetText.fmtDuration(3600.0))
        assertEquals("1h 30m", GadgetText.fmtDuration(5400.0))
    }

    @Test
    fun `ago degrades from just now to an absolute date`() {
        val now = 1_790_000_400_000L
        assertEquals("never", GadgetText.ago(null, now))
        assertEquals("never", GadgetText.ago(0L, now))
        assertEquals("just now", GadgetText.ago(now - 30_000, now))
        assertEquals("5m ago", GadgetText.ago(now - 5 * 60_000, now))
        assertEquals("3h ago", GadgetText.ago(now - 3 * 3_600_000, now))
        assertEquals("2d ago", GadgetText.ago(now - 2 * 86_400_000L, now))
    }

    @Test
    fun `titleFromFile strips one extension and turns underscores into spaces`() {
        assertEquals("never gonna give you up", GadgetText.titleFromFile("never_gonna_give_you_up.mp3"))
        assertEquals("a b", GadgetText.titleFromFile("a__b.flac"))
        // the web replaces runs of underscores whether or not there was an extension,
        // and it never touches interior dots
        assertEquals("no extension", GadgetText.titleFromFile("no_extension"))
        assertEquals("dots.kept", GadgetText.titleFromFile("dots.kept.m4a"))
    }

    /* ------------------------------------------------------------------- library model */

    private fun track(id: String, title: String, folder: String) =
        Track(id = id, title = title, folder = folder, cover = null, uri = "content://$id")

    @Test
    fun `sortTracks orders by folder then numeric title`() {
        val list = mutableListOf(
            track("b/2", "Track 10", "b"),
            track("a/1", "Track 2", "a"),
            track("b/1", "Track 9", "b"),
        )
        Library.sortTracks(list)
        assertEquals(listOf("a/1", "b/1", "b/2"), list.map { it.id })
    }

    @Test
    fun `artRank prefers cover then front then album`() {
        // /^(cover|front)/ -> 0, /^(folder|album)/ -> 1, anything else -> 2:
        // cover and front share a tier, so they must rank equal, not strictly ordered
        assertEquals(Library.artRank("cover.jpg"), Library.artRank("front.png"))
        assertEquals(Library.artRank("folder.jpg"), Library.artRank("album.jpg"))
        assertTrue(Library.artRank("cover.jpg") < Library.artRank("album.jpg"))
        assertTrue(Library.artRank("album.jpg") < Library.artRank("random_art.png"))
        assertEquals(0, Library.artRank("COVER.JPG"))
        assertEquals(2, Library.artRank("random_art.png"))
    }

    @Test
    fun `groupAlbums falls back to the folder leaf and sorts numerically`() {
        val tracks = listOf(
            track("Music/B/1.mp3", "one", "Music/B"),
            track("Music/A10/1.mp3", "two", "Music/A10"),
            track("Music/A9/1.mp3", "three", "Music/A9"),
        )
        val albums = Library.groupAlbums(tracks) { null }
        assertEquals(listOf("A9", "A10", "B"), albums.map { it.name })
        assertEquals("Music/A9", albums.first().id)
        assertEquals(1, albums.first().tracks.size)
    }

    @Test
    fun `letterOf strips diacritics and buckets non-letters under hash`() {
        assertEquals("A", Library.letterOf("Ángel"))
        assertEquals("Z", Library.letterOf("  zoo"))
        assertEquals("#", Library.letterOf("2048 theme"))
    }

    /* ----------------------------------------------------------------------- 2048 rules */

    private val rng = RandomSource.of(kotlin.random.Random(42))

    @Test
    fun `a fresh board has two tiles`() {
        var id = 0
        val tiles = Game2048.fresh({ id++ }, rng)
        assertEquals(2, tiles.size)
        assertTrue(tiles.all { it.v == 2 || it.v == 4 })
    }

    @Test
    fun `equal neighbours merge once per move and score the merge`() {
        var id = 100
        val row = listOf(
            Tile(id++, 2, 0, 0),
            Tile(id++, 2, 0, 1),
            Tile(id++, 4, 0, 2),
            Tile(id++, 4, 0, 3),
        )
        val res = Game2048.move(row, Direction.LEFT) { id++ }
        assertTrue(res.moved)
        assertEquals(12, res.gained)
        val merged = res.tiles.filter { it.r == 0 }.sortedBy { it.c }
        assertEquals(listOf(4, 8), merged.map { it.v })
    }

    @Test
    fun `each direction compacts toward its own edge`() {
        // Regression: right/down must count slots back from the far edge, not from zero.
        var id = 700
        val single = { Tile(id++, 2, 0, 0) }

        val left = Game2048.move(listOf(Tile(id, 2, 0, 3)), Direction.LEFT) { id++ }
        assertEquals(0, left.tiles.single().c)

        val right = Game2048.move(listOf(single()), Direction.RIGHT) { id++ }
        assertEquals("right must push to column 3", 3, right.tiles.single().c)
        assertTrue(right.moved)

        val up = Game2048.move(listOf(Tile(id, 2, 3, 0)), Direction.UP) { id++ }
        assertEquals(0, up.tiles.single().r)

        val down = Game2048.move(listOf(single()), Direction.DOWN) { id++ }
        assertEquals("down must push to row 3", 3, down.tiles.single().r)
        assertTrue(down.moved)

        // a right-aligned pair merges at the right edge and scores once
        var m = 800
        val pair = listOf(Tile(m++, 2, 0, 2), Tile(m++, 2, 0, 3))
        val res = Game2048.move(pair, Direction.RIGHT) { m++ }
        assertEquals(3, res.tiles.single().c)
        assertEquals(4, res.tiles.single().v)
        assertEquals(4, res.gained)
    }

    @Test
    fun `a move that changes nothing reports moved false`() {
        var id = 200
        val stuck = listOf(Tile(id++, 2, 0, 0), Tile(id++, 4, 0, 1))
        val res = Game2048.move(stuck, Direction.LEFT) { id++ }
        assertFalse(res.moved)
        assertEquals(0, res.gained)
    }

    @Test
    fun `isWon needs a 2048 tile and isStuck needs every direction blocked`() {
        var id = 300
        assertFalse(Game2048.isWon(listOf(Tile(id++, 1024, 0, 0))))
        assertTrue(Game2048.isWon(listOf(Tile(id++, 2048, 0, 0))))

        val free = listOf(Tile(id++, 2, 0, 0))
        assertFalse(Game2048.isStuck(free) { id++ })

        // A packed checkerboard of distinct values cannot move in any direction.
        var n = 400
        val packed = (0 until 4).flatMap { r -> (0 until 4).map { c -> Tile(n++, if ((r + c) % 2 == 0) 2 else 4, r, c) } }
        assertTrue(Game2048.isStuck(packed) { n++ })
    }

    @Test
    fun `spawn fills a free cell and follows the web 90-10 split`() {
        // `free[Math.floor(Math.random() * free.length)]`, `Math.random() < 0.9 ? 2 : 4`
        var id = 500
        val one = listOf(Tile(id++, 2, 0, 0))
        val low = Game2048.spawn(one, { id++ }, scripted(0.0, 0.5))
        assertEquals(2, low.size)
        assertEquals(2, low.last().v)
        assertTrue("spawned onto an occupied cell", low.last().r != 0 || low.last().c != 0)
        assertTrue("a new tile must be flagged pop", low.last().pop)

        val high = Game2048.spawn(one, { id++ }, scripted(0.0, 0.95))
        assertEquals(4, high.last().v)

        // a full board cannot spawn
        var n = 600
        val full = (0 until 16).map { Tile(n++, 2, it / 4, it % 4) }
        assertEquals(16, Game2048.spawn(full, { n++ }, scripted(0.0, 0.0)).size)
    }

    /** A scripted randomness source, so spawn cell and value are deterministic. */
    private fun scripted(vararg values: Double): RandomSource = object : RandomSource {
        private var i = 0
        override fun nextDouble(): Double = values[(i++).coerceAtMost(values.size - 1)]
    }

    /* ------------------------------------------------------------------- nav semantics */

    @Test
    fun `push then back steps exactly one level and home clears the stack`() {
        val origin = Origin(10f, 20f, 30f)
        var s = NavState()
        assertTrue(s.isEmpty)
        s = NavStack.push(s, Route.Music, origin, fromHub = true)
        s = NavStack.push(s, Route.Albums, origin, fromHub = false)
        assertEquals(2, s.stack.size)
        assertEquals(com.thegadget.app.core.NavDirection.FORWARD, s.dir)
        s = NavStack.back(s)
        assertEquals(com.thegadget.app.core.NavDirection.BACK, s.dir)
        assertEquals(listOf<Route>(Route.Music), s.stack)
        s = NavStack.back(s)
        assertTrue(s.isEmpty)
        // back at the hub is a no-op — it must not invent a route
        s = NavStack.back(s)
        assertTrue(s.isEmpty)
        s = NavStack.push(s, Route.Config, origin, fromHub = true)
        s = NavStack.home(s)
        assertTrue(s.isEmpty)
    }

    @Test
    fun `route keys distinguish parameterised routes`() {
        assertEquals("album:a", Route.Album("a").key())
        assertEquals("album:b", Route.Album("b").key())
        assertEquals("music", Route.Music.key())
        assertEquals("game-browser:https://x", Route.GameBrowser("https://x", "x").key())
    }


    /* ------------------------------------------------------------------- plugin schema */

    private val minimalPlugin = """
        {
          "id": "example.arcade",
          "name": "Arcade",
          "pages": [
            { "id": "home", "label": "home", "title": "Arcade", "subtitle": "hi",
              "blocks": [ { "type": "text", "value": "hello" },
                          { "type": "header", "value": "Section" },
                          { "type": "note", "value": "a note" },
                          { "type": "link", "label": "site", "url": "https://example.com" },
                          { "type": "button", "label": "go", "page": "home" },
                          { "type": "tiles", "items": [ { "label": "one", "icon": "star", "page": "home" } ] } ] }
          ]
        }
    """.trimIndent()

    @Test
    fun `a valid manifest parses into blocks the renderer can walk`() {
        val rec = PluginSchema.parsePlugin(minimalPlugin)
        assertEquals("example.arcade", rec.id)
        assertEquals("Arcade", rec.name)
        assertTrue(rec.enabled)
        val page = rec.doc.pages.single()
        assertEquals(6, page.blocks.size)
    }

    @Test
    fun `invalid manifests are rejected with a readable message`() {
        val cases = mapOf(
            "" to "empty",
            "   " to "blank",
            "[]" to "not an object",
            "{\"name\":\"x\"}" to "missing id",
            "{\"id\":\"BAD ID!\",\"name\":\"x\"}" to "bad id charset",
            "{\"id\":\"ok.id\",\"name\":\"\"}" to "blank name",
        )
        for ((json, why) in cases) {
            try {
                PluginSchema.parsePlugin(json)
                fail("expected rejection for $why")
            } catch (e: PluginError) {
                assertTrue("$why produced no message", e.message!!.isNotBlank())
            }
        }
    }

    @Test
    fun `oversized sources are refused at the documented 200 KB cap`() {
        val big = "{\"id\":\"ok.id\",\"name\":\"" + "a".repeat(PluginSchema.MAX_SOURCE) + "\"}"
        try {
            PluginSchema.parsePlugin(big)
            fail("expected rejection for oversized source")
        } catch (e: PluginError) {
            assertTrue(e.message!!.contains("too large"))
        }
    }

    @Test
    fun `glyph and page names are validated against the published lists`() {
        assertTrue(PluginSchema.isGlyphName("music"))
        assertTrue(PluginSchema.isGlyphName("star"))
        assertFalse(PluginSchema.isGlyphName("not-a-glyph"))
        assertTrue(PluginSchema.PAGES.contains("2048"))
        assertFalse(PluginSchema.PAGES.contains("nope"))
    }

    /* ------------------------------------------------------------------ stage metrics */

    @Test
    fun `the reference stage is 736 and u scales with the viewport`() {
        val m = GadgetMetrics.compute(390f, 844f)
        assertTrue("portrait expected", m.portrait)
        assertEquals(390f, m.sw, 0.001f)
        assertEquals(390f / 736f * 1.2f, m.u, 0.0001f) // minDim < 520 -> 1.2 boost
        assertEquals(0f, m.fx, 0.001f)
    }

    @Test
    fun `landscape clamps the stage to a square and centres it`() {
        val m = GadgetMetrics.compute(844f, 390f)
        assertFalse(m.portrait)
        assertEquals(390f, m.sw, 0.001f)
        assertEquals(390f, m.sh, 0.001f)
        assertEquals((844f - 390f) / 2f, m.fx, 0.001f)
    }

    @Test
    fun `stage coordinates are a percentage of the stage, offset by the frame origin`() {
        val m = GadgetMetrics.compute(390f, 844f)
        // The web composes inside `.frame { left: var(--fx); top: var(--fy); w/h }`, so a node's
        // centre is `fx + x% * sw` / `fy + y% * sh` — NOT a bare percentage of the window.
        assertEquals(m.fx + m.sw * 0.495f, m.stageX(49.5f), 0.001f)
        assertEquals(m.fy + m.sh * 0.52f, m.stageY(52f), 0.001f)
        // fx/fy are the centring offsets of the stage within the window.
        assertEquals((m.w - m.sw) / 2f, m.fx, 0.001f)
        assertEquals((m.h - m.sh) / 2f, m.fy, 0.001f)
    }

    /* ------------------------------------------------------------------ ids and svg */

    @Test
    fun `codes are normalised and validated like the web`() {
        assertEquals("hp7k2m9a", Ids.normalizeCode("  HP7K2M9A "))
        assertTrue(Ids.isValidCode("hp7k2m9a"))
        assertFalse(Ids.isValidCode("ab"))
        assertFalse(Ids.isValidCode("has space"))
        assertEquals("lnk-hp7k2m9a", Ids.linkedHomieId("hp7k2m9a"))
        assertTrue(Ids.hostCode().startsWith("hp"))
        assertEquals(8, Ids.hostCode().length)
    }

    @Test
    fun `the extracted svg catalogue keeps its digest and the shell groups resolve`() {
        assertEquals("9693031e14e4ab3f", SvgPaths.DIGEST)
        for (name in listOf("wire.Wireframe", "top.TopBar", "mascot.MascotFace", "chain.tile", "glyph.MusicGlyph")) {
            val g = SvgPaths.group(name)
            assertTrue("$name missing from the catalogue", g != null)
            assertEquals(4, g!!.viewBox.split(" ").size)
            assertTrue("$name has no geometry", g.elements.isNotEmpty())
        }
        assertNull(SvgPaths.group("does.NotExist"))
    }

    // ---------------------------------------------------------------------
    // The SFXR port behind the UI sounds (src/utils/audio.ts uses jsfxr)
    // ---------------------------------------------------------------------

    @Test
    fun `the ui sound map matches the web presets and volumes`() {
        val expected = mapOf(
            "hover" to ("blipSelect" to 0.10),
            "tap" to ("click" to 0.40),
            "open" to ("powerUp" to 0.20),
            "close" to ("jump" to 0.18),
            "confirm" to ("pickupCoin" to 0.30),
            "shake" to ("hitHurt" to 0.22),
        )
        assertEquals(expected.keys.toList(), com.thegadget.app.core.Sfxr.MAP.keys.toList())
        for ((key, spec) in expected) {
            assertEquals("$key preset", spec.first, com.thegadget.app.core.Sfxr.MAP[key]!!.first)
            assertEquals("$key volume", spec.second, com.thegadget.app.core.Sfxr.MAP[key]!!.second, 1e-9)
        }
    }

    @Test
    fun `the haptic patterns match navigator vibrate calls`() {
        val h = com.thegadget.app.core.Sfxr.HAPTICS
        assertEquals(4, h.size)
        assertTrue(h["tap"]!!.contentEquals(longArrayOf(8)))
        assertTrue(h["open"]!!.contentEquals(longArrayOf(10)))
        assertTrue(h["confirm"]!!.contentEquals(longArrayOf(14)))
        assertTrue(h["shake"]!!.contentEquals(longArrayOf(20, 40, 20)))
        // hover and close are sound-only in the web app
        assertNull(h["hover"])
        assertNull(h["close"])
    }

    @Test
    fun `every ui sound synthesises finite bounded samples and is reproducible`() {
        for ((key, spec) in com.thegadget.app.core.Sfxr.MAP) {
            val a = com.thegadget.app.core.Sfxr.render(
                com.thegadget.app.core.Sfxr.preset(spec.first, kotlin.random.Random(0x6AD6E7L)),
                kotlin.random.Random(0x6AD6E7L),
            )
            val b = com.thegadget.app.core.Sfxr.render(
                com.thegadget.app.core.Sfxr.preset(spec.first, kotlin.random.Random(0x6AD6E7L)),
                kotlin.random.Random(0x6AD6E7L),
            )
            assertTrue("$key rendered nothing", a.isNotEmpty())
            assertTrue("$key is not reproducible", a.contentEquals(b))
            var peak = 0f
            for (s in a) {
                assertTrue("$key produced NaN", !s.isNaN())
                if (kotlin.math.abs(s) > peak) peak = kotlin.math.abs(s)
            }
            assertTrue("$key is silent", peak > 0f)
            assertTrue("$key clips past the sfxr gain envelope ($peak)", peak < 8f)
        }
    }

    @Test
    fun `preset parameters follow the sfxr definitions`() {
        val r = kotlin.random.Random(1)
        val coin = com.thegadget.app.core.Sfxr.pickupCoin(r)
        assertEquals(com.thegadget.app.core.Sfxr.SAWTOOTH, coin.waveType)
        assertTrue("base freq range", coin.baseFreq >= 0.4 && coin.baseFreq <= 0.9)
        assertTrue("punch range", coin.envPunch >= 0.3 && coin.envPunch <= 0.6)

        val jump = com.thegadget.app.core.Sfxr.jump(kotlin.random.Random(1))
        assertEquals(com.thegadget.app.core.Sfxr.SQUARE, jump.waveType)
        assertTrue("jump slides up", jump.freqRamp >= 0.1 && jump.freqRamp <= 0.3)

        val hurt = com.thegadget.app.core.Sfxr.hitHurt(kotlin.random.Random(1))
        assertFalse("hitHurt is never a sine", hurt.waveType == com.thegadget.app.core.Sfxr.SINE)
        assertTrue("hitHurt slides down", hurt.freqRamp < 0)

        val blip = com.thegadget.app.core.Sfxr.blipSelect(kotlin.random.Random(1))
        assertEquals("blipSelect high-pass", 0.1, blip.hpfFreq, 1e-9)
    }
}
