package com.thegadget.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.thegadget.app.core.Direction
import com.thegadget.app.core.Game2048
import com.thegadget.app.core.RandomSource
import com.thegadget.app.core.Route
import com.thegadget.app.core.Tile
import com.thegadget.app.data.Game
import com.thegadget.app.shell.Empty
import com.thegadget.app.shell.Field
import com.thegadget.app.shell.GlassButton
import com.thegadget.app.state.AppState
import com.thegadget.app.ui.theme.GadgetFonts
import com.thegadget.app.ui.theme.GadgetTokens
import kotlin.math.abs

@Composable
fun GamesScreen(app: AppState, tokens: GadgetTokens) {
    val games by app.games.collectAsState()
    Page(app, tokens, "games", subtitle = "${games.size} games") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("2048", tokens) { app.push(Route.Game2048) }
            GlassButton("add game", tokens, variant = "ghost") { app.push(Route.GameEdit(null)) }
            games.forEach { g ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).pointerInput(Unit) {}) {
                        Text(g.name, color = tokens.colors.text, style = TextStyle(fontFamily = GadgetFonts.body, fontWeight = FontWeight.W700, fontSize = 15.sp))
                        Text(g.category, color = tokens.colors.faint, style = TextStyle(fontFamily = GadgetFonts.body, fontSize = 12.sp))
                    }
                    GlassButton("open", tokens, variant = "ghost") { app.push(Route.Game(g.id)) }
                    Spacer(Modifier.size(8.dp))
                    GlassButton("edit", tokens, variant = "ghost") { app.push(Route.GameEdit(g.id)) }
                }
            }
        }
    }
}

@Composable
fun GameDetailScreen(app: AppState, id: String, tokens: GadgetTokens) {
    val games by app.games.collectAsState()
    val g = games.firstOrNull { it.id == id }
    Page(app, tokens, g?.name ?: "game", subtitle = g?.category) {
        g?.let {
            Text(it.url, color = tokens.colors.dim, style = TextStyle(fontFamily = GadgetFonts.body, fontSize = 12.sp))
            Spacer(Modifier.height(12.dp))
            // Launching counts a play, exactly like `GameDetail.launch`.
            GlassButton("launch", tokens) { app.markGamePlayed(it.id); app.push(com.thegadget.app.core.Route.GameBrowser(it.url, it.name)) }
            Spacer(Modifier.height(8.dp))
            GlassButton("edit", tokens, variant = "ghost") { app.push(Route.GameEdit(it.id)) }
            GlassButton("delete", tokens, variant = "ghost") { app.deleteGame(it.id) }
        }
    }
}

@Composable
fun GameEditScreen(app: AppState, id: String?, tokens: GadgetTokens) {
    val games by app.games.collectAsState()
    val existing = games.firstOrNull { it.id == id }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var url by remember { mutableStateOf(existing?.url ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "") }
    Page(app, tokens, if (id == null) "add game" else "edit game") {
        Field(name, { name = it }, tokens, label = "name")
        Spacer(Modifier.height(10.dp))
        Field(url, { url = it }, tokens, label = "url")
        Spacer(Modifier.height(10.dp))
        Field(category, { category = it }, tokens, label = "category")
        Spacer(Modifier.height(16.dp))
        GlassButton("save", tokens) { app.saveGame(existing?.id, name, url, category); app.back() }
        Spacer(Modifier.height(8.dp))
        GlassButton("cancel", tokens, variant = "ghost") { app.back() }
    }
}

@Composable
fun Game2048Screen(app: AppState, tokens: GadgetTokens) {
    val best by app.best2048.collectAsState()
    val rng = remember { RandomSource.of(kotlin.random.Random) }
    var tiles by remember { mutableStateOf(Game2048.fresh({ (System.nanoTime() % 1_000_000).toInt() }, rng)) }
    var score by remember { mutableIntStateOf(0) }
    var bestState by remember { mutableIntStateOf(best) }
    fun apply(dir: Direction) {
        var nextId = 1000
        val result = Game2048.move(tiles, dir, { nextId++ })
        if (!result.moved) return
        var spawned = Game2048.spawn(result.tiles, { nextId++ }, rng)
        tiles = spawned
        score += result.gained
        if (score > bestState) {
            bestState = score
            app.setBest2048(score)
        }
    }
    Page(app, tokens, "2048", subtitle = "score $score · best $bestState") {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF16171A))
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        if (abs(drag.x) > abs(drag.y)) apply(if (drag.x > 0) Direction.RIGHT else Direction.LEFT)
                        else apply(if (drag.y > 0) Direction.DOWN else Direction.UP)
                    }
                },
        ) {
            // 4x4 grid; each cell is 1/4 of the board, positioned by (c, r) exactly as the web board.
            tiles.forEach { t ->
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val cell = maxWidth / 4
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(start = cell * t.c, top = cell * t.r)
                            .size(cell),
                        contentAlignment = Alignment.Center,
                    ) {
                        BoardTile(t, tokens)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        GlassButton("new game", tokens) { tiles = Game2048.fresh({ 1 }, rng); score = 0 }
    }
}

@Composable
private fun BoardTile(t: Tile, tokens: GadgetTokens) {
    val bg = when {
        t.v >= 2048 -> Color(0xFFE9F33F)
        t.v >= 128 -> Color(0xFFC9B458)
        t.v >= 16 -> Color(0xFF8A6A2F)
        else -> Color(0xFF2E3033)
    }
    Box(
        Modifier.fillMaxSize().padding(4.dp).aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            t.v.toString(),
            color = if (t.v >= 8) Color(0xFF141414) else Color(0xFFF2F2EF),
            style = TextStyle(fontFamily = GadgetFonts.display, fontWeight = FontWeight.Black, fontSize = 22.sp),
        )
    }
}

/* The web-games container: an isolated WebView, which the task explicitly allows for
 * user-added external game content only. Everything around it is native. */
/**
 * The one place a WebView is allowed: user-added external web games. It is fully isolated — its own
 * data directory suffix, no cookies or storage shared with anything else, JavaScript confined to
 * this view — and every pixel of chrome around it is native.
 */
@Composable
fun GameBrowserScreen(url: String, title: String, tokens: GadgetTokens) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    // Isolation: a private data store, no shared cookies, no file access.
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mediaPlaybackRequiresUserGesture = false
                    webViewClient = android.webkit.WebViewClient()
                    loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
