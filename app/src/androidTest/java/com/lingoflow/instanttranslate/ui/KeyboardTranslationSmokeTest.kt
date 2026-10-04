package com.lingoflow.instanttranslate.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.lingoflow.instanttranslate.prefs.DisclosurePreferences
import com.lingoflow.instanttranslate.reading.ReadingSession
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real, different-UID chat editor; all keyboard tests explicitly deny overlay access. */
@RunWith(AndroidJUnit4::class)
class KeyboardTranslationSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val app = context.packageName
    private val host = "com.lingoflow.instanttranslate.testhost.withqueries"
    private val ime = "$app/.keyboard.LingoKeyboardService"

    private fun click(text: String) {
        requireNotNull(device.wait(Until.findObject(By.text(text)), 5000)) { "Missing $text" }.click()
    }
    private fun desc(text: String) {
        requireNotNull(device.wait(Until.findObject(By.desc(text)), 5000)) { "Missing $text" }.click()
    }
    private fun draft() = requireNotNull(device.findObject(By.desc("Writing draft")))
    private fun panelDraft() = requireNotNull(device.findObject(By.desc("Translation draft")))
    private fun live() = assumeTrue("Pass liveCloud=true", InstrumentationRegistry.getArguments().getString("liveCloud") == "true")

    private fun configured(action: () -> Unit) {
        ReadingSession.stop(context)
        val oldIme = device.executeShellCommand("settings get secure default_input_method").trim()
        val oldOverlay = device.executeShellCommand("appops get $app SYSTEM_ALERT_WINDOW")
        device.executeShellCommand("appops set $app SYSTEM_ALERT_WINDOW deny")
        device.executeShellCommand("ime enable $ime")
        device.executeShellCommand("ime set $ime")
        DisclosurePreferences(context).acknowledge()
        device.executeShellCommand("am force-stop $host")
        device.executeShellCommand("am start -n $host/com.lingoflow.instanttranslate.testhost.FlowFixtureActivity")
        assertTrue(device.wait(Until.hasObject(By.desc("Writing draft")), 5000))
        try { action() } catch (error: Throwable) {
            device.dumpWindowHierarchy(java.io.File(context.getExternalFilesDir(null), "keyboard_failure.xml"))
            throw error
        } finally {
            device.pressBack()
            if (oldIme.contains('/')) device.executeShellCommand("ime set $oldIme")
            if (oldIme != ime) device.executeShellCommand("ime disable $ime")
            val oldMode = Regex("SYSTEM_ALERT_WINDOW: (allow|ignore|deny|default)").find(oldOverlay)?.groupValues?.get(1) ?: "default"
            device.executeShellCommand("appops set $app SYSTEM_ALERT_WINDOW $oldMode")
            device.executeShellCommand("am force-stop $host")
        }
    }
    private fun openKeyboard() {
        draft().click()
        assertTrue(device.wait(Until.hasObject(By.text("Space")), 5000))
    }
    private fun englishResult() {
        assertTrue("Result did not appear inside keyboard", device.wait(Until.hasObject(
            By.desc("Keyboard translation result").textContains("tomorrow")), 35_000))
        assertTrue(device.hasObject(By.text("Read · English")))
        assertFalse("Reading started an overlay", device.hasObject(By.text("Lingo · English")))
    }

    @Test fun richKeyboardTypesSymbolsEmojiAndOneShotShiftWithoutTranslating() = configured {
        draft().text = ""
        openKeyboard()
        click("⇧"); click("H"); click("i"); click("Space")
        assertEquals("Hi ", draft().text)
        click("?123"); click(";"); click("?")
        desc("Switch symbol page"); click("["); click("]")
        assertEquals("Hi ;?[]", draft().text)
        desc("Open 50 emoji"); click("😀")
        assertEquals("Hi ;?[]😀", draft().text)
        click("⌫")
        assertEquals("Hi ;?[]", draft().text)
        click("ABC")
        // Two rapid Shift taps lock case; tapping Caps Lock returns to lowercase.
        val point = requireNotNull(device.findObject(By.text("⇧"))).visibleCenter
        // UiObject2.click waits for accessibility idle, which exceeds a human double-tap interval.
        device.click(point.x, point.y); device.click(point.x, point.y)
        assertTrue(device.wait(Until.hasObject(By.text("⇪")), 3000))
        click("A"); click("B"); click("⇪"); click("c")
        assertEquals("Hi ;?[]ABc", draft().text)
        val length = draft().text.length
        requireNotNull(device.findObject(By.text("⌫"))).click(1000)
        assertTrue("Hold-to-delete did not repeat", draft().text.orEmpty().length < length - 1)
        assertFalse(device.hasObject(By.desc("Translation draft")))
    }

    @Test fun translationDraftStaysLocalDirectionSwapsAndCloseDiscardsIt() = configured {
        openKeyboard()
        val original = draft().text
        click("Translate")
        assertTrue(device.hasObject(By.text("Roman Hindi → English")))
        click("h"); click("i"); click("Space"); click("?123"); click("?")
        assertEquals("hi ?", panelDraft().text)
        assertEquals("Typing in translation draft changed chat", original, draft().text)
        desc("Swap translation languages")
        assertTrue(device.hasObject(By.text("English → Roman Hindi")))
        assertEquals("hi ?", panelDraft().text)
        desc("Close")
        assertFalse(device.hasObject(By.desc("Translation draft")))
        assertEquals(original, draft().text)
    }

    @Test fun landscapeKeepsWriteAndReadControlsAboveTheKeys() = configured {
        click("Copy received message")
        openKeyboard()
        device.setOrientationLeft()
        try {
            openKeyboard()
            click("Translate")
            assertTrue(device.hasObject(By.text("Translate & insert")))
            click("h"); click("i")
            assertEquals("hi", panelDraft().text)
            desc("Close"); click("Read")
            assertEquals("main kal nahi aa sakta", panelDraft().text)
            val confirmation = requireNotNull(device.findObject(By.text("Translate to English"))).visibleBounds
            assertTrue(confirmation.top > 0 && confirmation.bottom < device.displayHeight)
            assertTrue(device.hasObject(By.text("Space")))
        } finally { device.setOrientationNatural(); device.unfreezeRotation() }
    }

    @Test fun passwordDisablesCloudControlsAndUnsupportedDraftLeavesChatUnchanged() = configured {
        click("Focus password field")
        assertTrue(device.wait(Until.hasObject(By.text("Space")), 5000))
        assertFalse(requireNotNull(device.findObject(By.text("Translate"))).isEnabled)
        assertFalse(requireNotNull(device.findObject(By.text("Read"))).isEnabled)
        device.pressBack()
        click("Focus draft field")
        val original = draft().text
        click("Translate")
        panelDraft().text = "कल आना"
        click("Translate & insert")
        assertTrue(device.wait(Until.hasObject(By.textContains("Devanagari is not supported")), 5000))
        assertEquals(original, draft().text)
    }

    @Test fun firstRequestShowsDisclosureAndClosingLeavesChatUnchanged() = configured {
        context.getSharedPreferences("instanttranslate_prefs", 0).edit()
            .putInt("cloud_disclosure_ack_version", 0).commit()
        try {
            openKeyboard()
            val original = draft().text
            click("Translate")
            panelDraft().text = "main kal nahi aa sakta"
            click("Translate & insert")
            assertTrue(device.wait(Until.hasObject(By.text("Continue")), 5000))
            assertTrue(device.hasObject(By.textContains("Only text you ask to translate")))
            assertFalse(device.hasObject(By.text("Translating…")))
            desc("Close")
            assertEquals(original, draft().text)
        } finally { DisclosurePreferences(context).acknowledge() }
    }

    @Test fun writingTranslatesSeparateDraftAndInsertsWithoutSending() {
        live()
        configured {
            draft().text = "Before | After"
            openKeyboard()
            click("Translate")
            panelDraft().text = "main kal nahi aa sakta"
            assertEquals("Before | After", draft().text)
            click("Translate & insert")
            assertTrue("Translated draft was not inserted", device.wait(Until.hasObject(
                By.desc("Writing draft").textContains("tomorrow")), 35_000))
            assertTrue("Existing chat draft lost", draft().text.contains("Before | After"))
            assertFalse(device.hasObject(By.desc("Translation draft")))
            assertFalse(device.hasObject(By.text("Read · English")))
        }
    }

    @Test fun copiedReadingConfirmsThenShowsEnglishAboveKeysWithoutChangingChat() {
        live()
        configured {
            click("Copy received message")
            val original = draft().text
            openKeyboard(); click("Read")
            assertEquals("main kal nahi aa sakta", panelDraft().text)
            assertFalse(device.hasObject(By.desc("Keyboard translation result")))
            assertEquals(original, draft().text)
            click("Translate to English"); englishResult()
            assertEquals("Reading changed chat", original, draft().text)
            click("h"); click("i")
            assertEquals("Keyboard did not resume chat typing below reading result", original.length + 2, draft().text.length)
            englishResult()
            click("Copy")
            click("New")
            // Android exposes the hint as accessibility text for an empty EditText.
            assertEquals("Own output should not reload as a new received message",
                context.getString(com.lingoflow.instanttranslate.R.string.keyboard_read_hint), panelDraft().text)
            assertTrue(device.hasObject(By.textContains("No readable English or Roman Hindi message found")))
        }
    }

    @Test fun englishWritingSwapsDirectionAndInsertsRomanHindi() {
        live()
        configured {
            draft().text = ""
            openKeyboard(); click("Translate"); desc("Swap translation languages")
            panelDraft().text = "How are you?"
            click("Translate & insert")
            assertTrue("English writing did not produce Roman Hindi", device.wait(Until.hasObject(
                By.desc("Writing draft").textContains("kaise")), 35_000))
            assertFalse(draft().text.any { it in '\u0900'..'\u097f' })
            assertFalse(device.hasObject(By.desc("Translation draft")))
        }
    }

    @Test fun editorSelectionShowsTranslateIconAndReadResultNeverReplacesSelection() {
        live()
        configured {
            click("Select draft text")
            assertTrue(device.wait(Until.hasObject(By.text("Translate selection")), 5000))
            val original = draft().text
            click("Translate selection")
            englishResult()
            assertEquals("Reading replaced selected text", original, draft().text)
        }
    }

    @Test fun receivedSelectionActionHandsOffToKeyboardWithoutOverlay() {
        live()
        configured {
            click("Read selected message")
            assertTrue(device.wait(Until.hasObject(By.text("No replacement returned")), 5000))
            openKeyboard(); englishResult()
            assertEquals("Before | main kal nahi aa sakta | After", draft().text)
        }
    }
}
