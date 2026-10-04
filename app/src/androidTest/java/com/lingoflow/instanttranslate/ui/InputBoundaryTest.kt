package com.lingoflow.instanttranslate.ui

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lingoflow.instanttranslate.textaction.ProcessTextInput
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InputBoundaryTest {
    private fun selection(text: String = "aap kaise ho") = Intent(Intent.ACTION_PROCESS_TEXT)
        .setType("text/plain").putExtra(Intent.EXTRA_PROCESS_TEXT, text)

    @Test fun exportedBoundaryRequiresActionMimeAndBoundedPlainText() {
        assertNull(ProcessTextInput.validate(selection().setAction(Intent.ACTION_SEND)))
        assertNull(ProcessTextInput.validate(selection().setType("text/html")))
        assertNull(ProcessTextInput.validate(selection().setType(null)))
        assertNull(ProcessTextInput.validate(selection(" ")))
        assertNull(ProcessTextInput.validate(selection("x".repeat(4001))))
        val missing = selection().apply { removeExtra(Intent.EXTRA_PROCESS_TEXT) }
        assertNull(ProcessTextInput.validate(missing))
        assertNull(ProcessTextInput.validate(selection().putExtra(Intent.EXTRA_PROCESS_TEXT, 123)))
        assertTrue(ProcessTextInput.validate(selection())!!.isReadOnly)
        assertFalse(ProcessTextInput.validate(selection().putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false))!!.isReadOnly)
        assertEquals("aap kaise ho", ProcessTextInput.validate(selection())!!.text)
    }
}
