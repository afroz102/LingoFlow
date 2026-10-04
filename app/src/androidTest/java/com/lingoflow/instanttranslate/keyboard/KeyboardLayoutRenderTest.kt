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
