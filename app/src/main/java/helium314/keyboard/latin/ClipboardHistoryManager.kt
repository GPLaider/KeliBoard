// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.InputType
import android.text.TextUtils
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import helium314.keyboard.keyboard.KeyboardTypeface
import helium314.keyboard.compat.ClipboardManagerCompat
import helium314.keyboard.event.Event
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.isValidNumber
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.databinding.ClipboardSuggestionBinding
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.InputTypeUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ClipboardHistoryManager(
        private val latinIME: LatinIME
) : ClipboardManager.OnPrimaryClipChangedListener {

    private lateinit var clipboardManager: ClipboardManager
    private var clipboardSuggestionView: View? = null
    private var clipboardDao: ClipboardDao? = null
    private var tempPrimaryClip = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val refreshScreenshotSuggestion = Runnable {
        if (latinIME.isInputViewShown) latinIME.setNeutralSuggestionStrip()
    }
    private val screenshotObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            screenshotQueryValid = false
            mainHandler.removeCallbacks(refreshScreenshotSuggestion)
            mainHandler.postDelayed(refreshScreenshotSuggestion, SCREENSHOT_SETTLE_MILLIS)
        }
    }
    private var screenshotObserverRegistered = false
    private var screenshotQueryValid = false
    private var recentScreenshot: RecentScreenshot? = null
    private var dismissedScreenshotUri: Uri? = null

    fun onCreate() {
        clipboardManager = latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(this)
        clipboardDao = ClipboardDao.getInstance(latinIME)
        if (latinIME.mSettings.current.mClipboardHistoryEnabled)
            fetchPrimaryClip()
    }

    fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(this)
        if (screenshotObserverRegistered)
            latinIME.contentResolver.unregisterContentObserver(screenshotObserver)
        mainHandler.removeCallbacks(refreshScreenshotSuggestion)
    }

    fun onInputViewHidden() {
        setScreenshotObserverEnabled(false)
    }

    override fun onPrimaryClipChanged() {
        if (tempPrimaryClip) return
        // Storing clipboard history and showing the current clipboard suggestion are separate settings.
        if (latinIME.mSettings.current.mClipboardHistoryEnabled)
            fetchPrimaryClip()
        dontShowCurrentSuggestion = false
        if (latinIME.isInputViewShown) latinIME.setNeutralSuggestionStrip()
    }

    // todo for later
    //  setting whether to store sensitive clip data?
    //  care about other clip items than first?
    private fun fetchPrimaryClip() {
        if (tempPrimaryClip) return // avoid updating history
        val clipData = clipboardManager.primaryClip ?: return
        if (clipData.itemCount == 0) return
        val clipItem = clipData.getItemAt(0) ?: return
        val description = clipData.description ?: return
        val timeStamp = ClipboardManagerCompat.getClipTimestamp(clipData)

        if (description.hasMimeType("text/*")) {
            val content = clipItem.coerceToText(latinIME)
            if (TextUtils.isEmpty(content)) return
            clipboardDao?.addClip(timeStamp, false, content.toString())
        } else if (maySaveFromUri(clipItem.uri, latinIME)) {
            clipboardDao?.addClipUri(timeStamp, false, clipItem.uri, description, latinIME)
        }
    }

    // fallback method because in some apps there is no supported mime type and commitContend does nothing,
    // but KeyEvent.KEYCODE_PASTE for pasting from primary clip works fine
    // (actually we do change the primary clip, but (try to) revert immediately)
    fun pasteWithoutChangingClips(content: InputContentInfoCompat) {
        Log.d(TAG, "trying fallback pasting with system clipboard")
        val primaryClip = clipboardManager.primaryClip
        val tempClip = ClipData(content.description, ClipData.Item(content.contentUri))
        tempPrimaryClip = true
        clipboardManager.setPrimaryClip(tempClip)
        latinIME.onEvent(Event.createSoftwareKeypressEvent(KeyCode.CLIPBOARD_PASTE, 0,
            Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false))
        tempPrimaryClip = false
        if (primaryClip == null)
            return
        // we need to wait a little before switching back to the original primary clip
        // a. it can happen that we switch back before the pasting has started, in that case we only past the primary clip
        // b. if we switch while the clip is pasted, it might crash the app (tested with joplin and logseq)
        // todo: replacing the current primary clip is far from ideal, try finding a different way
        GlobalScope.launch {
            delay(500)
            try {
                clipboardManager.setPrimaryClip(primaryClip)
            } catch (e: Exception) {
                Log.i(TAG, "could not go back to old primary clip", e)
                // happens wen the clip was a file
                // try to find it in out clipboard entries
                val clip = clipboardDao?.getAll()?.firstOrNull { it.timeStamp == ClipboardManagerCompat.getClipTimestamp(primaryClip) }
                if (clip?.filename != null)
                    clipboardManager.setPrimaryClip(ClipData(
                        ClipDescription(clip.text, clip.mimeTypes?.toTypedArray()),
                        ClipData.Item(clip.getContentUri(latinIME))
                    ))
                else if (clip != null)
                    clipboardManager.setPrimaryClip(ClipData(
                        ClipDescription("", arrayOf("text/*")),
                        ClipData.Item(clip.text)
                    ))
            }
        }
    }

    fun toggleClipPinned(id: Long) {
        clipboardDao?.togglePinned(id)
    }

    fun clearHistory() {
        clipboardDao?.clearNonPinned()
        ClipboardManagerCompat.clearPrimaryClip(clipboardManager)
        removeClipboardSuggestion()
    }

    fun canRemove(index: Int) = clipboardDao?.isPinned(index) == false

    fun removeEntry(index: Int) {
        if (canRemove(index))
            clipboardDao?.deleteClipAt(index)
    }

    fun sortHistoryEntries() {
        clipboardDao?.sort()
    }

    // We do not want to update history while user is visualizing it, so we check retention only
    // when history is about to be shown
    fun prepareClipboardHistory() = clipboardDao?.clearOldClips(true)

    fun getHistorySize() = clipboardDao?.count() ?: 0

    fun getHistoryEntry(position: Int) = clipboardDao?.getAt(position)

    fun getHistoryEntryContent(id: Long) = clipboardDao?.get(id)

    fun setHistoryChangeListener(listener: ClipboardDao.Listener?) {
        clipboardDao?.listener = listener
    }

    private fun isClipSensitive(inputType: Int): Boolean {
        ClipboardManagerCompat.getClipSensitivity(clipboardManager.primaryClip?.description)?.let { return it }
        return InputTypeUtils.isPasswordInputType(inputType)
    }

    fun getClipboardSuggestionView(editorInfo: EditorInfo?, parent: ViewGroup?): View? {
        // maybe no need to create a new view
        // but a cache has to consider a few possible changes, so better don't implement without need
        clipboardSuggestionView = null

        val inputType = editorInfo?.inputType ?: InputType.TYPE_NULL
        val now = System.currentTimeMillis()
        val clipboardData = getRecentClipboardSuggestion(now)
        val screenshotsEnabled = latinIME.prefs().getBoolean(
            Settings.PREF_SUGGEST_RECENT_SCREENSHOTS, Defaults.PREF_SUGGEST_RECENT_SCREENSHOTS
        ) && parent != null && canSuggestRecentScreenshot(latinIME.mSettings.current.mIsLocked, inputType)
        val screenshotObserverEnabled = setScreenshotObserverEnabled(screenshotsEnabled)
        if (parent == null) return null
        val screenshot = if (screenshotObserverEnabled && clipboardData == null)
            getRecentScreenshot(now)
        else null
        val clipData = clipboardData ?: screenshot?.let {
            ClipData(ClipDescription(it.name, arrayOf(it.mimeType)), ClipData.Item(it.uri))
        } ?: return null
        val clipItem = clipData.getItemAt(0) ?: return null
        val hasText = clipData.description?.hasMimeType("text/*") == true
        val hasImage = clipData.description?.hasMimeType("image/*") == true && clipItem.uri != null
        if (!hasText && !hasImage) return null
        val content = clipItem.coerceToText(latinIME)

        // create the view
        val binding = ClipboardSuggestionBinding.inflate(LayoutInflater.from(latinIME), parent, false)
        val textView = binding.clipboardSuggestionText
        val clipIcon = KeyboardIconsSet.instance.getIconDrawable(ToolbarKey.PASTE.name.lowercase())
        clipIcon?.setBounds(0, 0, textView.lineHeight, textView.lineHeight) // scale the icon to the text
        textView.setCompoundDrawablesRelative(clipIcon, null, null, null)
        if (hasText) {
            if (TextUtils.isEmpty(content)) return null
            if (InputTypeUtils.isNumberInputType(inputType) && !content.isValidNumber()) return null
            KeyboardTypeface.applyToTextView(textView)
            textView.text = (if (isClipSensitive(inputType)) "*".repeat(content.length.coerceAtMost(200)) else content)
        }
        val onClickListener = View.OnClickListener {
            dontShowCurrentSuggestion = true
            if (hasText) latinIME.onTextInput(content.toString())
            else if (screenshot != null) latinIME.mKeyboardActionListener.onContent(
                InputContentInfoCompat(screenshot.uri, clipData.description, null)
            ) else latinIME.onEvent(Event.createSoftwareKeypressEvent(KeyCode.CLIPBOARD_PASTE, 0,
                    Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false))
            if (screenshot != null) dismissedScreenshotUri = screenshot.uri
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
            binding.root.isGone = true
        }
        textView.setOnClickListener(onClickListener)

        if (hasImage) {
            if (InputTypeUtils.isNumberInputType(inputType)) return null
            val imageView = binding.clipboardSuggestionImage
            imageView.isVisible = true
            try {
                if (screenshot != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        imageView.setImageBitmap(latinIME.contentResolver.loadThumbnail(clipItem.uri, Size(256, 256), null))
                    } catch (_: Exception) {
                        imageView.setImageURI(clipItem.uri)
                    }
                } else imageView.setImageURI(clipItem.uri)
                imageView.contentDescription = clipData.description?.label
            } catch (e: Exception) {
                Log.w(TAG, "error setting clipboard image", e) // happens with SecurityException: Permission Denial
                if (screenshot == null) return null
                imageView.setImageResource(R.drawable.ic_dictionary)
            }
            imageView.setOnClickListener(onClickListener)
        }

        val closeButton = binding.clipboardSuggestionClose
        closeButton.setImageDrawable(KeyboardIconsSet.instance.getIconDrawable(ToolbarKey.CLOSE_HISTORY.name.lowercase()))
        closeButton.layoutParams.width = textView.lineHeight // scale the icon to the text
        closeButton.layoutParams.height = textView.lineHeight
        closeButton.setOnClickListener {
            if (screenshot != null) dismissedScreenshotUri = screenshot.uri
            else dontShowCurrentSuggestion = true
            removeSuggestionView()
        }

        val colors = latinIME.mSettings.current.mColors
        textView.setTextColor(colors.get(ColorType.KEY_TEXT))
        clipIcon?.let { colors.setColor(it, ColorType.CLIPBOARD_SUGGESTION_ICON) }
        colors.setColor(closeButton, ColorType.REMOVE_SUGGESTION_ICON)
        colors.setBackground(binding.root, ColorType.CLIPBOARD_SUGGESTION_BACKGROUND)

        clipboardSuggestionView = binding.root
        return clipboardSuggestionView
    }

    private fun removeClipboardSuggestion() {
        dontShowCurrentSuggestion = true
        removeSuggestionView()
    }

    private fun removeSuggestionView() {
        val csv = clipboardSuggestionView ?: return
        if (csv.parent != null && !csv.isGone) {
            // clipboard view is shown ->
            latinIME.setNeutralSuggestionStrip()
            latinIME.mHandler.postResumeSuggestions(false)
        }
        csv.isGone = true
    }

    private fun getRecentClipboardSuggestion(now: Long): ClipData? {
        if (!latinIME.mSettings.current.mSuggestClipboardContent || dontShowCurrentSuggestion) return null
        val clipData = clipboardManager.primaryClip ?: return null
        if (clipData.itemCount == 0) return null
        val item = clipData.getItemAt(0) ?: return null
        val hasText = clipData.description?.hasMimeType("text/*") == true
        if (hasText && TextUtils.isEmpty(item.coerceToText(latinIME))) return null
        val supported = hasText ||
                (clipData.description?.hasMimeType("image/*") == true && item.uri != null)
        if (!supported || now - ClipboardManagerCompat.getClipTimestamp(clipData) > RECENT_TIME_MILLIS) return null
        return clipData
    }

    private fun setScreenshotObserverEnabled(enabled: Boolean): Boolean {
        val shouldEnable = enabled && hasFullImageAccess(latinIME)
        if (shouldEnable == screenshotObserverRegistered) return shouldEnable
        if (shouldEnable) {
            latinIME.contentResolver.registerContentObserver(imageCollectionUri(), true, screenshotObserver)
            screenshotQueryValid = false
        } else {
            latinIME.contentResolver.unregisterContentObserver(screenshotObserver)
            recentScreenshot = null
            dismissedScreenshotUri = null
            screenshotQueryValid = false
        }
        screenshotObserverRegistered = shouldEnable
        return shouldEnable
    }

    private fun getRecentScreenshot(now: Long): RecentScreenshot? {
        if (!screenshotQueryValid) {
            recentScreenshot = queryRecentScreenshot(now)
            screenshotQueryValid = true
        }
        return recentScreenshot?.takeIf {
            now - it.timestamp <= RECENT_TIME_MILLIS && it.uri != dismissedScreenshotUri
        }
    }

    private fun queryRecentScreenshot(now: Long): RecentScreenshot? {
        val columns = mutableListOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            columns += MediaStore.Images.Media.RELATIVE_PATH
        try {
            latinIME.contentResolver.query(
                imageCollectionUri(),
                columns.toTypedArray(),
                "${MediaStore.Images.Media.DATE_ADDED} >= ?",
                arrayOf(((now - RECENT_TIME_MILLIS) / 1000).toString()),
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val name = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mime = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val added = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val taken = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val bucket = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                else -1
                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(name)
                    if (!isScreenshot(displayName, if (path >= 0) cursor.getString(path) else null, cursor.getString(bucket)))
                        continue
                    val uri = ContentUris.withAppendedId(imageCollectionUri(), cursor.getLong(id))
                    val timestamp = maxOf(cursor.getLong(added) * 1000, cursor.getLong(taken))
                    return RecentScreenshot(uri, timestamp, displayName ?: "Screenshot", cursor.getString(mime) ?: "image/*")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "could not query recent screenshots", e)
        }
        return null
    }

    private fun imageCollectionUri(): Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    private data class RecentScreenshot(
        val uri: Uri,
        val timestamp: Long,
        val name: String,
        val mimeType: String,
    )

    companion object {
        private val TAG = "ClipboardHistoryManager"

        // avoid showing the current suggestion because it has been dismissed or pasted
        private var dontShowCurrentSuggestion: Boolean = false

        const val RECENT_TIME_MILLIS = 3 * 60 * 1000L // 3 minutes (for clipboard suggestions)
        private const val SCREENSHOT_SETTLE_MILLIS = 500L

        fun imageReadPermissions(): Array<String> = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        fun hasFullImageAccess(context: Context): Boolean {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.READ_MEDIA_IMAGES
            else Manifest.permission.READ_EXTERNAL_STORAGE
            return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

        internal fun canSuggestRecentScreenshot(deviceLocked: Boolean, inputType: Int): Boolean =
            !deviceLocked && !InputTypeUtils.isAnyPasswordInputType(inputType) && !InputTypeUtils.isNumberInputType(inputType)

        internal fun isScreenshot(displayName: String?, relativePath: String?, bucketName: String?): Boolean =
            sequenceOf(displayName, relativePath, bucketName).filterNotNull().any {
                val normalized = it.lowercase().replace(" ", "").replace("_", "").replace("-", "")
                "screenshot" in normalized || "screencapture" in normalized || "스크린샷" in it
            }

        private fun maySaveFromUri(uri: Uri?, context: Context): Boolean {
            val maxSize = context.prefs().getInt(Settings.PREF_CLIPBOARD_FILES_SIZE_LIMIT, Defaults.PREF_CLIPBOARD_FILES_SIZE_LIMIT)
            val saveUriData = context.prefs().getBoolean(Settings.PREF_CLIPBOARD_USE_FILES, Defaults.PREF_CLIPBOARD_USE_FILES)
            if (uri == null || !saveUriData) return false
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null).use {
                    if (it?.moveToFirst() != true) return false
                    val size = it.getLong(0)
                    return size <= maxSize * 1000000 // maxSize is megabytes
                }
            } catch (e: Exception) {
                Log.w(TAG, "error checking clip size", e) // happens with SecurityException: Permission Denial
                return false
            }
        }
    }
}
