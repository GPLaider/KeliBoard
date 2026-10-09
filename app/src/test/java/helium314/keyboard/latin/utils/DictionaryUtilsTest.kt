// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.settings.screens.getUserAndInternalDictionaries
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DictionaryUtilsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    // https://github.com/HeliBorg/HeliBoard/issues/1705
    @Test fun downloadOnlyMainDictionaryCanBeFoundWithoutInstallingIt() {
        val locale = "af".constructLocale()
        assertTrue(getKnownDictionariesForLocale(locale, context).any { it.second.endsWith("/main_af.dict") })
        assertTrue(locale in getDictionaryLocales(context))
        val (files, hasInternal) = getUserAndInternalDictionaries(context, locale)
        assertTrue(files.isEmpty())
        assertFalse(hasInternal)
    }

    @Test fun downloadOnlyEmojiDictionaryCanBeFoundWithoutInstallingIt() {
        val locale = "cy".constructLocale()
        assertTrue(getKnownDictionariesForLocale(locale, context).any { it.second.endsWith("/emoji_cy.dict") })
        assertTrue(locale in getDictionaryLocales(context))
        val (files, hasInternal) = getUserAndInternalDictionaries(context, locale)
        assertTrue(files.isEmpty())
        assertFalse(hasInternal)
    }

    @Test fun dictionaryListKeepsEveryBundledLocale() {
        val locales = getDictionaryLocales(context)
        val bundled = DictionaryInfoUtils.getAssetsDictionaryList(context)!!.map {
            DictionaryInfoUtils.extractLocaleFromAssetsDictionaryFile(it)
        }
        assertTrue(bundled.isNotEmpty())
        assertTrue(locales.containsAll(bundled))
    }

    @Test fun dictionaryListIncludesCatalogLocaleVariantsWithoutUnrelatedLanguages() {
        val locales = getDictionaryLocales(context)
        assertTrue("en_US".constructLocale() in locales)
        assertTrue("en_GB".constructLocale() in locales)
        assertFalse("zz".constructLocale() in locales)
    }
}
