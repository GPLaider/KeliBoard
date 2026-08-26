// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.event

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import kotlin.test.Test
import kotlin.test.assertEquals

class HangulCombinerTest {
    @Test fun cheonjiinSpaceSeparatesAnActiveConsonantCycle() {
        var now = 1_000L
        val combiner = HangulCombiner { now }

        fun tap(codePoint: Int): Event {
            return combiner.processEvent(arrayListOf(), Event.createSoftwareKeypressEvent(
                codePoint, Event.NOT_A_KEY_CODE, 0, 0, 0, false
            )).also { now += 100 }
        }

        fun typeKieuk() {
            tap(HangulCombiner.CHEONJIIN_CONSONANT_GIYEOK)
            tap(HangulCombiner.CHEONJIIN_CONSONANT_GIYEOK)
        }

        typeKieuk()
        val separator = tap(' '.code)
        assertEquals("", separator.nextEvent?.textToCommit)
        typeKieuk()
        assertEquals("ㅋㅋ", separator.text.toString() + combiner.combiningStateFeedback)
        now += 1_600
        val actualSpace = tap(' '.code)
        assertEquals("ㅋ", actualSpace.text)
        assertEquals(" ", actualSpace.nextEvent?.textToCommit)
    }

    @Test fun cheonjiinComposesModernVowelsAndCyclesKeys() {
        var now = 1_000L
        val combiner = HangulCombiner { now }

        fun tap(codePoint: Int) {
            combiner.processEvent(arrayListOf(), Event.createSoftwareKeypressEvent(
                codePoint, Event.NOT_A_KEY_CODE, 0, 0, 0, false
            ))
            now += 100
        }
        fun delete() {
            combiner.processEvent(arrayListOf(), Event.createSoftwareKeypressEvent(
                Event.NOT_A_CODE_POINT, KeyCode.DELETE, 0, 0, 0, false
            ))
        }

        val vowelKeys = mapOf(
            '1' to HangulCombiner.CHEONJIIN_VOWEL_I,
            '2' to HangulCombiner.CHEONJIIN_VOWEL_DOT,
            '3' to HangulCombiner.CHEONJIIN_VOWEL_EU,
        )
        mapOf(
            "1" to "ㅣ", "12" to "ㅏ", "121" to "ㅐ", "122" to "ㅑ", "1221" to "ㅒ",
            "21" to "ㅓ", "211" to "ㅔ", "221" to "ㅕ", "2211" to "ㅖ",
            "23" to "ㅗ", "231" to "ㅚ", "2312" to "ㅘ", "23121" to "ㅙ", "223" to "ㅛ",
            "3" to "ㅡ", "31" to "ㅢ", "32" to "ㅜ", "321" to "ㅟ", "322" to "ㅠ",
            "3221" to "ㅝ", "32211" to "ㅞ",
        ).forEach { (sequence, expected) ->
            combiner.reset()
            sequence.forEach { tap(vowelKeys.getValue(it)) }
            assertEquals(expected, combiner.combiningStateFeedback, sequence)
        }

        combiner.reset()
        tap(HangulCombiner.CHEONJIIN_CONSONANT_SIOT)
        tap(HangulCombiner.CHEONJIIN_CONSONANT_SIOT)
        tap(HangulCombiner.CHEONJIIN_VOWEL_I)
        tap(HangulCombiner.CHEONJIIN_VOWEL_DOT)
        tap(HangulCombiner.CHEONJIIN_CONSONANT_NIEUN)
        tap(HangulCombiner.CHEONJIIN_CONSONANT_GIYEOK)
        tap(HangulCombiner.CHEONJIIN_VOWEL_EU)
        tap(HangulCombiner.CHEONJIIN_CONSONANT_NIEUN)
        tap(HangulCombiner.CHEONJIIN_CONSONANT_NIEUN)
        assertEquals("한글", combiner.combiningStateFeedback)

        combiner.reset()
        repeat(3) { tap(HangulCombiner.CHEONJIIN_CONSONANT_GIYEOK) }
        assertEquals("ㄲ", combiner.combiningStateFeedback)
        now += 1_600
        tap(HangulCombiner.CHEONJIIN_CONSONANT_GIYEOK)
        assertEquals("ㄲㄱ", combiner.combiningStateFeedback)

        combiner.reset()
        repeat(4) { tap(HangulCombiner.CHEONJIIN_PUNCTUATION) }
        assertEquals("!", combiner.combiningStateFeedback)
        now += 1_600
        tap(HangulCombiner.CHEONJIIN_PUNCTUATION)
        assertEquals("!.", combiner.combiningStateFeedback)

        combiner.reset()
        tap(HangulCombiner.CHEONJIIN_VOWEL_DOT)
        tap(HangulCombiner.CHEONJIIN_VOWEL_DOT)
        assertEquals("ㆍㆍ", combiner.combiningStateFeedback)
        delete()
        assertEquals("ㆍ", combiner.combiningStateFeedback)

        mapOf(
            "않아" to intArrayOf(
                HangulCombiner.CHEONJIIN_CONSONANT_IEUNG,
                HangulCombiner.CHEONJIIN_VOWEL_I, HangulCombiner.CHEONJIIN_VOWEL_DOT,
                HangulCombiner.CHEONJIIN_CONSONANT_NIEUN,
                HangulCombiner.CHEONJIIN_CONSONANT_SIOT, HangulCombiner.CHEONJIIN_CONSONANT_SIOT,
                HangulCombiner.CHEONJIIN_CONSONANT_IEUNG,
                HangulCombiner.CHEONJIIN_VOWEL_I, HangulCombiner.CHEONJIIN_VOWEL_DOT,
            ),
            "잖아" to intArrayOf(
                HangulCombiner.CHEONJIIN_CONSONANT_JIEUT,
                HangulCombiner.CHEONJIIN_VOWEL_I, HangulCombiner.CHEONJIIN_VOWEL_DOT,
                HangulCombiner.CHEONJIIN_CONSONANT_NIEUN,
                HangulCombiner.CHEONJIIN_CONSONANT_SIOT, HangulCombiner.CHEONJIIN_CONSONANT_SIOT,
                HangulCombiner.CHEONJIIN_CONSONANT_IEUNG,
                HangulCombiner.CHEONJIIN_VOWEL_I, HangulCombiner.CHEONJIIN_VOWEL_DOT,
            ),
            "삶" to intArrayOf(
                HangulCombiner.CHEONJIIN_CONSONANT_SIOT,
                HangulCombiner.CHEONJIIN_VOWEL_I, HangulCombiner.CHEONJIIN_VOWEL_DOT,
                HangulCombiner.CHEONJIIN_CONSONANT_NIEUN, HangulCombiner.CHEONJIIN_CONSONANT_NIEUN,
                HangulCombiner.CHEONJIIN_CONSONANT_IEUNG, HangulCombiner.CHEONJIIN_CONSONANT_IEUNG,
            ),
            "핥" to intArrayOf(
                HangulCombiner.CHEONJIIN_CONSONANT_SIOT, HangulCombiner.CHEONJIIN_CONSONANT_SIOT,
                HangulCombiner.CHEONJIIN_VOWEL_I, HangulCombiner.CHEONJIIN_VOWEL_DOT,
                HangulCombiner.CHEONJIIN_CONSONANT_NIEUN, HangulCombiner.CHEONJIIN_CONSONANT_NIEUN,
                HangulCombiner.CHEONJIIN_CONSONANT_DIGEUT, HangulCombiner.CHEONJIIN_CONSONANT_DIGEUT,
            ),
        ).forEach { (expected, sequence) ->
            combiner.reset()
            sequence.forEach { tap(it) }
            assertEquals(expected, combiner.combiningStateFeedback, expected)
        }
    }
}
