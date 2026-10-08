package helium314.keyboard.latin.utils

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageButton
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.settings.Settings
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ToolbarKeyLongPressTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val prefs = activity.prefs()
    private val oldTimeout = prefs.all[Settings.PREF_KEY_LONGPRESS_TIMEOUT] as Int?
    private lateinit var button: ImageButton
    private var clicks = 0
    private var longClicks = 0

    @Before fun setup() {
        Robolectric.setupService(LatinIME::class.java)
        button = createToolbarKey(activity, ToolbarKey.COPY)
        activity.setContentView(button)
        button.layout(0, 0, 100, 100)
        button.setOnClickListener { clicks++ }
        button.setOnLongClickListener { longClicks++; true }
    }

    @After fun restore() {
        button.cancelLongPress()
        prefs.edit().apply {
            if (oldTimeout == null) remove(Settings.PREF_KEY_LONGPRESS_TIMEOUT)
            else putInt(Settings.PREF_KEY_LONGPRESS_TIMEOUT, oldTimeout)
        }.commit()
        activity.finish()
    }

    @Test fun shortConfiguredDelayTriggersExactlyOneLongClick() {
        prefs.edit().putInt(Settings.PREF_KEY_LONGPRESS_TIMEOUT, 200).commit()
        touch(MotionEvent.ACTION_DOWN)
        advance(199)
        assertEquals(0, longClicks)
        advance(1)
        assertEquals(1, longClicks)
        advance(1000)
        touch(MotionEvent.ACTION_UP)
        advance(1)
        assertEquals(1, longClicks)
        assertEquals(0, clicks)
    }

    @Test fun longConfiguredDelayDoesNotUseSystemTimeout() {
        prefs.edit().putInt(Settings.PREF_KEY_LONGPRESS_TIMEOUT, 700).commit()
        touch(MotionEvent.ACTION_DOWN)
        advance(699)
        assertEquals(0, longClicks)
        advance(1)
        assertEquals(1, longClicks)
        touch(MotionEvent.ACTION_UP)
        assertEquals(0, clicks)
    }

    @Test fun shortTapStillClicksAndNewPressUsesUpdatedSetting() {
        prefs.edit().putInt(Settings.PREF_KEY_LONGPRESS_TIMEOUT, 200).commit()
        touch(MotionEvent.ACTION_DOWN)
        advance(100)
        touch(MotionEvent.ACTION_UP)
        advance(1000)
        assertEquals(1, clicks)
        assertEquals(0, longClicks)
        prefs.edit().putInt(Settings.PREF_KEY_LONGPRESS_TIMEOUT, 700).commit()
        touch(MotionEvent.ACTION_DOWN)
        advance(699)
        assertEquals(0, longClicks)
        advance(1)
        assertEquals(1, longClicks)
        touch(MotionEvent.ACTION_UP)
        assertEquals(1, clicks)
    }

    @Test fun scrollCancellationAndMovingOffKeyCancelLongClick() {
        prefs.edit().putInt(Settings.PREF_KEY_LONGPRESS_TIMEOUT, 200).commit()
        touch(MotionEvent.ACTION_DOWN)
        advance(100)
        touch(MotionEvent.ACTION_CANCEL)
        advance(1000)
        touch(MotionEvent.ACTION_DOWN)
        advance(100)
        touch(MotionEvent.ACTION_MOVE, 10000f)
        advance(1000)
        touch(MotionEvent.ACTION_UP, 10000f)
        advance(1)
        assertEquals(0, clicks)
        assertEquals(0, longClicks)
    }

    @Test fun removingPressedKeyCancelsLongClick() {
        prefs.edit().putInt(Settings.PREF_KEY_LONGPRESS_TIMEOUT, 200).commit()
        touch(MotionEvent.ACTION_DOWN)
        activity.setContentView(android.view.View(activity))
        advance(1000)
        assertEquals(0, longClicks)
    }

    @Test fun navigationRepeatsOnlyWhileHeld() {
        prefs.edit().putInt(Settings.PREF_KEY_LONGPRESS_TIMEOUT, 200).commit()
        button.tag = ToolbarKey.LEFT
        val repeats = mutableListOf<Boolean>()
        button.setOnLongClickListener { view ->
            onLongClickToolbarKey(view) { _, repeat -> repeats.add(repeat) }
            true
        }
        touch(MotionEvent.ACTION_DOWN)
        advance(600)
        assertEquals(false, repeats.first())
        assertTrue(repeats.count { it } > 0)
        touch(MotionEvent.ACTION_UP)
        val count = repeats.size
        advance(1000)
        assertEquals(count, repeats.size)
        assertEquals(0, clicks)
    }

    @Test fun accessibilityCanStillInvokeLongClick() {
        assertTrue(button.performAccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null))
        assertEquals(1, longClicks)
    }

    private fun touch(action: Int, x: Float = 50f) {
        val event = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), action, x, 50f, 0)
        button.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun advance(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
}
