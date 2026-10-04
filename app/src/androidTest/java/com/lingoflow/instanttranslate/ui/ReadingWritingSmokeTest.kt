package com.lingoflow.instanttranslate.ui

import android.content.Intent
import android.content.pm.PackageManager
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

/** Emulator-owned access configuration is restored after every test. Host has a different UID. */
@RunWith(AndroidJUnit4::class)
class ReadingWritingSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val app = context.packageName
    private val host = "com.lingoflow.instanttranslate.testhost.withqueries"
    private val ime = "$app/.keyboard.LingoKeyboardService"

    private fun fixture() {
        device.executeShellCommand("am force-stop $host")
        device.executeShellCommand("am start -n $host/com.lingoflow.instanttranslate.testhost.FlowFixtureActivity")
        assertTrue(device.wait(Until.hasObject(By.text("Copy received message")), 5000))
        if (device.hasObject(By.text("Space"))) device.pressBack() // Dismiss our keyboard.
    }
    private fun click(text: String) {
        var control = device.wait(Until.findObject(By.text(text)), 1500)
        if (control == null && text == "Start reading session") {
            for (attempt in 0..2) {
                device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                    device.displayWidth / 2, device.displayHeight / 4, 20)
                control = device.wait(Until.findObject(By.text(text)), 500)
                if (control != null) break
            }
        }
        requireNotNull(control) { "Missing control $text" }.click()
    }
    private fun englishResult() {
        assertTrue("English floating result missing", device.wait(Until.hasObject(
            By.textContains("tomorrow")), 35_000))
        assertTrue(device.hasObject(By.text("Lingo · English")))
    }
    private fun live() { assumeTrue("Pass liveCloud=true", InstrumentationRegistry.getArguments().getString("liveCloud") == "true") }

    private fun configured(defaultKeyboard: Boolean, action: () -> Unit) {
        ReadingSession.stop(context)
        val oldIme = device.executeShellCommand("settings get secure default_input_method").trim()
        val oldOverlay = device.executeShellCommand("appops get $app SYSTEM_ALERT_WINDOW")
        device.executeShellCommand("appops set $app SYSTEM_ALERT_WINDOW allow")
        DisclosurePreferences(context).acknowledge()
        if (defaultKeyboard) {
            device.executeShellCommand("ime enable $ime")
            device.executeShellCommand("ime set $ime")
            assertEquals("Lingo IME was not selected", ime, device.executeShellCommand("settings get secure default_input_method").trim())
        }
        try { action() } catch (failure: Throwable) {
            device.dumpWindowHierarchy(java.io.File(context.getExternalFilesDir(null), "flow_failure.xml"))
            throw failure
        } finally {
            ReadingSession.stop(context)
            if (oldIme.contains('/')) device.executeShellCommand("ime set $oldIme")
            if (oldIme != ime) device.executeShellCommand("ime disable $ime")
            val oldMode = Regex("SYSTEM_ALERT_WINDOW: (allow|ignore|deny|default)").find(oldOverlay)?.groupValues?.get(1) ?: "default"
            device.executeShellCommand("appops set $app SYSTEM_ALERT_WINDOW $oldMode")
            device.executeShellCommand("am force-stop $host")
        }
    }
    private fun startSession() {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        click("Start reading session")
        device.wait(Until.findObject(By.res("com.android.permissioncontroller", "permission_allow_button")), 1000)?.click()
        assertTrue(device.wait(Until.hasObject(By.text("Lingo")), 5000))
        fixture()
    }

    @Test fun automaticCopyShowsConfirmationAndSkipsSensitiveClipsWithoutARequest() = configured(true) {
        startSession()
        click("Copy received message")
        assertTrue("Background copy prompt missing", device.wait(Until.hasObject(By.text("Message copied. Translate it to English?")), 5000))
        assertFalse(device.hasObject(By.text("Translating meaning and context…")))
        click("Close")
        click("Copy sensitive message")
        assertFalse("Sensitive clip prompted", device.wait(Until.hasObject(By.text("Message copied. Translate it to English?")), 1000))
        click("Copy received message")
        assertFalse("Repeated copy prompted", device.wait(Until.hasObject(By.text("Message copied. Translate it to English?")), 1000))
        click("Copy another message")
        assertTrue(device.wait(Until.hasObject(By.text("Message copied. Translate it to English?")), 5000))
        device.setOrientationLeft()
        try {
            assertTrue("Overlay lost on rotation", device.wait(Until.hasObject(By.text("Lingo · English")), 5000))
            assertTrue("Landscape Stop is inaccessible", device.hasObject(By.text("Stop")))
            click("Stop")
        } finally { device.setOrientationNatural(); device.unfreezeRotation() }
        assertTrue("Session overlay survived Stop", device.wait(Until.gone(By.text("Lingo · English")), 5000))
    }

    @Test fun keyboardTypesLocallyAndPasswordFocusSuppressesCopyPrompts() = configured(true) {
        startSession()
        val draft = device.findObject(By.desc("Writing draft"))
        draft.text = ""
        draft.click()
        click("⇧"); click("H"); click("i"); click("Space")
        click("?123"); click("7"); click("⌫")
        assertEquals("Keyboard commit/backspace failed", "Hi ", device.findObject(By.desc("Writing draft")).text)
        device.pressBack()
        click("Focus password field")
        assertTrue("Lingo keyboard did not attach to password editor", device.wait(Until.hasObject(By.text("Space")), 5000))
        device.pressBack()
        click("Copy received message")
        assertFalse("Password editor produced a clipboard prompt", device.wait(Until.hasObject(
            By.text("Message copied. Translate it to English?")), 1500))
        assertTrue(device.hasObject(By.text("Lingo")))
    }

    @Test fun confirmedAutomaticCopyFloatsEnglishAndOwnCopyDoesNotPrompt() {
        live()
        configured(true) {
            startSession(); click("Copy received message"); click("Translate"); englishResult()
            click("Copy")
            assertFalse(device.wait(Until.hasObject(By.text("Message copied. Translate it to English?")), 1000))
            click("Minimize"); click("Lingo"); englishResult(); click("Close")
        }
    }

    @Test fun otherKeyboardCanReadAfterBubbleTap() {
        live()
        configured(false) {
            startSession(); click("Copy received message")
            assertFalse(device.wait(Until.hasObject(By.text("Message copied. Translate it to English?")), 1000))
            click("Lingo"); englishResult()
        }
    }

    @Test fun standardAndroidSelectionMenuReplacesAnEditableHost() {
        live()
        configured(false) {
            device.executeShellCommand("am force-stop $host")
            device.executeShellCommand("am start -n $host/com.lingoflow.instanttranslate.testhost.MainActivity")
            val editor = requireNotNull(device.wait(Until.findObject(By.res(host, "edit_single_line")), 5000))
            editor.text = "main kal nahi aa sakta"
            editor.click(800)
            device.wait(Until.findObject(By.text("Select all")), 1500)?.click()
            device.wait(Until.findObject(By.desc("More options")), 1500)?.click()
            click("LingoBoard Translate")
            assertTrue("Native host selection was not replaced", device.wait(Until.hasObject(
                By.res(host, "edit_single_line").textContains("tomorrow")), 35_000))
            assertFalse("Replacement still needs a separate control", device.hasObject(By.res(app, "button_replace")))
        }
    }

    @Test fun writingReturnsOnlySelectionAndReadingNeverReturnsReplacement() {
        live()
        configured(false) {
            fixture(); click("Translate draft selection")
            assertTrue("Automatic writing replacement missing", device.wait(Until.hasObject(By.text("Writing returned replacement")), 35_000))
            val draft = device.findObject(By.desc("Writing draft")).text
            assertTrue("Unselected prefix or suffix changed", draft.startsWith("Before | ") && draft.endsWith(" | After"))
            assertFalse(draft.contains("main kal nahi aa sakta"))
            click("Read selected message"); englishResult()
            assertTrue("Read-only host received replacement", device.wait(Until.hasObject(By.text("No replacement returned")), 5000))
            click("Close")
        }
    }
}
