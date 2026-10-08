// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.KeyboardElement
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardView
import helium314.keyboard.keyboard.internal.KeyDrawParams
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.EmojiParser
import helium314.keyboard.latin.LatinIME
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EmoticonRenderingTest {
    private val context = Robolectric.setupService(LatinIME::class.java)

    @Test fun emoticonLabelsFitTheirKeysAndKeepTheirOutputText() {
        val params = KeyboardParams().apply {
            mId = KeyboardLayoutSet.getFakeKeyboardId(KeyboardElement.EMOJI_EMOTICONS)
            mDefaultKeyWidth = 0.1f
            mDefaultRowHeight = 0.25f
        }
        val keys = EmojiParser(params, context).parse().flatten().map { it.createKey() }
        assertTrue(keys.size > 10)
        val view = TestKeyboardView(context)
        val canvas = Canvas(Bitmap.createBitmap(500, 200, Bitmap.Config.ARGB_8888))
        for (key in keys) for (scale in listOf(0.8f, 1f, 1.5f)) {
            val label = checkNotNull(key.label)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            view.drawLabel(key, canvas, paint, scale)
            assertTrue(paint.measureText(label) > 0)
            val bounds = Rect()
            paint.getTextBounds(label, 0, label.length, bounds)
            val origin = (key.drawWidth - paint.measureText(label)) / 2f
            assertTrue(origin + bounds.left >= -0.5f && origin + bounds.right <= key.drawWidth + 0.5f,
                "$label extends outside its ${key.drawWidth}px key at size ${paint.textSize}")
            assertEquals(label, key.outputText)
        }
    }

    @Test fun emojiLabelsKeepTheirConfiguredSize() {
        val params = KeyboardParams().apply {
            mId = KeyboardLayoutSet.getFakeKeyboardId(KeyboardElement.EMOJI_SMILEYS)
            mDefaultKeyWidth = 0.1f
            mDefaultRowHeight = 0.25f
        }
        val key = EmojiParser(params, context).parse().flatten().first().createKey()
        val view = TestKeyboardView(context)
        val canvas = Canvas(Bitmap.createBitmap(500, 200, Bitmap.Config.ARGB_8888))
        for (scale in listOf(0.8f, 1f, 1.5f)) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            view.drawLabel(key, canvas, paint, scale)
            assertEquals(40f * scale, paint.textSize)
        }
    }

    private class TestKeyboardView(context: Context) : KeyboardView(context, null) {
        fun drawLabel(key: Key, canvas: Canvas, paint: Paint, scale: Float = 1f) {
            mFontSizeMultiplier = scale
            val params = KeyDrawParams().apply {
                mLetterSize = 40
                mLabelSize = 40
                mLargeLetterSize = 40
                mAnimAlpha = 255
            }
            onDrawKeyTopVisuals(key, canvas, paint, params)
        }
    }
}
