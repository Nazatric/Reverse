package com.thegadget.app

import android.app.Application
import com.thegadget.app.core.AbsoluteDate
import com.thegadget.app.data.Stores
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The ErrorBoundary equivalent: nothing may ever take the app to a blank screen. Uncaught
 * exceptions on the main thread are captured into `gadget:crash` (the same key the web app uses)
 * and the offending route is popped, mirroring the web boundary's "render the hub, keep the
 * chrome" behaviour.
 */
class GadgetApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        AbsoluteDate.formatter = object : com.thegadget.app.core.AbsoluteDateFormatter {
            override fun format(epochMillis: Long): String =
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))
        }
        // The hub/Backdrop draw extracted SVG every frame; parsing those path strings on the
        // render thread is what stalls the main thread on device. Warm the cache off-main.
        Thread { com.thegadget.app.ui.warmSvgCache() }.apply {
            isDaemon = true
            name = "gadget-svg-warm"
            start()
        }
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                scope.launch { Stores(this@GadgetApp).setCrash("${error::class.java.simpleName}: ${error.message} @ ${thread.name}") }
            }
        }
    }
}
