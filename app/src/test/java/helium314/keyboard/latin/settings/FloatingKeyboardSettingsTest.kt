package helium314.keyboard.latin.settings

import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.App
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "xxhdpi")
class FloatingKeyboardSettingsTest {
    @Test fun storedPixelSizeIsClampedToDpMinimum() {
        val context = ApplicationProvider.getApplicationContext<App>()
        val resources = context.resources
        val screenWidth = resources.displayMetrics.widthPixels
        val widthKey = Settings.PREF_FLOATING_WIDTH_PREFIX + screenWidth
        val heightKey = Settings.PREF_FLOATING_HEIGHT_PREFIX + screenWidth
        assertTrue(resources.displayMetrics.density > 1f)

        context.prefs().edit().putInt(widthKey, 150).putInt(heightKey, 100).commit()
        try {
            assertEquals(resources.getDimensionPixelSize(R.dimen.config_floating_min_width), readFloatingWidth(context))
            assertEquals(resources.getDimensionPixelSize(R.dimen.config_floating_min_height), readFloatingHeight(context))
        } finally {
            context.prefs().edit().remove(widthKey).remove(heightKey).commit()
        }
    }
}
