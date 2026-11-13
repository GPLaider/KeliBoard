// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.content.Context
import android.graphics.Paint
import android.os.Build
import androidx.core.content.edit
import helium314.keyboard.keyboard.KeyboardTypeface
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs

object SupportedEmojis {
    private val unsupportedEmojis = hashSetOf<String>()

    fun load(context: Context) {
        determineMaxSdk(context)
        val maxSdk = context.prefs().getInt(Settings.PREF_EMOJI_MAX_SDK, 0)
        val paint = Paint().apply {
            typeface = KeyboardTypeface.emojiTypeface() ?: KeyboardTypeface.customTypeface()
        }
        unsupportedEmojis.clear()
        context.assets.open("emoji/minApi.txt").reader().readLines().forEach {
            val s = it.split(" ")
            val minApi = s.first().toInt()
            s.drop(1).forEach { emoji ->
                // A supported newer emoji does not guarantee that every older ZWJ sequence
                // renders as one glyph, especially with custom or vendor emoji fonts.
                if (minApi > maxSdk || Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !paint.hasGlyph(emoji))
                    unsupportedEmojis.add(emoji)
            }
        }
    }

    private fun determineMaxSdk(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (context.prefs().contains(Settings.PREF_EMOJI_MAX_SDK)) return
        val paint = Paint()
        (KeyboardTypeface.emojiTypeface() ?: KeyboardTypeface.customTypeface())
            ?.let { paint.setTypeface(it) }
        val maxApi = context.assets.open("emoji/minApi.txt").reader().readLines().maxOf {
            val s = it.split(" ")
            val supported = paint.hasGlyph(s[1])
            if (supported) s.first().toInt() else 0
        }
        val newMax = maxApi.coerceAtLeast(Build.VERSION.SDK_INT)
        context.prefs().edit { putInt(Settings.PREF_EMOJI_MAX_SDK, newMax) }
    }

    fun isUnsupported(emoji: String) = emoji in unsupportedEmojis
}
