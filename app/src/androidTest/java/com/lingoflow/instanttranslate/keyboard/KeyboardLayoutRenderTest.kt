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
    @Test fun panelUpdatesKeepCursorAndKeysMountedAndReadingHasNoInsertAction() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = LingoKeyboardView(instrumentation.targetContext, noActions)
            val state = KeyboardPanelState(TranslationPanel.READ, Direction.MULTILINGUAL, "hello", false, null, null, false, false)
            view.renderPanel(state); view.renderKeys(KeyPage.LETTERS, ShiftState.OFF, "↵")
            fun children(group: android.view.ViewGroup): List<View> = (0 until group.childCount).flatMap {
                val child = group.getChildAt(it)
                listOf(child) + if (child is android.view.ViewGroup) children(child) else emptyList()
            }
            val editor = children(view).filterIsInstance<android.widget.EditText>().single()
            val key = children(view).filterIsInstance<android.widget.Button>().single { it.text == "q" }
            editor.setSelection(2)
            view.renderPanel(state.copy(languages = com.lingoflow.instanttranslate.direction.TranslationLanguagePair("hi-Latn", "en")))
            view.renderKeys(KeyPage.LETTERS, ShiftState.OFF, "Send")
            org.junit.Assert.assertSame(editor, children(view).filterIsInstance<android.widget.EditText>().single())
            org.junit.Assert.assertEquals(2, editor.selectionStart)
            org.junit.Assert.assertSame(key, children(view).filterIsInstance<android.widget.Button>().single { it.text == "q" })
            assertTrue(children(view).single { it.contentDescription == "Read" }.isSelected)
            assertTrue(children(view).filterIsInstance<android.widget.Button>().none { it.text == "Translate & insert" || it.text == "Insert here" })
            view.renderPanel(state.copy(languages = com.lingoflow.instanttranslate.direction.TranslationLanguagePair("en", "hi-Latn")))
            val target = children(view).filterIsInstance<android.widget.Button>().single {
                it.contentDescription?.toString()?.startsWith("Choose target language") == true
            }
            org.junit.Assert.assertEquals("Hindi (Roman) ▾", target.text.toString())
            org.junit.Assert.assertEquals("Choose target language: Hindi (Roman)", target.contentDescription)
            view.renderPanel(state.copy(result = "Hello there"))
            view.renderKeys(KeyPage.LETTERS, ShiftState.OFF, "Send")
            org.junit.Assert.assertSame(key, children(view).filterIsInstance<android.widget.Button>().single { it.text == "q" })
            assertTrue(children(view).filterIsInstance<android.widget.Button>().none { it.text == "Insert here" })
        }
    }

    @Test fun fastReleaseCommitsImmediatelyAndCancelOrLongPressNeverAddsASecondKey() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            var clicks = 0
            var longPresses = 0
            val key = ImmediateKeyButton(instrumentation.targetContext)
            key.layout(0, 0, 100, 100)
            key.setOnClickListener { clicks++ }
            key.setOnLongClickListener { longPresses++; true }
            fun touch(action: Int) {
                val now = android.os.SystemClock.uptimeMillis()
                val event = android.view.MotionEvent.obtain(now, now, action, 50f, 50f, 0)
                key.onTouchEvent(event); event.recycle()
            }
            touch(android.view.MotionEvent.ACTION_DOWN); touch(android.view.MotionEvent.ACTION_UP)
            org.junit.Assert.assertEquals("Release must commit without a posted click task", 1, clicks)
            touch(android.view.MotionEvent.ACTION_DOWN); touch(android.view.MotionEvent.ACTION_CANCEL)
            org.junit.Assert.assertEquals(1, clicks)
            touch(android.view.MotionEvent.ACTION_DOWN); key.performLongClick(); touch(android.view.MotionEvent.ACTION_UP)
            org.junit.Assert.assertEquals(1, longPresses)
            org.junit.Assert.assertEquals("Long press must not also type the ordinary key", 1, clicks)
            key.performClick()
            org.junit.Assert.assertEquals("Accessibility click remains supported", 2, clicks)
        }
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
