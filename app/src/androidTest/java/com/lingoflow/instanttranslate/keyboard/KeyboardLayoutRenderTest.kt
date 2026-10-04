package com.lingoflow.instanttranslate.keyboard

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lingoflow.instanttranslate.direction.Direction
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Renders the actual keyboard view using synthetic samples; never captures a user's screen. */
@RunWith(AndroidJUnit4::class)
class KeyboardLayoutRenderTest {
    private val noActions = object : KeyboardActions {
        override fun translateIcon() = Unit
        override fun readIcon() = Unit
        override fun closePanel() = Unit
        override fun swapDirection() = Unit
        override fun translate() = Unit
        override fun continueDisclosure() = Unit
        override fun readCopy() = Unit
        override fun copyResult() = Unit
        override fun insertResult() = Unit
        override fun switchKeyboard() = Unit
        override fun key(text: String) = Unit
        override fun shift() = Unit
        override fun lockShift() = Unit
        override fun newTranslation() = Unit
        override fun page(page: KeyPage) = Unit
        override fun backspace() = Unit
        override fun enter() = Unit
        override fun draftChanged(text: String) = Unit
    }
    @Test fun keyboardAndPanelsFitPortraitAndLandscapeAndRenderPreviews() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = File(context.getExternalFilesDir(null), "keyboard-previews").apply { mkdirs() }
        instrumentation.runOnMainSync {
            val states = listOf(
                "typing" to KeyboardPanelState(TranslationPanel.NONE, Direction.HINGLISH_TO_ENGLISH, "", false, null, null, false, false),
                "writing" to KeyboardPanelState(TranslationPanel.WRITE, Direction.HINGLISH_TO_ENGLISH, "main kal nahi aa sakta", false, null, null, false, false),
                "reading" to KeyboardPanelState(TranslationPanel.READ, Direction.READ_TO_ENGLISH, "", false,
                    "I can't come tomorrow. Let's do the raid after reset.", null, false, false),
            )
            for (dark in listOf(false, true)) for (landscape in listOf(false, true)) {
                val configuration = Configuration(context.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
                    orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                }
                val themedContext = context.createConfigurationContext(configuration)
                val density = themedContext.resources.displayMetrics.density
                for ((name, state) in states) {
                    val view = LingoKeyboardView(themedContext, noActions)
                    view.renderPanel(state)
                    view.renderKeys(KeyPage.LETTERS, ShiftState.OFF, "↵")
                    val width = ((if (landscape) 720 else 390) * density).toInt()
                    view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    val maximum = ((if (landscape) 360 else 480) * density).toInt()
                    assertTrue("$name landscape=$landscape height=${view.measuredHeight / density}dp exceeds available keyboard height", view.measuredHeight <= maximum)
                    view.layout(0, 0, width, view.measuredHeight)
                    val bitmap = Bitmap.createBitmap(width, view.measuredHeight, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    File(directory, "$name-${if (landscape) "landscape" else "portrait"}${if (dark) "-dark" else ""}.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
        }
    }
    private fun children(group: android.view.ViewGroup): List<View> = (0 until group.childCount).flatMap {
        val child = group.getChildAt(it)
        listOf(child) + if (child is android.view.ViewGroup) children(child) else emptyList()
    }

    @Test fun panelUpdatesKeepCursorAndKeysMountedAndReadingHasNoInsertAction() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = LingoKeyboardView(instrumentation.targetContext, noActions)
            val state = KeyboardPanelState(TranslationPanel.READ, Direction.MULTILINGUAL, "hello", false, null, null, false, false)
            view.renderPanel(state); view.renderKeys(KeyPage.LETTERS, ShiftState.OFF, "↵")
            val editor = children(view).filterIsInstance<android.widget.EditText>().single()
            val grid = children(view).filterIsInstance<KeyGridView>().single()
            editor.setSelection(2)
            view.renderPanel(state.copy(languages = com.lingoflow.instanttranslate.direction.TranslationLanguagePair("hi-Latn", "en")))
            view.renderKeys(KeyPage.LETTERS, ShiftState.OFF, "Send")
            org.junit.Assert.assertSame(editor, children(view).filterIsInstance<android.widget.EditText>().single())
            org.junit.Assert.assertEquals(2, editor.selectionStart)
            org.junit.Assert.assertSame(grid, children(view).filterIsInstance<KeyGridView>().single())
            assertTrue(children(view).single { it.contentDescription == "Read" }.isSelected)
            assertTrue(children(view).none { it.contentDescription == "Translate & insert" })
            assertTrue(children(view).filterIsInstance<android.widget.Button>().none { it.text == "Insert here" })
            view.renderPanel(state.copy(languages = com.lingoflow.instanttranslate.direction.TranslationLanguagePair("en", "hi-Latn")))
            val target = children(view).filterIsInstance<android.widget.Button>().single {
                it.contentDescription?.toString()?.startsWith("Choose target language") == true
            }
            org.junit.Assert.assertEquals("Hindi (Roman) ▾", target.text.toString())
            org.junit.Assert.assertEquals("Choose target language: Hindi (Roman)", target.contentDescription)
            view.renderPanel(state.copy(result = "Hello there"))
            view.renderKeys(KeyPage.LETTERS, ShiftState.OFF, "Send")
            org.junit.Assert.assertSame(grid, children(view).filterIsInstance<KeyGridView>().single())
            assertTrue(children(view).filterIsInstance<android.widget.Button>().none { it.text == "Insert here" })
        }
    }

    @Test fun languagePickersAppearOnlyWithATranslationPanel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = LingoKeyboardView(instrumentation.targetContext, noActions)
            fun languageBar() = children(view).filterIsInstance<android.widget.Button>().single {
                it.contentDescription?.toString()?.startsWith("Choose source language") == true
            }.parent as View
            view.renderPanel(KeyboardPanelState(TranslationPanel.NONE, Direction.MULTILINGUAL, "", false, null, null, false, false))
            org.junit.Assert.assertEquals(View.INVISIBLE, languageBar().visibility)
            assertTrue("Keyboard header carries no logo", children(view).none { it.contentDescription == "LingoBoard logo" })
            view.renderPanel(KeyboardPanelState(TranslationPanel.WRITE, Direction.MULTILINGUAL, "", false, null, null, false, false))
            org.junit.Assert.assertEquals(View.VISIBLE, languageBar().visibility)
            // Paste and Translate share the draft's row instead of adding a separate button row.
            val editor = children(view).filterIsInstance<android.widget.EditText>().single()
            val submit = children(view).single { it.contentDescription == "Translate & insert" }
            val paste = children(view).single { it.contentDescription == "Paste copied text" }
            val row = editor.parent.parent
            assertTrue(submit.parent.parent === row && paste.parent === editor.parent)
        }
    }

    /** Records what the grid fires so touch rules can be checked without a host editor. */
    private class RecordingKeys : KeyGridListener {
        val keys = mutableListOf<String>()
        val longPresses = mutableListOf<String>()
        override fun onKey(key: KeySpec) { keys.add(key.label) }
        override fun onLongPress(key: KeySpec) { longPresses.add(key.label) }
    }

    private fun laidOutGrid(recorder: RecordingKeys): KeyGridView {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val grid = KeyGridView(context, KeyboardPalette.of(context), false, recorder)
        val width = (390 * context.resources.displayMetrics.density).toInt()
        grid.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        grid.layout(0, 0, width, grid.measuredHeight)
        return grid
    }

    /** Dispatches a (multi-)pointer event; [points] maps pointer id to the key it touches. */
    private fun KeyGridView.touch(action: Int, index: Int, vararg points: Pair<Int, String>) {
        val now = android.os.SystemClock.uptimeMillis()
        val properties = points.map { (id, _) -> android.view.MotionEvent.PointerProperties().apply { this.id = id } }.toTypedArray()
        val coords = points.map { (_, label) ->
            val bounds = requireNotNull(keyBounds(label)) { "No key labelled $label" }
            android.view.MotionEvent.PointerCoords().apply { x = bounds.centerX(); y = bounds.centerY(); pressure = 1f; size = 1f }
        }.toTypedArray()
        val masked = if (points.size > 1 && action != android.view.MotionEvent.ACTION_MOVE && action != android.view.MotionEvent.ACTION_CANCEL)
            (if (action == android.view.MotionEvent.ACTION_DOWN) android.view.MotionEvent.ACTION_POINTER_DOWN else android.view.MotionEvent.ACTION_POINTER_UP) or
                (index shl android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        else action
        val event = android.view.MotionEvent.obtain(now, now, masked, points.size, properties, coords, 0, 0, 1f, 1f, 0, 0,
            android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
        onTouchEvent(event); event.recycle()
    }

    @Test fun keysTypeOnReleaseAndCancelOrSlideNeverDoubleType() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val recorder = RecordingKeys()
            val grid = laidOutGrid(recorder)
            assertTrue("Letter rows keep a Gboard-sized touch target",
                requireNotNull(grid.keyBounds("q")).height() / grid.resources.displayMetrics.density >= 54)
            grid.touch(android.view.MotionEvent.ACTION_DOWN, 0, 0 to "q")
            org.junit.Assert.assertEquals("Characters commit on release, like Gboard", emptyList<String>(), recorder.keys)
            grid.touch(android.view.MotionEvent.ACTION_UP, 0, 0 to "q")
            org.junit.Assert.assertEquals(listOf("q"), recorder.keys)
            grid.touch(android.view.MotionEvent.ACTION_DOWN, 0, 0 to "w")
            grid.touch(android.view.MotionEvent.ACTION_CANCEL, 0, 0 to "w")
            org.junit.Assert.assertEquals(listOf("q"), recorder.keys)
            // Sliding re-targets the key under the finger; only the final key types.
            grid.touch(android.view.MotionEvent.ACTION_DOWN, 0, 0 to "e")
            grid.touch(android.view.MotionEvent.ACTION_MOVE, 0, 0 to "r")
            grid.touch(android.view.MotionEvent.ACTION_UP, 0, 0 to "r")
            org.junit.Assert.assertEquals(listOf("q", "r"), recorder.keys)
            // Delete acts on touch-down so a tap never feels delayed.
            grid.touch(android.view.MotionEvent.ACTION_DOWN, 0, 0 to "⌫")
            org.junit.Assert.assertEquals(listOf("q", "r", "⌫"), recorder.keys)
            grid.touch(android.view.MotionEvent.ACTION_UP, 0, 0 to "⌫")
            org.junit.Assert.assertEquals(listOf("q", "r", "⌫"), recorder.keys)
        }
    }

    @Test fun secondFingerCommitsTheFirstKeyInTypingOrder() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val recorder = RecordingKeys()
            val grid = laidOutGrid(recorder)
            grid.touch(android.view.MotionEvent.ACTION_DOWN, 0, 0 to "h")
            grid.touch(android.view.MotionEvent.ACTION_DOWN, 1, 0 to "h", 1 to "i")
            org.junit.Assert.assertEquals(listOf("h"), recorder.keys)
            // The second finger lifts first; the first must not type again when it lifts.
            grid.touch(android.view.MotionEvent.ACTION_UP, 1, 0 to "h", 1 to "i")
            grid.touch(android.view.MotionEvent.ACTION_UP, 0, 0 to "h")
            org.junit.Assert.assertEquals(listOf("h", "i"), recorder.keys)
        }
    }

    @Test fun holdingSpaceSwitchesKeyboardsWithoutTypingASpaceAndLettersHaveNoAlternates() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val recorder = RecordingKeys()
        lateinit var grid: KeyGridView
        instrumentation.runOnMainSync {
            grid = laidOutGrid(recorder)
            grid.touch(android.view.MotionEvent.ACTION_DOWN, 0, 0 to "Space")
        }
        Thread.sleep(android.view.ViewConfiguration.getLongPressTimeout() + 300L)
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync { grid.touch(android.view.MotionEvent.ACTION_UP, 0, 0 to "Space") }
        org.junit.Assert.assertEquals(listOf("Space"), recorder.longPresses)
        org.junit.Assert.assertEquals("Long-pressed space must not also type", emptyList<String>(), recorder.keys)
        instrumentation.runOnMainSync { grid.touch(android.view.MotionEvent.ACTION_DOWN, 0, 0 to "a") }
        Thread.sleep(android.view.ViewConfiguration.getLongPressTimeout() + 300L)
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync { grid.touch(android.view.MotionEvent.ACTION_UP, 0, 0 to "a") }
        org.junit.Assert.assertEquals("Holding a letter types it once, with no symbol alternate", listOf("a"), recorder.keys)
        org.junit.Assert.assertEquals(listOf("Space"), recorder.longPresses)
    }

    @Test fun setupUsesDimSlateLogoAndCollapsesFloatingTools() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(android.content.Intent(instrumentation.targetContext,
            com.lingoflow.instanttranslate.ui.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            instrumentation.runOnMainSync {
                val root = activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as android.widget.ScrollView
                org.junit.Assert.assertEquals(android.graphics.Color.parseColor("#18252B"),
                    (root.background as android.graphics.drawable.ColorDrawable).color)
                val content = root.getChildAt(0) as android.view.ViewGroup
                val start = (0 until content.childCount).map { content.getChildAt(it) }.filterIsInstance<android.widget.Button>()
                    .single { it.text == "Start reading session" }
                org.junit.Assert.assertEquals(View.GONE, start.visibility)
                val toggle = (0 until content.childCount).map { content.getChildAt(it) }.filterIsInstance<android.widget.Button>()
                    .single { it.text == "Optional: floating reading session" }
                toggle.performClick(); org.junit.Assert.assertEquals(View.VISIBLE, start.visibility)
                toggle.performClick(); org.junit.Assert.assertEquals(View.GONE, start.visibility)
                val header = content.getChildAt(0) as android.view.ViewGroup
                assertTrue((0 until header.childCount).any { header.getChildAt(it).contentDescription == "LingoBoard logo" })
            }
            val device = androidx.test.uiautomator.UiDevice.getInstance(instrumentation)
            device.waitForIdle()
            assertTrue(device.takeScreenshot(File(instrumentation.targetContext.getExternalFilesDir(null), "lingoboard-setup.png")))
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun draftEditingKeepsEditableAndCursorAndDeletesWholeEmoji() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = LingoKeyboardView(instrumentation.targetContext, noActions)
            view.renderPanel(KeyboardPanelState(TranslationPanel.WRITE, Direction.MULTILINGUAL, "hello", false, null, null, false, false))
            fun findEditor(group: android.view.ViewGroup): android.widget.EditText? {
                for (index in 0 until group.childCount) {
                    val child = group.getChildAt(index)
                    if (child is android.widget.EditText) return child
                    if (child is android.view.ViewGroup) findEditor(child)?.let { return it }
                }
                return null
            }
            val editor = requireNotNull(findEditor(view))
            val editable = editor.text
            editor.setSelection(1, 4)
            view.editDraft("😀")
            org.junit.Assert.assertSame(editable, editor.text)
            org.junit.Assert.assertEquals("h😀o", editor.text.toString())
            view.editDraft(delete = true)
            org.junit.Assert.assertEquals("ho", editor.text.toString())
            org.junit.Assert.assertEquals(1, editor.selectionStart)
        }
    }
}
