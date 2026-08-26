package helium314.keyboard.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class KoreanSettingsTranslationTest {
    @Test fun allBaseStringsHaveKoreanTranslation() {
        val stringName = Regex("""<string\s+name="([^"]+)"""")
        fun names(path: String) = stringName.findAll(File(path).readText()).map { it.groupValues[1] }.toSet()

        val base = names("src/main/res/values/strings.xml")
        val korean = names("src/main/res/values-ko/strings.xml")
        val missing = base - korean

        assertTrue(missing.isEmpty(), "Missing Korean strings: ${missing.sorted().joinToString()}")
    }
}
