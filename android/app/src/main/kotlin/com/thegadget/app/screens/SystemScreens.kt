package com.thegadget.app.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thegadget.app.core.GadgetSettings
import com.thegadget.app.shell.Field
import com.thegadget.app.shell.GlassButton
import com.thegadget.app.state.AppState
import com.thegadget.app.ui.theme.GadgetFonts
import com.thegadget.app.ui.theme.GadgetTokens

@Composable
fun AccountScreen(app: AppState, tokens: GadgetTokens) {
    val profile by app.profile.collectAsState()
    var name by remember { mutableStateOf(profile.name) }
    var tagline by remember { mutableStateOf(profile.tagline) }
    var status by remember { mutableStateOf(profile.status) }
    Page(app, tokens, "account", subtitle = "your profile is broadcast to linked homies") {
        Field(name, { name = it }, tokens, label = "name")
        Spacer(Modifier.height(10.dp))
        Field(tagline, { tagline = it }, tokens, label = "tagline")
        Spacer(Modifier.height(10.dp))
        Field(status, { status = it }, tokens, label = "status")
        Spacer(Modifier.height(16.dp))
        GlassButton("save", tokens) { app.saveProfile(name, tagline, status, profile.mascot) }
    }
}

@Composable
fun ConfigScreen(app: AppState, tokens: GadgetTokens) {
    val settings by app.settings.collectAsState()
    Page(app, tokens, "config", subtitle = "every change persists") {
        Slider2048Row("orb scale", settings.orbScale, 0.5f, 2f, tokens) { app.updateSetting("orbScale", it) }
        Slider2048Row("hub scale", settings.hubScale, 0.5f, 2f, tokens) { app.updateSetting("hubScale", it) }
        Slider2048Row("chain scale", settings.chainScale, 0.5f, 2f, tokens) { app.updateSetting("chainScale", it) }
        Slider2048Row("label scale", settings.labelScale, 0.5f, 2f, tokens) { app.updateSetting("labelScale", it) }
        Slider2048Row("glow", settings.glow, 0f, 2f, tokens) { app.updateSetting("glow", it) }
        ToggleRow("24-hour clock", settings.hour24, tokens) { app.updateSetting("hour24", it) }
        ToggleRow("auto-immersive", settings.autoImmersive, tokens) { app.updateSetting("autoImmersive", it) }
        Spacer(Modifier.height(16.dp))
        GlassButton("plugins", tokens, variant = "ghost") { app.push(com.thegadget.app.core.Route.Plugins) }
        Spacer(Modifier.height(8.dp))
        GlassButton("reset settings", tokens, variant = "ghost") { app.resetSettings() }
    }
}

@Composable
private fun Slider2048Row(label: String, value: Float, min: Float, max: Float, tokens: GadgetTokens, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().height(64.dp)) {
        Text("$label  ${"%.2f".format(value)}", color = tokens.colors.dim, style = TextStyle(fontFamily = GadgetFonts.body, fontSize = 12.sp))
        Slider(value = value, onValueChange = onChange, valueRange = min..max)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, tokens: GadgetTokens, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = tokens.colors.text, style = TextStyle(fontFamily = GadgetFonts.body, fontSize = 14.sp), modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun PluginsScreen(app: AppState, tokens: GadgetTokens) {
    val plugins by app.plugins.collectAsState()
    var msg by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: ""
            msg = app.importPlugin(text) ?: "installed"
        }
    }
    Page(app, tokens, "plugins", subtitle = "${plugins.size} installed") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("install from file", tokens) { picker.launch(arrayOf("application/json")) }
            if (msg.isNotEmpty()) Text(msg, color = tokens.colors.accent, style = TextStyle(fontFamily = GadgetFonts.body, fontSize = 12.sp))
            plugins.forEach { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = tokens.colors.text, style = TextStyle(fontFamily = GadgetFonts.body, fontWeight = FontWeight.W700, fontSize = 15.sp))
                        Text(p.id, color = tokens.colors.faint, style = TextStyle(fontFamily = GadgetFonts.body, fontSize = 11.sp))
                    }
                    Switch(checked = p.enabled, onCheckedChange = { app.togglePlugin(p.id) })
                    GlassButton("del", tokens, variant = "ghost") { app.deletePlugin(p.id) }
                }
            }
        }
    }
}

@Composable
fun PluginPageScreen(app: AppState, id: String, tokens: GadgetTokens) {
    val plugins by app.plugins.collectAsState()
    val p = plugins.firstOrNull { it.id == id }
    Page(app, tokens, p?.name ?: "plugin", subtitle = p?.description) {
        Text(
            "This page is rendered from the plugin manifest (id ${p?.id}). The block renderer ships in the plugins milestone.",
            color = tokens.colors.dim,
            style = TextStyle(fontFamily = GadgetFonts.body, fontSize = 13.sp),
        )
    }
}

@Composable
fun OnboardingScreen(app: AppState) {
    val tokens = com.thegadget.app.ui.theme.LocalTokens.current
    var step by remember { mutableStateOf(0) }
    val steps = listOf(
        "welcome" to "The Gadget is a media hub that lives entirely on your device.",
        "pick music" to "Choose a folder to build your library. Nothing leaves your phone.",
        "ready" to "You're set. Tap the hub to open music, games, homies, config.",
    )
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(steps[step].first, color = tokens.colors.text, style = TextStyle(fontFamily = GadgetFonts.display, fontWeight = FontWeight.Black, fontSize = 28.sp))
            Text(steps[step].second, color = tokens.colors.dim, style = TextStyle(fontFamily = GadgetFonts.body, fontSize = 14.sp))
            GlassButton(if (step < steps.size - 1) "next" else "start", tokens) {
                if (step < steps.size - 1) step++ else app.finishOnboarding()
            }
        }
    }
}
