package com.lingoflow.instanttranslate.timing

import android.os.SystemClock
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The Gate 1 automated timing harness (docs/IMPLEMENTATION_PLAN.md §3 item 6, timing definitions
 * in docs/VALIDATION_PLAN.md §3.4).
 *
 * It drives the **real** cross-app path — a genuine text selection in the dev-only test host,
 * a genuine tap on the Process Text action — because that is the only way `T_action` means what
 * §3.4 says it means. Launching `ProcessTextActivity` with a synthetic intent would skip the
 * platform's own selection-menu dispatch, which is exactly the interval `platform entry`
 * (`T_receive - T_action`) is supposed to measure.
 *
 * Each measured run emits one line to `latency_runs.jsonl` in the app's external files
 * directory. Aggregate with `benchmark/collect_timings.py`; see `benchmark/TIMING_HARNESS.md`
 * for the full procedure, including what this harness cannot measure.
 *
 * Privacy: this test knows the workload text (it has to, to type it into the host), but it never
 * writes that text anywhere. Output rows carry a corpus item id, a direction, a length bucket and
 * timestamps — nothing else. The app-side [TranslationTimeline] is structurally incapable of
 * emitting content at all.
 *
 * Tunable with instrumentation arguments, e.g.:
 * ```
 * ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.measuredRuns=100 \
 *   -Pandroid.testInstrumentationRunnerArguments.warmupRuns=10
 * ```
 */
@RunWith(AndroidJUnit4::class)
class TranslationLatencyBenchmark {

    private lateinit var device: UiDevice
    private lateinit var workloads: List<Workload>
    private lateinit var outputFile: File

