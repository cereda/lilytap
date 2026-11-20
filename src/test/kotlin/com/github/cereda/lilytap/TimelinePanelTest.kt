// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The recording timeline: it keeps the playhead on screen during playback. */
class TimelinePanelTest {
    // 4/4, 2 cells per beat: 8 cells a bar, so 8 bars are much wider than a 400 px view.
    private fun config(bars: Int = 8) = Config(60.0, TimeSignature(4, 4), 2, bars, 0.0, "c")

    private fun panel(config: Config = config()): Pair<TimelinePanel, GridView> {
        val grid =
            GridView().apply {
                this.config = config
                viewWidth = 400
            }
        val panel = TimelinePanel(grid)
        panel.setSize(400, 130)
        return panel to grid
    }

    private fun score(config: Config) = Score(listOf(Event(TapKind.NOTE, 0, config.totalUnits)), config)

    private fun playheadX(
        grid: GridView,
        panel: TimelinePanel,
        config: Config,
    ): Double = grid.xOfCell(panel.playbackPositionMs / config.cellMs)

    @Test
    fun `pressing play snaps the view back to the start`() {
        val (panel, grid) = panel()
        val cfg = grid.config!!
        panel.score = score(cfg)
        grid.scrollTo(grid.maxScroll)
        panel.playbackPositionMs = 0.0
        panel.follow()
        assertEquals(0.0, grid.scrollX)
    }

    @Test
    fun `resetScroll brings the grid back to the start`() {
        val (panel, grid) = panel()
        grid.scrollTo(grid.maxScroll)
        panel.resetScroll()
        assertEquals(0.0, grid.scrollX)
    }

    @Test
    fun `the view follows the playhead through a long score`() {
        val (panel, grid) = panel()
        val cfg = grid.config!!
        panel.score = score(cfg)
        val totalMs = cfg.totalCells * cfg.cellMs
        for (fraction in listOf(0.0, 0.25, 0.5, 0.75, 1.0)) {
            panel.playbackPositionMs = totalMs * fraction
            panel.follow()
            val x = playheadX(grid, panel, cfg)
            assertTrue(
                x >= grid.padX - 0.5 && x <= grid.viewWidth - grid.padX + 0.5,
                "playhead at ${fraction * 100}% is at x=$x, outside the view",
            )
        }
    }

    @Test
    fun `the view is pinned at the end when the score runs out`() {
        val (panel, grid) = panel()
        val cfg = grid.config!!
        panel.score = score(cfg)
        panel.playbackPositionMs = cfg.totalCells * cfg.cellMs
        panel.follow()
        assertEquals(grid.maxScroll, grid.scrollX)
    }

    @Test
    fun `a short score that fits does not scroll during playback`() {
        val (panel, grid) = panel(config(bars = 1))
        val cfg = grid.config!!
        panel.score = score(cfg)
        panel.playbackPositionMs = cfg.totalCells * cfg.cellMs / 2
        panel.follow()
        assertEquals(0.0, grid.scrollX)
    }

    @Test
    fun `the timeline paints a score without a session`() {
        val (panel, grid) = panel()
        panel.score = score(grid.config!!)
        panel.playbackPositionMs = 0.0
        val image = BufferedImage(400, 130, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        panel.paint(graphics)
        graphics.dispose()
        assertNotNull(image.colorModel)
    }

    @Test
    fun `score blocks are drawn at their cell positions`() {
        val cfg = config(bars = 1)
        val (panel, grid) = panel(cfg)
        panel.score =
            Score(
                listOf(
                    Event(TapKind.NOTE, 0, 32),
                    Event(TapKind.REST, 32, 32),
                ),
                cfg,
            )
        val image = BufferedImage(400, 130, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            panel.paint(graphics)
        } finally {
            graphics.dispose()
        }
        // The rest starts at cell 4. If blocks were positioned with milliseconds instead of
        // cells, it would be drawn far off-screen and this pixel would stay background.
        val x = grid.xOfCell(5.5).toInt()
        val pixel = image.getRGB(x, 70) and 0xFFFFFF
        assertTrue(pixel != Theme.code.rgb, "the rest block should be visible at cell 5.5")
    }
}
