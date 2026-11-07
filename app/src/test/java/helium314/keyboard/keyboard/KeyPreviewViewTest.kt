// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import helium314.keyboard.keyboard.internal.KeyDrawParams
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.KeyPreviewView
import helium314.keyboard.latin.LatinIME
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KeyPreviewViewTest {
    private val context = Robolectric.setupService(LatinIME::class.java)

    // https://github.com/HeliBorg/HeliBoard/issues/712
    @Test fun narrowPreviewKeepsLetterProportions() {
        val view = KeyPreviewView(context, null).apply { background = SizedBackground(40) }
        preview(view, "M", 96)
        assertEquals(1f, view.textScaleX)
        assertTrue(view.paint.measureText("M") <= 28.5f)
        assertTrue(view.textSize < 96f)
    }

    @Test fun previouslyFittingLabelIsRefittedWhenBackgroundNarrows() {
        val view = KeyPreviewView(context, null).apply { background = SizedBackground(200) }
        preview(view, "G", 96)
        assertEquals(96f, view.textSize)
        view.background = SizedBackground(40)
        preview(view, "G", 96)
        assertTrue(view.paint.measureText("G") <= 28.5f)
        assertEquals(1f, view.textScaleX)
    }

    @Test fun previouslyFittingLabelIsRefittedWhenTextSizeIncreases() {
        val view = KeyPreviewView(context, null).apply { background = SizedBackground(40) }
        preview(view, "P", 16)
        assertEquals(16f, view.textSize)
        preview(view, "P", 96)
        assertTrue(view.paint.measureText("P") <= 28.5f)
        assertEquals(1f, view.textScaleX)
    }

    @Test fun fittingTextAndEmojiKeepTheirRequestedSize() {
        val view = KeyPreviewView(context, null).apply { background = SizedBackground(200) }
        preview(view, "A", 24)
        assertEquals(24f, view.textSize)
        assertEquals(1f, view.textScaleX)
        view.background = SizedBackground(20)
        preview(view, "😀", 96)
        assertEquals(96f, view.textSize)
        assertEquals(1f, view.textScaleX)
    }

    private fun preview(view: KeyPreviewView, label: String, size: Int) {
        val key = Key(label, null, label.codePointAt(0), null, null, 0,
            Key.BACKGROUND_TYPE_NORMAL, 0, 0, 80, 100, 0, 0)
        val params = KeyDrawParams().apply { mPreviewTextSize = size; mLetterSize = size }
        view.setPreviewVisual(key, KeyboardIconsSet.instance, params)
    }

    private class SizedBackground(private val width: Int) : Drawable() {
        override fun getIntrinsicWidth() = width
        override fun getIntrinsicHeight() = 160
        override fun getPadding(padding: Rect): Boolean { padding.set(6, 6, 6, 6); return true }
        override fun draw(canvas: Canvas) = Unit
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Suppress("DEPRECATION")
        override fun getOpacity() = PixelFormat.TRANSPARENT
    }
}
