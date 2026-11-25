// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The colours of the few stock Swing pieces the app still uses: the tooltips.
 */

import javax.swing.BorderFactory
import javax.swing.UIManager

/**
 * Puts the colours of the theme on the stock Swing pieces.
 *
 * The app draws its own widgets, but the tooltips are Swing's own, and they follow the
 * theme through the look and feel. Without this a tooltip would be the colour of the
 * desktop in the middle of a dark window.
 */
internal fun applyDialogTheme() {
    UIManager.put("ToolTip.background", Theme.field)
    UIManager.put("ToolTip.foreground", Theme.text)
    UIManager.put("ToolTip.border", BorderFactory.createLineBorder(Theme.line))
}
