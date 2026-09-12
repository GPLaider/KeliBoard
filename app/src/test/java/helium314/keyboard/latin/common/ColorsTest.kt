// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin.common

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.keyboard.KeyboardTheme.Companion.STYLE_MATERIAL
import helium314.keyboard.latin.App
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.EnumMap
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ColorsTest {
    @Test fun actionKeyIconUsesActualContrast() {
        assertTrue(needsDarkActionKeyIcon(Color.rgb(176, 198, 255)))
        assertFalse(needsDarkActionKeyIcon(Color.rgb(25, 55, 110)))
    }

    @Test fun mainBackgroundDrawableIsNotSharedBetweenViews() {
        val context = ApplicationProvider.getApplicationContext<App>()
        val colors = AllColors(EnumMap(ColorType::class.java), STYLE_MATERIAL, false, ColorDrawable(Color.RED))
        val first = View(context).apply { layout(0, 0, 100, 100) }
        val second = View(context).apply { layout(0, 0, 50, 50) }

        colors.setBackground(first, ColorType.MAIN_BACKGROUND)
        colors.setBackground(second, ColorType.MAIN_BACKGROUND)

        assertNotSame(first.background, second.background)
    }
}
