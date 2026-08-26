package helium314.keyboard.keyboard.emoji

import helium314.keyboard.latin.LatinIME
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class EmojiSearchActivityTest {
    @Test fun searchTextDoesNotLeakIntoNextSearch() {
        Robolectric.setupService(LatinIME::class.java)
        val searchText = EmojiSearchActivity::class.java.getDeclaredField("searchText").apply { isAccessible = true }
        val first = Robolectric.buildActivity(EmojiSearchActivity::class.java).get()
        val second = Robolectric.buildActivity(EmojiSearchActivity::class.java).get()

        try {
            searchText.set(first, "stale query")
            assertEquals("", searchText.get(second))
        } finally {
            searchText.set(first, "")
        }
    }
}
