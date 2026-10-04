package com.lingoflow.instanttranslate.ui

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.lingoflow.instanttranslate.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Two opt-in live requests verify the control, actual resolved label and read-only boundary. */
@RunWith(AndroidJUnit4::class)
class HinglishResultSmokeTest {
    @Test fun romanizedInputCanBeConvertedToDevanagariFromTheResult() {
        assumeTrue("Pass liveCloud=true to allow live requests",
            InstrumentationRegistry.getArguments().getString("liveCloud") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val app = context.packageName
        context.startActivity(ResultActivity.createIntent(context, "aap kaise ho?", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            // Exercise disclosure if this is a fresh install instead of bypassing consent.
            device.wait(Until.findObject(By.res(app, "button_disclosure_continue")), 5000)?.click()
            assertTrue("Hinglish result missing", device.wait(
                Until.hasObject(By.res(app, "text_direction").text(context.getString(R.string.direction_hinglish_to_en))), 35_000))
            assertFalse("Read-only selection exposed Replace", device.hasObject(By.res(app, "button_replace")))
            device.findObject(By.res(app, "button_direction")).click()
            val option = device.wait(Until.findObject(By.text(context.getString(R.string.direction_hinglish_to_hi))), 5000)
            requireNotNull(option) { "Hinglish to Hindi choice missing" }.click()
            assertTrue("Corrected direction missing", device.wait(
                Until.hasObject(By.res(app, "text_direction").text(context.getString(R.string.direction_hinglish_to_hi))), 35_000))
            val output = device.findObject(By.res(app, "text_translated"))?.text.orEmpty()
            assertTrue("Devanagari output missing", output.any { it in 'ऀ'..'ॿ' })
            assertTrue("Copy unavailable", device.hasObject(By.res(app, "button_copy")))
            assertFalse("Read-only correction exposed Replace", device.hasObject(By.res(app, "button_replace")))
        } finally {
            device.pressBack()
        }
    }
}
