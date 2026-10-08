// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.ImageButton
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings

/** Toolbar keys use the keyboard's long-press delay instead of the system delay. */
class ToolbarKeyButton(context: Context) : ImageButton(context, null, R.attr.suggestionWordStyle) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var performedLongClick = false
    private val longPress = Runnable {
        if (isEnabled && isPressed && isLongClickable)
            performedLongClick = performLongClick()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || (!isClickable && !isLongClickable))
            return super.onTouchEvent(event)

        // Handle the press here so View cannot schedule a second, system-timed long click.
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelLongPress()
                performedLongClick = false
                isPressed = true
                drawableHotspotChanged(event.x, event.y)
                if (isLongClickable) {
                    val timeout = context.prefs().getInt(
                        Settings.PREF_KEY_LONGPRESS_TIMEOUT, Defaults.PREF_KEY_LONGPRESS_TIMEOUT)
                    postDelayed(longPress, timeout.toLong())
                }
            }
            MotionEvent.ACTION_MOVE -> {
                drawableHotspotChanged(event.x, event.y)
                if (event.x < -touchSlop || event.x >= width + touchSlop
                    || event.y < -touchSlop || event.y >= height + touchSlop) {
                    cancelLongPress()
                    isPressed = false
                }
            }
            MotionEvent.ACTION_UP -> {
                cancelLongPress()
                if (isPressed && !performedLongClick && isClickable) performClick()
                isPressed = false
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelLongPress()
                isPressed = false
            }
        }
        return true
    }

    override fun cancelLongPress() {
        super.cancelLongPress()
        removeCallbacks(longPress)
    }

    override fun onDetachedFromWindow() {
        cancelLongPress()
        isPressed = false
        super.onDetachedFromWindow()
    }
}
