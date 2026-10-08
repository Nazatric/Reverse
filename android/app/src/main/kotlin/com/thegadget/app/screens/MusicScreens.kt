package com.thegadget.app.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.thegadget.app.core.GadgetText
import com.thegadget.app.core.Library
import com.thegadget.app.core.Route
import com.thegadget.app.core.Track
import com.thegadget.app.service.QueueItem
import com.thegadget.app.data.LibraryStatus
import com.thegadget.app.shell.Empty
import com.thegadget.app.shell.GlassButton
import com.thegadget.app.shell.Orb
import com.thegadget.app.state.AppState
import com.thegadget.app.ui.theme.GadgetFonts
import com.thegadget.app.ui.theme.GadgetTokens
import java.io.File
import kotlinx.coroutines.flow.map

/* ------------------------------------------------------------ shared music bits */

@Composable
fun Cover(src: String?, size: Int, tokens: GadgetTokens, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size.dp).clip(RoundedCornerShape(8.dp))
            .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFF2A2B29), Color(0xFF0E0F0E)))),
        contentAlignment = Alignment.Center,
    ) {
        if (src != null && File(src).exists()) {
            AsyncImage(
                model = File(src),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Text("♪", color = tokens.colors.faint, style = TextStyle(fontSize = (size * 0.4f).sp))
        }
    }
}

@Composable
fun TrackRow(track: Track, tokens: GadgetTokens, playing: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(track.cover, 44, tokens)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = if (playing) tokens.colors.accent else tokens.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = GadgetFonts.body, fontWeight = FontWeight.W600, fontSize = 14.sp))
            Text(track.folder.substringAfterLast('/'), color = tokens.colors.faint, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = GadgetFonts.body, fontSize = 12.sp))
        }
    }
}

@Composable
fun PlayerControls(app: AppState, tokens: GadgetTokens) {
    val snap by app.player.state.collectAsState()
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
        Orb(size = 48f, tokens = tokens, onClick = { app.player.prev() }) { Text("⏮", color = tokens.colors.text, style = TextStyle(fontSize = 16.sp)) }
        Orb(size = 64f, tokens = tokens, on = snap.playing, onClick = { app.player.toggle() }) { Text(if (snap.playing) "⏸" else "▶", color = tokens.colors.text, style = TextStyle(fontSize = 20.sp)) }
        Orb(size = 48f, tokens = tokens, onClick = { app.player.next() }) { Text("⏭", color = tokens.colors.text, style = TextStyle(fontSize = 16.sp)) }
        Orb(size = 48f, tokens = tokens, on = snap.shuffle, onClick = { app.player.cycleShuffle() }) { Text("🔀", color = tokens.colors.text, style = TextStyle(fontSize = 16.sp)) }
        Orb(size = 48f, tokens = tokens, on = snap.repeat != "off", onClick = { app.player.cycleRepeat() }) { Text(if (snap.repeat == "one") "🔂" else "🔁", color = tokens.colors.text, style = TextStyle(fontSize = 16.sp)) }
    }
}

/* --------------------------------------------------------------------- screens */

@Composable
fun MusicHomeScreen(app: AppState, tokens: GadgetTokens) {
    val lib by app.library.state.collectAsState()
    val snap by app.player.state.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) app.adoptFolder(uri)
    }
    Page(app, tokens, "music", subtitle = when (lib.status) {
        LibraryStatus.EMPTY -> "no folder chosen"
        LibraryStatus.LOADING -> "scanning…"
        LibraryStatus.NEEDS_PERMISSION -> "permission needed"
        LibraryStatus.READY -> "${lib.tracks.size} tracks"
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("choose folder", tokens) { picker.launch(null) }
            if (lib.tracks.isNotEmpty()) {
                GlassButton("play all", tokens) { app.player.playQueue(lib.tracks, 0) }
                GlassButton("shuffle", tokens, variant = "ghost") { app.player.setShuffle(true); app.player.playQueue(lib.tracks, 0) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("albums", tokens, variant = "ghost") { app.push(Route.Albums) }
                GlassButton("songs", tokens, variant = "ghost") { app.push(Route.Songs) }
                GlassButton("playlists", tokens, variant = "ghost") { app.push(Route.Playlists) }
                GlassButton("search", tokens, variant = "ghost") { app.push(Route.Search) }
            }
            snap.current?.let { t ->
                Spacer(Modifier.height(8.dp))
                Text("now playing", color = tokens.colors.dim, style = TextStyle(fontFamily = GadgetFonts.display, fontSize = 11.sp, letterSpacing = 2.sp))
                TrackRow(t.toTrack(), tokens, playing = snap.playing) { app.push(Route.Now) }
                PlayerControls(app, tokens)
            }
        }
    }
}

