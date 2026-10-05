// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.view.inputmethod.EditorInfo
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RecentEmojisTest {
    @Test fun recentsFillRowsAtDefaultScale() = assertFullRows(1f)

    // https://github.com/HeliBorg/HeliBoard/issues/2810
    @Test fun recentsFillRowsAtEightyPercentScale() = assertFullRows(0.8f)

    @Test fun recentsFillRowsAtMinimumScale() = assertFullRows(0.5f)

    @Test @Config(qualifiers = "land")
    fun recentsFillLandscapeRowsAtMinimumScale() = assertFullRows(0.5f)

    private fun assertFullRows(scale: Float) {
        val ime = Robolectric.setupService(LatinIME::class.java)
        val prefs = ime.prefs()
        val original = prefs.getString(Settings.PREF_RECENT_EMOJIS, null)
        val originalScale = prefs.getFloat(Settings.PREF_EMOJI_FONT_SCALE, 1f)
        try {
            prefs.edit().putFloat(Settings.PREF_EMOJI_FONT_SCALE, scale).commit()
            Settings.getInstance().loadSettings(ime, Locale.US,
                InputAttributes(EditorInfo(), false, ime.packageName))
            KeyboardLayoutSet.onKeyboardThemeChanged()
            RecentEmojis.set(emptyList())
            for (codePoint in 0x1F600..0x1F64F) RecentEmojis.addCodepoint(codePoint)
            // Reusing an emoji must keep it first without reducing the available history.
            RecentEmojis.addCodepoint(0x1F640)
            val history = RecentEmojis.get()
            assertEquals("🙀", history.first())
            assertEquals(history.distinct(), history)

            // Build a fresh palette to verify the grid after restoring persisted history.
            val view = EmojiPalettesView(ime, null)
            val category = ReflectionHelpers.getField<EmojiCategory>(view, "mEmojiCategory")
            val keyboard = category.getKeyboard(EmojiCategory.Category.RECENTS, 0)
            val columns = keyboard.occupiedColumnCount
            val nominalCount = ime.resources.getInteger(R.integer.config_emoji_keyboard_max_recents_key_count)
            val rows = (nominalCount + columns - 1) / columns
            assertTrue(history.size < 80, "recent history must remain bounded")
            assertEquals(rows * columns, keyboard.sortedKeys.size, "incomplete recent rows at scale $scale")
            assertEquals(0, keyboard.sortedKeys.size % columns)
        } finally {
            prefs.edit().putString(Settings.PREF_RECENT_EMOJIS, original)
                .putFloat(Settings.PREF_EMOJI_FONT_SCALE, originalScale).commit()
            Settings.getInstance().loadSettings(ime, Locale.US,
                InputAttributes(EditorInfo(), false, ime.packageName))
            KeyboardLayoutSet.onKeyboardThemeChanged()
        }
    }
}
