// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common

import android.graphics.Color
import helium314.keyboard.keyboard.KeyboardTheme.Companion.STYLE_MATERIAL
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ColorsTest {
    @Test fun shiftedIconUsesKeyTextColor() {
        val colors = DefaultColors(
            themeStyle = STYLE_MATERIAL,
            hasKeyBorders = true,
            accent = 0xff171717.toInt(),
            background = Color.BLACK,
            keyBackground = 0xff171717.toInt(),
            functionalKey = 0xff171717.toInt(),
            spaceBar = 0xff171717.toInt(),
            keyText = Color.WHITE,
            keyHintText = Color.WHITE,
        )

        assertEquals(Color.WHITE, colors.get(ColorType.SHIFT_KEY_ICON))
    }
}
