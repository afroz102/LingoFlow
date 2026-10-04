package com.lingoflow.instanttranslate.timing

import android.os.SystemClock
import android.os.Trace
import android.util.Log
import com.lingoflow.instanttranslate.BuildConfig

/** Client-side timing marks for the custom backend path. T_action is collected by
 * the test host harness. Client readiness validates the configured endpoint;
 * provider round-trip includes SQLite quota processing and Gemini generation.
 * There is no streaming first-token measurement. */
enum class TimingMark {
    /** `ACTION_PROCESS_TEXT` intent received and validated by the text-action adapter. */
    T_RECEIVE,

    /** Translation direction resolved. */
    T_DIRECTION,

    /** The backend client is configured and ready. */
    T_CLIENT_READY,

    /** Immediately before the selected text is posted to our translation backend. */
    T_REQUEST_SENT,

    /** Complete provider response received, parsed, and validated. */
    T_RESPONSE_END,

    /** The result is on screen and usable — marked after the frame containing it is drawn. */
    T_RENDER,
}

/** How a timed run ended. An enum, not a string, so no message text can reach a trace label. */
enum class TimingOutcome {
    SUCCESS,
    DISCLOSURE_REQUIRED,
    OFFLINE,
    FAILED,
}

/**
 * Records the §3.4 timing chain for one translate request and emits it as a single content-free
 * line for the benchmark harness to collect (`benchmark/collect_timings.py`).
 *
 * **The privacy rule of docs/TECHNICAL_PLAN.md — "no selected text or hash ever
 * enters a trace label" — is enforced by this type's signature, not by convention.** [mark] and
 * [complete] accept only enum values; there is no overload anywhere that takes a caller-supplied
 * string, so there is no code path by which selected text, a translation, or a digest of either
 * could be emitted. Everything written out is a mark name and a monotonic nanosecond reading.
 *
 * Timings use [SystemClock.elapsedRealtimeNanos] — monotonic as §3.4 requires, and unaffected by
 * wall-clock or timezone changes mid-run, which would otherwise silently corrupt a long
 * benchmark session.
 *
 * Instrumentation is compiled in unconditionally but gated at runtime on
 * [BuildConfig.TIMING_ENABLED], which is false for release builds. A future release-shaped
 * benchmark build type only has to flip that one flag rather than re-instrument anything.
 */
object TranslationTimeline {

    private val lock = Any()

    /** Insertion-ordered so the emitted line reads in the order the flow actually happened. */
    private val marks = LinkedHashMap<TimingMark, Long>()

    private var runId = 0L

    /**
     * Begins a new timed run, discarding any partial previous one.
     *
     * Called for a fresh Process Text selection and again for each user-triggered retry, because
     * a retry is a separate provider request with its own latency and must not be averaged into
     * the first attempt's numbers. A retry has no [TimingMark.T_RECEIVE] — the intent was received
     * once, runs ago — and the collector reports absent marks rather than inventing them.
     */
    fun startRun() {
        if (!BuildConfig.TIMING_ENABLED) return
        synchronized(lock) {
            marks.clear()
            runId++
        }
    }

    fun mark(mark: TimingMark) {
        if (!BuildConfig.TIMING_ENABLED) return
        val now = SystemClock.elapsedRealtimeNanos()
        synchronized(lock) {
            // First write wins. The provider is an object reused for the process lifetime, so on
            // a second request T_CLIENT_READY is marked again by an already-warm client; keeping
            // the first reading of a run keeps each mark meaning what §3.4 says it means.
            // containsKey rather than Map.putIfAbsent: that default method needs API 24 and
            // minSdk here is 23.
            if (!marks.containsKey(mark)) marks[mark] = now
        }
        // Zero-width Perfetto slice. The label is an enum name, never request content.
        Trace.beginSection(mark.name)
        Trace.endSection()
    }

    /**
     * Ends the run and emits it. Safe to call for every terminal state, including failures —
     * §3.4 requires provider failures to be reported, not just successful latencies, so a run
     * that ended in [TimingOutcome.OFFLINE] is data, not a run to discard.
     */
    fun complete(outcome: TimingOutcome) {
        if (!BuildConfig.TIMING_ENABLED) return
        val line = synchronized(lock) {
            if (marks.isEmpty()) return
            val body = marks.entries.joinToString(",") { (mark, nanos) -> "\"${mark.name}\":$nanos" }
            val id = runId
            marks.clear()
            "{\"run\":$id,\"outcome\":\"${outcome.name}\",\"marks\":{$body}}"
        }
        Log.i(TAG, line)
    }

    /**
     * The logcat tag the collector greps for. Deliberately distinct from every other tag in the
     * app so a collection run cannot accidentally scrape an unrelated log line.
     */
    const val TAG = "LingoFlowTiming"
}
