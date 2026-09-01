// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin.common

import android.graphics.Color
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ColorsTest {
    @Test fun actionKeyIconUsesActualContrast() {
        assertTrue(needsDarkActionKeyIcon(Color.rgb(176, 198, 255)))
        assertFalse(needsDarkActionKeyIcon(Color.rgb(25, 55, 110)))
    }
}