@Composable
fun AlbumsScreen(app: AppState, tokens: GadgetTokens) {
    val lib by app.library.state.collectAsState()
    val albums = remember(lib.tracks) { Library.groupAlbums(lib.tracks, { null }) }
    Page(app, tokens, "albums", subtitle = "${albums.size} albums") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            albums.forEach { album ->
                Row(Modifier.fillMaxWidth().clickable { app.push(Route.Album(album.id)) }, verticalAlignment = Alignment.CenterVertically) {
                    Cover(album.cover, 56, tokens)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(album.name, color = tokens.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = GadgetFonts.body, fontWeight = FontWeight.W700, fontSize = 15.sp))
                        Text("${album.tracks.size} tracks", color = tokens.colors.faint, style = TextStyle(fontFamily = GadgetFonts.body, fontSize = 12.sp))
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumScreen(app: AppState, id: String, tokens: GadgetTokens) {
    val lib by app.library.state.collectAsState()
    val album = remember(lib.tracks) { Library.groupAlbums(lib.tracks, { null }).firstOrNull { it.id == id } }
    Page(app, tokens, album?.name ?: "album", subtitle = album?.let { "${it.tracks.size} tracks" }) {
        album?.let { a ->
            GlassButton("play album", tokens) { app.player.playQueue(a.tracks, 0) }
            Spacer(Modifier.height(12.dp))
            a.tracks.forEachIndexed { i, t -> TrackRow(t, tokens, playing = false) { app.player.playQueue(a.tracks, i) } }
        }
    }
}

@Composable
fun SongsScreen(app: AppState, tokens: GadgetTokens) {
    val lib by app.library.state.collectAsState()
    Page(app, tokens, "songs", subtitle = "${lib.tracks.size} tracks") {
        Column {
            lib.tracks.forEachIndexed { i, t -> TrackRow(t, tokens, playing = false) { app.player.playQueue(lib.tracks, i) } }
        }
    }
}

@Composable
fun PlaylistsScreen(app: AppState, tokens: GadgetTokens) {
    val playlists by app.playlists.collectAsState()
    Page(app, tokens, "playlists", subtitle = "${playlists.size} playlists") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("new playlist", tokens) { app.createPlaylist() }
            playlists.forEach { pl ->
                Row(Modifier.fillMaxWidth().clickable { app.push(Route.Playlist(pl.id)) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(pl.name, color = tokens.colors.text, style = TextStyle(fontFamily = GadgetFonts.body, fontWeight = FontWeight.W700, fontSize = 15.sp))
                        Text("${pl.ids.size} songs", color = tokens.colors.faint, style = TextStyle(fontFamily = GadgetFonts.body, fontSize = 12.sp))
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistScreen(app: AppState, id: String, tokens: GadgetTokens) {
    val playlists by app.playlists.collectAsState()
    val lib by app.library.state.collectAsState()
    val pl = playlists.firstOrNull { it.id == id }
    val tracks = remember(pl, lib.tracks) {
        pl?.ids?.mapNotNull { pid -> lib.tracks.firstOrNull { it.id == pid } } ?: emptyList()
    }
    Page(app, tokens, pl?.name ?: "playlist", subtitle = "${tracks.size} songs") {
        pl?.let {
            GlassButton("play", tokens) { app.player.playQueue(tracks, 0) }
            Spacer(Modifier.height(12.dp))
            tracks.forEachIndexed { i, t -> TrackRow(t, tokens, playing = false) { app.player.playQueue(tracks, i) } }
            Spacer(Modifier.height(16.dp))
            GlassButton("delete playlist", tokens, variant = "ghost") { app.deletePlaylist(it.id) }
        }
    }
}

@Composable
fun SearchScreen(app: AppState, tokens: GadgetTokens) {
    val lib by app.library.state.collectAsState()
    var q by remember { mutableStateOf("") }
    val results = remember(q, lib.tracks) {
        if (q.isBlank()) emptyList() else lib.tracks.filter { it.title.contains(q, true) || it.folder.contains(q, true) }
    }
    Page(app, tokens, "search") {
        com.thegadget.app.shell.Field(q, { q = it }, tokens, label = "search")
        Spacer(Modifier.height(12.dp))
        Column {
            results.forEachIndexed { i, t -> TrackRow(t, tokens, playing = false) { app.player.playQueue(results, i) } }
        }
    }
}

@Composable
fun NowPlayingScreen(app: AppState, tokens: GadgetTokens) {
    val snap by app.player.state.collectAsState()
    val t = snap.current
    Page(app, tokens, t?.title ?: "now playing", subtitle = t?.folder?.substringAfterLast('/')) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Cover(t?.cover, 260, tokens, Modifier.aspectRatio(1f))
            Spacer(Modifier.height(16.dp))
            Text(
                "${GadgetText.formatTime(snap.positionMs / 1000.0)} / ${GadgetText.formatTime(snap.durationMs / 1000.0)}",
                color = tokens.colors.dim,
                style = TextStyle(fontFamily = GadgetFonts.display, fontSize = 13.sp),
            )
            androidx.compose.material3.Slider(
                value = snap.positionMs.toFloat().coerceIn(0f, snap.durationMs.toFloat().coerceAtLeast(1f)),
                onValueChange = { app.player.seek(it.toLong()) },
                valueRange = 0f..snap.durationMs.toFloat().coerceAtLeast(1f),
            )
            PlayerControls(app, tokens)
        }
    }
}

private fun QueueItem.toTrack(): Track = Track(id = id, title = title, folder = folder, cover = cover, uri = uri)
