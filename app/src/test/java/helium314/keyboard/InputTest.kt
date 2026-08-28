package helium314.keyboard

import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.InputType
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import helium314.keyboard.keyboard.KeyboardElement
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsSubtype
import helium314.keyboard.latin.utils.SubtypeUtilsAdditional
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// todo: expand with swipe & touch stuff
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowInputMethodService::class,
    ShadowLooper::class,
])
class InputTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val keyboardSwitcher = KeyboardSwitcher.getInstance()
    init {
        ShadowLog.setupLogging()
        ShadowLog.stream = System.out
        // create keyboardView
        keyboardSwitcher.onCreateInputView(latinIME, true)
        // create and set keyboard
        keyboardSwitcher.reloadMainKeyboard()
    }

    @Test fun pressShift() {
        assertEquals(KeyboardElement.ALPHABET, keyboardSwitcher.keyboard?.mId?.element)
        touchKey(KeyCode.SHIFT, MotionEvent.ACTION_DOWN)
        assertEquals(KeyboardElement.ALPHABET_MANUAL_SHIFTED, keyboardSwitcher.keyboard?.mId?.element)
    }

    @Test fun keyInput() {
        touchKey('a'.code, MotionEvent.ACTION_DOWN)
        touchKey('a'.code, MotionEvent.ACTION_UP)
        assertEquals("a", ShadowInputMethodService.text)
    }

    @Test fun holdShift() {
        touchKey(KeyCode.SHIFT, MotionEvent.ACTION_DOWN)
        assertEquals(KeyboardElement.ALPHABET_MANUAL_SHIFTED, keyboardSwitcher.keyboard?.mId?.element)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks() // this doesn't actually wait, just results in handling the delayed message immediately
        assertEquals(KeyboardElement.ALPHABET_SHIFT_LOCKED, keyboardSwitcher.keyboard?.mId?.element)
    }

    @Test fun slidingInput() {
        touchKey(KeyCode.SHIFT, MotionEvent.ACTION_DOWN)
        touchKey('F'.code, MotionEvent.ACTION_MOVE)
        touchKey('F'.code, MotionEvent.ACTION_UP)
        assertEquals("F", ShadowInputMethodService.text)
        assertEquals(KeyboardElement.ALPHABET, keyboardSwitcher.keyboard?.mId?.element)
    }

    @Test fun endCapsLockWithShift() {
        // keyboardSwitcher.setAlphabetShiftLockedKeyboard() isn't enough because it doesn't set the fully correct state
        touchKey(KeyCode.SHIFT, MotionEvent.ACTION_DOWN)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        touchKey(KeyCode.SHIFT, MotionEvent.ACTION_UP)

        // need to set event time to prevent mTouchNoiseThresholdTime thing triggering (todo: should be done automatically in the test)
        touchKey(KeyCode.SHIFT, MotionEvent.ACTION_DOWN, 50L)
        touchKey(KeyCode.SHIFT, MotionEvent.ACTION_UP, 50L)
        assertEquals(KeyboardElement.ALPHABET, keyboardSwitcher.keyboard?.mId?.element)
    }

    @Test fun slidingInputFromCapsLock() {
        touchKey(KeyCode.SHIFT, MotionEvent.ACTION_DOWN)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        touchKey(KeyCode.SHIFT, MotionEvent.ACTION_UP)

        touchKey(KeyCode.SHIFT, MotionEvent.ACTION_DOWN, 50L)
        assertEquals(KeyboardElement.ALPHABET, keyboardSwitcher.keyboard?.mId?.element)
        touchKey('f'.code, MotionEvent.ACTION_MOVE)
        touchKey('f'.code, MotionEvent.ACTION_UP)
        assertEquals("f", ShadowInputMethodService.text)
        assertEquals(KeyboardElement.ALPHABET_SHIFT_LOCKED, keyboardSwitcher.keyboard?.mId?.element)
    }

    @Test fun securePasswordAndLockscreenUseShiftableQwerty() {
        val keyguard = latinIME.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_FLAG_FORCE_ASCII
        }
        val korean = SubtypeUtilsAdditional.createEmojiCapableAdditionalSubtype(
            Locale.KOREAN, "korean_cheonjiin", false
        )
        val fallback = SettingsSubtype.fallbackSubtype.toAdditionalSubtype()

        try {
            RichInputMethodManager.forceSubtype(korean)
            ShadowInputMethodService.currentInputType = editorInfo.inputType
            ShadowInputMethodService.currentImeOptions = editorInfo.imeOptions
            shadowOf(keyguard).setIsDeviceLocked(false)
            latinIME.onStartInputView(editorInfo, false)
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            assertFalse(Settings.getValues().mIsLocked)
            assertEquals(Locale.US, keyboardSwitcher.keyboard?.mId?.subtype?.locale)
            assertNotNull(keyboardSwitcher.keyboard?.getKey(KeyCode.SHIFT))

            shadowOf(keyguard).setIsDeviceLocked(true)
            latinIME.onStartInputView(editorInfo, true)
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            assertTrue(Settings.getValues().mIsLocked)
            val keyboard = keyboardSwitcher.keyboard!!
            assertTrue(keyboard.mId.deviceLocked)
            assertTrue(keyboard.mId.imeOptions and EditorInfo.IME_FLAG_FORCE_ASCII != 0)
            assertEquals(Locale.US, keyboard.mId.subtype.locale)
            assertNotNull(keyboard.getKey(KeyCode.SHIFT))
            touchKey(KeyCode.SHIFT, MotionEvent.ACTION_DOWN)
            assertEquals(KeyboardElement.ALPHABET_MANUAL_SHIFTED, keyboardSwitcher.keyboard?.mId?.element)
        } finally {
            RichInputMethodManager.forceSubtype(fallback)
            shadowOf(keyguard).setIsDeviceLocked(false)
        }
    }

    @Test fun copiedTextImmediatelyAppearsAndPastesFromSuggestionStrip() {
        val prefs = latinIME.prefs()
        val historyEnabled = prefs.getBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, true)
        val clipboard = latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val root = keyboardSwitcher.stripContainer.rootView
        val suggestions = root.findViewById<ViewGroup>(R.id.suggestions_strip)

        try {
            prefs.edit().putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, false).apply()
            latinIME.onStartInputView(EditorInfo(), false)
            latinIME.updateSuggestionStripView(root)
            clipboard.clearPrimaryClip()
            latinIME.setNeutralSuggestionStrip()

            val clip = ClipData.newPlainText("copied text", "바로 붙여넣기")
            clipboard.setPrimaryClip(clip)
            // Robolectric 4.16 stamps clips with uptime, while Android uses wall time.
            ReflectionHelpers.callInstanceMethod<Any?>(
                clip.description,
                "setTimestamp",
                ReflectionHelpers.ClassParameter.from(Long::class.javaPrimitiveType!!, System.currentTimeMillis())
            )
            latinIME.clipboardHistoryManager.onPrimaryClipChanged()
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            assertFalse(Settings.getValues().mClipboardHistoryEnabled)
            val suggestion = suggestions.findViewById<TextView>(R.id.clipboard_suggestion_text)
            assertEquals("바로 붙여넣기", suggestion?.text?.toString())
            suggestion!!.performClick()
            assertEquals("바로 붙여넣기", ShadowInputMethodService.text)
        } finally {
            prefs.edit().putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, historyEnabled).apply()
        }
    }

    private fun touchKey(code: Int, action: Int, eventTime: Long = 0L) {
        val kb = keyboardSwitcher.keyboard!!
        val key = kb.getKey(code)!!
        val x = key.x + key.height / 2
        val y = key.y + key.width / 2
        val me = MotionEvent.obtain( // todo: there are many more parameters, also related to pointers
            0L,
            eventTime,
            action,
            x.toFloat(),
            y.toFloat(),
            0
        )
        keyboardSwitcher.mainKeyboardView.onTouchEvent(me)
    }

    @BeforeTest
    fun reset() {
        keyboardSwitcher.reloadMainKeyboard()
        ShadowInputMethodService.reset()
    }
}
