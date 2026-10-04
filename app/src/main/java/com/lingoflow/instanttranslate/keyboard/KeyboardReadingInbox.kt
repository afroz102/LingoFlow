package com.lingoflow.instanttranslate.keyboard

import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import com.lingoflow.instanttranslate.direction.DirectionDetector

/** In-process handoff from a read-only selection action; expires rather than keeping a history. */
object KeyboardReadingInbox {
    private var message: String? = null
    private var expiresAt = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val expire = Runnable { clear() }

    @Synchronized fun offer(text: String) {
        if (!DirectionDetector.isSupported(text)) return
        message = text
        expiresAt = SystemClock.elapsedRealtime() + 60_000
        handler.removeCallbacks(expire)
        handler.postDelayed(expire, 60_000)
    }

    @Synchronized fun take(): String? {
        val text = message.takeIf { SystemClock.elapsedRealtime() <= expiresAt }
        message = null
        expiresAt = 0L
        handler.removeCallbacks(expire)
        return text
    }

    @Synchronized fun clear() { message = null; expiresAt = 0L; handler.removeCallbacks(expire) }
}
