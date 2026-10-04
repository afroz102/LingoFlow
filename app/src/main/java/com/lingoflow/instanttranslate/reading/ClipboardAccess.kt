package com.lingoflow.instanttranslate.reading

import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.os.Build
import com.lingoflow.instanttranslate.direction.DirectionDetector
import com.lingoflow.instanttranslate.keyboard.LingoKeyboardService
import java.security.MessageDigest

/** Never coerce clipboard URIs: resolving content can read unrelated files or grant payloads. */
object ClipboardAccess {
    const val OWN_CLIP_LABEL = "LingoBoard result"

    fun hasKeyboardAccess(context: Context): Boolean =
        ComponentName.unflattenFromString(Settings.Secure.getString(context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty()) == ComponentName(context, LingoKeyboardService::class.java)

    fun readText(context: Context): String? = try {
        if (KeyboardPrivacy.passwordInputActive) null else {
            val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
            val description = clip?.description
            if (clip == null || clip.itemCount != 1 || description?.label?.toString() == OWN_CLIP_LABEL ||
                (Build.VERSION.SDK_INT >= 24 && description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true)) null
            else clip.getItemAt(0).text?.toString()?.takeIf(DirectionDetector::isSupported)
        }
    } catch (_: RuntimeException) { null }
}

/** Password fields must never make a clipboard prompt appear, even when we are the default IME. */
object KeyboardPrivacy {
    var passwordInputActive = false
}

/** Keeps a session-only fingerprint rather than a second copy of the user's message. */
class ClipboardDeduplicator {
    private var lastFingerprint: String? = null
    fun accept(text: String): Boolean {
        val hash = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
        if (hash == lastFingerprint) return false
        lastFingerprint = hash
        return true
    }
}
