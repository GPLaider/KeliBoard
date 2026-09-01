package helium314.keyboard.keyboard

import android.content.Context
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.common.Constants
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PopupKeysKeyboardViewTest {
    @Test fun lockPopupsDispatchFullKeyLifecycle() {
        Robolectric.setupService(LatinIME::class.java)
        val listener = Mockito.mock(KeyboardActionListener::class.java)
        val view = TestPopupKeysKeyboardView(RuntimeEnvironment.getApplication())

        for (code in listOf(KeyCode.CAPS_LOCK, KeyCode.CTRL_LOCK)) {
            val key = Mockito.mock(Key::class.java)
            Mockito.`when`(key.code).thenReturn(code)

            view.dispatch(key, listener)

            Mockito.inOrder(listener).apply {
                verify(listener).onPressKey(code, 0, 1, HapticEvent.NO_HAPTICS)
                verify(listener).onCodeInput(code, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
                verify(listener).onReleaseKey(code, false)
            }
            Mockito.clearInvocations(listener)
        }
    }

    private class TestPopupKeysKeyboardView(context: Context) : PopupKeysKeyboardView(context, null) {
        private val testKeyboard = Mockito.mock(Keyboard::class.java)

        override fun getKeyboard() = testKeyboard

        fun dispatch(key: Key, listener: KeyboardActionListener) {
            mListener = listener
            onKeyInput(key, 0, 0)
        }
    }
}
