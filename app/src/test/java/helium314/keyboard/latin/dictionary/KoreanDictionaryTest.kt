// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.dictionary

import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import java.text.Normalizer
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class KoreanDictionaryTest {
    @Test fun queriesTheRecommendedDictionaryWithPrecomposedHangul() {
        val delegate = RecordingDictionary()
        val dictionary = KoreanDictionary(delegate)

        assertEquals(7, dictionary.getFrequency(Normalizer.normalize("한글", Normalizer.Form.NFD)))
        assertEquals("한글", delegate.queriedWord)
    }

    private class RecordingDictionary : Dictionary(TYPE_MAIN, Locale.KOREAN) {
        var queriedWord: String? = null

        override fun getSuggestions(
            composedData: ComposedData?,
            ngramContext: NgramContext?,
            proximityInfoHandle: Long,
            settingsValuesForSuggestion: SettingsValuesForSuggestion?,
            sessionId: Int,
            weightForLocale: Float,
            inOutWeightOfLangModelVsSpatialModel: FloatArray?,
        ): ArrayList<SuggestedWords.SuggestedWordInfo> = arrayListOf()

        override fun isInDictionary(word: String?): Boolean = false

        override fun getFrequency(word: String?): Int {
            queriedWord = word
            return 7
        }
    }
}
