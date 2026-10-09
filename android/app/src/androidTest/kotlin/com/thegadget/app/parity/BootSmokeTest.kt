package com.thegadget.app.parity

import android.graphics.Bitmap
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.thegadget.app.MainActivity
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Launch smoke test: proves the activity reaches RESUMED and the first frame is drawn without an
 * ANR. Prints BOOT_OK so the CI log is unambiguous; on a main-thread block the scenario call below
 * times out instead and the job fails loudly.
 */
class BootSmokeTest {

    @Test
    fun bootsToHubWithoutAnr() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        // Blocks until the activity is up; an ANR makes the instrumentation time out.
        scenario.onActivity { }
        assertTrue(
            "activity never reached RESUMED (state=${scenario.state})",
            scenario.state.isAtLeast(Lifecycle.State.RESUMED),
        )

        // Let the hub draw a few frames, then screenshot as evidence.
        Thread.sleep(2500)
        val shot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "parity",
        ).apply { mkdirs() }
        FileOutputStream(File(dir, "boot-smoke.png")).use { out ->
            shot.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        // Also emit an ASCII luminance grid of the hub so CI can compare the native layout against
        // the web reference as text (the artifact blob store is unreachable from the authoring net,
        // and adb pull from Android/data is blocked on API 30 — but println reaches logcat).
        val grid = asciiGrid(shot, 46)
        File(dir, "hub-grid.txt").writeText(grid.joinToString("\n"))
        for (line in grid) println("HUBGRID_ROW|$line")
        println("BOOT_OK state=${scenario.state} png=${shot.width}x${shot.height}")
        scenario.close()
    }

    /** Downsample to a cols-wide ASCII luminance grid, matching parity/web/ascii.mjs. */
    private fun asciiGrid(src: Bitmap, cols: Int): List<String> {
        val rows = Math.max(8, Math.round(cols.toFloat() * src.height / src.width))
        val small = Bitmap.createScaledBitmap(src, cols, rows, true)
        val ramp = " .:-=+*#%@"
        return (0 until rows).map { r ->
            buildString {
                for (c in 0 until cols) {
                    val p = small.getPixel(c, r)
                    val lum = ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
                    append(ramp[(lum * ramp.length / 256).coerceIn(0, ramp.length - 1)])
                }
            }
        }.also { if (small != src) small.recycle() }
    }
}
