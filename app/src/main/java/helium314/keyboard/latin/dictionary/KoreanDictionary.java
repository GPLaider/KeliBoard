// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin.dictionary;

import helium314.keyboard.latin.NgramContext;
import helium314.keyboard.latin.SuggestedWords;
import helium314.keyboard.latin.common.ComposedData;
import helium314.keyboard.latin.makedict.WordProperty;
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion;

import java.text.Normalizer;
import java.util.ArrayList;

/* The recommended Korean dictionary stores precomposed Hangul syllables. */
public class KoreanDictionary extends Dictionary {

    private final Dictionary mDictionary;

    public KoreanDictionary(Dictionary dictionary) {
        super(dictionary.mDictType, dictionary.mLocale);
        mDictionary = dictionary;
    }

    private String processInput(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFC);
    }

    private String processOutput(String output) {
        return Normalizer.normalize(output, Normalizer.Form.NFC);
    }

    @Override
    public ArrayList<SuggestedWords.SuggestedWordInfo> getSuggestions(ComposedData composedData,
                  NgramContext ngramContext, long proximityInfoHandle, SettingsValuesForSuggestion settingsValuesForSuggestion,
                  int sessionId, float weightForLocale, float[] inOutWeightOfLangModelVsSpatialModel) {
        composedData = new ComposedData(composedData.mInputPointers,
                composedData.mIsBatchMode, processInput(composedData.mTypedWord));
        ArrayList<SuggestedWords.SuggestedWordInfo> suggestions = mDictionary.getSuggestions(composedData,
                ngramContext, proximityInfoHandle, settingsValuesForSuggestion, sessionId,
                weightForLocale, inOutWeightOfLangModelVsSpatialModel);
        ArrayList<SuggestedWords.SuggestedWordInfo> result = new ArrayList<>();
        for (SuggestedWords.SuggestedWordInfo info : suggestions) {
            result.add(new SuggestedWords.SuggestedWordInfo(processOutput(info.mWord), info.mPrevWordsContext,
                    info.mScore, info.mKindAndFlags, info.mSourceDict, info.mIndexOfTouchPointOfSecondWord, info.mAutoCommitFirstWordConfidence));
        }
        return result;
    }

    @Override
    public boolean isInDictionary(String word) {
        return mDictionary.isInDictionary(processInput(word));
    }

    @Override
    public int getFrequency(String word) {
        return mDictionary.getFrequency(processInput(word));
    }

    @Override
    public int getMaxFrequencyOfExactMatches(String word) {
        return mDictionary.getMaxFrequencyOfExactMatches(processInput(word));
    }

    @Override
    public WordProperty getWordProperty(String word, boolean isBeginningOfSentence) {
        return mDictionary.getWordProperty(processInput(word), isBeginningOfSentence);
    }

    @Override
    protected boolean same(char[] word, int length, String typedWord) {
        word = processInput(new String(word)).toCharArray();
        typedWord = processInput(typedWord);
        return mDictionary.same(word, length, typedWord);
    }

    @Override
    public void close() {
        mDictionary.close();
    }

    @Override
    public void onFinishInput() {
        mDictionary.onFinishInput();
    }

    @Override
    public boolean isInitialized() {
        return mDictionary.isInitialized();
    }

    @Override
    public boolean shouldAutoCommit(SuggestedWords.SuggestedWordInfo candidate) {
        return mDictionary.shouldAutoCommit(candidate);
    }

    @Override
    public boolean isUserSpecific() {
        return mDictionary.isUserSpecific();
    }
}
