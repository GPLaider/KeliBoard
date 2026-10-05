package helium314.keyboard

import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import helium314.keyboard.latin.spellcheck.AndroidSpellCheckerService
import helium314.keyboard.latin.spellcheck.AndroidSpellCheckerSession
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowInputMethodManager2::class,
    ShadowDictionaryFacilitatorImpl::class,
])
class SpellCheckerTest {
    private val checkService = Robolectric.setupService(AndroidSpellCheckerService::class.java)
    private val checker = AndroidSpellCheckerSession(checkService)

    @Test fun `number is not a typo`() {
        val result = checker.onGetSuggestions(TextInfo("123"), 2)
        assertNotEquals(SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO, result.suggestionsAttributes)
    }

    @Test fun `$ is not a typo`() {
        val result = checker.onGetSuggestions(TextInfo("$"), 2)
        assertNotEquals(SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO, result.suggestionsAttributes)
    }

    // https://github.com/HeliBorg/HeliBoard/issues/2851
    @Test fun singleLettersAreNotTypos() {
        for (text in listOf("x", "X", "q", "z")) {
            assertFalse(isTypo(checker.onGetSuggestions(TextInfo(text), 2)), text)
        }
    }

    @Test fun linksAreNotTypos() {
        for (text in listOf("https://example.org/zzqxx", "example.org", "www.example.org",
            "https://example.org/path?query=value#fragment")) {
            assertFalse(isTypo(checker.onGetSuggestions(TextInfo(text), 2)), text)
        }
    }

    @Test fun sentenceLinkIsNotSplitAndSurroundingTyposAreStillChecked() {
        // Avoid native suggestion generation; unknown ordinary words should still be flagged.
        checkService.prefs().edit().putBoolean(Settings.PREF_SPELLCHECK_SUGGEST, false).commit()
        val link = "https://example.org/zzqxx?query=value"
        val sentence = "zzqxx $link zzqxx"
        val result = checker.onGetSentenceSuggestionsMultiple(arrayOf(TextInfo(sentence, 17, 23)), 2)!!.single()!!

        assertEquals(3, result.suggestionsCount)
        assertTrue(isTypo(result.getSuggestionsInfoAt(0)))
        assertFalse(isTypo(result.getSuggestionsInfoAt(1)))
        assertTrue(isTypo(result.getSuggestionsInfoAt(2)))
        assertEquals(sentence.indexOf(link), result.getOffsetAt(1))
        assertEquals(link.length, result.getLengthAt(1))
        for (index in 0 until result.suggestionsCount) {
            assertEquals(17, result.getSuggestionsInfoAt(index).cookie)
            assertEquals(23, result.getSuggestionsInfoAt(index).sequence)
        }
    }

    private fun isTypo(result: SuggestionsInfo) =
        result.suggestionsAttributes and SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO != 0
}
