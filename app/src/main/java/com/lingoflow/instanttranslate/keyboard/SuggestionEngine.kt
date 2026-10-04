package com.lingoflow.instanttranslate.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Process-wide owner of the offline [WordPredictor] and the learned-words file. The IME and the
 * setup screen ("Clear learned words") share one instance. Disk work runs on one background
 * thread; [PersonalDictionary] is only touched on the main thread.
 */
internal object SuggestionEngine {
    private const val TAG = "LingoSuggestions"
    private const val LEARNED_FILE = "learned_words.tsv"
    private val disk = Executors.newSingleThreadExecutor { Thread(it, "lingo-suggestions") }
    private val main = Handler(Looper.getMainLooper())
    /** Bumped by clear so a load that read the old file can't resurrect cleared words. */
    private var generation = 0

    var predictor: WordPredictor? = null
        private set
    private var personal: PersonalDictionary? = null
    private var loading = false

    /** Loads once; [onReady] runs on the main thread when suggestions become available. */
    fun load(context: Context, onReady: () -> Unit) {
        if (predictor != null) { onReady(); return }
        if (loading) return
        loading = true
        val app = context.applicationContext
        val startedAt = generation
        disk.execute {
            val loaded = try {
                fun asset(name: String) = app.assets.open("dictionary/$name").bufferedReader().use { it.readText() }
                val learned = File(app.filesDir, LEARNED_FILE).takeIf { it.exists() }?.readText()
                val dictionary = learned?.let { PersonalDictionary.parse(it) } ?: PersonalDictionary()
                dictionary to WordPredictor(WordPredictor.parseWords(asset("en.txt")), WordPredictor.parseWords(asset("hi-Latn.txt")),
                    WordPredictor.parseNextWords(asset("next-words.txt")), dictionary)
            } catch (error: IOException) {
                // Typing still works without suggestions; say why instead of failing silently.
                Log.w(TAG, "Suggestions disabled: could not read bundled dictionary or learned words", error)
                null
            }
            main.post {
                loading = false
                if (loaded == null || startedAt != generation) return@post
                personal = loaded.first; predictor = loaded.second
                onReady()
            }
        }
    }

    /** Main thread. Returns false for non-words, which are never stored. */
    fun learn(word: String, previous: String?): Boolean = personal?.learn(word, previous) == true

    /** Writes learned words if they changed; the snapshot is taken here, the write happens off-thread. */
    fun save(context: Context) {
        val dictionary = personal ?: return
        if (!dictionary.changed) return
        val snapshot = dictionary.serialize()
        val file = File(context.applicationContext.filesDir, LEARNED_FILE)
        disk.execute {
            try {
                val temporary = File(file.parentFile, "$LEARNED_FILE.tmp")
                temporary.writeText(snapshot)
                if (!temporary.renameTo(file)) throw IOException("rename to ${file.name} failed")
            } catch (error: IOException) {
                Log.w(TAG, "Could not save learned words; they stay in memory until the next save", error)
            }
        }
    }

    /** Forgets every learned word in memory and on disk (setup screen privacy control). */
    fun clearLearned(context: Context) {
        generation++
        personal?.clear()
        val file = File(context.applicationContext.filesDir, LEARNED_FILE)
        disk.execute { if (file.exists() && !file.delete()) Log.w(TAG, "Could not delete ${file.name}") }
        personal?.serialize() // Marks the cleared state as saved; nothing remains to write.
    }
}
