package helium314.keyboard

import android.Manifest
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentProvider
import android.content.Context
import android.content.ContextWrapper
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.MatrixCursor
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.TextView
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.KeyboardElement
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.keyboard.emoji.EmojiSearchActivity
import helium314.keyboard.keyboard.internal.keyboard_parser.LayoutParser
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.ClipboardHistoryManager
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsSubtype
import helium314.keyboard.latin.suggestions.SuggestionStripView
import helium314.keyboard.latin.utils.LayoutType
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.SubtypeUtilsAdditional
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.ToolbarMode
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    // https://github.com/HeliBorg/HeliBoard/issues/2420
    @Test fun shiftModifiesNavigationAndCtrlShortcut() {
        val listener = latinIME.mKeyboardActionListener
        val clipboard = latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("copied text", "paste"))
        try {
            listener.onCodeInput(KeyCode.CTRL, 0, 0, false)
            touchKey(KeyCode.SHIFT, MotionEvent.ACTION_DOWN)
            touchKey(KeyCode.SHIFT, MotionEvent.ACTION_UP)
            listener.onCodeInput('V'.code, 0, 0, false)
            assertEquals("paste", ShadowInputMethodService.text)
        } finally {
            clipboard.clearPrimaryClip()
        }

        assertTrue(keyboardSwitcher.keyboard!!.mId.element.isAlphabetShiftedManually)
        listener.onCodeInput(KeyCode.ARROW_RIGHT, 0, 0, false)
        assertTrue(ShadowInputMethodService.lastKeyEvent!!.metaState and KeyEvent.META_SHIFT_ON != 0)

        listener.onHorizontalSpaceSwipe(1)
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, ShadowInputMethodService.lastKeyEvent!!.keyCode)
        assertTrue(ShadowInputMethodService.lastKeyEvent!!.metaState and KeyEvent.META_SHIFT_ON != 0)
    }

    // https://github.com/HeliBorg/HeliBoard/issues/2335
    @Test fun spaceSwipeFollowsNearbyTextDirectionInsteadOfKeyboardLanguage() {
        val fallback = SettingsSubtype.fallbackSubtype.toAdditionalSubtype()
        val arabic = SubtypeUtilsAdditional.createEmojiCapableAdditionalSubtype(
            Locale.forLanguageTag("ar"), "arabic", false
        )
        try {
            RichInputMethodManager.forceSubtype(arabic)
            ShadowInputMethodService.text = "abc"
            ShadowInputMethodService.selectionStart = 1
            ShadowInputMethodService.selectionEnd = 1
            val editorInfo = EditorInfo().apply {
                inputType = InputType.TYPE_CLASS_TEXT
                initialSelStart = 1
                initialSelEnd = 1
            }
            latinIME.onStartInputView(editorInfo, false)
            latinIME.onUpdateSelection(1, 1, 1, 1, -1, -1)
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            latinIME.mKeyboardActionListener.onHorizontalSpaceSwipe(1)

            assertEquals(2, ShadowInputMethodService.selectionStart)
            assertEquals(2, ShadowInputMethodService.selectionEnd)

            ShadowInputMethodService.text = "ابت"
            ShadowInputMethodService.selectionStart = 1
            ShadowInputMethodService.selectionEnd = 1
            latinIME.onUpdateSelection(2, 2, 1, 1, -1, -1)
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            latinIME.mKeyboardActionListener.onHorizontalSpaceSwipe(1)
            assertEquals(0, ShadowInputMethodService.selectionStart)
            assertEquals(0, ShadowInputMethodService.selectionEnd)
        } finally {
            RichInputMethodManager.forceSubtype(fallback)
            keyboardSwitcher.reloadMainKeyboard()
        }
    }

    @Test fun alphaKeyReturnsFromExternallyOpenedEmojiKeyboard() {
        keyboardSwitcher.setEmojiKeyboard()
        assertEquals(View.VISIBLE, keyboardSwitcher.emojiPalettesView.visibility)

        latinIME.mKeyboardActionListener.onCodeInput(KeyCode.ALPHA, 0, 0, false)

        assertEquals(View.GONE, keyboardSwitcher.emojiPalettesView.visibility)
        assertEquals(KeyboardElement.ALPHABET, keyboardSwitcher.keyboard?.mId?.element)
    }

    // https://github.com/HeliBorg/HeliBoard/issues/2277
    @Test fun incognitoLayoutKeyShowsEnabledState() {
        val prefs = latinIME.prefs()
        val oldIncognito = prefs.getBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, false)
        val originalView = keyboardSwitcher.mainKeyboardView
        val keyboardView = Mockito.mock(MainKeyboardView::class.java)
        try {
            prefs.edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, false).commit()
            ReflectionHelpers.setField(keyboardSwitcher, "mKeyboardView", keyboardView)

            latinIME.mKeyboardActionListener.onCodeInput(
                KeyCode.TOGGLE_INCOGNITO_MODE, 0, 0, false
            )

            Mockito.verify(keyboardView)
                .updateLockState(KeyCode.TOGGLE_INCOGNITO_MODE, true)

            latinIME.mKeyboardActionListener.onCodeInput(
                KeyCode.TOGGLE_INCOGNITO_MODE, 0, 0, false
            )
            Mockito.verify(keyboardView)
                .updateLockState(KeyCode.TOGGLE_INCOGNITO_MODE, false)
        } finally {
            ReflectionHelpers.setField(keyboardSwitcher, "mKeyboardView", originalView)
            prefs.edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, oldIncognito).commit()
        }
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

    @Test fun keyboardStateSelectorDoesNotRepeatChangedAction() {
        // Windows strips trailing dots from file names, so don't use LayoutUtilsCustom.getLayoutName here.
        val layoutName = "custom.state-selector"
        val layoutFile = LayoutUtilsCustom.getLayoutFile(layoutName, LayoutType.FUNCTIONAL, latinIME)
        val subtype = SettingsSubtype.fallbackSubtype.withLayout(LayoutType.FUNCTIONAL, layoutName).toAdditionalSubtype()
        val defaultLayout = latinIME.assets.open("layouts/functional/functional_keys.json").bufferedReader().use { it.readText() }
        val selector = """{ "${'$'}": "keyboard_state_selector",
              "moreSymbols": { "label": "numpad", "width": 0.15, "type": "function" },
              "default": { "label": "shift", "width": 0.15 } }"""

        try {
            layoutFile.writeText(defaultLayout.replace("""{ "label": "shift", "width": 0.15 }""", selector))
            LayoutUtilsCustom.onLayoutFileChanged()
            RichInputMethodManager.forceSubtype(subtype)
            LayoutParser.clearCache()
            keyboardSwitcher.reloadMainKeyboard()

            touchKey(KeyCode.SYMBOL_ALPHA, MotionEvent.ACTION_DOWN, 1_000L)
            touchKey(KeyCode.SYMBOL_ALPHA, MotionEvent.ACTION_UP, 1_001L)
            assertEquals(KeyboardElement.SYMBOLS, keyboardSwitcher.keyboard?.mId?.element)

            val shift = keyboardSwitcher.keyboard!!.getKey(KeyCode.SHIFT)!!
            val x = shift.x + shift.width / 2
            val y = shift.y + shift.height / 2
            touchAt(x, y, MotionEvent.ACTION_DOWN, 2_000L)
            assertEquals(KeyboardElement.SYMBOLS_SHIFTED, keyboardSwitcher.keyboard?.mId?.element)
            assertEquals(KeyCode.NUMPAD, keyboardSwitcher.keyboard?.sortedKeys?.firstOrNull { it.isOnKey(x, y) }?.code)
            touchAt(x, y, MotionEvent.ACTION_UP, 2_001L)
            assertEquals(KeyboardElement.SYMBOLS_SHIFTED, keyboardSwitcher.keyboard?.mId?.element)
        } finally {
            RichInputMethodManager.forceSubtype(SettingsSubtype.fallbackSubtype.toAdditionalSubtype())
            layoutFile.delete()
            LayoutUtilsCustom.onLayoutFileChanged()
            LayoutParser.clearCache()
            keyboardSwitcher.reloadMainKeyboard()
        }
    }

    @Test fun securePasswordAndLockscreenUseShiftableQwerty() {
        val keyguard = latinIME.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val prefs = latinIME.prefs()
        val oldToolbarMode = prefs.getString(Settings.PREF_TOOLBAR_MODE, null)
        val oldIncognito = prefs.getBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, false)
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_FLAG_FORCE_ASCII
        }
        val korean = SubtypeUtilsAdditional.createEmojiCapableAdditionalSubtype(
            Locale.KOREAN, "korean_cheonjiin", false
        )
        val fallback = SettingsSubtype.fallbackSubtype.toAdditionalSubtype()

        try {
            prefs.edit()
                .putString(Settings.PREF_TOOLBAR_MODE, ToolbarMode.EXPANDABLE.name)
                .putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, false)
                .commit()
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

            // https://github.com/HeliBorg/HeliBoard/issues/2642
            shadowOf(keyguard).setIsDeviceLocked(false)
            latinIME.onStartInputView(editorInfo, true)
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            assertFalse(Settings.getValues().mIsLocked)
            assertEquals(ToolbarMode.EXPANDABLE, Settings.getValues().mToolbarMode)
            assertTrue(latinIME.hasSuggestionStripView())

            val normalEditorInfo = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
            ShadowInputMethodService.currentInputType = normalEditorInfo.inputType
            ShadowInputMethodService.currentImeOptions = normalEditorInfo.imeOptions
            latinIME.onStartInputView(normalEditorInfo, false)
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            assertFalse(Settings.getValues().mIncognitoModeEnabled)
        } finally {
            RichInputMethodManager.forceSubtype(fallback)
            shadowOf(keyguard).setIsDeviceLocked(false)
            prefs.edit()
                .putString(Settings.PREF_TOOLBAR_MODE, oldToolbarMode)
                .putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, oldIncognito)
                .commit()
        }
    }

    @Test fun noLanguageSpacebarUsesLayoutName() {
        val subtype = RichInputMethodSubtype.get(
            SettingsSubtype(Locale.forLanguageTag(SubtypeLocaleUtils.NO_LANGUAGE), "")
                .withLayout(LayoutType.MAIN, "qwerty")
                .toAdditionalSubtype()
        )

        val text = ReflectionHelpers.callInstanceMethod<String>(
            keyboardSwitcher.mainKeyboardView,
            "layoutLanguageOnSpacebar",
            ReflectionHelpers.ClassParameter.from(Paint::class.java, Paint()),
            ReflectionHelpers.ClassParameter.from(RichInputMethodSubtype::class.java, subtype),
            ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType!!, 1_000),
        )

        assertEquals(SubtypeLocaleUtils.getMainLayoutDisplayName("qwerty"), text)
    }

    // https://github.com/HeliBorg/HeliBoard/issues/2639
    @Test fun noLanguageShiftTypesUppercase() {
        val fallback = SettingsSubtype.fallbackSubtype.toAdditionalSubtype()
        val subtype = SettingsSubtype(Locale.forLanguageTag(SubtypeLocaleUtils.NO_LANGUAGE), "")
            .withLayout(LayoutType.MAIN, "qwerty")
            .toAdditionalSubtype()
        try {
            RichInputMethodManager.forceSubtype(subtype)
            latinIME.onStartInputView(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, false)
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            touchKey(KeyCode.SHIFT, MotionEvent.ACTION_DOWN)
            touchKey(KeyCode.SHIFT, MotionEvent.ACTION_UP)
            touchKey('F'.code, MotionEvent.ACTION_DOWN, 50L)
            touchKey('F'.code, MotionEvent.ACTION_UP, 50L)

            assertEquals("F", ShadowInputMethodService.text)
        } finally {
            RichInputMethodManager.forceSubtype(fallback)
        }
    }

    @Test fun largeCopiedTextAppearsAsTruncatedSuggestionAndPastesCompletely() {
        val prefs = latinIME.prefs()
        val historyEnabled = prefs.getBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, true)
        val clipboard = latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val root = keyboardSwitcher.stripContainer.rootView
        val suggestions = root.findViewById<ViewGroup>(R.id.suggestions_strip)
        val copiedText = "바로 붙여넣기".repeat(20_000)

        try {
            prefs.edit().putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, false).apply()
            latinIME.onStartInputView(EditorInfo(), false)
            latinIME.updateSuggestionStripView(root)
            clipboard.clearPrimaryClip()
            latinIME.setNeutralSuggestionStrip()

            val clip = ClipData.newPlainText("copied text", copiedText)
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
            assertEquals(copiedText.take(200), suggestion?.text?.toString())
            suggestion!!.performClick()
            assertEquals(copiedText, ShadowInputMethodService.text)
        } finally {
            prefs.edit().putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, historyEnabled).apply()
        }
    }

    @Test fun incognitoDoesNotForceExpandKeyWhenToolbarIsNotExpandable() {
        val prefs = latinIME.prefs()
        val oldToolbarMode = prefs.getString(Settings.PREF_TOOLBAR_MODE, null)
        val oldIncognito = prefs.getBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, false)
        try {
            prefs.edit()
                .putString(Settings.PREF_TOOLBAR_MODE, ToolbarMode.SUGGESTION_STRIP.name)
                .putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, true)
                .commit()
            latinIME.onStartInputView(EditorInfo(), false)
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            assertTrue(Settings.getValues().mIncognitoModeEnabled)
            assertEquals(
                View.GONE,
                keyboardSwitcher.stripContainer.rootView
                    .findViewById<View>(R.id.suggestions_strip_toolbar_key).visibility,
            )
        } finally {
            prefs.edit()
                .putString(Settings.PREF_TOOLBAR_MODE, oldToolbarMode)
                .putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, oldIncognito)
                .commit()
            latinIME.onStartInputView(EditorInfo(), false)
        }
    }

    @Test fun toolbarStaysVisibleWhenSuggestionStripViewReturns() {
        val root = keyboardSwitcher.stripContainer.rootView
        val strip = root.findViewById<SuggestionStripView>(R.id.suggestion_strip_view)
        val toolbar = strip.findViewById<View>(R.id.toolbar_container)
        val suggestions = strip.findViewById<View>(R.id.suggestions_strip)

        try {
            strip.setToolbarVisibility(true)
            ReflectionHelpers.callInstanceMethod<Any?>(
                strip,
                "onVisibilityChanged",
                ReflectionHelpers.ClassParameter.from(View::class.java, strip),
                ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType!!, View.VISIBLE),
            )

            assertEquals(View.VISIBLE, toolbar.visibility)
            assertEquals(View.GONE, suggestions.visibility)
        } finally {
            strip.setToolbarVisibility(false)
            strip.visibility = View.VISIBLE
        }
    }

    @Test fun emojiSearchResultWaitsForHostEditor() {
        ShadowInputMethodService.currentPrivateImeOptions =
            "helium314.keyboard.keyboard.emoji.search.300,"
        val result = Intent(EmojiSearchActivity.EMOJI_SEARCH_DONE_ACTION)
            .putExtra(EmojiSearchActivity.EMOJI_KEY, "😀")

        latinIME.onStartCommand(result, 0, 42)
        assertEquals("", ShadowInputMethodService.text)

        ShadowInputMethodService.currentPrivateImeOptions = null
        latinIME.onStartInputView(EditorInfo(), false)
        assertEquals("😀", ShadowInputMethodService.text)
    }

    @Test fun pasteKeyAndCtrlVCommitPlainTextClipboard() {
        val clipboard = latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val copiedText = "붙여넣기\n테스트"
        clipboard.setPrimaryClip(ClipData.newPlainText("copied text", copiedText))
        try {
            latinIME.mKeyboardActionListener.onCodeInput(
                KeyCode.CLIPBOARD_PASTE, 0, 0, false
            )
            assertEquals(copiedText, ShadowInputMethodService.text)

            ShadowInputMethodService.reset()
            latinIME.onEvent(Event.createSoftwareKeypressEvent(
                'v'.code, Event.NOT_A_KEY_CODE, KeyEvent.META_CTRL_ON, 0, 0, false
            ))
            assertEquals(copiedText, ShadowInputMethodService.text)
        } finally {
            clipboard.clearPrimaryClip()
        }
    }

    @Test fun toolbarCopyDelegatesLongSelectionToEditor() {
        val selected = "긴 선택문".repeat(50_000)
        ShadowInputMethodService.text = selected
        ShadowInputMethodService.selectionStart = 0
        ShadowInputMethodService.selectionEnd = selected.length
        ShadowInputMethodService.selectedTextLimit = 32
        latinIME.onUpdateSelection(0, 0, 0, selected.length, -1, -1)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        latinIME.mKeyboardActionListener.onCodeInput(KeyCode.CLIPBOARD_COPY, 0, 0, false)

        assertEquals(android.R.id.copy, ShadowInputMethodService.lastContextMenuAction)
        assertEquals(selected, ShadowInputMethodService.contextMenuCopiedText)
    }

    @Test fun selectAllDelegatesWhenTextAfterCursorIsUnavailable() {
        ShadowInputMethodService.text = "first second"
        ShadowInputMethodService.selectionStart = 0
        ShadowInputMethodService.selectionEnd = 5
        ShadowInputMethodService.textAfterCursorAvailable = false
        latinIME.onUpdateSelection(0, 0, 0, 5, -1, -1)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        latinIME.mKeyboardActionListener.onCodeInput(KeyCode.CLIPBOARD_SELECT_ALL, 0, 0, false)

        assertEquals(android.R.id.selectAll, ShadowInputMethodService.lastContextMenuAction)
        assertEquals("first second", ShadowInputMethodService.selectedText)
    }

    @Test fun selectWordIncludesDigits() {
        ShadowInputMethodService.text = "I ran 1500m"
        ShadowInputMethodService.selectionStart = 8
        ShadowInputMethodService.selectionEnd = 8
        latinIME.onUpdateSelection(0, 0, 8, 8, -1, -1)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        latinIME.mKeyboardActionListener.onCodeInput(KeyCode.CLIPBOARD_SELECT_WORD, 0, 0, false)

        assertEquals("1500m", ShadowInputMethodService.selectedText)
    }

    @Test fun backspaceDeletesSelectedText() {
        ShadowInputMethodService.text = "keep delete keep"
        ShadowInputMethodService.selectionStart = 5
        ShadowInputMethodService.selectionEnd = 11
        latinIME.onUpdateSelection(0, 0, 5, 11, -1, -1)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        latinIME.mKeyboardActionListener.onCodeInput(KeyCode.DELETE, 0, 0, false)

        assertEquals("keep  keep", ShadowInputMethodService.text)
    }

    @Test fun pasteKeyCommitsTextItemWithNonTextMimeType() {
        val clipboard = latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData(
            ClipDescription("terminal clipboard", arrayOf("application/x-terminal-text")),
            ClipData.Item("terminal paste")
        ))
        try {
            latinIME.mKeyboardActionListener.onCodeInput(
                KeyCode.CLIPBOARD_PASTE, 0, 0, false
            )
            assertEquals("terminal paste", ShadowInputMethodService.text)
        } finally {
            clipboard.clearPrimaryClip()
        }
    }

    @Test fun recentScreenshotDetectionRejectsPrivateAndUnrelatedMedia() {
        assertTrue(ClipboardHistoryManager.isScreenshot("Screenshot_20260829.png", null, null))
        assertTrue(ClipboardHistoryManager.isScreenshot("image.png", "Pictures/Screenshots/", null))
        assertTrue(ClipboardHistoryManager.isScreenshot("image.png", "Pictures/스크린샷/", null))
        assertFalse(ClipboardHistoryManager.isScreenshot("IMG_20260829.jpg", "DCIM/Camera/", "Camera"))
        assertTrue(ClipboardHistoryManager.canSuggestRecentScreenshot(false, InputType.TYPE_CLASS_TEXT))
        assertFalse(ClipboardHistoryManager.canSuggestRecentScreenshot(true, InputType.TYPE_CLASS_TEXT))
        assertFalse(ClipboardHistoryManager.canSuggestRecentScreenshot(
            false, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        ))
        assertFalse(ClipboardHistoryManager.canSuggestRecentScreenshot(
            false, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        ))
    }

    @Test fun clipboardHistoryDoesNotCrossAndroidProfiles() {
        assertFalse(ClipboardHistoryManager.isCrossProfileClient(12_345, 23_456))
        assertTrue(ClipboardHistoryManager.isCrossProfileClient(112_345, 23_456))
    }

    @Test fun recentGalleryScreenshotAppearsButNotInPasswordFields() {
        val prefs = latinIME.prefs()
        val enabled = prefs.getBoolean(Settings.PREF_SUGGEST_RECENT_SCREENSHOTS, false)
        val clipboard = latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val resolver = latinIME.contentResolver
        val media = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val now = System.currentTimeMillis()
        val previousProvider = ShadowContentResolver.getProvider(media)
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun query(
                uri: Uri,
                projection: Array<out String>?,
                selection: String?,
                selectionArgs: Array<out String>?,
                sortOrder: String?,
            ) = MatrixCursor(arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH,
            )).apply {
                addRow(arrayOf<Any?>(
                    42L, "Screenshot_20260829.png", "image/png", now / 1000, now,
                    "Screenshots", "Pictures/Screenshots/"
                ))
            }
            override fun getType(uri: Uri) = "image/png"
            override fun insert(uri: Uri, values: ContentValues?) = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        }.apply {
            attachInfo(latinIME, ProviderInfo().apply { authority = "media" })
        }
        ShadowContentResolver.registerProviderInternal("media", provider)
        val shadowResolver = shadowOf(resolver)
        val parent = keyboardSwitcher.stripContainer.rootView.findViewById<ViewGroup>(R.id.suggestions_strip)
        val textEditor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        val hadImageAccess = ClipboardHistoryManager.hasFullImageAccess(latinIME)
        val permissionContext = ContextWrapper(latinIME)

        try {
            if (!hadImageAccess) shadowOf(permissionContext).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
            assertTrue(ClipboardHistoryManager.hasFullImageAccess(latinIME))
            prefs.edit().putBoolean(Settings.PREF_SUGGEST_RECENT_SCREENSHOTS, true).apply()
            clipboard.clearPrimaryClip()
            latinIME.onStartInputView(textEditor, false)
            assertFalse(Settings.getValues().mIsLocked)

            val suggestion = latinIME.clipboardHistoryManager.getClipboardSuggestionView(textEditor, parent)
            assertNotNull(suggestion)
            assertEquals(View.VISIBLE, suggestion.findViewById<ImageView>(R.id.clipboard_suggestion_image).visibility)
            assertTrue(shadowResolver.getContentObservers(media).isNotEmpty())

            val passwordEditor = EditorInfo().apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            assertNull(latinIME.clipboardHistoryManager.getClipboardSuggestionView(passwordEditor, parent))
            assertTrue(shadowResolver.getContentObservers(media).isEmpty())
        } finally {
            prefs.edit().putBoolean(Settings.PREF_SUGGEST_RECENT_SCREENSHOTS, enabled).apply()
            if (!hadImageAccess) shadowOf(permissionContext).denyPermissions(Manifest.permission.READ_MEDIA_IMAGES)
            ShadowContentResolver.registerProviderInternal("media", previousProvider)
        }
    }

    private fun touchKey(code: Int, action: Int, eventTime: Long = 0L) {
        val kb = keyboardSwitcher.keyboard!!
        val key = kb.getKey(code)!!
        val x = key.x + key.height / 2
        val y = key.y + key.width / 2
        touchAt(x, y, action, eventTime)
    }

    private fun touchAt(x: Int, y: Int, action: Int, eventTime: Long) {
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
