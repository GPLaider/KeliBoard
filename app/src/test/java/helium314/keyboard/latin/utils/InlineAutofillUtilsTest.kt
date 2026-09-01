package helium314.keyboard.latin.utils

import android.os.Build
import android.view.ViewGroup
import android.widget.inline.InlineContentView
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class InlineAutofillUtilsTest {
    @Test fun nestedInlineSuggestionSurfaceFollowsRequestedZOrder() {
        val root = Mockito.mock(ViewGroup::class.java)
        val nested = Mockito.mock(ViewGroup::class.java)
        val inlineSuggestion = Mockito.mock(InlineContentView::class.java)
        Mockito.`when`(root.childCount).thenReturn(1)
        Mockito.`when`(root.getChildAt(0)).thenReturn(nested)
        Mockito.`when`(nested.childCount).thenReturn(1)
        Mockito.`when`(nested.getChildAt(0)).thenReturn(inlineSuggestion)

        InlineAutofillUtils.setInlineSuggestionsOnTop(root, false)
        Mockito.verify(inlineSuggestion).setZOrderedOnTop(false)

        InlineAutofillUtils.setInlineSuggestionsOnTop(root, true)
        Mockito.verify(inlineSuggestion).setZOrderedOnTop(true)
    }
}
