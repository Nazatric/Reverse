package com.thegadget.app.core

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/**
 * Hub composition — port of `src/components/hub/nodes.ts` + `Chains.tsx`.
 *
 * Positions are percentages of the stage; `d` (orb diameter) and `ly` (distance from the orb's
 * bottom edge to the centre of its label) are in reference pixels of the 736-unit canvas.
 */
enum class HubNodeId(val key: String) {
    MUSIC("music"),
    GAMES("games"),
    CONFIG("config"),
    HOMIES("homies"),
    ACCOUNT("account");

    companion object {
        fun fromKey(key: String): HubNodeId? = entries.firstOrNull { it.key == key }
    }
}

data class HubNodeSpec(
    val id: HubNodeId,
    val label: String,
    val x: Float,
    val y: Float,
    val d: Float,
    val ly: Float,
    /** Plugin nodes carry a glyph name and an optional target page; built-ins leave both null. */
    val icon: String? = null,
    val page: String? = null,
    val pluginId: String? = null,
)

/** HUB, NODES — byte-for-byte the values in `nodes.ts`. */
object HubNodes {
    val HUB = HubNodeSpec(HubNodeId.MUSIC, "music", 49.5f, 52f, 172f, 0f)

    val NODES: List<HubNodeSpec> = listOf(
        HubNodeSpec(HubNodeId.MUSIC, "music", 18.5f, 22.8f, 172f, 23f),
        HubNodeSpec(HubNodeId.GAMES, "games", 74.6f, 31.1f, 108f, 14f),
        HubNodeSpec(HubNodeId.CONFIG, "config", 77.6f, 65.5f, 108f, 24f),
        HubNodeSpec(HubNodeId.HOMIES, "homies", 27.2f, 73.8f, 106f, 26f),
        HubNodeSpec(HubNodeId.ACCOUNT, "account", 53f, 79.5f, 94f, 16f),
    )

    /**
     * Wall-clock delay before each orb animates in: `180ms + i * 90ms`, and while the boot
     * sequence is playing `300ms + i * 80ms` (see `hub.css` `html[data-boot=on] .node`).
     */
    fun nodeDelayMs(index: Int, booting: Boolean): Int =
        if (booting) 300 + index * 80 else 180 + index * 90
}

/** One chain branch geometry, exactly as `Chains.tsx` computes it. */
data class ChainBranch(
    val id: HubNodeId,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    /** rotation in degrees */
    val angle: Float,
    /** CSS animation-delay value, in seconds */
    val delaySec: Float,
)

object HubLayout {
    /** `const H = Math.max(26, 40 * m.u)` */
    fun chainThickness(u: Float, chainScale: Float = 1f): Float = max(26f, 40f * u * chainScale)

    fun chains(m: GadgetMetrics, chainScale: Float = 1f): List<ChainBranch> {
        val hx = m.stageX(HubNodes.HUB.x)
        val hy = m.stageY(HubNodes.HUB.y)
        val h = chainThickness(m.u, chainScale)
        return HubNodes.NODES.mapIndexed { i, n ->
            val dx = m.stageX(n.x) - hx
            val dy = m.stageY(n.y) - hy
            ChainBranch(
                id = n.id,
                left = hx,
                top = hy - h / 2f,
                width = hypot(dx, dy),
                height = h,
                angle = (atan2(dy, dx) * 180.0 / Math.PI).toFloat(),
                delaySec = -i * 1.9f,
            )
        }
    }

    /** Sway is `rotate(a ± …)`; the two ends of the 9s alternate keyframes. */
    const val SWAY_MIN_DEG = -0.7f
    const val SWAY_MAX_DEG = 0.9f

    /** Label centre offset below the orb: `top: 100% + ly * u`, then `translate(-50%,-50%)`. */
    fun labelCenterY(orbTop: Float, size: Float, ly: Float, u: Float): Float =
        orbTop + size + ly * u

    /** A plugin node's default `ly` when the document omits it. */
    const val PLUGIN_DEFAULT_LY = 14f
    const val PLUGIN_DEFAULT_D = 96f
}
