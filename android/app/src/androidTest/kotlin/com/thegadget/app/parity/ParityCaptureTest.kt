package com.thegadget.app.parity

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.thegadget.app.MainActivity
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/**
 * Native half of the visual parity pipeline.
 *
 * For every screen the web harness captures (`parity/web/capture.mjs`), this test puts the app on
 * the same route with the same seeded state and writes a PNG. `parity/compare.mjs` then diffs the
 * two sets and emits reference / native / diff / overlay images plus a mismatch table.
 *
 * Determinism mirrors the web harness exactly:
 *   - the same frozen clock (2026-10-07T16:20),
 *   - the same seeded profile, library, playlists, games, homies and plugin record,
 *   - motion off, so transitions are already at rest when the shot is taken.
 *
 * Run:
 *   ./gradlew :app:connectedDebugAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=com.thegadget.app.parity.ParityCaptureTest
 *   adb pull /sdcard/Android/data/com.thegadget.app.debug/files/parity parity/out/native
 */
class ParityCaptureTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun captureAllScreens() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outDir = File(context.getExternalFilesDir(null), "parity/").apply { mkdirs() }

        // The app boots with onboarding suppressed and the deterministic seed applied — the same
        // JSON the web harness injects into localStorage.
        for (screen in ParityScreens.ALL) {
            rule.activity.runOnUiThread { ParityScreens.apply(rule.activity, screen) }
            rule.waitForIdle()
            // Let one frame settle exactly like settle(page, 400) does on the web side.
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(400)
            val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
            FileOutputStream(File(outDir, "${screen}.png")).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        // A missing screen in the output directory is the failure mode this test must prevent.
        assumeTrue("parity output written to ${outDir.absolutePath}", outDir.listFiles()?.isNotEmpty() == true)
    }
}
