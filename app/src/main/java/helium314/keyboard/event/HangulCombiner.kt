// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.event

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.isEmoji
import java.lang.StringBuilder
import java.util.ArrayList

class HangulCombiner(
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 }
) : Combiner {

    private val composingWord = StringBuilder()

    val history: MutableList<HangulSyllable> = mutableListOf()
    private val syllable: HangulSyllable? get() = history.lastOrNull()

    private var lastCheonjiinCycleCode = 0
    private var lastCheonjiinCycleIndex = 0
    private var lastCheonjiinCycleTime = 0L
    private var cheonjiinVowel = 0
    private var pendingCheonjiinDots = 0

    override fun processEvent(previousEvents: ArrayList<Event>?, event: Event): Event {
        processCheonjiinEvent(event)?.let { return it }

        if (event.keyCode == KeyCode.DELETE && pendingCheonjiinDots > 0) {
            pendingCheonjiinDots--
            clearCheonjiinCycle()
            cheonjiinVowel = 0
            return Event.createConsumedEvent(event)
        }
        if (pendingCheonjiinDots > 0 && isEmoji(event.codePoint)) {
            val text = combiningStateFeedback
            reset()
            return createEventChainFromSequence(text, event)
        }
        clearCheonjiinCycle()
        cheonjiinVowel = 0
        if (pendingCheonjiinDots > 0 && !event.isFunctionalKeyEvent && !Character.isWhitespace(event.codePoint))
            finishPendingCheonjiinDots()
        if (event.keyCode == KeyCode.SHIFT || isEmoji(event.codePoint)) return event

        return processHangulEvent(event)
    }

    private fun processHangulEvent(sourceEvent: Event): Event {
        // previously we only used the combiner if codePoint > 0x1100 or codePoint == -1, but looks here it's not necessary
        val event = HangulEventDecoder.decodeSoftwareKeyEvent(sourceEvent)
        if (Character.isWhitespace(event.codePoint)) {
            val text = combiningStateFeedback
            reset()
            return createEventChainFromSequence(text, event)
        } else if (event.isFunctionalKeyEvent) {
            if(event.keyCode == KeyCode.DELETE) {
                return when {
                    history.size == 1 && composingWord.isEmpty() || history.isEmpty() && composingWord.length == 1 -> {
                        reset()
                        Event.createHardwareKeypressEvent(0x20, Constants.CODE_SPACE, 0, event, event.isKeyRepeat)
                    }
                    history.isNotEmpty() -> {
                        history.removeAt(history.lastIndex)
                        Event.createConsumedEvent(event)
                    }
                    composingWord.isNotEmpty() -> {
                        composingWord.deleteCharAt(composingWord.lastIndex)
                        Event.createConsumedEvent(event)
                    }
                    else -> event
                }
            }
            val text = combiningStateFeedback
            reset()
            return createEventChainFromSequence(text, event)
        } else {
            val currentSyllable = syllable ?: HangulSyllable()
            val jamo = HangulJamo.of(event.codePoint)
            if (!event.isCombining || jamo is HangulJamo.NonHangul) {
                composingWord.append(currentSyllable.string)
                composingWord.append(jamo.string)
                history.clear()
            } else {
                when (jamo) {
                    is HangulJamo.Consonant -> {
                        val initial = jamo.toInitial()
                        val final = jamo.toFinal()
                        if (currentSyllable.initial != null && currentSyllable.medial != null) {
                            if (currentSyllable.final == null) {
                                val combination = COMBINATION_TABLE_DUBEOLSIK[currentSyllable.initial.codePoint to (initial?.codePoint ?: -1)]
                                history +=
                                    if (combination != null) {
                                        currentSyllable.copy(initial = HangulJamo.Initial(combination))
                                    } else {
                                        if (final != null) {
                                            currentSyllable.copy(final = final)
                                        } else {
                                            composingWord.append(currentSyllable.string)
                                            history.clear()
                                            HangulSyllable(initial = initial)
                                        }
                                    }
                            } else {
                                val pair = currentSyllable.final.codePoint to (final?.codePoint ?: -1)
                                val combination = COMBINATION_TABLE_DUBEOLSIK[pair]
                                history += if (combination != null) {
                                    currentSyllable.copy(final = HangulJamo.Final(combination, combinationPair = pair))
                                } else {
                                    composingWord.append(currentSyllable.string)
                                    history.clear()
                                    HangulSyllable(initial = initial)
                                }
                            }
                        } else {
                            composingWord.append(currentSyllable.string)
                            history.clear()
                            history += HangulSyllable(initial = initial)
                        }
                    }
                    is HangulJamo.Vowel -> {
                        val medial = jamo.toMedial()
                        if (currentSyllable.final == null) {
                            history +=
                                if (currentSyllable.medial != null) {
                                    val combination = COMBINATION_TABLE_DUBEOLSIK[currentSyllable.medial.codePoint to (medial?.codePoint ?: -1)]
                                    if (combination != null) {
                                        currentSyllable.copy(medial = HangulJamo.Medial(combination))
                                    } else {
                                        composingWord.append(currentSyllable.string)
                                        history.clear()
                                        HangulSyllable(medial = medial)
                                    }
                            } else {
                                currentSyllable.copy(medial = medial)
                            }
                        } else if (currentSyllable.final.combinationPair != null) {
                            val pair = currentSyllable.final.combinationPair

                            history.removeAt(history.lastIndex)
                            val final = HangulJamo.Final(pair.first)
                            history += currentSyllable.copy(final = final)
                            composingWord.append(syllable?.string ?: "")
                            history.clear()
                            val initial = HangulJamo.Final(pair.second).toConsonant()?.toInitial()
                            val newSyllable = HangulSyllable(initial = initial)
                            history += newSyllable
                            history += newSyllable.copy(medial = medial)
                        } else {
                            history.removeAt(history.lastIndex)
                            composingWord.append(syllable?.string ?: "")
                            history.clear()
                            val initial = currentSyllable.final.toConsonant()?.toInitial()
                            val newSyllable = HangulSyllable(initial = initial)
                            history += newSyllable
                            history += newSyllable.copy(medial = medial)
                        }
                    }
                    is HangulJamo.Initial -> {
                        history +=
                            if (currentSyllable.initial != null) {
                                val combination = COMBINATION_TABLE_SEBEOLSIK[currentSyllable.initial.codePoint to jamo.codePoint]
                                if (combination != null && currentSyllable.medial == null && currentSyllable.final == null) {
                                    currentSyllable.copy(initial = HangulJamo.Initial(combination))
                                } else {
                                    composingWord.append(currentSyllable.string)
                                    history.clear()
                                    HangulSyllable(initial = jamo)
                                }
                            } else {
                                currentSyllable.copy(initial = jamo)
                            }
                    }
                    is HangulJamo.Medial -> {
                        history +=
                            if (currentSyllable.medial != null) {
                                val combination = COMBINATION_TABLE_SEBEOLSIK[currentSyllable.medial.codePoint to jamo.codePoint]
                                if (combination != null) {
                                    currentSyllable.copy(medial = HangulJamo.Medial(combination))
                                } else {
                                    composingWord.append(currentSyllable.string)
                                    history.clear()
                                    HangulSyllable(medial = jamo)
                                }
                            } else {
                                currentSyllable.copy(medial = jamo)
                            }
                    }
                    is HangulJamo.Final -> {
                        history +=
                            if (currentSyllable.final != null) {
                                val combination = COMBINATION_TABLE_SEBEOLSIK[currentSyllable.final.codePoint to jamo.codePoint]
                                if (combination != null) {
                                    currentSyllable.copy(final = HangulJamo.Final(combination))
                                } else {
                                    composingWord.append(currentSyllable.string)
                                    history.clear()
                                    HangulSyllable(final = jamo)
                                }
                            } else {
                                currentSyllable.copy(final = jamo)
                            }
                    }
                    // compiler bug? when it's not added, compiler complains that it's missing
                    // but when added, linter (correctly) states it's unreachable anyway
                    is HangulJamo.NonHangul -> Unit
                }
            }
        }

        return Event.createConsumedEvent(event)
    }

    private fun processCheonjiinEvent(event: Event): Event? {
        return when (event.codePoint) {
            CHEONJIIN_VOWEL_I -> processCheonjiinVowel('ㅣ', event)
            CHEONJIIN_VOWEL_DOT -> processCheonjiinVowel(CHEONJIIN_DOT, event)
            CHEONJIIN_VOWEL_EU -> processCheonjiinVowel('ㅡ', event)
            CHEONJIIN_PUNCTUATION -> processCheonjiinPunctuation(event)
            else -> CONSONANT_CYCLES[event.codePoint]?.let { processCheonjiinConsonant(event.codePoint, it, event) }
        }
    }

    private fun processCheonjiinConsonant(code: Int, cycle: IntArray, event: Event): Event {
        if (pendingCheonjiinDots > 0)
            finishPendingCheonjiinDots()
        cheonjiinVowel = 0

        val now = nowMillis()
        val continuesCycle = code == lastCheonjiinCycleCode
                && now - lastCheonjiinCycleTime in 0..CHEONJIIN_CYCLE_TIMEOUT_MS
        lastCheonjiinCycleIndex = if (continuesCycle) (lastCheonjiinCycleIndex + 1) % cycle.size else 0
        if (continuesCycle)
            undoLastHangulInput()
        lastCheonjiinCycleCode = code
        lastCheonjiinCycleTime = now
        return processHangulCodePoint(cycle[lastCheonjiinCycleIndex], event)
    }

    private fun processCheonjiinVowel(stroke: Char, event: Event): Event {
        clearCheonjiinCycle()

        if (pendingCheonjiinDots > 0) {
            if (stroke == CHEONJIIN_DOT) {
                pendingCheonjiinDots = if (pendingCheonjiinDots == 2) 1 else pendingCheonjiinDots + 1
                return Event.createConsumedEvent(event)
            }
            val vowel = when (pendingCheonjiinDots to stroke) {
                1 to 'ㅣ' -> 'ㅓ'
                1 to 'ㅡ' -> 'ㅗ'
                2 to 'ㅣ' -> 'ㅕ'
                2 to 'ㅡ' -> 'ㅛ'
                else -> null
            }
            pendingCheonjiinDots = 0
            if (vowel != null) {
                cheonjiinVowel = vowel.code
                return processHangulCodePoint(vowel.code, event)
            }
        }

        if (cheonjiinVowel == 0 && syllable?.final == null) {
            val exposedVowel = syllable?.medial?.toVowel()?.codePoint ?: 0
            if (VOWEL_TRANSITIONS.keys.any { it.first == exposedVowel })
                cheonjiinVowel = exposedVowel
        }
        VOWEL_TRANSITIONS[cheonjiinVowel to stroke.code]?.let { vowel ->
            undoLastHangulInput()
            cheonjiinVowel = vowel
            return processHangulCodePoint(vowel, event)
        }

        cheonjiinVowel = 0
        return when (stroke) {
            CHEONJIIN_DOT -> {
                pendingCheonjiinDots = 1
                Event.createConsumedEvent(event)
            }
            'ㅣ', 'ㅡ' -> {
                cheonjiinVowel = stroke.code
                processHangulCodePoint(stroke.code, event)
            }
            else -> Event.createConsumedEvent(event)
        }
    }

    private fun processCheonjiinPunctuation(event: Event): Event {
        if (pendingCheonjiinDots > 0)
            finishPendingCheonjiinDots()
        cheonjiinVowel = 0

        val now = nowMillis()
        val continuesCycle = lastCheonjiinCycleCode == CHEONJIIN_PUNCTUATION
                && now - lastCheonjiinCycleTime in 0..CHEONJIIN_CYCLE_TIMEOUT_MS
        lastCheonjiinCycleIndex = if (continuesCycle) (lastCheonjiinCycleIndex + 1) % PUNCTUATION_CYCLE.size else 0
        if (continuesCycle && composingWord.isNotEmpty()) {
            composingWord.deleteCharAt(composingWord.lastIndex)
        } else {
            finishCurrentSyllable()
        }
        composingWord.appendCodePoint(PUNCTUATION_CYCLE[lastCheonjiinCycleIndex])
        lastCheonjiinCycleCode = CHEONJIIN_PUNCTUATION
        lastCheonjiinCycleTime = now
        return Event.createConsumedEvent(event)
    }

    private fun processHangulCodePoint(codePoint: Int, sourceEvent: Event): Event =
        processHangulEvent(Event.createSoftwareKeypressEvent(
            codePoint, Event.NOT_A_KEY_CODE, sourceEvent.metaState, sourceEvent.x, sourceEvent.y, sourceEvent.isKeyRepeat
        ))

    private fun undoLastHangulInput() {
        if (history.isNotEmpty()) {
            history.removeAt(history.lastIndex)
        } else if (composingWord.isNotEmpty()) {
            composingWord.deleteCharAt(composingWord.lastIndex)
        }
    }

    private fun finishCurrentSyllable() {
        composingWord.append(syllable?.string ?: "")
        history.clear()
    }

    private fun finishPendingCheonjiinDots() {
        finishCurrentSyllable()
        repeat(pendingCheonjiinDots) { composingWord.append(CHEONJIIN_DOT) }
        pendingCheonjiinDots = 0
    }

    private fun clearCheonjiinCycle() {
        lastCheonjiinCycleCode = 0
        lastCheonjiinCycleIndex = 0
        lastCheonjiinCycleTime = 0L
    }

    override val combiningStateFeedback: CharSequence
        get() = buildString {
            append(composingWord)
            append(syllable?.string ?: "")
            repeat(pendingCheonjiinDots) { append(CHEONJIIN_DOT) }
        }

    override fun reset() {
        composingWord.setLength(0)
        history.clear()
        clearCheonjiinCycle()
        cheonjiinVowel = 0
        pendingCheonjiinDots = 0
    }

    sealed class HangulJamo {
        abstract val codePoint: Int
        abstract val modern: Boolean
        val string: String get() = codePoint.toChar().toString()
        data class NonHangul(override val codePoint: Int) : HangulJamo() {
            override val modern: Boolean get() = false
        }
        data class Initial(override val codePoint: Int) : HangulJamo() {
            override val modern: Boolean get() = codePoint in 0x1100 .. 0x1112
            val ordinal: Int get() = codePoint - 0x1100
            fun toConsonant(): Consonant? {
                val codePoint = COMPAT_CONSONANTS.getOrNull(CONVERT_INITIALS.indexOf(codePoint.toChar())) ?: return null
                if(codePoint.code == 0) return null
                return Consonant(codePoint.code)
            }
        }
        data class Medial(override val codePoint: Int) : HangulJamo() {
            override val modern: Boolean get() = codePoint in 1161 .. 0x1175
            val ordinal: Int get() = codePoint - 0x1161
            fun toVowel(): Vowel? {
                val codePoint = COMPAT_VOWELS.getOrNull(CONVERT_MEDIALS.indexOf(codePoint.toChar())) ?: return null
                return Vowel(codePoint.code)
            }
        }
        data class Final(override val codePoint: Int, val combinationPair: Pair<Int, Int>? = null) : HangulJamo() {
            override val modern: Boolean get() = codePoint in 0x11a8 .. 0x11c2
            val ordinal: Int get() = codePoint - 0x11a7
            fun toConsonant(): Consonant? {
                val codePoint = COMPAT_CONSONANTS.getOrNull(CONVERT_FINALS.indexOf(codePoint.toChar())) ?: return null
                if(codePoint.code == 0) return null
                return Consonant(codePoint.code)
            }
        }
        data class Consonant(override val codePoint: Int) : HangulJamo() {
            override val modern: Boolean get() = codePoint in 0x3131 .. 0x314e
            val ordinal: Int get() = codePoint - 0x3131
            fun toInitial(): Initial? {
                val codePoint = CONVERT_INITIALS.getOrNull(COMPAT_CONSONANTS.indexOf(codePoint.toChar())) ?: return null
                if(codePoint.code == 0) return null
                return Initial(codePoint.code)
            }
            fun toFinal(): Final? {
                val codePoint = CONVERT_FINALS.getOrNull(COMPAT_CONSONANTS.indexOf(codePoint.toChar())) ?: return null
                if(codePoint.code == 0) return null
                return Final(codePoint.code)
            }
        }
        data class Vowel(override val codePoint: Int) : HangulJamo() {
            override val modern: Boolean get() = codePoint in 0x314f .. 0x3163
            val ordinal: Int get() = codePoint - 0x314f1
            fun toMedial(): Medial? {
                val codePoint = CONVERT_MEDIALS.getOrNull(COMPAT_VOWELS.indexOf(codePoint.toChar())) ?: return null
                if(codePoint.code == 0) return null
                return Medial(codePoint.code)
            }
        }
        companion object {
            const val COMPAT_CONSONANTS = "ㄱㄲㄳㄴㄵㄶㄷㄸㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅃㅄㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
            const val COMPAT_VOWELS = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
            const val CONVERT_INITIALS = "ᄀᄁ\u0000ᄂ\u0000\u0000ᄃᄄᄅ\u0000\u0000\u0000\u0000\u0000\u0000\u0000ᄆᄇᄈ\u0000ᄉᄊᄋᄌᄍᄎᄏᄐᄑᄒ"
            const val CONVERT_MEDIALS = "ᅡᅢᅣᅤᅥᅦᅧᅨᅩᅪᅫᅬᅭᅮᅯᅰᅱᅲᅳᅴᅵ"
            const val CONVERT_FINALS = "ᆨᆩᆪᆫᆬᆭᆮ\u0000ᆯᆰᆱᆲᆳᆴᆵᆶᆷᆸ\u0000ᆹᆺᆻᆼᆽ\u0000ᆾᆿᇀᇁᇂ"
            fun of(codePoint: Int): HangulJamo {
                return when(codePoint) {
                    in 0x3131 .. 0x314e -> Consonant(codePoint)
                    in 0x314f .. 0x3163 -> Vowel(codePoint)
                    in 0x1100 .. 0x115f -> Initial(codePoint)
                    in 0x1160 .. 0x11a7 -> Medial(codePoint)
                    in 0x11a8 .. 0x11ff -> Final(codePoint)
                    else -> NonHangul(codePoint)
                }
            }
        }
    }

    data class HangulSyllable(
            val initial: HangulJamo.Initial? = null,
            val medial: HangulJamo.Medial? = null,
            val final: HangulJamo.Final? = null
    ) {
        val combinable: Boolean get() = (initial?.modern ?: false) && (medial?.modern ?: false) && (final?.modern ?: true)
        val combined: String get() = (0xac00 + (initial?.ordinal ?: 0) * 21 * 28
                + (medial?.ordinal ?: 0) * 28
                + (final?.ordinal ?: 0)).toChar().toString()
        val uncombined: String get() = (initial?.string ?: "") + (medial?.string ?: "") + (final?.string ?: "")
        val uncombinedCompat: String get() = (initial?.toConsonant()?.string ?: "") +
                (medial?.toVowel()?.string ?: "") + (final?.toConsonant()?.string ?: "")
        val string: String get() = if (this.combinable) this.combined else this.uncombinedCompat
    }

    companion object {
        const val CHEONJIIN_VOWEL_I = 0xe000
        const val CHEONJIIN_VOWEL_DOT = 0xe001
        const val CHEONJIIN_VOWEL_EU = 0xe002
        const val CHEONJIIN_CONSONANT_GIYEOK = 0xe010
        const val CHEONJIIN_CONSONANT_NIEUN = 0xe011
        const val CHEONJIIN_CONSONANT_DIGEUT = 0xe012
        const val CHEONJIIN_CONSONANT_BIEUP = 0xe013
        const val CHEONJIIN_CONSONANT_SIOT = 0xe014
        const val CHEONJIIN_CONSONANT_JIEUT = 0xe015
        const val CHEONJIIN_CONSONANT_IEUNG = 0xe016
        const val CHEONJIIN_PUNCTUATION = 0xe020

        private const val CHEONJIIN_CYCLE_TIMEOUT_MS = 1500L
        private const val CHEONJIIN_DOT = 'ㆍ'

        private val CONSONANT_CYCLES = mapOf(
            CHEONJIIN_CONSONANT_GIYEOK to intArrayOf('ㄱ'.code, 'ㅋ'.code, 'ㄲ'.code),
            CHEONJIIN_CONSONANT_NIEUN to intArrayOf('ㄴ'.code, 'ㄹ'.code),
            CHEONJIIN_CONSONANT_DIGEUT to intArrayOf('ㄷ'.code, 'ㅌ'.code, 'ㄸ'.code),
            CHEONJIIN_CONSONANT_BIEUP to intArrayOf('ㅂ'.code, 'ㅍ'.code, 'ㅃ'.code),
            CHEONJIIN_CONSONANT_SIOT to intArrayOf('ㅅ'.code, 'ㅎ'.code, 'ㅆ'.code),
            CHEONJIIN_CONSONANT_JIEUT to intArrayOf('ㅈ'.code, 'ㅊ'.code, 'ㅉ'.code),
            CHEONJIIN_CONSONANT_IEUNG to intArrayOf('ㅇ'.code, 'ㅁ'.code),
        )
        private val PUNCTUATION_CYCLE = intArrayOf('.'.code, ','.code, '?'.code, '!'.code)
        private val VOWEL_TRANSITIONS = mapOf(
            'ㅣ'.code to CHEONJIIN_DOT.code to 'ㅏ'.code,
            'ㅏ'.code to CHEONJIIN_DOT.code to 'ㅑ'.code,
            'ㅏ'.code to 'ㅣ'.code to 'ㅐ'.code,
            'ㅑ'.code to 'ㅣ'.code to 'ㅒ'.code,
            'ㅓ'.code to 'ㅣ'.code to 'ㅔ'.code,
            'ㅓ'.code to CHEONJIIN_DOT.code to 'ㅕ'.code,
            'ㅕ'.code to 'ㅣ'.code to 'ㅖ'.code,
            'ㅗ'.code to 'ㅣ'.code to 'ㅚ'.code,
            'ㅗ'.code to CHEONJIIN_DOT.code to 'ㅛ'.code,
            'ㅚ'.code to CHEONJIIN_DOT.code to 'ㅘ'.code,
            'ㅘ'.code to 'ㅣ'.code to 'ㅙ'.code,
            'ㅡ'.code to 'ㅣ'.code to 'ㅢ'.code,
            'ㅡ'.code to CHEONJIIN_DOT.code to 'ㅜ'.code,
            'ㅜ'.code to 'ㅣ'.code to 'ㅟ'.code,
            'ㅜ'.code to CHEONJIIN_DOT.code to 'ㅠ'.code,
            'ㅠ'.code to 'ㅣ'.code to 'ㅝ'.code,
            'ㅝ'.code to 'ㅣ'.code to 'ㅞ'.code,
        )

        val COMBINATION_TABLE_DUBEOLSIK = mapOf<Pair<Int, Int>, Int>(
                0x1169 to 0x1161 to 0x116a,
                0x1169 to 0x1162 to 0x116b,
                0x1169 to 0x1175 to 0x116c,
                0x116e to 0x1165 to 0x116f,
                0x116e to 0x1166 to 0x1170,
                0x116e to 0x1175 to 0x1171,
                0x1173 to 0x1175 to 0x1174,

                0x11a8 to 0x11ba to 0x11aa,
                0x11ab to 0x11bd to 0x11ac,
                0x11ab to 0x11c2 to 0x11ad,
                0x11af to 0x11a8 to 0x11b0,
                0x11af to 0x11b7 to 0x11b1,
                0x11af to 0x11b8 to 0x11b2,
                0x11af to 0x11ba to 0x11b3,
                0x11af to 0x11c0 to 0x11b4,
                0x11af to 0x11c1 to 0x11b5,
                0x11af to 0x11c2 to 0x11b6,
                0x11b8 to 0x11ba to 0x11b9
        )
        val COMBINATION_TABLE_SEBEOLSIK = mapOf<Pair<Int, Int>, Int>(
                0x1100 to 0x1100 to 0x1101,	// ㄲ
                0x1103 to 0x1103 to 0x1104,	// ㄸ
                0x1107 to 0x1107 to 0x1108,	// ㅃ
                0x1109 to 0x1109 to 0x110a,	// ㅆ
                0x110c to 0x110c to 0x110d,	// ㅉ

                0x1169 to 0x1161 to 0x116a,	// ㅘ
                0x1169 to 0x1162 to 0x116b,	// ㅙ
                0x1169 to 0x1175 to 0x116c,	// ㅚ
                0x116e to 0x1165 to 0x116f,	// ㅝ
                0x116e to 0x1166 to 0x1170,	// ㅞ
                0x116e to 0x1175 to 0x1171,	// ㅟ
                0x1173 to 0x1175 to 0x1174,	// ㅢ

                0x11a8 to 0x11a8 to 0x11a9,	// ㄲ
                0x11a8 to 0x11ba to 0x11aa,	// ㄳ
                0x11ab to 0x11bd to 0x11ac,	// ㄵ
                0x11ab to 0x11c2 to 0x11ad,	// ㄶ
                0x11af to 0x11a8 to 0x11b0,	// ㄺ
                0x11af to 0x11b7 to 0x11b1,	// ㄻ
                0x11af to 0x11b8 to 0x11b2,	// ㄼ
                0x11af to 0x11ba to 0x11b3,	// ㄽ
                0x11af to 0x11c0 to 0x11b4,	// ㄾ
                0x11af to 0x11c1 to 0x11b5,	// ㄿ
                0x11af to 0x11c2 to 0x11b6,	// ㅀ
                0x11b8 to 0x11ba to 0x11b9,	// ㅄ
                0x11ba to 0x11ba to 0x11bb	// ㅆ
        )
        private fun createEventChainFromSequence(text: CharSequence, originalEvent: Event): Event {
            return Event.createSoftwareTextEvent(text, KeyCode.MULTIPLE_CODE_POINTS, originalEvent)
        }
    }

}
