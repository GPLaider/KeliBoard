// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.provider.UserDictionary
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowContentResolver
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class PersonalDictionaryScreenTest {
    @Test fun importedBlankShortcutIsDeletedByRowId() {
        val context = RuntimeEnvironment.getApplication()
        val uri = UserDictionary.Words.CONTENT_URI
        val authority = uri.authority!!
        val previousProvider = ShadowContentResolver.getProvider(uri)
        var deletedSelection: String? = null
        var deletedArgs: Array<out String>? = null
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
                deletedSelection = selection
                deletedArgs = selectionArgs
                return 1
            }
            override fun update(uri: Uri, values: ContentValues?, selection: String?,
                selectionArgs: Array<out String>?): Int = 0
        }.apply {
            attachInfo(context, ProviderInfo().apply { this.authority = authority })
        }
        ShadowContentResolver.registerProviderInternal(authority, provider)

        try {
            deleteWord(Word("XX", "", 250, 42L), context.contentResolver)
            assertEquals("${UserDictionary.Words._ID}=?", deletedSelection)
            assertEquals(listOf("42"), deletedArgs?.toList())
        } finally {
            ShadowContentResolver.registerProviderInternal(authority, previousProvider)
        }
    }
}
