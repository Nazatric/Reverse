package com.thegadget.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thegadget.app.core.GadgetText
import com.thegadget.app.core.Route
import com.thegadget.app.shell.Field
import com.thegadget.app.shell.GlassButton
import com.thegadget.app.social.Social
import com.thegadget.app.state.AppState
import com.thegadget.app.ui.theme.GadgetFonts
import com.thegadget.app.ui.theme.GadgetTokens
import androidx.compose.foundation.clickable

@Composable
fun HomiesScreen(app: AppState, tokens: GadgetTokens) {
    val homies by app.homies.collectAsState()
    val presence by Social.presence.collectAsState()
    var code by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    LaunchedEffect(homies) {
        Social.connect(app.myCode(), homies.mapNotNull { it.code })
    }
    Page(app, tokens, "homies", subtitle = "${homies.size} homies · ${if (Social.connected.value) "online" else "offline"}") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("your code: ${app.myCode()}", color = tokens.colors.accent, style = TextStyle(fontFamily = GadgetFonts.display, fontSize = 13.sp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(code, { code = it }, tokens, label = "link a code", modifier = Modifier.weight(1f))
                Spacer(Modifier.height(0.dp))
                GlassButton("link", tokens) { err = app.addLinkedHomie(code) ?: ""; code = "" }
            }
            if (err.isNotEmpty()) Text(err, color = tokens.colors.text, style = TextStyle(fontFamily = GadgetFonts.body, fontSize = 12.sp))
            GlassButton("add homie", tokens, variant = "ghost") { app.push(Route.HomieEdit(null)) }
            homies.forEach { h ->
                val p = h.code?.let { presence[it] }
                Row(Modifier.fillMaxWidth().clickable { app.push(Route.Homie(h.id)) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p?.name ?: h.name, color = tokens.colors.text, style = TextStyle(fontFamily = GadgetFonts.body, fontWeight = FontWeight.W700, fontSize = 15.sp))
                        Text(p?.status ?: h.status, color = tokens.colors.faint, style = TextStyle(fontFamily = GadgetFonts.body, fontSize = 12.sp))
                    }
                    if (h.kind == "linked") GlassButton("chat", tokens, variant = "ghost") { app.push(Route.Chat(h.code!!)) }
                }
            }
        }
    }
}

@Composable
fun HomieDetailScreen(app: AppState, id: String, tokens: GadgetTokens) {
    val homies by app.homies.collectAsState()
    val h = homies.firstOrNull { it.id == id }
    Page(app, tokens, h?.name ?: "homie") {
        h?.let {
            Text("status: ${it.status}", color = tokens.colors.dim, style = TextStyle(fontFamily = GadgetFonts.body, fontSize = 13.sp))
            if (it.note.isNotEmpty()) Text(it.note, color = tokens.colors.text, style = TextStyle(fontFamily = GadgetFonts.body, fontSize = 13.sp))
            if (it.phone.isNotEmpty()) Text(it.phone, color = tokens.colors.text, style = TextStyle(fontFamily = GadgetFonts.body, fontSize = 13.sp))
            Spacer(Modifier.height(12.dp))
            GlassButton("edit", tokens) { app.push(Route.HomieEdit(it.id)) }
            Spacer(Modifier.height(8.dp))
            GlassButton("delete", tokens, variant = "ghost") { app.deleteHomie(it.id); app.back() }
        }
    }
}

@Composable
fun HomieEditScreen(app: AppState, id: String?, tokens: GadgetTokens) {
    val homies by app.homies.collectAsState()
    val existing = homies.firstOrNull { it.id == id }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    Page(app, tokens, if (id == null) "add homie" else "edit homie") {
        Field(name, { name = it }, tokens, label = "name")
        Spacer(Modifier.height(10.dp))
        Field(note, { note = it }, tokens, label = "note")
        Spacer(Modifier.height(10.dp))
        Field(phone, { phone = it }, tokens, label = "phone")
        Spacer(Modifier.height(16.dp))
        GlassButton("save", tokens) { app.saveHomie(existing?.id, name, note, phone, existing?.status ?: "online"); app.back() }
        Spacer(Modifier.height(8.dp))
        GlassButton("cancel", tokens, variant = "ghost") { app.back() }
    }
}

@Composable
fun ChatScreen(app: AppState, code: String, tokens: GadgetTokens) {
    val chat by Social.chat.collectAsState()
    var draft by remember { mutableStateOf("") }
    val msgs = chat[code] ?: emptyList()
    LaunchedEffect(code) { Social.connect(app.myCode(), listOf(code)) }
    Page(app, tokens, code, subtitle = "chat") {
        Column {
            msgs.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(if (m.from == app.myCode()) "you" else m.name.ifEmpty { code }, color = tokens.colors.faint, style = TextStyle(fontFamily = GadgetFonts.body, fontSize = 11.sp))
                        Text(m.text, color = tokens.colors.text, style = TextStyle(fontFamily = GadgetFonts.body, fontSize = 14.sp))
                    }
                    Text(GadgetText.ago(m.t, com.thegadget.app.core.Clock.now()), color = tokens.colors.faint, style = TextStyle(fontFamily = GadgetFonts.body, fontSize = 11.sp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(draft, { draft = it }, tokens, modifier = Modifier.weight(1f))
                GlassButton("send", tokens) { if (draft.isNotBlank()) { Social.send(code, draft); draft = "" } }
            }
        }
    }
}
