// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.compat

import android.text.InputType
import android.view.inputmethod.EditorInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class AppWorkaroundsTest {
    @Test fun `viaAPP Bluetooth PIN field gets done action`() {
        assertEquals(
            EditorInfo.IME_ACTION_DONE,
            AppWorkarounds.adjustImeOptions(
                EditorInfo.IME_FLAG_NO_ENTER_ACTION,
                "com.viatraffic.viagraphapp",
            ),
        )
    }

    @Test fun `firefox autocorrect fields keep suggestions`() {
        val packageName = "org.mozilla.firefox"
        val autocorrectField = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        val plainWebField = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE

        val adjustedAutocorrectField = AppWorkarounds.adjustInputType(autocorrectField, packageName)
        val adjustedPlainWebField = AppWorkarounds.adjustInputType(plainWebField, packageName)

        assertEquals(0, adjustedAutocorrectField and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        assertEquals(InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT,
            adjustedAutocorrectField and InputType.TYPE_MASK_VARIATION)
        assertEquals(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            adjustedPlainWebField and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
    }
}
