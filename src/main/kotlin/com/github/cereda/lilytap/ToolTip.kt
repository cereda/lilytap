// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The tooltips of the app: the lines of text a tooltip says.
 */

/**
 * A tooltip that says more than one line.
 *
 * Swing draws a tooltip on a single line unless the text is HTML, so a tooltip that has
 * more to say is written as HTML with a break between the lines. The width is fixed, so a
 * long line wraps instead of running off the screen.
 *
 * @param lines the lines of the tooltip.
 * @return the text of the tooltip.
 */
internal fun tooltip(vararg lines: String): String = htmlLines(TOOLTIP_WIDTH, lines.toList())

/**
 * A tooltip that says more than one line.
 *
 * @param lines the lines of the tooltip.
 * @return the text of the tooltip.
 */
internal fun tooltip(lines: List<String>): String = htmlLines(TOOLTIP_WIDTH, lines)

/**
 * A tooltip that says more than one paragraph.
 *
 * The paragraphs are a little apart, the way the paragraphs of a message are.
 *
 * @param lines the paragraphs of the tooltip.
 * @return the text of the tooltip.
 */
internal fun tooltipParagraphs(lines: List<String>): String = htmlParagraphs(TOOLTIP_WIDTH, lines)

/**
 * The HTML of a few lines of text.
 *
 * The width is an attribute of the body and not a style: Swing reads a style as a minimum
 * and keeps the natural width of the text, so a long line would run off the screen.
 *
 * @param widthPx how wide the text is, in pixels.
 * @param lines the lines of the text.
 * @return the HTML of the text.
 */
internal fun htmlLines(
    widthPx: Int,
    lines: List<String>,
): String =
    "<html><body width='$widthPx'>" +
        lines.joinToString("<br>") { escapeHtml(it) } +
        "</body></html>"

/**
 * The HTML of a few paragraphs of text.
 *
 * The space between the paragraphs is a margin on the paragraphs themselves: Swing has no
 * line height of its own, so a `<br>` leaves a whole line between the paragraphs, and a
 * smaller font is not a smaller line - Swing keeps the height of the line. The margin is
 * the only way to leave a small gap.
 *
 * @param widthPx how wide the text is, in pixels.
 * @param lines the paragraphs of the text.
 * @return the HTML of the text.
 */
internal fun htmlParagraphs(
    widthPx: Int,
    lines: List<String>,
): String =
    "<html><body width='$widthPx'>" +
        lines.dropLast(1).joinToString("") { paragraph(it, PARAGRAPH_GAP) } +
        lines.lastOrNull()?.let { paragraph(it, 0) }.orEmpty() +
        "</body></html>"

/**
 * One paragraph of text.
 *
 * @param text the text of the paragraph.
 * @param gapPx the space below the paragraph, in pixels.
 * @return the HTML of the paragraph.
 */
private fun paragraph(
    text: String,
    gapPx: Int,
): String {
    val margin = if (gapPx > 0) "margin:0 0 ${gapPx}px 0" else "margin:0"
    return "<p style='$margin'>${escapeHtml(text)}</p>"
}

/**
 * The text with the characters HTML would read as markup.
 *
 * @param text the text.
 * @return the text, escaped.
 */
internal fun escapeHtml(text: String): String =
    text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

/** How wide a tooltip is, in pixels. */
private const val TOOLTIP_WIDTH = 360

/** The space between the paragraphs of a message, in pixels. */
private const val PARAGRAPH_GAP = 3
