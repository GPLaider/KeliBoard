// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.App
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowPaint
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ShadowEmojiGlyphPaint::class])
class SupportedEmojisTest {
    private val context = ApplicationProvider.getApplicationContext<App>()

    @BeforeTest fun reset() {
        ShadowEmojiGlyphPaint.missingGlyphs = emptySet()
        context.prefs().edit().putInt(Settings.PREF_EMOJI_MAX_SDK, 34).commit()
    }

    @AfterTest fun restore() {
        ShadowEmojiGlyphPaint.missingGlyphs = emptySet()
        context.prefs().edit().remove(Settings.PREF_EMOJI_MAX_SDK).commit()
        SupportedEmojis.load(context)
    }

    // https://github.com/HeliBorg/HeliBoard/issues/2823
    @Test fun missingSequenceIsHiddenEvenWhenItsVersionIsAllowed() {
        ShadowEmojiGlyphPaint.missingGlyphs = setOf("🚶‍➡️")
        SupportedEmojis.load(context)

        assertTrue(SupportedEmojis.isUnsupported("🚶‍➡️"))
        assertFalse(SupportedEmojis.isUnsupported("🙂‍↔️"))
        assertFalse(SupportedEmojis.isUnsupported("🚶"))
    }

    @Test fun newerGlyphDoesNotMakeUnsupportedOlderSequencesVisible() {
        context.prefs().edit().putInt(Settings.PREF_EMOJI_MAX_SDK, 36).commit()
        ShadowEmojiGlyphPaint.missingGlyphs = setOf("🙂‍↔️", "🚶‍➡️")
        SupportedEmojis.load(context)

        assertFalse(SupportedEmojis.isUnsupported("🫩"))
        assertTrue(SupportedEmojis.isUnsupported("🙂‍↔️"))
        assertTrue(SupportedEmojis.isUnsupported("🚶‍➡️"))
    }

    @Test fun versionOverrideStillLimitsRenderableGlyphs() {
        context.prefs().edit().putInt(Settings.PREF_EMOJI_MAX_SDK, 33).commit()
        SupportedEmojis.load(context)

        assertFalse(SupportedEmojis.isUnsupported("🫨"))
        assertTrue(SupportedEmojis.isUnsupported("🙂‍↔️"))
        assertTrue(SupportedEmojis.isUnsupported("🫩"))
    }
}

@Implements(Paint::class)
class ShadowEmojiGlyphPaint : ShadowPaint() {
    @Implementation fun hasGlyph(text: String): Boolean = text !in missingGlyphs

    companion object {
        @Volatile var missingGlyphs = emptySet<String>()
    }
}
