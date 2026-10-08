package com.thegadget.app.core

/**
 * Y2K 2048 — pure engine, ported 1:1 from `src/pages/games/Game2048.tsx`.
 *
 * The web implementation walks the four lines of the board (reversing the order for
 * right/down), merges equal neighbours into a *new* tile that carries `pop`, and reports whether
 * anything moved plus the score gained. `spawn` picks a uniformly random free cell and gives it a
 * 2 (90%) or a 4 (10%). Keeping the same shapes means the native board behaves identically,
 * including the 120 ms slide-then-spawn beat and the win/stuck detection.
 */
enum class Direction { UP, DOWN, LEFT, RIGHT }

data class Tile(
    val id: Int,
    val v: Int,
    val r: Int,
    val c: Int,
    val pop: Boolean = false,
)

data class MoveResult(val tiles: List<Tile>, val gained: Int, val moved: Boolean)

/**
 * Randomness source so the engine is deterministic under test. Production passes
 * `Math.random()`-equivalent `kotlin.random.Random`; tests pass a scripted sequence.
 */
interface RandomSource {
    /** Uniform in [0,1). */
    fun nextDouble(): Double

    companion object {
        fun of(random: kotlin.random.Random): RandomSource = object : RandomSource {
            override fun nextDouble(): Double = random.nextDouble()
        }
    }
}

object Game2048 {
    const val SIZE = 4
    const val WIN = 2048

    /** `spawn`: one new tile in a free cell, 2 at 90% / 4 at 10%. */
    fun spawn(tiles: List<Tile>, nextId: () -> Int, random: RandomSource): List<Tile> {
        val taken = tiles.map { it.r * SIZE + it.c }.toSet()
        val free = (0 until SIZE * SIZE).filter { it !in taken }
        if (free.isEmpty()) return tiles
        val cell = free[(random.nextDouble() * free.size).toInt().coerceIn(0, free.size - 1)]
        val value = if (random.nextDouble() < 0.9) 2 else 4
        return tiles + Tile(
            id = nextId(),
            v = value,
            r = cell / SIZE,
            c = cell % SIZE,
            pop = true,
        )
    }

    fun fresh(nextId: () -> Int, random: RandomSource): List<Tile> =
        spawn(spawn(emptyList(), nextId, random), nextId, random)

    /**
     * `move(tiles, dir)` — identical traversal order and merge rule to the web version:
     * equal neighbours merge into one tile of double value, each tile merges at most once per move.
     */
    fun move(tiles: List<Tile>, dir: Direction, nextId: () -> Int): MoveResult {
        val grid: Array<Array<Tile?>> = Array(SIZE) { arrayOfNulls(SIZE) }
        for (t in tiles) grid[t.r][t.c] = t

        var gained = 0
        var moved = false
        val result: Array<Array<Tile?>> = Array(SIZE) { arrayOfNulls(SIZE) }

        // For up/down a "line" walks a column (c = k), for left/right it walks a row (r = k).
        for (k in 0 until SIZE) {
            fun row(i: Int): IntArray =
                if (dir == Direction.UP || dir == Direction.DOWN) intArrayOf(i, k) else intArrayOf(k, i)

            // Cells of this line in traversal order: left/up count from the near edge,
            // right/down from the far edge.
            val forward = dir == Direction.LEFT || dir == Direction.UP
            val order = IntArray(SIZE) { if (forward) it else SIZE - 1 - it }
            val line = order.map { i ->
                val (r, c) = row(i)
                grid[r][c]
            }
            val list = line.filterNotNull()

            // The destination of the n-th surviving tile is the n-th cell *in traversal order*,
            // so for right/down it counts back from the far edge (`order[slot]`, not `slot`).
            var slot = 0
            var i = 0
            while (i < list.size) {
                val current = list[i]
                if (i + 1 < list.size && current.v == list[i + 1].v) {
                    val (r, c) = row(order[slot])
                    result[r][c] = Tile(id = nextId(), v = current.v * 2, r = r, c = c, pop = true)
                    gained += current.v * 2
                    moved = true
                    i++
                } else {
                    val (r, c) = row(order[slot])
                    if (current.r != r || current.c != c) moved = true
                    result[r][c] = current.copy(r = r, c = c)
                }
                slot++
                i++
            }
        }

        val out = ArrayList<Tile>(SIZE * SIZE)
        for (r in 0 until SIZE) for (c in 0 until SIZE) result[r][c]?.let { out.add(it) }
        return MoveResult(out, gained, moved)
    }

    /** The web app probes all four directions; no trial move succeeding means the game is over. */
    fun isStuck(tiles: List<Tile>, nextId: () -> Int): Boolean =
        Direction.entries.none { move(tiles, it, nextId).moved }

    fun isWon(tiles: List<Tile>): Boolean = tiles.any { it.v >= WIN }
}

/** The score/best rule from the web view: best is persisted whenever the score overtakes it. */
fun Game2048.newBest(score: Int, gained: Int, best: Int): Pair<Int, Int> {
    val next = score + gained
    return if (next > best) next to next else next to best
}
