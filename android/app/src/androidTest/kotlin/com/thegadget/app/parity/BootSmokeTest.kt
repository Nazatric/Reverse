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
        println("BOOT_OK state=${scenario.state} png=${shot.width}x${shot.height}")
        scenario.close()
    }
}