    /** One corpus item, reduced to what the harness needs. Deliberately does not outlive the run. */
    private data class Workload(
        val id: String,
        val direction: String,
        val lengthBucket: String,
        val text: String,
    )

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)

        // The harness is worthless against an uninstalled host: every run would fail identically
        // and produce a file full of zeros that looks like data. Skip loudly instead.
        assumeTrue(
            "Test host '$TEST_HOST_PACKAGE' is not installed. Install it first: " +
                "./gradlew :testhost:installWithQueriesDebug",
            isPackageInstalled(TEST_HOST_PACKAGE),
        )

        workloads = loadWorkloads()
        check(workloads.isNotEmpty()) {
            "No workloads selected from the corpus asset '$CORPUS_ASSET'. The corpus is copied " +
                "into androidTest assets by the copyBenchmarkCorpus Gradle task — check that it ran."
        }

        val outputDir = requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)) {
            "External files dir is unavailable, so measured runs would have nowhere to go"
        }
        outputFile = File(outputDir, OUTPUT_FILE_NAME).apply { delete() }
    }

    @Test
    fun measureTranslateLatencyAcrossWorkloads() {
        val warmupRuns = argument("warmupRuns", DEFAULT_WARMUP_RUNS)
        val measuredRuns = argument("measuredRuns", DEFAULT_MEASURED_RUNS)

        for (workload in workloads) {
            // §3.4: "at least 10 untimed stabilization runs where appropriate". These are thrown
            // away deliberately — the first run of a slice pays for JIT, page cache and a cold
            // provider client, and mixing that into the warm distribution inflates P50.
            repeat(warmupRuns) { performOneRun(workload, record = false) }
            repeat(measuredRuns) { performOneRun(workload, record = true) }
        }

        check(outputFile.length() > 0) {
            "The benchmark completed but recorded nothing to ${outputFile.absolutePath}. " +
                "That means every run failed to produce a timeline — check that the app under " +
                "test is a debug build (BuildConfig.TIMING_ENABLED) and that the Process Text " +
                "action is discoverable from the test host."
        }
    }

    /**
     * One full selection-to-result cycle.
     *
     * @param record false for stabilization runs, whose timings are discarded rather than written.
     */
    private fun performOneRun(workload: Workload, record: Boolean) {
        device.executeShellCommand("logcat -c")
        launchHostWith(workload.text)

        val target = device.wait(
            Until.findObject(By.res(TEST_HOST_PACKAGE, READ_ONLY_FIXTURE_ID)),
            UI_TIMEOUT_MILLIS,
        ) ?: fail("read-only fixture view not found in the test host")

        target.click(LONG_PRESS_MILLIS)
        clickToolbarItem("Select all", required = false)

        // Stage 0A found the action under the overflow menu rather than at toolbar top level on
        // every host where it appeared at all (benchmark/compatibility_matrix.csv). Open it if
        // present; on a host that promotes the action, the direct lookup below still finds it.
        clickToolbarItem("More options", required = false, byDescription = true)

        val action = device.wait(Until.findObject(By.text(PROCESS_TEXT_ACTION_LABEL)), UI_TIMEOUT_MILLIS)
            ?: fail(
                "the '$PROCESS_TEXT_ACTION_LABEL' Process Text action was not offered. On this " +
                    "host/OS that is a compatibility finding, not a harness bug — record it in " +
                    "benchmark/compatibility_matrix.csv"
            )

        // T_action, per §3.4: the moment the automated tap lands on the selection action. Read
        // from the same monotonic clock the app uses, so the two are directly subtractable.
        val actionNanos = SystemClock.elapsedRealtimeNanos()
        action.click()

        val appeared = device.wait(Until.hasObject(By.pkg(APP_PACKAGE).depth(0)), RESULT_TIMEOUT_MILLIS)
        val timeline = if (appeared) readTimeline() else null

        if (record) {
            writeRun(workload, actionNanos, timeline)
        }

        // Back out of both our result surface and the host, so the next run starts from the same
        // state rather than inheriting a stale selection toolbar.
        device.pressBack()
        device.pressBack()
        device.executeShellCommand("am force-stop $TEST_HOST_PACKAGE")
    }

    private fun launchHostWith(text: String) {
        val encoded = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        device.executeShellCommand(
            "am start -n $TEST_HOST_PACKAGE/$TEST_HOST_ACTIVITY " +
                "--es $EXTRA_FIXTURE_TEXT_B64 $encoded"
        )
        device.wait(Until.hasObject(By.pkg(TEST_HOST_PACKAGE).depth(0)), UI_TIMEOUT_MILLIS)
    }

    private fun clickToolbarItem(label: String, required: Boolean, byDescription: Boolean = false) {
        val selector = if (byDescription) By.desc(label) else By.text(label)
        val item: UiObject2? = device.wait(Until.findObject(selector), TOOLBAR_TIMEOUT_MILLIS)
        if (item == null) {
            if (required) fail("selection toolbar item '$label' not found")
            return
        }
        item.click()
    }

    /** Reads back the one content-free line the app emitted for this run, or null if none appeared. */
    private fun readTimeline(): JSONObject? {
        val log = device.executeShellCommand("logcat -d -s ${TranslationTimeline.TAG}:I")
        val line = log.lineSequence()
            .mapNotNull { line -> line.indexOf('{').takeIf { it >= 0 }?.let { line.substring(it) } }
            .lastOrNull() ?: return null
        return try {
            JSONObject(line)
        } catch (malformed: org.json.JSONException) {
            // A malformed timeline is a harness defect worth seeing, not a run to silently drop.
            throw IllegalStateException("timeline line was not valid JSON: $line", malformed)
        }
    }

    private fun writeRun(workload: Workload, actionNanos: Long, timeline: JSONObject?) {
        val row = JSONObject()
            .put("item_id", workload.id)
            .put("direction", workload.direction)
            .put("length_bucket", workload.lengthBucket)
            .put("source_chars", workload.text.length)
            .put("T_ACTION", actionNanos)
        if (timeline == null) {
            // A run with no timeline is a failure, and §3.4 requires failures to be reported
            // rather than dropped — a harness that only records successes reports a latency
            // distribution for a product that sometimes does not respond at all.
            row.put("outcome", "NO_TIMELINE")
        } else {
            row.put("outcome", timeline.getString("outcome"))
            row.put("marks", timeline.getJSONObject("marks"))
        }
        outputFile.appendText(row.toString() + "\n")
    }

    /**
     * Picks a small, representative set from the frozen corpus: the first item of each
     * (direction, length bucket) pair. §3.4 wants many repetitions of a few workloads, not one
     * repetition of many — a single pass over all 458 items would produce no usable percentiles
     * and would burn the free-tier quota (§3.3 state 8) before the run finished.
     */
    private fun loadWorkloads(): List<Workload> {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val lines = assets.open(CORPUS_ASSET).bufferedReader().use { it.readLines() }

        val perSlice = argument("itemsPerSlice", DEFAULT_ITEMS_PER_SLICE)
        val counts = mutableMapOf<Pair<String, String>, Int>()
        val selected = mutableListOf<Workload>()

        for (line in lines) {
            if (line.isBlank()) continue
            val item = JSONObject(line)
            val direction = item.getString("direction")
            val bucket = item.getString("length_bucket")
            val slice = direction to bucket
            if ((counts[slice] ?: 0) >= perSlice) continue
            counts[slice] = (counts[slice] ?: 0) + 1
            selected += Workload(
                id = item.getString("id"),
                direction = direction,
                lengthBucket = bucket,
                text = item.getString("source"),
            )
        }
        return selected
    }

    private fun isPackageInstalled(packageName: String): Boolean =
        device.executeShellCommand("pm list packages $packageName").contains(packageName)

    private fun argument(name: String, default: Int): Int {
        val raw = InstrumentationRegistry.getArguments().getString(name) ?: return default
        return raw.toIntOrNull()
            ?: error("instrumentation argument '$name' must be an integer, got '$raw'")
    }

    private fun fail(reason: String): Nothing = error("Benchmark run aborted: $reason")

    private companion object {
        const val APP_PACKAGE = "com.lingoflow.instanttranslate"

        // The withQueries flavor specifically: the withoutQueries flavor cannot discover our
        // action at all on Android 11+, which Stage 0A confirmed on two OS bands.
        const val TEST_HOST_PACKAGE = "com.lingoflow.instanttranslate.testhost.withqueries"
        const val TEST_HOST_ACTIVITY = "com.lingoflow.instanttranslate.testhost.MainActivity"
        const val EXTRA_FIXTURE_TEXT_B64 = "fixture_text_b64"
        const val READ_ONLY_FIXTURE_ID = "text_readonly"

        /**
         * Must match `process_text_action_label` in app/src/main/res/values/strings.xml —
         * that string is ProcessTextActivity's android:label, which is what the platform
         * renders as the selection-menu entry. If the label is ever localised, this lookup
         * has to follow it or every run will abort with "action was not offered".
         */
        const val PROCESS_TEXT_ACTION_LABEL = "Translate"

        const val CORPUS_ASSET = "corpus_v1.jsonl"
        const val OUTPUT_FILE_NAME = "latency_runs.jsonl"

        const val DEFAULT_WARMUP_RUNS = 10
        const val DEFAULT_MEASURED_RUNS = 30
        const val DEFAULT_ITEMS_PER_SLICE = 1

        const val LONG_PRESS_MILLIS = 800L
        const val TOOLBAR_TIMEOUT_MILLIS = 2_000L
        const val UI_TIMEOUT_MILLIS = 5_000L

        // Longer than GeminiTranslationProvider's own 20s request deadline, so a run that the
        // provider itself times out still produces a recorded TIMEOUT outcome rather than being
        // cut short here and misattributed to the harness.
        const val RESULT_TIMEOUT_MILLIS = 25_000L
    }
}
