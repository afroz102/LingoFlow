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
            for (landscape in listOf(false, true)) {
                val configuration = Configuration(context.resources.configuration).apply {
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
                    File(directory, "$name-${if (landscape) "landscape" else "portrait"}.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
        }
    }
}
